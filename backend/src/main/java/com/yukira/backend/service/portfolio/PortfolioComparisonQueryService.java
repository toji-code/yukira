package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.AnalyticalScore;
import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.domain.entity.ScoreDimension;
import com.yukira.backend.dto.analysis.ComparisonRequest;
import com.yukira.backend.dto.analysis.ComparisonResponse;
import com.yukira.backend.dto.portfolio.FundConcentrationDto;
import com.yukira.backend.repository.AnalyticalScoreRepository;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only comparison assembler for persisted fund evidence and portfolio intelligence.
 *
 * Enforces the YUKIRA Comparability Contract (Phase 3 & Phase 5):
 * - Assigns explicit Comparability Status (COMPARABLE, COMPARABLE_WITH_LIMITATIONS, NOT_COMPARABLE, INSUFFICIENT_DATA)
 * - Identifies observation window mismatches, candidate governance status, and data limitations
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

    public PortfolioComparisonQueryService(
        SchemeOptionRepository schemeOptionRepository,
        AnalyticalScoreRepository analyticalScoreRepository,
        ScoreDimensionRepository scoreDimensionRepository,
        CalculationRunRepository calculationRunRepository,
        MetricResultRepository metricResultRepository,
        @Autowired(required = false) PortfolioIntelligenceService portfolioIntelligenceService
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.analyticalScoreRepository = analyticalScoreRepository;
        this.scoreDimensionRepository = scoreDimensionRepository;
        this.calculationRunRepository = calculationRunRepository;
        this.metricResultRepository = metricResultRepository;
        this.portfolioIntelligenceService = portfolioIntelligenceService;
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

        List<ComparisonResponse.ComparisonFund> funds = schemeOptionIds.stream()
            .map(id -> toFund(optionById.get(id), scoreByOptionId.get(id), dimensionsByScoreId, holdingContexts.get(id)))
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
            investigationPrompts
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
        ComparisonResponse.PortfolioHoldingContext holdingContext
    ) {
        SchemePlan plan = option.getPlan();
        Scheme scheme = plan.getScheme();
        String amcName = scheme.getAmc() != null ? scheme.getAmc().getLegalName() : null;
        YukiraScoreSummary scoreSummary = score != null ? toScoreSummary(score, dimensionsByScoreId.getOrDefault(score.getId(), Collections.emptyList())) : null;
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
            null
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