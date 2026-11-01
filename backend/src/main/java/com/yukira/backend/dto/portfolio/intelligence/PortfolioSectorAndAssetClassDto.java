package com.yukira.backend.dto.portfolio.intelligence;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioSectorAndAssetClassDto(
    List<PortfolioSectorExposureDto> sectors,
    BigDecimal unclassifiedSectorValue,
    BigDecimal unclassifiedSectorPct,
    List<PortfolioAssetClassExposureDto> assetClasses
) {}
