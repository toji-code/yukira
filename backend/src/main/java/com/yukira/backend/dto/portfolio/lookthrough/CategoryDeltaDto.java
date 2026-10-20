package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;

public record CategoryDeltaDto(
    String categoryName,
    BigDecimal baselineWeightPct,
    BigDecimal simulatedWeightPct,
    BigDecimal deltaWeightPct
) {}
