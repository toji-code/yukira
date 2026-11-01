package com.yukira.backend.dto.portfolio.performance;

import java.math.BigDecimal;
import java.util.List;

/**
 * Disclosed dividend income and reinvestment contribution.
 *
 * Epistemic Rules:
 * 1. Dividend payouts represent actual cash receipts received by the investor.
 * 2. Dividend reinvestments represent internal unit creations and must NOT be counted
 *    as an additional external contribution or duplicate cash income.
 * 3. Never infers dividend distributions from NAV movements; only reflects authoritative disclosures.
 */
public record DividendIncomeContributionDto(
    BigDecimal totalCashDividendPayouts,
    BigDecimal totalDividendReinvestments,
    Integer totalDividendEventsCount,
    Integer cashPayoutEventsCount,
    Integer reinvestmentEventsCount,
    List<SchemeDividendItemDto> schemeBreakdown,
    String accountingRuleNote
) {}

