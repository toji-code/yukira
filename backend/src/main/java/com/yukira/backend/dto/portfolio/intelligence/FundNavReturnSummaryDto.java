package com.yukira.backend.dto.portfolio.intelligence;

import java.math.BigDecimal;

public record FundNavReturnSummaryDto(
    Long schemeOptionId,
    String fundName,
    BigDecimal portfolioWeightPct,
    BigDecimal ret1Y,
    BigDecimal ret3Y,
    BigDecimal ret5Y,
    String returnStatus
) {}
