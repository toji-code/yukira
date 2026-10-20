package com.yukira.backend.dto.portfolio.attribution;

import java.math.BigDecimal;
import java.util.Map;

public record AttributionSectorExposureDto(
    String sectorName,
    BigDecimal portfolioWeightPct,
    Map<String, BigDecimal> fundContributions
) {}
