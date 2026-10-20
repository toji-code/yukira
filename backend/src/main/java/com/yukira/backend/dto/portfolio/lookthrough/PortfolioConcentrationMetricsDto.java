package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;

public record PortfolioConcentrationMetricsDto(
    BigDecimal top5WeightPct,
    BigDecimal top10WeightPct,
    BigDecimal herfindahlHirschmanIndex,
    String hhiInterpretation,
    int uniqueSecuritiesCount,
    int sharedSecuritiesCount,
    BigDecimal sharedSecuritiesWeightPct,
    BigDecimal disclosedEquityCoveragePct,
    BigDecimal cashAndEquivalentsWeightPct,
    BigDecimal unclassifiedWeightPct
) {}
