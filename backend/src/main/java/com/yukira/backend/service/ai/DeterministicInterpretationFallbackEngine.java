package com.yukira.backend.service.ai;

import com.yukira.backend.dto.ai.GroundedAiInterpretationDto;
import com.yukira.backend.dto.ai.GroundedEvidencePackageDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Deterministic Rule-Based AI Interpretation Fallback Engine.
 *
 * Epistemic Contract:
 * - Used whenever LLM provider is unconfigured, disabled, times out, or produces unverified output.
 * - Formulates evidence-grounded interpretations derived purely from backend quantitative metrics, scores, and data-quality taxonomies.
 * - Enforces zero investment advice, zero score modification, zero unevidenced prose.
 */
@Service
public class DeterministicInterpretationFallbackEngine {

    public GroundedAiInterpretationDto generateInterpretation(GroundedEvidencePackageDto evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("Evidence package cannot be null for fallback engine.");
        }

        boolean isPortfolio = "MULTI_ASSET_PORTFOLIO".equalsIgnoreCase(evidence.category());

        if (isPortfolio) {
            return generatePortfolioInterpretation(evidence);
        }

        return generateFundOptionInterpretation(evidence);
    }

    private GroundedAiInterpretationDto generateFundOptionInterpretation(GroundedEvidencePackageDto evidence) {
        String schemeName = evidence.schemeName() != null ? evidence.schemeName() : "Selected Instrument";
        Double score = evidence.overallScore();
        String scoreStatus = evidence.scoreStatus() != null ? evidence.scoreStatus() : "CANDIDATE";

        List<String> whatHappened = new ArrayList<>();
        List<String> interpretation = new ArrayList<>();
        List<String> riskFactors = new ArrayList<>();
        List<String> dataQualityCaveats = new ArrayList<>();
        List<String> invalidationFactors = new ArrayList<>();
        List<String> investigationQuestions = new ArrayList<>();
        Map<String, Object> evidenceRefs = new LinkedHashMap<>();

        // 1. What Happened
        if (score != null) {
            whatHappened.add(String.format("YUKIRA_SCORE_V1 evaluated %s at %s (%s status).",
                schemeName, String.format(Locale.ROOT, "%.2f / 100", score), scoreStatus));
            evidenceRefs.put("overallScore", score);
        } else {
            whatHappened.add(String.format("YUKIRA evaluated %s under %s status (%s).",
                schemeName, scoreStatus, evidence.methodologyStatus() != null ? evidence.methodologyStatus() : "CANDIDATE"));
        }

        if (evidence.dimensionScores() != null && !evidence.dimensionScores().isEmpty()) {
            StringBuilder dimSb = new StringBuilder("Dimension breakdown: ");
            evidence.dimensionScores().forEach((dim, val) -> {
                dimSb.append(String.format(Locale.ROOT, "%s: %.1f | ", dim, val));
                evidenceRefs.put("dimension_" + dim, val);
            });
            whatHappened.add(dimSb.substring(0, Math.max(0, dimSb.length() - 3)));
        }

        if (evidence.metricValues() != null && !evidence.metricValues().isEmpty()) {
            evidence.metricValues().forEach((code, metricObj) -> {
                if (metricObj instanceof Map<?, ?> m) {
                    Object val = m.get("formatted");
                    Object name = m.get("name");
                    if (val != null && name != null) {
                        whatHappened.add(String.format("Observed %s (%s): %s.", name, code, val));
                        evidenceRefs.put(code, val);
                    }
                }
            });
        }

        // 2. What it means
        if (score != null) {
            if (score >= 70.0) {
                interpretation.add(String.format("%s exhibits strong evidence across evaluated return, risk-adjusted, and consistency dimensions relative to its peer group baseline.", schemeName));
            } else if (score >= 50.0) {
                interpretation.add(String.format("%s displays moderate quantitative performance, showing balanced return-risk efficiency but mixed peer-relative rankings.", schemeName));
            } else {
                interpretation.add(String.format("%s reflects below-average risk-adjusted output or higher downside vulnerability over the evaluated historical window.", schemeName));
            }
        } else {
            interpretation.add(String.format("The current evaluation for %s reflects candidate methodology status; non-authoritative scoring fallback applies.", schemeName));
        }
        interpretation.add("Evaluation is grounded in deterministic historical observations; past structural efficiency does not imply future outperformance.");

        // 3. Risks
        if (evidence.dimensionScores() != null && evidence.dimensionScores().containsKey("Downside Risk")) {
            Double downsideScore = evidence.dimensionScores().get("Downside Risk");
            if (downsideScore != null && downsideScore < 50.0) {
                riskFactors.add(String.format("Elevated downside risk score (%.1f/100) indicates historical drawdown depth or semideviation higher than peer median.", downsideScore));
            }
        }
        if (evidence.dimensionScores() != null && evidence.dimensionScores().containsKey("Volatility")) {
            Double volScore = evidence.dimensionScores().get("Volatility");
            if (volScore != null && volScore < 50.0) {
                riskFactors.add(String.format("Volatility dimension score of %.1f/100 reflects wider historical price dispersion during market stress periods.", volScore));
            }
        }
        if (riskFactors.isEmpty()) {
            riskFactors.add("Market risk: Fund NAV remains exposed to broad benchmark market declines and sector concentration shifts.");
            riskFactors.add("Interest rate & macro risk: Monetary policy adjustments and economic cycle reversals may impact holding asset valuations.");
        }

        // 4. Data Quality Caveats
        if (evidence.dataQualityFlags() != null && !evidence.dataQualityFlags().isEmpty()) {
            evidence.dataQualityFlags().forEach((flag, val) -> {
                dataQualityCaveats.add(String.format("Taxonomy [%s]: %s.", flag, val));
            });
        }
        dataQualityCaveats.add("Methodology Notice: YUKIRA_SCORE_V1 is a candidate research methodology operating with provisional peer reference distributions.");

        // 5. Invalidation Factors
        invalidationFactors.add("Significant structural changes in fund manager tenure or investment mandate execution.");
        invalidationFactors.add("Substantial macro regime shift (e.g. sharp rate cycle inflection or liquidity dislocation) not captured in the 3-year historical window.");
        invalidationFactors.add("Retroactive AMFI/AMC NAV revisions altering observation input data.");

        // 6. Investigation Questions
        investigationQuestions.add("Has the fund manager's stock selection process changed relative to the historical evaluation window?");
        investigationQuestions.add("Is the fund's current expense ratio competitive against peer direct growth options?");
        investigationQuestions.add("How does this fund's downside capture ratio align with your personal risk tolerance and investment horizon?");

        String summary = String.format("Deterministic evidence analysis for %s (%s, %s): Overall Score %s.",
            schemeName, evidence.category(), evidence.plan(), score != null ? String.format(Locale.ROOT, "%.2f", score) : "Candidate");

        return new GroundedAiInterpretationDto(
            summary,
            whatHappened,
            interpretation,
            riskFactors,
            dataQualityCaveats,
            invalidationFactors,
            investigationQuestions,
            evidenceRefs,
            evidence.calculationRunId(),
            evidence.schemeOptionId(),
            evidence.methodologyStatus() != null ? evidence.methodologyStatus() : "YUKIRA_SCORE_V1",
            evidence.asOfDate(),
            evidence.knowledgeCutoff(),
            evidence.sourceArtifactDigests() != null ? evidence.sourceArtifactDigests() : Collections.emptyList(),
            "DETERMINISTIC_FALLBACK",
            true,
            "deterministic-rule-engine-v1",
            Instant.now().toString()
        );
    }

    private GroundedAiInterpretationDto generatePortfolioInterpretation(GroundedEvidencePackageDto evidence) {
        Map<String, Object> ctx = evidence.portfolioContext() != null ? evidence.portfolioContext() : Collections.emptyMap();
        Object totalValuation = ctx.get("totalValuation");
        Object totalCostBasis = ctx.get("totalCostBasis");
        Object unrealizedGain = ctx.get("totalUnrealizedGain");
        Object unrealizedGainPct = ctx.get("totalUnrealizedGainPercentage");
        Object holdingCount = ctx.get("holdingCount");
        Double weightedScore = evidence.overallScore();

        List<String> whatHappened = new ArrayList<>();
        List<String> interpretation = new ArrayList<>();
        List<String> riskFactors = new ArrayList<>();
        List<String> dataQualityCaveats = new ArrayList<>();
        List<String> invalidationFactors = new ArrayList<>();
        List<String> investigationQuestions = new ArrayList<>();
        Map<String, Object> evidenceRefs = new LinkedHashMap<>();

        whatHappened.add(String.format("Portfolio comprises %s active holdings with total current valuation of %s (cost basis: %s).",
            holdingCount != null ? holdingCount : 0,
            totalValuation != null ? totalValuation : "N/A",
            totalCostBasis != null ? totalCostBasis : "N/A"));

        if (unrealizedGain != null && unrealizedGainPct != null) {
            whatHappened.add(String.format("Total unrealized gain/loss: %s (%s%%).", unrealizedGain, unrealizedGainPct));
            evidenceRefs.put("unrealizedGain", unrealizedGain);
            evidenceRefs.put("unrealizedGainPct", unrealizedGainPct);
        }

        if (weightedScore != null) {
            whatHappened.add(String.format("Weighted Portfolio YUKIRA Score: %.2f / 100 (%s status).", weightedScore, evidence.scoreStatus()));
            evidenceRefs.put("weightedPortfolioScore", weightedScore);
        }

        // Interpretation
        interpretation.add("Portfolio aggregation reflects deterministic sum of individual asset holdings and weighted score contributions.");
        if (weightedScore != null && weightedScore >= 60.0) {
            interpretation.add("Overall portfolio holding mix demonstrates solid aggregate score quality, with majority capital allocated to scored equity options.");
        } else {
            interpretation.add("Portfolio contains un-scored or lower-scoring holdings; allocators should review individual holding risk exposures.");
        }

        // Risks
        if (ctx.containsKey("topCategoryConcentration")) {
            riskFactors.add(String.format("Category Concentration: Top asset category represents %s of portfolio weight.", ctx.get("topCategoryConcentration")));
        }
        if (ctx.containsKey("topAmcConcentration")) {
            riskFactors.add(String.format("AMC Concentration: Top fund manager/AMC represents %s of portfolio weight.", ctx.get("topAmcConcentration")));
        }
        if (riskFactors.isEmpty()) {
            riskFactors.add("Single-asset class concentration risk if portfolio is heavily weighted towards equity mutual funds.");
        }

        // Caveats
        dataQualityCaveats.add("Portfolio tracking V1 relies on user-provided holding units and cost basis.");
        dataQualityCaveats.add("Scores are non-advisory candidate research indicators; portfolio score does not constitute a composite rating.");

        // Invalidation Factors
        invalidationFactors.add("Unrecorded external transactions (SIPs, redemptions, switches) not reflected in system holding records.");
        invalidationFactors.add("Market volatility drastically altering category allocation weights relative to target allocation.");

        // Questions
        investigationQuestions.add("Are your current category weights aligned with your long-term goal target allocation?");
        investigationQuestions.add("Does any single fund holding represent more than 25% of your total investable capital?");

        return new GroundedAiInterpretationDto(
            String.format("Deterministic evidence analysis for investor portfolio (%s holdings, valuation %s).",
                holdingCount != null ? holdingCount : 0, totalValuation != null ? totalValuation : "N/A"),
            whatHappened,
            interpretation,
            riskFactors,
            dataQualityCaveats,
            invalidationFactors,
            investigationQuestions,
            evidenceRefs,
            null,
            null,
            "PORTFOLIO_TRACKING_V1",
            evidence.asOfDate(),
            evidence.knowledgeCutoff(),
            Collections.emptyList(),
            "DETERMINISTIC_FALLBACK",
            true,
            "deterministic-portfolio-rule-engine-v1",
            Instant.now().toString()
        );
    }
}
