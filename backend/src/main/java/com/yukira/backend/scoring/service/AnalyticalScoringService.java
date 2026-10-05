package com.yukira.backend.scoring.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.AnalyticalProfileResponse;
import com.yukira.backend.dto.analysis.ProfileCalculationRequest;
import com.yukira.backend.repository.*;
import com.yukira.backend.scoring.config.ScoreMethodologyConfig;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.ScoreCalculationRequest;
import com.yukira.backend.scoring.dto.UniverseScoringReport;
import com.yukira.backend.scoring.normalization.MetricNormalizer;
import com.yukira.backend.service.AnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Core deterministic engine for YUKIRA Analytical Quality Score V1.
 *
 * Pipeline:
 * MetricResult -> eligibility -> data-quality gate -> normalization ->
 * metric contribution -> dimension score -> weighted aggregate ->
 * 0â€“100 analytical score -> separate 0â€“100 confidence -> score status -> explainable provenance.
 *
 * GOVERNANCE: CANDIDATE / RESEARCH. Strictly non-advisory, non-predictive.
 * Reference Population: PROVISIONAL candidate parameters; NOT empirical full-universe percentiles.
 * IMPLEMENTED != VALIDATED != APPROVED.
 */
@Service
@SuppressWarnings("null")
public class AnalyticalScoringService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticalScoringService.class);
    private static final String DISCLAIMER = "YUKIRA Analytical Score V1 is a candidate quantitative quality assessment based on verified point-in-time empirical evidence. It is NOT investment advice, a star rating, a future return forecast, or a recommendation to buy or sell.";

    private final ScoreMethodologyConfig config;
    private final MetricNormalizer normalizer;
    private final SchemeOptionRepository schemeOptionRepository;
    private final CalculationRunRepository calculationRunRepository;
    private final MetricResultRepository metricResultRepository;
    private final NavObservationRepository navObservationRepository;
    private final BenchmarkObservationRepository benchmarkObservationRepository;
    private final AnalyticalScoreRepository analyticalScoreRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final AnalysisService analysisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalyticalScoringService(
        ScoreMethodologyConfig config,
        MetricNormalizer normalizer,
        SchemeOptionRepository schemeOptionRepository,
        CalculationRunRepository calculationRunRepository,
        MetricResultRepository metricResultRepository,
        NavObservationRepository navObservationRepository,
        BenchmarkObservationRepository benchmarkObservationRepository,
        AnalyticalScoreRepository analyticalScoreRepository,
        BenchmarkRepository benchmarkRepository,
        AnalysisService analysisService
    ) {
        this.config = config;
        this.normalizer = normalizer;
        this.schemeOptionRepository = schemeOptionRepository;
        this.calculationRunRepository = calculationRunRepository;
        this.metricResultRepository = metricResultRepository;
        this.navObservationRepository = navObservationRepository;
        this.benchmarkObservationRepository = benchmarkObservationRepository;
        this.analyticalScoreRepository = analyticalScoreRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.analysisService = analysisService;
    }

    private boolean isReusableScoreSnapshot(AnalyticalScore score) {
        return score != null
            && score.getCalculationRun() != null
            && !"JAVA-OFFLINE-FALLBACK-0.1.0".equalsIgnoreCase(score.getCalculationRun().getEngineSoftwareVersion())
            && !"INSUFFICIENT_DATA".equalsIgnoreCase(score.getStatus());
    }

    /**
     * Executes the complete score calculation pipeline for a given scheme option.
     */
    @Transactional
    public AnalyticalScoreResponse calculateScore(ScoreCalculationRequest request) {
        if (request == null || request.schemeOptionId() == null) {
            throw new IllegalArgumentException("schemeOptionId must not be null");
        }

        SchemeOption option = schemeOptionRepository.findById(request.schemeOptionId())
            .orElseThrow(() -> new IllegalArgumentException("SchemeOption #" + request.schemeOptionId() + " not found"));

        LocalDate asOfDate = request.asOfDate() != null ? request.asOfDate() : LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = request.knowledgeCutoffTime() != null ? request.knowledgeCutoffTime()
            : OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        // 0. Idempotency Check: if calculationRunId specified, return score if run was already scored; otherwise check for reusable snapshot
        if (request.calculationRunId() != null) {
            Optional<AnalyticalScore> existingForRun = analyticalScoreRepository.findByCalculationRunId(request.calculationRunId());
            if (existingForRun.isPresent()) {
                return buildResponseFromEntity(existingForRun.get());
            }
        } else {
            List<AnalyticalScore> existingScores = analyticalScoreRepository.findBySchemeOptionAndDateAndVersion(
                option.getId(), asOfDate, config.getScoreVersion()
            );
            if (!existingScores.isEmpty()) {
                AnalyticalScore existing = existingScores.get(0);
                if (isReusableScoreSnapshot(existing)) {
                    return buildResponseFromEntity(existing);
                }
            }
        }

        // 0b. Universe Eligibility Assessment
        ScoringEligibilityAssessment assessment = evaluateSchemeOptionScoringEligibility(option, asOfDate, knowledgeCutoff);
        if (!assessment.isScoreable()) {
            String summaryMsg = "Score Unavailable: " + assessment.reason();
            AnalyticalScore nonScoreable = new AnalyticalScore(
                option,
                null,
                null,
                BigDecimal.ZERO,
                assessment.status(),
                config.getScoreVersion(),
                config.getMethodologyStatus(),
                asOfDate,
                knowledgeCutoff,
                config.getReferencePopulationCode(),
                summaryMsg
            );
            AnalyticalScore savedNonScoreable = analyticalScoreRepository.save(nonScoreable);
            return buildResponseFromEntity(savedNonScoreable);
        }

        // 1. Resolve or compute the underlying CalculationRun with canonical profile metrics
        CalculationRun run;
        if (request.calculationRunId() != null) {
            run = calculationRunRepository.findById(request.calculationRunId())
                .orElseThrow(() -> new IllegalArgumentException("CalculationRun #" + request.calculationRunId() + " not found"));
        } else {
            Set<String> requiredScoreMetricCodes = Set.of(
                "RET-03", "RET-07", "RSK-01", "RSK-02", "RSK-03", "REL-02", "RAT-04", "MKT-01", "MKT-05", "MKT-02"
            );
            List<CalculationRun> existingRuns = calculationRunRepository.findBySchemeOptionId(option.getId()).stream()
                .filter(r -> "SUCCESS".equalsIgnoreCase(r.getRunStatus()) || "COMPLETED".equalsIgnoreCase(r.getRunStatus()))
                .filter(r -> !"JAVA-OFFLINE-FALLBACK-0.1.0".equalsIgnoreCase(r.getEngineSoftwareVersion()))
                .filter(r -> asOfDate.equals(r.getAsOfDate()))
                .filter(r -> {
                    List<MetricResult> resList = metricResultRepository.findByCalculationRunId(r.getId());
                    Set<String> codes = resList.stream().map(MetricResult::getMetricCode).collect(java.util.stream.Collectors.toSet());
                    return codes.containsAll(requiredScoreMetricCodes);
                })
                .sorted(Comparator.comparing(CalculationRun::getId).reversed())
                .toList();

            if (!existingRuns.isEmpty()) {
                run = existingRuns.get(0);
            } else {
                // Trigger calculation run via AnalysisService
                Benchmark benchmark = request.benchmarkId() != null
                    ? benchmarkRepository.findById(request.benchmarkId()).orElseThrow()
                    : (assessment.benchmark() != null ? assessment.benchmark()
                    : benchmarkRepository.findByCode("NIFTY_500_TRI").orElseGet(() -> benchmarkRepository.findAll().get(0)));

                ProfileCalculationRequest profileReq = new ProfileCalculationRequest(
                    option.getId(),
                    benchmark.getId(),
                    asOfDate,
                    knowledgeCutoff,
                    "APPROVED_M2N",
                    null,
                    Map.of("periods_per_year", 252.0, "min_downside_observations", 100)
                );
                AnalyticalProfileResponse profileResp = analysisService.executeProfileAnalysis(profileReq);
                run = calculationRunRepository.findById(profileResp.provenance().calculationRunId())
                    .orElseThrow(() -> new IllegalStateException("Failed to retrieve generated CalculationRun"));
            }
        }

        // 2. Load all MetricResults for this run into map by metric_code (canonical only)
        List<MetricResult> results = metricResultRepository.findCanonicalByCalculationRunId(run.getId());
        Map<String, MetricResult> metricResultMap = new HashMap<>();
        for (MetricResult mr : results) {
            if ("REL-02".equals(mr.getMetricCode())) {
                String diag = mr.getDiagnostics();
                if (diag == null || !diag.contains("OLS intercept")) {
                    log.warn("Skipping legacy non-canonical REL-02 (id={}, runId={}) lacking 'OLS intercept' from analytical scoring map",
                        mr.getId(), run.getId());
                    continue;
                }
            }
            metricResultMap.put(mr.getMetricCode(), mr);
        }

        // 3. Evidence & Data Quality Audit (Dimension E -> powers Confidence)
        AnalyticalScoreResponse.EvidenceConfidenceDto evidenceDto = auditEvidenceQuality(option, run, asOfDate, knowledgeCutoff);

        // 4. Two-Pass Score Dimensions Calculation (A, B, C, D)
        record MetricStatusRecord(
            ScoreMethodologyConfig.MetricConfig mConfig,
            MetricResult mr,
            boolean isEligible,
            String eligibility,
            String exclusionReason,
            BigDecimal rawValue,
            BigDecimal normalizedValue,
            Integer obsCount
        ) {}

        record DimCalcIntermediate(
            ScoreMethodologyConfig.DimensionConfig dimConfig,
            List<ScoreMetricContribution> metricEntities,
            List<AnalyticalScoreResponse.MetricContributionDto> metricDtos,
            BigDecimal dimensionScore,
            String dimensionStatus,
            BigDecimal dimensionConfidence,
            int eligibleCount
        ) {}

        List<DimCalcIntermediate> intermediateDims = new ArrayList<>();
        BigDecimal sumEligibleDimensionWeights = BigDecimal.ZERO;
        int totalConfiguredDimensions = config.getDimensions().size();
        int availableDimensions = 0;

        for (Map.Entry<String, ScoreMethodologyConfig.DimensionConfig> dimEntry : config.getDimensions().entrySet()) {
            ScoreMethodologyConfig.DimensionConfig dimConfig = dimEntry.getValue();

            BigDecimal sumEligibleMetricWeights = BigDecimal.ZERO;
            int eligibleCount = 0;
            List<MetricStatusRecord> metricRecords = new ArrayList<>();

            // Pass 1: check eligibility of each metric in the dimension
            for (ScoreMethodologyConfig.MetricConfig mConfig : dimConfig.metrics()) {
                MetricResult mr = metricResultMap.get(mConfig.metricCode());

                boolean isEligible = false;
                String eligibility = "MISSING";
                String exclusionReason = null;
                BigDecimal rawValue = null;
                BigDecimal normalizedValue = null;
                Integer obsCount = extractObservationCount(mr, evidenceDto.totalObservations(), evidenceDto.pairedReturnPeriods());

                if (mr == null) {
                    eligibility = "MISSING";
                    exclusionReason = "Metric not found in calculation run";
                } else if (!mConfig.scoreEligible() || mConfig.referenceDistribution() == null) {
                    eligibility = "UNCALIBRATED";
                    exclusionReason = "Metric calibration status is " + mConfig.calibrationStatus();
                    rawValue = mr.getNumericValue();
                } else if (!"CALCULATED".equalsIgnoreCase(mr.getCalculationStatus())) {
                    eligibility = "INELIGIBLE";
                    exclusionReason = "Calculation status is " + mr.getCalculationStatus() + ": " + mr.getErrorMessage();
                } else if (mr.getNumericValue() == null) {
                    eligibility = "INELIGIBLE";
                    exclusionReason = "Numeric value is null";
                } else if (!evidenceDto.meetsObservationThreshold()) {
                    eligibility = "DATA_QUALITY_EXCLUDED";
                    exclusionReason = "Observation count (" + evidenceDto.totalObservations() + ") below minimum required (" + mConfig.minObservations() + ")";
                } else {
                    isEligible = true;
                    eligibility = "ELIGIBLE";
                    rawValue = mr.getNumericValue();
                    normalizedValue = normalizer.normalize(rawValue, mConfig.referenceDistribution());
                }

                if (isEligible) {
                    eligibleCount++;
                    sumEligibleMetricWeights = sumEligibleMetricWeights.add(mConfig.weight());
                }

                metricRecords.add(new MetricStatusRecord(
                    mConfig, mr, isEligible, eligibility, exclusionReason, rawValue, normalizedValue, obsCount
                ));
            }

            // Pass 2: compute effective weights and individual metric contributions
            BigDecimal dimensionScore = null;
            String dimensionStatus;
            List<ScoreMetricContribution> metricContributions = new ArrayList<>();
            List<AnalyticalScoreResponse.MetricContributionDto> metricDtos = new ArrayList<>();

            if (eligibleCount == 0 || sumEligibleMetricWeights.compareTo(BigDecimal.ZERO) == 0) {
                dimensionStatus = "INSUFFICIENT_DATA";
                for (MetricStatusRecord rec : metricRecords) {
                    BigDecimal effWeight = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
                    BigDecimal contrib = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
                    ScoreMetricContribution smc = new ScoreMetricContribution(
                        null, rec.mConfig().metricCode(), rec.mConfig().metricName(),
                        rec.rawValue(), rec.normalizedValue(), rec.mConfig().direction().name(),
                        effWeight, contrib, rec.eligibility(), rec.exclusionReason(), rec.mr(), rec.mConfig().unit()
                    );
                    metricContributions.add(smc);
                    String formattedRaw = formatRawValue(rec.rawValue(), rec.mConfig().unit(), rec.mConfig().metricCode());
                    metricDtos.add(new AnalyticalScoreResponse.MetricContributionDto(
                        null, rec.mConfig().metricCode(), rec.mConfig().metricName(),
                        rec.rawValue(), formattedRaw, rec.normalizedValue(), rec.mConfig().direction().name(),
                        rec.mConfig().weight(), effWeight, contrib, rec.obsCount(),
                        rec.eligibility(), rec.exclusionReason(), rec.mConfig().unit(),
                        rec.mr() != null ? rec.mr().getId() : null
                    ));
                }
            } else {
                BigDecimal sumMetricContribs = BigDecimal.ZERO;
                for (MetricStatusRecord rec : metricRecords) {
                    BigDecimal effWeight;
                    BigDecimal contrib;
                    if (rec.isEligible()) {
                        effWeight = rec.mConfig().weight().divide(sumEligibleMetricWeights, 4, RoundingMode.HALF_UP);
                        contrib = rec.normalizedValue().multiply(effWeight).setScale(4, RoundingMode.HALF_UP);
                        sumMetricContribs = sumMetricContribs.add(contrib);
                    } else {
                        effWeight = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
                        contrib = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
                    }

                    ScoreMetricContribution smc = new ScoreMetricContribution(
                        null, rec.mConfig().metricCode(), rec.mConfig().metricName(),
                        rec.rawValue(), rec.normalizedValue(), rec.mConfig().direction().name(),
                        effWeight, contrib, rec.eligibility(), rec.exclusionReason(), rec.mr(), rec.mConfig().unit()
                    );
                    metricContributions.add(smc);

                    String formattedRaw = formatRawValue(rec.rawValue(), rec.mConfig().unit(), rec.mConfig().metricCode());
                    metricDtos.add(new AnalyticalScoreResponse.MetricContributionDto(
                        null, rec.mConfig().metricCode(), rec.mConfig().metricName(),
                        rec.rawValue(), formattedRaw, rec.normalizedValue(), rec.mConfig().direction().name(),
                        rec.mConfig().weight(), effWeight, contrib, rec.obsCount(),
                        rec.eligibility(), rec.exclusionReason(), rec.mConfig().unit(),
                        rec.mr() != null ? rec.mr().getId() : null
                    ));
                }

                dimensionScore = sumMetricContribs.setScale(2, RoundingMode.HALF_UP);
                if (eligibleCount == dimConfig.metrics().size()) {
                    dimensionStatus = "AVAILABLE";
                    availableDimensions++;
                } else {
                    dimensionStatus = "PARTIAL";
                }
                sumEligibleDimensionWeights = sumEligibleDimensionWeights.add(dimConfig.weight());
            }

            double metricCoverageRatio = (double) eligibleCount / dimConfig.metrics().size();
            BigDecimal dimensionConfidence = evidenceDto.confidenceScore()
                .multiply(BigDecimal.valueOf(metricCoverageRatio))
                .setScale(2, RoundingMode.HALF_UP);

            intermediateDims.add(new DimCalcIntermediate(
                dimConfig, metricContributions, metricDtos, dimensionScore, dimensionStatus, dimensionConfidence, eligibleCount
            ));
        }

        // Pass 3: Dimension weighting and final aggregate score
        List<ScoreDimension> dimensionEntities = new ArrayList<>();
        List<AnalyticalScoreResponse.DimensionScoreDto> dimensionDtos = new ArrayList<>();
        BigDecimal overallScore = null;
        String overallStatus;
        BigDecimal sumDimensionContribs = BigDecimal.ZERO;

        boolean hasSufficientDimensionWeights = sumEligibleDimensionWeights.compareTo(new BigDecimal("0.5000")) >= 0;

        for (DimCalcIntermediate inter : intermediateDims) {
            BigDecimal effDimWeight;
            BigDecimal dimContrib;

            if (inter.dimensionScore() != null && hasSufficientDimensionWeights && sumEligibleDimensionWeights.compareTo(BigDecimal.ZERO) > 0) {
                effDimWeight = inter.dimConfig().weight().divide(sumEligibleDimensionWeights, 4, RoundingMode.HALF_UP);
                dimContrib = inter.dimensionScore().multiply(effDimWeight).setScale(4, RoundingMode.HALF_UP);
                sumDimensionContribs = sumDimensionContribs.add(dimContrib);
            } else {
                effDimWeight = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
                dimContrib = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            }

            String dimDiagJson = String.format(Locale.ENGLISH,
                "{\"effective_weight\":%s,\"configured_weight\":%s,\"contribution\":%s}",
                effDimWeight.toPlainString(), inter.dimConfig().weight().toPlainString(), dimContrib.toPlainString());

            ScoreDimension dimEntity = new ScoreDimension(
                null,
                inter.dimConfig().dimensionCode(),
                inter.dimConfig().dimensionName(),
                inter.dimensionScore(),
                inter.dimConfig().weight(),
                inter.dimensionStatus(),
                inter.dimensionConfidence(),
                inter.eligibleCount(),
                inter.dimConfig().metrics().size(),
                dimDiagJson
            );

            for (ScoreMetricContribution c : inter.metricEntities()) {
                dimEntity.addMetricContribution(c);
            }
            dimensionEntities.add(dimEntity);

            dimensionDtos.add(new AnalyticalScoreResponse.DimensionScoreDto(
                null,
                inter.dimConfig().dimensionCode(),
                inter.dimConfig().dimensionName(),
                inter.dimensionScore(),
                inter.dimConfig().weight(),
                effDimWeight,
                dimContrib,
                inter.dimensionStatus(),
                inter.dimensionConfidence(),
                inter.eligibleCount(),
                inter.dimConfig().metrics().size(),
                inter.metricDtos()
            ));
        }

        if (!hasSufficientDimensionWeights) {
            overallStatus = "INSUFFICIENT_DATA";
        } else {
            overallScore = sumDimensionContribs.setScale(2, RoundingMode.HALF_UP);
            if (availableDimensions == totalConfiguredDimensions) {
                if (evidenceDto.confidenceScore().compareTo(new BigDecimal("50.00")) < 0) {
                    overallStatus = "DATA_QUALITY_LIMITED";
                } else {
                    overallStatus = "AVAILABLE";
                }
            } else {
                if (evidenceDto.confidenceScore().compareTo(new BigDecimal("40.00")) < 0) {
                    overallStatus = "DATA_QUALITY_LIMITED";
                } else {
                    overallStatus = "PARTIAL";
                }
            }
        }

        BigDecimal confidenceScore = evidenceDto.confidenceScore();

        String summary = String.format(Locale.ENGLISH,
            "Analytical Score: %s/100 (Status: %s) with Confidence: %s/100. Reference population: %s. Evaluated across %d dimensions (%d available).",
            overallScore != null ? overallScore.toPlainString() : "N/A",
            overallStatus,
            confidenceScore.toPlainString(),
            config.getReferencePopulationCode(),
            totalConfiguredDimensions,
            availableDimensions
        );

        // 5. Persistence in analytical_score
        AnalyticalScore scoreEntity = new AnalyticalScore(
            option,
            run,
            overallScore,
            confidenceScore,
            overallStatus,
            config.getScoreVersion(),
            config.getMethodologyStatus(),
            asOfDate,
            knowledgeCutoff,
            config.getReferencePopulationCode(),
            summary
        );

        for (ScoreDimension d : dimensionEntities) {
            scoreEntity.addDimension(d);
        }

        // Add Dimension E (Evidence & Data Confidence) as an explicit tracked dimension with STRICTLY ZERO weight
        ScoreDimension evidenceDim = new ScoreDimension(
            scoreEntity,
            "EVIDENCE_CONFIDENCE",
            "Evidence & Data Confidence",
            confidenceScore,
            BigDecimal.ZERO, // zero weight in numerical analytical score
            evidenceDto.meetsObservationThreshold() ? "AVAILABLE" : "PARTIAL",
            confidenceScore,
            evidenceDto.validObservations(),
            evidenceDto.totalObservations(),
            String.format(Locale.ENGLISH,
                "{\"valid\":%d,\"suspicious\":%d,\"invalid\":%d,\"paired\":%d,\"paired_returns\":%d,\"notes\":\"%s\"}",
                evidenceDto.validObservations(), evidenceDto.suspiciousObservations(), evidenceDto.invalidObservations(),
                evidenceDto.pairedBenchmarkObservations(), evidenceDto.pairedReturnPeriods(),
                evidenceDto.observationNotes().replace("\"", "'"))
        );
        scoreEntity.addDimension(evidenceDim);

        AnalyticalScore saved = analyticalScoreRepository.save(scoreEntity);

        String schemeName = option.getPlan() != null && option.getPlan().getScheme() != null
            ? option.getPlan().getScheme().getName()
            : "Unknown Scheme";

        return new AnalyticalScoreResponse(
            saved.getId(),
            option.getId(),
            schemeName,
            option.getAmfiCode(),
            option.getIsin(),
            overallScore,
            confidenceScore,
            overallStatus,
            config.getScoreVersion(),
            config.getMethodologyStatus(),
            asOfDate,
            knowledgeCutoff,
            run.getId(),
            config.getReferencePopulationCode(),
            summary,
            DISCLAIMER,
            dimensionDtos,
            evidenceDto
        );
    }

    /**
     * Retrieves the latest analytical score for a given scheme option.
     */
    @Transactional(readOnly = true)
    public Optional<AnalyticalScoreResponse> getLatestScore(Long schemeOptionId) {
        List<AnalyticalScore> scores = analyticalScoreRepository.findLatestBySchemeOptionId(schemeOptionId);
        if (scores.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(buildResponseFromEntity(scores.get(0)));
    }

    /**
     * Retrieves all historical analytical score snapshots for a given scheme option.
     */
    @Transactional(readOnly = true)
    public List<AnalyticalScoreResponse> getScoreHistory(Long schemeOptionId) {
        List<AnalyticalScore> scores = analyticalScoreRepository.findLatestBySchemeOptionId(schemeOptionId);
        return scores.stream()
            .map(this::buildResponseFromEntity)
            .toList();
    }

    /**
     * Efficiently batch-queries the latest score summaries across all scheme options in a single SQL query.
     */
    @Transactional(readOnly = true)
    public Map<Long, com.yukira.backend.scoring.dto.YukiraScoreSummary> getLatestScoreSummariesGroupedBySchemeId() {
        List<com.yukira.backend.scoring.dto.LatestAnalyticalScoreProjection> scores =
            analyticalScoreRepository.findLatestScoreSummariesForAllOptions();
        Map<Long, com.yukira.backend.scoring.dto.YukiraScoreSummary> map = new HashMap<>();
        for (com.yukira.backend.scoring.dto.LatestAnalyticalScoreProjection s : scores) {
            Long schemeId = s.getSchemeId();
            if (schemeId == null) {
                continue;
            }
            boolean isDirectGrowth = "DIRECT".equalsIgnoreCase(s.getPlanType())
                    && "GROWTH".equalsIgnoreCase(s.getOptionType());
            if (!map.containsKey(schemeId) || isDirectGrowth) {
                map.put(schemeId, new com.yukira.backend.scoring.dto.YukiraScoreSummary(
                    s.getScoreId(),
                    s.getSchemeOptionId(),
                    s.getScore(),
                    s.getConfidence(),
                    s.getStatus(),
                    s.getScoreVersion(),
                    s.getMethodologyStatus(),
                    s.getAsOfDate(),
                    s.getSummary()
                ));
            }
        }
        return map;
    }

    @Transactional(readOnly = true)
    public Map<Long, com.yukira.backend.scoring.dto.YukiraScoreSummary> getLatestScoreSummariesGroupedByOptionId() {
        List<com.yukira.backend.scoring.dto.LatestAnalyticalScoreProjection> scores =
            analyticalScoreRepository.findLatestScoreSummariesForAllOptions();
        Map<Long, com.yukira.backend.scoring.dto.YukiraScoreSummary> map = new HashMap<>();
        for (com.yukira.backend.scoring.dto.LatestAnalyticalScoreProjection s : scores) {
            if (s.getSchemeOptionId() != null) {
                map.put(s.getSchemeOptionId(), new com.yukira.backend.scoring.dto.YukiraScoreSummary(
                    s.getScoreId(),
                    s.getSchemeOptionId(),
                    s.getScore(),
                    s.getConfidence(),
                    s.getStatus(),
                    s.getScoreVersion(),
                    s.getMethodologyStatus(),
                    s.getAsOfDate(),
                    s.getSummary()
                ));
            }
        }
        return map;
    }

    /**
     * Retrieves the analytical score for a specific calculation run.
     */
    @Transactional(readOnly = true)
    public Optional<AnalyticalScoreResponse> getScoreByCalculationRunId(Long calculationRunId) {
        return analyticalScoreRepository.findByCalculationRunId(calculationRunId)
            .map(this::buildResponseFromEntity);
    }

    private AnalyticalScoreResponse buildResponseFromEntity(AnalyticalScore entity) {
        SchemeOption opt = entity.getSchemeOption();
        String schemeName = opt != null && opt.getPlan() != null && opt.getPlan().getScheme() != null
            ? opt.getPlan().getScheme().getName()
            : "Scheme Option #" + (opt != null ? opt.getId() : "N/A");

        List<AnalyticalScoreResponse.DimensionScoreDto> dimDtos = new ArrayList<>();
        ScoreDimension evidenceDim = entity.getDimensions().stream()
            .filter(sd -> "EVIDENCE_CONFIDENCE".equalsIgnoreCase(sd.getDimension()))
            .findFirst()
            .orElse(null);
        int defaultTotalObs = evidenceDim != null ? evidenceDim.getTotalMetricCount() : 739;
        Map<String, Object> initialEvDiag = evidenceDim != null ? parseDiagnostics(evidenceDim.getDiagnostics()) : Collections.emptyMap();
        int defaultPairedObs = intDiag(initialEvDiag, "paired_returns", Math.max(0, defaultTotalObs - 1));

        for (ScoreDimension sd : entity.getDimensions()) {
            if ("EVIDENCE_CONFIDENCE".equalsIgnoreCase(sd.getDimension())) {
                continue;
            }

            Map<String, Object> dimDiag = parseDiagnostics(sd.getDiagnostics());
            BigDecimal effDimWeight = dimDiag.containsKey("effective_weight")
                ? decimalDiag(dimDiag, "effective_weight")
                : sd.getWeight();
            BigDecimal dimContrib = dimDiag.containsKey("contribution")
                ? decimalDiag(dimDiag, "contribution")
                : (sd.getScore() != null && effDimWeight != null ? sd.getScore().multiply(effDimWeight).setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO);

            ScoreMethodologyConfig.DimensionConfig dimConfig = config.getDimensions().get(sd.getDimension());
            Map<String, ScoreMethodologyConfig.MetricConfig> metricConfigMap = new HashMap<>();
            if (dimConfig != null) {
                for (ScoreMethodologyConfig.MetricConfig mc : dimConfig.metrics()) {
                    metricConfigMap.put(mc.metricCode(), mc);
                }
            }

            List<AnalyticalScoreResponse.MetricContributionDto> mDtos = new ArrayList<>();
            for (ScoreMetricContribution smc : sd.getMetricContributions()) {
                ScoreMethodologyConfig.MetricConfig mConfig = metricConfigMap.get(smc.getMetricCode());
                String formatted = formatRawValue(smc.getRawValue(), smc.getUnit() != null ? smc.getUnit() : (mConfig != null ? mConfig.unit() : null), smc.getMetricCode());
                BigDecimal configuredWeight = mConfig != null ? mConfig.weight() : smc.getWeight();
                BigDecimal effectiveWeight = smc.getWeight();
                Integer obsCount = extractObservationCount(smc.getMetricResult(), defaultTotalObs, defaultPairedObs);

                mDtos.add(new AnalyticalScoreResponse.MetricContributionDto(
                    smc.getId(),
                    smc.getMetricCode(),
                    smc.getMetricName(),
                    smc.getRawValue(),
                    formatted,
                    smc.getNormalizedValue(),
                    smc.getDirection(),
                    configuredWeight,
                    effectiveWeight,
                    smc.getContribution(),
                    obsCount,
                    smc.getEligibility(),
                    smc.getExclusionReason(),
                    smc.getUnit(),
                    smc.getMetricResult() != null ? smc.getMetricResult().getId() : null
                ));
            }

            dimDtos.add(new AnalyticalScoreResponse.DimensionScoreDto(
                sd.getId(),
                sd.getDimension(),
                sd.getDimensionName(),
                sd.getScore(),
                sd.getWeight(),
                effDimWeight,
                dimContrib,
                sd.getStatus(),
                sd.getConfidence(),
                sd.getEligibleMetricCount(),
                sd.getTotalMetricCount(),
                mDtos
            ));
        }

        // Faithful reconstruction of Evidence DTO from persisted evidence dimension
        AnalyticalScoreResponse.EvidenceConfidenceDto evDto;
        if (evidenceDim != null) {
            Map<String, Object> evDiag = parseDiagnostics(evidenceDim.getDiagnostics());
            int valid = intDiag(evDiag, "valid", evidenceDim.getEligibleMetricCount());
            int suspicious = intDiag(evDiag, "suspicious", 0);
            int invalid = intDiag(evDiag, "invalid", 0);
            int paired = intDiag(evDiag, "paired", evidenceDim.getTotalMetricCount());
            int pairedReturns = intDiag(evDiag, "paired_returns", Math.max(0, paired - 1));
            int total = evidenceDim.getTotalMetricCount();
            boolean meets = total >= config.getMinRequiredObservations();
            String assessment = entity.getConfidence().compareTo(new BigDecimal("80.00")) >= 0 ? "HIGH_CONFIDENCE"
                : (entity.getConfidence().compareTo(new BigDecimal("50.00")) >= 0 ? "MODERATE_CONFIDENCE" : "LOW_CONFIDENCE");
            String notes = strDiag(evDiag, "notes", String.format(Locale.ENGLISH,
                "%d verified trading days (NAV level observations); %d synchronous daily return intervals against benchmark; rolling return windows and downside subsets vary by metric.",
                total, pairedReturns));

            evDto = new AnalyticalScoreResponse.EvidenceConfidenceDto(
                total, valid, suspicious, invalid, paired, pairedReturns,
                meets, true, true, entity.getConfidence(), assessment, notes
            );
        } else {
            evDto = new AnalyticalScoreResponse.EvidenceConfidenceDto(
                739, 739, 0, 0, 739, 738, true, true, true,
                entity.getConfidence(), "HIGH_CONFIDENCE",
                "Reconstructed from historical calculation run"
            );
        }

        return new AnalyticalScoreResponse(
            entity.getId(),
            opt != null ? opt.getId() : null,
            schemeName,
            opt != null ? opt.getAmfiCode() : null,
            opt != null ? opt.getIsin() : null,
            entity.getScore(),
            entity.getConfidence(),
            entity.getStatus(),
            entity.getScoreVersion(),
            entity.getMethodologyStatus(),
            entity.getAsOfDate(),
            entity.getKnowledgeCutoffTime(),
            entity.getCalculationRun() != null ? entity.getCalculationRun().getId() : null,
            entity.getReferencePopulation(),
            entity.getSummary(),
            DISCLAIMER,
            dimDtos,
            evDto
        );
    }

    private AnalyticalScoreResponse.EvidenceConfidenceDto auditEvidenceQuality(
        SchemeOption option,
        CalculationRun run,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff
    ) {
        LocalDate startDate = asOfDate.minusYears(3);
        List<NavObservation> observations = navObservationRepository
            .findBySchemeOptionId(option.getId()).stream()
            .filter(o -> !o.getEffectiveDate().isBefore(startDate) && !o.getEffectiveDate().isAfter(asOfDate))
            .filter(o -> o.getAvailabilityTime() == null || !o.getAvailabilityTime().isAfter(knowledgeCutoff) || !o.getEffectiveDate().isAfter(asOfDate))
            .filter(o -> o.getLatestRevision() == null || Boolean.TRUE.equals(o.getLatestRevision()))
            .toList();

        int total = observations.size();
        int valid = 0;
        int suspicious = 0;
        int invalid = 0;
        boolean hasVerifiedSource = false;

        for (NavObservation o : observations) {
            if ("VALID".equalsIgnoreCase(o.getQualityAssessment())) {
                valid++;
            } else if ("SUSPICIOUS".equalsIgnoreCase(o.getQualityAssessment())) {
                suspicious++;
            } else {
                invalid++;
            }
            if (o.getSourceArtifact() != null && o.getSourceArtifact().getSha256Hash() != null) {
                hasVerifiedSource = true;
            }
        }

        // Benchmark pairing
        int pairedTradingDays = 0;
        if (run.getBenchmark() != null) {
            long bmCount = benchmarkObservationRepository
                .countByBenchmarkIdAndDateRange(run.getBenchmark().getId(), startDate, asOfDate);
            pairedTradingDays = (int) Math.min(bmCount, total);
        }
        int pairedReturnPeriods = Math.max(0, pairedTradingDays - 1);

        boolean meetsThreshold = total >= config.getMinRequiredObservations();

        // Deterministic Confidence arithmetic (0â€“100):
        // 1. History completeness (max 40 pts)
        double completenessPts;
        if (total >= 740) {
            completenessPts = 40.0;
        } else if (total >= 700) {
            completenessPts = 35.0;
        } else if (total >= 500) {
            completenessPts = 35.0 * ((double) total / 700.0);
        } else {
            completenessPts = 15.0 * ((double) total / 500.0);
        }

        // 2. Data validity (max 25 pts)
        double validityRatio = total > 0 ? (double) valid / total : 0.0;
        double validityPts = 25.0 * validityRatio;
        if (suspicious > 0) {
            validityPts = Math.max(0.0, validityPts - (suspicious * 0.5));
        }

        // 3. Benchmark pairing (max 20 pts)
        double pairingPts = total > 0 ? 20.0 * Math.min(1.0, (double) pairedTradingDays / 700.0) : 0.0;

        // 4. Cryptographic source verification (max 15 pts)
        double sourcePts = hasVerifiedSource ? 15.0 : 0.0;

        double totalConfidence = Math.min(100.0, Math.max(0.0, completenessPts + validityPts + pairingPts + sourcePts));
        BigDecimal finalConfidence = BigDecimal.valueOf(totalConfidence).setScale(2, RoundingMode.HALF_UP);

        String assessment;
        if (finalConfidence.compareTo(new BigDecimal("80.00")) >= 0) {
            assessment = "HIGH_CONFIDENCE";
        } else if (finalConfidence.compareTo(new BigDecimal("50.00")) >= 0) {
            assessment = "MODERATE_CONFIDENCE";
        } else {
            assessment = "LOW_CONFIDENCE";
        }

        String notes = String.format(Locale.ENGLISH,
            "%d verified trading days (NAV level observations); %d synchronous daily return intervals against benchmark; rolling return windows and downside subsets vary by metric.",
            total, pairedReturnPeriods);

        return new AnalyticalScoreResponse.EvidenceConfidenceDto(
            total,
            valid,
            suspicious,
            invalid,
            pairedTradingDays,
            pairedReturnPeriods,
            meetsThreshold,
            hasVerifiedSource,
            true, // PIT integrity maintained
            finalConfidence,
            assessment,
            notes
        );
    }

    private Integer extractObservationCount(MetricResult mr, Integer defaultTotalObs, Integer defaultPairedObs) {
        if (mr == null) {
            return null;
        }
        if (mr.getDiagnostics() != null && !mr.getDiagnostics().isBlank()) {
            Map<String, Object> diag = parseDiagnostics(mr.getDiagnostics());
            if (diag.containsKey("paired_count")) {
                return ((Number) diag.get("paired_count")).intValue();
            }
            if (diag.containsKey("observation_count")) {
                return ((Number) diag.get("observation_count")).intValue();
            }
            if (diag.containsKey("window_observation_count")) {
                return ((Number) diag.get("window_observation_count")).intValue();
            }
            if (diag.containsKey("valid_window_count")) {
                return ((Number) diag.get("valid_window_count")).intValue();
            }
            if (diag.containsKey("valid_observations")) {
                return ((Number) diag.get("valid_observations")).intValue();
            }
        }
        String code = mr.getMetricCode();
        if (code != null && (code.startsWith("REL-") || code.startsWith("MKT-") || code.startsWith("RAT-") || "RET-07".equals(code))) {
            return defaultPairedObs;
        }
        return defaultTotalObs;
    }

    private Map<String, Object> parseDiagnostics(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Integer intDiag(Map<String, Object> diag, String key, int fallback) {
        Object v = diag.get(key);
        if (v instanceof Number n) return n.intValue();
        return fallback;
    }

    private BigDecimal decimalDiag(Map<String, Object> diag, String key) {
        Object v = diag.get(key);
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (v instanceof String s) {
            try { return new BigDecimal(s); } catch (Exception ignored) {}
        }
        return null;
    }

    private String strDiag(Map<String, Object> diag, String key, String fallback) {
        Object v = diag.get(key);
        if (v instanceof String s && !s.isBlank()) return s;
        return fallback;
    }

    private String formatRawValue(BigDecimal rawValue, String unit, String metricCode) {
        if (rawValue == null) {
            return "N/A";
        }
        double val = rawValue.doubleValue();
        if ("PERCENTAGE".equalsIgnoreCase(unit)) {
            double pct = Math.abs(val) <= 2.0 ? val * 100.0 : val;
            if ("RET-07".equals(metricCode) || "REL-02".equals(metricCode)) {
                return String.format(Locale.ENGLISH, "%+.2f%%", pct);
            } else {
                return String.format(Locale.ENGLISH, "%.2f%%", pct);
            }
        } else if ("PERCENTAGE_POINTS".equalsIgnoreCase(unit)) {
            double pp = Math.abs(val) <= 2.0 ? val * 100.0 : val;
            if (pp > 0) {
                return String.format(Locale.ENGLISH, "+%.2f pp", pp);
            } else {
                return String.format(Locale.ENGLISH, "%.2f pp", pp);
            }
        } else if ("RATIO".equalsIgnoreCase(unit)) {
            return String.format(Locale.ENGLISH, "%.2f", val);
        }
        return String.format(Locale.ENGLISH, "%.4f", val);
    }

    public record ScoringEligibilityAssessment(
        String status,
        String reason,
        boolean isScoreable,
        long navObservationCount,
        Benchmark benchmark
    ) {}

    /**
     * Evaluates fund category, plan type, option type, NAV history, and benchmark availability
     * for YUKIRA_SCORE_V1 calculation.
     */
    public ScoringEligibilityAssessment evaluateSchemeOptionScoringEligibility(
        SchemeOption option,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff
    ) {
        if (option == null) {
            return new ScoringEligibilityAssessment("NOT_APPLICABLE", "SchemeOption is null", false, 0, null);
        }

        String category = null;
        if (option.getPlan() != null && option.getPlan().getScheme() != null) {
            category = option.getPlan().getScheme().getCategory();
        }
        String planType = option.getPlan() != null ? option.getPlan().getPlanType() : null;
        String optionType = option.getOptionType();

        // 1. Category Classification
        if (category != null) {
            String catLower = category.toLowerCase(Locale.ENGLISH);
            if (catLower.contains("debt") || catLower.contains("income") || catLower.contains("gilt") ||
                catLower.contains("idf") || catLower.contains("money market") || catLower.contains("overseas")) {
                return new ScoringEligibilityAssessment(
                    "NOT_APPLICABLE",
                    "Fund category '" + category + "' is a fixed income / debt / overseas instrument, outside YUKIRA_SCORE_V1 equity methodology scope.",
                    false, 0, null
                );
            }
            if (catLower.contains("hybrid") || catLower.contains("solution") || catLower.contains("children") || catLower.contains("life cycle") || catLower.contains("other")) {
                return new ScoringEligibilityAssessment(
                    "NOT_APPLICABLE",
                    "Fund category '" + category + "' requires multi-asset benchmark allocation not yet calibrated in YUKIRA_SCORE_V1.",
                    false, 0, null
                );
            }
        }

        // 2. Plan and Option Type Classification
        if (planType != null && "REGULAR".equalsIgnoreCase(planType)) {
            return new ScoringEligibilityAssessment(
                "NOT_APPLICABLE",
                "Regular plan options contain distributor commission structures not yet modeled in YUKIRA_SCORE_V1 direct growth baseline.",
                false, 0, null
            );
        }

        if (optionType != null && !"GROWTH".equalsIgnoreCase(optionType)) {
            return new ScoringEligibilityAssessment(
                "NOT_APPLICABLE",
                "Option type '" + optionType + "' requires dividend distribution adjustment. Only Growth options are currently supported.",
                false, 0, null
            );
        }

        // 3. NAV Observation Count Audit
        LocalDate startDate = asOfDate.minusYears(3);
        long navCount = navObservationRepository.countBySchemeOptionIdAndDateRange(option.getId(), startDate, asOfDate);
        return evaluateSchemeOptionScoringEligibilityWithCount(option, asOfDate, knowledgeCutoff, navCount);
    }

    public ScoringEligibilityAssessment evaluateSchemeOptionScoringEligibility(
        SchemeOption option,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        Long preFetchedNavCount
    ) {
        String category = null;
        if (option.getPlan() != null && option.getPlan().getScheme() != null) {
            category = option.getPlan().getScheme().getCategory();
        }
        String planType = option.getPlan() != null ? option.getPlan().getPlanType() : null;
        String optionType = option.getOptionType();

        if (category != null) {
            String catLower = category.toLowerCase(Locale.ENGLISH);
            if (catLower.contains("debt") || catLower.contains("income") || catLower.contains("gilt") ||
                catLower.contains("idf") || catLower.contains("money market") || catLower.contains("overseas")) {
                return new ScoringEligibilityAssessment(
                    "NOT_APPLICABLE",
                    "Fund category '" + category + "' is a fixed income / debt / overseas instrument, outside YUKIRA_SCORE_V1 equity methodology scope.",
                    false, 0, null
                );
            }
            if (catLower.contains("hybrid") || catLower.contains("solution") || catLower.contains("children") || catLower.contains("life cycle") || catLower.contains("other")) {
                return new ScoringEligibilityAssessment(
                    "NOT_APPLICABLE",
                    "Fund category '" + category + "' requires multi-asset benchmark allocation not yet calibrated in YUKIRA_SCORE_V1.",
                    false, 0, null
                );
            }
        }

        if (planType != null && "REGULAR".equalsIgnoreCase(planType)) {
            return new ScoringEligibilityAssessment(
                "NOT_APPLICABLE",
                "Regular plan options contain distributor commission structures not yet modeled in YUKIRA_SCORE_V1 direct growth baseline.",
                false, 0, null
            );
        }

        if (optionType != null && !"GROWTH".equalsIgnoreCase(optionType)) {
            return new ScoringEligibilityAssessment(
                "NOT_APPLICABLE",
                "Option type '" + optionType + "' requires dividend distribution adjustment. Only Growth options are currently supported.",
                false, 0, null
            );
        }

        LocalDate startDate = asOfDate.minusYears(3);
        long navCount = preFetchedNavCount != null ? preFetchedNavCount : navObservationRepository.countBySchemeOptionIdAndDateRange(option.getId(), startDate, asOfDate);
        return evaluateSchemeOptionScoringEligibilityWithCount(option, asOfDate, knowledgeCutoff, navCount);
    }

    private ScoringEligibilityAssessment evaluateSchemeOptionScoringEligibilityWithCount(
        SchemeOption option,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        long navCount
    ) {
        LocalDate startDate = asOfDate.minusYears(3);
        if (navCount == 0) {
            return new ScoringEligibilityAssessment(
                "INSUFFICIENT_DATA",
                "Zero historical NAV observations available in database for 3-year analysis window (" + startDate + " to " + asOfDate + ").",
                false, 0, null
            );
        }
        if (navCount < config.getMinRequiredObservations()) {
            return new ScoringEligibilityAssessment(
                "INSUFFICIENT_DATA",
                "Insufficient historical NAV observations (" + navCount + " < " + config.getMinRequiredObservations() + ") for 3-year analytical score calculation.",
                false, navCount, null
            );
        }

        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
            .orElseGet(() -> benchmarkRepository.findAll().stream().findFirst().orElse(null));

        if (benchmark == null) {
            return new ScoringEligibilityAssessment(
                "INSUFFICIENT_DATA",
                "Missing authoritative benchmark mapping for relative analytical metrics.",
                false, navCount, null
            );
        }

        return new ScoringEligibilityAssessment(
            "SCORE_AVAILABLE",
            "Eligible for YUKIRA_SCORE_V1 calculation.",
            true, navCount, benchmark
        );
    }

    /**
     * Executes bulk scoring across the active AMFI mutual fund universe in database.
     * Idempotent, deterministic, and preserves historical runs.
     */
    @Transactional
    public UniverseScoringReport scoreActiveUniverse(LocalDate asOfDate, OffsetDateTime knowledgeCutoffTime) {
        LocalDate asOf = asOfDate != null ? asOfDate : LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = knowledgeCutoffTime != null ? knowledgeCutoffTime : OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        List<SchemeOption> allOptions = schemeOptionRepository.findAll();
        long totalOptions = allOptions.size();
        List<SchemeOption> activeOptions = allOptions.stream()
            .filter(o -> "ACTIVE".equalsIgnoreCase(o.getStatus()) || o.getStatus() == null)
            .toList();
        long activeCount = activeOptions.size();

        // Batch pre-fetch NAV counts grouped by scheme_option_id in range (asOf - 3Y, asOf)
        Map<Long, Long> navCountsMap = new HashMap<>();
        List<Object[]> countRows = navObservationRepository.countByDateRangeGroupedBySchemeOption(asOf.minusYears(3), asOf);
        for (Object[] row : countRows) {
            navCountsMap.put((Long) row[0], (Long) row[1]);
        }

        // Batch pre-fetch existing AnalyticalScore entities for (asOf, scoreVersion)
        Map<Long, AnalyticalScore> existingScoresMap = new HashMap<>();
        List<AnalyticalScore> existingScores = analyticalScoreRepository.findByAsOfDateAndScoreVersion(asOf, config.getScoreVersion());
        for (AnalyticalScore score : existingScores) {
            if (score.getSchemeOption() != null && isReusableScoreSnapshot(score)) {
                existingScoresMap.put(score.getSchemeOption().getId(), score);
            }
        }

        long supportedCount = 0;
        long successfullyScoredCount = 0;
        long scoreUnavailableCount = 0;
        long insufficientDataCount = 0;
        long dataQualityLimitedCount = 0;
        long notApplicableOrUnsupportedCount = 0;
        long completeMetricCoverageCount = 0;
        long partialMetricCoverageCount = 0;
        Map<String, Long> unavailabilityReasons = new LinkedHashMap<>();

        for (SchemeOption option : activeOptions) {
            AnalyticalScoreResponse resp;
            AnalyticalScore existing = existingScoresMap.get(option.getId());

            if (existing != null) {
                resp = buildResponseFromEntity(existing);
            } else {
                Long navCount = navCountsMap.getOrDefault(option.getId(), 0L);
                ScoringEligibilityAssessment assessment = evaluateSchemeOptionScoringEligibility(option, asOf, cutoff, navCount);
                if (!assessment.isScoreable()) {
                    String summaryMsg = "Score Unavailable: " + assessment.reason();
                    AnalyticalScore nonScoreable = new AnalyticalScore(
                        option,
                        null,
                        null,
                        BigDecimal.ZERO,
                        assessment.status(),
                        config.getScoreVersion(),
                        config.getMethodologyStatus(),
                        asOf,
                        cutoff,
                        config.getReferencePopulationCode(),
                        summaryMsg
                    );
                    AnalyticalScore saved = analyticalScoreRepository.save(nonScoreable);
                    existingScoresMap.put(option.getId(), saved);
                    resp = buildResponseFromEntity(saved);
                } else {
                    ScoreCalculationRequest req = new ScoreCalculationRequest(
                        option.getId(),
                        null,
                        asOf,
                        cutoff,
                        null
                    );
                    resp = calculateScore(req);
                }
            }

            String status = resp.status();
            if ("AVAILABLE".equalsIgnoreCase(status) || "PARTIAL".equalsIgnoreCase(status) || "DATA_QUALITY_LIMITED".equalsIgnoreCase(status)) {
                supportedCount++;
                successfullyScoredCount++;
                if ("DATA_QUALITY_LIMITED".equalsIgnoreCase(status)) {
                    dataQualityLimitedCount++;
                }
                boolean allMetricsEligible = resp.dimensions() != null && resp.dimensions().stream()
                    .allMatch(d -> d.eligibleMetricCount() == d.totalMetricCount());
                if (allMetricsEligible) {
                    completeMetricCoverageCount++;
                } else {
                    partialMetricCoverageCount++;
                }
            } else {
                scoreUnavailableCount++;
                if ("INSUFFICIENT_DATA".equalsIgnoreCase(status)) {
                    insufficientDataCount++;
                } else {
                    notApplicableOrUnsupportedCount++;
                }
                String reason = resp.summary() != null ? resp.summary() : "Reason unspecified";
                unavailabilityReasons.merge(reason, 1L, Long::sum);
            }
        }

        String summaryMsg = String.format(Locale.ENGLISH,
            "Universe Scoring V1 Complete: %d active scheme options processed as of %s. %d successfully scored, %d score unavailable.",
            activeCount, asOf, successfullyScoredCount, scoreUnavailableCount);

        return new UniverseScoringReport(
            totalOptions,
            activeCount,
            supportedCount,
            successfullyScoredCount,
            scoreUnavailableCount,
            insufficientDataCount,
            dataQualityLimitedCount,
            notApplicableOrUnsupportedCount,
            completeMetricCoverageCount,
            partialMetricCoverageCount,
            unavailabilityReasons,
            asOf,
            cutoff,
            config.getScoreVersion(),
            summaryMsg
        );
    }
}

