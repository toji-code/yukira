package com.yukira.backend.service;

import com.yukira.backend.dto.metric.AuthoritativeMetricDefinitionDto;
import com.yukira.backend.dto.monitoring.MonitoringMetricConfigDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Service centralizing quantitative metric mappings, units, scaling conventions,
 * and candidate thresholds for continuous fund and portfolio monitoring.
 *
 * Epistemic Guardrails:
 * - Direct alignment with AuthoritativeMetricRegistryService as the sole source of truth.
 * - RSK-01 = Annualized Volatility, RSK-02 = Downside Semideviation, RSK-03 = Maximum Drawdown.
 * - Maximum Drawdown uses negative decimal convention (loss is negative, e.g. -0.15 = -15%).
 * - RSK-04 is Maximum Drawdown Duration (calendar days spent underwater).
 * - MKT-01 is Beta 3Y, MKT-02 is Downside Beta 3Y.
 * - Stored metric_result returns and volatilities are decimals; concentration and drift are percentage points.
 * - Implementation of candidate metrics does NOT represent validation or approval.
 */
@Service
public class MonitoringMetricRegistryService {

    private final AuthoritativeMetricRegistryService authoritativeRegistry;
    private final Map<String, MonitoringMetricConfigDto> configs = new LinkedHashMap<>();

    public MonitoringMetricRegistryService(AuthoritativeMetricRegistryService authoritativeRegistry) {
        this.authoritativeRegistry = authoritativeRegistry;
        registerAll();
    }

    public List<MonitoringMetricConfigDto> getAllConfigs() {
        return new ArrayList<>(configs.values());
    }

    public Optional<MonitoringMetricConfigDto> getConfig(String metricCode) {
        if (metricCode == null) return Optional.empty();
        String canonical = authoritativeRegistry.resolveCanonicalCode(metricCode);
        MonitoringMetricConfigDto cfg = configs.get(canonical);
        if (cfg == null) {
            cfg = configs.get(metricCode.trim().toUpperCase());
        }
        return Optional.ofNullable(cfg);
    }

    public boolean isSupported(String metricCode) {
        if (metricCode == null) return false;
        String canonical = authoritativeRegistry.resolveCanonicalCode(metricCode);
        return configs.containsKey(canonical) || configs.containsKey(metricCode.trim().toUpperCase());
    }

    /**
     * Normalizes an investor or system input threshold into the canonical internal numeric scale.
     * E.g.:
     * - For RSK-03 (Drawdown): 15.0 or 0.15 -> -0.1500 (strictly negative decimal).
     * - For RSK-01 (Volatility): 20.0 -> 0.2000 (decimal).
     * - For PRT-01 / CONC-02 or PORTFOLIO_DRIFT: 5.0 -> 5.0000 (percentage points).
     */
    public BigDecimal normalizeThreshold(String metricCode, BigDecimal rawInput, String unit) {
        if (rawInput == null) return null;
        String canonical = authoritativeRegistry.resolveCanonicalCode(metricCode);

        // Maximum Drawdown: RSK-03 (Strictly negative decimal convention)
        if ("RSK-03".equals(canonical)) {
            BigDecimal abs = rawInput.abs();
            // If entered as percentage (e.g. 15 or 15.0), scale to decimal (0.15)
            if (abs.compareTo(BigDecimal.ONE) > 0 || "%".equals(unit) || "PERCENTAGE".equalsIgnoreCase(unit)) {
                abs = abs.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            }
            return abs.negate();
        }

        // Return and Volatility metrics stored as decimals in metric_result (e.g. 0.20 for 20%)
        if ("RSK-01".equals(canonical) || "RSK-02".equals(canonical) || "RET-01".equals(canonical)
            || "RET-02".equals(canonical) || "RET-03".equals(canonical) || "RET-04".equals(canonical)
            || "RET-07".equals(canonical) || "REL-01".equals(canonical) || "REL-02".equals(canonical)) {
            BigDecimal abs = rawInput.abs();
            if (abs.compareTo(BigDecimal.ONE) > 0 || "%".equals(unit) || "PERCENTAGE".equalsIgnoreCase(unit)) {
                return rawInput.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            }
            return rawInput;
        }

        // Ratios and percentage point metrics (Beta, Capture, Concentration, Drift, Days, Counts)
        return rawInput;
    }

