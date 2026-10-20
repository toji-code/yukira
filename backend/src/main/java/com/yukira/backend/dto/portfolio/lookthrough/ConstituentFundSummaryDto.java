package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConstituentFundSummaryDto(
    Long schemeOptionId,
    String fundName,
    String amfiCode,
    String isin,
    BigDecimal rawWeight,
    BigDecimal normalizedWeightPct,
    LocalDate observationDate,
    int holdingsCount,
    BigDecimal disclosedEquityCoveragePct,
    BigDecimal cashWeightPct,
    int temporalMismatchDays,
    String status
) {}
