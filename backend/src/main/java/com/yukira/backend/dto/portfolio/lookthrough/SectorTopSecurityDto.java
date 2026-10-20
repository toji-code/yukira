package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;

/**
 * Top contributing security within a consolidated macro-sector.
 */
public record SectorTopSecurityDto(
    String canonicalName,
    String isin,
    BigDecimal consolidatedWeightPct,
    int fundCount
) {}
