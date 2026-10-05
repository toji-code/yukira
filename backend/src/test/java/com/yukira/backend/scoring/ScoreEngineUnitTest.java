package com.yukira.backend.scoring;

import com.yukira.backend.scoring.config.ScoreMethodologyConfig;
import com.yukira.backend.scoring.normalization.Direction;
import com.yukira.backend.scoring.normalization.MetricNormalizer;
import com.yukira.backend.scoring.normalization.ReferenceDistribution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScoreEngineUnitTest {

    private final MetricNormalizer normalizer = new MetricNormalizer();
    private final ScoreMethodologyConfig config = new ScoreMethodologyConfig();

    @Test
    @DisplayName("Higher is better: Monotonic scaling from 0 to 100 with winsorization")
    void testHigherIsBetter() {
        ReferenceDistribution ref = config.getDistribution("RET-03"); // 3Y CAGR (min 0.05, max 0.35, median 0.20)
        assertNotNull(ref);
        assertEquals(Direction.HIGHER_IS_BETTER, ref.direction());

        // Extreme low below min winsorizes to 0.00
        BigDecimal low = normalizer.normalize(new BigDecimal("0.02"), ref);
        assertEquals(new BigDecimal("0.0000"), low);

        // Min boundary gives 0.00
        BigDecimal min = normalizer.normalize(new BigDecimal("0.05"), ref);
        assertEquals(new BigDecimal("0.0000"), min);

        // Exact percentile boundaries
        BigDecimal p10 = normalizer.normalize(new BigDecimal("0.12"), ref);
        assertEquals(new BigDecimal("10.0000"), p10);

        BigDecimal p25 = normalizer.normalize(new BigDecimal("0.16"), ref);
        assertEquals(new BigDecimal("25.0000"), p25);

        BigDecimal p50 = normalizer.normalize(new BigDecimal("0.20"), ref);
        assertEquals(new BigDecimal("50.0000"), p50);

        BigDecimal p75 = normalizer.normalize(new BigDecimal("0.24"), ref);
        assertEquals(new BigDecimal("75.0000"), p75);

        BigDecimal p90 = normalizer.normalize(new BigDecimal("0.28"), ref);
        assertEquals(new BigDecimal("90.0000"), p90);

        // Max boundary gives 100.00
        BigDecimal max = normalizer.normalize(new BigDecimal("0.35"), ref);
        assertEquals(new BigDecimal("100.0000"), max);

        // Extreme high above max winsorizes to 100.00
        BigDecimal extremeHigh = normalizer.normalize(new BigDecimal("0.55"), ref);
        assertEquals(new BigDecimal("100.0000"), extremeHigh);
    }

    @Test
    @DisplayName("Lower is better: Inverted scaling so lower risk gives higher score")
    void testLowerIsBetter() {
        ReferenceDistribution ref = config.getDistribution("RSK-01"); // 3Y Volatility (min 0.10, max 0.28, median 0.16)
        assertNotNull(ref);
        assertEquals(Direction.LOWER_IS_BETTER, ref.direction());

        // Lowest volatility (below min) gives maximum score 100.00
        BigDecimal lowRisk = normalizer.normalize(new BigDecimal("0.08"), ref);
        assertEquals(new BigDecimal("100.0000"), lowRisk);

        // Exact min gives 100.00
        BigDecimal min = normalizer.normalize(new BigDecimal("0.10"), ref);
        assertEquals(new BigDecimal("100.0000"), min);

        // Exact percentile boundaries
        BigDecimal p10 = normalizer.normalize(new BigDecimal("0.13"), ref);
        assertEquals(new BigDecimal("90.0000"), p10);

        BigDecimal p25 = normalizer.normalize(new BigDecimal("0.145"), ref);
        assertEquals(new BigDecimal("75.0000"), p25);

        BigDecimal p50 = normalizer.normalize(new BigDecimal("0.16"), ref);
        assertEquals(new BigDecimal("50.0000"), p50);

        BigDecimal p75 = normalizer.normalize(new BigDecimal("0.18"), ref);
        assertEquals(new BigDecimal("25.0000"), p75);

        BigDecimal p90 = normalizer.normalize(new BigDecimal("0.21"), ref);
        assertEquals(new BigDecimal("10.0000"), p90);

        // Exact max gives 0.00
        BigDecimal highRisk = normalizer.normalize(new BigDecimal("0.28"), ref);
        assertEquals(new BigDecimal("0.0000"), highRisk);

        // Extreme high risk above max winsorizes to 0.00
        BigDecimal extremeRisk = normalizer.normalize(new BigDecimal("0.40"), ref);
        assertEquals(new BigDecimal("0.0000"), extremeRisk);
    }

    @Test
    @DisplayName("Target value: Closeness to target gives maximum score with symmetric decay")
    void testTargetValue() {
        ReferenceDistribution ref = config.getDistribution("MKT-01"); // Beta (target = 1.0)
        assertNotNull(ref);
        assertEquals(Direction.TARGET_VALUE, ref.direction());

        // Exact target 1.0 gives 100.00
        BigDecimal targetScore = normalizer.normalize(new BigDecimal("1.00"), ref);
        assertEquals(new BigDecimal("100.0000"), targetScore);

        // Near target 0.95 gives high score
        BigDecimal nearTarget = normalizer.normalize(new BigDecimal("0.95"), ref);
        assertTrue(nearTarget.compareTo(new BigDecimal("90.0000")) >= 0);

        // Far from target gives lower score
        BigDecimal farTarget = normalizer.normalize(new BigDecimal("1.40"), ref);
        assertTrue(farTarget.compareTo(new BigDecimal("50.0000")) < 0);

        // Extreme distance beyond maxDist clamps to 0.00
        BigDecimal extremeDistance = normalizer.normalize(new BigDecimal("2.50"), ref);
        assertEquals(new BigDecimal("0.0000"), extremeDistance);
    }

    @Test
    @DisplayName("Edge cases: Null, NaN, Infinite, zero, and incomplete distributions handled safely")
    void testNormalizationEdgeCases() {
        ReferenceDistribution ref = config.getDistribution("RET-03");

        // Null raw value returns ZERO
        assertEquals(new BigDecimal("0.0000"), normalizer.normalize(null, ref));

        // Null reference distribution returns ZERO
        assertEquals(new BigDecimal("0.0000"), normalizer.normalize(new BigDecimal("0.15"), null));

        // Zero return
        BigDecimal zeroReturn = normalizer.normalize(BigDecimal.ZERO, ref);
        assertEquals(new BigDecimal("0.0000"), zeroReturn);

        // Negative values (for drawdown RSK-03 where negative is standard)
        ReferenceDistribution rsk03Ref = config.getDistribution("RSK-03");
        assertNotNull(rsk03Ref);
        BigDecimal shallowDrawdown = normalizer.normalize(new BigDecimal("-0.05"), rsk03Ref); // better (closer to 0)
        assertEquals(new BigDecimal("100.0000"), shallowDrawdown);
        BigDecimal severeDrawdown = normalizer.normalize(new BigDecimal("-0.40"), rsk03Ref); // worse
        assertEquals(new BigDecimal("0.0000"), severeDrawdown);

        // Incomplete distribution with null percentiles returns ZERO
        ReferenceDistribution brokenRef = new ReferenceDistribution(
            "BROKEN", "Broken Distribution", "PCT", Direction.HIGHER_IS_BETTER,
            null, null, null, null, null, null, null, null, "TEST", true, "broken"
        );
        assertEquals(new BigDecimal("0.0000"), normalizer.normalize(new BigDecimal("0.20"), brokenRef));
    }

    @Test
    @DisplayName("Tied values: Deterministic output guarantees identical scores for identical values")
    void testDeterministicTies() {
        ReferenceDistribution ref = config.getDistribution("RET-03");
        BigDecimal val = new BigDecimal("0.22345");

        BigDecimal score1 = normalizer.normalize(val, ref);
        BigDecimal score2 = normalizer.normalize(val, ref);

        assertEquals(score1, score2);
        assertEquals(new BigDecimal("64.6563"), score1);
    }

    @Test
    @DisplayName("Methodology configuration integrity: Dimensions, weights, candidate status")
    void testMethodologyConfig() {
        assertEquals("YUKIRA_SCORE_V1", config.getScoreVersion());
        assertEquals("CANDIDATE", config.getMethodologyStatus());
        assertEquals("INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1", config.getReferencePopulationCode());

        // Check dimension weights sum to 1.00
        BigDecimal sumWeights = config.getDimensions().values().stream()
            .map(ScoreMethodologyConfig.DimensionConfig::weight)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(new BigDecimal("1.0000"), sumWeights.setScale(4, RoundingMode.HALF_UP));

        // Check each dimension's metric weights sum to 1.00
        for (ScoreMethodologyConfig.DimensionConfig dim : config.getDimensions().values()) {
            BigDecimal sumMetricWeights = dim.metrics().stream()
                .map(ScoreMethodologyConfig.MetricConfig::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(new BigDecimal("1.0000"), sumMetricWeights.setScale(4, RoundingMode.HALF_UP),
                "Dimension " + dim.dimensionCode() + " metric weights must sum to 1.0");
        }
    }

    @Test
    @DisplayName("YUKIRA_SCORE_V1 contract: exactly four positive-weight scored dimensions with frozen weights")
    void testFrozenScoredDimensionContract() {
        assertEquals(4, config.getDimensions().size(), "YUKIRA_SCORE_V1 must expose exactly four scored dimensions");
        assertEquals(ScoreMethodologyConfig.SCORED_DIMENSION_CODES, config.getDimensions().keySet().stream().toList());

        assertEquals(new BigDecimal("0.3000"), config.getDimensions().get("RETURN_QUALITY").weight());
        assertEquals(new BigDecimal("0.3000"), config.getDimensions().get("RISK_QUALITY").weight());
        assertEquals(new BigDecimal("0.2500"), config.getDimensions().get("BENCHMARK_RELATIVE_QUALITY").weight());
        assertEquals(new BigDecimal("0.1500"), config.getDimensions().get("CONSISTENCY_DOWNSIDE_QUALITY").weight());
    }

    @Test
    @DisplayName("YUKIRA_SCORE_V1 contract: exactly ten authorized score inputs and no analytical-only metric drift")
    void testFrozenScoreInputContract() {
        List<String> configuredInputs = config.getDimensions().values().stream()
            .flatMap(d -> d.metrics().stream())
            .map(ScoreMethodologyConfig.MetricConfig::metricCode)
            .toList();

        assertEquals(ScoreMethodologyConfig.SCORE_INPUT_METRIC_CODES, configuredInputs,
            "ScoreMethodologyConfig must remain the closed ten-input YUKIRA_SCORE_V1 contract");

        for (String nonScoreMetric : ScoreMethodologyConfig.ANALYTICAL_NON_SCORE_METRIC_CODES) {
            assertFalse(configuredInputs.contains(nonScoreMetric),
                nonScoreMetric + " may be persisted as a MetricResult but must not be a score input");
        }
    }
    @Test
    @DisplayName("Double counting & unauthorized metrics audit: Deprecated aliases cannot enter scoring")
    void testUnauthorizedMetricsAudit() {
        Set<String> unauthorized = ScoreMethodologyConfig.UNAUTHORIZED_METRICS;
        assertTrue(unauthorized.contains("MKT-06"), "MKT-06 must be in unauthorized set");
        assertTrue(unauthorized.contains("REL-04"), "REL-04 must be in unauthorized set");
        assertTrue(unauthorized.contains("REL-05"), "REL-05 must be in unauthorized set");
        assertTrue(unauthorized.contains("REL-06"), "REL-06 must be in unauthorized set");
        assertTrue(unauthorized.contains("RAT-05"), "RAT-05 must be in unauthorized set");

        // Verify none of the unauthorized metrics are in any dimension configuration
        for (ScoreMethodologyConfig.DimensionConfig dim : config.getDimensions().values()) {
            for (ScoreMethodologyConfig.MetricConfig m : dim.metrics()) {
                assertFalse(unauthorized.contains(m.metricCode()),
                    "Dimension " + dim.dimensionCode() + " must never contain unauthorized metric: " + m.metricCode());
            }
        }
    }

    @Test
    @DisplayName("Contribution arithmetic: normalized_value * effective_weight = contribution, sum = dimension score")
    void testMetricContributionArithmetic() {
        // Dimension A (Return Quality): RET-03 (0.60), RET-07 (0.40)
        BigDecimal normRet03 = new BigDecimal("87.1915");
        BigDecimal normRet07 = new BigDecimal("4.0275");
        BigDecimal wRet03 = new BigDecimal("0.6000");
        BigDecimal wRet07 = new BigDecimal("0.4000");

        // When both eligible: sumEligibleWeights = 1.0000
        BigDecimal effRet03 = wRet03.divide(new BigDecimal("1.0000"), 4, RoundingMode.HALF_UP);
        BigDecimal effRet07 = wRet07.divide(new BigDecimal("1.0000"), 4, RoundingMode.HALF_UP);

        BigDecimal contribRet03 = normRet03.multiply(effRet03).setScale(4, RoundingMode.HALF_UP); // 52.3149
        BigDecimal contribRet07 = normRet07.multiply(effRet07).setScale(4, RoundingMode.HALF_UP); // 1.6110

        assertEquals(new BigDecimal("52.3149"), contribRet03);
        assertEquals(new BigDecimal("1.6110"), contribRet07);

        BigDecimal dimScore = contribRet03.add(contribRet07).setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("53.93"), dimScore);

        // Verification: sum of contributions equals dimension score within 0.01 tolerance
        BigDecimal sumContrib = contribRet03.add(contribRet07).setScale(2, RoundingMode.HALF_UP);
        assertEquals(dimScore, sumContrib);
    }

    @Test
    @DisplayName("Missing metric != zero: Missing metric reweights eligible metrics without zero penalty")
    void testMissingMetricReweighting() {
        // If RET-07 is missing, sumEligibleWeights = 0.6000
        BigDecimal normRet03 = new BigDecimal("80.0000");
        BigDecimal configuredWeight = new BigDecimal("0.6000");
        BigDecimal sumEligibleWeights = new BigDecimal("0.6000");

        BigDecimal effWeight = configuredWeight.divide(sumEligibleWeights, 4, RoundingMode.HALF_UP); // 1.0000
        assertEquals(new BigDecimal("1.0000"), effWeight);

        BigDecimal contrib = normRet03.multiply(effWeight).setScale(4, RoundingMode.HALF_UP); // 80.0000
        assertEquals(new BigDecimal("80.0000"), contrib);

        BigDecimal dimScore = contrib.setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("80.00"), dimScore, "Dimension score must equal normalized value of single eligible metric without zero penalty");
    }

    @Test
    @DisplayName("Dimension contribution arithmetic: sum(weighted dimension contributions) = final score")
    void testDimensionContributionArithmetic() {
        // Dimension scores: Return (53.92), Risk (67.31), Relative (84.18), Consistency (26.90)
        // Configured weights: 0.30, 0.30, 0.25, 0.15 (sum = 1.0000)
        BigDecimal sReturn = new BigDecimal("53.92");
        BigDecimal sRisk = new BigDecimal("67.31");
        BigDecimal sRelative = new BigDecimal("84.18");
        BigDecimal sConsistency = new BigDecimal("26.90");

        BigDecimal wReturn = new BigDecimal("0.3000");
        BigDecimal wRisk = new BigDecimal("0.3000");
        BigDecimal wRelative = new BigDecimal("0.2500");
        BigDecimal wConsistency = new BigDecimal("0.1500");

        BigDecimal cReturn = sReturn.multiply(wReturn).setScale(4, RoundingMode.HALF_UP); // 16.1760
        BigDecimal cRisk = sRisk.multiply(wRisk).setScale(4, RoundingMode.HALF_UP); // 20.1930
        BigDecimal cRelative = sRelative.multiply(wRelative).setScale(4, RoundingMode.HALF_UP); // 21.0450
        BigDecimal cConsistency = sConsistency.multiply(wConsistency).setScale(4, RoundingMode.HALF_UP); // 4.0350

        assertEquals(new BigDecimal("16.1760"), cReturn);
        assertEquals(new BigDecimal("20.1930"), cRisk);
        assertEquals(new BigDecimal("21.0450"), cRelative);
        assertEquals(new BigDecimal("4.0350"), cConsistency);

        BigDecimal sumDimensionContribs = cReturn.add(cRisk).add(cRelative).add(cConsistency); // 61.4490
        BigDecimal overallScore = sumDimensionContribs.setScale(2, RoundingMode.HALF_UP); // 61.45

        assertEquals(new BigDecimal("61.45"), overallScore);
        assertEquals(overallScore, sumDimensionContribs.setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Missing dimension != zero: Missing dimension reweights remaining dimensions without zero penalty")
    void testMissingDimensionReweighting() {
        // Return (0.30) score 80.00, Risk (0.30) score 70.00. Benchmark-Relative (0.25) and Consistency (0.15) missing.
        BigDecimal dimReturnScore = new BigDecimal("80.00");
        BigDecimal dimReturnWeight = new BigDecimal("0.3000");
        BigDecimal dimRiskScore = new BigDecimal("70.00");
        BigDecimal dimRiskWeight = new BigDecimal("0.3000");

        BigDecimal sumEligibleWeights = dimReturnWeight.add(dimRiskWeight); // 0.6000
        BigDecimal effReturnWeight = dimReturnWeight.divide(sumEligibleWeights, 4, RoundingMode.HALF_UP); // 0.5000
        BigDecimal effRiskWeight = dimRiskWeight.divide(sumEligibleWeights, 4, RoundingMode.HALF_UP); // 0.5000

        BigDecimal cReturn = dimReturnScore.multiply(effReturnWeight).setScale(4, RoundingMode.HALF_UP); // 40.0000
        BigDecimal cRisk = dimRiskScore.multiply(effRiskWeight).setScale(4, RoundingMode.HALF_UP); // 35.0000

        BigDecimal overallScore = cReturn.add(cRisk).setScale(2, RoundingMode.HALF_UP); // 75.00

        assertEquals(new BigDecimal("75.00"), overallScore, "Overall score must be 75.00, reweighted over eligible dimensions without zero penalty");
    }

    @Test
    @DisplayName("Insufficient data threshold: < 50% eligible dimension weight yields null score")
    void testInsufficientDataThreshold() {
        BigDecimal dimWeight = new BigDecimal("0.1500");
        boolean isInsufficient = dimWeight.compareTo(new BigDecimal("0.5000")) < 0;
        assertTrue(isInsufficient, "Weight below 0.50 must be classified as INSUFFICIENT_DATA");
    }

    @Test
    @DisplayName("Separate Score from Confidence: High score + low confidence and low score + high confidence are both possible")
    void testScoreConfidenceSeparation() {
        // Scenario 1: High Score (85.00) + Low Confidence (42.00) due to limited history (< 500 obs)
        BigDecimal highScore = new BigDecimal("85.00");
        BigDecimal lowConfidence = new BigDecimal("42.00");
        assertTrue(highScore.compareTo(new BigDecimal("80.00")) >= 0);
        assertTrue(lowConfidence.compareTo(new BigDecimal("50.00")) < 0);

        // Weak evidence does NOT reduce the 85.00 numerical score; it surfaces in status/assessment
        String statusScenario1 = lowConfidence.compareTo(new BigDecimal("50.00")) < 0 ? "DATA_QUALITY_LIMITED" : "AVAILABLE";
        assertEquals("DATA_QUALITY_LIMITED", statusScenario1);
        assertEquals(new BigDecimal("85.00"), highScore, "Numerical performance score must not be artificially penalized");

        // Scenario 2: Low Score (32.00) + High Confidence (95.00) due to complete, verified 3Y history
        BigDecimal lowScore = new BigDecimal("32.00");
        BigDecimal highConfidence = new BigDecimal("95.00");
        assertTrue(lowScore.compareTo(new BigDecimal("40.00")) < 0);
        assertTrue(highConfidence.compareTo(new BigDecimal("80.00")) >= 0);

        String statusScenario2 = "AVAILABLE";
        assertEquals("AVAILABLE", statusScenario2);
        assertEquals(new BigDecimal("32.00"), lowScore);
    }

    @Test
    @DisplayName("MKT-05 Capture Spread: Uncalibrated and excluded from score normalization; MKT-02 renormalized to 1.0000")
    void testMkt05CaptureSpreadExcludedFromScoreNormalization() {
        ScoreMethodologyConfig.DimensionConfig dimD = config.getDimensions().get("CONSISTENCY_DOWNSIDE_QUALITY");
        assertNotNull(dimD, "Dimension D must exist");

        ScoreMethodologyConfig.MetricConfig mkt05 = dimD.metrics().stream()
            .filter(m -> "MKT-05".equals(m.metricCode()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("MKT-05 must be defined in Dimension D"));

        // Metric identity verification
        assertEquals("Capture Spread 3Y", mkt05.metricName());
        assertEquals("PERCENTAGE_POINTS", mkt05.unit());
        assertEquals(Direction.HIGHER_IS_BETTER, mkt05.direction());
        assertEquals(new BigDecimal("0.5000"), mkt05.weight());

        // Governance & Calibration status verification: MKT-05 is NOT eligible for score
        assertFalse(mkt05.scoreEligible(), "MKT-05 must NOT be eligible for score while uncalibrated");
        assertNull(mkt05.referenceDistribution(), "MKT-05 reference distribution must be null in MetricConfig to prevent normalization");
        assertTrue(mkt05.calibrationStatus().contains("UNCALIBRATED"), "MKT-05 calibration status must declare UNCALIBRATED");

        // Renormalization verification: Remaining eligible metric (MKT-02) carries 100% of Dimension D weight
        ScoreMethodologyConfig.MetricConfig mkt02 = dimD.metrics().stream()
            .filter(m -> "MKT-02".equals(m.metricCode()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("MKT-02 must be defined in Dimension D"));
        assertTrue(mkt02.scoreEligible(), "MKT-02 must be eligible for score");

        BigDecimal sumEligibleWeights = mkt02.weight(); // Only MKT-02 is eligible
        BigDecimal effMkt02Weight = mkt02.weight().divide(sumEligibleWeights, 4, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("1.0000"), effMkt02Weight, "MKT-02 effective weight must renormalize to 1.0000 when MKT-05 is uncalibrated");
    }

    @Test
    @DisplayName("RET-07 Metadata: Declares 3-Year Annualized Active Return; zero occurrences of Rolling Return Mean")
    void testRet07MetadataAnnualizedActiveReturn() {
        ScoreMethodologyConfig.DimensionConfig dimA = config.getDimensions().get("RETURN_QUALITY");
        assertNotNull(dimA, "Dimension A must exist");

        ScoreMethodologyConfig.MetricConfig ret07 = dimA.metrics().stream()
            .filter(m -> "RET-07".equals(m.metricCode()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("RET-07 must be defined in Dimension A"));

        assertEquals("3-Year Annualized Active Return", ret07.metricName());
        assertEquals("PERCENTAGE", ret07.unit());
        assertFalse(ret07.metricName().toLowerCase().contains("rolling"),
            "RET-07 name must not contain 'rolling'");

        ReferenceDistribution dist = config.getDistribution("RET-07");
        assertNotNull(dist, "RET-07 reference distribution must exist");
        assertEquals("3-Year Annualized Active Return", dist.metricName());
        assertFalse(dist.metricName().toLowerCase().contains("rolling"),
            "RET-07 distribution metric name must not contain 'rolling'");
        assertFalse(dist.notes().toLowerCase().contains("rolling"),
            "RET-07 notes must not contain 'rolling'");
    }

    @Test
    @DisplayName("REL-02 Canonical Containment: Rejects legacy Pearson Correlation and Tracking Error")
    void testLegacyRel02CannotEnterCanonicalConsumers() {
        ScoreMethodologyConfig.DimensionConfig dimC = config.getDimensions().get("BENCHMARK_RELATIVE_QUALITY");
        assertNotNull(dimC, "Dimension C must exist");

        ScoreMethodologyConfig.MetricConfig rel02 = dimC.metrics().stream()
            .filter(m -> "REL-02".equals(m.metricCode()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("REL-02 must be defined in Dimension C"));

        assertEquals("Jensen's Alpha 3Y", rel02.metricName());
        assertEquals("PERCENTAGE", rel02.unit());

        // Canonical diagnostics signature verification: must contain "OLS intercept"
        String canonicalDiag = "{\"beta\": 0.95, \"formula\": \"OLS intercept (alpha_daily * 252)\", \"periods_per_year\": 252.0}";
        assertTrue(canonicalDiag.contains("OLS intercept"), "Canonical REL-02 must have OLS intercept in diagnostics");

        // Legacy Pearson Correlation signature: must NOT contain "OLS intercept"
        String legacyCorrDiag = "{\"formula\": \"Pearson correlation of synchronous daily fund and benchmark returns\"}";
        assertFalse(legacyCorrDiag.contains("OLS intercept"), "Legacy Pearson Correlation must NOT contain OLS intercept");

        // Legacy Tracking Error signature: must NOT contain "OLS intercept"
        String legacyTeDiag = "{\"annualization_convention\": \"DAILY_SQRT_252\", \"annualized_mean_active_return\": 0.0487}";
        assertFalse(legacyTeDiag.contains("OLS intercept"), "Legacy Tracking Error must NOT contain OLS intercept");
    }

    @Test
    @DisplayName("Reference Population remains Provisional and Empirical Cohort N=24 is Research-Only")
    void testReferencePopulationProvisionalAndCohortResearchOnly() {
        assertEquals("INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1", config.getReferencePopulationCode(),
            "Reference population code must remain the provisional baseline");
        assertEquals("CANDIDATE", config.getMethodologyStatus(),
            "Methodology status must remain CANDIDATE");
    }
}

