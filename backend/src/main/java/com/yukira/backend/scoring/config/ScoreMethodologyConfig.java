package com.yukira.backend.scoring.config;

import com.yukira.backend.scoring.normalization.Direction;
import com.yukira.backend.scoring.normalization.ReferenceDistribution;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Machine-readable, versioned methodology configuration for YUKIRA Analytical Quality Score V1.
 *
 * GOVERNANCE STATUS: CANDIDATE / RESEARCH
 * NOTE: Weights and reference distributions are PROVISIONAL candidate parameters pending multi-cycle empirical study.
 * They are NOT empirical full-universe percentiles and NOT peer-ranked against the complete universe.
 * IMPLEMENTED != VALIDATED != APPROVED.
 */
@Component
public class ScoreMethodologyConfig {

    public static final String SCORE_VERSION = "YUKIRA_SCORE_V1";
    public static final String METHODOLOGY_STATUS = "CANDIDATE";
    public static final String REFERENCE_POPULATION_CODE = "INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1";
    public static final LocalDate EFFECTIVE_DATE = LocalDate.of(2024, 1, 15);
    public static final int MIN_REQUIRED_OBSERVATIONS = 700;

    /**
     * Closed score-input set for the currently authorized executable YUKIRA_SCORE_V1.
     * Analytical MetricResults outside this list may be persisted, but must not influence
     * the 0-100 score unless a future methodology change is explicitly authorized.
     */
    public static final List<String> SCORE_INPUT_METRIC_CODES = List.of(
        "RET-03", "RET-07",
        "RSK-01", "RSK-02", "RSK-03",
        "REL-02", "RAT-04",
        "MKT-01", "MKT-05", "MKT-02"
    );

    /**
     * Analytical metrics currently allowed to exist as MetricResults without being
     * YUKIRA_SCORE_V1 score inputs.
     */
    public static final List<String> ANALYTICAL_NON_SCORE_METRIC_CODES = List.of(
        "REL-01", "REL-03",
        "RAT-01", "RAT-02", "RAT-03",
        "RET-02",
        "MKT-03", "MKT-04",
        "RSK-04", "RSK-05", "RSK-06", "RSK-07"
    );

    public static final List<String> SCORED_DIMENSION_CODES = List.of(
        "RETURN_QUALITY",
        "RISK_QUALITY",
        "BENCHMARK_RELATIVE_QUALITY",
        "CONSISTENCY_DOWNSIDE_QUALITY"
    );

    /**
     * Strictly unauthorized metrics and deprecated aliases forbidden from scoring.
     */
    public static final Set<String> UNAUTHORIZED_METRICS = Set.of(
        "MKT-06", // Deprecated alias for Upside Beta
        "REL-04", // Deprecated alias for Downside Beta
        "REL-05", // Unauthorized Upside Beta
        "REL-06", // Unauthorized Active Return in profile
        "RAT-05"  // Candidate Sortino Ratio not approved for V1 score
    );

    public record MetricConfig(
        String metricCode,
        String metricName,
        String unit,
        BigDecimal weight,
        Direction direction,
        int minObservations,
        ReferenceDistribution referenceDistribution,
        boolean scoreEligible,
        String calibrationStatus
    ) {
        public MetricConfig(
            String metricCode,
            String metricName,
            String unit,
            BigDecimal weight,
            Direction direction,
            int minObservations,
            ReferenceDistribution referenceDistribution
        ) {
            this(
                metricCode, metricName, unit, weight, direction, minObservations, referenceDistribution,
                referenceDistribution != null,
                referenceDistribution != null ? (referenceDistribution.isProvisional() ? "PROVISIONAL" : "CALIBRATED") : "UNCALIBRATED / NOT ELIGIBLE FOR SCORE"
            );
        }
    }

    public record DimensionConfig(
        String dimensionCode,
        String dimensionName,
        BigDecimal weight,
        List<MetricConfig> metrics
    ) {}

    private final Map<String, DimensionConfig> dimensions = new LinkedHashMap<>();
    private final Map<String, ReferenceDistribution> distributions = new HashMap<>();

    public ScoreMethodologyConfig() {
        initMethodology();
    }

