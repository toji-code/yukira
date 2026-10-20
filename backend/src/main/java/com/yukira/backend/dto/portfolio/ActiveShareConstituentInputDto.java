package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

/**
 * Inspectable constituent-level calculation input for Point-in-Time Active Share.
 * Shows exact reported portfolio weight, verified benchmark weight (or null if unweighted),
 * absolute discrepancy, benchmark membership status, and market-cap classification.
 */
public record ActiveShareConstituentInputDto(
    String isin,
    String securityName,
    BigDecimal portfolioWeightPct,
    BigDecimal benchmarkWeightPct,
    boolean isBenchmarkMember,
    BigDecimal absoluteDiscrepancyPct,
    String mappingStatus, // "VERIFIED", "UNMAPPED", "AMBIGUOUS"
    String marketCapCategory // "LARGE_CAP", "MID_CAP", "SMALL_CAP", "UNCLASSIFIED"
) {}
