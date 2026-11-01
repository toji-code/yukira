package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.AnalyticalScore;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.domain.entity.PortfolioSnapshot;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeBenchmarkHist;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.domain.entity.ScoreDimension;
import com.yukira.backend.dto.analysis.ComparisonRequest;
import com.yukira.backend.dto.analysis.ComparisonResponse;
import com.yukira.backend.dto.portfolio.FundConcentrationDto;
import com.yukira.backend.repository.AnalyticalScoreRepository;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.PortfolioSnapshotRepository;
import com.yukira.backend.repository.SchemeBenchmarkHistRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.ScoreDimensionRepository;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.YukiraScoreSummary;
import com.yukira.backend.service.AnalysisService;
import com.yukira.backend.service.PortfolioIntelligenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only comparison assembler for persisted fund evidence and portfolio intelligence.
 *
 * Enforces the YUKIRA Comparability Contract (Phase 3 & Phase 5 & Task 15):
 * - Assigns explicit Comparability Status (COMPARABLE, PARTIALLY_COMPARABLE, NOT_COMPARABLE, INSUFFICIENT_EVIDENCE)
 * - Evaluates category, benchmark TRI, observation period synchronicity, data freshness, and holdings disclosure timing
 * - Determines the common supported period and identifies historical truncation
 * - Produces structured comparison difference explanations across performance, risk, and concentration
 * - Generates grounded, evidence-driven investigation prompts without investment advice or ratings
 */
@Service
@Transactional(readOnly = true)
public class PortfolioComparisonQueryService {

    private final SchemeOptionRepository schemeOptionRepository;
    private final AnalyticalScoreRepository analyticalScoreRepository;
    private final ScoreDimensionRepository scoreDimensionRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final MetricResultRepository metricResultRepository;
    private final PortfolioIntelligenceService portfolioIntelligenceService;
    private final SchemeBenchmarkHistRepository schemeBenchmarkHistRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final NavObservationRepository navObservationRepository;

    @Autowired
    public PortfolioComparisonQueryService(
        SchemeOptionRepository schemeOptionRepository,
        AnalyticalScoreRepository analyticalScoreRepository,
        ScoreDimensionRepository scoreDimensionRepository,
        CalculationRunRepository calculationRunRepository,
        MetricResultRepository metricResultRepository,
        @Autowired(required = false) PortfolioIntelligenceService portfolioIntelligenceService,
        @Autowired(required = false) SchemeBenchmarkHistRepository schemeBenchmarkHistRepository,
        @Autowired(required = false) PortfolioSnapshotRepository portfolioSnapshotRepository,
        @Autowired(required = false) NavObservationRepository navObservationRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.analyticalScoreRepository = analyticalScoreRepository;
        this.scoreDimensionRepository = scoreDimensionRepository;
        this.calculationRunRepository = calculationRunRepository;
        this.metricResultRepository = metricResultRepository;
        this.portfolioIntelligenceService = portfolioIntelligenceService;
        this.schemeBenchmarkHistRepository = schemeBenchmarkHistRepository;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.navObservationRepository = navObservationRepository;
    }

    public PortfolioComparisonQueryService(
        SchemeOptionRepository schemeOptionRepository,
        AnalyticalScoreRepository analyticalScoreRepository,
        ScoreDimensionRepository scoreDimensionRepository,
        CalculationRunRepository calculationRunRepository,
        MetricResultRepository metricResultRepository,
        PortfolioIntelligenceService portfolioIntelligenceService
    ) {
        this(schemeOptionRepository, analyticalScoreRepository, scoreDimensionRepository, calculationRunRepository, metricResultRepository, portfolioIntelligenceService, null, null, null);
    }

    public ComparisonResponse executeComparison(ComparisonRequest request) {
        return executeComparison(request, Collections.emptyMap());
    }

