package com.yukira.backend.dto.portfolio.attribution;

import java.math.BigDecimal;
import java.util.Map;

public record AttributionMarketCapExposureDto(
    String category,
    BigDecimal portfolioWeightPct,
    Map<String, BigDecimal> fundContributions
) {}
