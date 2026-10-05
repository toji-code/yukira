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
import com.yukira.backend.repository.AnalyticalScoreRepository;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.ScoreDimensionRepository;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.YukiraScoreSummary;
import com.yukira.backend.service.AnalysisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 * Read-only comparison assembler for persisted fund evidence.
 *
 * No financial mathematics is performed here. This service only batch-loads canonical identities,
 * persisted scores, persisted score dimensions, and persisted metric results, then maps them to the
 * existing comparison response contract.
 */
@Service
@Transactional(readOnly = true)
public class PortfolioComparisonQueryService {

    private final SchemeOptionRepository schemeOptionRepository;
    private final AnalyticalScoreRepository analyticalScoreRepository;
    private final ScoreDimensionRepository scoreDimensionRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final MetricResultRepository metricResultRepository;

    public PortfolioComparisonQueryService(
        SchemeOptionRepository schemeOptionRepository,
        AnalyticalScoreRepository analyticalScoreRepository,
        ScoreDimensionRepository scoreDimensionRepository,
        CalculationRunRepository calculationRunRepository,
        MetricResultRepository metricResultRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.analyticalScoreRepository = analyticalScoreRepository;
        this.scoreDimensionRepository = scoreDimensionRepository;
        this.calculationRunRepository = calculationRunRepository;
        this.metricResultRepository = metricResultRepository;
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

        List<ComparisonResponse.ComparisonFund> funds = schemeOptionIds.stream()
            .map(id -> toFund(optionById.get(id), scoreByOptionId.get(id), dimensionsByScoreId, holdingContexts.get(id)))
            .toList();

        List<ComparisonResponse.ComparisonMetric> metrics = new ArrayList<>();
        for (String metricCode : metricCodes) {
            AnalysisService.MetricMetadata meta = AnalysisService.comparisonMetricMetadata(metricCode);
            List<ComparisonResponse.ComparisonMetricResult> results = new ArrayList<>();
            for (Long schemeOptionId : schemeOptionIds) {
                MetricResult result = metricResultsByOptionId.getOrDefault(schemeOptionId, Collections.emptyMap()).get(metricCode);
                results.add(toMetricResult(schemeOptionId, metricCode, result, meta));
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
                results
            ));
        }

        return new ComparisonResponse(
            funds,
            metrics,
            new ComparisonResponse.ComparisonPeriod(
                null,
                request.asOfDate().toString(),
                request.knowledgeCutoffTime().toString()
            ),
            System.nanoTime(),
            OffsetDateTime.now()
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
        if (result == null) {
            return new ComparisonResponse.ComparisonMetricResult(
                schemeOptionId,
                null,
                null,
                meta.defaultUnits(),
                "INSUFFICIENT_DATA",
                "No persisted MetricResult is available for " + metricCode + ".",
                null
            );
        }

        BigDecimal numericValue = result.getNumericValue();
        return new ComparisonResponse.ComparisonMetricResult(
            schemeOptionId,
            numericValue != null ? numericValue.doubleValue() : null,
            result.getStringValue(),
            result.getUnits() != null ? result.getUnits() : meta.defaultUnits(),
            result.getCalculationStatus(),
            result.getErrorMessage(),
            null
        );
    }

    private AnalyticalScore latestScore(AnalyticalScore left, AnalyticalScore right) {
        Comparator<AnalyticalScore> comparator = Comparator
            .comparing(AnalyticalScore::getAsOfDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AnalyticalScore::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AnalyticalScore::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        return comparator.compare(left, right) >= 0 ? left : right;
    }

    private MetricResult preferCalculated(MetricResult left, MetricResult right) {
        if ("CALCULATED".equals(right.getCalculationStatus()) && !"CALCULATED".equals(left.getCalculationStatus())) {
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