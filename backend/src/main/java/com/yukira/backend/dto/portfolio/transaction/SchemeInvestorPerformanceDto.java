package com.yukira.backend.dto.portfolio.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SchemeInvestorPerformanceDto(
    Long schemeOptionId,
    String schemeName,
    String planType,
    String optionType,
    String amfiCode,
    String isin,
    BigDecimal currentUnits,
    BigDecimal latestNav,
    LocalDate navDate,
    BigDecimal currentMarketValue,
    BigDecimal totalInvestedCapital,
    BigDecimal totalRedeemedCapital,
    BigDecimal totalDividendIncome,
    BigDecimal remainingCostBasis,
    BigDecimal realizedGainOrLoss,
    BigDecimal unrealizedGainOrLoss,
    BigDecimal totalEconomicGain,
    BigDecimal investorXirrAnnualized,
    String xirrStatus, // AVAILABLE, INSUFFICIENT_CASH_FLOWS, INSUFFICIENT_HISTORY, MISSING_ENDING_VALUATION, NO_VALID_ROOT, NON_CONVERGENT
    String xirrStatusMessage,
    Integer cashFlowCount,
    LocalDate firstCashFlowDate,
    LocalDate lastCashFlowDate,
    BigDecimal fundNavCagr3Y,
    String fundCagrContextNote,
    String accountingMethod
) {}

