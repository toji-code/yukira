package com.yukira.backend.service;

import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.dto.metric.AuthoritativeMetricDefinitionDto;
import com.yukira.backend.dto.metric.MetricAvailabilityEvaluationDto;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.NavObservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Authoritative Metric Registry Service.
 * Serves as the single repository source of truth for all quantitative metric definitions,
 * formulas, conventions, dependencies, availability requirements, and governance status.
 *
 * Epistemic Guardrails:
 * - Direct alignment with phase2h_quantitative_methodology.md (Table 4.1 30-metric candidate catalogue).
 * - RET-02 is the ONLY OPERATIONAL_BASELINE / APPROVED return metric.
 * - RSK-01 = Annualized Volatility, RSK-02 = Downside Semideviation, RSK-03 = Maximum Drawdown (strictly negative convention).
 * - RSK-04 is Maximum Drawdown Duration (calendar days spent underwater).
 * - RSK-06 is Historical VaR 95%, RSK-07 is Expected Shortfall 95% (CVaR).
 * - MKT-01 is Beta 3Y, MKT-02 is Downside Beta 3Y.
 * - Correlation and R-Squared are analytical regression diagnostics of MKT-01, NOT competing metric codes.
 * - Drawdown episodes and recovery states are analytical diagnostics of RSK-03 / RSK-04.
 * - Operational diagnostics (freshness, drift, unvalued counts) are categorized under OPERATIONAL_DATA_QUALITY.
 */
@Service
public class AuthoritativeMetricRegistryService {

    private final Map<String, AuthoritativeMetricDefinitionDto> registry = new LinkedHashMap<>();
    private final Map<String, String> aliasToCanonical = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private NavObservationRepository navObservationRepository;

    @Autowired(required = false)
    private BenchmarkObservationRepository benchmarkObservationRepository;

    public AuthoritativeMetricRegistryService() {
        registerAll();
    }

    public AuthoritativeMetricRegistryService(NavObservationRepository navObservationRepository) {
        this.navObservationRepository = navObservationRepository;
        registerAll();
    }

    public List<AuthoritativeMetricDefinitionDto> getAllDefinitions() {
        return new ArrayList<>(registry.values());
    }

    public MetricAvailabilityEvaluationDto.SchemeMetricAvailabilityReport evaluateMetricAvailability(
        Long schemeOptionId,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoffTime
    ) {
        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : LocalDate.now();
        OffsetDateTime effectiveCutoff = knowledgeCutoffTime != null ? knowledgeCutoffTime : OffsetDateTime.now();

        List<NavObservation> fundObs = navObservationRepository != null
            ? navObservationRepository.findAuthoritativeObservationsAsOfCutoff(schemeOptionId, effectiveAsOf, effectiveCutoff)
            : Collections.emptyList();

        int fundCount = fundObs.size();
        LocalDate earliest = fundObs.isEmpty() ? null : fundObs.get(0).getEffectiveDate();
        LocalDate latest = fundObs.isEmpty() ? null : fundObs.get(fundObs.size() - 1).getEffectiveDate();

        List<MetricAvailabilityEvaluationDto> evals = new ArrayList<>();
        int availableCount = 0;
        int restrictedCount = 0;

        for (AuthoritativeMetricDefinitionDto def : getAllDefinitions()) {
            String status = "AVAILABLE";
            String reason = "Metric is available for point-in-time calculation.";
            int requiredObs = 2; // minimum for simple return
            String horizon = def.measurementHorizon() != null ? def.measurementHorizon() : "POINT_IN_TIME";

            if ("3Y".equalsIgnoreCase(horizon)) {
                requiredObs = 700;
            } else if ("5Y".equalsIgnoreCase(horizon)) {
                requiredObs = 1200;
            } else if ("10Y".equalsIgnoreCase(horizon)) {
                requiredObs = 2400;
            } else if ("1Y".equalsIgnoreCase(horizon)) {
                requiredObs = 240;
            }

            boolean benchmarkNeeded = def.benchmarkDependency() != null && !"NONE".equalsIgnoreCase(def.benchmarkDependency());
            boolean benchmarkAvailable = true;
            boolean riskFreeNeeded = def.riskFreeDependency() != null && !"NONE".equalsIgnoreCase(def.riskFreeDependency());
            boolean riskFreeAvailable = true;

            if (fundCount == 0) {
                status = "MISSING_SOURCE_DATA";
                reason = "No authoritative NAV observations found as of " + effectiveAsOf;
                restrictedCount++;
            } else if (fundCount < requiredObs) {
                status = "INSUFFICIENT_OBSERVATIONS";
                reason = String.format("Requires %d observations for %s horizon, but only %d available.", requiredObs, horizon, fundCount);
                restrictedCount++;
            } else if (latest != null && latest.isBefore(effectiveAsOf.minusDays(14))) {
                status = "STALE_SOURCE_DATA";
                reason = "Latest available observation is from " + latest + ", exceeding staleness threshold.";
                restrictedCount++;
            } else {
                availableCount++;
            }

            evals.add(new MetricAvailabilityEvaluationDto(
                def.metricCode(),
                def.canonicalName(),
                status,
                reason,
                horizon,
                fundCount,
                requiredObs,
                earliest,
                latest,
                benchmarkAvailable,
                riskFreeAvailable,
                def.governanceStatus(),
                def.investigationQuestion()
            ));
        }

        return new MetricAvailabilityEvaluationDto.SchemeMetricAvailabilityReport(
            schemeOptionId,
            effectiveAsOf,
            effectiveCutoff,
            evals,
            availableCount,
            restrictedCount
        );
    }

    public Optional<AuthoritativeMetricDefinitionDto> getDefinition(String metricCodeOrAlias) {
        if (metricCodeOrAlias == null) return Optional.empty();
        String canonical = resolveCanonicalCode(metricCodeOrAlias);
        return Optional.ofNullable(registry.get(canonical));
    }

    public boolean isRegistered(String metricCodeOrAlias) {
        if (metricCodeOrAlias == null) return false;
        String canonical = resolveCanonicalCode(metricCodeOrAlias);
        return registry.containsKey(canonical);
    }

    public String resolveCanonicalCode(String codeOrAlias) {
        if (codeOrAlias == null) return null;
        String normalized = codeOrAlias.trim().toUpperCase();
        if (registry.containsKey(normalized)) {
            return normalized;
        }
        return aliasToCanonical.getOrDefault(normalized, normalized);
    }

    public String getRankingDirection(String metricCodeOrAlias) {
        return getDefinition(metricCodeOrAlias)
            .map(AuthoritativeMetricDefinitionDto::rankingDirection)
            .orElse("HIGHER_IS_BETTER");
    }

    public List<AuthoritativeMetricDefinitionDto> getDefinitionsByDomain(String domain) {
        if (domain == null) return Collections.emptyList();
        return registry.values().stream()
            .filter(def -> domain.equalsIgnoreCase(def.analyticalDimension()))
            .collect(Collectors.toList());
    }

    public List<AuthoritativeMetricDefinitionDto> getAnalyticalMetrics() {
        return registry.values().stream()
            .filter(def -> !Boolean.TRUE.equals(def.isDiagnostic()) && !"OPERATIONAL_DATA_QUALITY".equalsIgnoreCase(def.analyticalDimension()))
            .collect(Collectors.toList());
    }

    public List<AuthoritativeMetricDefinitionDto> getOperationalDiagnostics() {
        return registry.values().stream()
            .filter(def -> "OPERATIONAL_DATA_QUALITY".equalsIgnoreCase(def.analyticalDimension()) || Boolean.TRUE.equals(def.isDiagnostic()))
            .collect(Collectors.toList());
    }

    public List<AuthoritativeMetricDefinitionDto> getDiagnosticsForMetric(String parentMetricCode) {
        if (parentMetricCode == null) return Collections.emptyList();
        String canonicalParent = resolveCanonicalCode(parentMetricCode);
        return registry.values().stream()
            .filter(def -> canonicalParent.equalsIgnoreCase(def.parentMetricCode()))
            .collect(Collectors.toList());
    }

