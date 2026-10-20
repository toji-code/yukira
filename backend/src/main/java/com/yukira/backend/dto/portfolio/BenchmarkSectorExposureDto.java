package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

/**
 * Benchmark sector exposure details reflecting authentic disclosed weights and constituent coverage.
 * Epistemic rule: Disclosed weight is a lower bound when tail constituents are unweighted.
 */
public record BenchmarkSectorExposureDto(
    String sectorName,
    BigDecimal disclosedWeightPct,
    int weightedConstituentsCount,
    int totalConstituentsCount,
    BigDecimal constituentDisclosedCoveragePct,
    String weightStatus
) {}
