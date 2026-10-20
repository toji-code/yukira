package com.yukira.backend.dto.portfolio.attribution;

import java.math.BigDecimal;

public record AttributionSecurityExposureDto(
    String securityName,
    String isin,
    String sector,
    String capCategory,
    BigDecimal portfolioWeightPct,
    int fundCount
) {}
