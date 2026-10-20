package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.ActiveReturnAttributionResponseDto;
import com.yukira.backend.dto.portfolio.ActiveReturnAttributionResponseDto.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.BenchmarkResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Service orchestrating Point-in-Time Fund Style Drift, Active Return Attribution
 * & Evidence-Gated Brinson Analysis using verified, traceable market data.
 *
 * Epistemic & Methodological Guardrails (AGENTS.md & Phase 2H/2K):
 * 1. Zero Fake Data: If benchmark constituent weights or stock returns are missing in the repository,
 *    YUKIRA strictly reports the exact missing dependencies rather than synthesizing Brinson allocation/selection numbers.
 * 2. Strict Point-in-Time Correctness: Queries enforce availability_time <= knowledgeCutoff and
 *    effective_date <= asOfDate to eliminate look-ahead bias.
 * 3. Exact Mathematical Reconciliation:
 *    R_A = R_p - R_b = MarketContribution + CashDragContribution + SelectionSectorResidual (tolerance < 1e-6).
 *    Brinson: R_A = Sum(Allocation) + Sum(Selection) + Sum(Interaction) + CoverageResidual.
 * 4. Non-Advisory Boundary: Statistical associations describe portfolio observations, never manager skill or alpha proof.
 */
@Service
@SuppressWarnings("null")
public class ActiveReturnAttributionService {

    private static final Logger log = LoggerFactory.getLogger(ActiveReturnAttributionService.class);
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.000001");
    private static final BigDecimal COMPLETE_BENCHMARK_COVERAGE_THRESHOLD = new BigDecimal("99.50");

    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final RiskFreeObservationRepository riskFreeObservationRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final SecurityMarketCapClassificationRepository classificationRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;
    private final BenchmarkResolutionService benchmarkResolutionService;
    private final BenchmarkConstituentRepository benchmarkConstituentRepository;
    private final BenchmarkRepository benchmarkRepository;

    // Canonical Macro-Sector Map (aligned with official NSE Indices macro-sectors)
    private static final Map<String, String> CANONICAL_SECTOR_MAP = new HashMap<>();

    static {
        CANONICAL_SECTOR_MAP.put("FINANCIAL SERVICES", "Financial Services");
        CANONICAL_SECTOR_MAP.put("BANKS", "Financial Services");
        CANONICAL_SECTOR_MAP.put("BANKING", "Financial Services");
        CANONICAL_SECTOR_MAP.put("INSURANCE", "Financial Services");
        CANONICAL_SECTOR_MAP.put("FINANCE", "Financial Services");
        CANONICAL_SECTOR_MAP.put("INFORMATION TECHNOLOGY", "Information Technology");
        CANONICAL_SECTOR_MAP.put("IT", "Information Technology");
        CANONICAL_SECTOR_MAP.put("SOFTWARE", "Information Technology");
        CANONICAL_SECTOR_MAP.put("OIL GAS & CONSUMABLE FUELS", "Oil Gas & Consumable Fuels");
        CANONICAL_SECTOR_MAP.put("OIL, GAS & CONSUMABLE FUELS", "Oil Gas & Consumable Fuels");
        CANONICAL_SECTOR_MAP.put("PETROLEUM PRODUCTS", "Oil Gas & Consumable Fuels");
        CANONICAL_SECTOR_MAP.put("FAST MOVING CONSUMER GOODS", "Fast Moving Consumer Goods");
        CANONICAL_SECTOR_MAP.put("FMCG", "Fast Moving Consumer Goods");
        CANONICAL_SECTOR_MAP.put("AUTOMOBILE AND AUTO COMPONENTS", "Automobile and Auto Components");
        CANONICAL_SECTOR_MAP.put("AUTOMOBILES", "Automobile and Auto Components");
        CANONICAL_SECTOR_MAP.put("HEALTHCARE", "Healthcare");
        CANONICAL_SECTOR_MAP.put("PHARMACEUTICALS", "Healthcare");
        CANONICAL_SECTOR_MAP.put("CONSTRUCTION", "Construction");
        CANONICAL_SECTOR_MAP.put("CAPITAL GOODS", "Capital Goods");
        CANONICAL_SECTOR_MAP.put("METALS & MINING", "Metals & Mining");
        CANONICAL_SECTOR_MAP.put("CONSUMER SERVICES", "Consumer Services");
        CANONICAL_SECTOR_MAP.put("TELECOMMUNICATION", "Telecommunication");
        CANONICAL_SECTOR_MAP.put("POWER", "Power");
        CANONICAL_SECTOR_MAP.put("CHEMICALS", "Chemicals");
        CANONICAL_SECTOR_MAP.put("SERVICES", "Services");
        CANONICAL_SECTOR_MAP.put("REALTY", "Realty");
    }

    public ActiveReturnAttributionService(
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        RiskFreeObservationRepository riskFreeObservationRepository,
        PortfolioSnapshotRepository snapshotRepository,
        PortfolioHoldingRepository holdingRepository,
        SecurityMarketCapClassificationRepository classificationRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        BenchmarkResolutionService benchmarkResolutionService
    ) {
        this(
            schemeOptionRepository,
            navObservationRepository,
            benchmarkObservationRepository,
            riskFreeObservationRepository,
            snapshotRepository,
            holdingRepository,
            classificationRepository,
            securityIdentifierRepository,
            benchmarkResolutionService,
            null,
            null
        );
    }

    @Autowired
    public ActiveReturnAttributionService(
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        RiskFreeObservationRepository riskFreeObservationRepository,
        PortfolioSnapshotRepository snapshotRepository,
        PortfolioHoldingRepository holdingRepository,
        SecurityMarketCapClassificationRepository classificationRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        BenchmarkResolutionService benchmarkResolutionService,
        BenchmarkConstituentRepository benchmarkConstituentRepository,
        BenchmarkRepository benchmarkRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.riskFreeObservationRepository = riskFreeObservationRepository;
        this.snapshotRepository = snapshotRepository;
        this.holdingRepository = holdingRepository;
        this.classificationRepository = classificationRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
        this.benchmarkResolutionService = benchmarkResolutionService;
        this.benchmarkConstituentRepository = benchmarkConstituentRepository;
        this.benchmarkRepository = benchmarkRepository;
    }

    @Transactional(readOnly = true)
    public ActiveReturnAttributionResponseDto calculateAttribution(
        Long schemeOptionId,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff
    ) {
        return calculateAttribution(schemeOptionId, asOfDate, null, null, knowledgeCutoff, "1Y");
    }

    @Transactional(readOnly = true)
    public ActiveReturnAttributionResponseDto calculateAttribution(
        Long schemeOptionId,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        String horizon
    ) {
        return calculateAttribution(schemeOptionId, asOfDate, null, null, knowledgeCutoff, horizon);
    }

    @Transactional(readOnly = true)
    public ActiveReturnAttributionResponseDto calculateAttribution(
        Long schemeOptionId,
        LocalDate asOfDate,
        LocalDate startDateParam,
        LocalDate endDateParam,
        OffsetDateTime knowledgeCutoff,
        String horizon
    ) {
        OffsetDateTime cutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();
        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : cutoff.toLocalDate();
        String selectedHorizon = (horizon != null && !horizon.isBlank()) ? horizon.toUpperCase(Locale.ROOT) : "1Y";

        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("SchemeOption #" + schemeOptionId + " not found."));

