package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;

public record ConsolidatedMarketCapExposureDto(
    String category,
    BigDecimal consolidatedWeightPct,
    int securitiesCount,
    String classificationProvenance
) {}
