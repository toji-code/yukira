package com.yukira.backend.dto.portfolio.intelligence;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioConcentrationAnalysisDto(
    List<ConsolidatedPortfolioSecurityDto> top5Securities,
    List<ConsolidatedPortfolioSecurityDto> top10Securities,
    List<ConsolidatedPortfolioSecurityDto> top20Securities,
    BigDecimal top5ExposureValue,
    BigDecimal top5ExposurePct,
    BigDecimal top10ExposureValue,
    BigDecimal top10ExposurePct,
    BigDecimal top20ExposureValue,
    BigDecimal top20ExposurePct,
    String largestIndividualSecurityName,
    BigDecimal largestIndividualSecurityValue,
    BigDecimal largestIndividualSecurityPct,
    BigDecimal hhi,
    BigDecimal effectiveNumberOfConstituents,
    String concentrationClassification,
    String calculationUniverseNotice
) {}