    /**
     * Formats an internal stored numeric value into an investor-friendly display string with appropriate units.
     */
    public String formatDisplayValue(String metricCode, BigDecimal numericVal) {
        if (numericVal == null) return "Unavailable";
        Optional<MonitoringMetricConfigDto> opt = getConfig(metricCode);

        if (opt.isEmpty()) {
            return numericVal.toPlainString();
        }

        MonitoringMetricConfigDto cfg = opt.get();
        if ("PERCENTAGE".equalsIgnoreCase(cfg.unit())) {
            double val = numericVal.doubleValue() * cfg.scaleFactor();
            return String.format("%.2f%%", val);
        } else if ("DAYS".equalsIgnoreCase(cfg.unit())) {
            return String.format("%d days", numericVal.longValue());
        } else if ("COUNT".equalsIgnoreCase(cfg.unit())) {
            return String.format("%d", numericVal.longValue());
        } else if ("RATIO".equalsIgnoreCase(cfg.unit())) {
            return String.format("%.2f", numericVal.doubleValue());
        } else if ("POINTS".equalsIgnoreCase(cfg.unit())) {
            return String.format("%.2f pts", numericVal.doubleValue());
        }

        return numericVal.toPlainString();
    }

    private void registerAll() {
        List<String> numericOps = List.of("GT", "GTE", "LT", "LTE", "ABS_CHANGE_GT", "DATA_UNAVAILABLE");
        List<String> freshnessOps = List.of("GT", "GTE", "EVIDENCE_STALE", "DATA_UNAVAILABLE");

        // --- RSK-03: Maximum Drawdown (3Y) ---
        registerRule(
            "RSK-03",
            "LT",
            numericOps,
            100.0,
            true, // Strictly negative convention
            BigDecimal.valueOf(-0.15),
            "-15.00%"
        );

        // --- RSK-01: 3-Year Annualized Volatility ---
        registerRule(
            "RSK-01",
            "GT",
            numericOps,
            100.0,
            false,
            BigDecimal.valueOf(0.20),
            "20.00%"
        );

        // --- RSK-02: Downside Semideviation (3Y) ---
        registerRule(
            "RSK-02",
            "GT",
            numericOps,
            100.0,
            false,
            BigDecimal.valueOf(0.15),
            "15.00%"
        );

        // --- RSK-04: Maximum Drawdown Duration ---
        registerRule(
            "RSK-04",
            "GT",
            numericOps,
            1.0,
            false,
            BigDecimal.valueOf(365),
            "365 days"
        );

        // --- RET-02: Simple Period Return ---
        registerRule(
            "RET-02",
            "LT",
            numericOps,
            100.0,
            false,
            BigDecimal.ZERO,
            "0.00%"
        );

        // --- RET-03: Compound Annual Growth Rate (3Y CAGR) ---
        registerRule(
            "RET-03",
            "LT",
            numericOps,
            100.0,
            false,
            BigDecimal.ZERO,
            "0.00%"
        );

        // --- RET-01: 1Y CAGR ---
        registerRule(
            "RET-01",
            "LT",
            numericOps,
            100.0,
            false,
            BigDecimal.ZERO,
            "0.00%"
        );

        // --- RET-06: Rolling Outperformance % vs Benchmark ---
        registerRule(
            "RET-06",
            "LT",
            numericOps,
            1.0,
            false,
            BigDecimal.valueOf(50.0),
            "50.00%"
        );

        // --- RET-07: 3-Year Annualized Active Return ---
        registerRule(
            "RET-07",
            "LT",
            numericOps,
            100.0,
            false,
            BigDecimal.ZERO,
            "0.00%"
        );

        // --- RAT-01: Sharpe Ratio (3Y) ---
        registerRule(
            "RAT-01",
            "LT",
            numericOps,
            1.0,
            false,
            BigDecimal.valueOf(0.50),
            "0.50"
        );

        // --- RAT-02: Sortino Ratio (3Y) ---
        registerRule(
            "RAT-02",
            "LT",
            numericOps,
            1.0,
            false,
            BigDecimal.valueOf(0.70),
            "0.70"
        );

        // --- MKT-01: Beta (3Y) ---
        registerRule(
            "MKT-01",
            "GT",
            numericOps,
            1.0,
            false,
            BigDecimal.valueOf(1.20),
            "1.20"
        );

        // --- MKT-02: Downside Beta (3Y) ---
        registerRule(
            "MKT-02",
            "GT",
            numericOps,
            1.0,
            false,
            BigDecimal.valueOf(1.10),
            "1.10"
        );

        // --- CONC-01: Top 5 Holdings Concentration ---
        registerRule(
            "CONC-01",
            "GT",
            numericOps,
            1.0,
            false,
            BigDecimal.valueOf(35.0),
            "35.00%"
        );

        // --- PRT-01 / CONC-02 / HOLDINGS_CONCENTRATION_TOP10 ---
        registerRule(
            "PRT-01",
            "ABS_CHANGE_GT",
            numericOps,
            1.0,
            false,
            BigDecimal.valueOf(3.0),
            "3.00%"
        );

        // --- PORTFOLIO_DRIFT (alias: PORTFOLIO_ALLOCATION_DRIFT) ---
        registerRule(
            "PORTFOLIO_DRIFT",
            "GT",
            numericOps,
            1.0,
            false,
            BigDecimal.valueOf(5.0),
            "5.00%"
        );

        // --- PORTFOLIO_UNVALUED_POSITIONS ---
        registerRule(
            "PORTFOLIO_UNVALUED_POSITIONS",
            "GT",
            numericOps,
            1.0,
            false,
            BigDecimal.ZERO,
            "0 positions"
        );

        // --- NAV_FRESHNESS ---
        registerRule(
            "NAV_FRESHNESS",
            "GT",
            freshnessOps,
            1.0,
            false,
            BigDecimal.valueOf(4),
            "4 days"
        );

        // --- HOLDINGS_FRESHNESS ---
        registerRule(
            "HOLDINGS_FRESHNESS",
            "GT",
            freshnessOps,
            1.0,
            false,
            BigDecimal.valueOf(40),
            "40 days"
        );

        // --- UNCLASSIFIED_EXPOSURE ---
        registerRule(
            "UNCLASSIFIED_EXPOSURE",
            "GT",
            numericOps,
            1.0,
            false,
            BigDecimal.valueOf(5.0),
            "5.00%"
        );
    }

