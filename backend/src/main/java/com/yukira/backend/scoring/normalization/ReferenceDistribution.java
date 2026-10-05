package com.yukira.backend.scoring.normalization;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Encapsulates the versioned, auditable peer population reference distribution
 * used to normalize a single analytical metric into a standardized 0–100 score.
 */
public record ReferenceDistribution(
    String metricCode,
    String metricName,
    String unit,
    Direction direction,
    BigDecimal targetValue,
    BigDecimal min,
    BigDecimal p10,
    BigDecimal p25,
    BigDecimal median,
    BigDecimal p75,
    BigDecimal p90,
    BigDecimal max,
    String populationCode,
    boolean isProvisional,
    String notes
) implements Serializable {

    public static ReferenceDistribution higherIsBetter(
        String metricCode,
        String metricName,
        String unit,
        double min,
        double p10,
        double p25,
        double median,
        double p75,
        double p90,
        double max,
        String populationCode,
        boolean isProvisional,
        String notes
    ) {
        return new ReferenceDistribution(
            metricCode,
            metricName,
            unit,
            Direction.HIGHER_IS_BETTER,
            null,
            BigDecimal.valueOf(min),
            BigDecimal.valueOf(p10),
            BigDecimal.valueOf(p25),
            BigDecimal.valueOf(median),
            BigDecimal.valueOf(p75),
            BigDecimal.valueOf(p90),
            BigDecimal.valueOf(max),
            populationCode,
            isProvisional,
            notes
        );
    }

    public static ReferenceDistribution lowerIsBetter(
        String metricCode,
        String metricName,
        String unit,
        double min,
        double p10,
        double p25,
        double median,
        double p75,
        double p90,
        double max,
        String populationCode,
        boolean isProvisional,
        String notes
    ) {
        return new ReferenceDistribution(
            metricCode,
            metricName,
            unit,
            Direction.LOWER_IS_BETTER,
            null,
            BigDecimal.valueOf(min),
            BigDecimal.valueOf(p10),
            BigDecimal.valueOf(p25),
            BigDecimal.valueOf(median),
            BigDecimal.valueOf(p75),
            BigDecimal.valueOf(p90),
            BigDecimal.valueOf(max),
            populationCode,
            isProvisional,
            notes
        );
    }

    public static ReferenceDistribution targetValue(
        String metricCode,
        String metricName,
        String unit,
        double target,
        double toleranceHalfScore,
        String populationCode,
        boolean isProvisional,
        String notes
    ) {
        return new ReferenceDistribution(
            metricCode,
            metricName,
            unit,
            Direction.TARGET_VALUE,
            BigDecimal.valueOf(target),
            BigDecimal.valueOf(target - toleranceHalfScore * 2),
            BigDecimal.valueOf(target - toleranceHalfScore),
            BigDecimal.valueOf(target - toleranceHalfScore * 0.5),
            BigDecimal.valueOf(target),
            BigDecimal.valueOf(target + toleranceHalfScore * 0.5),
            BigDecimal.valueOf(target + toleranceHalfScore),
            BigDecimal.valueOf(target + toleranceHalfScore * 2),
            populationCode,
            isProvisional,
            notes
        );
    }
}
