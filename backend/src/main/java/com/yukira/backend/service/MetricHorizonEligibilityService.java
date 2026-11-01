package com.yukira.backend.service;

import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.analysis.MetricHorizonEligibilityDto;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.universe.AnalyticalHorizon;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Metric Horizon Eligibility Service (Phase B1).
 *
 * Epistemic & Quantitative Rules:
 * 1. Horizon support is determined strictly per metric, not by a single global
 * flag.
 * 2. Cleanly distinguishes IMPLEMENTED, VALIDATED, APPROVED, and UNSUPPORTED.
 * 3. Preserves frozen Phase 2H 3Y calculation behavior:
 * - RET-02, RET-03, RSK-01 are APPROVED baseline metrics under 3Y.
 * - RSK-02..07, MKT-01..05, REL-01 are CANDIDATE under 3Y, 5Y, 10Y.
 * - 5Y and 10Y calculations for return and risk metrics are CANDIDATE (not
 * APPROVED).
 * - Risk-adjusted metrics (RAT-01..04, REL-02) for 5Y and 10Y are UNSUPPORTED /
 * RESTRICTED
 * because multi-year risk-free daily compounding and term structure alignments
 * have
 * NOT been validated or authorized under frozen Phase 2H governance.
 * 4. Separates calculation support from historical data availability.
 */
@Service
public class MetricHorizonEligibilityService {

    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;

