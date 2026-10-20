package com.yukira.backend.dto.portfolio.mcap;

import java.math.BigDecimal;

/**
 * Holding that shifted market-cap classification categories between observation periods
 * due to an authentic AMFI semi-annual ranking update.
 */
public record MarketCapReclassificationItemDto(
    String isin,
    String securityName,
    String earlierCategory,
    Integer earlierRank,
    String laterCategory,
    Integer laterRank,
    BigDecimal earlierWeightPct,
    BigDecimal laterWeightPct,
    BigDecimal weightDeltaPct,
    String rankingPeriodChange,
    String explanation
) {}