    private void registerAll() {
        // ==========================================
        // 1. RETURN QUALITY METRICS (RET Series)
        // ==========================================

        // RET-02: OPERATIONAL BASELINE (AGENTS.md Section 11 & 12)
        register(new AuthoritativeMetricDefinitionDto(
            "RET-02",
            "Simple Period Return",
            "RETURNS",
            "Point-to-Point Discrete Return",
            "Direct percentage change in Net Asset Value between two discrete operational boundary dates.",
            "R = (NAV_end - NAV_start) / NAV_start",
            "(NAV_end - NAV_start) / NAV_start",
            List.of("NAV_SERIES"),
            "DISCRETE_ARITHMETIC",
            "NOT_APPLICABLE",
            "DISCRETE_BOUNDARY",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "HIGHER_IS_BETTER",
            "Exact start and end boundary observations available within 4 calendar days tolerance.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-baseline",
            "OPERATIONAL_BASELINE",
            Map.of(
                "Boundary Tolerance", "4 calendar days lookback for weekend/market holiday adjustment",
                "Annualization", "None (Unannualized discrete period percentage)",
                "Data Provenance", "Cryptographically linked to official AMFI source artifacts (SHA-256)"
            ),
            "Highly sensitive to boundary date selection; does not reflect intermediate drawdowns, volatility, or cash flows.",
            "Does the discrete period return hold up when evaluated over alternative operational boundary dates?",
            "PERCENTAGE",
            false,
            null,
            List.of("SIMPLE_RETURN", "RET_02")
        ));

        // RET-03: 3Y CAGR
        register(new AuthoritativeMetricDefinitionDto(
            "RET-03",
            "Compound Annual Growth Rate (3Y CAGR)",
            "RETURNS",
            "Annualized Compound Return",
            "3-year compound annual growth rate normalized over leap-adjusted Julian trading years (365.25 days).",
            "CAGR = (NAV_end / NAV_start)^(365.25 / elapsed_calendar_days) - 1.0",
            "(NAV_end / NAV_start)^(365.25 / calendar_days) - 1.0",
            List.of("NAV_SERIES"),
            "GEOMETRIC_CAGR_JULIAN_365_25",
            "JULIAN_365_25",
            "3Y_STANDARD",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "HIGHER_IS_BETTER",
            ">= 700 trading days across 36 calendar months; no synthetic observation filling.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Annualization Factor", "365.25 Julian calendar days",
                "Minimum Trading Days", "700 trading days",
                "Reinvestment Convention", "Gross dividends reinvested (Growth option)"
            ),
            "Conceals sub-period volatility, intermediate drawdowns, and cash-flow timing. Past performance provides zero guarantee of future returns.",
            "Does the fund's 3-year compound growth depend on a single outlier quarter, or is it sustained across market regimes?",
            "PERCENTAGE",
            false,
            null,
            List.of("CAGR_3Y", "RET_03")
        ));