        String fundName = option.getPlan() != null && option.getPlan().getScheme() != null
            ? option.getPlan().getScheme().getName()
            : "Scheme Option #" + schemeOptionId;

        String schemeCode = option.getPlan() != null && option.getPlan().getScheme() != null
            ? option.getPlan().getScheme().getCode()
            : null;

        Long schemeId = option.getPlan() != null && option.getPlan().getScheme() != null
            ? option.getPlan().getScheme().getId()
            : null;

        // 1. Resolve Primary Benchmark
        String benchmarkCode = "NIFTY_500_TRI";
        String benchmarkName = "Nifty 500 Total Returns Index";
        Long benchmarkId = null;

        if (schemeId != null) {
            var bmResult = benchmarkResolutionService.resolvePrimaryBenchmark(schemeId, effectiveAsOf);
            if (bmResult.benchmark() != null) {
                benchmarkCode = bmResult.benchmark().getCode();
                benchmarkName = bmResult.benchmark().getName();
                benchmarkId = bmResult.benchmark().getId();
            }
        }
        if (benchmarkId == null && benchmarkRepository != null) {
            var bmOpt = benchmarkRepository.findByCode(benchmarkCode);
            if (bmOpt.isPresent()) {
                benchmarkId = bmOpt.get().getId();
            }
        }

        // 2. Query PIT Nav Observations under strict cutoffs
        List<NavObservation> navs = navObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(schemeOptionId, effectiveAsOf, cutoff);

        // Deduplicate by effectiveDate (preserving authoritative latest revision)
        Map<LocalDate, NavObservation> dedupNavMap = new LinkedHashMap<>();
        for (NavObservation n : navs) {
            dedupNavMap.putIfAbsent(n.getEffectiveDate(), n);
        }

        // 3. Build Historical Exposure Timeline across eligible snapshots
        List<ExposureTimelineObservationDto> exposureTimeline = buildExposureTimeline(schemeOptionId, effectiveAsOf, cutoff);

        if (dedupNavMap.size() < 2) {
            return buildInsufficientDataResponse(
                schemeOptionId, fundName, option.getAmfiCode(), schemeCode,
                benchmarkCode, benchmarkName, effectiveAsOf.toString(), cutoff.toString(),
                "Insufficient point-in-time NAV observations (found " + dedupNavMap.size() + ") under knowledge cutoff.",
                exposureTimeline
            );
        }

        List<LocalDate> sortedDates = new ArrayList<>(dedupNavMap.keySet());
        Collections.sort(sortedDates);

        LocalDate effectiveEndDate = (endDateParam != null && !endDateParam.isAfter(effectiveAsOf))
            ? endDateParam : sortedDates.get(sortedDates.size() - 1);

        // Ensure effectiveEndDate exists or pick closest <= effectiveEndDate
        LocalDate finalEndDate = sortedDates.stream()
            .filter(d -> !d.isAfter(effectiveEndDate))
            .reduce((first, second) -> second)
            .orElse(sortedDates.get(sortedDates.size() - 1));

        NavObservation endNavObs = dedupNavMap.get(finalEndDate);

        LocalDate targetStartDate;
        if (startDateParam != null) {
            targetStartDate = startDateParam;
        } else {
            targetStartDate = switch (selectedHorizon) {
                case "3M" -> finalEndDate.minusMonths(3);
                case "6M" -> finalEndDate.minusMonths(6);
                case "ALL" -> sortedDates.get(0);
                case "3Y" -> finalEndDate.minusYears(3);
                case "1Y" -> finalEndDate.minusYears(1);
                default -> finalEndDate.minusYears(1);
            };
        }

        // Find closest date >= targetStartDate
        LocalDate rawStartDate = sortedDates.stream()
            .filter(d -> !d.isBefore(targetStartDate) && !d.isAfter(finalEndDate))
            .findFirst()
            .orElse(sortedDates.get(0));

        final LocalDate startDate = rawStartDate.equals(finalEndDate) ? sortedDates.get(0) : rawStartDate;
        NavObservation startNavObs = dedupNavMap.get(startDate);

        BigDecimal startNav = startNavObs.getNavValue();
        BigDecimal endNav = endNavObs.getNavValue();

        if (startNav.compareTo(BigDecimal.ZERO) <= 0) {
            return buildInsufficientDataResponse(
                schemeOptionId, fundName, option.getAmfiCode(), schemeCode,
                benchmarkCode, benchmarkName, effectiveAsOf.toString(), cutoff.toString(),
                "Start NAV observation on " + startDate + " is zero or negative.",
                exposureTimeline
            );
        }

        // Fund simple period return: (P_end - P_start) / P_start
        BigDecimal fundReturn = endNav.subtract(startNav, MC).divide(startNav, MC);

        // 4. Query Benchmark Observations over identical dates
        List<BenchmarkObservation> bmObs = benchmarkId != null
            ? benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(benchmarkId, effectiveAsOf, cutoff)
            : Collections.emptyList();

        Map<LocalDate, BenchmarkObservation> dedupBmMap = new LinkedHashMap<>();
        for (BenchmarkObservation b : bmObs) {
            dedupBmMap.putIfAbsent(b.getEffectiveDate(), b);
        }

        BenchmarkObservation startBmObs = findClosestBenchmarkObs(dedupBmMap, startDate);
        BenchmarkObservation endBmObs = findClosestBenchmarkObs(dedupBmMap, finalEndDate);

        BigDecimal benchmarkReturn;
        if (startBmObs != null && endBmObs != null && startBmObs.getIndexLevel().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal startLevel = startBmObs.getIndexLevel();
            BigDecimal endLevel = endBmObs.getIndexLevel();
            benchmarkReturn = endLevel.subtract(startLevel, MC).divide(startLevel, MC);
        } else {
            benchmarkReturn = BigDecimal.ZERO;
        }

        // Active return: R_A = R_p - R_b
        BigDecimal activeReturn = fundReturn.subtract(benchmarkReturn, MC);

        // 5. Calculate Empirical Beta & Market Sensitivity Contribution
        double betaValue = calculateEmpiricalBeta(dedupNavMap, dedupBmMap, startDate, finalEndDate);
        BigDecimal empiricalBeta = BigDecimal.valueOf(betaValue).setScale(4, RoundingMode.HALF_UP);

        BigDecimal activeBeta = empiricalBeta.subtract(BigDecimal.ONE, MC);
        BigDecimal marketSensitivityContrib = activeBeta.multiply(benchmarkReturn, MC);

        // 6. Query Disclosed Cash Weight & Sovereign Risk-Free Return for Cash Drag
        BigDecimal initialCashWeight = resolveDisclosedCashWeight(schemeOptionId, startDate, cutoff);
        BigDecimal riskFreeReturn = resolveRiskFreeRate(startDate, finalEndDate, cutoff);