    private void registerRule(
        String metricCode,
        String defaultOperator,
        List<String> supportedOperators,
        Double scaleFactor,
        Boolean isNegativeConvention,
        BigDecimal candidateThresholdNumeric,
        String candidateThresholdDisplay
    ) {
        String canonicalCode = authoritativeRegistry.resolveCanonicalCode(metricCode);
        AuthoritativeMetricDefinitionDto auth = authoritativeRegistry.getDefinition(canonicalCode)
            .orElseThrow(() -> new IllegalStateException("Metric code " + metricCode + " must be defined in AuthoritativeMetricRegistryService"));

        MonitoringMetricConfigDto cfg = new MonitoringMetricConfigDto(
            canonicalCode,
            auth.canonicalName(),
            auth.categoryLabel() != null ? auth.categoryLabel() : auth.analyticalDimension(),
            auth.unit() != null ? auth.unit() : "PERCENTAGE",
            scaleFactor,
            isNegativeConvention,
            defaultOperator,
            supportedOperators,
            candidateThresholdNumeric,
            candidateThresholdDisplay,
            auth.financialMeaning(),
            auth.governanceStatus()
        );

        configs.put(canonicalCode, cfg);
        if (auth.aliases() != null) {
            for (String alias : auth.aliases()) {
                if (alias != null && !alias.isBlank()) {
                    configs.put(alias.trim().toUpperCase(), cfg);
                }
            }
        }
    }
}
