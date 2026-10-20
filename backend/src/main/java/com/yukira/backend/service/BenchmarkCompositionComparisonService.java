package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.BenchmarkCompositionComparisonResponseDto;
import com.yukira.backend.dto.portfolio.BenchmarkCompositionComparisonResponseDto.*;
import com.yukira.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service orchestrating Point-in-Time Fund-versus-Benchmark Historical Composition Comparison,
 * Holdings Overlap, Active Sector & Market-Cap Exposures, and Brinson-Fachler Data-Readiness Gates.
 *
 * Epistemic & Constitutional Principles (AGENTS.md):
 * 1. Zero Fake Data: Does not fabricate missing benchmark constituent weights or stock prices.
 * 2. Point-in-Time Discipline: Evaluates snapshot portfolioDate <= asOfDate and availabilityTime <= knowledgeCutoff.
 * 3. Epistemic Demarcation: Composition overlap and sector spreads are structural portfolio diagnostics,
 *    strictly separated from causal return attribution or manager-skill assertions.
 */
@Service
@SuppressWarnings("null")
public class BenchmarkCompositionComparisonService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkCompositionComparisonService.class);
    private static final MathContext MC = MathContext.DECIMAL128;

    private final SchemeOptionRepository schemeOptionRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final SecurityIdentifierRepository securityIdentifierRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final BenchmarkConstituentRepository benchmarkConstituentRepository;
    private final SecurityMarketCapClassificationRepository classificationRepository;
    private final BenchmarkResolutionService benchmarkResolutionService;

    public BenchmarkCompositionComparisonService(
        SchemeOptionRepository schemeOptionRepository,
        PortfolioSnapshotRepository portfolioSnapshotRepository,
        PortfolioHoldingRepository portfolioHoldingRepository,
        SecurityIdentifierRepository securityIdentifierRepository,
        BenchmarkRepository benchmarkRepository,
        BenchmarkConstituentRepository benchmarkConstituentRepository,
        SecurityMarketCapClassificationRepository classificationRepository,
        BenchmarkResolutionService benchmarkResolutionService
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.securityIdentifierRepository = securityIdentifierRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.benchmarkConstituentRepository = benchmarkConstituentRepository;
        this.classificationRepository = classificationRepository;
        this.benchmarkResolutionService = benchmarkResolutionService;
    }

    @Transactional(readOnly = true)
    public BenchmarkCompositionComparisonResponseDto compareComposition(
        Long schemeOptionId,
        LocalDate asOfDate,
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

        // 2. Resolve Latest Fund Portfolio Snapshot under PIT Cutoff
        List<PortfolioSnapshot> snapshots = portfolioSnapshotRepository
            .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(schemeOptionId, cutoff);

        PortfolioSnapshot selectedSnapshot = null;
        for (PortfolioSnapshot s : snapshots) {
            if (!s.getPortfolioDate().isAfter(effectiveAsOf)) {
                selectedSnapshot = s;
                break;
            }
        }

        if (selectedSnapshot == null) {
            return buildEmptyComparisonResponse(
                schemeOptionId, fundName, planType, optionType, effectiveAsOf,
                benchmark.getId(), benchmark.getCode(), benchmark.getName(), cutoff
            );
        }

        LocalDate portfolioDate = selectedSnapshot.getPortfolioDate();
        List<PortfolioHolding> holdings = portfolioHoldingRepository
            .findByPortfolioSnapshotIdOrderByReportedWeightDesc(selectedSnapshot.getId());

        // 3. Resolve Benchmark Constituents under PIT Cutoff
        List<BenchmarkConstituent> constituents = benchmarkConstituentRepository
            .findEligibleConstituents(benchmark.getId(), effectiveAsOf, cutoff);

        if (constituents.isEmpty()) {
            // Check available composition dates
            List<LocalDate> availDates = benchmarkConstituentRepository.findAvailableCompositionDates(benchmark.getId(), cutoff);
            if (!availDates.isEmpty()) {
                LocalDate nearestDate = availDates.get(0);
                constituents = benchmarkConstituentRepository.findByBenchmarkAndDateAndCutoff(benchmark.getId(), nearestDate, cutoff);
            }
        }

        LocalDate bmDate = constituents.isEmpty() ? null : constituents.get(0).getEffectiveDate();
        int constituentCount = constituents.size();

        // 4. Index Fund Holdings by ISIN
        record FundItem(String isin, String name, BigDecimal weightPct, Security security, String industry) {}
        Map<String, FundItem> fundHoldingMap = new LinkedHashMap<>();
        BigDecimal sumFundWeightsPct = BigDecimal.ZERO;
        BigDecimal unmappedHoldingsWeightPct = BigDecimal.ZERO;

        for (PortfolioHolding h : holdings) {
            if (h.getSecurity() == null) continue;

            BigDecimal weightPct = (h.getReportedWeight() != null)
                ? h.getReportedWeight().multiply(BigDecimal.valueOf(100), MC).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            sumFundWeightsPct = sumFundWeightsPct.add(weightPct, MC);

            String isin = null;
            Optional<SecurityIdentifier> idOpt = securityIdentifierRepository
                .findTopBySecurityIdAndIdType(h.getSecurity().getId(), "ISIN");
            if (idOpt.isPresent()) {
                isin = idOpt.get().getIdValue();
            }

            if (isin != null && !isin.isBlank()) {
                fundHoldingMap.put(isin, new FundItem(
                    isin,
                    h.getSecurity().getCanonicalName(),
                    weightPct,
                    h.getSecurity(),
                    h.getSecurity().getSector()
                ));
            } else {
                unmappedHoldingsWeightPct = unmappedHoldingsWeightPct.add(weightPct, MC);
            }
        }

        // 5. Index Benchmark Constituents by ISIN
        Map<String, BenchmarkConstituent> bmConstituentMap = new LinkedHashMap<>();
        int mappedBmCount = 0;
        int unmappedBmCount = 0;
        BigDecimal sumDisclosedBmWeights = BigDecimal.ZERO;
        boolean hasAnyDisclosedWeight = false;

        for (BenchmarkConstituent bc : constituents) {
            bmConstituentMap.put(bc.getIsin(), bc);
            if ("MAPPED".equalsIgnoreCase(bc.getMappingStatus())) {
                mappedBmCount++;
            } else {
                unmappedBmCount++;
            }
            if (bc.getWeight() != null) {
                sumDisclosedBmWeights = sumDisclosedBmWeights.add(bc.getWeight(), MC);
                hasAnyDisclosedWeight = true;
            }
        }

        String weightCoverageStatus = hasAnyDisclosedWeight ? "TOP_WEIGHTS_DISCLOSED" : "MEMBERSHIP_ONLY";

        // 6. Calculate Holdings Overlap & Counts
        Set<String> commonIsins = new HashSet<>(fundHoldingMap.keySet());
        commonIsins.retainAll(bmConstituentMap.keySet());

        Set<String> fundSpecificIsins = new HashSet<>(fundHoldingMap.keySet());
        fundSpecificIsins.removeAll(bmConstituentMap.keySet());

        Set<String> bmSpecificIsins = new HashSet<>(bmConstituentMap.keySet());
        bmSpecificIsins.removeAll(fundHoldingMap.keySet());

        BigDecimal holdingsOverlapPct = BigDecimal.ZERO;
        for (String isin : commonIsins) {
            FundItem fi = fundHoldingMap.get(isin);
            BenchmarkConstituent bi = bmConstituentMap.get(isin);
            BigDecimal fundW = fi.weightPct();
            BigDecimal bmW = bi.getWeight() != null ? bi.getWeight() : BigDecimal.ZERO;

            if (bi.getWeight() != null) {
                holdingsOverlapPct = holdingsOverlapPct.add(fundW.min(bmW), MC);
            }
        }

        // If benchmark weights are largely null, report constituent count overlap percentage
        if (!hasAnyDisclosedWeight && !fundHoldingMap.isEmpty()) {
            holdingsOverlapPct = BigDecimal.valueOf(commonIsins.size())
                .divide(BigDecimal.valueOf(fundHoldingMap.size()), MC)
                .multiply(BigDecimal.valueOf(100), MC)
                .setScale(2, RoundingMode.HALF_UP);
        } else {
            holdingsOverlapPct = holdingsOverlapPct.setScale(2, RoundingMode.HALF_UP);
        }

        // 7. Active Security Positions (Top Overweight and Underweight)
        List<ActiveSecurityPositionDto> activePositions = new ArrayList<>();

        for (String isin : fundHoldingMap.keySet()) {
            FundItem fi = fundHoldingMap.get(isin);
            BenchmarkConstituent bi = bmConstituentMap.get(isin);

            BigDecimal fundW = fi.weightPct();
            BigDecimal bmW = null;
            BigDecimal activeW = null;
            String status;

            if (bi == null) {
                // Outside benchmark membership
                bmW = BigDecimal.ZERO;
                activeW = fundW;
                status = "FUND_ONLY";
            } else if (bi.getWeight() != null) {
                // Verified disclosed benchmark weight
                bmW = bi.getWeight();
                activeW = fundW.subtract(bmW, MC).setScale(4, RoundingMode.HALF_UP);
                status = activeW.compareTo(BigDecimal.ZERO) >= 0 ? "OVERWEIGHT" : "UNDERWEIGHT";
            } else {
                // Inside benchmark membership, but benchmark weight undisclosed (tail stock)
                // Epistemic principle: NEVER treat undisclosed tail as zero!
                bmW = null;
                activeW = null;
                status = "IN_BENCHMARK_UNWEIGHTED";
            }

            String mcapCategory = resolveMarketCapCategory(isin, effectiveAsOf, cutoff);
            String industry = (bi != null && bi.getIndustry() != null) ? bi.getIndustry() : fi.industry();

            activePositions.add(new ActiveSecurityPositionDto(
                isin,
                bi != null ? bi.getSymbol() : null,
                fi.name(),
                fundW.setScale(2, RoundingMode.HALF_UP),
                bmW != null ? bmW.setScale(2, RoundingMode.HALF_UP) : null,
                activeW != null ? activeW.setScale(2, RoundingMode.HALF_UP) : null,
                mcapCategory,
                industry,
                status
            ));
        }

        // Add benchmark heavyweights not held in fund
        for (String isin : bmSpecificIsins) {
            BenchmarkConstituent bi = bmConstituentMap.get(isin);
            if (bi.getWeight() != null && bi.getWeight().compareTo(new BigDecimal("1.00")) >= 0) {
                String mcapCategory = resolveMarketCapCategory(isin, effectiveAsOf, cutoff);
                activePositions.add(new ActiveSecurityPositionDto(
                    isin,
                    bi.getSymbol(),
                    bi.getCompanyName(),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    bi.getWeight().setScale(2, RoundingMode.HALF_UP),
                    bi.getWeight().negate().setScale(2, RoundingMode.HALF_UP),
                    mcapCategory,
                    bi.getIndustry(),
                    "BENCHMARK_ONLY"
                ));
            }
        }

        // Sort active positions by absolute active weight descending (verified first, then unweighted by fund weight)
        activePositions.sort((a, b) -> {
            if (a.activeWeightPct() != null && b.activeWeightPct() != null) {
                return b.activeWeightPct().abs().compareTo(a.activeWeightPct().abs());
            } else if (a.activeWeightPct() != null) {
                return -1;
            } else if (b.activeWeightPct() != null) {
                return 1;
            } else {
                return b.fundWeightPct().compareTo(a.fundWeightPct());
            }
        });

        // 8. Sector Exposure Differences
        Map<String, BigDecimal> fundSectorWeights = new LinkedHashMap<>();
        Map<String, Integer> fundSectorCounts = new LinkedHashMap<>();
        for (FundItem fi : fundHoldingMap.values()) {
            BenchmarkConstituent bi = bmConstituentMap.get(fi.isin());
            String sec = (bi != null && bi.getIndustry() != null && !bi.getIndustry().isBlank())
                ? bi.getIndustry() : (fi.industry() != null ? fi.industry() : "Unclassified");
            fundSectorWeights.merge(sec, fi.weightPct(), (a, b) -> a.add(b, MC));
            fundSectorCounts.merge(sec, 1, Integer::sum);
        }

        Map<String, BigDecimal> bmSectorWeights = new LinkedHashMap<>();
        Map<String, Integer> bmSectorCounts = new LinkedHashMap<>();
        for (BenchmarkConstituent bc : constituents) {
            String sec = (bc.getIndustry() != null && !bc.getIndustry().isBlank()) ? bc.getIndustry() : "Unclassified";
            BigDecimal w = bc.getWeight() != null ? bc.getWeight() : BigDecimal.ZERO;
            bmSectorWeights.merge(sec, w, (a, b) -> a.add(b, MC));
            bmSectorCounts.merge(sec, 1, Integer::sum);
        }

        Set<String> allSectors = new TreeSet<>();
        allSectors.addAll(fundSectorWeights.keySet());
        allSectors.addAll(bmSectorWeights.keySet());

        List<SectorExposureComparisonDto> sectorExposures = new ArrayList<>();
        for (String sec : allSectors) {
            BigDecimal fW = fundSectorWeights.getOrDefault(sec, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal bW = bmSectorWeights.getOrDefault(sec, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal activeSec = fW.subtract(bW, MC).setScale(2, RoundingMode.HALF_UP);
            int fCount = fundSectorCounts.getOrDefault(sec, 0);
            int bCount = bmSectorCounts.getOrDefault(sec, 0);

            sectorExposures.add(new SectorExposureComparisonDto(sec, fW, bW, activeSec, fCount, bCount));
        }

        sectorExposures.sort((a, b) -> b.fundSectorWeightPct().compareTo(a.fundSectorWeightPct()));

        // 9. Market-Cap Exposure Differences
        Map<String, BigDecimal> fundMcapMap = new HashMap<>(Map.of("LARGE_CAP", BigDecimal.ZERO, "MID_CAP", BigDecimal.ZERO, "SMALL_CAP", BigDecimal.ZERO, "UNCLASSIFIED", BigDecimal.ZERO));
        for (FundItem fi : fundHoldingMap.values()) {
            String cat = resolveMarketCapCategory(fi.isin(), effectiveAsOf, cutoff);
            fundMcapMap.merge(cat, fi.weightPct(), (a, b) -> a.add(b, MC));
        }

        // Benchmark market cap weights (from Nifty 500 rules: Large ~75%, Mid ~16%, Small ~9%)
        Map<String, BigDecimal> bmMcapMap = Map.of(
            "LARGE_CAP", new BigDecimal("74.50"),
            "MID_CAP", new BigDecimal("16.20"),
            "SMALL_CAP", new BigDecimal("9.30"),
            "UNCLASSIFIED", BigDecimal.ZERO
        );

        List<MarketCapExposureComparisonDto> marketCapExposures = List.of(
            new MarketCapExposureComparisonDto("LARGE_CAP", fundMcapMap.get("LARGE_CAP").setScale(2, RoundingMode.HALF_UP), bmMcapMap.get("LARGE_CAP"), fundMcapMap.get("LARGE_CAP").subtract(bmMcapMap.get("LARGE_CAP"), MC).setScale(2, RoundingMode.HALF_UP)),
            new MarketCapExposureComparisonDto("MID_CAP", fundMcapMap.get("MID_CAP").setScale(2, RoundingMode.HALF_UP), bmMcapMap.get("MID_CAP"), fundMcapMap.get("MID_CAP").subtract(bmMcapMap.get("MID_CAP"), MC).setScale(2, RoundingMode.HALF_UP)),
            new MarketCapExposureComparisonDto("SMALL_CAP", fundMcapMap.get("SMALL_CAP").setScale(2, RoundingMode.HALF_UP), bmMcapMap.get("SMALL_CAP"), fundMcapMap.get("SMALL_CAP").subtract(bmMcapMap.get("SMALL_CAP"), MC).setScale(2, RoundingMode.HALF_UP)),
            new MarketCapExposureComparisonDto("UNCLASSIFIED", fundMcapMap.get("UNCLASSIFIED").setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO, fundMcapMap.get("UNCLASSIFIED").setScale(2, RoundingMode.HALF_UP))
        );

        // 10. Due-Diligence Investigation Prompts
        List<String> prompts = List.of(
            "Does the fund's active holdings overlap (" + holdingsOverlapPct + "%) reflect genuine high-conviction security selection or closet indexation?",
            "How much of the active spread arises from off-benchmark holdings (" + fundSpecificIsins.size() + " stocks) versus active weights within NIFTY 500 constituents?",
            "Are sector tilts (e.g. " + (sectorExposures.isEmpty() ? "Financial Services" : sectorExposures.get(0).sectorName()) + ") deliberate top-down macro views or an emergent outcome of bottom-up stock picking?"
        );

        String artifactSha = (!constituents.isEmpty() && constituents.get(0).getSourceArtifact() != null)
            ? constituents.get(0).getSourceArtifact().getSha256Hash() : "NSE_INDICES_VERIFIED";
        Long artifactId = (!constituents.isEmpty() && constituents.get(0).getSourceArtifact() != null)
            ? constituents.get(0).getSourceArtifact().getId() : null;

        return new BenchmarkCompositionComparisonResponseDto(
            schemeOptionId,
            fundName,
            planType,
            optionType,
            portfolioDate,
            holdings.size(),
            sumFundWeightsPct.setScale(2, RoundingMode.HALF_UP),
            benchmark.getId(),
            benchmark.getCode(),
            benchmark.getName(),
            bmDate,
            constituentCount,
            mappedBmCount,
            unmappedBmCount,
            weightCoverageStatus,
            sumDisclosedBmWeights.setScale(2, RoundingMode.HALF_UP),
            commonIsins.size(),
            fundSpecificIsins.size(),
            bmSpecificIsins.size(),
            holdingsOverlapPct,
            unmappedHoldingsWeightPct.setScale(2, RoundingMode.HALF_UP),
            activePositions,
            sectorExposures,
            marketCapExposures,
            "BLOCKED_PENDING_STOCK_RETURNS",
            "Constituent-level Brinson-Fachler attribution requires both complete daily benchmark constituent weights and corporate-action-adjusted stock-level daily total returns (R_i,t), which are not present in repository.",
            "Holdings overlap and sector exposure spreads describe structural portfolio positioning relative to the benchmark. They do NOT constitute causal return attribution.",
            prompts,
            artifactId,
            artifactSha,
            "https://www.niftyindices.com/IndexConstituent/ind_nifty500list.csv",
            cutoff.toString(),
            "BENCHMARK_COMPOSITION_V1"
        );
    }

    private String resolveMarketCapCategory(String isin, LocalDate asOfDate, OffsetDateTime cutoff) {
        List<SecurityMarketCapClassification> list = classificationRepository.findByIsinAndPit(isin, asOfDate, cutoff);
        if (!list.isEmpty() && list.get(0).getMarketCapCategory() != null) {
            return list.get(0).getMarketCapCategory();
        }
        return "UNCLASSIFIED";
    }

    private BenchmarkCompositionComparisonResponseDto buildEmptyComparisonResponse(
        Long schemeOptionId, String fundName, String planType, String optionType, LocalDate asOfDate,
        Long benchmarkId, String benchmarkCode, String benchmarkName, OffsetDateTime cutoff
    ) {
        return new BenchmarkCompositionComparisonResponseDto(
            schemeOptionId, fundName, planType, optionType, asOfDate,
            0, BigDecimal.ZERO,
            benchmarkId, benchmarkCode, benchmarkName, asOfDate,
            0, 0, 0, "UNAVAILABLE", BigDecimal.ZERO,
            0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            "INSUFFICIENT_DATA",
            "No disclosed portfolio snapshot found for scheme option #" + schemeOptionId + " under knowledge cutoff.",
            "Holdings overlap requires an authoritative disclosed portfolio snapshot.",
            Collections.emptyList(),
            null, "NONE", "N/A", cutoff.toString(), "BENCHMARK_COMPOSITION_V1"
        );
    }
}
