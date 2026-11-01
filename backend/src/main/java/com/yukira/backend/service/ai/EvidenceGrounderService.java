package com.yukira.backend.service.ai;

import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.dto.ai.GroundedEvidencePackageDto;
import com.yukira.backend.dto.analysis.AnalyticalProfileResponse;
import com.yukira.backend.dto.analysis.AnalyticalProfileResponse.ProfileMetricItem;
import com.yukira.backend.dto.analysis.DataQualityAuditResponse;
import com.yukira.backend.dto.analysis.ProfileCalculationRequest;
import com.yukira.backend.dto.portfolio.PortfolioSummaryDto;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
import com.yukira.backend.service.AnalysisService;
import com.yukira.backend.service.portfolio.InvestorPortfolioService;
import com.yukira.backend.universe.AnalyticalHorizon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Deterministic Evidence Grounding Service for AI Interpretation Layer.
 *
 * Epistemic Contract:
 * - Extracts strictly verified quantitative data from backend DTOs, CalculationRun, MetricResult,
 *   AnalyticalProfileResponse, and Score models.
 * - Injects ZERO synthetic, estimated, or fabricated financial numbers.
 * - Produces an immutable GroundedEvidencePackageDto as input for LLM interpretation or fallback engine.
 */
@Service
public class EvidenceGrounderService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceGrounderService.class);

    private final CalculationRunRepository calculationRunRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final AnalysisService analysisService;
    private final AnalyticalScoringService scoringService;
    private final InvestorPortfolioService portfolioService;
    private final MetricResultRepository metricResultRepository;
    private final com.yukira.backend.repository.PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final com.yukira.backend.repository.PortfolioHoldingRepository portfolioHoldingRepository;

    @Autowired
    public EvidenceGrounderService(
        CalculationRunRepository calculationRunRepository,
        SchemeOptionRepository schemeOptionRepository,
        AnalysisService analysisService,
        AnalyticalScoringService scoringService,
        @Autowired(required = false) InvestorPortfolioService portfolioService,
        @Autowired(required = false) MetricResultRepository metricResultRepository,
        @Autowired(required = false) com.yukira.backend.repository.PortfolioSnapshotRepository portfolioSnapshotRepository,
        @Autowired(required = false) com.yukira.backend.repository.PortfolioHoldingRepository portfolioHoldingRepository
    ) {
        this.calculationRunRepository = calculationRunRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.analysisService = analysisService;
        this.scoringService = scoringService;
        this.portfolioService = portfolioService;
        this.metricResultRepository = metricResultRepository;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
    }

    public EvidenceGrounderService(
        CalculationRunRepository calculationRunRepository,
        SchemeOptionRepository schemeOptionRepository,
        AnalysisService analysisService,
        AnalyticalScoringService scoringService,
        InvestorPortfolioService portfolioService
    ) {
        this(calculationRunRepository, schemeOptionRepository, analysisService, scoringService, portfolioService, null, null, null);
    }

    /**
     * Builds grounded evidence package for a specific calculation run ID.
     */
    public GroundedEvidencePackageDto buildEvidencePackageForRun(Long runId) {
        CalculationRun run = calculationRunRepository.findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("CalculationRun #" + runId + " not found."));

        SchemeOption option = run.getSchemeOption();
        Long schemeOptionId = option.getId();

        List<String> artifacts = new ArrayList<>();
        if (run.getInputSnapshotSha256() != null) {
            artifacts.add(run.getInputSnapshotSha256());
        }

        String version = run.getMethodologyVersion() != null && run.getMethodologyVersion().getVersionTag() != null
            ? run.getMethodologyVersion().getVersionTag()
            : "CANDIDATE_V1";

        GroundedEvidencePackageDto base = buildEvidencePackageInternal(option, run.getId(),
            run.getAsOfDate() != null ? run.getAsOfDate().toString() : LocalDate.now().toString(),
            run.getKnowledgeCutoffTime() != null ? run.getKnowledgeCutoffTime().toString() : OffsetDateTime.now().toString(),
            version,
            artifacts
        );

        // If direct canonical MetricResults exist for this CalculationRun, ground them into metricValues
        if (metricResultRepository != null) {
            try {
                List<MetricResult> results = metricResultRepository.findCanonicalByCalculationRunId(runId);
                if (results != null && !results.isEmpty()) {
                    Map<String, Object> enrichedMetrics = new LinkedHashMap<>(base.metricValues());
                    for (MetricResult mr : results) {
                        enrichedMetrics.put(mr.getMetricCode(), Map.of(
                            "code", mr.getMetricCode(),
                            "name", mr.getMetricCode(),
                            "value", mr.getNumericValue() != null ? mr.getNumericValue().doubleValue() : null,
                            "formatted", mr.getStringValue() != null ? mr.getStringValue() : (mr.getNumericValue() != null ? mr.getNumericValue().toPlainString() : "N/A"),
                            "unit", mr.getUnits() != null ? mr.getUnits() : "",
                            "periodType", mr.getPeriodType() != null ? mr.getPeriodType() : "3Y",
                            "status", mr.getCalculationStatus() != null ? mr.getCalculationStatus() : "CALCULATED"
                        ));
                    }
                    return new GroundedEvidencePackageDto(
                        base.schemeOptionId(), base.schemeName(), base.amfiCode(), base.isin(),
                        base.category(), base.plan(), base.option(), base.calculationRunId(),
                        base.asOfDate(), base.knowledgeCutoff(), base.overallScore(), base.scoreStatus(),
                        base.dimensionScores(), enrichedMetrics, base.dataQualityFlags(),
                        base.methodologyStatus(), base.sourceArtifactDigests(), base.portfolioContext(),
                        base.horizon(), base.windowStartDate(), base.observationCount(),
                        base.requiredObservationThreshold(), base.benchmarkName(), base.benchmarkCode(),
                        base.benchmarkMappingStatus(), base.multiHorizonSummary(), base.limitations()
                    );
                }
            } catch (Exception e) {
                log.warn("Unable to enrich evidence from MetricResult for run #{}: {}", runId, e.getMessage());
            }
        }

        return base;
    }

    /**
     * Builds grounded evidence package for a scheme option ID.
     */
    public GroundedEvidencePackageDto buildEvidencePackageForSchemeOption(Long schemeOptionId) {
        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("SchemeOption #" + schemeOptionId + " not found."));

        return buildEvidencePackageInternal(option, null, LocalDate.now().toString(), OffsetDateTime.now().toString(), "YUKIRA_SCORE_V1", Collections.emptyList());
    }

    /**
     * Builds grounded evidence package directly from an authoritative AnalyticalProfileResponse.
     * This guarantees 100% synchronization with the profile on screen (same horizon, dates, metrics).
     */
    public GroundedEvidencePackageDto buildEvidencePackageForProfile(AnalyticalProfileResponse profile) {
        if (profile == null || profile.context() == null) {
            throw new IllegalArgumentException("AnalyticalProfileResponse context cannot be null.");
        }

        AnalyticalProfileResponse.ProfileContext ctx = profile.context();
        String horizon = ctx.horizon() != null ? ctx.horizon() : "3Y";
        int threshold = "10Y".equalsIgnoreCase(horizon) ? 2400 : ("5Y".equalsIgnoreCase(horizon) ? 1200 : 700);

        Map<String, Object> metricValues = new LinkedHashMap<>();

        // Helper to collect profile metrics
        List<ProfileMetricItem> allMetrics = new ArrayList<>();
        if (profile.returnMetrics() != null) allMetrics.addAll(profile.returnMetrics());
        if (profile.riskMetrics() != null) allMetrics.addAll(profile.riskMetrics());
        if (profile.riskAdjustedMetrics() != null) allMetrics.addAll(profile.riskAdjustedMetrics());
        if (profile.marketSensitivityMetrics() != null) allMetrics.addAll(profile.marketSensitivityMetrics());

        for (ProfileMetricItem item : allMetrics) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", item.metricCode());
            m.put("name", item.metricName());
            m.put("value", item.numericValue() != null ? item.numericValue().doubleValue() : null);
            m.put("formatted", item.formattedValue() != null ? item.formattedValue() : "N/A");
            m.put("units", item.units() != null ? item.units() : "");
            m.put("periodType", item.periodType() != null ? item.periodType() : horizon);
            m.put("calculationStatus", item.calculationStatus() != null ? item.calculationStatus() : "UNKNOWN");
            m.put("governanceStatus", item.governanceStatus() != null ? item.governanceStatus() : "CANDIDATE");
            m.put("interpretation", item.interpretation() != null ? item.interpretation() : "");
            m.put("limitations", item.limitations() != null ? item.limitations() : "");
            metricValues.put(item.metricCode(), m);
        }

        Map<String, String> dataQualityFlags = new LinkedHashMap<>();
        int obsCount = 0;
        if (profile.quality() != null) {
            dataQualityFlags.put("overallAssessment", profile.quality().overallAssessment());
            if (profile.quality().validationFlags() != null) {
                dataQualityFlags.put("validationFlags", String.join(", ", profile.quality().validationFlags()));
            }
        }
        if (profile.provenance() != null) {
            obsCount = (int) profile.provenance().navObservationCount();
            dataQualityFlags.put("totalObservations", String.valueOf(profile.provenance().navObservationCount()));
        }

        List<String> limitationsList = new ArrayList<>();
        if (profile.limitations() != null) {
            if (profile.limitations().candidateMethodologyDisclaimer() != null) {
                limitationsList.add(profile.limitations().candidateMethodologyDisclaimer());
            }
        }

        List<String> sourceArtifacts = profile.provenance() != null && profile.provenance().inputSnapshotSha256() != null
            ? List.of(profile.provenance().inputSnapshotSha256())
            : Collections.emptyList();

        String benchmarkMappingStatus = "CANDIDATE";

        String methodologyStatus = "3Y".equalsIgnoreCase(horizon) ? "APPROVED_BASELINE" : "CANDIDATE_MULTI_CYCLE";

        Map<String, Object> portfolioContext = new LinkedHashMap<>();
        if (portfolioSnapshotRepository != null && portfolioHoldingRepository != null && ctx.schemeOptionId() != null) {
            try {
                var snapOpt = portfolioSnapshotRepository.findTopBySchemeOptionIdOrderByPortfolioDateDescRevisionSeqDesc(ctx.schemeOptionId());
                if (snapOpt.isPresent()) {
                    var snap = snapOpt.get();
                    portfolioContext.put("portfolioDate", snap.getPortfolioDate() != null ? snap.getPortfolioDate().toString() : null);
                    var holdings = portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snap.getId());
                    if (holdings != null && !holdings.isEmpty()) {
                        portfolioContext.put("holdingCount", holdings.size());
                        double top5 = 0.0;
                        double top10 = 0.0;
                        List<String> topNames = new ArrayList<>();
                        for (int i = 0; i < holdings.size(); i++) {
                            var h = holdings.get(i);
                            double w = h.getReportedWeight() != null ? h.getReportedWeight().doubleValue() : 0.0;
                            if (i < 5) top5 += w;
                            if (i < 10) {
                                top10 += w;
                                if (i < 4 && h.getSecurity() != null && h.getSecurity().getCanonicalName() != null) {
                                    topNames.add(h.getSecurity().getCanonicalName());
                                }
                            }
                        }
                        portfolioContext.put("top5Concentration", top5);
                        portfolioContext.put("top10Concentration", top10);
                        portfolioContext.put("topHoldingNames", String.join(", ", topNames));
                    }
                }
            } catch (Exception ignored) {
                // Non-blocking fallback
            }
        }

        return new GroundedEvidencePackageDto(
            ctx.schemeOptionId(),
            ctx.schemeName() != null ? ctx.schemeName() : "Scheme Option #" + ctx.schemeOptionId(),
            ctx.amfiCode() != null ? ctx.amfiCode() : "N/A",
            ctx.isin() != null ? ctx.isin() : "N/A",
            "Equity",
            ctx.planType() != null ? ctx.planType() : "DIRECT",
            ctx.optionType() != null ? ctx.optionType() : "GROWTH",
            ctx.runId(),
            ctx.asOfDate() != null ? ctx.asOfDate().toString() : LocalDate.now().toString(),
            ctx.knowledgeCutoffTime() != null ? ctx.knowledgeCutoffTime().toString() : OffsetDateTime.now().toString(),
            null,
            "ANALYTICAL_PROFILE",
            Collections.emptyMap(),
            metricValues,
            dataQualityFlags,
            methodologyStatus,
            sourceArtifacts,
            null,
            horizon,
            ctx.startDate() != null ? ctx.startDate().toString() : null,
            obsCount,
            threshold,
            ctx.benchmarkName() != null ? ctx.benchmarkName() : "NIFTY 500 TRI",
            ctx.benchmarkCode() != null ? ctx.benchmarkCode() : "NIFTY500",
            benchmarkMappingStatus,
            portfolioContext,
            limitationsList
        );
    }

    /**
     * Builds grounded evidence package for a scheme option ID across an explicit horizon and date context.
     */
    public GroundedEvidencePackageDto buildEvidencePackageForProfile(
        Long schemeOptionId,
        AnalyticalHorizon horizon,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff
    ) {
        AnalyticalHorizon effectiveHorizon = horizon != null ? horizon : AnalyticalHorizon.THREE_YEAR;
        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : LocalDate.now();
        OffsetDateTime effectiveCutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();

        ProfileCalculationRequest request = new ProfileCalculationRequest(
            schemeOptionId,
            null,
            effectiveAsOf,
            effectiveCutoff,
            "YUKIRA_PROFILE_V1",
            null,
            null,
            null,
            null,
            effectiveHorizon.getCode()
        );

        AnalyticalProfileResponse profile = analysisService.executeProfileAnalysis(request);
        return buildEvidencePackageForProfile(profile);
    }

    private GroundedEvidencePackageDto buildEvidencePackageInternal(
        SchemeOption option,
        Long runIdOverride,
        String defaultAsOfDate,
        String defaultKnowledgeCutoff,
        String defaultMethodology,
        List<String> defaultArtifacts
    ) {
        Long schemeOptionId = option.getId();
        Map<String, Double> dimensionScores = new LinkedHashMap<>();
        Map<String, Object> metricValues = new LinkedHashMap<>();
        Map<String, String> dataQualityFlags = new LinkedHashMap<>();
        Double overallScore = null;
        String scoreStatus = "NOT_SCORED";
        Long calculationRunId = runIdOverride;
        String asOfDate = defaultAsOfDate;
        String knowledgeCutoff = defaultKnowledgeCutoff;
        String methodologyStatus = defaultMethodology;

        try {
            Optional<AnalyticalScoreResponse> scoreOpt = scoringService.getLatestScore(schemeOptionId);
            if (scoreOpt.isPresent()) {
                AnalyticalScoreResponse score = scoreOpt.get();
                if (calculationRunId == null) calculationRunId = score.calculationRunId();
                if (score.asOfDate() != null) asOfDate = score.asOfDate().toString();
                if (score.knowledgeCutoffTime() != null) knowledgeCutoff = score.knowledgeCutoffTime().toString();
                if (score.methodologyStatus() != null) methodologyStatus = score.methodologyStatus();
                if (score.score() != null) overallScore = score.score().doubleValue();
                scoreStatus = score.status();

                if (score.dimensions() != null) {
                    for (AnalyticalScoreResponse.DimensionScoreDto d : score.dimensions()) {
                        if (d.score() != null) {
                            dimensionScores.put(d.dimensionName() != null ? d.dimensionName() : d.dimension(), d.score().doubleValue());
                        }
                        if (d.metricContributions() != null) {
                            for (AnalyticalScoreResponse.MetricContributionDto m : d.metricContributions()) {
                                if (m.rawValue() != null) {
                                    metricValues.put(m.metricCode(), Map.of(
                                        "code", m.metricCode(),
                                        "name", m.metricName(),
                                        "value", m.rawValue().doubleValue(),
                                        "formatted", m.formattedRawValue() != null ? m.formattedRawValue() : m.rawValue().toString(),
                                        "unit", m.unit() != null ? m.unit() : "",
                                        "observationCount", m.observationCount() != null ? m.observationCount() : 0,
                                        "eligibility", m.eligibility() != null ? m.eligibility() : "ELIGIBLE"
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Unable to fetch score for scheme option #{}: {}", schemeOptionId, e.getMessage());
        }

        try {
            DataQualityAuditResponse quality = analysisService.executeDataQualityAudit(schemeOptionId, OffsetDateTime.now());
            if (quality != null && quality.summary() != null) {
                dataQualityFlags.put("overallStatus", quality.summary().overallStatus());
                dataQualityFlags.put("totalObservations", String.valueOf(quality.summary().totalObservations()));
                dataQualityFlags.put("validObservations", String.valueOf(quality.summary().validObservations()));
            }
        } catch (Exception e) {
            log.warn("Data quality audit error for option #{}: {}", schemeOptionId, e.getMessage());
        }

        SchemePlan plan = option.getPlan();
        Scheme scheme = plan != null ? plan.getScheme() : null;

        String schemeName = scheme != null && scheme.getName() != null ? scheme.getName() : "Mutual Fund Option #" + schemeOptionId;
        String amfiCode = option.getAmfiCode() != null ? option.getAmfiCode() : "N/A";
        String isin = option.getIsin() != null ? option.getIsin() : "N/A";
        String category = scheme != null && scheme.getCategory() != null ? scheme.getCategory() : "Equity";
        String planType = plan != null && plan.getPlanType() != null ? plan.getPlanType() : "DIRECT";
        String optionType = option.getOptionType() != null ? option.getOptionType() : "GROWTH";

        return new GroundedEvidencePackageDto(
            schemeOptionId,
            schemeName,
            amfiCode,
            isin,
            category,
            planType,
            optionType,
            calculationRunId,
            asOfDate,
            knowledgeCutoff,
            overallScore,
            scoreStatus,
            dimensionScores,
            metricValues,
            dataQualityFlags,
            methodologyStatus,
            defaultArtifacts,
            null
        );
    }

    /**
     * Builds grounded evidence package for investor portfolio.
     */
    public GroundedEvidencePackageDto buildEvidencePackageForPortfolio(String investorSubject) {
        if (portfolioService == null) {
            throw new IllegalStateException("InvestorPortfolioService is unavailable.");
        }

        PortfolioSummaryDto portfolio = portfolioService.getPortfolioSummary(investorSubject);
        Map<String, Object> portfolioContext = new LinkedHashMap<>();

        portfolioContext.put("totalValuation", portfolio.totalAvailableValue());
        portfolioContext.put("totalCostBasis", portfolio.totalInvestedAmount());
        portfolioContext.put("totalUnrealizedGain", portfolio.totalAbsoluteGainLoss());
        portfolioContext.put("totalUnrealizedGainPercentage", portfolio.totalAbsoluteGainLossPercentage());
        portfolioContext.put("holdingCount", portfolio.totalHoldings());
        portfolioContext.put("dataQualityLimitations", portfolio.dataQualityLimitations());

        if (portfolio.portfolioAnalyticalScore() != null) {
            portfolioContext.put("portfolioScore", portfolio.portfolioAnalyticalScore().portfolioScore());
            portfolioContext.put("scoredHoldingCount", portfolio.portfolioAnalyticalScore().coveredHoldingCount());
            portfolioContext.put("unscoredHoldingCount", portfolio.portfolioAnalyticalScore().excludedHoldingCount());
        }

        if (portfolio.concentrationAnalysis() != null) {
            portfolioContext.put("topAmcConcentration", portfolio.concentrationAnalysis().topAmcWeight());
            portfolioContext.put("topCategoryConcentration", portfolio.concentrationAnalysis().topCategoryWeight());
        }

        Map<String, Double> dimensionScores = new LinkedHashMap<>();
        Map<String, Object> metricValues = new LinkedHashMap<>();
        Map<String, String> dataQualityFlags = new LinkedHashMap<>();

        Double weightedScore = null;
        String scoreStatus = "PARTIAL";
        if (portfolio.portfolioAnalyticalScore() != null) {
            if (portfolio.portfolioAnalyticalScore().portfolioScore() != null) {
                weightedScore = portfolio.portfolioAnalyticalScore().portfolioScore().doubleValue();
            }
            if (portfolio.portfolioAnalyticalScore().scoreState() != null) {
                scoreStatus = portfolio.portfolioAnalyticalScore().scoreState();
            }
        }

        return new GroundedEvidencePackageDto(
            null,
            "Investor Mutual Fund Portfolio",
            "N/A",
            "N/A",
            "MULTI_ASSET_PORTFOLIO",
            "DIRECT",
            "GROWTH",
            null,
            LocalDate.now().toString(),
            OffsetDateTime.now().toString(),
            weightedScore,
            scoreStatus,
            dimensionScores,
            metricValues,
            dataQualityFlags,
            "PORTFOLIO_TRACKING_V1",
            Collections.emptyList(),
            portfolioContext
        );
    }
}
