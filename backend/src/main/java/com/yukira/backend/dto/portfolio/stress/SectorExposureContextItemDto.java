package com.yukira.backend.dto.portfolio.stress;

import java.math.BigDecimal;

public record SectorExposureContextItemDto(
    String macroSector,
    BigDecimal weightPct
) {}
