package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Historical sector drift change between two consecutive portfolio observations.
 * Disentangles observed shifts into portfolio weight adjustment, security entry/exit,
 * disclosure scope expansion, classification shift, or unresolved discrepancies.
 */
public record SectorDriftChangeDto(
    LocalDate earlierDate,
    LocalDate laterDate,
    String sectorName,
    BigDecimal earlierWeightPct,
    BigDecimal laterWeightPct,
    BigDecimal deltaWeightPct,
    String changeDriver,
    String driverExplanation,
    int earlierHoldingCount,
    int laterHoldingCount
) {}
