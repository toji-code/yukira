package com.yukira.backend.dto.portfolio.performance;

import java.math.BigDecimal;

public record MonthlyCashFlowAggregateDto(
    String yearMonth, // e.g. "2024-01"
    BigDecimal lumpSumContributions,
    BigDecimal sipContributions,
    BigDecimal totalContributions,
    BigDecimal redemptions,
    BigDecimal dividendPayouts,
    BigDecimal internalSwitches,
    BigDecimal netExternalCashFlow
) {}

