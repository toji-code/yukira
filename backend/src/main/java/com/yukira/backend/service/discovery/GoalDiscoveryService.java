package com.yukira.backend.service.discovery;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.discovery.*;
import com.yukira.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Backend service orchestrating deterministic goal-based mutual fund discovery.
 *
 * Epistemic &amp; Product Principles (V1 Hardened):
 * 1. Financial matching and eligibility logic is strictly owned by the backend.
 * 2. NO financial calculation or eligibility matching is performed in the presentation tier.
 * 3. NO return predictions, predicted CAGRs, future NAV projections, or success probabilities.
 * 4. NO investment advice, recommendation language (BUY/SELL/HOLD/BEST/#1/Guaranteed).
 * 5. Eligibility and YUKIRA Analytical Quality Score are kept strictly distinct.
 * 6. UNKNOWN is explicitly distinguished from NOT_ELIGIBLE and INSUFFICIENT_DATA.
 * 7. Missing data is NEVER treated as zero or silent match.
 *
 * Core V1 Epistemic Constraints:
 * - GOAL CATEGORY (Retirement, Wealth Creation, etc.) is investor CONTEXT ONLY.
 *   It does NOT create asset-class suitability rules.
 * - HORIZON evaluation: ONLY authoritative fund-specific lock-in data can produce
 *   MATCH/NO_MATCH. If no fund-specific lock-in exists, horizon = UNKNOWN.
 *   No equity-volatility or category-based horizon suitability inferences permitted.
 * - RISK TOLERANCE: Always UNKNOWN. No Riskometer data in master DB.
 * - FUND CATEGORY: MATCH/NO_MATCH only when investor explicitly selects a category.
 *   If investor selects ANY, category = NOT_APPLICABLE.
 * - NAV AVAILABILITY: Separately reported as AVAILABLE/INSUFFICIENT.
 *   navCount &gt; 0 does NOT imply analytical observation sufficiency.
 * - ANALYTICAL SCORE: Read-only from existing score engine. Never recalculated.
 * - HISTORICAL DATA: Never labeled "CURRENT". Expose actual as-of date.
 * - ELIGIBLE: Requires every applicable criterion to be explicitly MATCH/SUPPORTED.
 *   UNKNOWN is not MATCH. PARTIALLY_EVALUATED means something is UNKNOWN but nothing failed.
 * - NOT_ELIGIBLE: Only when authoritative evidence explicitly fails a criterion.
 */
@Service
@Transactional(readOnly = true)
public class GoalDiscoveryService {

    private final SchemeOptionRepository schemeOptionRepository;
    private final SchemeInvestmentTermsRepository investmentTermsRepository;
    private final AnalyticalScoreRepository analyticalScoreRepository;
    private final NavObservationRepository navObservationRepository;

    public GoalDiscoveryService(
        SchemeOptionRepository schemeOptionRepository,
        SchemeInvestmentTermsRepository investmentTermsRepository,
        AnalyticalScoreRepository analyticalScoreRepository,
        NavObservationRepository navObservationRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.investmentTermsRepository = investmentTermsRepository;
        this.analyticalScoreRepository = analyticalScoreRepository;
        this.navObservationRepository = navObservationRepository;
    }

    /**
     * Evaluates all scheme options against explicit investor requirements.
     * Returns transparent, structured discovery results with explicit epistemic states.
     */
    public GoalDiscoveryResponse evaluateGoalDiscovery(GoalDiscoveryRequest request) {
        List<SchemeOption> options = schemeOptionRepository.findAll();
        List<GoalDiscoveryResultDto> results = new ArrayList<>();

        for (SchemeOption option : options) {
            results.add(evaluateOption(option, request));
        }

        // Deterministic display ordering: NOT evaluative ranking.
        // 1. Result state: ELIGIBLE → PARTIALLY_EVALUATED → INSUFFICIENT_DATA → NOT_ELIGIBLE
        // 2. Score availability (scored funds first within same state)
        // 3. Fund name (A–Z)
        // This ordering is for display only. It does NOT imply relative suitability.
        results.sort(Comparator
            .comparing((GoalDiscoveryResultDto r) -> stateOrder(r.resultState()))
            .thenComparing((GoalDiscoveryResultDto r) -> r.analyticalScore().available() ? 0 : 1)
            .thenComparing(GoalDiscoveryResultDto::fundName)
        );

        return new GoalDiscoveryResponse(
            request,
            options.size(),
            results
        );
    }

    public GoalDiscoveryResultDto evaluateOption(SchemeOption option, GoalDiscoveryRequest req) {
        SchemePlan plan = option.getPlan();
        Scheme scheme = plan != null ? plan.getScheme() : null;
        Amc amc = scheme != null ? scheme.getAmc() : null;

        Long schemeOptionId = option.getId();
        Long schemeId = scheme != null ? scheme.getId() : null;

        String fundName = buildFundName(scheme, plan, option);
        String amcName = amc != null ? amc.getLegalName() : "Unknown AMC";
        String category = scheme != null && scheme.getCategory() != null ? scheme.getCategory() : null;
        String subcategory = scheme != null && scheme.getSubcategory() != null ? scheme.getSubcategory() : "Unspecified Subcategory";
        String planType = plan != null && plan.getPlanType() != null ? plan.getPlanType() : "DIRECT";
        String optionType = option.getOptionType() != null ? option.getOptionType() : "GROWTH";
        String amfiCode = option.getAmfiCode() != null ? option.getAmfiCode() : "N/A";
        String isin = option.getIsin() != null ? option.getIsin() : "N/A";

        // Query authoritative investment terms (for SIP/Lumpsum and lock-in only)
        Optional<SchemeInvestmentTerms> termsOpt = schemeId != null
            ? investmentTermsRepository.findTopBySchemeIdOrderByAsOfDateDesc(schemeId)
            : Optional.empty();

        // ─────────────────────────────────────────────────────────────────────
        // 1. CATEGORY CRITERION
        // Deterministic filter only when investor explicitly selects a category.
        // "ANY" → NOT_APPLICABLE. No hidden asset-class suitability inference.
        // Goal category does NOT create category suitability rules.
        // ─────────────────────────────────────────────────────────────────────
        CriterionEvaluationDto categoryEval = evaluateCategory(category, req.fundCategory());

        // ─────────────────────────────────────────────────────────────────────
        // 2. HORIZON CRITERION
        // ONLY authoritative fund-specific lock-in data can produce MATCH/NO_MATCH.
        // No equity-volatility or category-based horizon suitability inferences.
        // Missing fund-specific lock-in → UNKNOWN.
        // ─────────────────────────────────────────────────────────────────────
        CriterionEvaluationDto horizonEval = evaluateHorizon(termsOpt.orElse(null), req.horizonYears());

        // ─────────────────────────────────────────────────────────────────────
        // 3. RISK TOLERANCE CRITERION
        // Always UNKNOWN. No authoritative SEBI Riskometer data in master DB.
        // ─────────────────────────────────────────────────────────────────────
        CriterionEvaluationDto riskEval = evaluateRisk(req.riskTolerance());

        // ─────────────────────────────────────────────────────────────────────
        // 4. INVESTMENT MODE CRITERION
        // Uses authoritative enrichment terms only.
        // ─────────────────────────────────────────────────────────────────────
        CriterionEvaluationDto modeEval = evaluateInvestmentMode(termsOpt.orElse(null), req.investmentMode());

        // ─────────────────────────────────────────────────────────────────────
        // 5. NAV DATA AVAILABILITY
        // Separately reported. navCount > 0 = NAV AVAILABLE.
        // Does NOT imply analytical sufficiency.
        // ─────────────────────────────────────────────────────────────────────
        long navCount = navObservationRepository.countBySchemeOptionId(schemeOptionId);
        CriterionEvaluationDto navDataEval = evaluateNavAvailability(navCount);

        Map<String, CriterionEvaluationDto> criteriaMap = new LinkedHashMap<>();
        criteriaMap.put("category", categoryEval);
        criteriaMap.put("horizon", horizonEval);
        criteriaMap.put("risk", riskEval);
        criteriaMap.put("investmentMode", modeEval);
        criteriaMap.put("navData", navDataEval);

        // ─────────────────────────────────────────────────────────────────────
        // RESULT STATE DETERMINATION
        //
        // NOT_ELIGIBLE: Explicit authoritative failure (NO_MATCH on a mandatory criterion).
        // INSUFFICIENT_DATA: NAV unavailable (cannot evaluate the fund at all).
        // PARTIALLY_EVALUATED: No failure, but one or more criteria are UNKNOWN.
        // ELIGIBLE: Every applicable, evaluable criterion is explicitly MATCH/SUPPORTED.
        //           NOT_APPLICABLE and UNKNOWN prevent ELIGIBLE.
        //
        // IMPORTANT: UNKNOWN ≠ MATCH. ELIGIBLE requires all applicable criteria to pass.
        // ─────────────────────────────────────────────────────────────────────
        String resultState = determineResultState(categoryEval, horizonEval, riskEval, modeEval, navDataEval);

        // Existing YUKIRA Analytical Quality Score Lookup (READ-ONLY)
        ScoreSummaryDto scoreSummary = resolveAnalyticalScore(schemeOptionId);

        // Evidence state with honest as-of dates (no "CURRENT" label for historical data)
        EvidenceStateDto evidenceState = resolveEvidenceState(schemeOptionId, navCount, termsOpt.orElse(null));

        // Deterministic investigation questions generated from actual evidence states
        List<String> questions = generateInvestigationQuestions(
            categoryEval, horizonEval, riskEval, modeEval, navDataEval, scoreSummary, evidenceState
        );

        return new GoalDiscoveryResultDto(
            schemeOptionId,
            schemeId,
            fundName,
            amcName,
            category != null ? category : "Uncategorized",
            subcategory,
            planType,
            optionType,
            amfiCode,
            isin,
            resultState,
            criteriaMap,
            scoreSummary,
            evidenceState,
            questions
        );
    }

    /**
     * Evaluates fund category against explicit investor category filter.
     *
     * If investor selects ANY: NOT_APPLICABLE — no hidden suitability inference.
     * If investor selects a category: MATCH / NO_MATCH / UNKNOWN (if scheme category missing).
     *
     * PROHIBITED: Goal-category-derived asset-class rules (e.g., SHORT_TERM → Debt MATCH).
     */
    private CriterionEvaluationDto evaluateCategory(String schemeCategory, String reqFundCategory) {
        boolean hasExplicitFilter = reqFundCategory != null
            && !reqFundCategory.isBlank()
            && !"ANY".equalsIgnoreCase(reqFundCategory.trim());

        if (!hasExplicitFilter) {
            // Investor selected ANY — category is not a criterion for this evaluation.
            return new CriterionEvaluationDto(
                "category",
                "NOT_APPLICABLE",
                "No fund category filter applied (investor selected 'Any Category'). Category is not a matching criterion for this evaluation."
            );
        }

        // Explicit category filter: deterministic match/no-match.
        String targetCategory = reqFundCategory.trim();
        if (schemeCategory == null || schemeCategory.isBlank()) {
            return new CriterionEvaluationDto(
                "category",
                "UNKNOWN",
                "Scheme category is missing in master database records. Cannot evaluate against requested category filter '" + targetCategory + "'."
            );
        }
        if (schemeCategory.toLowerCase().contains(targetCategory.toLowerCase())) {
            return new CriterionEvaluationDto(
                "category",
                "MATCH",
                String.format("Scheme category '%s' matches the requested category filter '%s'.", schemeCategory, targetCategory)
            );
        } else {
            return new CriterionEvaluationDto(
                "category",
                "NO_MATCH",
                String.format("Scheme category '%s' does not match the requested category filter '%s'.", schemeCategory, targetCategory)
            );
        }
    }

    /**
     * Evaluates investment horizon against authoritative fund-specific lock-in data only.
     *
     * PERMITTED: If authoritative lock-in data exists → compare investor horizon to mandatory lock-in.
     * PROHIBITED: Inferring horizon suitability from fund category (Equity = long-term, Debt = short-term, etc.).
     * PROHIBITED: Using volatility, category name, or any non-lock-in data as a horizon proxy.
     *
     * If no authoritative fund-specific lock-in data exists → UNKNOWN.
     * This is the correct epistemic state for "no authorized methodology/data available".
     */
    private CriterionEvaluationDto evaluateHorizon(SchemeInvestmentTerms terms, int horizonYears) {
        if (terms != null && terms.getLockInPeriodDays() != null) {
            int lockInDays = terms.getLockInPeriodDays();
            double lockInYears = lockInDays / 365.0;
            if (horizonYears * 365 < lockInDays) {
                return new CriterionEvaluationDto(
                    "horizon",
                    "NO_MATCH",
                    String.format(
                        "Requested horizon of %d year(s) is shorter than the mandatory scheme lock-in period of %d days (%.1f years). " +
                        "This evaluates lock-in compatibility only, not overall investment-horizon suitability.",
                        horizonYears, lockInDays, lockInYears
                    )
                );
            } else {
                return new CriterionEvaluationDto(
                    "horizon",
                    "MATCH",
                    String.format(
                        "Requested horizon of %d year(s) satisfies the mandatory scheme lock-in period of %d days. " +
                        "This evaluates lock-in compatibility only, not overall investment-horizon suitability.",
                        horizonYears, lockInDays
                    )
                );
            }
        }

        // No authoritative fund-specific lock-in data found.
        // No authorized methodology exists to evaluate horizon suitability from category or other proxies.
        return new CriterionEvaluationDto(
            "horizon",
            "UNKNOWN",
            String.format(
                "No authoritative fund-specific lock-in or minimum holding period data found for this scheme. " +
                "Investment horizon compatibility for %d year(s) cannot be determined from available master database records.",
                horizonYears
            )
        );
    }

    /**
     * Risk tolerance evaluation.
     *
     * Always UNKNOWN. YUKIRA does not possess authoritative SEBI Riskometer data
     * in the master database. Selected risk tolerance is preserved for investor reference.
     *
     * PROHIBITED: Inferring risk compatibility from category, volatility, beta, or any other metric.
     */
    private CriterionEvaluationDto evaluateRisk(String selectedRiskTolerance) {
        return new CriterionEvaluationDto(
            "risk",
            "UNKNOWN",
            String.format(
                "Selected risk tolerance: %s. Authoritative SEBI Riskometer classification is UNKNOWN in master database records. " +
                "Risk compatibility with the selected tolerance cannot be determined from available data.",
                selectedRiskTolerance != null ? selectedRiskTolerance : "NOT_SPECIFIED"
            )
        );
    }

    /**
     * Investment mode evaluation using authoritative enrichment terms only.
     * SIP supported ↔ authoritative minimum SIP amount present in enrichment records.
     * Missing enrichment records → UNKNOWN.
     */
    private CriterionEvaluationDto evaluateInvestmentMode(SchemeInvestmentTerms terms, String mode) {
        if (terms == null) {
            return new CriterionEvaluationDto(
                "investmentMode",
                "UNKNOWN",
                "Scheme investment terms (SIP/Lumpsum minimums) are UNKNOWN in master records for this scheme."
            );
        }

        BigDecimal minSip = terms.getMinSipAmount();
        BigDecimal minLump = terms.getMinLumpsumAmount();

        if ("SIP".equalsIgnoreCase(mode)) {
            if (minSip != null) {
                return new CriterionEvaluationDto(
                    "investmentMode",
                    "SUPPORTED",
                    String.format("SIP investment supported per authoritative enrichment records (minimum ₹%s).", minSip.toPlainString())
                );
            } else {
                return new CriterionEvaluationDto(
                    "investmentMode",
                    "UNKNOWN",
                    "SIP minimum investment amount is UNKNOWN in master enrichment records."
                );
            }
        } else if ("LUMPSUM".equalsIgnoreCase(mode)) {
            if (minLump != null) {
                return new CriterionEvaluationDto(
                    "investmentMode",
                    "SUPPORTED",
                    String.format("Lumpsum investment supported per authoritative enrichment records (minimum ₹%s).", minLump.toPlainString())
                );
            } else {
                return new CriterionEvaluationDto(
                    "investmentMode",
                    "UNKNOWN",
                    "Lumpsum minimum investment amount is UNKNOWN in master enrichment records."
                );
            }
        } else {
            // EITHER / Flexible — supported if at least one mode has authoritative terms
            if (minSip != null || minLump != null) {
                return new CriterionEvaluationDto(
                    "investmentMode",
                    "SUPPORTED",
                    String.format(
                        "Flexible investment mode supported per authoritative enrichment records " +
                        "(SIP min: %s, Lumpsum min: %s).",
                        minSip != null ? "₹" + minSip.toPlainString() : "UNSPECIFIED",
                        minLump != null ? "₹" + minLump.toPlainString() : "UNSPECIFIED"
                    )
                );
            } else {
                return new CriterionEvaluationDto(
                    "investmentMode",
                    "UNKNOWN",
                    "Investment terms (SIP & Lumpsum) are UNKNOWN in master enrichment records for this scheme."
                );
            }
        }
    }

    /**
     * NAV data availability evaluation.
     *
     * AVAILABLE: One or more verified NAV observations in the database ledger.
     * INSUFFICIENT: No NAV observations found.
     *
     * IMPORTANT: AVAILABLE does NOT imply analytical sufficiency.
     * A fund with limited NAV history may not have enough observations for
     * multi-year analytical metrics. Analytical score availability is evaluated separately.
     */
    private CriterionEvaluationDto evaluateNavAvailability(long navCount) {
        if (navCount > 0) {
            return new CriterionEvaluationDto(
                "navData",
                "AVAILABLE",
                String.format(
                    "Verified historical NAV observations available (%d observation(s) in database ledger). " +
                    "NAV availability does not imply analytical observation sufficiency for multi-year metrics.",
                    navCount
                )
            );
        } else {
            return new CriterionEvaluationDto(
                "navData",
                "INSUFFICIENT",
                "No verified NAV observations found in database ledgers. Analytical evaluation cannot proceed."
            );
        }
    }

    /**
     * Determines overall result state based on evaluated criteria.
     *
     * INSUFFICIENT_DATA: No NAV data — evaluation cannot meaningfully proceed.
     * NOT_ELIGIBLE: At least one explicitly applicable criterion has authoritative evidence of failure (NO_MATCH/UNSUPPORTED).
     * PARTIALLY_EVALUATED: No criterion has failed, but one or more are UNKNOWN or NOT_APPLICABLE.
     * ELIGIBLE: Every applicable, evaluable criterion is explicitly MATCH or SUPPORTED.
     *
     * CRITICAL: NOT_APPLICABLE and UNKNOWN do NOT count as MATCH.
     * ELIGIBLE requires all applicable criteria to explicitly pass.
     */
    private String determineResultState(
        CriterionEvaluationDto categoryEval,
        CriterionEvaluationDto horizonEval,
        CriterionEvaluationDto riskEval,
        CriterionEvaluationDto modeEval,
        CriterionEvaluationDto navDataEval
    ) {
        // INSUFFICIENT_DATA: No NAV observations at all
        if ("INSUFFICIENT".equals(navDataEval.state())) {
            return "INSUFFICIENT_DATA";
        }

        // NOT_ELIGIBLE: Explicit authoritative failure on any applicable criterion
        boolean hasExplicitFailure = "NO_MATCH".equals(categoryEval.state())
            || "NO_MATCH".equals(horizonEval.state())
            || "UNSUPPORTED".equals(modeEval.state());
        if (hasExplicitFailure) {
            return "NOT_ELIGIBLE";
        }

        // ELIGIBLE: Every applicable, evaluable criterion explicitly passes.
        // NOT_APPLICABLE (e.g., category when ANY selected) is excluded from pass condition.
        // UNKNOWN means cannot evaluate → prevents ELIGIBLE.
        boolean allApplicablePass =
            (isPassState(categoryEval.state()) || "NOT_APPLICABLE".equals(categoryEval.state()))
            && isPassState(horizonEval.state())
            && isPassState(riskEval.state())
            && isPassState(modeEval.state());

        if (allApplicablePass) {
            return "ELIGIBLE";
        }

        // PARTIALLY_EVALUATED: No failure, some criteria are UNKNOWN/NOT_APPLICABLE
        return "PARTIALLY_EVALUATED";
    }

    /**
     * Returns true only for states that represent explicit positive evaluation.
     * UNKNOWN and NOT_APPLICABLE do NOT constitute a pass.
     */
    private boolean isPassState(String state) {
        return "MATCH".equals(state) || "SUPPORTED".equals(state) || "AVAILABLE".equals(state);
    }

    /**
     * Reads the existing YUKIRA Analytical Quality Score from the score engine.
     *
     * READ-ONLY. Never recalculates or alters the score.
     * Preserves actual methodology status (CANDIDATE, VALIDATED, etc.) without modification.
     * Missing score remains missing — never substituted with synthetic values.
     */
    private ScoreSummaryDto resolveAnalyticalScore(Long schemeOptionId) {
        List<AnalyticalScore> scores = analyticalScoreRepository.findLatestBySchemeOptionId(schemeOptionId);
        if (!scores.isEmpty()) {
            AnalyticalScore s = scores.get(0);

            // Confidence: derived from actual stored value; no default inference
            String confidenceStr;
            if (s.getConfidence() != null) {
                double confVal = s.getConfidence().doubleValue();
                if (confVal >= 0.8) confidenceStr = "HIGH";
                else if (confVal >= 0.5) confidenceStr = "MEDIUM";
                else confidenceStr = "LOW";
            } else {
                confidenceStr = "NOT_SPECIFIED";
            }

            // Methodology status: preserve actual DB value (CANDIDATE, VALIDATED, etc.)
            // Do NOT transform CANDIDATE to VALIDATED. Do NOT default to VALIDATED.
            String methodologyStatus = s.getMethodologyStatus() != null ? s.getMethodologyStatus() : "CANDIDATE";

            return new ScoreSummaryDto(
                true,
                s.getScore(),
                confidenceStr,
                methodologyStatus,
                s.getScoreVersion() != null ? s.getScoreVersion() : "YUKIRA_SCORE_V1",
                s.getAsOfDate() != null ? s.getAsOfDate().toString() : null
            );
        }

        // No score found — return honest unavailability state
        return new ScoreSummaryDto(
            false,
            null,
            "UNAVAILABLE",
            "NO_SCORE",
            "YUKIRA_SCORE_V1",
            null
        );
    }

    /**
     * Resolves evidence state with accurate as-of dates.
     *
     * CRITICAL: Historical NAV data must NEVER be labeled "CURRENT".
     * The quality flags expose the actual historical as-of date.
     * If data is as of 2024-01-15, investors must see "2024-01-15", not "CURRENT".
     */
    private EvidenceStateDto resolveEvidenceState(Long schemeOptionId, long navCount, SchemeInvestmentTerms terms) {
        List<NavObservation> navs = navObservationRepository.findBySchemeOptionId(schemeOptionId);
        String navAsOfDate = navs.isEmpty() ? "None" : navs.stream()
            .map(NavObservation::getEffectiveDate)
            .max(LocalDate::compareTo)
            .map(LocalDate::toString)
            .orElse("None");

        String termsAsOfDate = terms != null && terms.getAsOfDate() != null ? terms.getAsOfDate().toString() : "None";

        List<String> flags = new ArrayList<>();
        if (navCount > 0) {
            // Show the actual historical date — never "CURRENT"
            flags.add("NAV_AS_OF_" + navAsOfDate);
        }
        if (terms != null) flags.add("ENRICHMENT_VERIFIED");
        if (navCount == 0) flags.add("MISSING_NAV");

        String summary = String.format(
            "NAV observations: %d (as of %s) | Investment terms as-of: %s",
            navCount, navAsOfDate, termsAsOfDate
        );

        return new EvidenceStateDto(
            navAsOfDate,
            termsAsOfDate,
            summary,
            flags
        );
    }

    /**
     * Generates deterministic investigation questions from actual evidence states.
     *
     * Questions are derived exclusively from actual criterion states.
     * No invented facts. No AI prose. No assumption-based guidance.
     */
    private List<String> generateInvestigationQuestions(
        CriterionEvaluationDto categoryEval,
        CriterionEvaluationDto horizonEval,
        CriterionEvaluationDto riskEval,
        CriterionEvaluationDto modeEval,
        CriterionEvaluationDto navDataEval,
        ScoreSummaryDto scoreSummary,
        EvidenceStateDto evidenceState
    ) {
        List<String> q = new ArrayList<>();

        // Risk is always UNKNOWN — always generate this question
        q.add("Verify the current official SEBI Riskometer classification for this fund in the KIM/SID disclosures before investing.");

        // Horizon UNKNOWN — no lock-in data
        if ("UNKNOWN".equals(horizonEval.state())) {
            q.add("Investigate the scheme's current official investment-horizon guidance, minimum recommended holding period, and liquidity/exit load schedule in the Scheme Information Document (SID).");
        }

        // Investment mode UNKNOWN
        if ("UNKNOWN".equals(modeEval.state())) {
            q.add("Verify current SIP and lumpsum investment terms (minimum amounts, frequencies) from the official AMC scheme documents or registrar application form.");
        }

        // Score unavailable
        if (!scoreSummary.available()) {
            q.add("No YUKIRA Analytical Quality Score is currently available for this fund. Analytical evaluation cannot be completed without sufficient historical NAV data.");
        }

        // Score present but confidence is low
        if (scoreSummary.available() && "LOW".equalsIgnoreCase(scoreSummary.confidence())) {
            q.add(String.format(
                "Analytical Score confidence is LOW (as-of %s). Review underlying metric availability and observation window completeness before drawing conclusions.",
                scoreSummary.asOfDate() != null ? scoreSummary.asOfDate() : "date unknown"
            ));
        }

        // Historical NAV data — always clarify the as-of date boundary
        if (!"None".equals(evidenceState.navAsOfDate())) {
            q.add(String.format(
                "Confirm current fund information: the analytical evidence is as of %s. This is historical data. Obtain the latest NAV, portfolio, and scheme disclosures before taking any action.",
                evidenceState.navAsOfDate()
            ));
        }

        // Category UNKNOWN (missing master data)
        if ("UNKNOWN".equals(categoryEval.state())) {
            q.add("Scheme category is missing in master database records. Verify the official SEBI category classification from the scheme's SID.");
        }

        return q;
    }

    private String buildFundName(Scheme scheme, SchemePlan plan, SchemeOption option) {
        if (scheme == null) return "Unknown Mutual Fund Scheme";
        StringBuilder sb = new StringBuilder(scheme.getName());
        if (plan != null && plan.getPlanType() != null) {
            sb.append(" - ").append(plan.getPlanType()).append(" Plan");
        }
        if (option != null && option.getOptionType() != null) {
            sb.append(" - ").append(option.getOptionType()).append(" Option");
        }
        return sb.toString();
    }

    private int stateOrder(String state) {
        if ("ELIGIBLE".equals(state)) return 1;
        if ("PARTIALLY_EVALUATED".equals(state)) return 2;
        if ("INSUFFICIENT_DATA".equals(state)) return 3;
        return 4; // NOT_ELIGIBLE
    }
}
