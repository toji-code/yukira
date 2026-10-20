package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

/**
 * Sub-industry active comparison between fund and benchmark within a parent macro-sector.
 * Epistemic constraint: definitiveActiveWeightPct is strictly null when benchmark constituent weights are partially disclosed.
 */
public record IndustryActiveComparisonDto(
    String industryName,
    String parentSectorName,
    BigDecimal fundWeightPct,
    BigDecimal benchmarkDisclosedWeightPct,
    BigDecimal definitiveActiveWeightPct,
    String comparisonStatus,
    String statusReason,
    int fundHoldingCount,
    int benchmarkConstituentCount,
    int benchmarkWeightedConstituentCount
) {}
