package com.yukira.backend.dto.portfolio.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public record PortfolioInvestorPerformanceResponseDto(
    BigDecimal totalInvestedCapital,
    BigDecimal totalCurrentPortfolioValue,
    BigDecimal totalRedeemedCapital,
    BigDecimal totalDividendIncome,
    BigDecimal totalRealizedGainOrLoss,
    BigDecimal totalRemainingCostBasis,
    BigDecimal totalUnrealizedGainOrLoss,
    BigDecimal totalEconomicGain,
    BigDecimal portfolioXirrAnnualized,
    String portfolioXirrStatus, // AVAILABLE, INSUFFICIENT_CASH_FLOWS, INSUFFICIENT_HISTORY, INCOMPLETE_PORTFOLIO_VALUATION, NO_VALID_ROOT, NON_CONVERGENT
    String portfolioXirrStatusMessage,
    Integer externalCashFlowCount,
    LocalDate firstExternalCashFlowDate,
    LocalDate lastExternalCashFlowDate,
    String twrStatus, // UNAVAILABLE
    String twrLimitationReason,
    String accountingMethod,
    String dayCountConvention,
    LocalDate asOfDate,
    ZonedDateTime calculatedAt,
    List<SchemeInvestorPerformanceDto> schemePerformances,
    List<String> performanceLimitations
) {}

