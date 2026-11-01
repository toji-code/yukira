package com.yukira.backend.dto.portfolio.performance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SchemeDividendItemDto(
    Long schemeOptionId,
    String schemeName,
    BigDecimal cashDividendPayoutTotal,
    BigDecimal dividendReinvestmentTotal,
    Integer totalDividendEventsCount,
    LocalDate lastDividendDate,
    String sourceCoverage
) {}

