package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Deterministic fund exposure to a specific sub-industry / industry group within a parent macro-sector.
 */
public record FundIndustryExposureDto(
    String industryName,
    String parentSectorName,
    String classificationLevel,
    BigDecimal fundWeightPct,
    int holdingCount,
    String classificationSource,
    String classificationProvenance,
    LocalDate effectiveDate,
    OffsetDateTime availabilityTime,
    BigDecimal classificationCoveragePct,
    List<String> topHoldings
) {}