    public MetricHorizonEligibilityService(
            SchemeOptionRepository schemeOptionRepository,
            NavObservationRepository navObservationRepository) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
    }

    /**
     * Retrieves the metric-level horizon eligibility specification for a given
     * metric code and horizon.
     */
    public MetricHorizonEligibilityDto getEligibility(String metricCode, AnalyticalHorizon horizon) {
        if (metricCode == null || horizon == null) {
            throw new IllegalArgumentException("metricCode and horizon must not be null");
        }
        String code = metricCode.trim().toUpperCase();

        return switch (code) {
            // RETURN FAMILY
            case "RET-02" -> horizon == AnalyticalHorizon.THREE_YEAR
                    ? MetricHorizonEligibilityDto.supported(
                            "RET-02", "Simple Period Return", "RETURN_BENCHMARK", horizon,
                            "OPERATIONAL BASELINE", "PHASE_2H_FROZEN",
                            List.of("FUND_NAV"), "BOUNDARY_PAIR_4D_LOOKBACK",
                            "(NAV_end - NAV_start) / NAV_start",
                            "Discrete period return across boundary dates; unannualized.")
                    : MetricHorizonEligibilityDto.supported(
                            "RET-02", "Simple Period Return (" + horizon.getCode() + ")", "RETURN_BENCHMARK", horizon,
                            "CANDIDATE", "YUKIRA_V1_CANDIDATE_" + horizon.getCode(),
                            List.of("FUND_NAV"), "BOUNDARY_PAIR_4D_LOOKBACK",
                            "(NAV_end - NAV_start) / NAV_start",
                            "Point-to-point discrete percentage return across " + horizon.getYears()
                                    + "-year window; candidate methodology.");

            case "RET-03" -> horizon == AnalyticalHorizon.THREE_YEAR
                    ? MetricHorizonEligibilityDto.supported(
                            "RET-03", "Compound Annual Growth Rate (3Y CAGR)", "RETURN_BENCHMARK", horizon,
                            "CANDIDATE", "PHASE_2H_FROZEN",
                            List.of("FUND_NAV"), "FULL_SERIES_JULIAN_365_25",
                            "(NAV_end / NAV_start)^(365.25 / calendar_days) - 1.0",
                            "3-year compound annual growth rate normalized over leap-adjusted Julian trading years; requires >= 700 trading days.")
                    : MetricHorizonEligibilityDto.supported(
                            "RET-03", "Compound Annual Growth Rate (" + horizon.getCode() + " CAGR)",
                            "RETURN_BENCHMARK", horizon,
                            "CANDIDATE", "YUKIRA_V1_CANDIDATE_" + horizon.getCode() + "_CAGR",
                            List.of("FUND_NAV"), "FULL_SERIES_JULIAN_365_25",
                            "(NAV_end / NAV_start)^(365.25 / calendar_days) - 1.0",
                            horizon.getYears() + "-year CAGR under candidate Julian 365.25 convention; requires >= "
                                    + horizon.getDefaultThreshold() + " trading days. Candidate methodology.");

            case "RET-07" -> MetricHorizonEligibilityDto.supported(
                    "RET-07", horizon.getCode() + " Annualized Active Return", "RETURN_BENCHMARK", horizon,
                    "CANDIDATE", "YUKIRA_V1_CANDIDATE_ACTIVE_RETURN",
                    List.of("FUND_NAV", "BENCHMARK_TRI"), "SYNCHRONOUS_CALENDAR_DATES",
                    "Fund CAGR - Benchmark CAGR",
                    "Excess annualized growth rate relative to benchmark across " + horizon.getCode()
                            + " window. Unadjusted for market beta.");

            // ABSOLUTE RISK FAMILY
            case "RSK-01" -> horizon == AnalyticalHorizon.THREE_YEAR
                    ? MetricHorizonEligibilityDto.supported(
                            "RSK-01", "3-Year Annualized Volatility", "RISK_TAIL", horizon,
                            "CANDIDATE", "PHASE_2H_FROZEN",
                            List.of("FUND_NAV"), "FULL_SERIES_DAILY_RETURNS",
                            "Sample_Stdev(daily_returns) * sqrt(252)",
                            "3-year annualized sample standard deviation of daily returns; requires >= 700 trading days.")
                    : MetricHorizonEligibilityDto.supported(
                            "RSK-01", horizon.getCode() + " Annualized Volatility", "RISK_TAIL", horizon,
                            "CANDIDATE", "YUKIRA_V1_CANDIDATE_" + horizon.getCode() + "_VOLATILITY",
                            List.of("FUND_NAV"), "FULL_SERIES_DAILY_RETURNS",
                            "Sample_Stdev(daily_returns) * sqrt(252)",
                            horizon.getYears()
                                    + "-year annualized daily return dispersion; candidate annualization sqrt(252). Requires >= "
                                    + horizon.getDefaultThreshold() + " trading days.");

            case "RSK-02" -> MetricHorizonEligibilityDto.supported(
                    "RSK-02", "Downside Semideviation (" + horizon.getCode() + ")", "RISK_TAIL", horizon,
                    "CANDIDATE", "YUKIRA_V1_CANDIDATE_DOWNSIDE_DEV",
                    List.of("FUND_NAV"), "FULL_SERIES_DAILY_RETURNS",
                    "sqrt( sum( min(0, R_t - MAR)^2 ) / (N - 1) ) * sqrt(252)",
                    "Annualized downside dispersion below MAR (0.0%) across " + horizon.getCode() + " horizon.");

            case "RSK-03" -> MetricHorizonEligibilityDto.supported(
                    "RSK-03", "Maximum Drawdown (" + horizon.getCode() + ")", "RISK_TAIL", horizon,
                    "CANDIDATE", "YUKIRA_V1_CANDIDATE_MAX_DRAWDOWN",
                    List.of("FUND_NAV"), "FULL_SERIES_RUNNING_PEAK",
                    "min_t ( (NAV_t - Running_Peak_NAV_t) / Running_Peak_NAV_t )",
                    "Largest percentage peak-to-trough drop across " + horizon.getCode() + " horizon.");

            case "RSK-04" -> MetricHorizonEligibilityDto.supported(
                    "RSK-04", "Maximum Drawdown Duration (" + horizon.getCode() + ")", "RISK_TAIL", horizon,
                    "CANDIDATE", "YUKIRA_V1_CANDIDATE_DRAWDOWN_DURATION",
                    List.of("FUND_NAV"), "FULL_SERIES_RUNNING_PEAK",
                    "Max calendar days elapsed between peak NAV and complete recovery to peak level",
                    "Longest calendar period underwater during " + horizon.getCode() + " horizon.");

            case "RSK-05" -> MetricHorizonEligibilityDto.supported(
                    "RSK-05", "Ulcer Index (" + horizon.getCode() + ")", "RISK_TAIL", horizon,
                    "CANDIDATE", "YUKIRA_V1_CANDIDATE_ULCER_INDEX",
                    List.of("FUND_NAV"), "FULL_SERIES_RUNNING_PEAK",
                    "sqrt( sum( Drawdown_t^2 ) / N )",
                    "Quadratic drawdown holding stress measure across " + horizon.getCode() + " horizon.");

            case "RSK-06" -> MetricHorizonEligibilityDto.supported(
                    "RSK-06", "Historical VaR 95% (" + horizon.getCode() + ")", "RISK_TAIL", horizon,
                    "CANDIDATE", "YUKIRA_V1_CANDIDATE_VAR95",
                    List.of("FUND_NAV"), "FULL_SERIES_DAILY_RETURNS",
                    "5th percentile of daily return distribution (1-day horizon)",
                    "Empirical 95% loss quantile over " + horizon.getCode() + " horizon.");

            case "RSK-07" -> MetricHorizonEligibilityDto.supported(
                    "RSK-07", "Expected Shortfall 95% (" + horizon.getCode() + ")", "RISK_TAIL", horizon,
                    "CANDIDATE", "YUKIRA_V1_CANDIDATE_CVAR95",
                    List.of("FUND_NAV"), "FULL_SERIES_DAILY_RETURNS",
                    "Mean of daily returns strictly worse than 95% VaR threshold",
                    "Conditional average daily loss on worst 5% trading days over " + horizon.getCode() + " horizon.");

            // RELATIVE / BENCHMARK-RELATIVE FAMILY
            case "MKT-01" -> MetricHorizonEligibilityDto.supported(
                    "MKT-01", "Beta (" + horizon.getCode() + ")", "MARKET_SENSITIVITY_ALPHA", horizon,
                    "CANDIDATE", "YUKIRA_V1_CANDIDATE_BETA",
                    List.of("FUND_NAV", "BENCHMARK_TRI"), "SYNCHRONOUS_CALENDAR_DATES",
                    "Cov(R_p - R_f, R_b - R_f) / Var(R_b - R_f)",
                    "Linear CAPM slope versus benchmark over " + horizon.getCode()
                            + " horizon. Requires synchronized benchmark history.");

            case "MKT-02" -> MetricHorizonEligibilityDto.supported(
                    "MKT-02", "Downside Beta (" + horizon.getCode() + ")", "MARKET_SENSITIVITY_ALPHA", horizon,
                    "CANDIDATE", "YUKIRA_V1_CANDIDATE_DOWNSIDE_BETA",
                    List.of("FUND_NAV", "BENCHMARK_TRI"), "SYNCHRONOUS_BENCHMARK_DOWN_DAYS",
                    "Cov(R_p, R_b | R_b < 0) / Var(R_b | R_b < 0)",
                    "Portfolio co-movement conditioned on down-market days over " + horizon.getCode()
                            + " horizon. Requires at least 100 benchmark down days.");

            case "MKT-03" -> MetricHorizonEligibilityDto.supported(
                    "MKT-03", "Upside Capture Ratio (" + horizon.getCode() + ")", "MARKET_SENSITIVITY_ALPHA", horizon,
                    "CANDIDATE", "YUKIRA_V1_CANDIDATE_CAPTURE",
                    List.of("FUND_NAV", "BENCHMARK_TRI"), "SYNCHRONOUS_CALENDAR_DATES",
                    "Prod(1 + R_p | R_b > 0) / Prod(1 + R_b | R_b > 0)",
                    "Compounded return participation during positive benchmark days across " + horizon.getCode()
                            + " horizon.");

            case "MKT-04" -> MetricHorizonEligibilityDto.supported(
                    "MKT-04", "Downside Capture Ratio (" + horizon.getCode() + ")", "MARKET_SENSITIVITY_ALPHA", horizon,
                    "CANDIDATE", "YUKIRA_V1_CANDIDATE_CAPTURE",
                    List.of("FUND_NAV", "BENCHMARK_TRI"), "SYNCHRONOUS_CALENDAR_DATES",
                    "Prod(1 + R_p | R_b < 0) / Prod(1 + R_b | R_b < 0)",
                    "Compounded return participation during negative benchmark days across " + horizon.getCode()
                            + " horizon.");

            case "MKT-05" -> MetricHorizonEligibilityDto.supported(
                    "MKT-05", "Capture Spread (" + horizon.getCode() + ")", "MARKET_SENSITIVITY_ALPHA", horizon,
                    "CANDIDATE", "YUKIRA_V1_CANDIDATE_CAPTURE_SPREAD",
                    List.of("FUND_NAV", "BENCHMARK_TRI"), "SYNCHRONOUS_CALENDAR_DATES",
                    "Upside Capture - Downside Capture",
                    "Asymmetric market participation spread across " + horizon.getCode() + " horizon.");

            case "REL-01" -> MetricHorizonEligibilityDto.supported(
                    "REL-01", "Tracking Error (" + horizon.getCode() + ")", "BENCHMARK_ALPHA", horizon,
                    "CANDIDATE", "YUKIRA_V1_CANDIDATE_TRACKING_ERROR",
                    List.of("FUND_NAV", "BENCHMARK_TRI"), "SYNCHRONOUS_CALENDAR_DATES",
                    "Stdev(R_p - R_b) * sqrt(252)",
                    "Annualized volatility of daily active returns versus benchmark across " + horizon.getCode()
                            + " horizon.");

            // RISK-ADJUSTED FAMILY (Strict governance rule: 5Y and 10Y are UNSUPPORTED /
            // RESTRICTED)
            case "RAT-01" -> horizon == AnalyticalHorizon.THREE_YEAR
                    ? MetricHorizonEligibilityDto.supported(
                            "RAT-01", "Sharpe Ratio (3Y)", "RISK_ADJUSTED", horizon,
                            "CANDIDATE", "PHASE_2H_CANDIDATE_M2N",
                            List.of("FUND_NAV", "FBIL_91D_TBILL"), "DAILY_EXCESS_RETURN_ALIGNMENT",
                            "(Mean(R_p - R_f) * 252) / (Stdev(R_p) * sqrt(252))",
                            "3-year risk-adjusted excess return per unit of volatility relative to FBIL 91-Day T-Bill.")
                    : MetricHorizonEligibilityDto.unsupported(
                            "RAT-01", "Sharpe Ratio (" + horizon.getCode() + ")", "RISK_ADJUSTED", horizon,
                            "RESTRICTED_PHASE_2H",
                            List.of("FUND_NAV", "FBIL_91D_TBILL"), "DAILY_EXCESS_RETURN_ALIGNMENT",
                            "(Mean(R_p - R_f) * 252) / (Stdev(R_p) * sqrt(252))",
                            "Multi-year (" + horizon.getCode()
                                    + ") risk-adjusted calculations require multi-year risk-free daily compounding and term structure alignments not authorized under Phase 2H governance.",
                            "MULTI_YEAR_RISK_FREE_ALIGNMENT_UNAUTHORIZED");

            case "RAT-02" -> horizon == AnalyticalHorizon.THREE_YEAR
                    ? MetricHorizonEligibilityDto.supported(
                            "RAT-02", "Sortino Ratio (3Y)", "RISK_ADJUSTED", horizon,
                            "CANDIDATE", "PHASE_2H_CANDIDATE_M2N",
                            List.of("FUND_NAV"), "FULL_SERIES_DAILY_RETURNS",
                            "(Annualized Return - MAR) / Downside Semideviation",
                            "Excess return per unit of downside semideviation across 3-year horizon.")
                    : MetricHorizonEligibilityDto.unsupported(
                            "RAT-02", "Sortino Ratio (" + horizon.getCode() + ")", "RISK_ADJUSTED", horizon,
                            "RESTRICTED_PHASE_2H",
                            List.of("FUND_NAV"), "FULL_SERIES_DAILY_RETURNS",
                            "(Annualized Return - MAR) / Downside Semideviation",
                            "Multi-year (" + horizon.getCode()
                                    + ") Sortino ratio requires multi-year MAR term adjustment not authorized under Phase 2H governance.",
                            "MULTI_YEAR_MAR_ALIGNMENT_UNAUTHORIZED");

            case "RAT-03" -> horizon == AnalyticalHorizon.THREE_YEAR
                    ? MetricHorizonEligibilityDto.supported(
                            "RAT-03", "Treynor Ratio (3Y)", "RISK_ADJUSTED", horizon,
                            "CANDIDATE", "PHASE_2H_CANDIDATE_M2N",
                            List.of("FUND_NAV", "BENCHMARK_TRI", "FBIL_91D_TBILL"), "SYNCHRONOUS_EXCESS_RETURNS",
                            "(Mean(R_p - R_f) * 252) / Beta_p",
                            "Annualized excess return per unit of systematic Beta across 3-year horizon.")
                    : MetricHorizonEligibilityDto.unsupported(
                            "RAT-03", "Treynor Ratio (" + horizon.getCode() + ")", "RISK_ADJUSTED", horizon,
                            "RESTRICTED_PHASE_2H",
                            List.of("FUND_NAV", "BENCHMARK_TRI", "FBIL_91D_TBILL"), "SYNCHRONOUS_EXCESS_RETURNS",
                            "(Mean(R_p - R_f) * 252) / Beta_p",
                            "Multi-year (" + horizon.getCode()
                                    + ") Treynor calculation requires multi-year risk-free compounding not authorized under Phase 2H governance.",
                            "MULTI_YEAR_RISK_FREE_ALIGNMENT_UNAUTHORIZED");

            case "RAT-04" -> horizon == AnalyticalHorizon.THREE_YEAR
                    ? MetricHorizonEligibilityDto.supported(
                            "RAT-04", "Information Ratio (3Y)", "RISK_ADJUSTED", horizon,
                            "CANDIDATE", "PHASE_2H_CANDIDATE_M2N",
                            List.of("FUND_NAV", "BENCHMARK_TRI"), "SYNCHRONOUS_ACTIVE_RETURNS",
                            "Annualized Active Return / Annualized Tracking Error",
                            "Active return earned per unit of benchmark tracking risk over 3-year horizon.")
                    : MetricHorizonEligibilityDto.unsupported(
                            "RAT-04", "Information Ratio (" + horizon.getCode() + ")", "RISK_ADJUSTED", horizon,
                            "RESTRICTED_PHASE_2H",
                            List.of("FUND_NAV", "BENCHMARK_TRI"), "SYNCHRONOUS_ACTIVE_RETURNS",
                            "Annualized Active Return / Annualized Tracking Error",
                            "Multi-year (" + horizon.getCode()
                                    + ") Information Ratio requires multi-cycle benchmark synchronization validation not yet completed.",
                            "MULTI_YEAR_BENCHMARK_SYNC_UNVALIDATED");

            case "REL-02" -> horizon == AnalyticalHorizon.THREE_YEAR
                    ? MetricHorizonEligibilityDto.supported(
                            "REL-02", "Jensen's Alpha (3Y)", "BENCHMARK_ALPHA", horizon,
                            "CANDIDATE", "PHASE_2H_CANDIDATE_M2N",
                            List.of("FUND_NAV", "BENCHMARK_TRI", "FBIL_91D_TBILL"), "SYNCHRONOUS_EXCESS_RETURNS",
                            "OLS intercept of (R_p - R_f) on (R_b - R_f), annualized x252",
                            "CAPM residual alpha after accounting for beta and risk-free return over 3-year horizon.")
                    : MetricHorizonEligibilityDto.unsupported(
                            "REL-02", "Jensen's Alpha (" + horizon.getCode() + ")", "BENCHMARK_ALPHA", horizon,
                            "RESTRICTED_PHASE_2H",
                            List.of("FUND_NAV", "BENCHMARK_TRI", "FBIL_91D_TBILL"), "SYNCHRONOUS_EXCESS_RETURNS",
                            "OLS intercept of (R_p - R_f) on (R_b - R_f), annualized x252",
                            "Multi-year (" + horizon.getCode()
                                    + ") Jensen's Alpha requires multi-year risk-free compounding and multi-cycle CAPM stationarity validation not authorized under Phase 2H.",
                            "MULTI_YEAR_RISK_FREE_ALIGNMENT_UNAUTHORIZED");

            case "REL-03" -> MetricHorizonEligibilityDto.supported(
                    "REL-03", "Annualized Mean Active Return (" + horizon.getCode() + ")", "BENCHMARK_ALPHA", horizon,
                    "CANDIDATE", "YUKIRA_V1_CANDIDATE_MEAN_ACTIVE",
                    List.of("FUND_NAV", "BENCHMARK_TRI"), "SYNCHRONOUS_ACTIVE_RETURNS",
                    "mean(R_p - R_b) * periods_per_year",
                    "Mean daily active return relative to benchmark linearly annualized over " + horizon.getCode()
                            + " horizon.");

            // PORTFOLIO CONSTRUCTION
            case "CONC-01", "CONC-02", "CONC-03" -> MetricHorizonEligibilityDto.supported(
                    code, code.equals("CONC-01") ? "Top 5 Equity Holdings Concentration"
                            : (code.equals("CONC-02") ? "Top 10 Equity Holdings Concentration"
                                    : "Herfindahl-Hirschman Index (HHI)"),
                    "PORTFOLIO_CONSTRUCTION", horizon,
                    "CANDIDATE", "PHASE_2R_CONC_SNAPSHOT",
                    List.of("PORTFOLIO_HOLDINGS"), "LATEST_STATUTORY_DISCLOSURE",
                    "Portfolio concentration based on latest statutory disclosures",
                    "Evaluated strictly from latest disclosed equity snapshot; period horizon does not apply.");

            default -> MetricHorizonEligibilityDto.unsupported(
                    code, code, "OTHER", horizon,
                    "UNKNOWN_METHODOLOGY",
                    List.of("UNKNOWN"), "UNSPECIFIED",
                    "Not available",
                    "Metric code not recognized by institutional metric registry.",
                    "UNRECOGNIZED_METRIC_CODE");
        };
    }

    /**
     * Retrieves the complete metric horizon eligibility catalog for a given
     * horizon.
     */
    public List<MetricHorizonEligibilityDto> getAllEligibilities(AnalyticalHorizon horizon) {
        AnalyticalHorizon effHorizon = horizon != null ? horizon : AnalyticalHorizon.THREE_YEAR;
        List<String> codes = List.of(
                "RET-02", "RET-03", "RET-07",
                "RSK-01", "RSK-02", "RSK-03", "RSK-04", "RSK-05", "RSK-06", "RSK-07",
                "MKT-01", "MKT-02", "MKT-03", "MKT-04", "MKT-05",
                "REL-01", "REL-02", "REL-03",
                "RAT-01", "RAT-02", "RAT-03", "RAT-04",
                "CONC-01", "CONC-02", "CONC-03");
        return codes.stream().map(c -> getEligibility(c, effHorizon)).toList();
    }

    /**
     * Comprehensive evaluation of a scheme option's empirical eligibility for a
     * requested horizon.
     * Evaluates actual observation count within the window against the threshold
     * and reports per-metric readiness.
     */
    public Map<String, Object> evaluateSchemeOptionEligibility(
            Long schemeOptionId,
            AnalyticalHorizon horizon,
            LocalDate asOfDate,
            OffsetDateTime knowledgeCutoffTime) {
        AnalyticalHorizon effHorizon = horizon != null ? horizon : AnalyticalHorizon.THREE_YEAR;
        LocalDate effAsOf = asOfDate != null ? asOfDate : LocalDate.now();
        OffsetDateTime effCutoff = knowledgeCutoffTime != null ? knowledgeCutoffTime : OffsetDateTime.now();
        LocalDate windowStart = effHorizon.calculateStartDate(effAsOf);

        SchemeOption option = schemeOptionRepository.findById(schemeOptionId)
                .orElseThrow(() -> new IllegalArgumentException("SchemeOption not found: " + schemeOptionId));

        List<NavObservation> candidates = navObservationRepository
                .findAuthoritativeObservationsAsOfCutoff(schemeOptionId, effAsOf, effCutoff);

        long distinctDaysInWindow = candidates.stream()
                .map(NavObservation::getEffectiveDate)
                .filter(d -> !d.isBefore(windowStart) && !d.isAfter(effAsOf))
                .distinct()
                .count();

        int threshold = effHorizon.getDefaultThreshold();
        boolean dataSufficient = distinctDaysInWindow >= threshold;

        List<MetricHorizonEligibilityDto> metricSpecs = getAllEligibilities(effHorizon);
        List<Map<String, Object>> metricReadiness = new ArrayList<>();

        for (MetricHorizonEligibilityDto spec : metricSpecs) {
            Map<String, Object> item = new HashMap<>();
            item.put("metricCode", spec.metricCode());
            item.put("metricName", spec.metricName());
            item.put("horizon", spec.horizonCode());
            item.put("supported", spec.supported());
            item.put("governanceStatus", spec.governanceStatus());
            item.put("implementationStatus", spec.implementationStatus());
            item.put("calculationEligible", spec.supported() && dataSufficient);
            item.put("requiredSeries", spec.requiredSeries());
            item.put("limitations", spec.knownLimitations());
            if (!spec.supported()) {
                item.put("unsupportedReason", spec.unsupportedReason());
            } else if (!dataSufficient) {
                item.put("eligibilityFailureReason", String.format(
                        "Insufficient observations: %d available in %s window [%s to %s], minimum %d required.",
                        distinctDaysInWindow, effHorizon.getCode(), windowStart, effAsOf, threshold));
            }
            metricReadiness.add(item);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("schemeOptionId", schemeOptionId);
        response.put("schemeName", (option.getPlan() != null && option.getPlan().getScheme() != null) ? option.getPlan().getScheme().getName() : null);
        response.put("amfiCode", option.getAmfiCode());
        response.put("isin", option.getIsin());
        response.put("requestedHorizon", effHorizon.getCode());
        response.put("horizonYears", effHorizon.getYears());
        response.put("windowStartDate", windowStart);
        response.put("asOfDate", effAsOf);
        response.put("knowledgeCutoffTime", effCutoff);
        response.put("availableObservationsInWindow", distinctDaysInWindow);
        response.put("requiredObservationThreshold", threshold);
        response.put("dataSufficiencySatisfied", dataSufficient);
        response.put("horizonGovernanceStatus", effHorizon.getMethodologyStatus());
        response.put("horizonFrozen", effHorizon.isFrozen());
        response.put("metrics", metricReadiness);

        return response;
    }
}
