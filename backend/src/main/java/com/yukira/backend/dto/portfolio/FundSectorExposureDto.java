package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.util.List;

/**
 * Deterministic fund sector exposure details with holding count and classification provenance.
 */
public record FundSectorExposureDto(
    String sectorName,
    BigDecimal fundWeightPct,
    int holdingCount,
    String classificationProvenance,
    List<String> topHoldings
) {}