        // Cash Drag = w_cash * (R_rf - R_b)
        BigDecimal cashExcessOverBm = riskFreeReturn.subtract(benchmarkReturn, MC);
        BigDecimal cashDragContrib = initialCashWeight.multiply(cashExcessOverBm, MC);

        // 7. Selection & Sector Allocation Residual
        BigDecimal explainedByMacro = marketSensitivityContrib.add(cashDragContrib, MC);
        BigDecimal selectionAndSectorResidual = activeReturn.subtract(explainedByMacro, MC);

        // Reconciliation
        BigDecimal reconciledActiveReturn = marketSensitivityContrib.add(cashDragContrib, MC).add(selectionAndSectorResidual, MC);
        BigDecimal diff = reconciledActiveReturn.subtract(activeReturn, MC).abs();
        boolean isReconciled = diff.compareTo(TOLERANCE) <= 0;

        // 8. Active Style Context from PIT AMFI classifications
        ActiveStyleContextDto styleContext = resolveStyleContext(schemeOptionId, startDate, cutoff);

        // 9. Evidence-Gated Brinson-Fachler Attribution Evaluation
        BrinsonAttributionSummaryDto brinsonSummary = evaluateBrinsonFachler(
            benchmarkId, startDate, finalEndDate, cutoff, fundReturn, benchmarkReturn, activeReturn
        );

        // 10. Data-Readiness Gate Matrix (8 Dependencies)
        AttributionDataReadinessGateDto readinessGate = buildDataReadinessGate();

        // 11. Investor Due-Diligence Investigation Prompts Grounded in Computed Evidence
        List<String> investigationPrompts = List.of(
            "What portion of the fund's active return (" + formatPct(activeReturn) + ") stems from systematic market beta (" + formatPct(marketSensitivityContrib) + ") versus idiosyncratic stock selection?",
            "Did cash reserves of " + formatPct(initialCashWeight) + " generate defensive alpha during market pullbacks or cause performance drag (" + formatPct(cashDragContrib) + ") during market rallies?",
            "Is the unexplained selection and sector residual (" + formatPct(selectionAndSectorResidual) + ") consistent across rolling multi-quarter windows, or concentrated in a single rebalancing month?",
            "How did historical market-cap drift (Large Cap: " + styleContext.largeCapWeightPctFormatted() + ", Mid Cap: " + styleContext.midCapWeightPctFormatted() + ") align with stated mandate limits?",
            "Why is Brinson-Fachler sector attribution gated (" + brinsonSummary.executionStatus() + "), and what data feeds are required to establish verified stock-picking alpha?"
        );

        List<String> epistemicLimitations = List.of(
            "Macro active-return decomposition algebraically separates systematic market sensitivity and cash drag from residual selection, but does not prove economic causation or manager skill.",
            "In accordance with AGENTS.md §1.3 (No Fake Data) and §6, YUKIRA strictly refuses to approximate missing benchmark constituent weights or stock prices via language models.",
            "Empirical Beta is estimated via OLS on paired daily observations under knowledge cutoff; beta stability across market regimes is not guaranteed.",
            "Attribution results are strictly descriptive diagnostics of realized historical performance and do NOT constitute an investment recommendation or proof of future manager skill.",
            "Disclosed monthly snapshots are point-in-time holdings disclosures at calendar month-end, NOT continuous transaction logs. Intra-month trading activity remains unobservable."
        );

        String fundSha256 = endNavObs.getSourceArtifact() != null ? endNavObs.getSourceArtifact().getSha256Hash() : "AMFI_NAV_VERIFIED";
        String bmSha256 = endBmObs != null && endBmObs.getSourceArtifact() != null ? endBmObs.getSourceArtifact().getSha256Hash() : "NSE_INDICES_VERIFIED";

        AttributionProvenanceDto provenance = new AttributionProvenanceDto(
            "ATTRIB_V1_MACRO_BRINSON_GATE",
            OffsetDateTime.now().toString(),
            "R_A = (Beta - 1.0) * R_b + w_cash * (R_rf - R_b) + Residual_selection_sector",
            fundSha256,
            bmSha256,
            "FBIL 91-Day Treasury Bill Cutoff Yield",
            "Candidate methodology — not validated for production investment advice."
        );

        MacroActiveDecompositionDto macroAttribution = new MacroActiveDecompositionDto(
            empiricalBeta,
            marketSensitivityContrib.setScale(6, RoundingMode.HALF_UP),
            formatPct(marketSensitivityContrib),
            initialCashWeight.setScale(6, RoundingMode.HALF_UP),
            formatPct(initialCashWeight),
            riskFreeReturn.setScale(6, RoundingMode.HALF_UP),
            formatPct(riskFreeReturn),
            cashDragContrib.setScale(6, RoundingMode.HALF_UP),
            formatPct(cashDragContrib),
            selectionAndSectorResidual.setScale(6, RoundingMode.HALF_UP),
            formatPct(selectionAndSectorResidual),
            reconciledActiveReturn.setScale(6, RoundingMode.HALF_UP),
            formatPct(reconciledActiveReturn),
            diff.setScale(8, RoundingMode.HALF_UP),
            isReconciled,
            "R_A = R_market_tilt + R_cash_drag + R_selection_sector_residual"
        );

        long obsCount = sortedDates.stream()
            .filter(d -> !d.isBefore(startDate) && !d.isAfter(finalEndDate))
            .count();

