package com.yukira.backend.dto.portfolio.performance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Scheme-level performance contribution and financial outcome.
 *
 * Epistemic Rules:
 * 1. Net Financial Contribution (Rs) = Realized Gains + Unrealized Gains + Dividend Income - Recorded Charges.
 * 2. Contribution Percentage explicitly exposes the denominator value and description (e.g. portfolio absolute gains).
 * 3. Never confuses scheme market value, return percentage, absolute profit/loss, and contribution to portfolio total.
 * 4. Incomplete histories are flagged with contributionStatus = PARTIAL_HISTORY or UNRECONCILED.
 */
public record SchemePerformanceContributionDto(
    Long schemeOptionId,
    String schemeName,
    String planType,
    String optionType,
    String amfiCode,
    String isin,
    BigDecimal currentUnits,
    BigDecimal currentNav,
    LocalDate navEffectiveDate,
    Boolean isNavStale,
    BigDecimal currentMarketValue,
    BigDecimal investedCapital,
    BigDecimal redeemedCapital,
    BigDecimal remainingCostBasis,
    BigDecimal realizedGains,
    BigDecimal unrealizedGains,
    BigDecimal dividendIncome,
    BigDecimal reinvestedDividends,
    BigDecimal recordedCharges,
    BigDecimal netFinancialContribution,
    BigDecimal contributionToPortfolioAbsoluteGainsPercent,
    BigDecimal contributionDenominator,
    String contributionDenominatorDescription,
    Integer contributionRank,
    String contributionStatus, // COMPLETE, PARTIAL_HISTORY, UNRECONCILED, UNVALUED
    LocalDate transactionCoverageStartDate,
    LocalDate transactionCoverageEndDate,
    Integer transactionCount,
    String holdingsReconciliationStatus,
    BigDecimal schemeXirr,
    String schemeXirrStatus,
    BigDecimal fundNavCagr,
    String fundNavCagrPeriod,
    LocalDate cagrStartDate,
    LocalDate cagrEndDate,
    BigDecimal returnDifference, // Investor XIRR - Fund CAGR
    String returnDifferenceContext
) {}

