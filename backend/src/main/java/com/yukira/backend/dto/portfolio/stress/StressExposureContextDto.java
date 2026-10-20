package com.yukira.backend.dto.portfolio.stress;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Observed holdings exposure context (sector & market-cap) closest to the stress period,
 * with explicit temporal mismatch distance and epistemic warnings.
 */
public record StressExposureContextDto(
    LocalDate observationDate,
    long daysMismatchFromEpisodeEnd,
    String comparabilityState,
    BigDecimal largeCapWeightPct,
    BigDecimal midCapWeightPct,
    BigDecimal smallCapWeightPct,
    BigDecimal cashWeightPct,
    BigDecimal unclassifiedWeightPct,
    List<SectorExposureContextItemDto> topSectors,
    String epistemicWarning
) {}