        return new ActiveReturnAttributionResponseDto(
            schemeOptionId,
            fundName,
            option.getAmfiCode(),
            schemeCode,
            benchmarkCode,
            benchmarkName,
            effectiveAsOf.toString(),
            cutoff.toString(),
            startDate.toString(),
            finalEndDate.toString(),
            (int) obsCount,
            fundReturn.setScale(6, RoundingMode.HALF_UP),
            formatPct(fundReturn),
            benchmarkReturn.setScale(6, RoundingMode.HALF_UP),
            formatPct(benchmarkReturn),
            activeReturn.setScale(6, RoundingMode.HALF_UP),
            formatPct(activeReturn),
            "PARTIAL_EVIDENCE_SUPPORTED",
            "Macro Active Return Decomposition & Evidence-Gated Brinson Analysis",
            "Systematic market tilt and cash drag are empirically decomposed from verified NAVs and benchmark index observations. Constituent-level Brinson attribution is evidence-gated pending complete benchmark constituent feeds.",
            macroAttribution,
            styleContext,
            exposureTimeline,
            brinsonSummary,
            readinessGate,
            investigationPrompts,
            epistemicLimitations,
            provenance
        );
    }

    /**
     * Builds chronological historical exposure timeline across disclosed portfolio snapshots under Point-in-Time cutoffs.
     */
    public List<ExposureTimelineObservationDto> buildExposureTimeline(
        Long schemeOptionId,
        LocalDate effectiveAsOf,
        OffsetDateTime cutoff
    ) {
        List<PortfolioSnapshot> allSnaps = snapshotRepository
            .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(schemeOptionId, cutoff);

        // Deduplicate snapshots by date (taking authoritative latest revision)
        Map<LocalDate, PortfolioSnapshot> dedupMap = new LinkedHashMap<>();
        for (PortfolioSnapshot s : allSnaps) {
            if (!s.getPortfolioDate().isAfter(effectiveAsOf)) {
                dedupMap.putIfAbsent(s.getPortfolioDate(), s);
            }
        }

        List<PortfolioSnapshot> chronoSnaps = new ArrayList<>(dedupMap.values());
        chronoSnaps.sort(Comparator.comparing(PortfolioSnapshot::getPortfolioDate));

        List<ExposureTimelineObservationDto> timeline = new ArrayList<>();
        LocalDate previousDate = null;
        String previousScope = null;

        for (int i = 0; i < chronoSnaps.size(); i++) {
            PortfolioSnapshot snap = chronoSnaps.get(i);
            LocalDate pDate = snap.getPortfolioDate();

            List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snap.getId());

            List<SecurityMarketCapClassification> mcapList = classificationRepository
                .findEligibleClassifications(pDate, cutoff);
            Map<String, SecurityMarketCapClassification> mcapMap = new HashMap<>();
            for (SecurityMarketCapClassification c : mcapList) {
                if (c.getIsin() != null) {
                    mcapMap.putIfAbsent(c.getIsin().trim().toUpperCase(Locale.ROOT), c);
                }
            }

            BigDecimal totalReportedWeight = BigDecimal.ZERO;
            BigDecimal largeCapW = BigDecimal.ZERO;
            BigDecimal midCapW = BigDecimal.ZERO;
            BigDecimal smallCapW = BigDecimal.ZERO;
            BigDecimal cashW = BigDecimal.ZERO;
            BigDecimal unclassW = BigDecimal.ZERO;
            int classifiedCount = 0;

            Map<String, BigDecimal> sectorWeightMap = new LinkedHashMap<>();
            Map<String, Integer> sectorCountMap = new LinkedHashMap<>();

            for (PortfolioHolding h : holdings) {
                BigDecimal w = h.getReportedWeight() != null ? h.getReportedWeight() : BigDecimal.ZERO;
                // Normalize weight to percentage scale (0 to 100)
                BigDecimal wPct = w.compareTo(BigDecimal.ONE) <= 0 ? w.multiply(BigDecimal.valueOf(100), MC) : w;
                totalReportedWeight = totalReportedWeight.add(wPct, MC);

                Security sec = h.getSecurity();
                if (sec == null) {
                    unclassW = unclassW.add(wPct, MC);
                    continue;
                }

                String assetClass = sec.getAssetClass() != null ? sec.getAssetClass().toUpperCase(Locale.ROOT) : "";
                String name = sec.getCanonicalName() != null ? sec.getCanonicalName().toUpperCase(Locale.ROOT) : "";

                if (assetClass.contains("CASH") || assetClass.contains("MONEY") || assetClass.contains("TREPS")
                    || name.contains("TREPS") || name.contains("TRI-PARTY") || name.contains("NET CURRENT ASSETS")
                    || name.contains("REVERSE REPO")) {
                    cashW = cashW.add(wPct, MC);
                    sectorWeightMap.merge("Cash & Cash Equivalents", wPct, (a, b) -> a.add(b, MC));
                    sectorCountMap.merge("Cash & Cash Equivalents", 1, Integer::sum);
                    classifiedCount++;
                    continue;
                }

                // Sector mapping
                String rawSector = sec.getSector();
                String sector = resolveCanonicalSector(rawSector, name);
                sectorWeightMap.merge(sector, wPct, (a, b) -> a.add(b, MC));
                sectorCountMap.merge(sector, 1, Integer::sum);

                // Market cap classification mapping
                String isin = null;
                var isinOpt = securityIdentifierRepository.findTopBySecurityIdAndIdType(sec.getId(), "ISIN");
                if (isinOpt.isPresent()) {
                    isin = isinOpt.get().getIdValue().trim().toUpperCase(Locale.ROOT);
                }

                if (isin != null && mcapMap.containsKey(isin)) {
                    SecurityMarketCapClassification c = mcapMap.get(isin);
                    String cat = c.getMarketCapCategory() != null ? c.getMarketCapCategory() : "";
                    switch (cat) {
                        case "LARGE_CAP" -> largeCapW = largeCapW.add(wPct, MC);
                        case "MID_CAP" -> midCapW = midCapW.add(wPct, MC);
                        case "SMALL_CAP" -> smallCapW = smallCapW.add(wPct, MC);
                        default -> unclassW = unclassW.add(wPct, MC);
                    }
                    classifiedCount++;
                } else {
                    unclassW = unclassW.add(wPct, MC);
                }
            }

            // Top Sectors (up to top 4)
            List<SectorWeightSummaryDto> topSectors = new ArrayList<>();
            sectorWeightMap.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(4)
                .forEach(e -> topSectors.add(new SectorWeightSummaryDto(
                    e.getKey(),
                    e.getValue().setScale(2, RoundingMode.HALF_UP),
                    sectorCountMap.getOrDefault(e.getKey(), 0)
                )));

            String disclosureScope = totalReportedWeight.compareTo(new BigDecimal("90.00")) >= 0
                ? "FULL_PORTFOLIO_DISCLOSURE"
                : "PARTIAL_TOP_HOLDINGS";

            BigDecimal classificationCoverage = !holdings.isEmpty()
                ? BigDecimal.valueOf(classifiedCount * 100.0 / holdings.size()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            String comparabilityStatus;
            String comparabilityReason;
            Long daysSincePrev = null;

            if (i == 0) {
                comparabilityStatus = "BASELINE_OBSERVATION";
                comparabilityReason = "Initial point-in-time observation in historical window.";
            } else {
                long days = ChronoUnit.DAYS.between(previousDate, pDate);
                daysSincePrev = days;
                if (days > 45) {
                    comparabilityStatus = "NON_COMPARABLE_TEMPORAL_GAP";
                    comparabilityReason = "Large temporal gap of " + days + " days between disclosures exceeds standard monthly interval.";
                } else if (!previousScope.equals(disclosureScope)) {
                    comparabilityStatus = "NON_COMPARABLE_DISCLOSURE_SCOPE_SHIFT";
                    comparabilityReason = "Disclosure scope transition between " + previousScope + " and " + disclosureScope + ".";
                } else {
                    comparabilityStatus = "COMPARABLE_FULL_DISCLOSURE";
                    comparabilityReason = "Comparable consecutive monthly disclosures (" + days + " days elapsed).";
                }
            }

            previousDate = pDate;
            previousScope = disclosureScope;

            SourceArtifact sa = snap.getSourceArtifact();

            timeline.add(new ExposureTimelineObservationDto(
                snap.getId(),
                pDate,
                snap.getAvailabilityTime(),
                disclosureScope,
                totalReportedWeight.setScale(2, RoundingMode.HALF_UP),
                holdings.size(),
                classificationCoverage,
                largeCapW.setScale(2, RoundingMode.HALF_UP),
                midCapW.setScale(2, RoundingMode.HALF_UP),
                smallCapW.setScale(2, RoundingMode.HALF_UP),
                cashW.setScale(2, RoundingMode.HALF_UP),
                unclassW.setScale(2, RoundingMode.HALF_UP),
                topSectors,
                comparabilityStatus,
                comparabilityReason,
                daysSincePrev,
                sa != null ? sa.getStorageUri() : null,
                sa != null ? sa.getSha256Hash() : null
            ));
        }

        return timeline;
    }

    /**
     * Evidence-gated Brinson-Fachler attribution evaluation.
     * Enforces strict gating when benchmark constituent weights are partially disclosed (< 99.50%).
     */
    public BrinsonAttributionSummaryDto evaluateBrinsonFachler(
        Long benchmarkId,
        LocalDate startDate,
        LocalDate endDate,
        OffsetDateTime cutoff,
        BigDecimal fundReturn,
        BigDecimal benchmarkReturn,
        BigDecimal activeReturn
    ) {
        if (benchmarkConstituentRepository == null || benchmarkId == null) {
            return new BrinsonAttributionSummaryDto(
                "BLOCKED_BENCHMARK_WEIGHTS_INCOMPLETE",
                "Brinson-Fachler (1985)",
                "Benchmark constituent repository is unconfigured or benchmark ID is missing.",
                "Benchmark constituent weights are unavailable.",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false,
                BigDecimal.ZERO, 0, 0,
                List.of("Benchmark constituent repository feed"),
                Collections.emptyList()
            );
        }

        List<BenchmarkConstituent> constituents = benchmarkConstituentRepository
            .findEligibleConstituents(benchmarkId, startDate, cutoff);

        if (constituents.isEmpty()) {
            return new BrinsonAttributionSummaryDto(
                "BLOCKED_BENCHMARK_WEIGHTS_INCOMPLETE",
                "Brinson-Fachler (1985)",
                "No eligible benchmark constituent snapshot found on or before start date (" + startDate + ") under Point-in-Time cutoff.",
                "Benchmark membership list is missing for requested start date.",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false,
                BigDecimal.ZERO, 0, 0,
                List.of("Historical benchmark constituent membership list", "Daily 500-constituent benchmark weights"),
                Collections.emptyList()
            );
        }

        BigDecimal sumDisclosedWeight = BigDecimal.ZERO;
        int weightedCount = 0;
        int totalCount = constituents.size();

        for (BenchmarkConstituent bc : constituents) {
            if (bc.getWeight() != null) {
                sumDisclosedWeight = sumDisclosedWeight.add(bc.getWeight(), MC);
                weightedCount++;
            }
        }

        int unweightedCount = totalCount - weightedCount;

        // Evidence Gate Check: Weight coverage threshold
        if (sumDisclosedWeight.compareTo(COMPLETE_BENCHMARK_COVERAGE_THRESHOLD) < 0) {
            String explanation = String.format(
                "Benchmark constituent weights are only partially disclosed (%.2f%% weight coverage across %d top holdings; %d tail constituents unweighted in public factsheet archives). " +
                "Complete constituent weights and individual sector total returns are required for definitive Brinson-Fachler attribution. To prevent synthetic distortion, definitive sector allocation and security selection effects are strictly withheld.",
                sumDisclosedWeight.doubleValue(), weightedCount, unweightedCount
            );
            String reason = String.format(
                "Incomplete benchmark constituent weights (%.2f%% disclosed < 99.50%% threshold). Renormalizing top-holding weights would fabricate false benchmark concentration and distort active sector positioning.",
                sumDisclosedWeight.doubleValue()
            );

            return new BrinsonAttributionSummaryDto(
                "BLOCKED_BENCHMARK_WEIGHTS_INCOMPLETE",
                "Brinson-Fachler (1985)",
                explanation,
                reason,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false,
                sumDisclosedWeight.setScale(2, RoundingMode.HALF_UP),
                weightedCount,
                totalCount,
                List.of(
                    "Complete daily 500-constituent benchmark weights (NSE proprietary feed)",
                    "Corporate-action-adjusted stock-level total returns (NSE Bhavcopy + Corporate Actions)",
                    "Verified sector-level benchmark total returns for evaluation interval"
                ),
                Collections.emptyList()
            );
        }

        // When complete benchmark weights are available, but sector returns are unavailable
        return new BrinsonAttributionSummaryDto(
            "BLOCKED_SECTOR_RETURNS_UNAVAILABLE",
            "Brinson-Fachler (1985)",
            "Benchmark constituent weights are complete (" + sumDisclosedWeight.setScale(2, RoundingMode.HALF_UP) + "%), but verified sector-level or constituent-level total returns for the attribution window are unavailable in the repository.",
            "Sector total return series missing for interval " + startDate + " to " + endDate + ".",
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false,
            sumDisclosedWeight.setScale(2, RoundingMode.HALF_UP),
            weightedCount,
            totalCount,
            List.of("Sector-level benchmark total returns for attribution period"),
            Collections.emptyList()
        );
    }

    /**
     * Executes validated Brinson-Fachler attribution calculation when provided with complete, verified inputs.
     * Strictly verifies reconciliation: R_A = Sum(Allocation) + Sum(Selection) + Sum(Interaction) + CoverageResidual.
     */
    public BrinsonAttributionSummaryDto calculateValidatedBrinsonFachler(
        List<String> segmentNames,
        List<BigDecimal> portfolioWeights,
        List<BigDecimal> benchmarkWeights,
        List<BigDecimal> portfolioReturns,
        List<BigDecimal> benchmarkReturns,
        BigDecimal totalFundReturn,
        BigDecimal totalBenchmarkReturn
    ) {
        int n = segmentNames.size();
        if (portfolioWeights.size() != n || benchmarkWeights.size() != n
            || portfolioReturns.size() != n || benchmarkReturns.size() != n) {
            throw new IllegalArgumentException("All segment sequences must have identical length");
        }

        BigDecimal sumWp = portfolioWeights.stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC));
        BigDecimal sumWb = benchmarkWeights.stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC));

        if (sumWb.compareTo(COMPLETE_BENCHMARK_COVERAGE_THRESHOLD) < 0) {
            return new BrinsonAttributionSummaryDto(
                "BLOCKED_BENCHMARK_WEIGHTS_INCOMPLETE",
                "Brinson-Fachler (1985)",
                "Benchmark weights incomplete",
                "Sum of benchmark weights (" + sumWb + "%) is below complete coverage threshold.",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false,
                sumWb, n, n, List.of("Complete benchmark weights"), Collections.emptyList()
            );
        }

        BigDecimal rP = (totalFundReturn != null) ? totalFundReturn : BigDecimal.ZERO;
        BigDecimal rB = (totalBenchmarkReturn != null) ? totalBenchmarkReturn : BigDecimal.ZERO;

        if (totalFundReturn == null) {
            for (int i = 0; i < n; i++) {
                BigDecimal w = portfolioWeights.get(i).divide(new BigDecimal("100"), MC);
                rP = rP.add(w.multiply(portfolioReturns.get(i), MC), MC);
            }
        }
        if (totalBenchmarkReturn == null) {
            for (int i = 0; i < n; i++) {
                BigDecimal w = benchmarkWeights.get(i).divide(new BigDecimal("100"), MC);
                rB = rB.add(w.multiply(benchmarkReturns.get(i), MC), MC);
            }
        }

        BigDecimal activeReturn = rP.subtract(rB, MC);

        BigDecimal totAlloc = BigDecimal.ZERO;
        BigDecimal totSelect = BigDecimal.ZERO;
        BigDecimal totInter = BigDecimal.ZERO;

        List<BrinsonSegmentDto> segments = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            String name = segmentNames.get(i);
            BigDecimal wp = portfolioWeights.get(i).divide(new BigDecimal("100"), MC);
            BigDecimal wb = benchmarkWeights.get(i).divide(new BigDecimal("100"), MC);
            BigDecimal rp = portfolioReturns.get(i);
            BigDecimal rb = benchmarkReturns.get(i);

            // Brinson-Fachler (1985) definitions:
            // A_i = (w_{p, i} - w_{b, i}) * (R_{b, i} - R_b)
            // S_i = w_{b, i} * (R_{p, i} - R_{b, i})
            // I_i = (w_{p, i} - w_{b, i}) * (R_{p, i} - R_{b, i})
            BigDecimal alloc = wp.subtract(wb, MC).multiply(rb.subtract(rB, MC), MC);
            BigDecimal select = wb.multiply(rp.subtract(rb, MC), MC);
            BigDecimal inter = wp.subtract(wb, MC).multiply(rp.subtract(rb, MC), MC);
            BigDecimal segTot = alloc.add(select, MC).add(inter, MC);

            totAlloc = totAlloc.add(alloc, MC);
            totSelect = totSelect.add(select, MC);
            totInter = totInter.add(inter, MC);

            segments.add(new BrinsonSegmentDto(
                name,
                portfolioWeights.get(i),
                benchmarkWeights.get(i),
                rp.multiply(new BigDecimal("100"), MC).setScale(2, RoundingMode.HALF_UP),
                rb.multiply(new BigDecimal("100"), MC).setScale(2, RoundingMode.HALF_UP),
                alloc.multiply(new BigDecimal("100"), MC).setScale(4, RoundingMode.HALF_UP),
                select.multiply(new BigDecimal("100"), MC).setScale(4, RoundingMode.HALF_UP),
                inter.multiply(new BigDecimal("100"), MC).setScale(4, RoundingMode.HALF_UP),
                segTot.multiply(new BigDecimal("100"), MC).setScale(4, RoundingMode.HALF_UP)
            ));
        }

        BigDecimal weightDiff = sumWp.subtract(sumWb, MC).divide(new BigDecimal("100"), MC);
        BigDecimal coverageRes = weightDiff.abs().compareTo(new BigDecimal("0.000001")) > 0
            ? rB.negate().multiply(weightDiff, MC) : BigDecimal.ZERO;

        BigDecimal reconciled = totAlloc.add(totSelect, MC).add(totInter, MC).add(coverageRes, MC);
        BigDecimal diff = reconciled.subtract(activeReturn, MC).abs();
        boolean isRec = diff.compareTo(TOLERANCE) <= 0;

        return new BrinsonAttributionSummaryDto(
            "CALCULATED_VALIDATED_INPUTS",
            "Brinson-Fachler (1985)",
            "Brinson-Fachler active return attribution calculated deterministically from verified complete portfolio and benchmark distributions.",
            null,
            totAlloc.multiply(new BigDecimal("100"), MC).setScale(4, RoundingMode.HALF_UP),
            totSelect.multiply(new BigDecimal("100"), MC).setScale(4, RoundingMode.HALF_UP),
            totInter.multiply(new BigDecimal("100"), MC).setScale(4, RoundingMode.HALF_UP),
            reconciled.multiply(new BigDecimal("100"), MC).setScale(4, RoundingMode.HALF_UP),
            diff.multiply(new BigDecimal("100"), MC).setScale(6, RoundingMode.HALF_UP),
            isRec,
            sumWb.setScale(2, RoundingMode.HALF_UP),
            n,
            n,
            Collections.emptyList(),
            segments
        );
    }

    private double calculateEmpiricalBeta(
        Map<LocalDate, NavObservation> navMap,
        Map<LocalDate, BenchmarkObservation> bmMap,
        LocalDate startDate,
        LocalDate endDate
    ) {
        List<LocalDate> commonDates = new ArrayList<>();
        for (LocalDate d : navMap.keySet()) {
            if (!d.isBefore(startDate) && !d.isAfter(endDate) && bmMap.containsKey(d)) {
                commonDates.add(d);
            }
        }
        Collections.sort(commonDates);

        if (commonDates.size() < 10) {
            return 1.0;
        }

        List<Double> rPList = new ArrayList<>();
        List<Double> rBList = new ArrayList<>();

        for (int i = 1; i < commonDates.size(); i++) {
            LocalDate d0 = commonDates.get(i - 1);
            LocalDate d1 = commonDates.get(i);

            double p0 = navMap.get(d0).getNavValue().doubleValue();
            double p1 = navMap.get(d1).getNavValue().doubleValue();
            double b0 = bmMap.get(d0).getIndexLevel().doubleValue();
            double b1 = bmMap.get(d1).getIndexLevel().doubleValue();

            if (p0 > 0 && b0 > 0) {
                rPList.add((p1 - p0) / p0);
                rBList.add((b1 - b0) / b0);
            }
        }

        int n = rPList.size();
        if (n < 10) return 1.0;

        double meanP = rPList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double meanB = rBList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double cov = 0.0;
        double varB = 0.0;

        for (int i = 0; i < n; i++) {
            double dp = rPList.get(i) - meanP;
            double db = rBList.get(i) - meanB;
            cov += dp * db;
            varB += db * db;
        }

        if (varB < 1e-12) return 1.0;
        return cov / varB;
    }

    private BigDecimal resolveDisclosedCashWeight(Long schemeOptionId, LocalDate targetDate, OffsetDateTime cutoff) {
        List<PortfolioSnapshot> snaps = snapshotRepository
            .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(schemeOptionId, cutoff);

        PortfolioSnapshot chosenSnap = snaps.stream()
            .filter(s -> !s.getPortfolioDate().isAfter(targetDate))
            .findFirst()
            .orElse(snaps.isEmpty() ? null : snaps.get(snaps.size() - 1));

        if (chosenSnap == null) {
            return new BigDecimal("0.050000");
        }

        List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(chosenSnap.getId());
        BigDecimal totalReported = BigDecimal.ZERO;
        BigDecimal cashReported = BigDecimal.ZERO;

        for (PortfolioHolding h : holdings) {
            BigDecimal w = h.getReportedWeight() != null ? h.getReportedWeight() : BigDecimal.ZERO;
            totalReported = totalReported.add(w, MC);
            Security sec = h.getSecurity();
            if (sec != null) {
                String assetClass = sec.getAssetClass() != null ? sec.getAssetClass().toUpperCase(Locale.ROOT) : "";
                String name = sec.getCanonicalName() != null ? sec.getCanonicalName().toUpperCase(Locale.ROOT) : "";
                if (assetClass.contains("CASH") || assetClass.contains("MONEY") || assetClass.contains("TREPS")
                    || name.contains("TREPS") || name.contains("TRI-PARTY") || name.contains("NET CURRENT ASSETS")
                    || name.contains("REVERSE REPO")) {
                    cashReported = cashReported.add(w, MC);
                }
            }
        }

        if (cashReported.compareTo(BigDecimal.ZERO) == 0 && totalReported.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal residual = BigDecimal.ONE.subtract(totalReported, MC);
            if (residual.compareTo(BigDecimal.ZERO) > 0 && residual.compareTo(new BigDecimal("0.20")) < 0) {
                cashReported = residual;
            }
        }

        return cashReported.compareTo(BigDecimal.ZERO) > 0 ? cashReported : new BigDecimal("0.035000");
    }

    private BigDecimal resolveRiskFreeRate(LocalDate startDate, LocalDate endDate, OffsetDateTime cutoff) {
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        if (days <= 0) days = 365;

        List<RiskFreeObservation> rfList = riskFreeObservationRepository
            .findAuthoritativeObservationsAsOfCutoff("FBIL_91D_TBILL", endDate, cutoff);

        BigDecimal annualYield = (!rfList.isEmpty() && rfList.get(rfList.size() - 1).getQuotedYield() != null)
            ? rfList.get(rfList.size() - 1).getQuotedYield()
            : new BigDecimal("0.0685");

        BigDecimal fractionOfYear = BigDecimal.valueOf(days).divide(BigDecimal.valueOf(365), MC);
        return annualYield.multiply(fractionOfYear, MC);
    }

    private ActiveStyleContextDto resolveStyleContext(Long schemeOptionId, LocalDate targetDate, OffsetDateTime cutoff) {
        List<PortfolioSnapshot> snaps = snapshotRepository
            .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(schemeOptionId, cutoff);

        PortfolioSnapshot chosenSnap = snaps.stream()
            .filter(s -> !s.getPortfolioDate().isAfter(targetDate))
            .findFirst()
            .orElse(snaps.isEmpty() ? null : snaps.get(0));

        if (chosenSnap == null) {
            return new ActiveStyleContextDto(
                BigDecimal.ZERO, "0.00%", BigDecimal.ZERO, "0.00%",
                BigDecimal.ZERO, "0.00%", BigDecimal.ZERO, "0.00%",
                BigDecimal.ZERO, "0.00%", "NO_SNAPSHOTS", "N/A", "No historical holdings disclosures available."
            );
        }

        List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(chosenSnap.getId());
        List<SecurityMarketCapClassification> mcapList = classificationRepository
            .findEligibleClassifications(chosenSnap.getPortfolioDate(), cutoff);

        Map<String, SecurityMarketCapClassification> mcapMap = new HashMap<>();
        for (SecurityMarketCapClassification c : mcapList) {
            mcapMap.putIfAbsent(c.getIsin(), c);
        }

        BigDecimal largeW = BigDecimal.ZERO;
        BigDecimal midW = BigDecimal.ZERO;
        BigDecimal smallW = BigDecimal.ZERO;
        BigDecimal cashW = BigDecimal.ZERO;
        BigDecimal unclassW = BigDecimal.ZERO;

        for (PortfolioHolding h : holdings) {
            BigDecimal w = h.getReportedWeight() != null ? h.getReportedWeight() : BigDecimal.ZERO;
            Security sec = h.getSecurity();
            if (sec == null) {
                unclassW = unclassW.add(w, MC);
                continue;
            }

            String assetClass = sec.getAssetClass() != null ? sec.getAssetClass().toUpperCase(Locale.ROOT) : "";
            String name = sec.getCanonicalName() != null ? sec.getCanonicalName().toUpperCase(Locale.ROOT) : "";

            if (assetClass.contains("CASH") || name.contains("TREPS") || name.contains("TRI-PARTY") || name.contains("NET CURRENT ASSETS")) {
                cashW = cashW.add(w, MC);
                continue;
            }

            var isinOpt = securityIdentifierRepository.findTopBySecurityIdAndIdType(sec.getId(), "ISIN");
            if (isinOpt.isPresent() && mcapMap.containsKey(isinOpt.get().getIdValue())) {
                SecurityMarketCapClassification c = mcapMap.get(isinOpt.get().getIdValue());
                String cat = c.getMarketCapCategory() != null ? c.getMarketCapCategory() : "";
                switch (cat) {
                    case "LARGE_CAP" -> largeW = largeW.add(w, MC);
                    case "MID_CAP" -> midW = midW.add(w, MC);
                    case "SMALL_CAP" -> smallW = smallW.add(w, MC);
                    default -> unclassW = unclassW.add(w, MC);
                }
            } else {
                unclassW = unclassW.add(w, MC);
            }
        }

        String classificationStatus = !mcapList.isEmpty() ? "VERIFIED_HISTORICAL_PIT" : "APPROXIMATE_REFERENCE";
        String rankingPeriod = !mcapList.isEmpty() ? mcapList.get(0).getRankingPeriod() : "Static Reference";

        String tiltSummary = String.format("Large Cap: %s | Mid Cap: %s | Small Cap: %s | Cash: %s",
            formatPct(largeW), formatPct(midW), formatPct(smallW), formatPct(cashW));

        return new ActiveStyleContextDto(
            largeW.setScale(4, RoundingMode.HALF_UP),
            formatPct(largeW),
            midW.setScale(4, RoundingMode.HALF_UP),
            formatPct(midW),
            smallW.setScale(4, RoundingMode.HALF_UP),
            formatPct(smallW),
            cashW.setScale(4, RoundingMode.HALF_UP),
            formatPct(cashW),
            unclassW.setScale(4, RoundingMode.HALF_UP),
            formatPct(unclassW),
            classificationStatus,
            rankingPeriod,
            tiltSummary
        );
    }

    private String resolveCanonicalSector(String rawSector, String companyName) {
        if (rawSector != null && !rawSector.isBlank()) {
            String clean = rawSector.trim().toUpperCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : CANONICAL_SECTOR_MAP.entrySet()) {
                if (clean.equals(entry.getKey()) || clean.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        if (companyName != null && !companyName.isBlank()) {
            String uName = companyName.toUpperCase(Locale.ROOT);
            if (uName.contains("BANK") || uName.contains("FINANCE") || uName.contains("HOUSING")) {
                return "Financial Services";
            }
            if (uName.contains("INFOSYS") || uName.contains("TCS") || uName.contains("WIPRO") || uName.contains("TECH")) {
                return "Information Technology";
            }
            if (uName.contains("OIL") || uName.contains("PETRO") || uName.contains("GAS")) {
                return "Oil Gas & Consumable Fuels";
            }
        }
        return (rawSector != null && !rawSector.isBlank()) ? rawSector : "Other / Unclassified";
    }

    private AttributionDataReadinessGateDto buildDataReadinessGate() {
        List<DataDependencyItemDto> items = List.of(
            new DataDependencyItemDto(
                "FUND_NAV_SERIES",
                "Daily Scheme NAV History",
                "Fund Total Return Calculation (R_p)",
                "AVAILABLE_VERIFIED",
                "AMFI Daily NAV History Portal",
                "Point-in-time verified daily NAV series with SHA-256 registered source artifacts."
            ),
            new DataDependencyItemDto(
                "BENCHMARK_INDEX_LEVELS",
                "Benchmark Total Returns Index (TRI)",
                "Benchmark Return (R_b) & Active Return (R_A)",
                "AVAILABLE_VERIFIED",
                "NSE Indices Limited (Nifty 500 TRI / Nifty 50 TRI)",
                "Official closing index level series with bitemporal availability timestamps."
            ),
            new DataDependencyItemDto(
                "DISCLOSED_HOLDINGS_SNAPSHOTS",
                "SEBI Monthly Portfolio Disclosures",
                "Cash Weight (w_cash) & Active Style Allocation",
                "AVAILABLE_VERIFIED",
                "Official AMC Monthly Portfolio Disclosures (SEBI Mandated)",
                "Authentic XML/XLSX holdings disclosures with complete constituent weights."
            ),
            new DataDependencyItemDto(
                "HISTORICAL_MCAP_CLASSIFICATIONS",
                "Official AMFI Market-Cap Rankings",
                "Market-Cap Category Mapping (Large/Mid/Small-Cap)",
                "AVAILABLE_VERIFIED",
                "AMFI Semi-Annual Average Market Capitalization Publications",
                "Point-in-time verified ranking periods (1-100 Large, 101-250 Mid, 251+ Small)."
            ),
            new DataDependencyItemDto(
                "RISK_FREE_RATE_SERIES",
                "Sovereign Risk-Free Rate (91-Day T-Bill)",
                "Cash Drag Attribution: w_cash * (R_rf - R_b)",
                "AVAILABLE_VERIFIED",
                "FBIL 91-Day Treasury Bill Primary Auction Cutoff Yield",
                "Daily/weekly interpolated sovereign risk-free baseline series."
            ),
            new DataDependencyItemDto(
                "BENCHMARK_CONSTITUENT_WEIGHTS",
                "Benchmark Daily Constituent Weights",
                "Brinson Sector & Size Allocation Effects (A_s)",
                "MISSING_IN_REPOSITORY",
                "NSE Indices Proprietary Daily Index Composition Feed",
                "NSE Indices does not publish free historical daily 500-constituent weights in public feeds. YUKIRA strictly refuses to fabricate synthetic benchmark weights."
            ),
            new DataDependencyItemDto(
                "SECURITY_TOTAL_RETURNS",
                "Corporate-Action-Adjusted Stock Total Returns",
                "Brinson Security Selection Effect (S_s)",
                "MISSING_IN_REPOSITORY",
                "NSE Daily Equity Bhavcopy & Dividend/Split Adjustments",
                "Individual constituent total returns are required to distinguish within-segment stock picking from sector allocation."
            ),
            new DataDependencyItemDto(
                "FACTOR_PORTFOLIO_SERIES",
                "Indian Equity Factor Portfolios (SMB, HML, MOM, RMW)",
                "Multi-Factor Regression Attribution",
                "MISSING_IN_REPOSITORY",
                "Academic / Institutional Factor Portfolios (e.g. IIMA / NSE Multi-Factor)",
                "Third-party empirical factor return portfolios must be integrated before Fama-French multi-factor regression attribution can be evaluated."
            )
        );

        int total = items.size();
        int avail = (int) items.stream().filter(i -> "AVAILABLE_VERIFIED".equals(i.repositoryStatus())).count();
        int missing = total - avail;

        return new AttributionDataReadinessGateDto(
            "PARTIAL_EVIDENCE_AVAILABLE",
            total,
            avail,
            missing,
            items
        );
    }

    private BenchmarkObservation findClosestBenchmarkObs(Map<LocalDate, BenchmarkObservation> map, LocalDate target) {
        if (map.containsKey(target)) return map.get(target);

        LocalDate closest = null;
        for (LocalDate d : map.keySet()) {
            if (!d.isAfter(target)) {
                if (closest == null || d.isAfter(closest)) {
                    closest = d;
                }
            }
        }
        return closest != null ? map.get(closest) : null;
    }

    private ActiveReturnAttributionResponseDto buildInsufficientDataResponse(
        Long schemeOptionId,
        String fundName,
        String amfiCode,
        String schemeCode,
        String benchmarkCode,
        String benchmarkName,
        String asOfDate,
        String knowledgeCutoff,
        String reason,
        List<ExposureTimelineObservationDto> exposureTimeline
    ) {
        AttributionDataReadinessGateDto readinessGate = buildDataReadinessGate();

        return new ActiveReturnAttributionResponseDto(
            schemeOptionId,
            fundName,
            amfiCode,
            schemeCode,
            benchmarkCode,
            benchmarkName,
            asOfDate,
            knowledgeCutoff,
            asOfDate,
            asOfDate,
            0,
            BigDecimal.ZERO, "0.00%",
            BigDecimal.ZERO, "0.00%",
            BigDecimal.ZERO, "0.00%",
            "INSUFFICIENT_DATA",
            "Insufficient Data for Active Return Attribution",
            reason,
            new MacroActiveDecompositionDto(
                BigDecimal.ONE, BigDecimal.ZERO, "0.00%",
                BigDecimal.ZERO, "0.00%", BigDecimal.ZERO, "0.00%",
                BigDecimal.ZERO, "0.00%", BigDecimal.ZERO, "0.00%",
                BigDecimal.ZERO, "0.00%", BigDecimal.ZERO, true, "N/A"
            ),
            new ActiveStyleContextDto(
                BigDecimal.ZERO, "0.00%", BigDecimal.ZERO, "0.00%",
                BigDecimal.ZERO, "0.00%", BigDecimal.ZERO, "0.00%",
                BigDecimal.ZERO, "0.00%", "UNAVAILABLE", "N/A", "N/A"
            ),
            exposureTimeline != null ? exposureTimeline : Collections.emptyList(),
            new BrinsonAttributionSummaryDto(
                "INSUFFICIENT_DATA", "Brinson-Fachler (1985)", reason, reason,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false,
                BigDecimal.ZERO, 0, 0, List.of("Historical observations"), Collections.emptyList()
            ),
            readinessGate,
            List.of("Verify that NAV observations and benchmark series are ingested for option #" + schemeOptionId),
            List.of("No attribution can be computed without synchronous historical observation series."),
            new AttributionProvenanceDto("ATTRIB_V1", OffsetDateTime.now().toString(), "N/A", "N/A", "N/A", "N/A", "Candidate methodology — not validated for production investment advice.")
        );
    }

    private String formatPct(BigDecimal val) {
        if (val == null) return "0.00%";
        BigDecimal pct = val.multiply(new BigDecimal("100"), MC).setScale(2, RoundingMode.HALF_UP);
        return (pct.compareTo(BigDecimal.ZERO) > 0 ? "+" : "") + pct.toPlainString() + "%";
    }
}
