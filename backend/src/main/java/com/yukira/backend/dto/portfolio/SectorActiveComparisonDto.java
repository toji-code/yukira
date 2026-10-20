package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

/**
 * Sector active comparison between fund and benchmark.
 * Epistemic constraint: definitiveActiveWeightPct is strictly null when benchmark constituent weights are partially disclosed.
 */
public record SectorActiveComparisonDto(
    String sectorName,
    BigDecimal fundWeightPct,
    BigDecimal benchmarkDisclosedWeightPct,
    BigDecimal definitiveActiveWeightPct,
    String comparisonStatus,
    String statusReason,
    int fundHoldingCount,
    int benchmarkConstituentCount,
    int benchmarkWeightedConstituentCount
) {}
