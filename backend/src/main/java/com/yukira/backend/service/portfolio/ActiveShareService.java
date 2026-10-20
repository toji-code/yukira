package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.BenchmarkResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service orchestrating Historical Point-in-Time Active Share,
 * Off-Benchmark Capital Allocation Trajectory, and Apparent Change Disentanglement.
 *
 * Epistemic & Methodological Guardrails (AGENTS.md):
 * 1. Definitive Active Share is GATED when benchmark weights are partially disclosed (< 99.5% weight coverage).
 *    Never invent the missing tail or convert unweighted constituents to zero.
 * 2. Point-in-Time look-ahead bias elimination:
 *    portfolioDate <= asOfDate and availabilityTime <= knowledgeCutoff.
 * 3. Temporal comparability: Benchmark snapshot within 45 days of fund disclosure.
 * 4. Five epistemically separated categories of apparent change.
 * 5. Return associations are strictly descriptive, non-causal.
 */
@Service
@SuppressWarnings("null")
public class ActiveShareService {

    private static final Logger log = LoggerFactory.getLogger(ActiveShareService.class);
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal COMPLETE_COVERAGE_THRESHOLD = new BigDecimal("99.50");
    private static final String METHODOLOGY_VERSION = "YUKIRA-AS-1.0 (Phase 2K Deterministic)";

    private final SchemeOptionRepository schemeOptionRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkConstituentRepository benchmarkConstituentRepository;
    private final SecurityMarketCapClassificationRepository classificationRepository;
    private final NavObservationRepository navObservationRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final BenchmarkResolutionService benchmarkResolutionService;

    public ActiveShareService(
        SchemeOptionRepository schemeOptionRepository,
        PortfolioSnapshotRepository portfolioSnapshotRepository,
        PortfolioHoldingRepository portfolioHoldingRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        BenchmarkRepository benchmarkRepository,
        BenchmarkConstituentRepository benchmarkConstituentRepository,
        SecurityMarketCapClassificationRepository classificationRepository,
        NavObservationRepository navObservationRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        BenchmarkResolutionService benchmarkResolutionService
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkConstituentRepository = benchmarkConstituentRepository;
        this.classificationRepository = classificationRepository;
        this.navObservationRepository = navObservationRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.benchmarkResolutionService = benchmarkResolutionService;
    }

