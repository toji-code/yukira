package com.yukira.backend.service.ai;

import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.dto.ai.GroundedEvidencePackageDto;
import com.yukira.backend.dto.analysis.DataQualityAuditResponse;
import com.yukira.backend.dto.portfolio.PortfolioSummaryDto;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
import com.yukira.backend.service.AnalysisService;
import com.yukira.backend.service.portfolio.InvestorPortfolioService;
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
 * - Extracts strictly verified quantitative data from backend DTOs, CalculationRun, and Score models.
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

    public EvidenceGrounderService(
        CalculationRunRepository calculationRunRepository,
        SchemeOptionRepository schemeOptionRepository,
        AnalysisService analysisService,
        AnalyticalScoringService scoringService,
        @Autowired(required = false) InvestorPortfolioService portfolioService
    ) {
        this.calculationRunRepository = calculationRunRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.analysisService = analysisService;
        this.scoringService = scoringService;
        this.portfolioService = portfolioService;
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

        return buildEvidencePackageInternal(option, run.getId(),
            run.getAsOfDate() != null ? run.getAsOfDate().toString() : LocalDate.now().toString(),
            run.getKnowledgeCutoffTime() != null ? run.getKnowledgeCutoffTime().toString() : OffsetDateTime.now().toString(),
            version,
            artifacts
        );
    }

    /**
     * Builds grounded evidence package for a scheme option ID.
     */
    public GroundedEvidencePackageDto buildEvidencePackageForSchemeOption(Long schemeOptionId) {
        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
            .orElseThrow(() -> new IllegalArgumentException("SchemeOption #" + schemeOptionId + " not found."));

        return buildEvidencePackageInternal(option, null, LocalDate.now().toString(), OffsetDateTime.now().toString(), "YUKIRA_SCORE_V1", Collections.emptyList());
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
