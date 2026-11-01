package com.yukira.backend.service.ai;

import com.yukira.backend.dto.ai.EvidenceClaimDto;
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
 * - Formulates evidence-grounded interpretations derived purely from authoritative quantitative metrics,
 *   profile results, and data-quality taxonomies.
 * - Enforces zero investment advice, zero score modification, zero unevidenced prose.
 * - Generates structured claim-to-evidence associations (EvidenceClaimDto) linking claims directly
 *   to authoritative metric codes and methodology governance statuses.
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
        String schemeName = evidence.schemeName() != null ? evidence.schemeName() : "Selected Mutual Fund Option";
        String horizon = evidence.horizon() != null ? evidence.horizon() : "3Y";
        String asOfDate = evidence.asOfDate() != null ? evidence.asOfDate() : "Latest";
        String benchmarkName = evidence.benchmarkName() != null ? evidence.benchmarkName() : "NIFTY 500 TRI";
        Double score = evidence.overallScore();
        String scoreStatus = evidence.scoreStatus() != null ? evidence.scoreStatus() : "CANDIDATE";
        String benchmarkStatus = evidence.benchmarkMappingStatus() != null ? evidence.benchmarkMappingStatus() : "CANDIDATE";
        String methodologyGovernance = "3Y".equalsIgnoreCase(horizon) ? "APPROVED_BASELINE" : "CANDIDATE_MULTI_CYCLE";

        List<String> whatHappened = new ArrayList<>();
        List<String> interpretation = new ArrayList<>();
        List<String> returnCharacteristics = new ArrayList<>();
        List<String> riskCharacteristics = new ArrayList<>();
        List<String> horizonInterpretation = new ArrayList<>();
        List<String> riskFactors = new ArrayList<>();
        List<String> dataQualityCaveats = new ArrayList<>();
        List<String> invalidationFactors = new ArrayList<>();
        List<String> investigationQuestions = new ArrayList<>();
        List<String> conflictingEvidence = new ArrayList<>();
        List<EvidenceClaimDto> claims = new ArrayList<>();
        Map<String, Object> evidenceRefs = new LinkedHashMap<>();

        // Helper to extract metric values safely
        Map<String, Object> mValues = evidence.metricValues() != null ? evidence.metricValues() : Collections.emptyMap();

        Double ret03Val = extractDouble(mValues, "RET-03");
        String ret03Fmt = extractFormatted(mValues, "RET-03");
        Double ret07Val = extractDouble(mValues, "RET-07");
        String ret07Fmt = extractFormatted(mValues, "RET-07");
        Double rsk01Val = extractDouble(mValues, "RSK-01");
        String rsk01Fmt = extractFormatted(mValues, "RSK-01");
        Double rsk02Val = extractDouble(mValues, "RSK-02");
        String rsk02Fmt = extractFormatted(mValues, "RSK-02");
        Double rsk03Val = extractDouble(mValues, "RSK-03");
        String rsk03Fmt = extractFormatted(mValues, "RSK-03");
        Double mkt01Val = extractDouble(mValues, "MKT-01");
        String mkt01Fmt = extractFormatted(mValues, "MKT-01");
        Double rat01Val = extractDouble(mValues, "RAT-01");
        String rat01Fmt = extractFormatted(mValues, "RAT-01");
        Double rat04Val = extractDouble(mValues, "RAT-04");
        String rat04Fmt = extractFormatted(mValues, "RAT-04");
        Double rel01Val = extractDouble(mValues, "REL-01");
        String rel01Fmt = extractFormatted(mValues, "REL-01");

        // Incorporate statutory holdings disclosures if present
        Map<String, Object> portCtx = evidence.portfolioContext() != null ? evidence.portfolioContext() : Collections.emptyMap();
        Integer holdingCount = portCtx.get("holdingCount") instanceof Number n ? n.intValue() : null;
        Double top5Conc = portCtx.get("top5Concentration") instanceof Number n ? n.doubleValue() : null;
        Double top10Conc = portCtx.get("top10Concentration") instanceof Number n ? n.doubleValue() : null;
        String topPositions = portCtx.get("topHoldingNames") instanceof String s ? s : null;
        String portfolioDate = portCtx.get("portfolioDate") instanceof String s ? s : null;

        if (holdingCount != null && holdingCount > 0) {
            String pDateStr = portfolioDate != null ? (" as of " + portfolioDate) : "";
            String topPosStr = (topPositions != null && !topPositions.isBlank()) ? (" (top positions: " + topPositions + ")") : "";
            String top10Str = top10Conc != null ? String.format(Locale.ROOT, " with Top-10 concentration of %.2f%%", top10Conc * 100.0) : "";
            String hMsg = String.format("Portfolio disclosure%s reveals %d holdings%s%s.", pDateStr, holdingCount, top10Str, topPosStr);
            whatHappened.add(hMsg);
            claims.add(new EvidenceClaimDto(hMsg, "OBSERVATION", List.of(), horizon, "STATUTORY_DISCLOSURE"));

            if (top10Conc != null && top10Conc > 0.45) {
                riskFactors.add(String.format(Locale.ROOT, "Elevated single-portfolio concentration: Top-10 holdings represent %.2f%% of equity assets, increasing vulnerability to company-specific shocks.", top10Conc * 100.0));
            }
        }

        // 1. SECTION A: What Happened (Factual Observations)
        if (score != null) {
            String claimMsg = String.format(Locale.ROOT, "YUKIRA_SCORE_V1 evaluated %s at %.2f / 100 (%s status).",
                schemeName, score, scoreStatus);
            whatHappened.add(claimMsg);
            evidenceRefs.put("overallScore", score);
            claims.add(new EvidenceClaimDto(claimMsg, "OBSERVATION", List.of(), horizon, methodologyGovernance));
        } else {
            String claimMsg = String.format("YUKIRA evaluated %s across the %s horizon (%s reference context).",
                schemeName, horizon, asOfDate);
            whatHappened.add(claimMsg);
            claims.add(new EvidenceClaimDto(claimMsg, "OBSERVATION", List.of(), horizon, methodologyGovernance));
        }

        if (ret03Fmt != null) {
            String claimMsg = String.format("Observed %s Annualized Return (RET-03): %s.", horizon, ret03Fmt);
            whatHappened.add(claimMsg);
            evidenceRefs.put("RET-03", ret03Fmt);
            claims.add(new EvidenceClaimDto(claimMsg, "OBSERVATION", List.of("RET-03"), horizon, methodologyGovernance));
        }

        if (rsk01Fmt != null) {
            String claimMsg = String.format("Observed %s Annualized Volatility (RSK-01): %s.", horizon, rsk01Fmt);
            whatHappened.add(claimMsg);
            evidenceRefs.put("RSK-01", rsk01Fmt);
            claims.add(new EvidenceClaimDto(claimMsg, "OBSERVATION", List.of("RSK-01"), horizon, methodologyGovernance));
        }

        if (rsk03Fmt != null) {
            String claimMsg = String.format("Observed %s Maximum Drawdown (RSK-03): %s.", horizon, rsk03Fmt);
            whatHappened.add(claimMsg);
            evidenceRefs.put("RSK-03", rsk03Fmt);
            claims.add(new EvidenceClaimDto(claimMsg, "OBSERVATION", List.of("RSK-03"), horizon, methodologyGovernance));
        }

        if (ret07Fmt != null) {
            String claimMsg = String.format("Observed %s Active Return vs %s (RET-07): %s.", horizon, benchmarkName, ret07Fmt);
            whatHappened.add(claimMsg);
            evidenceRefs.put("RET-07", ret07Fmt);
            claims.add(new EvidenceClaimDto(claimMsg, "OBSERVATION", List.of("RET-07"), horizon, "CANDIDATE"));
        }

        // 2. SECTION B: Return Characteristics
        if (ret03Val != null) {
            if (ret03Val > 0.15) {
                String claimMsg = String.format(Locale.ROOT, "The fund delivered robust multi-year compound annualized growth (%s) over the %s window.", ret03Fmt, horizon);
                returnCharacteristics.add(claimMsg);
                claims.add(new EvidenceClaimDto(claimMsg, "INTERPRETATION", List.of("RET-03"), horizon, methodologyGovernance));
            } else if (ret03Val > 0.0) {
                String claimMsg = String.format(Locale.ROOT, "The fund generated positive compound growth (%s) over the %s window.", ret03Fmt, horizon);
                returnCharacteristics.add(claimMsg);
                claims.add(new EvidenceClaimDto(claimMsg, "INTERPRETATION", List.of("RET-03"), horizon, methodologyGovernance));
            } else {
                String claimMsg = String.format(Locale.ROOT, "The fund experienced negative compound return (%s) across the %s window.", ret03Fmt, horizon);
                returnCharacteristics.add(claimMsg);
                claims.add(new EvidenceClaimDto(claimMsg, "INTERPRETATION", List.of("RET-03"), horizon, methodologyGovernance));
            }
        }

        if (ret07Val != null) {
            if (ret07Val > 0.0) {
                String claimMsg = String.format("Relative outperformance: The fund outperformed its applicable benchmark (%s) by %s in annualized excess return.",
                    benchmarkName, ret07Fmt);
                returnCharacteristics.add(claimMsg);
                claims.add(new EvidenceClaimDto(claimMsg, "INTERPRETATION", List.of("RET-07"), horizon, "CANDIDATE"));
            } else {
                String claimMsg = String.format("Relative underperformance: The fund lagged its applicable benchmark (%s) by %s over the evaluation window.",
                    benchmarkName, ret07Fmt);
                returnCharacteristics.add(claimMsg);
                claims.add(new EvidenceClaimDto(claimMsg, "INTERPRETATION", List.of("RET-07"), horizon, "CANDIDATE"));
            }
        }
        returnCharacteristics.add("Past return realization is point-in-time empirical evidence; it does not constitute proof of manager skill or a guarantee of future outperformance.");

        // 3. SECTION C: Risk Characteristics
        if (rsk01Val != null) {
            String volAssessment = rsk01Val < 0.15 ? "moderate price variability" : "elevated historical return dispersion";
            String claimMsg = String.format(Locale.ROOT, "Annualized volatility of %s indicates %s during the %s evaluation period.", rsk01Fmt, volAssessment, horizon);
            riskCharacteristics.add(claimMsg);
            claims.add(new EvidenceClaimDto(claimMsg, "INTERPRETATION", List.of("RSK-01"), horizon, methodologyGovernance));
        }

        if (rsk03Val != null) {
            String claimMsg = String.format("Downside tail loss: The peak-to-trough decline reached %s over the historical window, defining the historical pain threshold.", rsk03Fmt);
            riskCharacteristics.add(claimMsg);
            claims.add(new EvidenceClaimDto(claimMsg, "RISK", List.of("RSK-03"), horizon, methodologyGovernance));
        }

        if (mkt01Val != null) {
            String betaType = mkt01Val > 1.05 ? "aggressive systematic sensitivity (Beta > 1.0)" : (mkt01Val < 0.95 ? "defensive systematic sensitivity (Beta < 1.0)" : "benchmark-matching market co-movement");
            String claimMsg = String.format(Locale.ROOT, "Systematic market sensitivity (Beta: %s) reflects %s relative to %s.", mkt01Fmt, betaType, benchmarkName);
            riskCharacteristics.add(claimMsg);
            claims.add(new EvidenceClaimDto(claimMsg, "INTERPRETATION", List.of("MKT-01"), horizon, "CANDIDATE"));
        }

        if (rat04Val != null) {
            String irQual = rat04Val > 0.5 ? "positive excess return efficiency per unit of active risk" : "limited active excess return relative to tracking volatility";
            String claimMsg = String.format(Locale.ROOT, "Information Ratio of %s demonstrates %s vs %s.", rat04Fmt, irQual, benchmarkName);
            riskCharacteristics.add(claimMsg);
            claims.add(new EvidenceClaimDto(claimMsg, "INTERPRETATION", List.of("RAT-04", "REL-01"), horizon, "CANDIDATE"));
        }

        // Add to general riskFactors
        if (rsk03Val != null && Math.abs(rsk03Val) > 0.15) {
            riskFactors.add(String.format("Severe historical drawdown: Maximum drawdown reached %s, demonstrating that capital was subject to significant recovery duration.", rsk03Fmt));
        }
        if (mkt01Val != null && mkt01Val > 1.10) {
            riskFactors.add(String.format(Locale.ROOT, "Elevated systematic sensitivity: Beta of %.2f amplifies portfolio downside exposure during broad equity market corrections.", mkt01Val));
        }
        riskFactors.add("Historical volatility and drawdown measures do not capture tail risks from unprecedented liquidity crunches or regulatory mandate shifts.");

        // 4. SECTION D: Horizon-Dependent Interpretation
        if ("3Y".equalsIgnoreCase(horizon)) {
            String claimMsg = "The 3-Year analytical horizon represents YUKIRA's validated baseline methodology under Phase 2H governance, fully calibrated against empirical market regimes.";
            horizonInterpretation.add(claimMsg);
            claims.add(new EvidenceClaimDto(claimMsg, "LIMITATION", List.of(), "3Y", "APPROVED_BASELINE"));
        } else {
            String claimMsg = String.format("Governance Notice: The %s analytical window operates under Candidate Methodology status. Multi-cycle risk-adjusted formulations are not yet approved for live financial advice.", horizon);
            horizonInterpretation.add(claimMsg);
            claims.add(new EvidenceClaimDto(claimMsg, "LIMITATION", List.of(), horizon, "CANDIDATE_MULTI_CYCLE"));
        }

        if (evidence.observationCount() != null && evidence.requiredObservationThreshold() != null) {
            int obs = evidence.observationCount();
            int req = evidence.requiredObservationThreshold();
            if (obs >= req) {
                String claimMsg = String.format("Sample Depth: Observation history meets the institutional threshold for %s analysis (%d / %d required trading days).",
                    horizon, obs, req);
                horizonInterpretation.add(claimMsg);
                claims.add(new EvidenceClaimDto(claimMsg, "OBSERVATION", List.of(), horizon, methodologyGovernance));
            } else {
                String claimMsg = String.format("Sample Limitation: Fund contains %d trading days, below the %d threshold required for authoritative %s evaluation.",
                    obs, req, horizon);
                horizonInterpretation.add(claimMsg);
                claims.add(new EvidenceClaimDto(claimMsg, "LIMITATION", List.of(), horizon, methodologyGovernance));
            }
        }

        // 5. SECTION E: Data & Methodology Limitations
        if (evidence.dataQualityFlags() != null && !evidence.dataQualityFlags().isEmpty()) {
            evidence.dataQualityFlags().forEach((flag, val) -> {
                dataQualityCaveats.add(String.format("Taxonomy [%s]: %s.", flag, val));
            });
        }
        if ("CANDIDATE".equalsIgnoreCase(benchmarkStatus)) {
            String claimMsg = String.format("Benchmark Mapping Disclosure: The benchmark assignment (%s) is a candidate mapping pending regulatory prospectus verification.", benchmarkName);
            dataQualityCaveats.add(claimMsg);
            claims.add(new EvidenceClaimDto(claimMsg, "LIMITATION", List.of(), horizon, "CANDIDATE"));
        }
        dataQualityCaveats.add("Deterministic Calculation Rule: All financial numbers are computed in Java/Python kernels. The AI interpretation layer computes zero financial math.");

        invalidationFactors.add("Material changes in fund manager tenure, key personnel, or investment mandate execution.");
        invalidationFactors.add("Retroactive AMFI or AMC NAV corrections modifying historical observation records.");
        invalidationFactors.add("Structural macroeconomic regime inflection (e.g., rapid interest rate cycle pivot) not represented in the historical window.");

        // Conflicting Empirical Evidence
        if (ret03Val != null && ret03Val > 0.15 && rsk03Val != null && Math.abs(rsk03Val) > 0.15) {
            conflictingEvidence.add(String.format(Locale.ROOT,
                "Return-Tail Conflict: High compound return (%s) was delivered alongside elevated historical drawdown (%s), demonstrating that past upside required enduring significant capital distress.",
                ret03Fmt, rsk03Fmt));
        }

        if (mkt01Val != null && mkt01Val > 1.10 && rat04Val != null && rat04Val < 0.20) {
            conflictingEvidence.add(String.format(Locale.ROOT,
                "Sensitivity-Alpha Conflict: Elevated systematic market beta (%.2f) coexists with low Information Ratio (%s), suggesting outperformance may reflect benchmark market beta amplification rather than independent security selection skill.",
                mkt01Val, rat04Fmt != null ? rat04Fmt : "N/A"));
        }

        if (rel01Val != null && rel01Val > 0.05 && (rat04Val == null || rat04Val <= 0.0)) {
            conflictingEvidence.add(String.format(Locale.ROOT,
                "Active Risk Conflict: Substantial tracking error (%s) was incurred without generating commensurate positive information ratio (%s) vs %s.",
                rel01Fmt != null ? rel01Fmt : "N/A", rat04Fmt != null ? rat04Fmt : "N/A", benchmarkName));
        }

        if ("CANDIDATE".equalsIgnoreCase(benchmarkStatus)) {
            conflictingEvidence.add(String.format(
                "Benchmark Attribution Conflict: Benchmark mapping (%s) is a candidate convention under Phase 2H. Evaluation against an alternate index would alter active return and relative risk metrics.",
                benchmarkName));
        }

        if (top10Conc != null && top10Conc > 0.45 && ("Flexi Cap".equalsIgnoreCase(evidence.category()) || "Large & Mid Cap".equalsIgnoreCase(evidence.category()))) {
            conflictingEvidence.add(String.format(Locale.ROOT,
                "Concentration vs Mandate Conflict: Portfolio displays concentrated asset allocation (Top 10: %.2f%%) despite operating under a diversified %s mandate.",
                top10Conc * 100.0, evidence.category()));
        }

        // 6. SECTION F: Allocator Investigation Questions (Context-Specific)
        if (top10Conc != null && top10Conc > 0.40) {
            investigationQuestions.add(String.format(Locale.ROOT,
                "Does the fund's Top-10 concentration (%.2f%%) align with allocator liquidity and single-issuer concentration limits?",
                top10Conc * 100.0));
        }
        if (ret07Val != null && ret07Val > 0.0 && mkt01Val != null && mkt01Val > 1.05) {
            investigationQuestions.add(String.format(Locale.ROOT,
                "Is the fund's observed active return (%s vs %s) primarily a reward for taking higher market beta (%.2f), or does it reflect genuine security selection alpha?",
                ret07Fmt, benchmarkName, mkt01Val));
        } else if (ret07Val != null && ret07Val > 0.0) {
            investigationQuestions.add(String.format("What specific sector overweights or stock selections drove the fund's %s active return vs %s?",
                ret07Fmt, benchmarkName));
        }

        if (rsk03Val != null) {
            investigationQuestions.add(String.format("During the period of maximum drawdown (%s), how long did the fund take to recover to previous peak NAV compared to category peers?",
                rsk03Fmt));
        }

        if (!"3Y".equalsIgnoreCase(horizon)) {
            investigationQuestions.add(String.format("How consistently did the fund's risk-adjusted profile hold across the 3Y baseline vs the candidate %s multi-cycle window?", horizon));
        } else {
            investigationQuestions.add("Has the fund's investment style drifted in market capitalisation (Large vs Mid vs Small cap) during the evaluated 3-year period?");
        }

        if ("CANDIDATE".equalsIgnoreCase(benchmarkStatus)) {
            investigationQuestions.add(String.format("Does the fund's apparent outperformance hold if evaluated against an alternate category benchmark rather than %s?", benchmarkName));
        }

        // Summary
        String summary = String.format("Grounded analysis of %s (%s, %s) over %s horizon as of %s: CAGR %s, Volatility %s, Drawdown %s.",
            schemeName, evidence.category(), evidence.plan(), horizon, asOfDate,
            ret03Fmt != null ? ret03Fmt : "N/A",
            rsk01Fmt != null ? rsk01Fmt : "N/A",
            rsk03Fmt != null ? rsk03Fmt : "N/A");

        return new GroundedAiInterpretationDto(
            summary,
            whatHappened,
            interpretation.isEmpty() ? List.of("Evaluation is grounded in deterministic historical observations; past structural efficiency does not imply future outperformance.") : interpretation,
            riskFactors,
            dataQualityCaveats,
            invalidationFactors,
            investigationQuestions,
            evidenceRefs,
            evidence.calculationRunId(),
            evidence.schemeOptionId(),
            evidence.methodologyStatus() != null ? evidence.methodologyStatus() : "YUKIRA_PROFILE_V1",
            asOfDate,
            evidence.knowledgeCutoff(),
            evidence.sourceArtifactDigests() != null ? evidence.sourceArtifactDigests() : Collections.emptyList(),
            "DETERMINISTIC_FALLBACK",
            true,
            "deterministic-profile-rule-engine-v1",
            Instant.now().toString(),
            horizon,
            claims,
            returnCharacteristics,
            riskCharacteristics,
            horizonInterpretation,
            conflictingEvidence
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
        List<String> returnCharacteristics = new ArrayList<>();
        List<String> riskCharacteristics = new ArrayList<>();
        List<String> horizonInterpretation = new ArrayList<>();
        List<String> riskFactors = new ArrayList<>();
        List<String> dataQualityCaveats = new ArrayList<>();
        List<String> invalidationFactors = new ArrayList<>();
        List<String> investigationQuestions = new ArrayList<>();
        List<EvidenceClaimDto> claims = new ArrayList<>();
        Map<String, Object> evidenceRefs = new LinkedHashMap<>();

        whatHappened.add(String.format("Portfolio comprises %s active holdings with total current valuation of %s (cost basis: %s).",
            holdingCount != null ? holdingCount : 0,
            totalValuation != null ? totalValuation : "N/A",
            totalCostBasis != null ? totalCostBasis : "N/A"));

        if (unrealizedGain != null && unrealizedGainPct != null) {
            String claimMsg = String.format("Total unrealized gain/loss: %s (%s%%).", unrealizedGain, unrealizedGainPct);
            whatHappened.add(claimMsg);
            evidenceRefs.put("unrealizedGain", unrealizedGain);
            evidenceRefs.put("unrealizedGainPct", unrealizedGainPct);
            claims.add(new EvidenceClaimDto(claimMsg, "OBSERVATION", List.of(), "CURRENT", "PORTFOLIO_TRACKING_V1"));
        }

        if (weightedScore != null) {
            String claimMsg = String.format(Locale.ROOT, "Weighted Portfolio YUKIRA Score: %.2f / 100 (%s status).", weightedScore, evidence.scoreStatus());
            whatHappened.add(claimMsg);
            evidenceRefs.put("weightedPortfolioScore", weightedScore);
            claims.add(new EvidenceClaimDto(claimMsg, "OBSERVATION", List.of(), "CURRENT", "CANDIDATE"));
        }

        interpretation.add("Portfolio aggregation reflects deterministic sum of individual asset holdings and weighted score contributions.");
        if (weightedScore != null && weightedScore >= 60.0) {
            interpretation.add("Overall portfolio holding mix demonstrates solid aggregate score quality, with majority capital allocated to scored equity options.");
        } else {
            interpretation.add("Portfolio contains un-scored or lower-scoring holdings; allocators should review individual holding risk exposures.");
        }

        if (ctx.containsKey("topCategoryConcentration")) {
            riskFactors.add(String.format("Category Concentration: Top asset category represents %s of portfolio weight.", ctx.get("topCategoryConcentration")));
        }
        if (ctx.containsKey("topAmcConcentration")) {
            riskFactors.add(String.format("AMC Concentration: Top fund manager/AMC represents %s of portfolio weight.", ctx.get("topAmcConcentration")));
        }
        if (riskFactors.isEmpty()) {
            riskFactors.add("Single-asset class concentration risk if portfolio is heavily weighted towards equity mutual funds.");
        }

        dataQualityCaveats.add("Portfolio tracking V1 relies on user-provided holding units and cost basis.");
        dataQualityCaveats.add("Scores are non-advisory candidate research indicators; portfolio score does not constitute a composite rating.");

        invalidationFactors.add("Unrecorded external transactions (SIPs, redemptions, switches) not reflected in system holding records.");
        invalidationFactors.add("Market volatility drastically altering category allocation weights relative to target allocation.");

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
            Instant.now().toString(),
            "3Y",
            claims,
            returnCharacteristics,
            riskCharacteristics,
            horizonInterpretation
        );
    }

    private Double extractDouble(Map<String, Object> map, String code) {
        Object item = map.get(code);
        if (item instanceof Map<?, ?> m) {
            Object val = m.get("value");
            if (val instanceof Number n) {
                return n.doubleValue();
            }
        }
        return null;
    }

    private String extractFormatted(Map<String, Object> map, String code) {
        Object item = map.get(code);
        if (item instanceof Map<?, ?> m) {
            Object fmt = m.get("formatted");
            if (fmt != null) return fmt.toString();
            Object val = m.get("value");
            if (val != null) return val.toString();
        }
        return null;
    }
}
