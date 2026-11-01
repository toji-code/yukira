package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.lookthrough.*;
import com.yukira.backend.dto.portfolio.risk.*;
import com.yukira.backend.dto.portfolio.risk.stress.*;
import com.yukira.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service orchestrating Investor Portfolio Risk & Diversification Intelligence (Tasks 16, 17, 18, 19, 20).
 *
 * Implements a deterministic, evidence-driven portfolio risk, diversification, and lookback
 * sensitivity analysis using the investor's actual current mutual fund allocations and aligned
 * historical fund returns.
 *
 * Task 20/21 Risk Contribution Semantics & Numerical Classification Integrity:
 * 1. Corrected Risk Contribution Classification Semantics (Policy V3):
 *    - NEGATIVE_CONTRIBUTOR: pctRc < -0.0001% (empirical variance dampener, NOT guaranteed hedge).
 *    - NEGLIGIBLE_NEGATIVE_CONTRIBUTION: -0.0001% <= pctRc < 0.0% (numerical noise around zero, NOT economically meaningful).
 *    - BELOW_WEIGHT_CONTRIBUTION: 0.0 <= ratio < 0.98 (%RC is lower than capital allocation share; does NOT imply absolute risk reduction).
 *    - PROPORTIONAL_CONTRIBUTION: 0.98 <= ratio <= 1.02 (|ratio - 1.0| <= 2% tolerance), or both %RC and weight ~ 0.
 *    - ABOVE_WEIGHT_CONTRIBUTION: 1.02 < ratio <= 1.15 (above weight, below candidate risk-driver boundary).
 *    - RISK_DRIVER: ratio > 1.15, or positive %RC with near-zero capital allocation.
 *    - UNRELIABLE_ATTRIBUTION: ill-conditioned matrix, singular matrix, or Euler reconciliation failure.
 *    - UNCLASSIFIED: null, non-finite, or negative allocation weights.
 * 2. Complete 6-Tier Covariance Matrix Suitability Hierarchy (Policy V2):
 *    - Tier 1: Non-finite or incomplete matrix => UNSUITABLE, NUMERICALLY_INVALID.
 *    - Tier 2: Materially negative minimum eigenvalue (lambda_min < -1e-8) => UNSUITABLE, NUMERICALLY_INVALID.
 *    - Tier 3: Singular / rank-deficient (lambda_min <= 1e-14) => CONDITIONALLY_SUITABLE, LIMITED_EVIDENCE, kappa = null.
 *    - Tier 4: Positive but near-zero minimum eigenvalue (1e-14 < lambda_min < 1e-6) => CONDITIONALLY_SUITABLE, LIMITED_EVIDENCE.
 *    - Tier 5: Near-singular or ill-conditioned (lambda_min >= 1e-6, but kappa > 10,000 or N <= M) => CONDITIONALLY_SUITABLE, LIMITED_EVIDENCE.
 *    - Tier 6: Well-conditioned positive-semidefinite (lambda_min >= 1e-6, kappa <= 10,000, N > M) => SUITABLE.
 * 3. Reconciled Tail-Risk Evidence Policy (Policy V3):
 *    - SUFFICIENT_EVIDENCE: N >= 250 AND T >= 12.
 *    - LIMITED_EVIDENCE: 100 <= N < 250 AND T >= 5.
 *    - INSUFFICIENT_EVIDENCE: N < 100 OR T < 5.
 *    - UNAVAILABLE: N = 0.
 *    - Tail selection boundary strictly spans indices 0 to T - 1 (exact T observations); ES is exact mean of T losses.
 */
