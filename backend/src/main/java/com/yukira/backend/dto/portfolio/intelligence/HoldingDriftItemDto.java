package com.yukira.backend.dto.portfolio.intelligence;

import java.math.BigDecimal;

public record HoldingDriftItemDto(
    String securityName,
    String isin,
    String sector,
    BigDecimal previousExposurePct,
    BigDecimal latestExposurePct,
    BigDecimal deltaPercentagePoints,
    String direction
) {}