    private void initMethodology() {
        // --- 1. Provisional Reference Distributions (Candidate parameters for Indian Equity Flexi Cap) ---
        // Explicitly PROVISIONAL: NOT empirical full-universe percentiles; pending empirical validation.

        // RET-03: 3Y CAGR (annualized return)
        distributions.put("RET-03", ReferenceDistribution.higherIsBetter(
            "RET-03", "3-Year Compound Annual Growth Rate (CAGR)", "PERCENTAGE",
            0.05, 0.12, 0.16, 0.20, 0.24, 0.28, 0.35,
            REFERENCE_POPULATION_CODE, true, "Provisional flexi-cap peer return distribution"
        ));

        // RET-07: 3Y Annualized Active Return
        distributions.put("RET-07", ReferenceDistribution.higherIsBetter(
            "RET-07", "3-Year Annualized Active Return", "PERCENTAGE",
            0.05, 0.11, 0.15, 0.19, 0.23, 0.27, 0.34,
            REFERENCE_POPULATION_CODE, true, "Provisional 3-year annualized active return distribution"
        ));

        // RSK-01: 3Y Annualized Volatility
        distributions.put("RSK-01", ReferenceDistribution.lowerIsBetter(
            "RSK-01", "3-Year Annualized Volatility", "PERCENTAGE",
            0.10, 0.13, 0.145, 0.16, 0.18, 0.21, 0.28,
            REFERENCE_POPULATION_CODE, true, "Provisional peer volatility distribution"
        ));

        // RSK-02: 3Y Downside Semideviation
        distributions.put("RSK-02", ReferenceDistribution.lowerIsBetter(
            "RSK-02", "3-Year Downside Semideviation", "PERCENTAGE",
            0.06, 0.08, 0.095, 0.11, 0.125, 0.15, 0.20,
            REFERENCE_POPULATION_CODE, true, "Provisional downside semideviation distribution"
        ));

        // RSK-03: 3Y Maximum Drawdown (stored as negative decimal e.g. -0.16, so shallower drawdown is better)
        // Values closer to 0 (or lower loss depth) are better
        distributions.put("RSK-03", ReferenceDistribution.higherIsBetter(
            "RSK-03", "3-Year Maximum Drawdown", "PERCENTAGE",
            -0.35, -0.25, -0.20, -0.16, -0.13, -0.10, -0.05,
            REFERENCE_POPULATION_CODE, true, "Provisional drawdown depth distribution (-0.05 is better than -0.35)"
        ));

        // REL-02: Jensen's Alpha 3Y
        distributions.put("REL-02", ReferenceDistribution.higherIsBetter(
            "REL-02", "Jensen's Alpha 3Y", "PERCENTAGE",
            -0.08, -0.03, 0.00, 0.025, 0.055, 0.09, 0.15,
            REFERENCE_POPULATION_CODE, true, "Provisional annualized Jensen's Alpha distribution"
        ));

        // RAT-04: Information Ratio 3Y
        distributions.put("RAT-04", ReferenceDistribution.higherIsBetter(
            "RAT-04", "Information Ratio 3Y", "RATIO",
            -1.0, -0.5, 0.0, 0.4, 0.8, 1.3, 2.0,
            REFERENCE_POPULATION_CODE, true, "Provisional active efficiency Information Ratio distribution"
        ));

        // MKT-01: Beta 3Y (target = 1.0)
        distributions.put("MKT-01", ReferenceDistribution.targetValue(
            "MKT-01", "Beta 3Y", "RATIO",
            1.00, 0.25,
            REFERENCE_POPULATION_CODE, true, "Provisional systematic beta target distribution (target=1.0)"
        ));

        // MKT-05: Capture Spread 3Y
        // DEFECT: Quantiles are ratio-shaped. Requires empirical recalibration before production approval.
        // UNCALIBRATED / NOT ELIGIBLE FOR SCORE: Excluded from active scoring until authorized calibration exists.
        distributions.put("MKT-05", ReferenceDistribution.higherIsBetter(
            "MKT-05", "Capture Spread 3Y", "PERCENTAGE_POINTS",
            0.60, 0.85, 0.95, 1.05, 1.18, 1.30, 1.60,
            REFERENCE_POPULATION_CODE, true, "UNCALIBRATED / NOT ELIGIBLE FOR SCORE: Contains Up/Down Ratio quantiles but evaluates Capture Spread."
        ));

        // MKT-02: Downside Beta 3Y (lower downside beta protects capital in market drops)
        distributions.put("MKT-02", ReferenceDistribution.lowerIsBetter(
            "MKT-02", "Downside Beta 3Y", "RATIO",
            0.60, 0.80, 0.90, 0.98, 1.05, 1.15, 1.40,
            REFERENCE_POPULATION_CODE, true, "Provisional downside market sensitivity distribution"
        ));

        // --- 2. Dimension Configs ---

        // Dimension A: Return Quality (weight: 0.30)
        dimensions.put("RETURN_QUALITY", new DimensionConfig(
            "RETURN_QUALITY",
            "Return Quality",
            new BigDecimal("0.3000"),
            List.of(
                new MetricConfig("RET-03", "3-Year Compound Annual Growth Rate (CAGR)", "PERCENTAGE", new BigDecimal("0.6000"), Direction.HIGHER_IS_BETTER, 700, distributions.get("RET-03")),
                new MetricConfig("RET-07", "3-Year Annualized Active Return", "PERCENTAGE", new BigDecimal("0.4000"), Direction.HIGHER_IS_BETTER, 700, distributions.get("RET-07"))
            )
        ));

        // Dimension B: Risk Quality (weight: 0.30)
        dimensions.put("RISK_QUALITY", new DimensionConfig(
            "RISK_QUALITY",
            "Risk Quality",
            new BigDecimal("0.3000"),
            List.of(
                new MetricConfig("RSK-01", "3-Year Annualized Volatility", "PERCENTAGE", new BigDecimal("0.3500"), Direction.LOWER_IS_BETTER, 700, distributions.get("RSK-01")),
                new MetricConfig("RSK-02", "3-Year Downside Semideviation", "PERCENTAGE", new BigDecimal("0.3500"), Direction.LOWER_IS_BETTER, 700, distributions.get("RSK-02")),
                new MetricConfig("RSK-03", "3-Year Maximum Drawdown", "PERCENTAGE", new BigDecimal("0.3000"), Direction.HIGHER_IS_BETTER, 700, distributions.get("RSK-03"))
            )
        ));

        // Dimension C: Benchmark-Relative Quality (weight: 0.25)
        dimensions.put("BENCHMARK_RELATIVE_QUALITY", new DimensionConfig(
            "BENCHMARK_RELATIVE_QUALITY",
            "Benchmark-Relative Quality",
            new BigDecimal("0.2500"),
            List.of(
                new MetricConfig("REL-02", "Jensen's Alpha 3Y", "PERCENTAGE", new BigDecimal("0.4000"), Direction.HIGHER_IS_BETTER, 700, distributions.get("REL-02")),
                new MetricConfig("RAT-04", "Information Ratio 3Y", "RATIO", new BigDecimal("0.3500"), Direction.HIGHER_IS_BETTER, 700, distributions.get("RAT-04")),
                new MetricConfig("MKT-01", "Beta 3Y", "RATIO", new BigDecimal("0.2500"), Direction.TARGET_VALUE, 700, distributions.get("MKT-01"))
            )
        ));

        // Dimension D: Consistency / Downside Quality (weight: 0.15)
        // MKT-05 is explicitly uncalibrated and NOT eligible for score (ratio quantiles are invalid for Capture Spread).
        // It carries nominal weight 0.5000 in config, but its effective weight in score normalization is 0.0000,
        // allowing MKT-02 to renormalize to carry 100% of Dimension D weight.
        dimensions.put("CONSISTENCY_DOWNSIDE_QUALITY", new DimensionConfig(
            "CONSISTENCY_DOWNSIDE_QUALITY",
            "Consistency & Downside Quality",
            new BigDecimal("0.1500"),
            List.of(
                new MetricConfig(
                    "MKT-05", "Capture Spread 3Y", "PERCENTAGE_POINTS", new BigDecimal("0.5000"),
                    Direction.HIGHER_IS_BETTER, 700, null, false, "UNCALIBRATED / NOT ELIGIBLE FOR SCORE"
                ),
                new MetricConfig("MKT-02", "Downside Beta 3Y", "RATIO", new BigDecimal("0.5000"), Direction.LOWER_IS_BETTER, 700, distributions.get("MKT-02"))
            )
        ));

        // Strict validation: assert zero unauthorized metrics or deprecated aliases
        for (DimensionConfig dim : dimensions.values()) {
            for (MetricConfig m : dim.metrics()) {
                if (UNAUTHORIZED_METRICS.contains(m.metricCode())) {
                    throw new IllegalStateException("Unauthorized metric or deprecated alias configured in dimension " 
                        + dim.dimensionCode() + ": " + m.metricCode());
                }
            }
        }
    }

    public String getScoreVersion() { return SCORE_VERSION; }
    public String getMethodologyStatus() { return METHODOLOGY_STATUS; }
    public String getReferencePopulationCode() { return REFERENCE_POPULATION_CODE; }
    public LocalDate getEffectiveDate() { return EFFECTIVE_DATE; }
    public int getMinRequiredObservations() { return MIN_REQUIRED_OBSERVATIONS; }

    public Map<String, DimensionConfig> getDimensions() {
        return Collections.unmodifiableMap(dimensions);
    }

    public ReferenceDistribution getDistribution(String metricCode) {
        return distributions.get(metricCode);
    }
}