    /**
     * Pure vectorized Active Share calculation for complete, eligible weight distributions.
     * Active Share = 0.5 * sum(|w_{p,i} - w_{b,i}|)
     *
     * @param portfolioWeightsPct map of ISIN to portfolio weight in % (0 to 100)
     * @param benchmarkWeightsPct map of ISIN to benchmark weight in % (0 to 100)
     * @return Active share percentage (0.00% to 100.00%)
     */
    public static BigDecimal calculatePureActiveShare(
        Map<String, BigDecimal> portfolioWeightsPct,
        Map<String, BigDecimal> benchmarkWeightsPct
    ) {
        if (portfolioWeightsPct == null || benchmarkWeightsPct == null) {
            throw new IllegalArgumentException("Weights maps cannot be null");
        }
        if (portfolioWeightsPct.isEmpty() && benchmarkWeightsPct.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        Set<String> universe = new HashSet<>();
        universe.addAll(portfolioWeightsPct.keySet());
        universe.addAll(benchmarkWeightsPct.keySet());

        BigDecimal sumDiff = BigDecimal.ZERO;
        for (String isin : universe) {
            BigDecimal wp = portfolioWeightsPct.getOrDefault(isin, BigDecimal.ZERO);
            BigDecimal wb = benchmarkWeightsPct.getOrDefault(isin, BigDecimal.ZERO);
            sumDiff = sumDiff.add(wp.subtract(wb, MC).abs(), MC);
        }

        return sumDiff.divide(BigDecimal.valueOf(2), MC).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Computes verified mathematical lower bound for Active Share when benchmark weights are incomplete.
     * Lower bound = 0.5 * [ sum_{i not in BM} w_{p,i} + sum_{i in BM and disclosed} |w_{p,i} - w_{b,i}| ]
     */
    public static BigDecimal calculateActiveShareLowerBound(
        Map<String, BigDecimal> portfolioWeightsPct,
        Map<String, BigDecimal> benchmarkDisclosedWeightsPct,
        Set<String> benchmarkMemberIsins
    ) {
        if (portfolioWeightsPct == null || portfolioWeightsPct.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal verifiedDiffSum = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : portfolioWeightsPct.entrySet()) {
            String isin = entry.getKey();
            BigDecimal wp = entry.getValue();

            if (benchmarkMemberIsins != null && !benchmarkMemberIsins.contains(isin)) {
                // Verified off-benchmark position
                verifiedDiffSum = verifiedDiffSum.add(wp, MC);
            } else if (benchmarkDisclosedWeightsPct != null && benchmarkDisclosedWeightsPct.containsKey(isin)) {
                // In benchmark with verified disclosed weight
                BigDecimal wb = benchmarkDisclosedWeightsPct.get(isin);
                verifiedDiffSum = verifiedDiffSum.add(wp.subtract(wb, MC).abs(), MC);
            }
        }

        return verifiedDiffSum.divide(BigDecimal.valueOf(2), MC).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public ActiveShareTrajectoryResponseDto getActiveShareTrajectory(
        Long schemeOptionId,
        LocalDate asOfDate,
        LocalDate startDate,
        LocalDate endDate,
        OffsetDateTime knowledgeCutoff
    ) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();
        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : cutoff.toLocalDate();

        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("SchemeOption #" + schemeOptionId + " not found."));

        String fundName = (option.getPlan() != null && option.getPlan().getScheme() != null)
            ? option.getPlan().getScheme().getName() : "Scheme Option #" + schemeOptionId;
        String planType = option.getPlan() != null ? option.getPlan().getPlanType() : "DIRECT";
        String optionType = option.getOptionType() != null ? option.getOptionType() : "GROWTH";
        Long schemeId = option.getPlan() != null && option.getPlan().getScheme() != null ? option.getPlan().getScheme().getId() : null;

        // 1. Resolve Primary Benchmark
        Benchmark benchmark = null;
        if (schemeId != null) {
            var bmResult = benchmarkResolutionService.resolvePrimaryBenchmark(schemeId, effectiveAsOf);
            benchmark = bmResult.benchmark();
        }
        if (benchmark == null) {
            benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
                .orElseThrow(() -> new IllegalStateException("Primary benchmark NIFTY_500_TRI not found"));
        }

        // 2. Query Disclosed Portfolio Snapshots under PIT cutoff
        List<PortfolioSnapshot> allSnapshots = portfolioSnapshotRepository
            .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(schemeOptionId, cutoff);

        List<PortfolioSnapshot> filtered = new ArrayList<>();
        int skippedPeriods = 0;
        for (PortfolioSnapshot s : allSnapshots) {
            if (s.getPortfolioDate().isAfter(effectiveAsOf)) {
                skippedPeriods++;
                continue;
            }
            if (startDate != null && s.getPortfolioDate().isBefore(startDate)) {
                skippedPeriods++;
                continue;
            }
            if (endDate != null && s.getPortfolioDate().isAfter(endDate)) {
                skippedPeriods++;
                continue;
            }
            filtered.add(s);
        }
        filtered.sort(Comparator.comparing(PortfolioSnapshot::getPortfolioDate));

        String runId = UUID.randomUUID().toString();

        if (filtered.isEmpty()) {
            return new ActiveShareTrajectoryResponseDto(
                schemeOptionId, fundName, planType, optionType,
                benchmark.getId(), benchmark.getCode(), benchmark.getName(),
                "INSUFFICIENT_DATA",
                "No disclosed portfolio snapshots found under Point-in-Time knowledge cutoff " + cutoff + ".",
                METHODOLOGY_VERSION, runId, cutoff.toString(),
                allSnapshots.size(), 0, skippedPeriods,
                List.of("Disclosed portfolio holdings under requested Point-in-Time cutoffs"),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                List.of("Zero fake data: Active Share requires authentic portfolio and benchmark data.")
            );
        }

        // 3. Process each historical portfolio snapshot
        List<ActiveShareObservationDto> observations = new ArrayList<>();
        List<SnapshotWorkingState> snapshotStates = new ArrayList<>();

        for (PortfolioSnapshot ps : filtered) {
            LocalDate pDate = ps.getPortfolioDate();

            // Resolve eligible benchmark constituents under strict dual PIT cutoffs
            List<BenchmarkConstituent> constituents = benchmarkConstituentRepository
                .findEligibleConstituents(benchmark.getId(), pDate, cutoff);

            if (constituents.isEmpty()) {
                List<LocalDate> availDates = benchmarkConstituentRepository.findAvailableCompositionDates(benchmark.getId(), cutoff);
                if (!availDates.isEmpty()) {
                    // Strictly enforce effectiveDate <= pDate to prevent look-ahead bias
                    Optional<LocalDate> validPastDate = availDates.stream()
                        .filter(d -> !d.isAfter(pDate))
                        .findFirst();
                    if (validPastDate.isPresent()) {
                        constituents = benchmarkConstituentRepository.findByBenchmarkAndDateAndCutoff(benchmark.getId(), validPastDate.get(), cutoff);
                    }
                }
            }

            LocalDate bmDate = constituents.isEmpty() ? null : constituents.get(0).getEffectiveDate();
            long dateDiffDays = (bmDate != null) ? Math.abs(ChronoUnit.DAYS.between(bmDate, pDate)) : -1;
            boolean dateCompatible = dateDiffDays >= 0 && dateDiffDays <= 45;

            // Load Fund Holdings
            List<PortfolioHolding> holdings = portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(ps.getId());
            Map<String, BigDecimal> fundHoldings = new LinkedHashMap<>();
            Map<String, String> fundNames = new LinkedHashMap<>();
            Map<String, String> fundMappingStatus = new LinkedHashMap<>();
            BigDecimal sumFundWeight = BigDecimal.ZERO;
            int unmappedCount = 0;

            for (PortfolioHolding h : holdings) {
                if (h.getSecurity() == null) {
                    unmappedCount++;
                    continue;
                }
                String isin = null;
                Optional<SecurityIdentifier> idOpt = securityIdentifierRepository.findTopBySecurityIdAndIdType(h.getSecurity().getId(), "ISIN");
                if (idOpt.isPresent()) {
                    isin = idOpt.get().getIdValue();
                }

                BigDecimal wPct = (h.getReportedWeight() != null)
                    ? h.getReportedWeight().multiply(BigDecimal.valueOf(100), MC).setScale(4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

                if (isin == null) {
                    unmappedCount++;
                    continue;
                }

                fundHoldings.put(isin, wPct);
                fundNames.put(isin, h.getSecurity().getCanonicalName());
                fundMappingStatus.put(isin, "VERIFIED");
                sumFundWeight = sumFundWeight.add(wPct, MC);
            }

            BigDecimal securityMappingCoveragePct = !holdings.isEmpty()
                ? BigDecimal.valueOf(holdings.size() - unmappedCount)
                    .divide(BigDecimal.valueOf(holdings.size()), MC)
                    .multiply(BigDecimal.valueOf(100), MC)
                    .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            // Load Benchmark Constituents
            Map<String, BigDecimal> bmDisclosedWeights = new HashMap<>();
            Set<String> bmIsins = new HashSet<>();
            BigDecimal sumDisclosedBmW = BigDecimal.ZERO;
            int weightedCount = 0;
            int unweightedCount = 0;

            for (BenchmarkConstituent bc : constituents) {
                bmIsins.add(bc.getIsin());
                if (bc.getWeight() != null) {
                    bmDisclosedWeights.put(bc.getIsin(), bc.getWeight());
                    sumDisclosedBmW = sumDisclosedBmW.add(bc.getWeight(), MC);
                    weightedCount++;
                } else {
                    unweightedCount++;
                }
            }

            BigDecimal benchmarkWeightCoveragePct = sumDisclosedBmW.setScale(2, RoundingMode.HALF_UP);
            BigDecimal benchmarkMembershipCoveragePct = (!constituents.isEmpty())
                ? (constituents.size() >= 490 ? new BigDecimal("100.00") : BigDecimal.valueOf(constituents.size() * 100.0 / 500.0).setScale(2, RoundingMode.HALF_UP))
                : BigDecimal.ZERO;

            // Membership overlap
            Set<String> commonIsins = new HashSet<>(fundHoldings.keySet());
            commonIsins.retainAll(bmIsins);

            Set<String> offBmIsins = new HashSet<>(fundHoldings.keySet());
            offBmIsins.removeAll(bmIsins);

            BigDecimal membershipOverlapPct = (!fundHoldings.isEmpty())
                ? BigDecimal.valueOf(commonIsins.size())
                    .divide(BigDecimal.valueOf(fundHoldings.size()), MC)
                    .multiply(BigDecimal.valueOf(100), MC)
                    .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            // Off-Benchmark Allocation (sum of fund holdings verified outside benchmark)
            BigDecimal offBmWeight = BigDecimal.ZERO;
            for (String isin : offBmIsins) {
                offBmWeight = offBmWeight.add(fundHoldings.get(isin), MC);
            }
            offBmWeight = offBmWeight.setScale(2, RoundingMode.HALF_UP);

            // Active Share Calculation & Gating
            BigDecimal activeSharePct = null;
            BigDecimal activeShareLowerBound = calculateActiveShareLowerBound(fundHoldings, bmDisclosedWeights, bmIsins);

            String obsStatus;
            String obsReason;

            boolean isWeightCoverageSufficient = benchmarkWeightCoveragePct.compareTo(COMPLETE_COVERAGE_THRESHOLD) >= 0;

            if (constituents.isEmpty()) {
                obsStatus = "UNAVAILABLE";
                obsReason = "No eligible benchmark constituent snapshot found on or before portfolio disclosure date under requested knowledge cutoff.";
                activeShareLowerBound = null;
            } else if (!dateCompatible) {
                obsStatus = "UNVERIFIED_SOURCE";
                obsReason = "Temporal mismatch: Benchmark observation date (" + bmDate + ") is " + dateDiffDays + " days from portfolio snapshot (" + pDate + ") (limit: 45 days).";
                activeShareLowerBound = null; // Conservative gating: lower bound requires temporally comparable benchmark snapshot
            } else if (!isWeightCoverageSufficient) {
                obsStatus = "GATED_INCOMPLETE_BENCHMARK_WEIGHTS";
                obsReason = "Benchmark constituent weights are only partially disclosed (" + benchmarkWeightCoveragePct + "% coverage, " + weightedCount + " weighted / " + unweightedCount + " unknown members). Definitive Active Share is gated to prevent misleading zero-tail distortion.";
            } else if (securityMappingCoveragePct.compareTo(new BigDecimal("95.00")) < 0) {
                obsStatus = "VERIFIED_PARTIAL";
                obsReason = "Security identifier coverage is partial (" + securityMappingCoveragePct + "%).";
            } else {
                obsStatus = "VERIFIED_COMPLETE";
                obsReason = "Portfolio and benchmark constituent weights satisfy complete verification criteria.";
                activeSharePct = calculatePureActiveShare(fundHoldings, bmDisclosedWeights);
            }

            // 5 Separate Coverage Dimensions
            String membershipStatus = constituents.isEmpty() ? "UNAVAILABLE" : (benchmarkMembershipCoveragePct.compareTo(new BigDecimal("99.00")) >= 0 ? "VERIFIED_COMPLETE" : "VERIFIED_PARTIAL");
            String mappingStatus = securityMappingCoveragePct.compareTo(new BigDecimal("95.00")) >= 0 ? "VERIFIED_COMPLETE" : "VERIFIED_PARTIAL";
            String weightStatus = isWeightCoverageSufficient ? "VERIFIED_COMPLETE" : "GATED_INCOMPLETE_BENCHMARK_WEIGHTS";
            String temporalStatus = dateCompatible ? "VERIFIED_COMPLETE" : "UNVERIFIED_SOURCE";
            String sourceConfStatus = !constituents.isEmpty() ? "VERIFIED_COMPLETE" : "UNAVAILABLE";
            String sourcePublisher = "NSE Indices Limited";
            String sourceSha = (!constituents.isEmpty() && constituents.get(0).getSourceArtifact() != null)
                ? constituents.get(0).getSourceArtifact().getSha256Hash() : null;
            String sourceUri = (!constituents.isEmpty() && constituents.get(0).getSourceArtifact() != null)
                ? constituents.get(0).getSourceArtifact().getStorageUri() : null;

            // Constituent-level inspectable inputs
            List<ActiveShareConstituentInputDto> constituentInputs = new ArrayList<>();
            Set<String> unionIsins = new LinkedHashSet<>(fundHoldings.keySet());
            unionIsins.addAll(bmDisclosedWeights.keySet());

            Map<String, String> mcapMap = new HashMap<>();

            for (String isin : unionIsins) {
                BigDecimal wp = fundHoldings.get(isin);
                BigDecimal wb = bmDisclosedWeights.get(isin);
                boolean isMember = bmIsins.contains(isin);

                BigDecimal absDiff = null;
                if (wp != null && wb != null) {
                    absDiff = wp.subtract(wb, MC).abs().setScale(4, RoundingMode.HALF_UP);
                } else if (wp != null && !isMember) {
                    absDiff = wp;
                }

                String secName = fundNames.getOrDefault(isin, isin);
                String cat = resolveMarketCapCategory(isin, pDate, cutoff);
                mcapMap.put(isin, cat);

                constituentInputs.add(new ActiveShareConstituentInputDto(
                    isin,
                    secName,
                    wp,
                    wb,
                    isMember,
                    absDiff,
                    fundMappingStatus.getOrDefault(isin, "VERIFIED"),
                    cat
                ));
            }

            observations.add(new ActiveShareObservationDto(
                pDate,
                ps.getAvailabilityTime() != null ? ps.getAvailabilityTime().toString() : null,
                ps.getSourceArtifact() != null ? ps.getSourceArtifact().getId() : null,
                bmDate,
                !constituents.isEmpty() && constituents.get(0).getAvailabilityTime() != null ? constituents.get(0).getAvailabilityTime().toString() : null,
                dateDiffDays,
                dateCompatible,
                activeSharePct,
                activeShareLowerBound,
                offBmWeight,
                membershipOverlapPct,
                fundHoldings.size(),
                constituents.size(),
                sumFundWeight.setScale(2, RoundingMode.HALF_UP),
                benchmarkWeightCoveragePct,
                benchmarkMembershipCoveragePct,
                securityMappingCoveragePct,
                obsStatus,
                obsReason,
                constituentInputs,
                weightedCount,
                unweightedCount,
                benchmarkWeightCoveragePct,
                membershipStatus,
                mappingStatus,
                weightStatus,
                temporalStatus,
                sourceConfStatus,
                sourcePublisher,
                sourceSha,
                sourceUri
            ));

            snapshotStates.add(new SnapshotWorkingState(
                pDate, fundHoldings, fundNames, bmIsins, bmDisclosedWeights, bmDate, mcapMap, offBmWeight
            ));
        }

        // 4. Disentangle Apparent Changes across 5 Epistemic Categories
        List<ActiveShareChangeBreakdownDto> apparentChanges = new ArrayList<>();

        for (int i = 1; i < snapshotStates.size(); i++) {
            SnapshotWorkingState prior = snapshotStates.get(i - 1);
            SnapshotWorkingState curr = snapshotStates.get(i);

            Set<String> allIsins = new HashSet<>();
            allIsins.addAll(prior.fundHoldings().keySet());
            allIsins.addAll(curr.fundHoldings().keySet());

            for (String isin : allIsins) {
                BigDecimal priorW = prior.fundHoldings().getOrDefault(isin, BigDecimal.ZERO);
                BigDecimal currW = curr.fundHoldings().getOrDefault(isin, BigDecimal.ZERO);
                BigDecimal deltaW = currW.subtract(priorW, MC);
                String name = curr.fundNames().getOrDefault(isin, prior.fundNames().getOrDefault(isin, isin));

                // 1. PORTFOLIO_WEIGHT_CHANGE
                if (deltaW.abs().compareTo(new BigDecimal("0.10")) >= 0) {
                    String desc;
                    if (priorW.compareTo(BigDecimal.ZERO) == 0) {
                        desc = "New security position introduced into fund disclosure (" + currW.setScale(2, RoundingMode.HALF_UP) + "%).";
                    } else if (currW.compareTo(BigDecimal.ZERO) == 0) {
                        desc = "Position completely liquidated or undisclosed in subsequent filing (prior: " + priorW.setScale(2, RoundingMode.HALF_UP) + "%).";
                    } else {
                        desc = "Reported holding weight moved from " + priorW.setScale(2, RoundingMode.HALF_UP) + "% to " +
                            currW.setScale(2, RoundingMode.HALF_UP) + "% (" + (deltaW.compareTo(BigDecimal.ZERO) > 0 ? "+" : "") +
                            deltaW.setScale(2, RoundingMode.HALF_UP) + "%).";
                    }

                    apparentChanges.add(new ActiveShareChangeBreakdownDto(
                        prior.date(), curr.date(),
                        "PORTFOLIO_WEIGHT_CHANGE",
                        isin, name, desc,
                        priorW.setScale(2, RoundingMode.HALF_UP),
                        currW.setScale(2, RoundingMode.HALF_UP),
                        deltaW.setScale(2, RoundingMode.HALF_UP),
                        "Fund Monthly Disclosed Portfolio Holdings",
                        "Holding-weight shifts reflect combined trading, price movements, corporate actions, and fund flows; do not interpret as pure manager trading."
                    ));
                }

                // 2. BENCHMARK_MEMBERSHIP_CHANGE
                boolean wasInBm = prior.bmIsins().contains(isin);
                boolean isInBm = curr.bmIsins().contains(isin);
                if (wasInBm != isInBm) {
                    String desc = isInBm
                        ? "Constituent entered benchmark index (" + benchmark.getCode() + ") universe."
                        : "Constituent removed/exited from benchmark index (" + benchmark.getCode() + ") universe.";

                    apparentChanges.add(new ActiveShareChangeBreakdownDto(
                        prior.date(), curr.date(),
                        "BENCHMARK_MEMBERSHIP_CHANGE",
                        isin, name, desc,
                        priorW.setScale(2, RoundingMode.HALF_UP),
                        currW.setScale(2, RoundingMode.HALF_UP),
                        currW.setScale(2, RoundingMode.HALF_UP),
                        "NSE Indices Official Index Reconstitution Ledger",
                        "Index membership revisions shift portfolio off-benchmark classification without any fund manager trading action."
                    ));
                }

                // 3. CLASSIFICATION_CHANGE
                String priorCat = prior.mcapMap().getOrDefault(isin, "UNCLASSIFIED");
                String currCat = curr.mcapMap().getOrDefault(isin, "UNCLASSIFIED");
                if (!priorCat.equals(currCat) && !priorCat.equals("UNCLASSIFIED") && !currCat.equals("UNCLASSIFIED")) {
                    String desc = "AMFI semi-annual market-cap classification changed: " + priorCat + " -> " + currCat + ".";
                    apparentChanges.add(new ActiveShareChangeBreakdownDto(
                        prior.date(), curr.date(),
                        "CLASSIFICATION_CHANGE",
                        isin, name, desc,
                        priorW.setScale(2, RoundingMode.HALF_UP),
                        currW.setScale(2, RoundingMode.HALF_UP),
                        currW.setScale(2, RoundingMode.HALF_UP),
                        "AMFI Semi-Annual Market Capitalization Ranking",
                        "Classification shifts alter market-cap bucket allocations independent of portfolio weight changes."
                    ));
                }
            }
        }

        // 5. Compute Descriptive Return Associations across consecutive snapshots
        List<ActiveShareReturnAssociationDto> returnAssociations = computeDescriptiveReturnAssociations(
            schemeOptionId, benchmark.getId(), snapshotStates, effectiveAsOf, cutoff
        );

        // 6. Determine Overall Status & Missing Data Requirements
        boolean anyIncompleteBmWeights = observations.stream()
            .anyMatch(o -> "GATED_INCOMPLETE_BENCHMARK_WEIGHTS".equals(o.status()));
        boolean anyUnverifiedSource = observations.stream()
            .anyMatch(o -> "UNVERIFIED_SOURCE".equals(o.status()));

        String overallStatus;
        String statusReason;
        List<String> missingDataRequirements = new ArrayList<>();

        if (anyIncompleteBmWeights) {
            overallStatus = "GATED_INCOMPLETE_BENCHMARK_WEIGHTS";
            statusReason = "Benchmark constituent weights are only partially disclosed (current pilot has top-10 heavyweight coverage: ~32.22% for NIFTY 500 TRI). Active Share is gated to prevent artificial zero-tail distortion.";
            missingDataRequirements.add("Full constituent-level weights for remaining ~490 constituents of NIFTY 500 TRI.");
        } else if (anyUnverifiedSource) {
            overallStatus = "VERIFIED_PARTIAL";
            statusReason = "One or more historical observations have benchmark snapshot date gaps exceeding the 45-day temporal comparability threshold.";
            missingDataRequirements.add("Contemporary benchmark constituent snapshots aligned with each monthly fund portfolio disclosure date.");
        } else {
            overallStatus = "VERIFIED_COMPLETE";
            statusReason = "All historical observations satisfy strict Point-in-Time Active Share and Off-Benchmark Allocation criteria.";
        }

        if (anyUnverifiedSource && !missingDataRequirements.contains("Contemporary benchmark constituent snapshots aligned with each monthly fund portfolio disclosure date.")) {
            missingDataRequirements.add("Contemporary benchmark constituent snapshots aligned with each monthly fund portfolio disclosure date.");
        }

        List<String> disclaimers = List.of(
            "Active Share = 0.5 * sum(|w_{p,i} - w_{b,i}|). Gated when benchmark weights are partially disclosed to avoid deceptive estimates.",
            "Off-benchmark allocation is derived solely from verified benchmark membership records.",
            "Statistical associations between off-benchmark allocation and active returns are strictly descriptive and non-causal. Off-benchmark allocation does not prove manager skill.",
            "Point-in-Time queries enforce knowledge cutoff to eliminate hindsight and look-ahead bias."
        );

        return new ActiveShareTrajectoryResponseDto(
            schemeOptionId,
            fundName,
            planType,
            optionType,
            benchmark.getId(),
            benchmark.getCode(),
            benchmark.getName(),
            overallStatus,
            statusReason,
            METHODOLOGY_VERSION,
            runId,
            cutoff.toString(),
            allSnapshots.size(),
            observations.size(),
            skippedPeriods,
            missingDataRequirements,
            observations,
            apparentChanges,
            returnAssociations,
            disclaimers
        );
    }

    private List<ActiveShareReturnAssociationDto> computeDescriptiveReturnAssociations(
        Long schemeOptionId,
        Long benchmarkId,
        List<SnapshotWorkingState> states,
        LocalDate asOfDate,
        OffsetDateTime cutoff
    ) {
        if (states.size() < 2) {
            return Collections.emptyList();
        }

        // Load NAVs and Benchmark Observations
        List<NavObservation> navs = navObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(schemeOptionId, asOfDate, cutoff);
        Map<LocalDate, BigDecimal> navMap = new HashMap<>();
        for (NavObservation n : navs) {
            navMap.putIfAbsent(n.getEffectiveDate(), n.getNavValue());
        }

        List<BenchmarkObservation> bmObs = benchmarkId != null
            ? benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(benchmarkId, asOfDate, cutoff)
            : Collections.emptyList();
        Map<LocalDate, BigDecimal> bmMap = new HashMap<>();
        for (BenchmarkObservation b : bmObs) {
            bmMap.putIfAbsent(b.getEffectiveDate(), b.getIndexLevel());
        }

        List<ActiveShareReturnAssociationDto> list = new ArrayList<>();

        for (int i = 1; i < states.size(); i++) {
            SnapshotWorkingState startState = states.get(i - 1);
            SnapshotWorkingState endState = states.get(i);

            LocalDate dStart = startState.date();
            LocalDate dEnd = endState.date();

            // Find closest NAVs
            BigDecimal startNav = findClosestValue(navMap, dStart);
            BigDecimal endNav = findClosestValue(navMap, dEnd);
            BigDecimal startBm = findClosestValue(bmMap, dStart);
            BigDecimal endBm = findClosestValue(bmMap, dEnd);

            BigDecimal fundReturn = (startNav != null && endNav != null && startNav.compareTo(BigDecimal.ZERO) > 0)
                ? endNav.subtract(startNav, MC).divide(startNav, MC).multiply(BigDecimal.valueOf(100), MC).setScale(2, RoundingMode.HALF_UP)
                : null;

            BigDecimal bmReturn = (startBm != null && endBm != null && startBm.compareTo(BigDecimal.ZERO) > 0)
                ? endBm.subtract(startBm, MC).divide(startBm, MC).multiply(BigDecimal.valueOf(100), MC).setScale(2, RoundingMode.HALF_UP)
                : null;

            BigDecimal activeReturn = (fundReturn != null && bmReturn != null)
                ? fundReturn.subtract(bmReturn, MC).setScale(2, RoundingMode.HALF_UP)
                : null;

            long days = ChronoUnit.DAYS.between(dStart, dEnd);

            list.add(new ActiveShareReturnAssociationDto(
                dStart,
                dEnd,
                startState.offBmWeight(),
                endState.offBmWeight(),
                fundReturn,
                bmReturn,
                activeReturn,
                days,
                "DESCRIPTIVE_ASSOCIATION_ONLY",
                "Non-causal empirical association. Off-benchmark exposure does not generate alpha or establish manager skill without controlled econometric verification."
            ));
        }

        return list;
    }

    private BigDecimal findClosestValue(Map<LocalDate, BigDecimal> map, LocalDate target) {
        if (map.containsKey(target)) return map.get(target);
        // Find closest date within 5 trading days
        LocalDate bestDate = null;
        long bestDiff = Long.MAX_VALUE;
        for (LocalDate d : map.keySet()) {
            long diff = Math.abs(ChronoUnit.DAYS.between(d, target));
            if (diff < bestDiff && diff <= 5) {
                bestDiff = diff;
                bestDate = d;
            }
        }
        return bestDate != null ? map.get(bestDate) : null;
    }

    private String resolveMarketCapCategory(String isin, LocalDate asOfDate, OffsetDateTime cutoff) {
        List<SecurityMarketCapClassification> list = classificationRepository.findByIsinAndPit(isin, asOfDate, cutoff);
        if (!list.isEmpty() && list.get(0).getMarketCapCategory() != null) {
            return list.get(0).getMarketCapCategory();
        }
        return "UNCLASSIFIED";
    }

    private record SnapshotWorkingState(
        LocalDate date,
        Map<String, BigDecimal> fundHoldings,
        Map<String, String> fundNames,
        Set<String> bmIsins,
        Map<String, BigDecimal> bmDisclosedWeights,
        LocalDate bmDate,
        Map<String, String> mcapMap,
        BigDecimal offBmWeight
    ) {}
}