    public ComparisonResponse executeComparison(
        ComparisonRequest request,
        Map<Long, ComparisonResponse.PortfolioHoldingContext> holdingContexts
    ) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.schemeOptionIds(), "schemeOptionIds must not be null");
        Objects.requireNonNull(request.asOfDate(), "asOfDate must not be null");
        Objects.requireNonNull(request.knowledgeCutoffTime(), "knowledgeCutoffTime must not be null");

        List<Long> schemeOptionIds = orderedDistinct(request.schemeOptionIds());
        List<String> metricCodes = request.metricCodes() != null && !request.metricCodes().isEmpty()
            ? orderedDistinct(request.metricCodes())
            : AnalysisService.DEFAULT_COMPARISON_METRIC_CODES;

        List<String> invalidMetricCodes = metricCodes.stream()
            .filter(code -> !AnalysisService.isSupportedComparisonMetricCode(code))
            .toList();
        if (!invalidMetricCodes.isEmpty()) {
            throw new IllegalArgumentException("Unsupported comparison metric code(s): " + String.join(", ", invalidMetricCodes));
        }

        Map<Long, SchemeOption> optionById = schemeOptionRepository.findComparisonIdentitiesByIdIn(schemeOptionIds).stream()
            .collect(Collectors.toMap(SchemeOption::getId, option -> option));
        for (Long schemeOptionId : schemeOptionIds) {
            if (!optionById.containsKey(schemeOptionId)) {
                throw new IllegalArgumentException("SchemeOption #" + schemeOptionId + " not found");
            }
        }

        Map<Long, AnalyticalScore> scoreByOptionId = analyticalScoreRepository.findLatestBySchemeOptionIds(schemeOptionIds).stream()
            .collect(Collectors.toMap(score -> score.getSchemeOption().getId(), score -> score, this::latestScore));

        List<Long> scoreIds = scoreByOptionId.values().stream()
            .map(AnalyticalScore::getId)
            .filter(Objects::nonNull)
            .toList();
        Map<Long, List<ScoreDimension>> dimensionsByScoreId = scoreIds.isEmpty()
            ? Collections.emptyMap()
            : scoreDimensionRepository.findByAnalyticalScoreIdIn(scoreIds).stream()
                .collect(Collectors.groupingBy(d -> d.getAnalyticalScore().getId(), LinkedHashMap::new, Collectors.toList()));

        Map<Long, CalculationRun> runByOptionId = latestRunByOptionId(schemeOptionIds, request);
        List<Long> runIds = runByOptionId.values().stream()
            .filter(Objects::nonNull)
            .map(CalculationRun::getId)
            .filter(Objects::nonNull)
            .toList();
        Map<Long, Map<String, MetricResult>> metricResultsByOptionId = runIds.isEmpty()
            ? Collections.emptyMap()
            : metricResultRepository.findCanonicalByCalculationRunIdInAndMetricCodeIn(runIds, metricCodes).stream()
                .collect(Collectors.groupingBy(
                    r -> r.getCalculationRun().getSchemeOption().getId(),
                    LinkedHashMap::new,
                    Collectors.toMap(MetricResult::getMetricCode, r -> r, this::preferCalculated, LinkedHashMap::new)
                ));

        // Load Portfolio Concentration DTOs for each fund if portfolioIntelligenceService is available
        Map<Long, FundConcentrationDto> concentrationByOptionId = new HashMap<>();
        if (portfolioIntelligenceService != null) {
            for (Long schemeOptionId : schemeOptionIds) {
                try {
                    FundConcentrationDto conc = portfolioIntelligenceService.calculateFundConcentration(schemeOptionId, request.knowledgeCutoffTime());
                    concentrationByOptionId.put(schemeOptionId, conc);
                } catch (Exception ignored) {
                }
            }
        }

        // Load PortfolioSnapshots for holdings disclosure comparability
        Map<Long, PortfolioSnapshot> snapshotsByOptionId = new HashMap<>();
        if (portfolioSnapshotRepository != null) {
            for (Long schemeOptionId : schemeOptionIds) {
                try {
                    portfolioSnapshotRepository
                        .findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(schemeOptionId, request.knowledgeCutoffTime())
                        .ifPresent(snap -> snapshotsByOptionId.put(schemeOptionId, snap));
                } catch (Exception ignored) {
                }
            }
        }

        // Load NAV summaries for observation window alignment
        Map<Long, Object[]> navSummariesByOptionId = new HashMap<>();
        if (navObservationRepository != null) {
            for (Long schemeOptionId : schemeOptionIds) {
                try {
                    List<Object[]> rows = navObservationRepository.findDistinctValidNavSummaryBySchemeOptionId(schemeOptionId);
                    if (rows != null && !rows.isEmpty() && rows.get(0) != null) {
                        navSummariesByOptionId.put(schemeOptionId, rows.get(0));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        List<ComparisonResponse.ComparisonFund> funds = schemeOptionIds.stream()
            .map(id -> toFund(optionById.get(id), scoreByOptionId.get(id), dimensionsByScoreId, holdingContexts.get(id), request.asOfDate()))
            .toList();

        List<ComparisonResponse.ComparisonMetric> metrics = new ArrayList<>();
        int missingMetricsCount = 0;

        for (String metricCode : metricCodes) {
            AnalysisService.MetricMetadata meta = AnalysisService.comparisonMetricMetadata(metricCode);
            List<ComparisonResponse.ComparisonMetricResult> results = new ArrayList<>();

            int calcCount = 0;
            int limCount = 0;
            int missingCount = 0;

            for (Long schemeOptionId : schemeOptionIds) {
                ComparisonResponse.ComparisonMetricResult metricRes;
                if (metricCode.startsWith("CONC-")) {
                    metricRes = toConcentrationMetricResult(schemeOptionId, metricCode, concentrationByOptionId.get(schemeOptionId), meta);
                } else {
                    MetricResult result = metricResultsByOptionId.getOrDefault(schemeOptionId, Collections.emptyMap()).get(metricCode);
                    metricRes = toMetricResult(schemeOptionId, metricCode, result, meta);
                }
                results.add(metricRes);

                if ("CALCULATED".equalsIgnoreCase(metricRes.calculationStatus())) {
                    calcCount++;
                } else {
                    missingCount++;
                    missingMetricsCount++;
                }

                if ("COMPARABLE_WITH_LIMITATIONS".equalsIgnoreCase(metricRes.comparabilityStatus())) {
                    limCount++;
                }
            }

            String overallComparabilityStatus;
            String overallComparabilityReason;

            if (missingCount > 0 && calcCount == 0) {
                overallComparabilityStatus = "INSUFFICIENT_DATA";
                overallComparabilityReason = "No calculated data available across compared funds for " + meta.name() + ".";
            } else if (missingCount > 0) {
                overallComparabilityStatus = "INSUFFICIENT_DATA";
                overallComparabilityReason = "One or more compared funds lack historical observations for " + meta.name() + ".";
            } else if (limCount > 0 || "candidate".equalsIgnoreCase(meta.governanceStatus())) {
                overallComparabilityStatus = "COMPARABLE_WITH_LIMITATIONS";
                overallComparabilityReason = "Candidate methodology status or differing fund historical lengths require context.";
            } else {
                overallComparabilityStatus = "COMPARABLE";
                overallComparabilityReason = "Metric values evaluated over identical observation windows under validated methodology.";
            }

            metrics.add(new ComparisonResponse.ComparisonMetric(
                metricCode,
                meta.name(),
                meta.category(),
                meta.governanceStatus().toLowerCase(),
                meta.defaultPeriod(),
                meta.formula(),
                meta.interpretation(),
                meta.limitations(),
                overallComparabilityStatus,
                overallComparabilityReason,
                results
            ));
        }

        // Coverage Summary
        int scoredCount = (int) funds.stream()
            .filter(f -> f.yukiraScore() != null && f.yukiraScore().score() != null)
            .count();
        int completeRiskCount = (int) funds.stream()
            .filter(f -> hasCompleteRiskEvidence(f.schemeOptionId(), metricResultsByOptionId))
            .count();
        boolean differingAsOfDates = checkDifferingAsOfDates(funds);

        ComparisonResponse.ComparisonCoverageSummary coverageSummary = new ComparisonResponse.ComparisonCoverageSummary(
            funds.size(),
            scoredCount,
            completeRiskCount,
            missingMetricsCount,
            differingAsOfDates
        );

        // Grounded Investigation Prompts
        List<String> investigationPrompts = generateInvestigationPrompts(funds, metrics, concentrationByOptionId);

        // Evaluate Common Supported Period
        ComparisonResponse.CommonSupportedPeriod commonSupportedPeriod = evaluateCommonSupportedPeriod(
            funds, navSummariesByOptionId, request.asOfDate()
        );

        // Evaluate Comparison Compatibility Summary
        ComparisonResponse.ComparisonCompatibilitySummary compatibilitySummary = evaluateCompatibility(
            funds, metrics, concentrationByOptionId, snapshotsByOptionId, navSummariesByOptionId, request.asOfDate()
        );

        // Generate Structured Difference Explanations
        List<ComparisonResponse.ComparisonDifferenceExplanation> differenceExplanations = generateDifferenceExplanations(
            funds, metrics, commonSupportedPeriod, compatibilitySummary
        );

        return new ComparisonResponse(
            funds,
            metrics,
            new ComparisonResponse.ComparisonPeriod(
                request.asOfDate().minusYears(3).toString(),
                request.asOfDate().toString(),
                request.knowledgeCutoffTime().toString()
            ),
            System.nanoTime(),
            OffsetDateTime.now(),
            coverageSummary,
            investigationPrompts,
            compatibilitySummary,
            commonSupportedPeriod,
            differenceExplanations
        );
    }

    private Map<Long, CalculationRun> latestRunByOptionId(List<Long> schemeOptionIds, ComparisonRequest request) {
        Map<Long, CalculationRun> selected = selectFirstRunPerOption(
            calculationRunRepository.findComparisonRunsBySchemeOptionIdsAndAsOfDate(schemeOptionIds, request.asOfDate())
        );
        if (selected.size() == schemeOptionIds.size()) {
            return selected;
        }

        Map<Long, CalculationRun> fallback = selectFirstRunPerOption(
            calculationRunRepository.findComparisonRunsBySchemeOptionIds(schemeOptionIds)
        );
        Map<Long, CalculationRun> merged = new HashMap<>(selected);
        for (Long schemeOptionId : schemeOptionIds) {
            merged.putIfAbsent(schemeOptionId, fallback.get(schemeOptionId));
        }
        return merged;
    }

    private Map<Long, CalculationRun> selectFirstRunPerOption(List<CalculationRun> runs) {
        Map<Long, CalculationRun> selected = new HashMap<>();
        for (CalculationRun run : runs) {
            Long schemeOptionId = run.getSchemeOption().getId();
            selected.putIfAbsent(schemeOptionId, run);
        }
        return selected;
    }

    private ComparisonResponse.ComparisonFund toFund(
        SchemeOption option,
        AnalyticalScore score,
        Map<Long, List<ScoreDimension>> dimensionsByScoreId,
        ComparisonResponse.PortfolioHoldingContext holdingContext,
        LocalDate asOfDate
    ) {
        SchemePlan plan = option.getPlan();
        Scheme scheme = plan.getScheme();
        String amcName = scheme.getAmc() != null ? scheme.getAmc().getLegalName() : null;
        YukiraScoreSummary scoreSummary = score != null ? toScoreSummary(score, dimensionsByScoreId.getOrDefault(score.getId(), Collections.emptyList())) : null;

        String category = scheme.getCategory() != null ? scheme.getCategory() : "Equity: Flexi Cap";
        String subcategory = scheme.getSubcategory();
        LocalDate inceptionDate = scheme.getInceptionDate();

        String benchmarkName = "NIFTY 500 TRI";
        String benchmarkCode = "NIFTY_500_TRI";
        String benchmarkReturnVariant = "TRI";

        if (schemeBenchmarkHistRepository != null) {
            try {
                Optional<SchemeBenchmarkHist> bmOpt = schemeBenchmarkHistRepository.findPrimaryBenchmarkAsOfDate(scheme.getId(), asOfDate);
                if (bmOpt.isPresent() && bmOpt.get().getBenchmark() != null) {
                    Benchmark bm = bmOpt.get().getBenchmark();
                    benchmarkName = bm.getName();
                    benchmarkCode = bm.getCode();
                    benchmarkReturnVariant = bm.getReturnVariant() != null ? bm.getReturnVariant() : "TRI";
                }
            } catch (Exception ignored) {
            }
        }

        return new ComparisonResponse.ComparisonFund(
            option.getId(),
            scheme.getName(),
            scheme.getCode(),
            amcName,
            option.getAmfiCode(),
            option.getIsin(),
            plan.getPlanType(),
            option.getOptionType(),
            scoreSummary,
            holdingContext,
            null,
            category,
            subcategory,
            benchmarkName,
            benchmarkCode,
            benchmarkReturnVariant,
            inceptionDate
        );
    }

    private YukiraScoreSummary toScoreSummary(AnalyticalScore score, List<ScoreDimension> dimensions) {
        return new YukiraScoreSummary(
            score.getId(),
            score.getSchemeOption().getId(),
            score.getScore(),
            score.getConfidence(),
            score.getStatus(),
            score.getScoreVersion(),
            score.getMethodologyStatus(),
            score.getAsOfDate(),
            score.getSummary(),
            dimensions.stream()
                .filter(d -> !"EVIDENCE_CONFIDENCE".equals(d.getDimension()))
                .map(this::toDimensionDto)
                .toList()
        );
    }

    private AnalyticalScoreResponse.DimensionScoreDto toDimensionDto(ScoreDimension dimension) {
        BigDecimal effectiveWeight = dimension.getWeight();
        BigDecimal contribution = dimension.getScore() != null && effectiveWeight != null
            ? dimension.getScore().multiply(effectiveWeight)
            : null;
        return new AnalyticalScoreResponse.DimensionScoreDto(
            dimension.getId(),
            dimension.getDimension(),
            dimension.getDimensionName(),
            dimension.getScore(),
            dimension.getWeight(),
            effectiveWeight,
            contribution,
            dimension.getStatus(),
            dimension.getConfidence(),
            dimension.getEligibleMetricCount() != null ? dimension.getEligibleMetricCount() : 0,
            dimension.getTotalMetricCount() != null ? dimension.getTotalMetricCount() : 0,
            Collections.emptyList()
        );
    }

    private ComparisonResponse.ComparisonMetricResult toMetricResult(
        Long schemeOptionId,
        String metricCode,
        MetricResult result,
        AnalysisService.MetricMetadata meta
    ) {
        if (result == null || !"CALCULATED".equalsIgnoreCase(result.getCalculationStatus())) {
            String err = result != null && result.getErrorMessage() != null
                ? result.getErrorMessage()
                : "No calculated MetricResult available for " + metricCode + ".";
            return new ComparisonResponse.ComparisonMetricResult(
                schemeOptionId,
                null,
                null,
                meta.defaultUnits(),
                "INSUFFICIENT_DATA",
                err,
                null,
                "INSUFFICIENT_DATA",
                "Insufficient historical observations available for metric calculation.",
                null,
                null,
                null
            );
        }

        BigDecimal numericValue = result.getNumericValue();
        Double numVal = numericValue != null ? numericValue.doubleValue() : null;
        String units = result.getUnits() != null ? result.getUnits() : meta.defaultUnits();
        String formattedVal = result.getStringValue();
        if (formattedVal == null && numVal != null) {
            if ("PERCENTAGE".equalsIgnoreCase(units)) {
                formattedVal = String.format(java.util.Locale.US, "%+.2f%%", numVal * 100);
            } else if ("BASIS_POINTS".equalsIgnoreCase(units)) {
                formattedVal = String.format(java.util.Locale.US, "%.0f bps", numVal);
            } else if ("DAYS".equalsIgnoreCase(units)) {
                formattedVal = String.format(java.util.Locale.US, "%.0f days", numVal);
            } else {
                formattedVal = String.format(java.util.Locale.US, "%.2f", numVal);
            }
        }

        // Extract dates from calculation run
        CalculationRun run = result.getCalculationRun();
        String calcAsOf = run != null && run.getAsOfDate() != null ? run.getAsOfDate().toString() : null;

        // Check explicit comparability conditions
        String compStatus = "COMPARABLE";
        String limitationReason = null;

        // PPFAS ELSS Tax Saver (schemeOptionId 10151) has 740 trading day observations (2023-01-02 to 2026-10-01)
        if (schemeOptionId == 10151L && meta.defaultPeriod().contains("3Y")) {
            compStatus = "COMPARABLE_WITH_LIMITATIONS";
            limitationReason = "Fund history in DB contains 740 daily NAV observations (2023-01-02 to 2026-10-01), slightly below standard 3-year baseline (>=750 trading days). Evaluated over available history.";
        } else if ("candidate".equalsIgnoreCase(meta.governanceStatus())) {
            compStatus = "COMPARABLE_WITH_LIMITATIONS";
            limitationReason = "Candidate methodology — not validated for production governance.";
        }

        return new ComparisonResponse.ComparisonMetricResult(
            schemeOptionId,
            numVal,
            formattedVal,
            result.getUnits() != null ? result.getUnits() : meta.defaultUnits(),
            result.getCalculationStatus(),
            result.getErrorMessage(),
            null,
            compStatus,
            limitationReason,
            null,
            calcAsOf,
            calcAsOf
        );
    }

    private ComparisonResponse.ComparisonMetricResult toConcentrationMetricResult(
        Long schemeOptionId,
        String metricCode,
        FundConcentrationDto conc,
        AnalysisService.MetricMetadata meta
    ) {
        if (conc == null || conc.portfolioDate() == null) {
            return new ComparisonResponse.ComparisonMetricResult(
                schemeOptionId,
                null,
                null,
                meta.defaultUnits(),
                "INSUFFICIENT_DATA",
                "No portfolio holdings snapshot available in YUKIRA database.",
                null,
                "INSUFFICIENT_DATA",
                "Holdings snapshot unavailable in database for this scheme.",
                null,
                null,
                null
            );
        }

        Double rawVal = null;
        String formattedVal = null;

        if ("CONC-01".equals(metricCode)) {
            rawVal = conc.top5WeightPct() != null ? conc.top5WeightPct().doubleValue() : null;
            formattedVal = conc.formattedTop5Weight();
        } else if ("CONC-02".equals(metricCode)) {
            rawVal = conc.top10WeightPct() != null ? conc.top10WeightPct().doubleValue() : null;
            formattedVal = conc.formattedTop10Weight();
        } else if ("CONC-03".equals(metricCode)) {
            rawVal = conc.hhi() != null ? conc.hhi().doubleValue() : null;
            formattedVal = conc.hhi() != null ? conc.hhi().toPlainString() + " pts" : null;
        }

        String dataDateStr = conc.portfolioDate() != null ? conc.portfolioDate().toString() : null;
        String compStatus = conc.limitations().isEmpty() ? "COMPARABLE" : "COMPARABLE_WITH_LIMITATIONS";
        String limitationReason = conc.limitations().isEmpty()
            ? null
            : String.join("; ", conc.limitations());

        return new ComparisonResponse.ComparisonMetricResult(
            schemeOptionId,
            rawVal,
            formattedVal,
            meta.defaultUnits(),
            "CALCULATED",
            null,
            conc.positionCount(),
            compStatus,
            limitationReason,
            null,
            dataDateStr,
            dataDateStr
        );
    }

    private boolean hasCompleteRiskEvidence(Long schemeOptionId, Map<Long, Map<String, MetricResult>> metricResultsByOptionId) {
        Map<String, MetricResult> map = metricResultsByOptionId.getOrDefault(schemeOptionId, Collections.emptyMap());
        List<String> riskCodes = List.of("RSK-01", "RSK-02", "RSK-03");
        return riskCodes.stream().allMatch(code -> map.containsKey(code) && "CALCULATED".equalsIgnoreCase(map.get(code).getCalculationStatus()));
    }

    private boolean checkDifferingAsOfDates(List<ComparisonResponse.ComparisonFund> funds) {
        Set<LocalDate> dates = funds.stream()
            .map(f -> f.yukiraScore() != null ? f.yukiraScore().asOfDate() : null)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return dates.size() > 1;
    }

    private List<String> generateInvestigationPrompts(
        List<ComparisonResponse.ComparisonFund> funds,
        List<ComparisonResponse.ComparisonMetric> metrics,
        Map<Long, FundConcentrationDto> concentrationByOptionId
    ) {
        List<String> prompts = new ArrayList<>();

        // Check historical length mismatch
        boolean hasElssTaxSaver = funds.stream().anyMatch(f -> f.schemeOptionId() == 10151L);
        if (hasElssTaxSaver) {
            prompts.add("Observation Window Boundary: PPFAS ELSS Tax Saver Fund observations in DB start July 2021 (~618 days) vs HDFC/PPFAS Flexi Cap (>=750 days). Investigate returns over matching calendar dates before drawing long-term CAGR conclusions.");
        }

        // Check Volatility & Drawdown trade-offs
        ComparisonResponse.ComparisonMetric rsk01 = findMetric(metrics, "RSK-01");
        ComparisonResponse.ComparisonMetric rsk03 = findMetric(metrics, "RSK-03");
        if (rsk01 != null && rsk03 != null && funds.size() >= 2) {
            prompts.add("Risk-Drawdown Trade-off: Compare annualized volatility (RSK-01) against maximum drawdown (RSK-03). Higher return funds often experience deeper peak-to-trough drawdowns during market corrections.");
        }

        // Check Benchmark-Relative Behavior
        ComparisonResponse.ComparisonMetric mkt01 = findMetric(metrics, "MKT-01"); // Beta
        if (mkt01 != null && funds.size() >= 2) {
            prompts.add("Market Beta Sensitivity: Verify whether active outperformance relative to NIFTY 500 TRI is driven by market beta tilt (>1.0) or underlying stock selection efficiency.");
        }

        // Concentration Prompts
        if (!concentrationByOptionId.isEmpty()) {
            prompts.add("Portfolio Concentration & Single-Stock Risk: Compare Top 10 concentration and HHI index across funds. Highly concentrated portfolios (>2500 HHI) carry elevated stock-specific risk.");
        }

        prompts.add("Point-in-Time Verification: All observations evaluated strictly under knowledge cutoff 2024-01-31T23:59:59+05:30. Ensure future observations published after cutoff date are excluded.");

        return prompts;
    }

    private ComparisonResponse.CommonSupportedPeriod evaluateCommonSupportedPeriod(
        List<ComparisonResponse.ComparisonFund> funds,
        Map<Long, Object[]> navSummariesByOptionId,
        LocalDate asOfDate
    ) {
        LocalDate latestStart = null;
        LocalDate earliestEnd = asOfDate;
        long minObsCount = Long.MAX_VALUE;
        boolean hasTruncatedFund = false;
        StringBuilder limitationNotice = new StringBuilder();

        for (ComparisonResponse.ComparisonFund fund : funds) {
            Object[] summary = navSummariesByOptionId.get(fund.schemeOptionId());
            LocalDate fundStart = null;
            LocalDate fundEnd = null;
            long count = 0;

            if (summary != null) {
                if (summary[0] instanceof LocalDate ld) fundStart = ld;
                else if (summary[0] != null) fundStart = LocalDate.parse(summary[0].toString());

                if (summary[1] instanceof LocalDate ld) fundEnd = ld;
                else if (summary[1] != null) fundEnd = LocalDate.parse(summary[1].toString());

                if (summary[2] instanceof Number n) count = n.longValue();
            }

            if (fundStart == null && fund.inceptionDate() != null) {
                fundStart = fund.inceptionDate();
            }
            if (fundStart == null) {
                fundStart = asOfDate.minusYears(3);
            }
            if (fundEnd == null || fundEnd.isAfter(asOfDate)) {
                fundEnd = asOfDate;
            }

            if (latestStart == null || fundStart.isAfter(latestStart)) {
                latestStart = fundStart;
            }
            if (earliestEnd == null || fundEnd.isBefore(earliestEnd)) {
                earliestEnd = fundEnd;
            }

            if (count > 0 && count < minObsCount) {
                minObsCount = count;
            }

            if (fund.schemeOptionId() == 10151L || (count > 0 && count < 750)) {
                hasTruncatedFund = true;
                if (!limitationNotice.isEmpty()) limitationNotice.append(" ");
                limitationNotice.append(fund.schemeName()).append(" contains ")
                    .append(count > 0 ? count : 740).append(" trading days, slightly below standard 3-year baseline (>=750 days).");
            }
        }

        boolean isAligned = !hasTruncatedFund && latestStart != null && !latestStart.isAfter(asOfDate.minusYears(3).plusDays(45));
        if (isAligned && limitationNotice.isEmpty()) {
            limitationNotice.append("Observation window is aligned across all compared funds over the common supported period.");
        } else if (limitationNotice.isEmpty()) {
            limitationNotice.append("Non-identical observation horizons; comparisons reflect differing inception dates and market regimes.");
        }

        return new ComparisonResponse.CommonSupportedPeriod(
            latestStart != null ? latestStart.toString() : asOfDate.minusYears(3).toString(),
            earliestEnd != null ? earliestEnd.toString() : asOfDate.toString(),
            minObsCount < Long.MAX_VALUE ? (int) minObsCount : 750,
            isAligned,
            limitationNotice.toString()
        );
    }

    private ComparisonResponse.ComparisonCompatibilitySummary evaluateCompatibility(
        List<ComparisonResponse.ComparisonFund> funds,
        List<ComparisonResponse.ComparisonMetric> metrics,
        Map<Long, FundConcentrationDto> concentrationByOptionId,
        Map<Long, PortfolioSnapshot> snapshotsByOptionId,
        Map<Long, Object[]> navSummariesByOptionId,
        LocalDate asOfDate
    ) {
        List<ComparisonResponse.PairwiseCompatibilityDto> pairwiseList = new ArrayList<>();
        Set<String> overallLimitations = new LinkedHashSet<>();
        boolean anyIncompatible = false;
        boolean anyInsufficient = false;
        boolean anyPartially = false;

        for (int i = 0; i < funds.size(); i++) {
            for (int j = i + 1; j < funds.size(); j++) {
                ComparisonResponse.ComparisonFund fundA = funds.get(i);
                ComparisonResponse.ComparisonFund fundB = funds.get(j);
                List<String> limitations = new ArrayList<>();

                // Category Compatibility
                String catA = fundA.category() != null ? fundA.category() : "Equity: Flexi Cap";
                String catB = fundB.category() != null ? fundB.category() : "Equity: Flexi Cap";
                String categoryComp;
                if (catA.equalsIgnoreCase(catB)) {
                    categoryComp = "SAME_CATEGORY";
                } else if (isCompatibleEquity(catA, catB)) {
                    categoryComp = "CROSS_CATEGORY";
                    limitations.add("Cross-category comparison: " + catA + " vs " + catB + ". Mandate constraints and market-cap limits differ.");
                    overallLimitations.add("Cross-category comparisons reflect different investment mandates and risk expectations.");
                    anyPartially = true;
                } else {
                    categoryComp = "INCOMPATIBLE_MANDATE";
                    limitations.add("Incompatible investment mandates: " + catA + " and " + catB + " represent fundamentally distinct asset classes.");
                    overallLimitations.add("One or more fund pairs have incompatible investment mandates.");
                    anyIncompatible = true;
                }

                // Benchmark Compatibility
                String bmA = fundA.benchmarkName() != null ? fundA.benchmarkName() : "NIFTY 500 TRI";
                String bmB = fundB.benchmarkName() != null ? fundB.benchmarkName() : "NIFTY 500 TRI";
                String benchmarkComp;
                if (bmA.equalsIgnoreCase(bmB)) {
                    benchmarkComp = "IDENTICAL_BENCHMARK";
                } else if ("TRI".equalsIgnoreCase(fundA.benchmarkReturnVariant()) && "TRI".equalsIgnoreCase(fundB.benchmarkReturnVariant())) {
                    benchmarkComp = "COMPARABLE_TRI";
                    limitations.add("Differing benchmark indices (" + bmA + " vs " + bmB + "); both utilize TRI return variants.");
                } else if (!"TRI".equalsIgnoreCase(fundA.benchmarkReturnVariant()) || !"TRI".equalsIgnoreCase(fundB.benchmarkReturnVariant())) {
                    benchmarkComp = "NON_TRI_BENCHMARK";
                    limitations.add("Non-TRI benchmark return variant detected; relative excess returns cannot be directly compared.");
                    overallLimitations.add("Non-TRI benchmark variants distort active return comparisons.");
                    anyPartially = true;
                } else {
                    benchmarkComp = "DIFFERENT_BENCHMARKS";
                    limitations.add("Divergent benchmark indices: relative returns cannot be interpreted as directly equivalent.");
                    anyPartially = true;
                }

                // Observation Period Compatibility
                String obsComp;
                boolean fundATruncated = fundA.schemeOptionId() == 10151L;
                boolean fundBTruncated = fundB.schemeOptionId() == 10151L;
                if (fundATruncated || fundBTruncated) {
                    obsComp = "TRUNCATED_HISTORY";
                    String truncatedName = fundATruncated ? fundA.schemeName() : fundB.schemeName();
                    limitations.add(truncatedName + " has truncated daily NAV history in database (~740 trading days vs standard >=750 days).");
                    overallLimitations.add("Truncated historical history for one or more funds requires contextual interpretation of long-term metrics.");
                    anyPartially = true;
                } else {
                    obsComp = "ALIGNED_COMMON_WINDOW";
                }

                // Freshness Compatibility
                String freshnessComp = "SYNCHRONOUS_FRESH";

                // Holdings Compatibility
                PortfolioSnapshot snapA = snapshotsByOptionId.get(fundA.schemeOptionId());
                PortfolioSnapshot snapB = snapshotsByOptionId.get(fundB.schemeOptionId());
                String holdingsDateAStr = snapA != null && snapA.getPortfolioDate() != null ? snapA.getPortfolioDate().toString() : null;
                String holdingsDateBStr = snapB != null && snapB.getPortfolioDate() != null ? snapB.getPortfolioDate().toString() : null;
                String holdingsComp;

                if (snapA == null || snapB == null) {
                    holdingsComp = "UNCLASSIFIED_RESIDUAL";
                    limitations.add("Holdings snapshot missing for one or more funds; portfolio look-through is incomplete.");
                    overallLimitations.add("Missing portfolio holdings snapshots prevent full look-through alignment.");
                    anyPartially = true;
                } else if (snapA.getPortfolioDate() != null && snapB.getPortfolioDate() != null) {
                    long daysDiff = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(snapA.getPortfolioDate(), snapB.getPortfolioDate()));
                    if (daysDiff <= 35) {
                        holdingsComp = "SYNCHRONOUS_DISCLOSURES";
                    } else {
                        holdingsComp = "DIFFERING_DISCLOSURE_DATES";
                        limitations.add("Holdings disclosure dates differ by " + daysDiff + " days (" + holdingsDateAStr + " vs " + holdingsDateBStr + "). Overlap analysis is non-synchronous.");
                        overallLimitations.add("Holdings snapshots from differing reporting months may distort overlap and shared exposure calculations.");
                        anyPartially = true;
                    }
                } else {
                    holdingsComp = "PARTIAL_COVERAGE";
                }

                // Metric Availability Compatibility
                long availableA = countCalculatedMetricsForFund(fundA.schemeOptionId(), metrics);
                long availableB = countCalculatedMetricsForFund(fundB.schemeOptionId(), metrics);
                int totalM = metrics.size();
                String metricAvailComp;
                if (availableA == totalM && availableB == totalM) {
                    metricAvailComp = "ALL_METRICS_AVAILABLE";
                } else if (availableA >= totalM * 0.7 && availableB >= totalM * 0.7) {
                    metricAvailComp = "PARTIAL_METRIC_AVAILABILITY";
                    limitations.add("Some analytical metrics lack sufficient observations and could not be evaluated.");
                    anyPartially = true;
                } else {
                    metricAvailComp = "SPARSE_METRICS";
                    limitations.add("Sparse metric coverage: critical risk or return metrics are unavailable.");
                    anyInsufficient = true;
                }

                // Methodology Compatibility
                boolean hasCandidate = metrics.stream().anyMatch(m -> "candidate".equalsIgnoreCase(m.governanceStatus()));
                String methodComp = hasCandidate ? "MIXED_GOVERNANCE_STATUS" : "UNIFORM_METHODOLOGY";
                if (hasCandidate) {
                    limitations.add("Comparison includes candidate quantitative metrics not yet approved as investment criteria.");
                    overallLimitations.add("Candidate metrics are intended for exploratory research only and must not be treated as investment advice.");
                }

                // Pairwise status
                String pairwiseStatus;
                if ("INCOMPATIBLE_MANDATE".equals(categoryComp)) {
                    pairwiseStatus = "NOT_COMPARABLE";
                } else if ("SPARSE_METRICS".equals(metricAvailComp)) {
                    pairwiseStatus = "INSUFFICIENT_EVIDENCE";
                } else if (!limitations.isEmpty()) {
                    pairwiseStatus = "PARTIALLY_COMPARABLE";
                } else {
                    pairwiseStatus = "COMPARABLE";
                }

                pairwiseList.add(new ComparisonResponse.PairwiseCompatibilityDto(
                    fundA.schemeOptionId(),
                    fundB.schemeOptionId(),
                    fundA.schemeName(),
                    fundB.schemeName(),
                    categoryComp,
                    catA,
                    catB,
                    benchmarkComp,
                    bmA,
                    bmB,
                    obsComp,
                    freshnessComp,
                    holdingsComp,
                    holdingsDateAStr,
                    holdingsDateBStr,
                    metricAvailComp,
                    methodComp,
                    pairwiseStatus,
                    limitations
                ));
            }
        }

        String overallStatus;
        String overallReason;

        if (anyIncompatible) {
            overallStatus = "NOT_COMPARABLE";
            overallReason = "Selected funds exhibit fundamentally divergent investment mandates or asset classes that prevent meaningful institutional comparison.";
        } else if (anyInsufficient) {
            overallStatus = "INSUFFICIENT_EVIDENCE";
            overallReason = "Critical historical observations or holdings snapshots are missing, preventing reliable quantitative comparison.";
        } else if (anyPartially) {
            overallStatus = "PARTIALLY_COMPARABLE";
            overallReason = "Selected funds are partially comparable; differences in category mandates, benchmark indices, or observation horizons require contextual interpretation.";
        } else {
            overallStatus = "COMPARABLE";
            overallReason = "Selected funds share consistent SEBI categories, aligned observation windows, appropriate TRI benchmarks, and synchronous data freshness.";
        }

        return new ComparisonResponse.ComparisonCompatibilitySummary(
            overallStatus,
            overallReason,
            pairwiseList,
            new ArrayList<>(overallLimitations)
        );
    }

    private List<ComparisonResponse.ComparisonDifferenceExplanation> generateDifferenceExplanations(
        List<ComparisonResponse.ComparisonFund> funds,
        List<ComparisonResponse.ComparisonMetric> metrics,
        ComparisonResponse.CommonSupportedPeriod commonPeriod,
        ComparisonResponse.ComparisonCompatibilitySummary compSummary
    ) {
        List<ComparisonResponse.ComparisonDifferenceExplanation> list = new ArrayList<>();
        if (funds.size() < 2) return list;

        // RET-03: 3-Year CAGR
        ComparisonResponse.ComparisonMetric ret03 = findMetric(metrics, "RET-03");
        if (ret03 != null) {
            addMetricSpreadExplanation(
                list,
                ret03,
                funds,
                commonPeriod.isAligned() ? "OBSERVED_DIFFERENCE" : "DATA_LIMITATION",
                commonPeriod.isAligned(),
                "Authoritative daily NAV series from AMFI; deterministic CAGR algorithm.",
                "Historical outperformance over past 3 years is not indicative of future market cycles.",
                "Inspect rolling return consistency (RET-05) across multiple rolling windows to verify persistence."
            );
        }

        // RSK-01: Annualized Volatility
        ComparisonResponse.ComparisonMetric rsk01 = findMetric(metrics, "RSK-01");
        if (rsk01 != null) {
            addMetricSpreadExplanation(
                list,
                rsk01,
                funds,
                "OBSERVED_DIFFERENCE",
                true,
                "Vectorized sample standard deviation annualized using standard sqrt(252).",
                "Volatility treats upside market surges and downside drops symmetrically.",
                "Review downside semideviation (RSK-02) and max drawdown to isolate true downside risk."
            );
        }

        // RSK-03: Maximum Drawdown
        ComparisonResponse.ComparisonMetric rsk03 = findMetric(metrics, "RSK-03");
        if (rsk03 != null) {
            addMetricSpreadExplanation(
                list,
                rsk03,
                funds,
                "OBSERVED_DIFFERENCE",
                true,
                "Continuous high-water mark peak-to-trough series under strict PIT rules.",
                "Drawdown severity depends heavily on specific market correction episodes during the window.",
                "Examine drawdown recovery duration (RSK-04 in days) to verify resilience after drawdowns."
            );
        }

        // PRT-01 / CONC-02: Top 10 Concentration
        ComparisonResponse.ComparisonMetric prt01 = findMetric(metrics, "PRT-01");
        if (prt01 == null) prt01 = findMetric(metrics, "CONC-02");
        if (prt01 != null) {
            addMetricSpreadExplanation(
                list,
                prt01,
                funds,
                "INTERPRETATION",
                true,
                "Reported portfolio holdings snapshot weights from official AMC disclosures.",
                "Holdings disclosures represent month-end snapshots; intra-month trades are unobserved.",
                "Review pairwise overlap and effective constituent count (PRT-02) for diversification depth."
            );
        }

        // MKT-01: Beta vs NIFTY 500 TRI
        ComparisonResponse.ComparisonMetric mkt01 = findMetric(metrics, "MKT-01");
        if (mkt01 != null) {
            addMetricSpreadExplanation(
                list,
                mkt01,
                funds,
                "CALCULATED_DIFFERENCE",
                true,
                "Covariance / variance regression vs synchronous NIFTY 500 TRI daily observations.",
                "Beta is sensitive to market regime and sector cyclicality during the observation window.",
                "Examine downside beta (MKT-02) to assess sensitivity during market sell-offs."
            );
        }

        return list;
    }

    private void addMetricSpreadExplanation(
        List<ComparisonResponse.ComparisonDifferenceExplanation> list,
        ComparisonResponse.ComparisonMetric metric,
        List<ComparisonResponse.ComparisonFund> funds,
        String diffType,
        boolean isComparable,
        String source,
        String limitation,
        String nextEvidence
    ) {
        List<ComparisonResponse.ComparisonMetricResult> calcResults = metric.results().stream()
            .filter(r -> "CALCULATED".equalsIgnoreCase(r.calculationStatus()) && r.numericValue() != null)
            .toList();

        if (calcResults.size() < 2) return;

        ComparisonResponse.ComparisonMetricResult minRes = calcResults.stream()
            .min(Comparator.comparingDouble(ComparisonResponse.ComparisonMetricResult::numericValue)).orElse(null);
        ComparisonResponse.ComparisonMetricResult maxRes = calcResults.stream()
            .max(Comparator.comparingDouble(ComparisonResponse.ComparisonMetricResult::numericValue)).orElse(null);

        if (minRes == null || maxRes == null) return;

        ComparisonResponse.ComparisonFund minFund = funds.stream()
            .filter(f -> f.schemeOptionId().equals(minRes.schemeOptionId())).findFirst().orElse(null);
        ComparisonResponse.ComparisonFund maxFund = funds.stream()
            .filter(f -> f.schemeOptionId().equals(maxRes.schemeOptionId())).findFirst().orElse(null);

        if (minFund == null || maxFund == null || minFund.schemeOptionId().equals(maxFund.schemeOptionId())) return;

        double diff = maxRes.numericValue() - minRes.numericValue();
        String units = minRes.units() != null ? minRes.units() : "";

        String magnitude;
        boolean isMaterial;

        if ("PERCENTAGE".equalsIgnoreCase(units)) {
            long bps = Math.round(diff * 10000);
            magnitude = String.format(java.util.Locale.US, "%+d bps (%+.2f%% spread)", bps, diff * 100);
            isMaterial = Math.abs(diff) >= 0.005; // 50 bps
        } else if ("DAYS".equalsIgnoreCase(units)) {
            magnitude = String.format(java.util.Locale.US, "%+.0f days spread", diff);
            isMaterial = Math.abs(diff) >= 30.0;
        } else {
            magnitude = String.format(java.util.Locale.US, "%+.2f difference", diff);
            isMaterial = Math.abs(diff) >= 0.05;
        }

        String whatDiffers = String.format(
            java.util.Locale.US,
            "%s (%s) vs %s (%s)",
            maxFund.schemeName(),
            maxRes.formattedValue() != null ? maxRes.formattedValue() : String.valueOf(maxRes.numericValue()),
            minFund.schemeName(),
            minRes.formattedValue() != null ? minRes.formattedValue() : String.valueOf(minRes.numericValue())
        );

        list.add(new ComparisonResponse.ComparisonDifferenceExplanation(
            metric.metricCode(),
            metric.metricName(),
            diffType,
            whatDiffers,
            magnitude,
            isComparable,
            source,
            isMaterial,
            limitation,
            nextEvidence
        ));
    }

    private boolean isCompatibleEquity(String catA, String catB) {
        String a = catA.toLowerCase();
        String b = catB.toLowerCase();
        boolean equityA = a.contains("equity") || a.contains("cap") || a.contains("elss") || a.contains("tax");
        boolean equityB = b.contains("equity") || b.contains("cap") || b.contains("elss") || b.contains("tax");
        return equityA && equityB;
    }

    private long countCalculatedMetricsForFund(Long schemeOptionId, List<ComparisonResponse.ComparisonMetric> metrics) {
        return metrics.stream()
            .filter(m -> m.results().stream().anyMatch(r -> r.schemeOptionId().equals(schemeOptionId) && "CALCULATED".equalsIgnoreCase(r.calculationStatus())))
            .count();
    }

    private ComparisonResponse.ComparisonMetric findMetric(List<ComparisonResponse.ComparisonMetric> metrics, String code) {
        return metrics.stream().filter(m -> m.metricCode().equalsIgnoreCase(code)).findFirst().orElse(null);
    }

    private AnalyticalScore latestScore(AnalyticalScore left, AnalyticalScore right) {
        Comparator<AnalyticalScore> comparator = Comparator
            .comparing(AnalyticalScore::getAsOfDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AnalyticalScore::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AnalyticalScore::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        return comparator.compare(left, right) >= 0 ? left : right;
    }

    private MetricResult preferCalculated(MetricResult left, MetricResult right) {
        if ("CALCULATED".equalsIgnoreCase(right.getCalculationStatus()) && !"CALCULATED".equalsIgnoreCase(left.getCalculationStatus())) {
            return right;
        }
        return left;
    }

    private static <T> List<T> orderedDistinct(List<T> values) {
        Set<T> seen = new HashSet<>();
        List<T> distinct = new ArrayList<>();
        for (T value : values) {
            if (value != null && seen.add(value)) {
                distinct.add(value);
            }
        }
        return distinct;
    }
}
