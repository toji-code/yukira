package com.yukira.backend.scoring.normalization;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Deterministic normalizer mapping raw continuous financial metrics into 0–100 scores
 * using an auditable, peer-referenced piecewise distribution model.
 *
 * Implements strict directionality (HIGHER_IS_BETTER, LOWER_IS_BETTER, TARGET_VALUE),
 * explicit outlier winsorization, and deterministic tie handling.
 */
@Component
public class MetricNormalizer {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100.0000");

    /**
     * Normalizes a raw metric value deterministically according to the reference distribution.
     * Guaranteed output is bounded strictly in [0.0000, 100.0000].
     */
    public BigDecimal normalize(BigDecimal rawValue, ReferenceDistribution ref) {
        if (rawValue == null || ref == null) {
            return ZERO;
        }

        double val = rawValue.doubleValue();
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return ZERO;
        }

        if (ref.min() == null || ref.max() == null || ref.median() == null) {
            return ZERO;
        }

        Direction dir = ref.direction() != null ? ref.direction() : Direction.HIGHER_IS_BETTER;

        if (dir == Direction.TARGET_VALUE) {
            return normalizeTarget(val, ref);
        } else if (dir == Direction.LOWER_IS_BETTER) {
            return normalizeLowerIsBetter(val, ref);
        } else {
            return normalizeHigherIsBetter(val, ref);
        }
    }

    private BigDecimal normalizeHigherIsBetter(double val, ReferenceDistribution ref) {
        double min = ref.min().doubleValue();
        double p10 = ref.p10().doubleValue();
        double p25 = ref.p25().doubleValue();
        double p50 = ref.median().doubleValue();
        double p75 = ref.p75().doubleValue();
        double p90 = ref.p90().doubleValue();
        double max = ref.max().doubleValue();

        double score;
        if (val <= min) {
            score = 0.0;
        } else if (val < p10) {
            score = interpolate(val, min, p10, 0.0, 10.0);
        } else if (val < p25) {
            score = interpolate(val, p10, p25, 10.0, 25.0);
        } else if (val < p50) {
            score = interpolate(val, p25, p50, 25.0, 50.0);
        } else if (val < p75) {
            score = interpolate(val, p50, p75, 50.0, 75.0);
        } else if (val < p90) {
            score = interpolate(val, p75, p90, 75.0, 90.0);
        } else if (val < max) {
            score = interpolate(val, p90, max, 90.0, 100.0);
        } else {
            score = 100.0;
        }

        return clampAndScale(score);
    }

    private BigDecimal normalizeLowerIsBetter(double val, ReferenceDistribution ref) {
        double min = ref.min().doubleValue();
        double p10 = ref.p10().doubleValue();
        double p25 = ref.p25().doubleValue();
        double p50 = ref.median().doubleValue();
        double p75 = ref.p75().doubleValue();
        double p90 = ref.p90().doubleValue();
        double max = ref.max().doubleValue();

        double score;
        if (val <= min) {
            score = 100.0;
        } else if (val < p10) {
            score = interpolate(val, min, p10, 100.0, 90.0);
        } else if (val < p25) {
            score = interpolate(val, p10, p25, 90.0, 75.0);
        } else if (val < p50) {
            score = interpolate(val, p25, p50, 75.0, 50.0);
        } else if (val < p75) {
            score = interpolate(val, p50, p75, 50.0, 25.0);
        } else if (val < p90) {
            score = interpolate(val, p75, p90, 25.0, 10.0);
        } else if (val < max) {
            score = interpolate(val, p90, max, 10.0, 0.0);
        } else {
            score = 0.0;
        }

        return clampAndScale(score);
    }

    private BigDecimal normalizeTarget(double val, ReferenceDistribution ref) {
        double target = ref.targetValue() != null ? ref.targetValue().doubleValue() : 1.0;
        double diff = Math.abs(val - target);
        double maxDist = Math.max(
            Math.abs(ref.max().doubleValue() - target),
            Math.abs(ref.min().doubleValue() - target)
        );

        if (maxDist <= 0.000001) {
            return diff <= 0.000001 ? HUNDRED : ZERO;
        }

        double score = Math.max(0.0, 100.0 * (1.0 - (diff / maxDist)));
        return clampAndScale(score);
    }

    private double interpolate(double x, double x0, double x1, double y0, double y1) {
        if (Math.abs(x1 - x0) < 0.0000001) {
            return y0;
        }
        return y0 + ((x - x0) / (x1 - x0)) * (y1 - y0);
    }

    private BigDecimal clampAndScale(double score) {
        if (Double.isNaN(score) || score <= 0.0) {
            return ZERO;
        }
        if (score >= 100.0) {
            return HUNDRED;
        }
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }
}
