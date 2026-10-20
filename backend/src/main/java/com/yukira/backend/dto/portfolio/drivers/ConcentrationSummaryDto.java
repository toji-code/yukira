package com.yukira.backend.dto.portfolio.drivers;

import java.math.BigDecimal;

/**
 * Summary metrics of portfolio-level concentration across underlying securities and asset categories.
 */
public record ConcentrationSummaryDto(
    BigDecimal top5WeightPct,
    BigDecimal top10WeightPct,
    BigDecimal largestSecurityWeightPct,
    String largestSecurityName,
    int distinctSecuritiesCount,
    int sharedSecuritiesCount,
    BigDecimal sharedSecuritiesWeightPct,
    BigDecimal disclosedEquityCoveragePct,
    BigDecimal unclassifiedWeightPct,
    BigDecimal cashAndEquivalentsWeightPct,
    BigDecimal herfindahlHirschmanIndex,
    String hhiInterpretation
) {}
