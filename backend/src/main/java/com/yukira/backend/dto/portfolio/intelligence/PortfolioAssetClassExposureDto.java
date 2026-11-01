package com.yukira.backend.dto.portfolio.intelligence;

import java.math.BigDecimal;

public record PortfolioAssetClassExposureDto(
    String assetClassName,
    BigDecimal exposureValue,
    BigDecimal portfolioWeightPct,
    String description
) {}
