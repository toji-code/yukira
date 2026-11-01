package com.yukira.backend.dto.portfolio.intelligence;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioPositionAllocationDto(
    Long schemeOptionId,
    String fundName,
    String amcName,
    String category,
    String planType,
    String optionType,
    BigDecimal units,
    BigDecimal nav,
    LocalDate navDate,
    BigDecimal marketValue,
    BigDecimal portfolioWeightPct,
    BigDecimal costBasis,
    BigDecimal investedAmount,
    String valuationState,
    String holdingsDataStatus,
    int disclosedHoldingsCount,
    BigDecimal disclosedWeightCoveragePct,
    BigDecimal undisclosedResidualWeightPct,
    LocalDate disclosureDate,
    String sourceAndProvenance
) {}
