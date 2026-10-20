package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Disentangles the Three Sources of Apparent Change (AGENTS.md):
 * 1. FUND_PORTFOLIO_CHANGE: Differences in disclosed fund holdings or weights.
 * 2. BENCHMARK_MEMBERSHIP_CHANGE: Constituents entering or exiting the benchmark.
 * 3. CLASSIFICATION_COVERAGE_CHANGE: Semi-annual AMFI reclassifications, mapping improvements, or snapshot date mismatches.
 */
public record ApparentChangeBreakdownDto(
    LocalDate earlierDate,
    LocalDate laterDate,
    String changeCategory, // "FUND_PORTFOLIO_CHANGE", "BENCHMARK_MEMBERSHIP_CHANGE", "CLASSIFICATION_COVERAGE_CHANGE"
    String isin,
    String symbol,
    String securityName,
    String description,
    BigDecimal weightImpactPct,
    String sourceEvidence // e.g. "Disclosed Monthly Holdings", "NSE Indices Benchmark Reconstitution", "AMFI Semi-Annual Market Cap Ranking"
) {}
