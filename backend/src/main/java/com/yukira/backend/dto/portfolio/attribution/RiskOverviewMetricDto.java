package com.yukira.backend.dto.portfolio.attribution;

import java.math.BigDecimal;

public record RiskOverviewMetricDto(
    String metricCode,
    String metricName,
    BigDecimal numericValue,
    String formattedValue,
    String unit,
    String description,
    String availabilityState
) {}