        // RET-01: 1Y CAGR
        register(new AuthoritativeMetricDefinitionDto(
            "RET-01",
            "Compound Annual Growth Rate (1Y CAGR)",
            "RETURNS",
            "Annualized Compound Return",
            "1-year compound growth rate evaluated across trailing 12 calendar months.",
            "CAGR = (NAV_end / NAV_start)^(365.25 / elapsed_calendar_days) - 1.0",
            "(NAV_end / NAV_start)^(365.25 / calendar_days) - 1.0",
            List.of("NAV_SERIES"),
            "GEOMETRIC_CAGR_JULIAN_365_25",
            "JULIAN_365_25",
            "1Y_CANDIDATE",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "HIGHER_IS_BETTER",
            ">= 240 trading days across trailing 12 calendar months.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Annualization Factor", "365.25 Julian calendar days",
                "Minimum Trading Days", "240 trading days"
            ),
            "Reflects short-term market momentum and regime-specific factor tailwinds; highly volatile across annual boundaries.",
            "Is the 1-year return representative of the fund's longer-term multi-cycle compounding ability?",
            "PERCENTAGE",
            false,
            null,
            List.of("CAGR_1Y", "RET_01")
        ));

        // RET-04: 5Y CAGR
        register(new AuthoritativeMetricDefinitionDto(
            "RET-04",
            "Compound Annual Growth Rate (5Y CAGR)",
            "RETURNS",
            "Annualized Compound Return",
            "5-year compound annual growth rate evaluated across trailing 60 calendar months.",
            "CAGR = (NAV_end / NAV_start)^(365.25 / elapsed_calendar_days) - 1.0",
            "(NAV_end / NAV_start)^(365.25 / calendar_days) - 1.0",
            List.of("NAV_SERIES"),
            "GEOMETRIC_CAGR_JULIAN_365_25",
            "JULIAN_365_25",
            "5Y_CANDIDATE",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "HIGHER_IS_BETTER",
            ">= 1200 trading days across 60 calendar months.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Annualization Factor", "365.25 Julian calendar days",
                "Minimum Trading Days", "1200 trading days"
            ),
            "Survivor bias and fund manager changes over 5-year periods can confound historical CAGR interpretation.",
            "Has the strategy maintained consistent management and portfolio allocation philosophy over the 5-year horizon?",
            "PERCENTAGE",
            false,
            null,
            List.of("CAGR_5Y", "RET_04")
        ));

        // RET-05: Rolling Return Distribution
        register(new AuthoritativeMetricDefinitionDto(
            "RET-05",
            "Rolling Return 3Y Distribution",
            "RETURNS",
            "Rolling Return Consistency",
            "Distribution of rolling 3-year annualized compound returns calculated across continuous daily step intervals.",
            "Mean and dispersion of rolling 3-year CAGRs",
            "Mean(CAGR_rolling_3Y)",
            List.of("NAV_SERIES"),
            "GEOMETRIC_CAGR_JULIAN_365_25",
            "JULIAN_365_25",
            "3Y_ROLLING",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "HIGHER_IS_BETTER",
            ">= 450 rolling 3-year windows (requires ~5 years total history).",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Window Step Size", "1 trading day",
                "Rolling Window Size", "3 Julian years (1095.75 calendar days)"
            ),
            "Consecutive rolling windows are serially correlated; does not represent independent statistical samples.",
            "What proportion of historical 3-year entry points yielded positive real returns?",
            "PERCENTAGE",
            false,
            null,
            List.of("ROLLING_RETURN_3Y", "RET_05")
        ));

        // RET-06: Rolling Outperformance % vs Benchmark
        register(new AuthoritativeMetricDefinitionDto(
            "RET-06",
            "Rolling Outperformance % vs Benchmark",
            "RETURNS",
            "Rolling Benchmark Excess Consistency",
            "Percentage of rolling calendar horizons in which the fund generated an annualized return strictly exceeding its primary benchmark Total Returns Index.",
            "Outperformance_Pct = Count(CAGR_fund,w > CAGR_bench,w) / Paired_Windows * 100.0",
            "Count(Fund_CAGR_w > Benchmark_CAGR_w) / Paired_Windows * 100.0",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES"),
            "DISCRETE_PERCENTAGE",
            "NOT_APPLICABLE",
            "3Y_ROLLING",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "HIGHER_IS_BETTER",
            ">= 450 paired rolling 3-year windows (requires ~5 years synchronous history).",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Strict Inequality", "Ties or equal returns are strictly excluded from outperformance count",
                "Benchmark Alignment", "Strictly synchronous calendar dates without date substitution"
            ),
            "Rolling outperformance frequency reflects historical relative persistence across past regimes. It does not attribute excess returns to skill or guarantee future outperformance.",
            "Has the fund's rolling outperformance been consistent across multiple market cycles, or concentrated in a single extended bull regime?",
            "PERCENTAGE",
            false,
            null,
            List.of("ROLLING_OUTPERFORMANCE_PCT", "RET_06")
        ));

        // RET-07: Active Return
        register(new AuthoritativeMetricDefinitionDto(
            "RET-07",
            "3-Year Annualized Active Return",
            "RETURNS",
            "Benchmark-Relative Excess Return",
            "Annualized excess compound return generated above the primary benchmark (Total Returns Index) over 36 months.",
            "Active Return = Fund_3Y_CAGR - Benchmark_3Y_CAGR",
            "Fund 3Y CAGR - Benchmark 3Y CAGR",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES"),
            "GEOMETRIC_CAGR_JULIAN_365_25",
            "JULIAN_365_25",
            "3Y_STANDARD",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "HIGHER_IS_BETTER",
            ">= 700 paired trading days aligned with official primary benchmark TRI.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Benchmark Family", "SEBI mandated Total Returns Index (TRI)",
                "Alignment Rule", "Exact date-by-date intersection; no synthetic filling"
            ),
            "Active return is an empirical excess growth rate over the primary benchmark. It does not isolate skill from factor tilt or beta and has no predictive power.",
            "Is the benchmark-relative active return generated by security selection skill or structural market beta and style tilts?",
            "PERCENTAGE",
            false,
            null,
            List.of("ACTIVE_RETURN_3Y", "RET_07")
        ));

        // ==========================================
        // 2. RISK & TAIL METRICS (RSK Series)
        // ==========================================

        // RSK-01: Annualized Volatility
        register(new AuthoritativeMetricDefinitionDto(
            "RSK-01",
            "3-Year Annualized Volatility",
            "RISK",
            "Total Return Dispersion",
            "Annualized sample standard deviation of daily discrete returns measuring total return dispersion.",
            "Volatility = Stdev_sample(daily_returns) * sqrt(252)",
            "Sample_Stdev(daily_returns) * sqrt(252)",
            List.of("NAV_SERIES"),
            "DISCRETE_ARITHMETIC",
            "SQRT_252",
            "3Y_STANDARD",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            ">= 700 daily returns over 36 calendar months.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Annualization Factor", "sqrt(252) trading days",
                "Divisor Convention", "N - 1 sample Bessel correction",
                "Distribution Assumption", "Stationary daily return variance"
            ),
            "Treats upside gains and downside drawdowns with equal mathematical penalty. Assumes stationary return variance.",
            "Is the observed annualized volatility driven by a few isolated market events, or does the fund consistently exhibit higher return dispersion than peers?",
            "PERCENTAGE",
            false,
            null,
            List.of("VOLATILITY_3Y", "ANNUALIZED_VOLATILITY", "RSK_01")
        ));

        // RSK-02: Downside Semideviation
        register(new AuthoritativeMetricDefinitionDto(
            "RSK-02",
            "Downside Semideviation (3Y)",
            "RISK",
            "Asymmetric Downside Risk",
            "Annualized sample volatility of returns falling strictly below the Minimum Acceptable Return (MAR = 0.0%).",
            "Downside_Dev = sqrt( sum( min(0, R_t - MAR)^2 ) / (N - 1) ) * sqrt(252)",
            "sqrt( sum( min(0, R_t - MAR)^2 ) / (N - 1) ) * sqrt(252)",
            List.of("NAV_SERIES"),
            "DISCRETE_ARITHMETIC",
            "SQRT_252",
            "3Y_STANDARD",
            "NONE",
            "NONE",
            "MAR_ZERO",
            "MAR_0_PERCENT",
            "LOWER_IS_BETTER",
            ">= 700 daily returns over 36 calendar months.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "MAR Convention", "0.0% nominal daily hurdle",
                "Divisor Convention", "N - 1 sample denominator over all observations",
                "Annualization Factor", "sqrt(252)"
            ),
            "Ignores magnitude of gains; sensitive to frequency and clustering of negative trading days.",
            "Does the downside deviation reflect persistent daily leakage or vulnerability to sharp market drops?",
            "PERCENTAGE",
            false,
            null,
            List.of("DOWNSIDE_SEMIDEVIATION_3Y", "RSK_02")
        ));

        // RSK-03: Maximum Drawdown (Strictly Negative Convention)
        register(new AuthoritativeMetricDefinitionDto(
            "RSK-03",
            "Maximum Drawdown (3Y)",
            "RISK",
            "Peak-to-Trough Capital Loss",
            "Largest percentage drop from a running historical peak Net Asset Value to a subsequent trough over 36 months (strictly negative convention).",
            "Max_Drawdown = min_t ( (NAV_t - Running_Peak_NAV_t) / Running_Peak_NAV_t )",
            "min_t ( (NAV_t - Running_Peak_NAV_t) / Running_Peak_NAV_t )",
            List.of("NAV_SERIES"),
            "DISCRETE_PERCENTAGE",
            "NOT_APPLICABLE",
            "3Y_STANDARD",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            ">= 700 trading days across 36 calendar months.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Peak Tracking", "Running maximum NAV from observation start date",
                "Sign Convention", "Strictly negative decimal (e.g. -0.15 = -15% decline)",
                "Recovery Evaluation", "Explicit verification whether subsequent NAV exceeds peak NAV; ongoing unrecovered episodes explicitly flagged"
            ),
            "Single path-dependent worst historical realization; does not indicate drawdown frequency or ongoing recovery state.",
            "Was the maximum drawdown experienced during general market turmoil, or did it stem from idiosyncratic portfolio losses?",
            "PERCENTAGE",
            false,
            null,
            List.of("MAX_DRAWDOWN_3Y", "MAX_DD_3Y", "RSK_03")
        ));

        // RSK-04: Maximum Drawdown Duration
        register(new AuthoritativeMetricDefinitionDto(
            "RSK-04",
            "Maximum Drawdown Duration",
            "RISK",
            "Underwater Recovery Time",
            "Longest continuous calendar period the fund spent underwater between peak NAV and complete recovery.",
            "Max elapsed calendar days between Peak NAV and subsequent recovery",
            "Max calendar days underwater",
            List.of("NAV_SERIES"),
            "CALENDAR_DAYS",
            "NOT_APPLICABLE",
            "3Y_STANDARD",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            ">= 700 trading days across 36 calendar months.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Duration Unit", "Calendar days elapsed",
                "Unclosed Episodes", "Capped at analysis cutoff date if unrecovered"
            ),
            "Dependent on market cycle length; episodes ongoing at the cutoff date remain unclosed.",
            "How long did the fund take to regain its previous peak compared to its category peer median?",
            "DAYS",
            false,
            null,
            List.of("DRAWDOWN_DURATION", "MAX_DD_DURATION", "RSK_04")
        ));

        // RSK-05: Ulcer Index
        register(new AuthoritativeMetricDefinitionDto(
            "RSK-05",
            "Ulcer Index (3Y)",
            "RISK",
            "Drawdown Stress Severity",
            "Quadratic measure of drawdown depth and duration reflecting aggregate investor psychological stress.",
            "Ulcer_Index = sqrt( sum( Drawdown_t^2 ) / N )",
            "sqrt( sum( Drawdown_t^2 ) / N )",
            List.of("NAV_SERIES"),
            "PERCENTAGE_POINTS",
            "NOT_APPLICABLE",
            "3Y_STANDARD",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            ">= 700 trading days across 36 calendar months.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Weighting", "Quadratic penalty on drawdown magnitude",
                "Unit", "Percentage points"
            ),
            "Non-linear penalty weighting; requires category peer context for meaningful interpretation.",
            "Does the fund spend extended periods underwater even if its single worst drawdown was moderate?",
            "POINTS",
            false,
            null,
            List.of("ULCER_INDEX_3Y", "RSK_05")
        ));

        // RSK-06: Historical VaR 95%
        register(new AuthoritativeMetricDefinitionDto(
            "RSK-06",
            "Historical VaR 95% (3Y)",
            "RISK",
            "Tail Risk Threshold",
            "Threshold daily loss expected to be exceeded on only 5% of trading days (1 day in 20).",
            "5th percentile of empirical daily return distribution (1-day horizon)",
            "Percentile_0.05(daily_returns)",
            List.of("NAV_SERIES"),
            "DISCRETE_PERCENTAGE",
            "NOT_APPLICABLE",
            "3Y_STANDARD",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            ">= 700 daily returns over 36 calendar months.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Confidence Level", "95.0%",
                "Horizon", "1 trading day"
            ),
            "Backward-looking empirical quantile; provides zero information about tail loss severity beyond the threshold.",
            "On the worst 5% of trading days, does the fund experience disproportionately larger drops than its benchmark?",
            "PERCENTAGE",
            false,
            null,
            List.of("VAR_95_3Y", "HISTORICAL_VAR_95", "RSK_06")
        ));

        // RSK-07: Expected Shortfall (CVaR 95%)
        register(new AuthoritativeMetricDefinitionDto(
            "RSK-07",
            "Expected Shortfall 95% (CVaR)",
            "RISK",
            "Conditional Tail Expectation",
            "Average expected daily percentage loss on trading days falling strictly beyond the 95% VaR threshold.",
            "Mean of daily returns strictly worse than 95% VaR threshold",
            "Mean(R_t | R_t < VaR_95)",
            List.of("NAV_SERIES"),
            "DISCRETE_PERCENTAGE",
            "NOT_APPLICABLE",
            "3Y_STANDARD",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            ">= 700 daily returns over 36 calendar months.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Conditioned Sample", "Worst 5% of daily observations (~37 trading days)"
            ),
            "Small tail sample size (~37 observations) makes metric sensitive to single-day extreme market shocks.",
            "When extreme market sell-offs occur, how severe is the fund's average daily loss?",
            "PERCENTAGE",
            false,
            null,
            List.of("CVAR_95_3Y", "EXPECTED_SHORTFALL_95", "RSK_07")
        ));

        // ==========================================
        // 3. RISK-ADJUSTED EFFICIENCY (RAT Series)
        // ==========================================

        // RAT-01: Sharpe Ratio
        register(new AuthoritativeMetricDefinitionDto(
            "RAT-01",
            "Sharpe Ratio (3Y)",
            "RISK_ADJUSTED",
            "Total Risk-Adjusted Excess Return",
            "Annualized excess return earned per unit of total annualized volatility relative to FBIL 91-Day T-Bill.",
            "Sharpe = (Mean(R_p - R_f) * 252) / (Stdev(R_p) * sqrt(252))",
            "(Mean(R_p - R_f) * 252) / (Stdev(R_p) * sqrt(252))",
            List.of("NAV_SERIES", "FBIL_91D_TBILL_SERIES"),
            "DISCRETE_ARITHMETIC",
            "SQRT_252",
            "3Y_STANDARD",
            "NONE",
            "NONE",
            "MANDATORY_FBIL_91D_TBILL",
            "FBIL_91_DAY_T_BILL",
            "HIGHER_IS_BETTER",
            ">= 700 daily returns aligned date-by-date with official FBIL 91-Day T-Bill rates.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Risk-Free Proxy", "FBIL 91-Day Treasury Bill secondary market rate",
                "Daily Conversion", "r_daily = (1 + r_annual)^(1/252) - 1.0",
                "Annualization Factor", "sqrt(252) trading days"
            ),
            "Penalizes upside return volatility equally with downside losses; assumes normal return distribution.",
            "Is the fund's Sharpe ratio driven by genuine excess return generation or artificially suppressed volatility?",
            "RATIO",
            false,
            null,
            List.of("SHARPE_3Y", "RAT_01")
        ));

        // RAT-02: Sortino Ratio
        register(new AuthoritativeMetricDefinitionDto(
            "RAT-02",
            "Sortino Ratio (3Y)",
            "RISK_ADJUSTED",
            "Downside Risk-Adjusted Excess Return",
            "Annualized excess return earned per unit of downside semideviation below the risk-free hurdle.",
            "Sortino = (Mean(R_p - R_f) * 252) / Downside_Semideviation_sample",
            "(Annualized Excess Return) / Downside Semideviation",
            List.of("NAV_SERIES", "FBIL_91D_TBILL_SERIES"),
            "DISCRETE_ARITHMETIC",
            "SQRT_252",
            "3Y_STANDARD",
            "NONE",
            "NONE",
            "MANDATORY_FBIL_91D_TBILL",
            "FBIL_91_DAY_T_BILL",
            "HIGHER_IS_BETTER",
            ">= 700 daily returns aligned with FBIL 91-Day T-Bill rates.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Downside Divisor", "N - 1 sample denominator (reconciled with RSK-02)",
                "MAR Hurdle", "FBIL 91-Day T-Bill daily converted rate",
                "Annualization Factor", "sqrt(252)"
            ),
            "Undefined when downside semideviation is zero; sensitive to threshold selection and negative return count.",
            "Does the fund demonstrate superior protection against downside deviations compared to its peer cohort?",
            "RATIO",
            false,
            null,
            List.of("SORTINO_3Y", "RAT_02")
        ));

        // RAT-03: Treynor Ratio
        register(new AuthoritativeMetricDefinitionDto(
            "RAT-03",
            "Treynor Ratio (3Y)",
            "RISK_ADJUSTED",
            "Systematic Risk-Adjusted Return",
            "Annualized excess return earned per unit of systematic market risk (Beta).",
            "Treynor = (Mean(R_p - R_f) * 252) / Beta_p",
            "(Mean(R_p - R_f) * 252) / Beta_p",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES", "FBIL_91D_TBILL_SERIES"),
            "DISCRETE_ARITHMETIC",
            "LINEAR_252",
            "3Y_STANDARD",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "MANDATORY_FBIL_91D_TBILL",
            "FBIL_91_DAY_T_BILL",
            "HIGHER_IS_BETTER",
            ">= 700 aligned daily observations; Beta > 0.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Beta Denominator", "Excess-return OLS Beta against primary benchmark TRI"
            ),
            "Meaningful only for well-diversified equity portfolios; completely ignores idiosyncratic and stock-specific risk.",
            "Is the fund compensating investors adequately for the systematic market exposure it assumes?",
            "RATIO",
            false,
            null,
            List.of("TREYNOR_3Y", "RAT_03")
        ));

        // RAT-04: Information Ratio
        register(new AuthoritativeMetricDefinitionDto(
            "RAT-04",
            "Information Ratio (3Y)",
            "RISK_ADJUSTED",
            "Active Efficiency Ratio",
            "Annualized mean active return earned per unit of annualized tracking error versus the primary benchmark.",
            "Information_Ratio = Annualized_Active_Return / Tracking_Error_3Y",
            "Active Return / Tracking Error",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES"),
            "DISCRETE_ARITHMETIC",
            "SQRT_252",
            "3Y_STANDARD",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "HIGHER_IS_BETTER",
            ">= 700 aligned trading days with primary benchmark TRI.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Benchmark", "Primary Total Returns Index (TRI) mandatory"
            ),
            "Can be distorted when tracking error approaches zero (closet indexing); historical only.",
            "Does the fund generate consistent excess returns relative to the active risk budget it consumes?",
            "RATIO",
            false,
            null,
            List.of("INFORMATION_RATIO_3Y", "RAT_04")
        ));

        // ==========================================
        // 4. MARKET SENSITIVITY (MKT Series)
        // ==========================================

        // MKT-01: Beta
        register(new AuthoritativeMetricDefinitionDto(
            "MKT-01",
            "Beta (3Y)",
            "MARKET_SENSITIVITY",
            "Systematic Market Sensitivity",
            "Linear sensitivity of portfolio excess returns relative to benchmark excess returns (OLS slope).",
            "Beta = Cov(R_p - R_f, R_b - R_f) / Var(R_b - R_f)",
            "Cov(R_p - R_f, R_b - R_f) / Var(R_b - R_f)",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES", "FBIL_91D_TBILL_SERIES"),
            "DISCRETE_ARITHMETIC",
            "NOT_APPLICABLE",
            "3Y_STANDARD",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "MANDATORY_FBIL_91D_TBILL",
            "FBIL_91_DAY_T_BILL",
            "NEUTRAL",
            ">= 700 aligned daily observations across 36 calendar months.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Model", "Single-index excess-return Ordinary Least Squares regression",
                "Benchmark", "SEBI mandated Total Returns Index (TRI)"
            ),
            "Assumes stationary linear relationship; beta fluctuates across bull, bear, and choppy market regimes.",
            "Does the fund's systematic market exposure align with its stated investment mandate and active risk profile?",
            "RATIO",
            false,
            null,
            List.of("BETA_3Y", "PORTFOLIO_BETA", "MKT_01")
        ));

        // MKT-02: Downside Beta
        register(new AuthoritativeMetricDefinitionDto(
            "MKT-02",
            "Downside Beta (3Y)",
            "MARKET_SENSITIVITY",
            "Down-Market Co-Movement",
            "Portfolio market sensitivity conditioned strictly on days when the benchmark return is negative.",
            "Downside_Beta = Cov(R_p, R_b | R_b < 0) / Var(R_b | R_b < 0)",
            "Cov(R_p, R_b | R_b < 0) / Var(R_b | R_b < 0)",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES"),
            "DISCRETE_ARITHMETIC",
            "NOT_APPLICABLE",
            "3Y_STANDARD",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            ">= 700 total aligned days with at least 100 benchmark negative trading days.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Down-Market Condition", "R_benchmark < 0.0%",
                "Minimum Negative Days", "100 trading days"
            ),
            "Conditioned sample excludes positive trading days; unadjusted for risk-free baseline.",
            "Does the fund amplify benchmark losses during down-market days (Downside Beta > 1.0)?",
            "RATIO",
            false,
            null,
            List.of("DOWNSIDE_BETA_3Y", "MKT_02")
        ));

        // MKT-03: Upside Capture Ratio
        register(new AuthoritativeMetricDefinitionDto(
            "MKT-03",
            "Upside Capture Ratio (3Y)",
            "MARKET_SENSITIVITY",
            "Up-Market Participation",
            "Compound return participation rate of the fund during periods when the benchmark is positive.",
            "Prod(1 + R_p | R_b > 0) / Prod(1 + R_b | R_b > 0)",
            "Prod(1 + R_p | R_b > 0) / Prod(1 + R_b | R_b > 0)",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES"),
            "GEOMETRIC_COMPOUNDED",
            "NOT_APPLICABLE",
            "3Y_STANDARD",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "HIGHER_IS_BETTER",
            ">= 700 aligned trading days with primary benchmark TRI.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of("Condition", "Benchmark daily return > 0.0%"),
            "Conditioned strictly on historical positive benchmark days; past participation is not predictive.",
            "Does the fund capture sufficient upside during bull market surges to compensate for its active fee?",
            "PERCENTAGE",
            false,
            null,
            List.of("UPSIDE_CAPTURE_3Y", "MKT_03")
        ));

        // MKT-04: Downside Capture Ratio
        register(new AuthoritativeMetricDefinitionDto(
            "MKT-04",
            "Downside Capture Ratio (3Y)",
            "MARKET_SENSITIVITY",
            "Down-Market Participation",
            "Compound return participation rate of the fund during periods when the benchmark is negative.",
            "Prod(1 + R_p | R_b < 0) / Prod(1 + R_b | R_b < 0)",
            "Prod(1 + R_p | R_b < 0) / Prod(1 + R_b | R_b < 0)",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES"),
            "GEOMETRIC_COMPOUNDED",
            "NOT_APPLICABLE",
            "3Y_STANDARD",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            ">= 700 aligned trading days with primary benchmark TRI.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of("Condition", "Benchmark daily return < 0.0%"),
            "Conditioned strictly on historical negative benchmark days; not predictive of future drawdowns.",
            "Does the fund cushion investor capital during market declines by capturing less than 100% of benchmark drops?",
            "PERCENTAGE",
            false,
            null,
            List.of("DOWNSIDE_CAPTURE_3Y", "MKT_04")
        ));

        // MKT-05: Capture Spread
        register(new AuthoritativeMetricDefinitionDto(
            "MKT-05",
            "Capture Spread (3Y)",
            "MARKET_SENSITIVITY",
            "Asymmetric Capture Efficiency",
            "Difference between upside capture ratio and downside capture ratio.",
            "Capture Spread = Upside_Capture - Downside_Capture",
            "Upside Capture - Downside Capture",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES"),
            "PERCENTAGE_POINTS",
            "NOT_APPLICABLE",
            "3Y_STANDARD",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "HIGHER_IS_BETTER",
            ">= 700 aligned trading days with primary benchmark TRI.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of("Spread Unit", "Percentage points difference"),
            "Combines two conditioned sample estimates; can be sample-sensitive.",
            "Does the fund maintain a persistently positive capture spread across differing market regimes?",
            "POINTS",
            false,
            null,
            List.of("CAPTURE_SPREAD_3Y", "MKT_05")
        ));

        // ==========================================
        // 5. REGIME PERFORMANCE (REG Series)
        // ==========================================

        // REG-01: Market Regime Stress Performance
        register(new AuthoritativeMetricDefinitionDto(
            "REG-01",
            "Historical Market Regime Performance",
            "MARKET_SENSITIVITY",
            "Stress & Regime Behavior",
            "Annualized compound return and excess return evaluated across pre-specified retrospective historical market regimes (liquidity shock, bull expansion, monetary tightening).",
            "CAGR_regime = (NAV_end / NAV_start)^(365.25 / elapsed_calendar_days) - 1.0",
            "(NAV_end / NAV_start)^(365.25 / calendar_days) - 1.0",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES"),
            "GEOMETRIC_CAGR_JULIAN_365_25",
            "JULIAN_365_25",
            "DISCRETE_EPISODE",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "HIGHER_IS_BETTER",
            ">= 126 continuous trading days within the pre-specified regime boundary.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Classification Basis", "Pre-specified retrospective historical regime boundaries per validation protocol",
                "Regime Coverage", "REG-01 (COVID Shock), REG-02 (Secular Bull), REG-03 (Monetary Tightening), REG-04 (Broad Expansion)"
            ),
            "Market regimes are identified retrospectively and cannot be identified in real time. Past regime performance does not guarantee future behavior under different macroeconomic shocks.",
            "How did the fund's downside resilience during liquidity shock regimes compare with its participation in subsequent secular bull expansions?",
            "PERCENTAGE",
            false,
            null,
            List.of("REGIME_PERFORMANCE", "REG_01")
        ));

        // ==========================================
        // 6. BENCHMARK / ALPHA RELATIVE (REL Series)
        // ==========================================

        // REL-01: Tracking Error
        register(new AuthoritativeMetricDefinitionDto(
            "REL-01",
            "Tracking Error (3Y Annualized)",
            "RELATIVE",
            "Active Tracking Volatility",
            "Annualized sample standard deviation of daily active return differences versus the primary benchmark TRI.",
            "Tracking_Error = Stdev_sample(R_p - R_b) * sqrt(252)",
            "Stdev(R_p - R_b) * sqrt(252)",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES"),
            "DISCRETE_ARITHMETIC",
            "SQRT_252",
            "3Y_STANDARD",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            ">= 700 aligned trading days with primary benchmark TRI.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Benchmark Requirement", "Strictly SEBI Total Returns Index (TRI); PRI strictly forbidden",
                "Divisor Convention", "N - 1 sample Bessel correction"
            ),
            "Treats upside and downside deviations symmetrically; high tracking error indicates active divergence, not necessarily skill.",
            "Is the fund's tracking error intentional active risk management or erratic portfolio churn?",
            "PERCENTAGE",
            false,
            null,
            List.of("TRACKING_ERROR_3Y", "REL_01")
        ));

        // REL-02: Jensen's Alpha
        register(new AuthoritativeMetricDefinitionDto(
            "REL-02",
            "Jensen's Alpha (3Y Annualized)",
            "RELATIVE",
            "CAPM Residual Excess Return",
            "Annualized intercept from the CAPM regression of portfolio excess returns on benchmark excess returns.",
            "Alpha = (Intercept_OLS) * 252",
            "OLS Intercept * 252",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES", "FBIL_91D_TBILL_SERIES"),
            "DISCRETE_ARITHMETIC",
            "LINEAR_252",
            "3Y_STANDARD",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "MANDATORY_FBIL_91D_TBILL",
            "FBIL_91_DAY_T_BILL",
            "HIGHER_IS_BETTER",
            ">= 700 aligned daily observations across 36 calendar months.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Regression Framework", "Single-factor CAPM OLS",
                "Annualization Factor", "252 linear trading days"
            ),
            "Single-index residual; sensitive to risk-free rate proxy and beta estimation noise.",
            "Does the estimated alpha remain statistically significant after controlling for market factor exposures?",
            "PERCENTAGE",
            false,
            null,
            List.of("JENSENS_ALPHA_3Y", "ALPHA_3Y", "REL_02")
        ));

        // ==========================================
        // 7. PORTFOLIO STRUCTURE & CONCENTRATION (PRT / CONC Series)
        // ==========================================

        // PRT-01 / CONC-02: Top 10 Holdings Concentration
        register(new AuthoritativeMetricDefinitionDto(
            "PRT-01",
            "Top 10 Equity Holdings Concentration",
            "PORTFOLIO_CONSTRUCTION",
            "Portfolio Concentration",
            "Sum of disclosed portfolio weights of the top 10 largest equity holdings.",
            "sum(w_i, i=1..10)",
            "sum(w_i, i=1..10)",
            List.of("PORTFOLIO_HOLDINGS_SNAPSHOT"),
            "PORTFOLIO_WEIGHT_SUM",
            "NOT_APPLICABLE",
            "LATEST_SNAPSHOT",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            "Authoritative monthly portfolio disclosure snapshot.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of("Source", "Statutory month-end portfolio disclosure"),
            "Excludes cash, derivatives, and unlisted holdings not reported in monthly disclosures.",
            "How does the fund's top 10 concentration compare to the category median?",
            "PERCENTAGE",
            false,
            null,
            List.of("CONC-02", "HOLDINGS_CONCENTRATION_TOP10", "TOP10_CONCENTRATION")
        ));

        // CONC-01: Top 5 Holdings Concentration
        register(new AuthoritativeMetricDefinitionDto(
            "CONC-01",
            "Top 5 Equity Holdings Concentration",
            "PORTFOLIO_CONSTRUCTION",
            "Portfolio Concentration",
            "Sum of disclosed portfolio weights of the top 5 largest equity holdings.",
            "sum(w_i, i=1..5)",
            "sum(w_i, i=1..5)",
            List.of("PORTFOLIO_HOLDINGS_SNAPSHOT"),
            "PORTFOLIO_WEIGHT_SUM",
            "NOT_APPLICABLE",
            "LATEST_SNAPSHOT",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            "Authoritative monthly portfolio disclosure snapshot.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of("Source", "Statutory month-end portfolio disclosure"),
            "Disclosed weights reflect periodic month-end statutory reporting; unnormalized for mid-month position shifts.",
            "Does top 5 concentration represent high-conviction security selection or excessive idiosyncratic single-stock vulnerability?",
            "PERCENTAGE",
            false,
            null,
            List.of("HOLDINGS_CONCENTRATION_TOP5", "TOP5_CONCENTRATION")
        ));

        // CONC-03: Herfindahl-Hirschman Index (HHI)
        register(new AuthoritativeMetricDefinitionDto(
            "CONC-03",
            "Herfindahl-Hirschman Index (HHI)",
            "PORTFOLIO_CONSTRUCTION",
            "Position Concentration",
            "Sum of squared percentage weights of all disclosed equity holdings. Higher scores denote concentrated exposure.",
            "HHI = sum( (100 * w_i)^2 )",
            "sum( (100 * w_i)^2 )",
            List.of("PORTFOLIO_HOLDINGS_SNAPSHOT"),
            "SQUARED_WEIGHT_SUM",
            "NOT_APPLICABLE",
            "LATEST_SNAPSHOT",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            "Authoritative monthly portfolio disclosure snapshot.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of("Scale", "0 to 10,000 points; > 2,500 indicates highly concentrated portfolio"),
            "Calculated strictly from disclosed equity holdings; residual undisclosed weight is omitted.",
            "Does the HHI score indicate balanced diversification across holdings or concentrated stock bets?",
            "POINTS",
            false,
            null,
            List.of("PORTFOLIO_HHI", "HHI")
        ));

        // PRT-02: Effective Number of Holdings (Candidate Specification)
        register(new AuthoritativeMetricDefinitionDto(
            "PRT-02",
            "Effective Number of Holdings",
            "PORTFOLIO_CONSTRUCTION",
            "Constituent Dispersion",
            "Inverse Herfindahl-Hirschman constituent count (1 / sum(w_i^2)) representing the equivalent number of equal-weighted holdings.",
            "N_eff = 1 / sum(w_i^2)",
            "1 / sum(w_i^2)",
            List.of("PORTFOLIO_HOLDINGS_SNAPSHOT"),
            "EFFECTIVE_COUNT",
            "NOT_APPLICABLE",
            "LATEST_SNAPSHOT",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "HIGHER_IS_BETTER",
            "Authoritative monthly portfolio disclosure snapshot with verified constituent weights.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "CANDIDATE_SPECIFICATION",
            Map.of("Scale", "Number of equal-weighted securities", "Derivation", "10,000 / HHI"),
            "Underweights small residual positions; candidate specification awaiting multi-cycle calibration.",
            "How does the effective number of holdings compare to the actual reported constituent count?",
            "COUNT",
            false,
            null,
            List.of("EFFECTIVE_HOLDINGS_COUNT", "PRT_02")
        ));

        // PRT-03: Active Share (Candidate Specification)
        register(new AuthoritativeMetricDefinitionDto(
            "PRT-03",
            "Active Share",
            "PORTFOLIO_CONSTRUCTION",
            "Benchmark Divergence",
            "Proportion of fund equity portfolio holdings differing from primary benchmark constituent weights.",
            "Active_Share = 0.5 * sum( |w_p,i - w_b,i| )",
            "0.5 * sum( |w_p,i - w_b,i| )",
            List.of("PORTFOLIO_HOLDINGS_SNAPSHOT", "BENCHMARK_CONSTITUENTS"),
            "PERCENTAGE",
            "NOT_APPLICABLE",
            "LATEST_SNAPSHOT",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "NEUTRAL",
            "Synchronous monthly portfolio disclosure and verified benchmark constituent weight file.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "CANDIDATE_SPECIFICATION",
            Map.of("Range", "0% (pure index fund) to 100% (zero overlap)"),
            "High active share does not ensure outperformance; candidate specification not yet wired into production calculation runs.",
            "Is high active share driven by high-conviction security selection or passive sector allocation bets?",
            "PERCENTAGE",
            false,
            null,
            List.of("ACTIVE_SHARE", "PRT_03")
        ));

        // PRT-04: Monthly Weight Turnover (Candidate Specification)
        register(new AuthoritativeMetricDefinitionDto(
            "PRT-04",
            "Monthly Weight Turnover",
            "PORTFOLIO_CONSTRUCTION",
            "Portfolio Churn & Stability",
            "Fraction of portfolio assets reallocated between consecutive monthly statutory disclosure snapshots.",
            "Turnover = 0.5 * sum( |w_t,i - w_t-1,i| )",
            "0.5 * sum( |w_t,i - w_t-1,i| )",
            List.of("PORTFOLIO_HOLDINGS_SNAPSHOT"),
            "PERCENTAGE",
            "NOT_APPLICABLE",
            "CONSECUTIVE_SNAPSHOTS",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            "Two consecutive authoritative monthly portfolio disclosure snapshots.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "CANDIDATE_SPECIFICATION",
            Map.of("Frequency", "Monthly snapshot delta"),
            "Misses intraday or intra-month position round-tripping; candidate specification under empirical review.",
            "Is portfolio turnover driven by disciplined profit taking or aggressive strategy churn?",
            "PERCENTAGE",
            false,
            null,
            List.of("PORTFOLIO_TURNOVER", "PRT_04")
        ));

        // PRT-05: Cash & Equivalent Allocation % (Candidate Specification)
        register(new AuthoritativeMetricDefinitionDto(
            "PRT-05",
            "Cash & Equivalent Allocation %",
            "PORTFOLIO_CONSTRUCTION",
            "Liquidity Cushion",
            "Proportion of total scheme net assets held in cash, reverse repos, TREPS, and liquid cash equivalents.",
            "Cash_Pct = Net_Assets_Cash / Total_Net_Assets * 100.0",
            "Net_Assets_Cash / Total_Net_Assets * 100.0",
            List.of("PORTFOLIO_HOLDINGS_SNAPSHOT"),
            "PERCENTAGE",
            "NOT_APPLICABLE",
            "LATEST_SNAPSHOT",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "NEUTRAL",
            "Authoritative monthly portfolio disclosure snapshot.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "CANDIDATE_SPECIFICATION",
            Map.of("Classification", "TREPS, Reverse Repo, Net Current Assets"),
            "Cash drag can penalize upside capture during rapid bull markets; candidate specification.",
            "Is cash deployment counter-cyclical, providing dry powder during market pullbacks?",
            "PERCENTAGE",
            false,
            null,
            List.of("CASH_ALLOCATION_PCT", "PRT_05")
        ));

        // GOV-01: Direct Plan TER (Candidate Specification)
        register(new AuthoritativeMetricDefinitionDto(
            "GOV-01",
            "Direct Plan TER",
            "GOVERNANCE_EXPENSE",
            "Total Expense Ratio",
            "Declared point-in-time Total Expense Ratio for the Direct Plan option.",
            "TER = Annualized_Operating_Expenses / Scheme_AUM * 100.0",
            "Operating Expenses / Scheme AUM * 100.0",
            List.of("SCHEME_EXPENSE_DISCLOSURE"),
            "PERCENTAGE",
            "NOT_APPLICABLE",
            "POINT_IN_TIME",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            "Statutory AMC expense disclosure.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "CANDIDATE_SPECIFICATION",
            Map.of("Plan", "Direct Plan strictly; regular plan distributor commissions excluded"),
            "TER changes periodically based on scheme AUM slabs; candidate specification.",
            "How does the direct plan expense ratio compare against identical-mandate category peers?",
            "PERCENTAGE",
            false,
            null,
            List.of("DIRECT_TER", "GOV_01")
        ));

        // ==========================================
        // 8. OPERATIONAL & DATA QUALITY EVIDENCE DIAGNOSTICS
        // ==========================================

        // PORTFOLIO_DRIFT
        register(new AuthoritativeMetricDefinitionDto(
            "PORTFOLIO_DRIFT",
            "Portfolio Target Allocation Drift",
            "OPERATIONAL_DATA_QUALITY",
            "Asset Allocation Drift",
            "Maximum percentage point divergence between actual investor portfolio weights and configured target allocation bands.",
            "max_c ( |Actual_Weight_c - Target_Weight_c| )",
            "max_c ( |Actual_Weight_c - Target_Weight_c| )",
            List.of("INVESTOR_PORTFOLIO_HOLDINGS", "TARGET_ALLOCATION_CONFIG"),
            "PERCENTAGE_POINTS",
            "NOT_APPLICABLE",
            "POINT_IN_TIME",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            "Active investor portfolio holdings and verified target allocation configuration.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-baseline",
            "OPERATIONAL_BASELINE",
            Map.of(
                "Scope", "Investor private portfolio level",
                "Non-Advisory Contract", "Informs re-examination; YUKIRA never generates automated rebalancing orders"
            ),
            "Depends on investor maintaining accurate current holdings; unadjusted for capital gains tax frictions.",
            "Which asset category or fund drove the allocation drift away from target bands?",
            "PERCENTAGE",
            true,
            null,
            List.of("PORTFOLIO_ALLOCATION_DRIFT", "ALLOCATION_DRIFT")
        ));

        // PORTFOLIO_UNVALUED_POSITIONS
        register(new AuthoritativeMetricDefinitionDto(
            "PORTFOLIO_UNVALUED_POSITIONS",
            "Unvalued Portfolio Positions",
            "OPERATIONAL_DATA_QUALITY",
            "Valuation Completeness",
            "Count of portfolio holdings lacking an effective NAV observation as of the analysis cutoff date.",
            "Count(Holding_i with NAV_as_of_cutoff == NULL)",
            "Count(Holding_i with NAV_as_of_cutoff == NULL)",
            List.of("INVESTOR_PORTFOLIO_HOLDINGS", "NAV_LEDGER"),
            "INTEGER_COUNT",
            "NOT_APPLICABLE",
            "POINT_IN_TIME",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            "Active investor portfolio holdings checked against PIT NAV availability ledger.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-baseline",
            "OPERATIONAL_BASELINE",
            Map.of("Epistemic Guardrail", "Unvalued positions are never silently estimated or filled with zero"),
            "Indicates pending NAV ingestion or unmapped scheme codes; portfolio totals exclude unvalued positions.",
            "Which specific portfolio positions lack point-in-time valuation data?",
            "COUNT",
            true,
            null,
            List.of("UNVALUED_POSITIONS", "UNVALUED_HOLDINGS_COUNT")
        ));

        // NAV_FRESHNESS
        register(new AuthoritativeMetricDefinitionDto(
            "NAV_FRESHNESS",
            "NAV Observation Freshness",
            "OPERATIONAL_DATA_QUALITY",
            "Market Data Recency",
            "Calendar days elapsed since the latest verified NAV observation was published and cryptographically recorded.",
            "today - latest_verified_effective_date",
            "today - latest_verified_effective_date",
            List.of("NAV_LEDGER"),
            "CALENDAR_DAYS",
            "NOT_APPLICABLE",
            "POINT_IN_TIME",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            "Verified point-in-time NAV ledger records.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-baseline",
            "OPERATIONAL_BASELINE",
            Map.of("Holiday Adjustment", "T+1 business day publishing cadence for Indian open-ended mutual funds"),
            "Market closures and bank holidays naturally extend elapsed calendar days without indicating data failure.",
            "Has the fund delayed its statutory daily NAV publication beyond standard T+1 market cutoffs?",
            "DAYS",
            true,
            null,
            List.of("NAV_STALENESS", "DATA_FRESHNESS_NAV")
        ));

        // HOLDINGS_FRESHNESS
        register(new AuthoritativeMetricDefinitionDto(
            "HOLDINGS_FRESHNESS",
            "Monthly Holdings Disclosure Freshness",
            "OPERATIONAL_DATA_QUALITY",
            "Disclosure Recency",
            "Calendar days elapsed since the statutory month-end holdings disclosure date.",
            "today - statutory_reporting_date",
            "today - statutory_reporting_date",
            List.of("PORTFOLIO_SNAPSHOT"),
            "CALENDAR_DAYS",
            "NOT_APPLICABLE",
            "POINT_IN_TIME",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            "Authoritative monthly portfolio disclosure snapshots.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-baseline",
            "OPERATIONAL_BASELINE",
            Map.of("Statutory Grace Period", "SEBI mandates month-end disclosure within 10 calendar days of month close"),
            "Holdings disclosures are monthly; intra-month trading activity remains private to the AMC.",
            "Is the fund's monthly holdings disclosure current or has the AMC exceeded the statutory reporting window?",
            "DAYS",
            true,
            null,
            List.of("HOLDINGS_STALENESS", "DATA_FRESHNESS_HOLDINGS")
        ));

        // UNCLASSIFIED_EXPOSURE
        register(new AuthoritativeMetricDefinitionDto(
            "UNCLASSIFIED_EXPOSURE",
            "Unclassified Holdings Exposure",
            "OPERATIONAL_DATA_QUALITY",
            "Look-Through Integrity",
            "Percentage of disclosed fund assets lacking verified security identifier mapping or sector classification.",
            "Unclassified_Weight / Total_Disclosed_Assets * 100.0",
            "Unclassified_Weight / Total_Disclosed_Assets * 100.0",
            List.of("PORTFOLIO_HOLDING", "SECURITY_MASTER"),
            "PERCENTAGE",
            "NOT_APPLICABLE",
            "LATEST_SNAPSHOT",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            "Authoritative monthly portfolio disclosure holdings matched against security master ledger.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of("Threshold", "Exposure > 5.0% triggers unclassified exposure warning"),
            "Limits look-through concentration and overlap precision; does not invalidate total fund NAV.",
            "Which underlying securities in the portfolio lack ISIN or market classification?",
            "PERCENTAGE",
            true,
            null,
            List.of("UNMAPPED_EXPOSURE", "UNCLASSIFIED_WEIGHT_PCT")
        ));

        // ==========================================
        // 9. ANALYTICAL DIAGNOSTICS (Attached to parent metrics)
        // ==========================================

        // RSK-03-DIAG-EPISODES: Drawdown Episode Diagnostics
        register(new AuthoritativeMetricDefinitionDto(
            "RSK-03-DIAG-EPISODES",
            "Drawdown Episode Diagnostics",
            "RISK",
            "Drawdown Episode Diagnostics",
            "Analytical diagnostics of historical drawdown episodes including intermediate trough dates, episode count, and ongoing recovery state. Serves as supporting context for Maximum Drawdown (RSK-03).",
            "Tracking of all continuous peak-to-recovery cycles over the 36-month lookback window.",
            "Episode_Diagnostics(Peak, Trough, Recovery_Date)",
            List.of("NAV_SERIES"),
            "DIAGNOSTIC_STRUCT",
            "NOT_APPLICABLE",
            "3Y_STANDARD",
            "NONE",
            "NONE",
            "NONE",
            "NONE",
            "NEUTRAL",
            "Continuous daily NAV observations over 36 calendar months.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of("Diagnostic Target", "RSK-03 / RSK-04 supporting context"),
            "Diagnostic information describing the drawdown path; not an independently authorized portfolio score.",
            "How many intermediate drawdown episodes occurred before the fund reached its historical maximum drawdown?",
            "COUNT",
            true,
            "RSK-03",
            List.of("DRAWDOWN_EPISODES", "RSK_03_DIAG")
        ));

        // MKT-01-DIAG-CORR: Benchmark Return Correlation
        register(new AuthoritativeMetricDefinitionDto(
            "MKT-01-DIAG-CORR",
            "Benchmark Return Correlation",
            "MARKET_SENSITIVITY",
            "Regression Diagnostic",
            "Empirical Pearson correlation coefficient between daily fund excess returns and benchmark excess returns. Serves as a regression diagnostic of Beta (MKT-01).",
            "Correlation = Cov(R_p - R_f, R_b - R_f) / (Stdev(R_p - R_f) * Stdev(R_b - R_f))",
            "Cov(R_p, R_b) / (Sigma_p * Sigma_b)",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES", "FBIL_91D_TBILL_SERIES"),
            "DISCRETE_ARITHMETIC",
            "NOT_APPLICABLE",
            "3Y_STANDARD",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "MANDATORY_FBIL_91D_TBILL",
            "FBIL_91_DAY_T_BILL",
            "NEUTRAL",
            ">= 700 aligned daily observations across 36 calendar months.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of("Mathematical Relation", "corr = beta * sigma_b / sigma_p"),
            "Linear co-movement diagnostic; does not measure beta magnitude or active return generation.",
            "Does the fund exhibit high linear correlation with the benchmark despite active sector bets?",
            "RATIO",
            true,
            "MKT-01",
            List.of("BENCHMARK_CORRELATION", "CORRELATION_3Y")
        ));

        // MKT-01-DIAG-RSQ: Benchmark Regression R-Squared
        register(new AuthoritativeMetricDefinitionDto(
            "MKT-01-DIAG-RSQ",
            "Benchmark Regression R-Squared",
            "MARKET_SENSITIVITY",
            "Regression Diagnostic",
            "Coefficient of determination (R^2) measuring the proportion of fund return variance explained by benchmark returns. Diagnostic of Beta (MKT-01).",
            "R_Squared = Correlation^2",
            "Correlation^2",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES", "FBIL_91D_TBILL_SERIES"),
            "PERCENTAGE",
            "NOT_APPLICABLE",
            "3Y_STANDARD",
            "MANDATORY_PRIMARY_TRI",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "NEUTRAL",
            ">= 700 aligned daily observations across 36 calendar months.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "v1.0.0-candidate",
            "IMPLEMENTED_CANDIDATE",
            Map.of("Interpretation", "Values > 0.85 indicate fund returns are primarily driven by benchmark fluctuations"),
            "Statistical diagnostic of CAPM regression fit; does not indicate whether active divergence was value-additive.",
            "What proportion of the fund's historical price movement is explained purely by broad-market movements?",
            "PERCENTAGE",
            true,
            "MKT-01",
            List.of("BENCHMARK_R_SQUARED", "R_SQUARED_3Y")
        ));

        // ==========================================
        // 8. HISTORICAL STRESS ANALYSIS METRICS (STR Series - Tasks 30-34)
        // ==========================================

        // STR-01: Historical Stress Proxy Cumulative Return
        register(new AuthoritativeMetricDefinitionDto(
            "STR-01",
            "Historical Stress Proxy Cumulative Return",
            "HISTORICAL_STRESS",
            "Stress Performance Proxy",
            "Cumulative compounding return of constant-weight daily rebalanced portfolio proxy across a deterministically identified market stress episode.",
            "R_{p,cum} = \\prod_{t=1}^{T} (1 + \\sum_{i=1}^{M} w_i R_{i,t}) - 1",
            "\\prod_{t=1}^{T} (1 + R_{p,t}) - 1, \\quad R_{p,t} = \\sum_{i} w_i R_{i,t}",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES", "PORTFOLIO_WEIGHTS"),
            "DISCRETE_COMPOUNDED",
            "NOT_ANNUALIZED_EVENT_WINDOW",
            "EVENT_SPECIFIC_PEAK_TO_RECOVERY",
            "MANDATORY_MARKET_STRESS_REFERENCE",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "HIGHER_IS_BETTER",
            ">= 15 common aligned daily observations across episode; full constituent universe existence; non-negative daily wealth trajectory.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE (withheld if constituent history is truncated without synthetic data fabrication)",
            "PORTFOLIO_STRESS_METHODOLOGY_V2",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Rebalancing Convention", "Constant-weight daily rebalancing to initial capital weights",
                "Static Weights", "Applies current portfolio weights backward across historical episode",
                "Portfolio Construction Convention", "CONSTANT_WEIGHT_DAILY_REBALANCED_PROXY",
                "Identification Policy", "PORTFOLIO_STRESS_IDENTIFICATION_POLICY_V2",
                "Evidence Policy", "PORTFOLIO_STRESS_EVIDENCE_POLICY_V2",
                "Eligibility Contract", "CENTRALIZED_STRESS_ELIGIBILITY_V2 (6 explicit outcomes across 8 separated dimensions)",
                "Duration Representation", "Trading sessions (sessions) separated from elapsed calendar days (days)",
                "Snapshot Provenance", "PORTFOLIO_ALLOCATION_SNAPSHOT_V2"
            ),
            "Static allocation proxy; does not reflect actual historical cash flows, reallocations, transaction costs, or tax drag.",
            "How would today's capital allocation have performed through this historical market crisis assuming daily constant-weight rebalancing?",
            "PERCENTAGE",
            false,
            null,
            List.of("STRESS_CUMULATIVE_RETURN", "PORTFOLIO_STRESS_RETURN", "PORTFOLIO_STRESS_CUM_RETURN")
        ));

        // STR-02: Historical Stress Maximum Drawdown
        register(new AuthoritativeMetricDefinitionDto(
            "STR-02",
            "Historical Stress Maximum Drawdown",
            "HISTORICAL_STRESS",
            "Stress Drawdown",
            "Maximum peak-to-trough percentage loss experienced by the constant-weight daily rebalanced portfolio proxy during a historical stress episode.",
            "MDD_p = \\min_{t \\in [t_0, T]} \\left( \\frac{V_{p,t} - \\max_{s \\in [t_0, t]} V_{p,s}}{\\max_{s \\in [t_0, t]} V_{p,s}} \\right), \\quad V_{p,0} = 1.0",
            "\\min_{t} \\left( \\frac{V_{p,t} - \\max_{s \\le t} V_{p,s}}{\\max_{s \\le t} V_{p,s}} \\right)",
            List.of("NAV_SERIES", "BENCHMARK_TRI_SERIES", "PORTFOLIO_WEIGHTS"),
            "DISCRETE_COMPOUNDED",
            "NOT_ANNUALIZED",
            "EVENT_SPECIFIC_PEAK_TO_TROUGH",
            "MANDATORY_MARKET_STRESS_REFERENCE",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "LOWER_IS_BETTER",
            ">= 15 common aligned daily observations across episode; full constituent universe existence; non-negative daily wealth trajectory.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE (withheld if constituent history is truncated without synthetic data fabrication)",
            "PORTFOLIO_STRESS_METHODOLOGY_V2",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Baseline Preserved", "Evaluated from initial pre-stress peak level (V_{p,0} = 1.0) through trough date",
                "Strict Negative Convention", "MDD is reported as a non-positive percentage",
                "Identification Policy", "PORTFOLIO_STRESS_IDENTIFICATION_POLICY_V2",
                "Evidence Policy", "PORTFOLIO_STRESS_EVIDENCE_POLICY_V2",
                "Eligibility Contract", "CENTRALIZED_STRESS_ELIGIBILITY_V2",
                "Duration Representation", "Trading sessions to trough/recovery separated from elapsed calendar days"
            ),
            "Measures peak-to-trough drawdown of the proxy wealth trajectory; does not model investor panic sales or liquidity disruptions.",
            "What was the deepest capital decline experienced by this allocation structure during the stress episode?",
            "PERCENTAGE",
            false,
            null,
            List.of("STRESS_MAX_DRAWDOWN", "PORTFOLIO_STRESS_MDD", "STRESS_DRAWDOWN")
        ));

        // STR-03: Constituent Exact Additive Return Contribution (Observation-Specific Carino Logarithmic Linking)
        register(new AuthoritativeMetricDefinitionDto(
            "STR-03",
            "Constituent Exact Additive Return Contribution",
            "HISTORICAL_STRESS",
            "Stress Return Attribution",
            "Exact additive multi-period return contribution of constituent fund i to portfolio cumulative return, linked via observation-specific Carino logarithmic factors k_t.",
            "A_i = \\frac{1}{K} \\sum_{t=1}^{T} k_t w_i R_{i,t}, \\quad k_t = \\begin{cases} \\frac{\\ln(1 + R_{p,t})}{R_{p,t}}, & R_{p,t} \\ne 0 \\\\ 1, & R_{p,t} = 0 \\end{cases}, \\quad K = \\begin{cases} \\frac{\\ln(1 + R_{p,cum})}{R_{p,cum}}, & R_{p,cum} \\ne 0 \\\\ 1, & R_{p,cum} = 0 \\end{cases}",
            "A_i = \\frac{1}{K} \\sum_{t=1}^{T} k_t w_i R_{i,t}, \\quad \\sum_{i} A_i = R_{p,cum}",
            List.of("NAV_SERIES", "PORTFOLIO_WEIGHTS", "PORTFOLIO_DAILY_RETURNS"),
            "CARINO_LOGARITHMIC_LINKING",
            "NOT_ANNUALIZED",
            "EVENT_SPECIFIC_PEAK_TO_RECOVERY",
            "MANDATORY_MARKET_STRESS_REFERENCE",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "HIGHER_IS_BETTER",
            "Synchronized daily returns across full constituent universe; finite logarithmic domain (wealth path V_t > 0); reconciliation tolerance <= 0.0001%.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE (uncomputable if portfolio proxy return is withheld)",
            "PORTFOLIO_STRESS_METHODOLOGY_V2",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Observation-Specific Linking Factor", "Factor k_t is observation-specific across trading days t (derived from portfolio return R_{p,t}), NOT constituent-specific k_i, guaranteeing exact linear additivity sum(A_i) = R_{p,cum}",
                "Reconciliation Tolerance", "Attribution sums to portfolio return within +/-0.0001% (+/-1e-6 decimal)",
                "Attribution Methodology", "CARINO_DAILY_REBALANCED_LOGARITHMIC_LINKING",
                "Evidence Policy", "PORTFOLIO_STRESS_EVIDENCE_POLICY_V2",
                "Attribution Role Taxonomy", "Two-dimensional role classification: linkedAttributionRole (Carino A_i based) vs standalonePerformanceRole (R_{i,cum} based)",
                "Primary Detractor Rule", "Defined unambiguously by largest negative linked additive contribution A_i; never conflated with worst standalone return",
                "Resilient Lower Loss Rule", "Requires R_{i,cum} > R_{p,cum} and A_i >= -1.0% pts; does NOT imply positive return, downside protection, or manager skill"
            ),
            "Attribution reflects compounding and daily constant-weight rebalancing over the specific episode; does not represent Euler covariance risk contribution (%RC_i).",
            "How many percentage points of the portfolio's total stress gain or loss originated from this specific fund?",
            "PERCENTAGE_POINTS",
            false,
            "STR-01",
            List.of("CARINO_RETURN_CONTRIBUTION", "FUND_STRESS_EXACT_CONTRIBUTION", "EXACT_ADDITIVE_CONTRIBUTION")
        ));

        // STR-04: Constituent Weighted Return to Linked Attribution Difference
        register(new AuthoritativeMetricDefinitionDto(
            "STR-04",
            "Constituent Weighted Return to Linked Attribution Difference",
            "HISTORICAL_STRESS",
            "Stress Attribution Diagnostic",
            "Difference between the single-period weighted constituent cumulative-return indicator and its exact multi-period linked additive attribution. Measures the combined impact of multi-period compounding and daily constant-weight rebalancing.",
            "\\Delta_i = w_i R_{i,cum} - A_i",
            "\\Delta_i = w_i R_{i,cum} - A_i",
            List.of("CONSTITUENT_CUMULATIVE_RETURN", "CONSTITUENT_WEIGHT", "EXACT_ADDITIVE_CONTRIBUTION"),
            "ARITHMETIC_DIFFERENCE",
            "NOT_ANNUALIZED",
            "EVENT_SPECIFIC_PEAK_TO_RECOVERY",
            "MANDATORY_MARKET_STRESS_REFERENCE",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "NEUTRAL",
            "Requires both standalone constituent cumulative return and Carino exact additive attribution.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "PORTFOLIO_STRESS_METHODOLOGY_V2",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Diagnostic Nature", "Accurately describes the difference between the naive weighted constituent cumulative-return indicator and its linked additive attribution",
                "Non-Isolation Notice", "Does NOT claim to isolate pure rebalancing interaction; reflects compounding cross-asset path divergence"
            ),
            "Difference includes compounding effects and cross-asset price path divergence; does NOT isolate pure rebalancing interaction in isolation.",
            "How much does the naive weighted return indicator diverge from the fund's exact multi-period additive attribution?",
            "PERCENTAGE_POINTS",
            true,
            "STR-03",
            List.of("ATTRIBUTION_DIFFERENCE", "WEIGHTED_INDICATOR_TO_ATTRIBUTION_DIFF", "REBALANCING_INTERACTION_DIAGNOSTIC")
        ));

        // STR-05: Portfolio Compounding Residual
        register(new AuthoritativeMetricDefinitionDto(
            "STR-05",
            "Portfolio Compounding Residual",
            "HISTORICAL_STRESS",
            "Stress Attribution Diagnostic",
            "Measures the aggregate difference between the sum of weighted standalone constituent cumulative-return indicators and the compounded constant-weight daily rebalanced portfolio proxy return.",
            "\\Delta_{compounding} = \\sum_{i=1}^{M} w_i R_{i,cum} - R_{p,cum}",
            "\\Delta_{compounding} = \\sum_{i} w_i R_{i,cum} - R_{p,cum}",
            List.of("CONSTITUENT_CUMULATIVE_RETURNS", "PORTFOLIO_WEIGHTS", "PORTFOLIO_CUMULATIVE_RETURN"),
            "ARITHMETIC_DIFFERENCE",
            "NOT_ANNUALIZED",
            "EVENT_SPECIFIC_PEAK_TO_RECOVERY",
            "MANDATORY_MARKET_STRESS_REFERENCE",
            "NIFTY_500_TRI",
            "NONE",
            "NONE",
            "NEUTRAL",
            "Requires calculable portfolio cumulative return and valid constituent cumulative returns across all included funds.",
            "FAIL_CLOSED_RETURN_UNAVAILABLE",
            "PORTFOLIO_STRESS_METHODOLOGY_V2",
            "IMPLEMENTED_CANDIDATE",
            Map.of(
                "Compounding Discrepancy", "Multi-period compounding is mathematically non-linear (prod(1+r) - 1 != sum(w*r)), creating an expected compounding residual that Carino logarithmic linking reconciles",
                "Reconciliation Role", "Measures the non-additivity of single-period weighted return indicators; vanishes when Carino logarithmic linking is applied"
            ),
            "Measures the non-additivity of single-period weighted return indicators; vanishes when Carino logarithmic linking is applied.",
            "What is the total multi-period compounding gap between naive constituent weighted returns and true portfolio cumulative growth?",
            "PERCENTAGE_POINTS",
            true,
            "STR-01",
            List.of("PORTFOLIO_COMPOUNDING_RESIDUAL", "STRESS_COMPOUNDING_GAP", "COMPOUNDING_RESIDUAL")
        ));
    }

    private void register(AuthoritativeMetricDefinitionDto definition) {
        String code = definition.metricCode().trim().toUpperCase();
        registry.put(code, definition);

        if (definition.aliases() != null) {
            for (String alias : definition.aliases()) {
                if (alias != null && !alias.isBlank()) {
                    aliasToCanonical.put(alias.trim().toUpperCase(), code);
                }
            }
        }
    }
}