@Service
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class InvestorPortfolioRiskDiversificationService {

    private static final Logger log = LoggerFactory.getLogger(InvestorPortfolioRiskDiversificationService.class);
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String DEFAULT_DEV_SUBJECT = "dev-investor-local";

    public static final String METHODOLOGY_VERSION = "PORTFOLIO_RISK_DIVERSIFICATION_V1";
    public static final String EVIDENCE_POLICY_VERSION = "PORTFOLIO_RISK_EVIDENCE_POLICY_V3";
    public static final String CLASSIFICATION_POLICY_VERSION = "PORTFOLIO_RISK_CLASSIFICATION_POLICY_V4";
    public static final String MATRIX_SUITABILITY_POLICY_VERSION = "COVARIANCE_SUITABILITY_POLICY_V2";
    public static final String SENSITIVITY_METHODOLOGY_VERSION = "LOOKBACK_SENSITIVITY_V1";
    public static final BigDecimal NUMERICAL_TOLERANCE = BigDecimal.valueOf(0.0001);
    public static final double ALLOCATION_WEIGHT_EPSILON = 0.0001; // Boundary for zero/near-zero capital allocation (0.0001% = 1 ppm)
    public static final double CONTRIBUTION_RESOLUTION_EPSILON = 0.0001; // Boundary for reporting resolution (0.0001% pts)
    public static final int ANNUALIZATION_FACTOR = 252;
    public static final double SQRT_ANNUALIZATION = Math.sqrt(ANNUALIZATION_FACTOR);

    // Standard Lookback Window Observation Targets (Trading Days)
    public static final int OBS_WINDOW_1Y = 250;
    public static final int OBS_WINDOW_3Y = 750;
    public static final int OBS_WINDOW_5Y = 1250;

    // Authoritative Observation Window Evidence Thresholds
    public static final int MIN_OBS_WINDOW_SUFFICIENT = 250;
    public static final int MIN_OBS_WINDOW_LIMITED = 60;
    public static final int MIN_OBS_DRAWDOWN_SUFFICIENT = 500;
    public static final int MIN_OBS_DRAWDOWN_LIMITED = 126;

    // Authoritative Tail-Risk (RSK-06 / RSK-07) Evidence Thresholds (Task 20)
    public static final int MIN_OBS_TAIL_SUFFICIENT = 250;
    public static final int MIN_OBS_TAIL_LIMITED = 100;
    public static final int MIN_TAIL_OBS_SUFFICIENT = 12;
    public static final int MIN_TAIL_OBS_LIMITED = 5;

    // Numerical Suitability & Tolerances (6-Tier Policy V2)
    public static final double PSD_EIGENVALUE_EPSILON = 1e-8; // Boundary for material negative eigenvalue (-1e-8)
    public static final double SINGULAR_EIGENVALUE_ZERO_TOLERANCE = 1e-14; // Exact singularity check (1e-14)
    public static final double NEAR_SINGULAR_EIGENVALUE_THRESHOLD = 1e-6; // Boundary for well-conditioned min eigenvalue
    public static final double WELL_CONDITIONED_MIN_EIGENVALUE_THRESHOLD = NEAR_SINGULAR_EIGENVALUE_THRESHOLD;
    public static final double NEAR_SINGULAR_CONDITION_NUMBER_THRESHOLD = 10000.0; // Condition number boundary (kappa = 10^4)
    public static final double NEAR_ZERO_VOL_EPSILON = 1e-6;
    public static final double EULER_RECONCILIATION_TOLERANCE_VALUE = 0.0001;

    // Candidate Sensitivity & Classification Thresholds (Policy V2)
    public static final double PROPORTIONAL_CONTRIBUTION_TOLERANCE = 0.02; // ±2% tolerance around ratio = 1.0
    public static final double RISK_CONTRIBUTION_RATIO_DRIVER_THRESHOLD = 1.15; // ratio > 1.15x
    public static final double VOLATILITY_MATERIAL_SHIFT_PCT = 1.0; // |Delta_sigma| >= 1.0%
    public static final double VOLATILITY_RELATIVE_SHIFT_PCT = 10.0; // |RelShift| >= 10.0%
    public static final double CORRELATION_MATERIAL_SHIFT = 0.15; // |Delta_r| >= 0.15

    private final InvestorRepository investorRepository;
    private final InvestorPortfolioHoldingRepository portfolioHoldingRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final MultiFundLookThroughService lookThroughService;
    private final PortfolioHistoricalStressService historicalStressService;

    public InvestorPortfolioRiskDiversificationService(
        InvestorRepository investorRepository,
        InvestorPortfolioHoldingRepository portfolioHoldingRepository,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        MultiFundLookThroughService lookThroughService
    ) {
        this(investorRepository, portfolioHoldingRepository, schemeOptionRepository, navObservationRepository, lookThroughService, null);
    }

    @Autowired
    public InvestorPortfolioRiskDiversificationService(
        InvestorRepository investorRepository,
        InvestorPortfolioHoldingRepository portfolioHoldingRepository,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        MultiFundLookThroughService lookThroughService,
        PortfolioHistoricalStressService historicalStressService
    ) {
        this.investorRepository = investorRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.lookThroughService = lookThroughService;
        this.historicalStressService = historicalStressService;
    }

    private Optional<Investor> resolveInvestor(String auth0Subject) {
        String effectiveSubject = (auth0Subject != null && !auth0Subject.isBlank())
            ? auth0Subject
            : DEFAULT_DEV_SUBJECT;
        return investorRepository.findByAuth0Subject(effectiveSubject);
    }

    public PortfolioRiskDiversificationResponseDto calculatePortfolioRiskDiversification(
        String auth0Subject,
        PortfolioRiskDiversificationRequestDto request
    ) {
        return calculateRiskAndDiversification(auth0Subject, request);
    }

    public PortfolioRiskDiversificationResponseDto calculateRiskAndDiversification(
        String auth0Subject,
        PortfolioRiskDiversificationRequestDto request
    ) {
        String runId = "RISK-RUN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OffsetDateTime cutoff = (request != null && request.knowledgeCutoff() != null)
            ? request.knowledgeCutoff()
            : OffsetDateTime.now();
        LocalDate asOf = (request != null && request.asOfDate() != null)
            ? request.asOfDate()
            : cutoff.toLocalDate();

        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("MethodologyVersion", METHODOLOGY_VERSION);
        notes.put("EvidencePolicyVersion", EVIDENCE_POLICY_VERSION);
        notes.put("ClassificationPolicyVersion", CLASSIFICATION_POLICY_VERSION);
        notes.put("MatrixSuitabilityPolicyVersion", MATRIX_SUITABILITY_POLICY_VERSION);
        notes.put("SensitivityMethodologyVersion", SENSITIVITY_METHODOLOGY_VERSION);
        notes.put("AnnualizationFactor", String.valueOf(ANNUALIZATION_FACTOR));
        notes.put("AnnualizationConvention", "252 Trading Days (Square Root of Time)");
        notes.put("SampleDenominator", "N - 1 (Bessel's correction)");
        notes.put("RiskDecompositionModel", "Euler Covariance-Based Volatility Decomposition (RC_i = w_i * (Sigma * w)_i / sigma_p)");
        notes.put("ModelClassification", "Static Current-Allocation Historical Risk Proxy");
        notes.put("NumericalTolerance", NUMERICAL_TOLERANCE.toPlainString());
        notes.put("WellConditionedMinEigenvalueThreshold", String.valueOf(WELL_CONDITIONED_MIN_EIGENVALUE_THRESHOLD));
        notes.put("SingularEigenvalueZeroTolerance", String.valueOf(SINGULAR_EIGENVALUE_ZERO_TOLERANCE));
        notes.put("PsdEigenvalueTolerance", String.valueOf(PSD_EIGENVALUE_EPSILON));
        notes.put("ConditionNumberThreshold", String.valueOf(NEAR_SINGULAR_CONDITION_NUMBER_THRESHOLD));
        notes.put("ProportionalContributionTolerance", "±" + (int)(PROPORTIONAL_CONTRIBUTION_TOLERANCE * 100) + "%");
        notes.put("RiskContributionRatioThreshold", String.valueOf(RISK_CONTRIBUTION_RATIO_DRIVER_THRESHOLD));
        notes.put("TailRiskEvidenceRule", "Sufficient: N >= 250 and T >= 12; Limited: 100 <= N < 250 and T >= 5; Insufficient: N < 100 or T < 5");
        notes.put("HistoricalStressModel", "Static Current-Allocation Historical Stress Proxy (R_{p,t} = \\sum w_i R_{i,t})");
        notes.put("StressIdentificationPolicyVersion", PortfolioHistoricalStressService.IDENTIFICATION_POLICY_VERSION);
        notes.put("StressEvidencePolicyVersion", PortfolioHistoricalStressService.EVIDENCE_POLICY_VERSION);

        Map<String, String> registryMappings = new LinkedHashMap<>();
        registryMappings.put("RSK-01", "Annualized Volatility (Square root of 252 days sample variance)");
        registryMappings.put("RSK-02", "Downside Semideviation (Annualized semivariance below risk-free benchmark)");
        registryMappings.put("RSK-03", "Maximum Drawdown (Peak-to-trough historical cumulative return decline)");
        registryMappings.put("RSK-04", "Maximum Drawdown Duration (Trading days from peak to trough)");
        registryMappings.put("RSK-06", "Historical Value at Risk 95% (Daily empirical 5th percentile order statistic - CANDIDATE)");
        registryMappings.put("RSK-07", "Historical Expected Shortfall 95% (Daily conditional mean of losses <= VaR 95 - CANDIDATE)");
        registryMappings.put("PRT-01", "Top-10 Holdings Concentration");
        registryMappings.put("MKT-02", "Downside Beta");
        registryMappings.put("STR-01", "Historical Stress Proxy Cumulative Return (Constant-weight daily rebalanced proxy - CANDIDATE)");
        registryMappings.put("STR-02", "Historical Stress Maximum Drawdown (Deterministic peak-to-trough - CANDIDATE)");
        registryMappings.put("STR-03", "Constituent Exact Additive Return Contribution (Observation-Specific Carino Logarithmic Linking - CANDIDATE)");
        registryMappings.put("STR-04", "Constituent Weighted Return to Linked Attribution Difference (w_i * R_i,cum - A_i - CANDIDATE)");
        registryMappings.put("STR-05", "Portfolio Compounding Residual (Sum(w_i * R_i,cum) - R_p,cum - CANDIDATE)");

        List<String> disclaimers = new ArrayList<>();
        disclaimers.add("Static current-allocation historical portfolio risk proxy: Assumes current allocation weights remained constant over the historical observation window. This does NOT reflect realized investor portfolio performance or changing positions over time.");
        disclaimers.add("Historical Stress Proxy (Methodology V2): Constant-weight daily rebalanced portfolio proxy across deterministically identified market stress episodes (>= 10.0% benchmark drawdown) using broad Indian equity TRI reference (preferred: NIFTY 500 TRI). Multi-period return attribution uses Carino Logarithmic Linking for exact additive reconciliation (+/-0.0001% tolerance). Does not reconstruct actual historical trades, taxes, or realized investor performance.");
        disclaimers.add("Unvalued positions: Holdings with missing or unresolved NAV observations are excluded from weight derivation and risk calculations without silent redistribution.");
        disclaimers.add("Look-Ahead Bias Guard: All NAV observations strictly respect analysis_cutoff <= asOfDate and availability_time <= knowledgeCutoff.");
        disclaimers.add("Tail Risk Metrics: Historical VaR 95% and Historical Expected Shortfall 95% are CANDIDATE methodologies based on historical empirical distributions and do not represent guaranteed loss boundaries.");
        disclaimers.add("Non-Advisory Mandate: Descriptive mathematical risk analysis only. Zero automated investment advice, portfolio optimization, or buy/sell recommendations.");
        disclaimers.add("Authoritative Evidence Policy V3: General observation windows require >= 250 aligned trading days for SUFFICIENT_EVIDENCE, 60-249 days for LIMITED_EVIDENCE, and < 60 days are strictly rejected as INSUFFICIENT_EVIDENCE. Tail-risk estimation requires N >= 250 and T >= 12 for sufficient evidence, N >= 100 and T >= 5 for limited evidence.");
        disclaimers.add("Candidate Classification Policy V4: Defines an 11-tier mutually exclusive risk contribution taxonomy across 15 decision branches. Explicitly handles zero-weight positions, near-zero allocations, allocation-contribution mismatches, and negative contributions without assuming small values are floating-point noise. Ratio-based risk-driver status requires valid capital weight > 0.0001% and verified ratio > 1.15. A negative contribution indicates historical sample covariance dampening, NOT a guaranteed future hedge. Below-weight contribution does not automatically imply absolute risk reduction.");

        Optional<Investor> investorOpt = resolveInvestor(auth0Subject);
        if (investorOpt.isEmpty()) {
            return buildEmptyResponse("INVESTOR_NOT_FOUND", asOf, cutoff, "Investor account not registered or authenticated.", disclaimers, notes, runId, registryMappings);
        }

        Investor investor = investorOpt.get();
        List<InvestorPortfolioHolding> rawHoldings = portfolioHoldingRepository.findByInvestorId(investor.getId());
        if (rawHoldings.isEmpty()) {
            return buildEmptyResponse("PORTFOLIO_EMPTY", asOf, cutoff, "No mutual fund holdings found in investor portfolio.", disclaimers, notes, runId, registryMappings);
        }

        // Filter to override scheme option IDs if specified
        Set<Long> allowedOptionIds = (request != null && request.overrideSchemeOptionIds() != null && !request.overrideSchemeOptionIds().isEmpty())
            ? new HashSet<>(request.overrideSchemeOptionIds())
            : null;

        // 1. Resolve Position Valuations and Unvalued Positions
        BigDecimal totalKnownMarketValue = BigDecimal.ZERO;
        List<UnvaluedPositionDto> unvaluedPositions = new ArrayList<>();
        Map<Long, BigDecimal> fundMarketValues = new LinkedHashMap<>();
        Map<Long, SchemeOption> valuedSchemes = new LinkedHashMap<>();
        int totalPositionsCount = 0;

        for (InvestorPortfolioHolding h : rawHoldings) {
            SchemeOption opt = h.getSchemeOption();
            if (opt == null) continue;
            Long optId = opt.getId();
            if (allowedOptionIds != null && !allowedOptionIds.contains(optId)) {
                continue;
            }
            totalPositionsCount++;

            String fundName = (opt.getPlan() != null && opt.getPlan().getScheme() != null)
                ? opt.getPlan().getScheme().getName()
                : "Scheme Option #" + optId;
            String amfiCode = opt.getAmfiCode() != null ? opt.getAmfiCode() : "";

            BigDecimal units = h.getUnits() != null ? h.getUnits() : BigDecimal.ZERO;

            Optional<NavObservation> navOpt = navObservationRepository.findLatestAuthoritativeObservationAsOfCutoff(optId, asOf, cutoff);
            if (navOpt.isPresent() && navOpt.get().getNavValue() != null && navOpt.get().getNavValue().compareTo(BigDecimal.ZERO) > 0 && units.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal nav = navOpt.get().getNavValue();
                BigDecimal mv = units.multiply(nav).setScale(4, RoundingMode.HALF_UP);
                totalKnownMarketValue = totalKnownMarketValue.add(mv);
                fundMarketValues.put(optId, mv);
                valuedSchemes.put(optId, opt);
            } else {
                String reason = units.compareTo(BigDecimal.ZERO) <= 0
                    ? "Position has zero units recorded."
                    : "No authoritative point-in-time NAV observation available on or before " + asOf + ".";
                unvaluedPositions.add(new UnvaluedPositionDto(optId, fundName, amfiCode, units, reason));
            }
        }

        int valuedCount = fundMarketValues.size();
        int unvaluedCount = unvaluedPositions.size();
        BigDecimal coveragePct = totalPositionsCount > 0
            ? BigDecimal.valueOf(valuedCount).multiply(HUNDRED).divide(BigDecimal.valueOf(totalPositionsCount), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        if (valuedCount == 0 || totalKnownMarketValue.compareTo(BigDecimal.ZERO) <= 0) {
            return buildEmptyResponse("NO_VALUED_POSITIONS", asOf, cutoff, "No positions could be valued with authoritative NAV data.", disclaimers, notes, runId, registryMappings);
        }

        // 2. Derive Current Allocation Weights (w_i = mv_i / totalKnownMarketValue)
        List<Long> fundIds = new ArrayList<>(fundMarketValues.keySet());
        int M = fundIds.size();
        double[] weights = new double[M];
        Map<Long, BigDecimal> weightPcts = new LinkedHashMap<>();

        for (int i = 0; i < M; i++) {
            Long optId = fundIds.get(i);
            BigDecimal mv = fundMarketValues.get(optId);
            BigDecimal fraction = mv.divide(totalKnownMarketValue, MC);
            weights[i] = fraction.doubleValue();
            weightPcts.put(optId, fraction.multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP));
        }

        // 3. Historical NAV Alignment & Common Synchronized Returns
        Map<Long, Map<LocalDate, BigDecimal>> fundNavHistory = new LinkedHashMap<>();
        Map<String, Integer> perFundObservationCounts = new LinkedHashMap<>();

        for (Long optId : fundIds) {
            List<NavObservation> navList = navObservationRepository.findAuthoritativeObservationsAsOfCutoff(optId, asOf, cutoff);
            Map<LocalDate, BigDecimal> navMap = new HashMap<>();
            for (NavObservation no : navList) {
                if (no.getEffectiveDate() != null && no.getNavValue() != null && no.getNavValue().compareTo(BigDecimal.ZERO) > 0) {
                    navMap.put(no.getEffectiveDate(), no.getNavValue());
                }
            }
            fundNavHistory.put(optId, navMap);
            String fundDisplayName = getSchemeDisplayName(valuedSchemes.get(optId));
            perFundObservationCounts.put(fundDisplayName, navMap.size());
        }

        Set<LocalDate> commonDates = null;
        for (Map<LocalDate, BigDecimal> navMap : fundNavHistory.values()) {
            if (commonDates == null) {
                commonDates = new HashSet<>(navMap.keySet());
            } else {
                commonDates.retainAll(navMap.keySet());
            }
        }
        if (commonDates == null) {
            commonDates = Collections.emptySet();
        }

        List<LocalDate> sortedDates = new ArrayList<>(commonDates);
        Collections.sort(sortedDates);

        if (sortedDates.size() < 5) {
            return buildInsufficientHistoryResponse(
                asOf, cutoff, totalKnownMarketValue, valuedCount, unvaluedCount, coveragePct,
                unvaluedPositions, sortedDates.isEmpty() ? null : sortedDates.get(0),
                sortedDates.isEmpty() ? null : sortedDates.get(sortedDates.size() - 1),
                0, 0, 0, perFundObservationCounts,
                "Insufficient synchronized trading dates across valued funds (" + sortedDates.size() + " days found, minimum " + MIN_OBS_WINDOW_LIMITED + " required).",
                disclaimers, notes, runId, registryMappings
            );
        }

        // 4. Compute Daily Returns & Static Current-Allocation Portfolio Return Series
        int nDates = sortedDates.size();
        List<LocalDate> returnDates = new ArrayList<>();
        List<Double> portReturns = new ArrayList<>();
        Map<Long, List<Double>> fundReturns = new LinkedHashMap<>();
        for (Long optId : fundIds) {
            fundReturns.put(optId, new ArrayList<>());
        }

        int calendarGapExclusionsCount = 0;
        int excludedObservationCount = 0;

        for (int k = 1; k < nDates; k++) {
            LocalDate prevDate = sortedDates.get(k - 1);
            LocalDate currDate = sortedDates.get(k);

            // Skip intervals with abnormal calendar gaps (> 7 days)
            if (ChronoUnit.DAYS.between(prevDate, currDate) > 7) {
                calendarGapExclusionsCount++;
                continue;
            }

            boolean validInterval = true;
            double[] rStep = new double[M];
            for (int i = 0; i < M; i++) {
                Long optId = fundIds.get(i);
                BigDecimal pNav = fundNavHistory.get(optId).get(prevDate);
                BigDecimal cNav = fundNavHistory.get(optId).get(currDate);
                if (pNav == null || cNav == null || pNav.compareTo(BigDecimal.ZERO) <= 0) {
                    validInterval = false;
                    break;
                }
                rStep[i] = cNav.subtract(pNav).divide(pNav, 10, RoundingMode.HALF_UP).doubleValue();
            }

            if (!validInterval) {
                excludedObservationCount++;
                continue;
            }

            double pDailyRet = 0.0;
            for (int i = 0; i < M; i++) {
                fundReturns.get(fundIds.get(i)).add(rStep[i]);
                pDailyRet += weights[i] * rStep[i];
            }
            portReturns.add(pDailyRet);
            returnDates.add(currDate);
        }

        int K = portReturns.size();
        LocalDate commonStart = !returnDates.isEmpty() ? returnDates.get(0) : sortedDates.get(0);
        LocalDate commonEnd = !returnDates.isEmpty() ? returnDates.get(returnDates.size() - 1) : sortedDates.get(nDates - 1);

        // Authoritative Evidence Evaluation for Observation Window
        String windowEvidenceState = evaluateObservationWindowEvidenceState(K, MIN_OBS_WINDOW_SUFFICIENT);

        // Strict Task 17/19/20 policy: Enforce K >= 60 minimum for reliable risk attribution
        if (K < MIN_OBS_WINDOW_LIMITED) {
            return buildInsufficientHistoryResponse(
                asOf, cutoff, totalKnownMarketValue, valuedCount, unvaluedCount, coveragePct,
                unvaluedPositions, commonStart, commonEnd, K, excludedObservationCount, calendarGapExclusionsCount,
                perFundObservationCounts,
                "Insufficient valid synchronized return intervals after calendar gap filtering (" + K + " intervals found; minimum " + MIN_OBS_WINDOW_LIMITED + " required for limited evidence, " + MIN_OBS_WINDOW_SUFFICIENT + " for complete sufficient evidence).",
                disclaimers, notes, runId, registryMappings
            );
        }

        // Retrieve pairwise overlap information from lookThroughService if available
        Map<String, PairwiseOverlapResultDto> overlapLookup = new HashMap<>();
        try {
            if (M >= 2) {
                List<ResearchPortfolioFundItemDto> constituentInputs = new ArrayList<>();
                for (int i = 0; i < M; i++) {
                    constituentInputs.add(new ResearchPortfolioFundItemDto(fundIds.get(i), BigDecimal.valueOf(weights[i] * 100.0)));
                }
                MultiFundLookThroughRequestDto lookThroughReq = new MultiFundLookThroughRequestDto(
                    constituentInputs, asOf, cutoff, "STATIC"
                );
                MultiFundLookThroughResponseDto lookThroughResp = lookThroughService.calculateMultiFundLookThrough(lookThroughReq);
                if (lookThroughResp != null && lookThroughResp.pairwiseOverlapMatrix() != null) {
                    for (PairwiseOverlapResultDto ov : lookThroughResp.pairwiseOverlapMatrix()) {
                        String key1 = ov.fundAId() + "-" + ov.fundBId();
                        String key2 = ov.fundBId() + "-" + ov.fundAId();
                        overlapLookup.put(key1, ov);
                        overlapLookup.put(key2, ov);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Look-through overlap integration could not be computed: {}", ex.getMessage());
        }

        // 5. BASE FULL-SAMPLE RISK EVALUATION (All K observations)
        PortfolioLookbackWindowResultDto baseResult = evaluateLookbackSlice(
            "ALL", K, portReturns, fundReturns, returnDates, fundIds, valuedSchemes,
            fundMarketValues, weights, weightPcts, asOf, cutoff
        );

        // 6. TASK 18/19/20: EVALUATE STANDARD HISTORICAL LOOKBACK WINDOWS (1Y, 3Y, 5Y)
        PortfolioLookbackWindowResultDto win1Y = evaluateLookbackWindow(
            "1Y", OBS_WINDOW_1Y, portReturns, fundReturns, returnDates, fundIds, valuedSchemes,
            fundMarketValues, weights, weightPcts, asOf, cutoff
        );
        PortfolioLookbackWindowResultDto win3Y = evaluateLookbackWindow(
            "3Y", OBS_WINDOW_3Y, portReturns, fundReturns, returnDates, fundIds, valuedSchemes,
            fundMarketValues, weights, weightPcts, asOf, cutoff
        );
        PortfolioLookbackWindowResultDto win5Y = evaluateLookbackWindow(
            "5Y", OBS_WINDOW_5Y, portReturns, fundReturns, returnDates, fundIds, valuedSchemes,
            fundMarketValues, weights, weightPcts, asOf, cutoff
        );

        // 7. TASK 18/19/20: SENSITIVITY AND STABILITY COMPARISONS
        List<String> fundDisplayNames = new ArrayList<>();
        List<BigDecimal> fundWeightsList = new ArrayList<>();
        for (Long optId : fundIds) {
            fundDisplayNames.add(getSchemeDisplayName(valuedSchemes.get(optId)));
            fundWeightsList.add(weightPcts.get(optId));
        }

        PortfolioLookbackSensitivityDto lookbackSensitivity = computeLookbackSensitivity(
            win1Y, win3Y, win5Y, fundIds, fundDisplayNames, fundWeightsList, weights, valuedSchemes
        );

        // 8. Correlated Pairs & Overlap Context (Base sample)
        List<CorrelatedPairDto> highlyCorrelatedPairs = new ArrayList<>();
        List<CorrelatedPairDto> lowOrNegativePairs = new ArrayList<>();

        for (int i = 0; i < M; i++) {
            Long idA = fundIds.get(i);
            String nameA = getSchemeDisplayName(valuedSchemes.get(idA));
            for (int j = i + 1; j < M; j++) {
                Long idB = fundIds.get(j);
                String nameB = getSchemeDisplayName(valuedSchemes.get(idB));
                BigDecimal corrBd = baseResult.correlationMatrix().get(i).get(j);
                double corrVal = corrBd.doubleValue();

                String pairKey = idA + "-" + idB;
                PairwiseOverlapResultDto ov = overlapLookup.get(pairKey);
                int sharedCount = ov != null ? ov.sharedSecurityCount() : 0;
                BigDecimal ovWeight = ov != null ? ov.overlapWeightPct() : BigDecimal.ZERO;

                String interp;
                if (corrVal >= 0.80) {
                    interp = String.format("High historical return co-movement (r = %.2f). Shared underlying holdings: %d securities (%.1f%% overlap).",
                        corrVal, sharedCount, ovWeight.doubleValue());
                    highlyCorrelatedPairs.add(new CorrelatedPairDto(idA, nameA, idB, nameB, corrBd, K, sharedCount, interp));
                } else if (corrVal <= 0.30) {
                    interp = String.format("Low/negative co-movement (r = %.2f), offering portfolio diversification cushion.", corrVal);
                    lowOrNegativePairs.add(new CorrelatedPairDto(idA, nameA, idB, nameB, corrBd, K, sharedCount, interp));
                }
            }
        }

        // 9. Build Base Covariance Matrix DTO
        List<String> amfiCodesList = new ArrayList<>();
        List<List<BigDecimal>> covGrid = new ArrayList<>();
        List<List<Integer>> obsGrid = new ArrayList<>();
        List<List<String>> stateGrid = new ArrayList<>();

        // Recompute covariance matrix entries for base display
        double[][] annCovBase = computeCovarianceMatrix(fundReturns, fundIds, K);
        for (int i = 0; i < M; i++) {
            SchemeOption opt = valuedSchemes.get(fundIds.get(i));
            amfiCodesList.add(opt.getAmfiCode() != null ? opt.getAmfiCode() : "");
            List<BigDecimal> covRow = new ArrayList<>();
            List<Integer> obsRow = new ArrayList<>();
            List<String> stateRow = new ArrayList<>();
            for (int j = 0; j < M; j++) {
                covRow.add(BigDecimal.valueOf(annCovBase[i][j]).setScale(6, RoundingMode.HALF_UP));
                obsRow.add(K);
                stateRow.add(baseResult.matrixEvidenceState());
            }
            covGrid.add(covRow);
            obsGrid.add(obsRow);
            stateGrid.add(stateRow);
        }

        PortfolioCovarianceMatrixDto covMatrixDto = new PortfolioCovarianceMatrixDto(
            fundIds,
            fundDisplayNames,
            amfiCodesList,
            covGrid,
            baseResult.correlationMatrix(),
            obsGrid,
            stateGrid,
            "COMPLETE",
            baseResult.isPositiveSemiDefinite(),
            baseResult.matrixEvidenceState(),
            true, // symmetric
            true, // finite
            true, // nonNegativeDiag
            baseResult.isPositiveSemiDefinite(),
            baseResult.isNearSingular(),
            baseResult.minimumEigenvalue(),
            baseResult.conditionNumber() != null ? baseResult.conditionNumber().multiply(baseResult.minimumEigenvalue()) : BigDecimal.ZERO, // max eigenvalue
            baseResult.conditionNumber(),
            baseResult.isPositiveSemiDefinite() && !baseResult.isNearSingular() ? BigDecimal.ONE : BigDecimal.ZERO, // determinant indicator
            baseResult.matrixSuitabilityState(),
            baseResult.limitationExplanation()
        );

        // 10. Build Diversification Analysis DTO
        String diversificationClassification;
        double deVal = baseResult.diversificationEffectPct().doubleValue();
        double drVal = baseResult.diversificationRatio().doubleValue();
        if (deVal >= 3.0 || drVal >= 1.25) {
            diversificationClassification = "SIGNIFICANT_DIVERSIFICATION";
        } else if (deVal >= 1.0) {
            diversificationClassification = "MODERATE_DIVERSIFICATION";
        } else if (deVal >= 0.0) {
            diversificationClassification = "LOW_DIVERSIFICATION";
        } else {
            diversificationClassification = "CONCENTRATED_CO_MOVEMENT";
        }

        PortfolioDiversificationAnalysisDto diversificationDto = new PortfolioDiversificationAnalysisDto(
            baseResult.weightedStandaloneVolatilityPct(),
            baseResult.annualizedPortfolioVolatilityProxy(),
            baseResult.diversificationEffectPct(),
            baseResult.diversificationRatio(),
            diversificationClassification,
            baseResult.diversificationEvidenceState(),
            !baseResult.diversificationEvidenceState().equals("NUMERICALLY_INVALID") && !baseResult.diversificationEvidenceState().equals("INSUFFICIENT_EVIDENCE"),
            highlyCorrelatedPairs,
            lowOrNegativePairs,
            overlapLookup.values().stream().mapToInt(PairwiseOverlapResultDto::sharedSecurityCount).max().orElse(0),
            overlapLookup.values().stream().map(PairwiseOverlapResultDto::overlapWeightPct).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO),
            "Holdings look-through confirms sector distribution across funds; return co-movement reflects market cyclicality.",
            "Historical co-movement does not guarantee future diversification cushion. Correlations may increase during systemic equity downturns."
        );

        // 11. Build Downside Analysis DTO
        PortfolioDownsideAnalysisDto downsideAnalysis = new PortfolioDownsideAnalysisDto(
            baseResult.maximumDrawdownPct(),
            baseResult.observationStartDate(),
            baseResult.observationEndDate(),
            baseResult.drawdownDurationTradingDays(),
            0,
            baseResult.hasRecovered(),
            baseResult.drawdownEvidenceState(),
            "Calculated over " + K + " synchronized daily returns (" + commonStart + " to " + commonEnd + ").",
            BigDecimal.ZERO,
            baseResult.historicalVaR95Pct(),
            baseResult.historicalExpectedShortfall95Pct(),
            BigDecimal.valueOf(0.95),
            baseResult.effectiveTailObservationCount(),
            MIN_TAIL_OBS_SUFFICIENT,
            "Empirical 5th Percentile Order Statistic (Discrete Lower Tail Boundary)",
            "Exact Mean of Daily Empirical Losses within the 5% Tail",
            baseResult.varEvidenceState(),
            baseResult.expectedShortfallEvidenceState(),
            K,
            "CANDIDATE",
            "Calculated empirically over " + K + " aligned trading observations. Governed strictly as CANDIDATE methodology under Policy V3."
        );

        // 12. Build Investigation Findings & Grounded Questions
        List<RiskInvestigationFindingDto> findings = new ArrayList<>();

        findings.add(new RiskInvestigationFindingDto(
            "FINDING-VALUATION-COVERAGE",
            "OBSERVED_FACT",
            "Known-Valued Allocation Scope",
            String.format("Valuation covers %d of %d portfolio positions (%.1f%% known coverage).", valuedCount, totalPositionsCount, coveragePct.doubleValue()),
            String.format("Total known portfolio value is ₹%.2f across %d valued mutual funds. %d unvalued positions remain isolated.",
                totalKnownMarketValue.doubleValue(), valuedCount, unvaluedCount),
            "Verify whether any unvalued positions represent significant unpriced capital commitments.",
            "Review unvalued fund positions in portfolio ledger"
        ));

        // Deterministic numerical suitability findings covering the 6-tier hierarchy
        if ("UNSUITABLE".equals(baseResult.matrixSuitabilityState())) {
            findings.add(new RiskInvestigationFindingDto(
                "FINDING-NUMERICAL-NEGATIVE-EIGENVALUE",
                "DATA_LIMITATION",
                "Non-Positive-Semidefinite Covariance Matrix",
                baseResult.limitationExplanation(),
                String.format("Minimum eigenvalue is materially negative (%.6e < -1e-8), resulting in NUMERICALLY_INVALID covariance state.",
                    baseResult.minimumEigenvalue().doubleValue()),
                "Inspect data alignment and numerical tolerances for constituent NAV returns.",
                "Review numerical matrix diagnostic report"
            ));
        } else if (baseResult.conditionNumber() == null && baseResult.isNearSingular()) {
            findings.add(new RiskInvestigationFindingDto(
                "FINDING-NUMERICAL-SINGULAR-MATRIX",
                "DATA_LIMITATION",
                "Singular Covariance Matrix (Rank Deficient)",
                baseResult.limitationExplanation(),
                "Minimum eigenvalue is numerically zero (<= 1e-14), indicating exact collinearity between portfolio funds. Condition number is mathematically undefined.",
                "Review portfolio for duplicate fund options or identical underlying tracking mandates.",
                "Inspect pairwise correlation matrix for redundant funds"
            ));
        } else if (baseResult.minimumEigenvalue().doubleValue() < NEAR_SINGULAR_EIGENVALUE_THRESHOLD) {
            findings.add(new RiskInvestigationFindingDto(
                "FINDING-NUMERICAL-NEAR-ZERO-EIGENVALUE",
                "DATA_LIMITATION",
                "Near-Zero Covariance Eigenvalue",
                baseResult.limitationExplanation(),
                String.format("Minimum eigenvalue (%.6e) lies in the ill-conditioned transition zone (< 1e-6). Attribution stability is compromised.",
                    baseResult.minimumEigenvalue().doubleValue()),
                "Evaluate whether extreme co-movement reduces statistical reliability of risk decomposition.",
                "Examine constituent fund covariance diagnostics"
            ));
        } else if (baseResult.isNearSingular()) {
            findings.add(new RiskInvestigationFindingDto(
                "FINDING-NUMERICAL-ILL-CONDITIONED",
                "DATA_LIMITATION",
                "Near-Singular Covariance Matrix",
                baseResult.limitationExplanation(),
                String.format("Covariance matrix condition number (%.2f) exceeds threshold (10,000) or observation count N (%d) is close to constituent count M (%d).",
                    baseResult.conditionNumber().doubleValue(), K, M),
                "Assess whether high collinearity among funds affects attribution stability.",
                "Inspect condition number in portfolio covariance section"
            ));
        }

        // Tail-risk evidence limitation finding under Policy V3
        if ("INSUFFICIENT_EVIDENCE".equals(baseResult.varEvidenceState())) {
            findings.add(new RiskInvestigationFindingDto(
                "FINDING-INSUFFICIENT-TAIL-OBSERVATIONS",
                "DATA_LIMITATION",
                "Insufficient Historical Observations for Tail-Risk Estimation",
                "Tail-risk metrics (VaR 95% and Expected Shortfall 95%) have INSUFFICIENT_EVIDENCE status under Policy V3.",
                String.format("Sample contains %d aligned observations (%d tail observations). Stricter Policy V3 requires N >= 250 and T >= 12 for sufficient evidence, N >= 100 and T >= 5 for limited evidence.",
                    K, baseResult.effectiveTailObservationCount()),
                "Consider tail estimates indicative and subject to significant empirical sampling variance.",
                "Check observation counts in downside risk section"
            ));
        }

        // Sensitivity findings
        for (VolatilitySensitivityComparisonDto vComp : lookbackSensitivity.volatilityComparisons()) {
            if ("MATERIAL_DIFFERENCE_OBSERVED".equals(vComp.shiftClassification()) && vComp.absoluteDifferencePct() != null) {
                findings.add(new RiskInvestigationFindingDto(
                    "FINDING-SENSITIVITY-VOLATILITY-SHIFT",
                    "CALCULATED_RISK",
                    "Material Volatility Lookback Shift (" + vComp.comparisonPair() + ")",
                    vComp.explanation(),
                    String.format("Annualized portfolio volatility shifted by %.2f%% (relative change: %.1f%%) between %s and %s under Candidate Classification Policy V4.",
                        vComp.absoluteDifferencePct().doubleValue(), vComp.relativeChangePct().doubleValue(), vComp.windowA(), vComp.windowB()),
                    "Investigate whether recent market volatility regimes or changing fund behaviour explain this lookback sensitivity.",
                    "Inspect lookback sensitivity comparison in portfolio risk workspace"
                ));
                break;
            }
        }

        for (FundRiskContributionSensitivityDto cComp : lookbackSensitivity.contributionComparisons()) {
            if (cComp.hasSignFlipped() || cComp.hasClassificationShifted()) {
                findings.add(new RiskInvestigationFindingDto(
                    "FINDING-SENSITIVITY-CONTRIBUTION-SHIFT",
                    "CALCULATED_RISK",
                    "Risk Contribution Dynamic Shift (" + cComp.fundName() + ")",
                    cComp.sensitivityNote(),
                    String.format("%s changed classification (%s in 1Y vs %s in 3Y) under Candidate Classification Policy V4.", cComp.fundName(), cComp.classification1Y(), cComp.classification3Y()),
                    "Assess whether this holding's volatility contribution is structurally volatile across market cycles.",
                    "Review fund risk contribution breakdown across observation windows"
                ));
                break;
            }
        }

        for (CorrelationSensitivityComparisonDto corrComp : lookbackSensitivity.correlationComparisons()) {
            if (corrComp.isMateriallySensitive()) {
                findings.add(new RiskInvestigationFindingDto(
                    "FINDING-SENSITIVITY-CORRELATION-SHIFT",
                    "CALCULATED_RISK",
                    "Material Return Co-Movement Shift",
                    corrComp.interpretation(),
                    String.format("Historical correlation between %s and %s shifted materially across observation windows.",
                        corrComp.fundNameA(), corrComp.fundNameB()),
                    "Determine whether increased co-movement reduces expected diversification during market drawdowns.",
                    "Examine correlation sensitivity between " + corrComp.fundNameA() + " and " + corrComp.fundNameB()
                ));
                break;
            }
        }

        if (win3Y.status().equals("INSUFFICIENT_EVIDENCE") || win5Y.status().equals("INSUFFICIENT_EVIDENCE")
            || win3Y.status().equals("LIMITED_EVIDENCE") || win5Y.status().equals("LIMITED_EVIDENCE")) {
            findings.add(new RiskInvestigationFindingDto(
                "FINDING-SENSITIVITY-WINDOW-LIMITATION",
                "DATA_LIMITATION",
                "Multi-Year Lookback Window Evidence Limitation",
                "Extended historical lookback comparison (3Y/5Y) is constrained by available synchronized fund history.",
                "At least 750 aligned trading days are required for complete 3Y analysis and 1,250 for complete 5Y analysis.",
                "Review inception dates of newer constituent funds in portfolio.",
                "Check historical NAV observation coverage across portfolio"
            ));
        }

        // Top risk contributor finding - strictly requires verified ratio > 1.15 and reliable attribution under Policy V4
        PortfolioFundRiskContributionDto topRiskDriver = baseResult.fundRiskContributions().stream()
            .max(Comparator.comparing(PortfolioFundRiskContributionDto::percentageRiskContribution))
            .orElse(null);

        if (topRiskDriver != null && topRiskDriver.isMathematicallyReliable()) {
            if ("RISK_DRIVER".equals(topRiskDriver.classification())
                && topRiskDriver.contributionToWeightRatio() != null
                && topRiskDriver.contributionToWeightRatio().doubleValue() > RISK_CONTRIBUTION_RATIO_DRIVER_THRESHOLD) {
                findings.add(new RiskInvestigationFindingDto(
                    "FINDING-TOP-RISK-DRIVER",
                    "CALCULATED_RISK",
                    "Disproportionate Volatility Contribution",
                    String.format(Locale.US, "%s contributes %.1f%% of portfolio volatility from %.1f%% capital weight (ratio: %.2fx > 1.15x threshold).",
                        topRiskDriver.fundName(), topRiskDriver.percentageRiskContribution().doubleValue(),
                        topRiskDriver.allocationWeightPct().doubleValue(),
                        topRiskDriver.contributionToWeightRatio().doubleValue()),
                    String.format(Locale.US, "Standalone annualized volatility of %.2f%% p.a. and covariance with other holdings make this fund a disproportionate risk contributor under Policy V4.",
                        topRiskDriver.standaloneVolatilityPct().doubleValue()),
                    "Evaluate whether this allocation's expected risk-adjusted return justifies its volatility dominance.",
                    "Conduct fund research brief on " + topRiskDriver.fundName()
                ));
            } else if ("ABOVE_WEIGHT_CONTRIBUTION".equals(topRiskDriver.classification())
                && topRiskDriver.contributionToWeightRatio() != null) {
                findings.add(new RiskInvestigationFindingDto(
                    "FINDING-TOP-RISK-CONTRIBUTOR",
                    "CALCULATED_RISK",
                    "Above-Weight Volatility Contribution",
                    String.format(Locale.US, "%s contributes %.1f%% of portfolio volatility from %.1f%% capital weight (ratio: %.2fx <= 1.15x threshold).",
                        topRiskDriver.fundName(), topRiskDriver.percentageRiskContribution().doubleValue(),
                        topRiskDriver.allocationWeightPct().doubleValue(),
                        topRiskDriver.contributionToWeightRatio().doubleValue()),
                    "Risk contribution exceeds capital allocation but remains below the 1.15x candidate risk-driver boundary. Candidate descriptive classification under Policy V4.",
                    "Assess whether this moderate tilt aligns with intended portfolio risk allocation.",
                    "Review fund risk contribution breakdown"
                ));
            }
        }

        // Allocation-contribution mismatch finding - highlights mathematical or data inconsistencies where weight is zero or near-zero
        List<PortfolioFundRiskContributionDto> mismatches = baseResult.fundRiskContributions().stream()
            .filter(c -> "ALLOCATION_CONTRIBUTION_MISMATCH".equals(c.classification()))
            .toList();
        if (!mismatches.isEmpty()) {
            PortfolioFundRiskContributionDto m = mismatches.get(0);
            findings.add(new RiskInvestigationFindingDto(
                "FINDING-ALLOCATION-CONTRIBUTION-MISMATCH",
                "DATA_LIMITATION",
                "Allocation and Risk Attribution Inconsistency (" + m.fundName() + ")",
                String.format(Locale.US, "%s exhibits risk contribution (%%RC = %.2f%%) despite zero or negligible capital allocation (%.4f%%).",
                    m.fundName(), m.percentageRiskContribution().doubleValue(), m.allocationWeightPct().doubleValue()),
                "Euler decomposition indicates non-negligible risk contribution from a position with zero or negligible allocation weight. Indicates potential data alignment discrepancy or unvalued position leakage under Policy V4.",
                "Verify position valuation and units recorded in portfolio ledger.",
                "Review unvalued fund positions and portfolio ledger"
            ));
        }

        // Negative contributor finding - highlight empirical variance dampeners with strict disclaimers
        List<PortfolioFundRiskContributionDto> negContributors = baseResult.fundRiskContributions().stream()
            .filter(c -> c.isMathematicallyReliable() && "NEGATIVE_CONTRIBUTOR".equals(c.classification())
                && c.percentageRiskContribution() != null && c.percentageRiskContribution().doubleValue() < -CONTRIBUTION_RESOLUTION_EPSILON)
            .toList();
        if (!negContributors.isEmpty()) {
            PortfolioFundRiskContributionDto neg = negContributors.get(0);
            findings.add(new RiskInvestigationFindingDto(
                "FINDING-NEGATIVE-RISK-CONTRIBUTOR",
                "OBSERVED_FACT",
                "Negative Historical Risk Contribution (" + neg.fundName() + ")",
                String.format(Locale.US, "%s exhibited negative historical component risk contribution (%%RC = %.2f%%) over the sample window.",
                    neg.fundName(), neg.percentageRiskContribution().doubleValue()),
                "Negative sample covariance with other portfolio assets reduced portfolio variance at the margin over the observed window under Policy V4. Descriptive empirical property of the sample, NOT a guaranteed future hedge or capital preservation benefit.",
                "Investigate historical return covariance regimes and stress periods for this holding.",
                "Review fund covariance and correlation in portfolio risk workspace"
            ));
        }

        // 13. Build Summary DTO
        String alignmentLimitations = String.format(
            "Synchronous common dates required across all %d valued funds. %d intervals excluded due to calendar gaps (> 7 days). %d dates excluded due to missing observations. %d unvalued positions isolated.",
            valuedCount, calendarGapExclusionsCount, excludedObservationCount, unvaluedCount
        );

        // Euler reconciliation on base sample
        double sumRcBase = 0.0;
        for (PortfolioFundRiskContributionDto c : baseResult.fundRiskContributions()) {
            sumRcBase += c.componentRiskContributionPct().doubleValue();
        }
        double eulerDiscrepancyBase = Math.abs(sumRcBase - baseResult.annualizedPortfolioVolatilityProxy().doubleValue());
        BigDecimal eulerDiscBd = BigDecimal.valueOf(eulerDiscrepancyBase).setScale(6, RoundingMode.HALF_UP);
        boolean isEulerReconciledBase = eulerDiscrepancyBase <= EULER_RECONCILIATION_TOLERANCE_VALUE && baseResult.isPositiveSemiDefinite();

        PortfolioRiskSummaryDto summaryDto = new PortfolioRiskSummaryDto(
            totalKnownMarketValue.setScale(2, RoundingMode.HALF_UP),
            valuedCount,
            unvaluedCount,
            coveragePct,
            "Total Known Portfolio Market Value",
            unvaluedPositions,
            asOf,
            cutoff,
            commonStart,
            commonEnd,
            K,
            windowEvidenceState,
            windowEvidenceState,
            baseResult.portfolioVolatilityEvidenceState(),
            excludedObservationCount,
            calendarGapExclusionsCount,
            true,
            perFundObservationCounts,
            alignmentLimitations,
            baseResult.annualizedPortfolioVolatilityProxy(),
            ANNUALIZATION_FACTOR,
            "252 Trading Days (Square Root of Time)",
            "N - 1 (Bessel's correction)",
            eulerDiscBd,
            isEulerReconciledBase,
            isEulerReconciledBase ? baseResult.matrixEvidenceState() : "NUMERICALLY_INVALID",
            "Static current-allocation historical portfolio risk proxy. Does not reflect realized historical investor returns or changing allocation weights over time."
        );

        // Tasks 48, 62, 63, 67: Unified Portfolio Allocation Snapshot across Risk, Sensitivity, and Stress
        String snapshotId = "ALLOC-SNAP-" + asOf + "-" + (runId.length() >= 8 ? runId.substring(0, 8) : runId);
        String snapshotFingerprint = PortfolioAllocationSnapshotDto.computeSnapshotFingerprint(
            snapshotId, asOf, asOf, valuedCount, unvaluedCount
        );
        BigDecimal totalPortfolioCapital = (unvaluedPositions == null || unvaluedPositions.isEmpty()) ? totalKnownMarketValue : null;
        PortfolioAllocationSnapshotDto allocationSnapshot = new PortfolioAllocationSnapshotDto(
            snapshotId,
            asOf,
            asOf,
            cutoff,
            totalKnownMarketValue,
            totalPortfolioCapital, // totalPortfolioCapitalAmount (Task 106: known if no unvalued positions; null if unvalued positions exist without prices)
            coveragePct,
            BigDecimal.valueOf(100.0), // valuedAllocationNormalizedPercentage
            valuedCount,
            unvaluedCount,
            valuedCount + unvaluedCount,
            valuedCount,
            unvaluedCount,
            (unvaluedPositions == null || unvaluedPositions.isEmpty()) ? "COMPLETE_VALUATION" : "PARTIAL_VALUATION_EXPOSURE_PRESENT",
            "TOTAL_VALUED_CAPITAL",
            BigDecimal.valueOf(100.0),
            snapshotFingerprint,
            "Capital weights derived from current investor portfolio valuation as of " + asOf + " and held constant across historical stress episodes and risk lookback windows with daily rebalancing.",
            true
        );

        PortfolioStressAnalysisResponseDto historicalStressAnalysis = null;
        if (historicalStressService != null) {
            try {
                String requestedBm = (request != null && request.benchmarkCode() != null)
                    ? request.benchmarkCode() : null;
                historicalStressAnalysis = historicalStressService.evaluateHistoricalStress(
                    investor.getId(), investor.getId(),
                    asOf, cutoff, requestedBm, fundIds, valuedSchemes, fundMarketValues,
                    weights, weightPcts, fundNavHistory, coveragePct, unvaluedPositions, findings, runId, allocationSnapshot
                );
            } catch (Exception e) {
                log.warn("Historical stress analysis evaluation failed: {}", e.getMessage(), e);
            }
        }

        return new PortfolioRiskDiversificationResponseDto(
            "SUCCESS",
            summaryDto,
            covMatrixDto,
            baseResult.fundRiskContributions(),
            diversificationDto,
            downsideAnalysis,
            findings,
            disclaimers,
            notes,
            runId,
            EVIDENCE_POLICY_VERSION,
            NUMERICAL_TOLERANCE,
            registryMappings,
            lookbackSensitivity,
            historicalStressAnalysis,
            allocationSnapshot
        );
    }

    /**
     * Evaluates lookback window (1Y, 3Y, 5Y) using latest eligible observation slice.
     * Enforces Authoritative Evidence Policy V3: slices with K < 60 are strictly rejected.
     */
    private PortfolioLookbackWindowResultDto evaluateLookbackWindow(
        String windowCode,
        int requestedObs,
        List<Double> allPortReturns,
        Map<Long, List<Double>> allFundReturns,
        List<LocalDate> allReturnDates,
        List<Long> fundIds,
        Map<Long, SchemeOption> valuedSchemes,
        Map<Long, BigDecimal> fundMarketValues,
        double[] weights,
        Map<Long, BigDecimal> weightPcts,
        LocalDate asOf,
        OffsetDateTime cutoff
    ) {
        int K = allPortReturns.size();

        // Strict rejection if below absolute minimum observation threshold (60)
        if (K < MIN_OBS_WINDOW_LIMITED) {
            return buildUnavailableLookbackWindow(
                windowCode, requestedObs, K,
                String.format("Portfolio has only %d aligned observations; minimum %d required for limited evidence (%d for complete window).",
                    K, MIN_OBS_WINDOW_LIMITED, requestedObs)
            );
        }

        int sliceLength = Math.min(K, requestedObs);
        int startIdx = K - sliceLength;

        List<Double> portSlice = allPortReturns.subList(startIdx, K);
        List<LocalDate> datesSlice = allReturnDates.subList(startIdx, K);
        Map<Long, List<Double>> fundSlice = new LinkedHashMap<>();
        for (Long id : fundIds) {
            fundSlice.put(id, allFundReturns.get(id).subList(startIdx, K));
        }

        return evaluateLookbackSlice(
            windowCode, requestedObs, portSlice, fundSlice, datesSlice, fundIds,
            valuedSchemes, fundMarketValues, weights, weightPcts, asOf, cutoff
        );
    }

    /**
     * Deterministic mathematical evaluation over an aligned return slice.
     */
    private PortfolioLookbackWindowResultDto evaluateLookbackSlice(
        String windowCode,
        int requestedObs,
        List<Double> portReturns,
        Map<Long, List<Double>> fundReturns,
        List<LocalDate> returnDates,
        List<Long> fundIds,
        Map<Long, SchemeOption> valuedSchemes,
        Map<Long, BigDecimal> fundMarketValues,
        double[] weights,
        Map<Long, BigDecimal> weightPcts,
        LocalDate asOf,
        OffsetDateTime cutoff
    ) {
        int N = portReturns.size();
        int M = fundIds.size();
        LocalDate startDate = returnDates.get(0);
        LocalDate endDate = returnDates.get(N - 1);

        // Authoritative Evidence Evaluation for Window
        String windowEvidenceState = evaluateObservationWindowEvidenceState(N, requestedObs);
        String status = N >= requestedObs ? "AVAILABLE" : (N >= MIN_OBS_WINDOW_LIMITED ? "LIMITED_EVIDENCE" : "INSUFFICIENT_EVIDENCE");

        // Covariance & Correlation (Dimensionally Consistent Annualization)
        double[][] annCovMatrix = computeCovarianceMatrix(fundReturns, fundIds, N);
        double[] standaloneDailyStd = new double[M];
        for (int i = 0; i < M; i++) {
            List<Double> rI = fundReturns.get(fundIds.get(i));
            double mean = rI.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double acc = 0.0;
            for (double r : rI) acc += (r - mean) * (r - mean);
            standaloneDailyStd[i] = Math.sqrt(Math.max(acc / (N - 1), 0.0));
        }

        List<List<BigDecimal>> corrGrid = new ArrayList<>();
        for (int i = 0; i < M; i++) {
            List<BigDecimal> row = new ArrayList<>();
            for (int j = 0; j < M; j++) {
                double denom = standaloneDailyStd[i] * standaloneDailyStd[j];
                double dailyCov = annCovMatrix[i][j] / ANNUALIZATION_FACTOR;
                double corr = (i == j) ? 1.0 : (denom > 1e-12 ? Math.max(-1.0, Math.min(1.0, dailyCov / denom)) : 0.0);
                row.add(BigDecimal.valueOf(corr).setScale(4, RoundingMode.HALF_UP));
            }
            corrGrid.add(row);
        }

        // Centralized Jacobi Eigensolver & 6-Tier Matrix Suitability Evaluation
        double[] eigenvalues = computeSymmetricEigenvalues(annCovMatrix);
        double minEigenvalue = eigenvalues[0];
        double maxEigenvalue = eigenvalues[M - 1];

        MatrixSuitabilityDecision suitability = evaluateMatrixSuitability(minEigenvalue, maxEigenvalue, N, M);
        boolean isPSD = suitability.isPositiveSemiDefinite();
        boolean isNearSingular = suitability.isNearSingular();
        Double conditionNumber = suitability.conditionNumber();
        String matrixSuitabilityState = suitability.matrixSuitabilityState();
        String matrixEvidenceState = suitability.matrixEvidenceState();

        // Portfolio Volatility Proxy
        double[] sigmaW = new double[M];
        double portVar = 0.0;
        for (int i = 0; i < M; i++) {
            double dot = 0.0;
            for (int j = 0; j < M; j++) {
                dot += annCovMatrix[i][j] * weights[j];
            }
            sigmaW[i] = dot;
            portVar += weights[i] * dot;
        }

        double portVolDec = Math.sqrt(Math.max(portVar, 0.0));
        double portVolPct = portVolDec * 100.0;
        String portVolEvidenceState = portVolDec <= NEAR_ZERO_VOL_EPSILON ? "NUMERICALLY_INVALID" : matrixEvidenceState;

        // Standalone Volatilities & Corrected Risk Contribution Classification (Policy V3)
        double[] standaloneAnnVolPct = new double[M];
        double weightedStandaloneVolPct = 0.0;
        List<PortfolioFundRiskContributionDto> riskContribs = new ArrayList<>();
        boolean reliable = isPSD && !isNearSingular && portVolDec > NEAR_ZERO_VOL_EPSILON;

        // Precompute component risk contributions and Euler reconciliation for this slice
        double[] mrc = new double[M];
        double[] rc = new double[M];
        double[] pctRc = new double[M];
        double sumRcSlice = 0.0;
        for (int i = 0; i < M; i++) {
            mrc[i] = (portVolDec > NEAR_ZERO_VOL_EPSILON && isPSD) ? (sigmaW[i] / portVolDec) * 100.0 : 0.0;
            rc[i] = weights[i] * mrc[i];
            pctRc[i] = (portVolPct > NEAR_ZERO_VOL_EPSILON && isPSD) ? (rc[i] / portVolPct) * 100.0 : 0.0;
            sumRcSlice += rc[i];
        }
        double eulerDiscrepancySlice = Math.abs(sumRcSlice - portVolPct);
        boolean isEulerReconciledSlice = isPSD && (eulerDiscrepancySlice <= EULER_RECONCILIATION_TOLERANCE_VALUE);

        for (int i = 0; i < M; i++) {
            standaloneAnnVolPct[i] = standaloneDailyStd[i] * SQRT_ANNUALIZATION * 100.0;
            weightedStandaloneVolPct += weights[i] * standaloneAnnVolPct[i];

            Long fId = fundIds.get(i);
            SchemeOption opt = valuedSchemes.get(fId);
            String fName = getSchemeDisplayName(opt);
            String amfi = opt.getAmfiCode() != null ? opt.getAmfiCode() : "";
            String plan = opt.getPlan() != null && opt.getPlan().getPlanType() != null ? opt.getPlan().getPlanType() : "DIRECT";
            String cat = opt.getPlan() != null && opt.getPlan().getScheme() != null ? opt.getPlan().getScheme().getCategory() : "Unspecified";

            // Shared centralized risk contribution classification (Task 22 Policy V4)
            Double allocWeight = (weightPcts.get(fId) != null) ? weightPcts.get(fId).doubleValue() : null;
            RiskContributionClassification rcc = classifyFundRiskContribution(pctRc[i], allocWeight, reliable, isEulerReconciledSlice);

            String standaloneVolEvidence = evaluateStandaloneVolatilityEvidenceState(N);

            riskContribs.add(new PortfolioFundRiskContributionDto(
                fId, fName, amfi, plan, cat, fundMarketValues.get(fId), weightPcts.get(fId),
                BigDecimal.valueOf(standaloneAnnVolPct[i]).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(mrc[i]).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(rc[i]).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(pctRc[i]).setScale(2, RoundingMode.HALF_UP),
                rcc.contributionToWeightRatio(),
                rcc.isNegativeContribution(),
                rcc.isRiskDriver(),
                rcc.isDiversificationDampener(),
                rcc.classificationToken(),
                matrixEvidenceState,
                standaloneVolEvidence,
                matrixEvidenceState,
                matrixEvidenceState,
                reliable && isEulerReconciledSlice,
                rcc.interpretation()
            ));
        }

        // Diversification Effect & Ratio
        double divEffectPct = weightedStandaloneVolPct - portVolPct;
        double divRatio = portVolPct > NEAR_ZERO_VOL_EPSILON ? (weightedStandaloneVolPct / portVolPct) : 1.0;
        String divEvidenceState = portVolDec <= NEAR_ZERO_VOL_EPSILON || !isPSD ? "NUMERICALLY_INVALID" : matrixEvidenceState;

        // Downside Dynamics: Maximum Drawdown & Duration
        double peakW = 1.0;
        double currentW = 1.0;
        double maxDd = 0.0;
        LocalDate peakDate = startDate;
        LocalDate troughDate = startDate;
        LocalDate currentPeakDate = startDate;

        for (int t = 0; t < N; t++) {
            double r = portReturns.get(t);
            LocalDate date = returnDates.get(t);
            currentW *= (1.0 + r);
            if (currentW > peakW) {
                peakW = currentW;
                currentPeakDate = date;
            }
            double dd = (currentW - peakW) / peakW;
            if (dd < maxDd) {
                maxDd = dd;
                troughDate = date;
                peakDate = currentPeakDate;
            }
        }

        int ddDurationDays = (int) ChronoUnit.DAYS.between(peakDate, troughDate);
        boolean hasRecovered = currentW >= peakW && troughDate.isBefore(endDate);
        String ddEvidenceState = evaluateDrawdownEvidenceState(N);

        // Centralized Tail-Risk: Empirical 5th Percentile Order Statistic & Expected Shortfall (Policy V3)
        TailRiskResult tailRisk = computeTailRisk(portReturns);

        List<String> fundNamesList = new ArrayList<>();
        for (Long id : fundIds) {
            fundNamesList.add(getSchemeDisplayName(valuedSchemes.get(id)));
        }

        String limitationMsg = String.format("Calculated over %d synchronized trading days (%s to %s). %s",
            N, startDate, endDate, (N < requestedObs ? "Window truncated by available fund history (" + N + " of " + requestedObs + " days)." : "Full requested observation quota met."));

        return new PortfolioLookbackWindowResultDto(
            windowCode,
            requestedObs,
            N,
            startDate,
            endDate,
            windowEvidenceState,
            matrixSuitabilityState,
            matrixEvidenceState,
            BigDecimal.valueOf(minEigenvalue).setScale(6, RoundingMode.HALF_UP),
            conditionNumber != null ? BigDecimal.valueOf(conditionNumber).setScale(2, RoundingMode.HALF_UP) : null,
            isPSD,
            isNearSingular,
            BigDecimal.valueOf(portVolPct).setScale(2, RoundingMode.HALF_UP),
            portVolEvidenceState,
            BigDecimal.valueOf(weightedStandaloneVolPct).setScale(2, RoundingMode.HALF_UP),
            BigDecimal.valueOf(divEffectPct).setScale(2, RoundingMode.HALF_UP),
            BigDecimal.valueOf(divRatio).setScale(2, RoundingMode.HALF_UP),
            divEvidenceState,
            BigDecimal.valueOf(maxDd * 100.0).setScale(2, RoundingMode.HALF_UP),
            ddDurationDays,
            hasRecovered,
            ddEvidenceState,
            BigDecimal.valueOf(tailRisk.var95LossPct()).setScale(2, RoundingMode.HALF_UP),
            BigDecimal.valueOf(tailRisk.es95LossPct()).setScale(2, RoundingMode.HALF_UP),
            tailRisk.tailCount(),
            tailRisk.varEvidenceState(),
            tailRisk.esEvidenceState(),
            riskContribs,
            corrGrid,
            fundNamesList,
            fundIds,
            status,
            limitationMsg
        );
    }

    /**
     * Constructs unavailable lookback window representation.
     */
    private PortfolioLookbackWindowResultDto buildUnavailableLookbackWindow(
        String windowCode,
        int requestedObs,
        int actualObs,
        String reason
    ) {
        return new PortfolioLookbackWindowResultDto(
            windowCode, requestedObs, actualObs, null, null,
            "INSUFFICIENT_EVIDENCE", "UNAVAILABLE", "UNAVAILABLE",
            BigDecimal.ZERO, null, false, false,
            BigDecimal.ZERO, "UNAVAILABLE",
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, "UNAVAILABLE",
            BigDecimal.ZERO, 0, false, "UNAVAILABLE",
            BigDecimal.ZERO, BigDecimal.ZERO, 0, "UNAVAILABLE", "UNAVAILABLE",
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            "INSUFFICIENT_EVIDENCE", reason
        );
    }

    /**
     * Computes sensitivity comparisons across 1Y, 3Y, and 5Y lookback windows.
     */
    private PortfolioLookbackSensitivityDto computeLookbackSensitivity(
        PortfolioLookbackWindowResultDto win1Y,
        PortfolioLookbackWindowResultDto win3Y,
        PortfolioLookbackWindowResultDto win5Y,
        List<Long> fundIds,
        List<String> fundNames,
        List<BigDecimal> weightsList,
        double[] weights,
        Map<Long, SchemeOption> valuedSchemes
    ) {
        List<PortfolioLookbackWindowResultDto> windowsList = List.of(win1Y, win3Y, win5Y);

        // 1. Volatility Sensitivity Comparisons
        List<VolatilitySensitivityComparisonDto> volComparisons = new ArrayList<>();
        volComparisons.add(compareVolatility("1Y_VS_3Y", win1Y, win3Y));
        volComparisons.add(compareVolatility("3Y_VS_5Y", win3Y, win5Y));
        volComparisons.add(compareVolatility("1Y_VS_5Y", win1Y, win5Y));

        // 2. Correlation Sensitivity Comparisons
        List<CorrelationSensitivityComparisonDto> corrComparisons = new ArrayList<>();
        int M = fundIds.size();
        for (int i = 0; i < M; i++) {
            for (int j = i + 1; j < M; j++) {
                Long idA = fundIds.get(i);
                Long idB = fundIds.get(j);
                String nameA = fundNames.get(i);
                String nameB = fundNames.get(j);

                BigDecimal r1Y = getPairCorrelation(win1Y, i, j);
                BigDecimal r3Y = getPairCorrelation(win3Y, i, j);
                BigDecimal r5Y = getPairCorrelation(win5Y, i, j);

                BigDecimal delta1Yto3Y = (r1Y != null && r3Y != null) ? r1Y.subtract(r3Y).setScale(4, RoundingMode.HALF_UP) : null;
                BigDecimal delta3Yto5Y = (r3Y != null && r5Y != null) ? r3Y.subtract(r5Y).setScale(4, RoundingMode.HALF_UP) : null;

                boolean isMaterial = (delta1Yto3Y != null && Math.abs(delta1Yto3Y.doubleValue()) >= CORRELATION_MATERIAL_SHIFT)
                    || (delta3Yto5Y != null && Math.abs(delta3Yto5Y.doubleValue()) >= CORRELATION_MATERIAL_SHIFT);

                String class1Y = r1Y != null ? (r1Y.doubleValue() >= 0.80 ? "HIGH_CO_MOVEMENT" : (r1Y.doubleValue() <= 0.30 ? "LOW_OR_NEGATIVE" : "MODERATE")) : "UNAVAILABLE";
                String class3Y = r3Y != null ? (r3Y.doubleValue() >= 0.80 ? "HIGH_CO_MOVEMENT" : (r3Y.doubleValue() <= 0.30 ? "LOW_OR_NEGATIVE" : "MODERATE")) : "UNAVAILABLE";

                boolean classificationChanged = !class1Y.equals("UNAVAILABLE") && !class3Y.equals("UNAVAILABLE") && !class1Y.equals(class3Y);
                boolean compDirect = win1Y.windowEvidenceState().equals("SUFFICIENT_EVIDENCE") && win3Y.windowEvidenceState().equals("SUFFICIENT_EVIDENCE");
                String compState = (r1Y != null && r3Y != null) ? (compDirect ? "DIRECTLY_COMPARABLE" : "CONDITIONALLY_COMPARABLE") : "INSUFFICIENT_EVIDENCE";

                String interp;
                if (r1Y != null && r3Y != null) {
                    interp = String.format("Pairwise correlation moved from %.2f (3Y) to %.2f (1Y) (delta: %+.2f). %s",
                        r3Y.doubleValue(), r1Y.doubleValue(), delta1Yto3Y.doubleValue(),
                        isMaterial ? "Material co-movement shift observed (>= 0.15 threshold). Candidate convention." : "Correlation remained relatively stable under candidate threshold.");
                } else {
                    interp = "Correlation sensitivity comparison unavailable due to insufficient multi-year data.";
                }

                corrComparisons.add(new CorrelationSensitivityComparisonDto(
                    idA, nameA, idB, nameB, r1Y, r3Y, r5Y, delta1Yto3Y, delta3Yto5Y,
                    classificationChanged, isMaterial, compState, interp
                ));
            }
        }

        // 3. Fund Risk Contribution Sensitivity Comparisons (Policy V2)
        List<FundRiskContributionSensitivityDto> contribComparisons = new ArrayList<>();
        for (int i = 0; i < M; i++) {
            Long fId = fundIds.get(i);
            String fName = fundNames.get(i);
            String amfi = valuedSchemes.get(fId).getAmfiCode() != null ? valuedSchemes.get(fId).getAmfiCode() : "";
            BigDecimal allocWeight = weightsList.get(i);

            PortfolioFundRiskContributionDto c1Y = getFundContrib(win1Y, fId);
            PortfolioFundRiskContributionDto c3Y = getFundContrib(win3Y, fId);
            PortfolioFundRiskContributionDto c5Y = getFundContrib(win5Y, fId);

            BigDecimal pct1Y = c1Y != null ? c1Y.percentageRiskContribution() : null;
            BigDecimal pct3Y = c3Y != null ? c3Y.percentageRiskContribution() : null;
            BigDecimal pct5Y = c5Y != null ? c5Y.percentageRiskContribution() : null;

            BigDecimal delta1Y3Y = (pct1Y != null && pct3Y != null) ? pct1Y.subtract(pct3Y).setScale(2, RoundingMode.HALF_UP) : null;
            BigDecimal delta3Y5Y = (pct3Y != null && pct5Y != null) ? pct3Y.subtract(pct5Y).setScale(2, RoundingMode.HALF_UP) : null;

            String class1Y = c1Y != null ? c1Y.classification() : "UNAVAILABLE";
            String class3Y = c3Y != null ? c3Y.classification() : "UNAVAILABLE";
            String class5Y = c5Y != null ? c5Y.classification() : "UNAVAILABLE";

            boolean signFlipped = false;
            if (pct1Y != null && pct3Y != null) {
                if ((pct1Y.doubleValue() > 0.0001 && pct3Y.doubleValue() < -0.0001) || (pct1Y.doubleValue() < -0.0001 && pct3Y.doubleValue() > 0.0001)) {
                    signFlipped = true;
                }
            }

            boolean classShifted = !class1Y.equals("UNAVAILABLE") && !class3Y.equals("UNAVAILABLE")
                && !class1Y.equals("UNRELIABLE_ATTRIBUTION") && !class3Y.equals("UNRELIABLE_ATTRIBUTION")
                && !class1Y.equals("UNCLASSIFIED") && !class3Y.equals("UNCLASSIFIED")
                && !class1Y.equals(class3Y);

            String note;
            if (pct1Y != null && pct3Y != null) {
                note = String.format("Percentage risk contribution moved from %.1f%% (3Y) to %.1f%% (1Y) (delta: %+.1f%%). Classification: %s (1Y) vs %s (3Y). Descriptive candidate convention.",
                    pct3Y.doubleValue(), pct1Y.doubleValue(), delta1Y3Y.doubleValue(), class1Y, class3Y);
            } else {
                note = "Multi-year contribution sensitivity comparison unavailable.";
            }

            contribComparisons.add(new FundRiskContributionSensitivityDto(
                fId, fName, amfi, allocWeight, pct1Y, pct3Y, pct5Y, delta1Y3Y, delta3Y5Y,
                signFlipped, classShifted, class1Y, class3Y, class5Y, note
            ));
        }

        // 4. Diversification Sensitivity Comparison
        BigDecimal de1Y = win1Y.diversificationEffectPct();
        BigDecimal de3Y = win3Y.diversificationEffectPct();
        BigDecimal de5Y = win5Y.diversificationEffectPct();

        BigDecimal deltaDe1Y3Y = (win1Y.status().equals("AVAILABLE") && win3Y.status().equals("AVAILABLE"))
            ? de1Y.subtract(de3Y).setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal deltaDe3Y5Y = (win3Y.status().equals("AVAILABLE") && win5Y.status().equals("AVAILABLE"))
            ? de3Y.subtract(de5Y).setScale(2, RoundingMode.HALF_UP) : null;

        String divCompState = (win1Y.status().equals("AVAILABLE") && win3Y.status().equals("AVAILABLE")) ? "DIRECTLY_COMPARABLE" : "INSUFFICIENT_EVIDENCE";
        String divExplanation = String.format("Diversification cushion was %.2f%% in 1Y vs %.2f%% in 3Y (ratio: %.2fx vs %.2fx).",
            de1Y.doubleValue(), de3Y.doubleValue(), win1Y.diversificationRatio().doubleValue(), win3Y.diversificationRatio().doubleValue());

        DiversificationSensitivityComparisonDto divComparison = new DiversificationSensitivityComparisonDto(
            de1Y, de3Y, de5Y, win1Y.diversificationRatio(), win3Y.diversificationRatio(), win5Y.diversificationRatio(),
            deltaDe1Y3Y, deltaDe3Y5Y, divCompState, divExplanation
        );

        // 5. Downside Sensitivity Comparison
        String downCompState = (win1Y.status().equals("AVAILABLE") && win3Y.status().equals("AVAILABLE")) ? "DIRECTLY_COMPARABLE" : "INSUFFICIENT_EVIDENCE";
        String downExplanation = String.format("Historical max drawdown: %.2f%% (1Y, %d tail obs) vs %.2f%% (3Y, %d tail obs). Longer lookback windows capture deeper historical market cycles.",
            win1Y.maximumDrawdownPct().doubleValue(), win1Y.effectiveTailObservationCount(),
            win3Y.maximumDrawdownPct().doubleValue(), win3Y.effectiveTailObservationCount());

        DownsideSensitivityComparisonDto downsideComparison = new DownsideSensitivityComparisonDto(
            win1Y.maximumDrawdownPct(), win3Y.maximumDrawdownPct(), win5Y.maximumDrawdownPct(),
            win1Y.historicalVaR95Pct(), win3Y.historicalVaR95Pct(), win5Y.historicalVaR95Pct(),
            win1Y.historicalExpectedShortfall95Pct(), win3Y.historicalExpectedShortfall95Pct(), win5Y.historicalExpectedShortfall95Pct(),
            win1Y.effectiveTailObservationCount(), win3Y.effectiveTailObservationCount(), win5Y.effectiveTailObservationCount(),
            downCompState, downExplanation
        );

        // Overall Comparability State
        long availableCount = windowsList.stream().filter(w -> w.status().equals("AVAILABLE")).count();
        long limitedCount = windowsList.stream().filter(w -> w.status().equals("LIMITED_EVIDENCE")).count();
        String overallCompState = availableCount == 3 ? "DIRECTLY_COMPARABLE" : ((availableCount + limitedCount) >= 2 ? "CONDITIONALLY_COMPARABLE" : "INSUFFICIENT_EVIDENCE");

        List<String> limitations = new ArrayList<>();
        limitations.add("Observation-count based windows: 1Y (250 obs), 3Y (750 obs), 5Y (1,250 obs).");
        limitations.add("Comparability rule: Uses identical static capital weights and identical constituent fund universe across all windows.");
        limitations.add("Regime boundary: Historical lookback differences do not forecast future volatility or prove macroeconomic structural changes.");

        Map<String, String> sensNotes = new LinkedHashMap<>();
        sensNotes.put("ComparabilityRule", "Identical weights, identical fund universe, independent matrix suitability per window");
        sensNotes.put("VolatilitySensitivityFormula", "Delta_sigma = sigma_A - sigma_B; Relative = (sigma_A - sigma_B) / sigma_B * 100");
        sensNotes.put("MaterialShiftThresholds", "Volatility: >= 1.0% abs or >= 10.0% rel; Correlation: >= 0.15; Risk Contribution Ratio: > 1.15x");
        sensNotes.put("PolicyVersion", CLASSIFICATION_POLICY_VERSION);

        return new PortfolioLookbackSensitivityDto(
            overallCompState,
            fundNames,
            weightsList,
            windowsList,
            volComparisons,
            corrComparisons,
            contribComparisons,
            divComparison,
            downsideComparison,
            limitations,
            sensNotes
        );
    }

    private VolatilitySensitivityComparisonDto compareVolatility(
        String pairCode,
        PortfolioLookbackWindowResultDto winA,
        PortfolioLookbackWindowResultDto winB
    ) {
        boolean bothCalculable = (winA.status().equals("AVAILABLE") || winA.status().equals("LIMITED_EVIDENCE"))
            && (winB.status().equals("AVAILABLE") || winB.status().equals("LIMITED_EVIDENCE"))
            && winA.isPositiveSemiDefinite() && winB.isPositiveSemiDefinite();

        if (!bothCalculable) {
            String unavailReason = !winA.isPositiveSemiDefinite() || !winB.isPositiveSemiDefinite()
                ? "Comparison unavailable: Covariance matrix non-positive-semidefinite."
                : String.format("Comparison unavailable: %s lacks required observation history (%d / %d observations).",
                    !winA.status().equals("AVAILABLE") && !winA.status().equals("LIMITED_EVIDENCE") ? winA.windowCode() : winB.windowCode(),
                    !winA.status().equals("AVAILABLE") && !winA.status().equals("LIMITED_EVIDENCE") ? winA.actualObservationCount() : winB.actualObservationCount(),
                    !winA.status().equals("AVAILABLE") && !winA.status().equals("LIMITED_EVIDENCE") ? winA.requestedObservationCount() : winB.requestedObservationCount());

            return new VolatilitySensitivityComparisonDto(
                pairCode, winA.windowCode(), winB.windowCode(),
                winA.annualizedPortfolioVolatilityProxy(), winB.annualizedPortfolioVolatilityProxy(),
                null, null, "UNCLASSIFIED", "INSUFFICIENT_EVIDENCE",
                unavailReason
            );
        }

        BigDecimal volA = winA.annualizedPortfolioVolatilityProxy();
        BigDecimal volB = winB.annualizedPortfolioVolatilityProxy();
        BigDecimal absDiff = volA.subtract(volB).setScale(2, RoundingMode.HALF_UP);

        BigDecimal relChange;
        if (volB.compareTo(BigDecimal.ZERO) > 0) {
            relChange = absDiff.multiply(BigDecimal.valueOf(100)).divide(volB, 2, RoundingMode.HALF_UP);
        } else {
            relChange = BigDecimal.ZERO;
        }

        boolean isDirectlyComparable = winA.windowEvidenceState().equals("SUFFICIENT_EVIDENCE") && winB.windowEvidenceState().equals("SUFFICIENT_EVIDENCE");
        String compState = isDirectlyComparable ? "DIRECTLY_COMPARABLE" : "CONDITIONALLY_COMPARABLE";

        String classification = classifyVolatilitySensitivityShift(absDiff, relChange, true);

        String explanation = String.format("%s volatility (%.2f%%) is %+.2f%% (%+.1f%% relative) compared to %s volatility (%.2f%%). %s",
            winA.windowCode(), volA.doubleValue(), absDiff.doubleValue(), relChange.doubleValue(),
            winB.windowCode(), volB.doubleValue(),
            classification.equals("MATERIAL_DIFFERENCE_OBSERVED")
                ? "Material difference observed under Candidate Classification Policy V2 (>= 1.0% abs or >= 10.0% rel)."
                : "No material difference observed under Candidate Classification Policy V2.");

        return new VolatilitySensitivityComparisonDto(
            pairCode, winA.windowCode(), winB.windowCode(),
            volA, volB, absDiff, relChange, classification, compState, explanation
        );
    }

    private BigDecimal getPairCorrelation(PortfolioLookbackWindowResultDto win, int i, int j) {
        if (win.correlationMatrix() == null || i >= win.correlationMatrix().size() || j >= win.correlationMatrix().get(i).size()) {
            return null;
        }
        return win.correlationMatrix().get(i).get(j);
    }

    private PortfolioFundRiskContributionDto getFundContrib(PortfolioLookbackWindowResultDto win, Long schemeOptionId) {
        if (win.fundRiskContributions() == null) return null;
        return win.fundRiskContributions().stream()
            .filter(c -> c.schemeOptionId().equals(schemeOptionId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Annualized covariance matrix calculator using sample denominator N - 1.
     */
    private double[][] computeCovarianceMatrix(Map<Long, List<Double>> fundReturns, List<Long> fundIds, int N) {
        int M = fundIds.size();
        double[] meanR = new double[M];
        for (int i = 0; i < M; i++) {
            List<Double> rets = fundReturns.get(fundIds.get(i));
            double sum = 0.0;
            for (double r : rets) sum += r;
            meanR[i] = sum / N;
        }

        double[][] annCov = new double[M][M];
        for (int i = 0; i < M; i++) {
            List<Double> rI = fundReturns.get(fundIds.get(i));
            for (int j = i; j < M; j++) {
                List<Double> rJ = fundReturns.get(fundIds.get(j));
                double acc = 0.0;
                for (int t = 0; t < N; t++) {
                    acc += (rI.get(t) - meanR[i]) * (rJ.get(t) - meanR[j]);
                }
                double dailyCov = acc / (N - 1);
                double annualized = dailyCov * ANNUALIZATION_FACTOR;
                annCov[i][j] = annualized;
                annCov[j][i] = annualized;
            }
        }
        return annCov;
    }

    /**
     * Symmetric Jacobi eigenvalue algorithm for M x M matrix.
     */
    private double[] computeSymmetricEigenvalues(double[][] matrix) {
        int n = matrix.length;
        if (n == 0) return new double[0];
        if (n == 1) return new double[]{ matrix[0][0] };

        double[][] a = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, a[i], 0, n);
        }

        int maxIter = 100;
        double eps = 1e-12;

        for (int it = 0; it < maxIter; it++) {
            double maxVal = 0.0;
            int p = 0;
            int q = 1;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double absVal = Math.abs(a[i][j]);
                    if (absVal > maxVal) {
                        maxVal = absVal;
                        p = i;
                        q = j;
                    }
                }
            }

            if (maxVal < eps) {
                break;
            }

            double app = a[p][p];
            double aqq = a[q][q];
            double apq = a[p][q];

            double tau = (aqq - app) / (2.0 * apq);
            double t;
            if (tau >= 0) {
                t = 1.0 / (tau + Math.sqrt(1.0 + tau * tau));
            } else {
                t = -1.0 / (-tau + Math.sqrt(1.0 + tau * tau));
            }

            double c = 1.0 / Math.sqrt(1.0 + t * t);
            double s = t * c;

            a[p][p] = app - t * apq;
            a[q][q] = aqq + t * apq;
            a[p][q] = 0.0;
            a[q][p] = 0.0;

            for (int r = 0; r < n; r++) {
                if (r != p && r != q) {
                    double arp = a[r][p];
                    double arq = a[r][q];
                    a[r][p] = c * arp - s * arq;
                    a[p][r] = a[r][p];
                    a[r][q] = s * arp + c * arq;
                    a[q][r] = a[r][q];
                }
            }
        }

        double[] eigenvalues = new double[n];
        for (int i = 0; i < n; i++) {
            eigenvalues[i] = a[i][i];
        }
        Arrays.sort(eigenvalues);
        return eigenvalues;
    }

    private String getSchemeDisplayName(SchemeOption opt) {
        if (opt == null) return "Unknown Scheme";
        if (opt.getPlan() != null && opt.getPlan().getScheme() != null) {
            return opt.getPlan().getScheme().getName();
        }
        return "Scheme Option #" + opt.getId();
    }

    private PortfolioRiskDiversificationResponseDto buildEmptyResponse(
        String status,
        LocalDate asOf,
        OffsetDateTime cutoff,
        String reason,
        List<String> disclaimers,
        Map<String, String> notes,
        String runId,
        Map<String, String> registryMappings
    ) {
        PortfolioRiskSummaryDto summary = new PortfolioRiskSummaryDto(
            BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, "Total Known Portfolio Market Value",
            Collections.emptyList(), asOf, cutoff, null, null, 0,
            "UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE",
            0, 0, false, Collections.emptyMap(), reason,
            BigDecimal.ZERO, ANNUALIZATION_FACTOR, "252 Trading Days", "N - 1",
            BigDecimal.ZERO, false, "UNAVAILABLE", reason
        );
        return new PortfolioRiskDiversificationResponseDto(
            status, summary, null, Collections.emptyList(), null, null,
            Collections.emptyList(), disclaimers, notes,
            runId, EVIDENCE_POLICY_VERSION, NUMERICAL_TOLERANCE, registryMappings,
            null
        );
    }

    private PortfolioRiskDiversificationResponseDto buildInsufficientHistoryResponse(
        LocalDate asOf,
        OffsetDateTime cutoff,
        BigDecimal knownValue,
        int valuedCount,
        int unvaluedCount,
        BigDecimal coveragePct,
        List<UnvaluedPositionDto> unvaluedPositions,
        LocalDate commonStart,
        LocalDate commonEnd,
        int obsCount,
        int excludedObsCount,
        int calendarGapExclusionsCount,
        Map<String, Integer> perFundCounts,
        String message,
        List<String> disclaimers,
        Map<String, String> notes,
        String runId,
        Map<String, String> registryMappings
    ) {
        String alignmentLimitations = String.format(
            "Synchronous common dates required across all %d valued funds. %d calendar gap exclusions (> 7 days). %d dates with missing observations. %d unvalued positions.",
            valuedCount, calendarGapExclusionsCount, excludedObsCount, unvaluedCount
        );

        PortfolioRiskSummaryDto summary = new PortfolioRiskSummaryDto(
            knownValue.setScale(2, RoundingMode.HALF_UP), valuedCount, unvaluedCount, coveragePct,
            "Total Known Portfolio Market Value", unvaluedPositions, asOf, cutoff,
            commonStart, commonEnd, obsCount,
            "INSUFFICIENT_EVIDENCE", "INSUFFICIENT_EVIDENCE", "INSUFFICIENT_EVIDENCE",
            excludedObsCount, calendarGapExclusionsCount, true, perFundCounts, alignmentLimitations,
            BigDecimal.ZERO, ANNUALIZATION_FACTOR, "252 Trading Days", "N - 1",
            BigDecimal.ZERO, false, "INSUFFICIENT_EVIDENCE", message
        );

        List<RiskInvestigationFindingDto> findings = List.of(
            new RiskInvestigationFindingDto(
                "FINDING-INSUFFICIENT-OBSERVATIONS",
                "DATA_LIMITATION",
                "Insufficient Aligned Historical Observations",
                message,
                "A minimum of 60 synchronized trading observations across all portfolio funds is required for limited evidence (250 for sufficient evidence). Found: " + obsCount + ".",
                "Review fund inception dates and NAV ingestion completeness.",
                "Inspect historical NAV observation coverage"
            )
        );

        return new PortfolioRiskDiversificationResponseDto(
            "INSUFFICIENT_DATA", summary, null, Collections.emptyList(), null, null,
            findings, disclaimers, notes,
            runId, EVIDENCE_POLICY_VERSION, NUMERICAL_TOLERANCE, registryMappings,
            null
        );
    }

    // =========================================================================
    // CENTRALIZED REUSABLE DETERMINISTIC GOVERNANCE & POLICY METHODS (TASK 20)
    // =========================================================================

    /**
     * Authoritative observation window evidence evaluation (Policy V3).
     */
    public static String evaluateObservationWindowEvidenceState(int actualObs, int requestedObs) {
        if (actualObs <= 0) {
            return "UNAVAILABLE";
        }
        if (actualObs < MIN_OBS_WINDOW_LIMITED) {
            return "INSUFFICIENT_EVIDENCE";
        }
        if (actualObs >= requestedObs) {
            return "SUFFICIENT_EVIDENCE";
        }
        return "LIMITED_EVIDENCE";
    }

    /**
     * Authoritative Standalone Volatility (RSK-01) Evidence Evaluation.
     */
    public static String evaluateStandaloneVolatilityEvidenceState(int actualObs) {
        if (actualObs <= 0) return "UNAVAILABLE";
        if (actualObs >= MIN_OBS_WINDOW_SUFFICIENT) return "SUFFICIENT_EVIDENCE";
        if (actualObs >= MIN_OBS_WINDOW_LIMITED) return "LIMITED_EVIDENCE";
        return "INSUFFICIENT_EVIDENCE";
    }

    /**
     * Authoritative Drawdown (RSK-03) Evidence Evaluation.
     */
    public static String evaluateDrawdownEvidenceState(int actualObs) {
        if (actualObs <= 0) return "UNAVAILABLE";
        if (actualObs >= MIN_OBS_DRAWDOWN_SUFFICIENT) return "SUFFICIENT_EVIDENCE";
        if (actualObs >= MIN_OBS_DRAWDOWN_LIMITED) return "LIMITED_EVIDENCE";
        return "INSUFFICIENT_EVIDENCE";
    }

    /**
     * Authoritative Tail-Risk (RSK-06 / RSK-07) Evidence Evaluation (Task 20 Policy V3).
     * Sufficient: N >= 250 AND T >= 12
     * Limited: 100 <= N < 250 AND T >= 5
     * Insufficient: N < 100 OR T < 5
     * Unavailable: N == 0
     */
    public static String evaluateTailRiskEvidenceState(int N, int tailCount) {
        if (N <= 0 || tailCount <= 0) {
            return "UNAVAILABLE";
        }
        if (N >= MIN_OBS_TAIL_SUFFICIENT && tailCount >= MIN_TAIL_OBS_SUFFICIENT) {
            return "SUFFICIENT_EVIDENCE";
        }
        if (N >= MIN_OBS_TAIL_LIMITED && tailCount >= MIN_TAIL_OBS_LIMITED) {
            return "LIMITED_EVIDENCE";
        }
        return "INSUFFICIENT_EVIDENCE";
    }

    public record MatrixSuitabilityDecision(
        boolean isPositiveSemiDefinite,
        boolean isNearSingular,
        Double conditionNumber,
        String matrixSuitabilityState,
        String matrixEvidenceState,
        String explanation
    ) {}

    /**
     * Complete 6-Tier Covariance Matrix Suitability Hierarchy (Task 20 Policy V2).
     * Distinguishes:
     * 1. Non-finite or incomplete matrix
     * 2. Materially negative minimum eigenvalue (lambda_min < -1e-8)
     * 3. Singular / rank-deficient (lambda_min <= 1e-14, conditionNumber = null)
     * 4. Positive but near-zero minimum eigenvalue (1e-14 < lambda_min < 1e-6)
     * 5. Near-singular or ill-conditioned (lambda_min >= 1e-6, but kappa > 10,000 or N <= M)
     * 6. Well-conditioned positive-semidefinite (lambda_min >= 1e-6, kappa <= 10,000, N > M)
     */
    public static MatrixSuitabilityDecision evaluateMatrixSuitability(
        double minEigenvalue,
        double maxEigenvalue,
        int N,
        int M
    ) {
        // Tier 1: Non-finite or incomplete matrix
        if (Double.isNaN(minEigenvalue) || Double.isInfinite(minEigenvalue)
            || Double.isNaN(maxEigenvalue) || Double.isInfinite(maxEigenvalue)
            || N <= 0 || M <= 0) {
            return new MatrixSuitabilityDecision(
                false,
                true,
                null,
                "UNSUITABLE",
                "NUMERICALLY_INVALID",
                "Non-finite or incomplete covariance matrix: eigenvalues contain NaN or Infinite values, or invalid matrix dimensions."
            );
        }

        // Tier 2: Materially negative minimum eigenvalue (lambda_min < -1e-8)
        if (minEigenvalue < -PSD_EIGENVALUE_EPSILON) {
            return new MatrixSuitabilityDecision(
                false,
                true,
                null,
                "UNSUITABLE",
                "NUMERICALLY_INVALID",
                String.format("Non-positive-semidefinite covariance matrix: minimum eigenvalue (%.6e) is materially negative beyond numerical tolerance (-1e-8). Indicates numerical breakdown or unsynchronized data.", minEigenvalue)
            );
        }

        // Tier 3: Singular / rank-deficient (-1e-8 <= lambda_min <= 1e-14)
        if (minEigenvalue <= SINGULAR_EIGENVALUE_ZERO_TOLERANCE) {
            return new MatrixSuitabilityDecision(
                true,
                true,
                null, // Condition number is mathematically undefined/infinite for singular matrix
                "CONDITIONALLY_SUITABLE",
                "LIMITED_EVIDENCE",
                String.format("Singular covariance matrix: minimum eigenvalue (%.6e) is numerically zero (<= 1e-14). Exact rank deficiency / collinearity detected among constituent funds. Condition number is mathematically undefined (null).", minEigenvalue)
            );
        }

        // Condition number is computable since minEigenvalue > 1e-14
        double conditionNumber = maxEigenvalue / minEigenvalue;

        // Tier 4: Positive but near-zero minimum eigenvalue (1e-14 < lambda_min < 1e-6)
        if (minEigenvalue < NEAR_SINGULAR_EIGENVALUE_THRESHOLD) {
            return new MatrixSuitabilityDecision(
                true,
                true,
                conditionNumber,
                "CONDITIONALLY_SUITABLE",
                "LIMITED_EVIDENCE",
                String.format("Positive but near-zero minimum eigenvalue: minimum eigenvalue (%.6e) lies in the ill-conditioned transition zone (< 1e-6 boundary, condition number: %.2f). Matrix is positive semi-definite but attribution is numerically unstable.", minEigenvalue, conditionNumber)
            );
        }

        // Tier 5: Near-singular or ill-conditioned matrix (lambda_min >= 1e-6, but kappa > 10,000 or N <= M)
        if (conditionNumber > NEAR_SINGULAR_CONDITION_NUMBER_THRESHOLD || N <= M) {
            return new MatrixSuitabilityDecision(
                true,
                true,
                conditionNumber,
                "CONDITIONALLY_SUITABLE",
                "LIMITED_EVIDENCE",
                String.format("Near-singular covariance matrix: condition number (%.2f) exceeds threshold (%.0f) or observation count N (%d) is close to constituent count M (%d).",
                    conditionNumber, NEAR_SINGULAR_CONDITION_NUMBER_THRESHOLD, N, M)
            );
        }

        // Tier 6: Well-conditioned positive-semidefinite matrix
        String matrixEvidence = (N >= MIN_OBS_WINDOW_SUFFICIENT && N >= 5 * M)
            ? "SUFFICIENT_EVIDENCE"
            : "LIMITED_EVIDENCE";

        return new MatrixSuitabilityDecision(
            true,
            false,
            conditionNumber,
            "SUITABLE",
            matrixEvidence,
            "Covariance matrix is positive semi-definite and well-conditioned."
        );
    }

    public record RiskContributionClassification(
        BigDecimal contributionToWeightRatio,
        boolean isNegativeContribution,
        boolean isRiskDriver,
        boolean isDiversificationDampener,
        String classificationToken,
        String interpretation
    ) {}

    /**
     * Authoritative Candidate Risk Contribution Classification Policy V4 (Task 22).
     *
     * Mathematically complete, mutually exclusive classification hierarchy:
     * - 11 distinct classification tokens across 15 deterministic decision branches.
     * - ZERO_WEIGHT_POSITION: weightPct == 0.0 and |pctRc| <= 0.0001% (ratio undefined)
     * - NEGLIGIBLE_WEIGHT_POSITION: 0.0 < weightPct <= 0.0001% and |pctRc| <= 0.0001% (ratio omitted due to mathematical instability)
     * - ALLOCATION_CONTRIBUTION_MISMATCH: weightPct <= 0.0001% with |pctRc| > 0.0001% (data/calculation inconsistency; ratio omitted; never classified as risk driver)
     * - NEGATIVE_CONTRIBUTOR: weightPct > 0.0001% and pctRc < -0.0001% (historical sample variance dampener; ratio omitted)
     * - NEGLIGIBLE_NEGATIVE_CONTRIBUTION: weightPct > 0.0001% and -0.0001% <= pctRc < 0.0% (near-zero marginal covariance within resolution threshold; ratio omitted)
     * - BELOW_WEIGHT_CONTRIBUTION: weightPct > 0.0001%, pctRc >= 0.0%, and 0.0 <= ratio < 0.98
     * - PROPORTIONAL_CONTRIBUTION: weightPct > 0.0001%, pctRc >= 0.0%, and 0.98 <= ratio <= 1.02 (|ratio - 1.0| <= 2% parity tolerance)
     * - ABOVE_WEIGHT_CONTRIBUTION: weightPct > 0.0001%, pctRc >= 0.0%, and 1.02 < ratio <= 1.15
     * - RISK_DRIVER: weightPct > 0.0001%, pctRc >= 0.0%, and ratio > 1.15
     * - UNRELIABLE_ATTRIBUTION: ill-conditioned matrix, singular matrix, or Euler reconciliation failure
     * - UNCLASSIFIED: null, non-finite, or negative allocation weights
     */
    public static RiskContributionClassification classifyFundRiskContribution(
        Double pctRc,
        Double weightPct,
        boolean isReliable,
        boolean isEulerReconciled
    ) {
        // Step 1: Input Validity (Branches 1-4)
        if (pctRc == null) {
            return new RiskContributionClassification(
                null, false, false, false, "UNCLASSIFIED",
                "Risk contribution could not be classified: percentage risk contribution is null."
            );
        }
        if (weightPct == null) {
            return new RiskContributionClassification(
                null, false, false, false, "UNCLASSIFIED",
                "Risk contribution could not be classified: allocation weight is null."
            );
        }
        if (Double.isNaN(pctRc) || Double.isInfinite(pctRc) || Double.isNaN(weightPct) || Double.isInfinite(weightPct)) {
            return new RiskContributionClassification(
                null, false, false, false, "UNCLASSIFIED",
                "Risk contribution could not be classified due to non-finite calculation values."
            );
        }
        if (weightPct < 0.0) {
            return new RiskContributionClassification(
                null, false, false, false, "UNCLASSIFIED",
                String.format(Locale.US, "Risk contribution could not be classified: negative allocation weight (%.2f%%) is invalid for a long-only portfolio.", weightPct)
            );
        }

        // Step 2: Attribution Reliability & Euler Identity Reconciliation (Branch 5)
        if (!isReliable || !isEulerReconciled) {
            String unreliabilityReason = !isEulerReconciled
                ? "Attribution unreliable due to Euler reconciliation discrepancy exceeding numerical tolerance."
                : "Attribution unreliable due to ill-conditioned covariance matrix, near-zero portfolio volatility, or limited historical observations.";
            return new RiskContributionClassification(
                null,
                pctRc < 0.0,
                false,
                false,
                "UNRELIABLE_ATTRIBUTION",
                unreliabilityReason
            );
        }

        // Step 3: Zero and Near-Zero Capital Allocation Handling (Branches 6-9)
        if (weightPct <= ALLOCATION_WEIGHT_EPSILON) {
            if (weightPct == 0.0) {
                if (Math.abs(pctRc) <= CONTRIBUTION_RESOLUTION_EPSILON) {
                    // Branch 6: Zero weight and zero contribution
                    return new RiskContributionClassification(
                        null, false, false, false, "ZERO_WEIGHT_POSITION",
                        "Zero-weight position: Holding has zero capital allocation (0.00%) and zero risk contribution (%RC = 0.00%). Ratio is mathematically undefined."
                    );
                } else {
                    // Branch 7: Zero weight with non-zero contribution -> Mismatch
                    return new RiskContributionClassification(
                        null, pctRc < 0.0, false, false, "ALLOCATION_CONTRIBUTION_MISMATCH",
                        String.format(Locale.US, "Allocation-contribution mismatch: Holding has zero capital allocation (0.00%%) but non-zero risk contribution (%%RC = %.4f%%). Indicates an allocation-attribution inconsistency or unvalued position leakage under Policy V4. Excluded from ratio-based risk driver classification.", pctRc)
                    );
                }
            } else {
                // 0.0 < weightPct <= ALLOCATION_WEIGHT_EPSILON
                if (Math.abs(pctRc) <= CONTRIBUTION_RESOLUTION_EPSILON) {
                    // Branch 8: Near-zero weight and near-zero contribution
                    return new RiskContributionClassification(
                        null, false, false, false, "NEGLIGIBLE_WEIGHT_POSITION",
                        String.format(Locale.US, "Near-zero allocation position: Holding has negligible capital allocation (%.4f%% <= 0.0001%%) and negligible risk contribution (%%RC = %.4f%%). Ratio is omitted due to numerical instability of near-zero division.", weightPct, pctRc)
                    );
                } else {
                    // Branch 9: Near-zero weight with non-negligible contribution -> Mismatch
                    return new RiskContributionClassification(
                        null, pctRc < 0.0, false, false, "ALLOCATION_CONTRIBUTION_MISMATCH",
                        String.format(Locale.US, "Allocation-contribution mismatch: Holding has near-zero capital allocation (%.4f%% <= 0.0001%%) but non-negligible risk contribution (%%RC = %.4f%%). Indicates an allocation-attribution inconsistency under Policy V4. Ratio is undefined and excluded from risk driver classification.", weightPct, pctRc)
                    );
                }
            }
        }

        // At this point, weightPct > ALLOCATION_WEIGHT_EPSILON (> 0.0001%) deterministically.

        // Step 4: Valid Positive-Weight Allocation with Negative Contributions (Branches 10-11)
        if (pctRc < -CONTRIBUTION_RESOLUTION_EPSILON) {
            // Branch 10: Economically meaningful negative contribution
            return new RiskContributionClassification(
                null,
                true,
                false,
                false,
                "NEGATIVE_CONTRIBUTOR",
                String.format(Locale.US, "Negative historical contribution: Component risk contribution is negative (%%RC = %.2f%%). Historical sample covariance reduced portfolio variance at the margin over the observed window under Policy V4. Descriptive empirical sample property, NOT a guaranteed future hedge or capital preservation benefit.", pctRc)
            );
        }

        if (pctRc < 0.0) {
            // Branch 11: Negligible negative contribution (-0.0001% <= pctRc < 0.0%)
            return new RiskContributionClassification(
                null,
                true,
                false,
                false,
                "NEGLIGIBLE_NEGATIVE_CONTRIBUTION",
                String.format(Locale.US, "Negligible negative contribution: Component risk contribution is marginally negative (%%RC = %.4f%%), falling within reporting resolution threshold (±0.0001%%). Reflects near-zero marginal covariance with the portfolio over the sample window. The platform does not estimate parameter standard errors; small magnitude must not be assumed to be computational noise or guaranteed diversification.", pctRc)
            );
        }

        // Step 5: Valid Positive-Weight Allocation with Non-Negative Contributions and Ratio Evaluation (Branches 12-15)
        double ratio = pctRc / weightPct;
        BigDecimal ratioBd = BigDecimal.valueOf(ratio).setScale(4, RoundingMode.HALF_UP);

        // Branch 12: Proportional contribution (0.98 <= ratio <= 1.02)
        if (Math.abs(ratio - 1.0) <= PROPORTIONAL_CONTRIBUTION_TOLERANCE) {
            return new RiskContributionClassification(
                ratioBd,
                false,
                false,
                false,
                "PROPORTIONAL_CONTRIBUTION",
                String.format(Locale.US, "Proportional to allocation: Fund contributes approximately proportionately to portfolio volatility (%%RC = %.2f%%) relative to its capital allocation (%.2f%%, ratio: %.2fx within ±2%% parity tolerance). Candidate descriptive classification under Policy V4.", pctRc, weightPct, ratio)
            );
        }

        // Branch 13: Below-weight contribution (0.0 <= ratio < 0.98)
        if (ratio < 1.0 - PROPORTIONAL_CONTRIBUTION_TOLERANCE) {
            return new RiskContributionClassification(
                ratioBd,
                false,
                false,
                false,
                "BELOW_WEIGHT_CONTRIBUTION",
                String.format(Locale.US, "Below allocation-weight contribution: Fund's share of portfolio volatility (%%RC = %.2f%%) is lower than its capital allocation share (%.2f%%, ratio: %.2fx). Does not automatically imply the fund reduces absolute portfolio risk. Candidate descriptive classification under Policy V4.", pctRc, weightPct, ratio)
            );
        }

        // Branch 14: Above-weight contribution (1.02 < ratio <= 1.15)
        if (ratio <= RISK_CONTRIBUTION_RATIO_DRIVER_THRESHOLD) {
            return new RiskContributionClassification(
                ratioBd,
                false,
                false,
                false,
                "ABOVE_WEIGHT_CONTRIBUTION",
                String.format(Locale.US, "Above allocation-weight contribution: Fund contributes more to portfolio volatility (%%RC = %.2f%%) than its capital allocation share (%.2f%%, ratio: %.2fx), but does not cross the 1.15x candidate risk-driver threshold. Candidate descriptive classification under Policy V4.", pctRc, weightPct, ratio)
            );
        }

        // Branch 15: Risk driver (ratio > 1.15)
        return new RiskContributionClassification(
            ratioBd,
            false,
            true,
            false,
            "RISK_DRIVER",
            String.format(Locale.US, "Disproportionate risk contributor: Fund contributes disproportionately more portfolio volatility (%%RC = %.2f%%) than its capital allocation share (%.2f%%, ratio: %.2fx > 1.15x threshold). Candidate descriptive classification under Policy V4.", pctRc, weightPct, ratio)
        );
    }

    public static RiskContributionClassification classifyFundRiskContribution(
        double pctRc,
        double weightPct,
        boolean isReliable
    ) {
        return classifyFundRiskContribution(Double.valueOf(pctRc), Double.valueOf(weightPct), isReliable, true);
    }

    /**
     * Authoritative Candidate Volatility Sensitivity Classification Policy V2.
     */
    public static String classifyVolatilitySensitivityShift(
        BigDecimal absDiff,
        BigDecimal relChange,
        boolean isComparable
    ) {
        if (!isComparable || absDiff == null) {
            return "UNCLASSIFIED";
        }
        double diffVal = Math.abs(absDiff.doubleValue());
        double relVal = relChange != null ? Math.abs(relChange.doubleValue()) : 0.0;

        if (diffVal >= VOLATILITY_MATERIAL_SHIFT_PCT || relVal >= VOLATILITY_RELATIVE_SHIFT_PCT) {
            return "MATERIAL_DIFFERENCE_OBSERVED";
        }
        return "NO_MATERIAL_DIFFERENCE_UNDER_CANDIDATE_RULE";
    }

    public record TailRiskResult(
        double var95LossPct,
        double es95LossPct,
        int tailCount,
        String varEvidenceState,
        String esEvidenceState
    ) {}

    /**
     * Reconciled Tail-Risk Order Statistic & Expected Shortfall Calculation (Policy V3).
     * Enforces Task 20 rule: Sufficient requires N >= 250 AND T >= 12.
     * Selects exactly T observations from lower tail (indices 0 to T - 1); ES is exact mean of T losses.
     */
    public static TailRiskResult computeTailRisk(List<Double> returns) {
        int N = returns.size();
        if (N == 0) {
            return new TailRiskResult(0.0, 0.0, 0, "UNAVAILABLE", "UNAVAILABLE");
        }

        int tailCount = (int) Math.floor(0.05 * N);
        String evidenceState = evaluateTailRiskEvidenceState(N, tailCount);

        if (tailCount < 1) {
            return new TailRiskResult(0.0, 0.0, 0, evidenceState, evidenceState);
        }

        List<Double> sortedRets = new ArrayList<>(returns);
        Collections.sort(sortedRets);

        // Empirical 5th percentile order statistic at boundary of tailCount observations (index tailCount - 1)
        double varRet = sortedRets.get(tailCount - 1);
        double varLoss = Math.max(0.0, -varRet * 100.0);

        // Expected Shortfall: exact arithmetic mean of the tailCount worst losses
        double sumLoss = 0.0;
        for (int i = 0; i < tailCount; i++) {
            sumLoss += Math.max(0.0, -sortedRets.get(i) * 100.0);
        }
        double esLoss = sumLoss / tailCount;

        return new TailRiskResult(varLoss, esLoss, tailCount, evidenceState, evidenceState);
    }

    /**
     * Exports a structured, auditable historical stress evidence record for an investor portfolio (Task 58).
     */
    @Transactional(readOnly = true)
    public PortfolioStressEvidenceRecordDto exportHistoricalStressEvidence(String auth0Subject, PortfolioRiskDiversificationRequestDto request) {
        PortfolioRiskDiversificationResponseDto riskResp = calculatePortfolioRiskDiversification(auth0Subject, request);
        if (riskResp == null || riskResp.historicalStressAnalysis() == null) {
            throw new IllegalStateException("Historical stress analysis could not be evaluated for evidence export.");
        }
        PortfolioStressAnalysisResponseDto stressResp = riskResp.historicalStressAnalysis();
        String recordId = "STRESS-EVIDENCE-" + stressResp.calculationRunId();

        List<RiskInvestigationFindingDto> attachedFindings = riskResp.investigationFindings() != null
            ? riskResp.investigationFindings().stream()
                .filter(f -> f.findingId() != null && f.findingId().contains("STRESS"))
                .toList()
            : Collections.emptyList();

        return new PortfolioStressEvidenceRecordDto(
            recordId,
            auth0Subject,
            stressResp.allocationSnapshot(),
            stressResp.calculationContext(),
            stressResp.benchmarkReference(),
            stressResp.summary(),
            stressResp.episodes(),
            attachedFindings,
            stressResp.unvaluedPositions(),
            stressResp.knownValueCoveragePct(),
            stressResp.governanceDisclaimers(),
            stressResp.methodologyNotes(),
            OffsetDateTime.now(),
            "Official YUKIRA Historical Stress Evidence Record. Deterministic static current-allocation proxy under candidate methodology. Non-advisory verification checkpoint."
        );
    }
}
