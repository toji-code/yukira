package com.yukira.backend.dto.portfolio.intelligence;

import java.math.BigDecimal;

public record SectorDriftItemDto(
    String sectorName,
    BigDecimal previousExposurePct,
    BigDecimal latestExposurePct,
    BigDecimal deltaPercentagePoints,
    String direction
) {}
