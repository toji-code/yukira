package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;

public record FundSectorContributionDto(
    Long schemeOptionId,
    String fundName,
    BigDecimal sectorWeightInFundPct,
    BigDecimal effectiveContributionPct
) {}
