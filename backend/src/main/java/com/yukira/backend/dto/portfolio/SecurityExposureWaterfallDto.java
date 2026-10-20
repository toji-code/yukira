package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

/**
 * Security-level active exposure waterfall entry decomposing sector and industry exposures.
 * Adheres strictly to the three-category epistemic rule:
 * 1. COMPARABLE_ACTIVE_EXPOSURE: Both fund weight and benchmark weight are authentically verified.
 * 2. BENCHMARK_MEMBER_WEIGHT_UNKNOWN: Benchmark constituent whose weight is undisclosed; active spread is strictly null.
 * 3. OFF_BENCHMARK_HOLDING: Security demonstrably absent from a verified complete benchmark membership snapshot.
 * 4. UNRESOLVED_MEMBERSHIP: Missing ISIN or unmapped security; cannot be classified as off-benchmark.
 */
public record SecurityExposureWaterfallDto(
    Long securityId,
    String companyName,
    String isin,
    String symbol,
    String parentSectorName,
    String industryName,
    BigDecimal fundWeightPct,
    BigDecimal benchmarkWeightPct,
    BigDecimal activeSpreadPct,
    String exposureCategory,
    String membershipStatus,
    String classificationProvenance,
    String mappingStatus,
    String inclusionReason
) {}
