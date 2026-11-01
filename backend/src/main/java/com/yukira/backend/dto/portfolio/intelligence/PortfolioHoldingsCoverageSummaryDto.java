package com.yukira.backend.dto.portfolio.intelligence;

import java.math.BigDecimal;

public record PortfolioHoldingsCoverageSummaryDto(
    BigDecimal totalPortfolioMarketValue,
    BigDecimal usableHoldingsMarketValue,
    BigDecimal lookThroughCoveragePct,
    BigDecimal staleDisclosuresValue,
    BigDecimal staleDisclosuresPct,
    BigDecimal incompleteDisclosuresValue,
    BigDecimal incompleteDisclosuresPct,
    BigDecimal missingDisclosuresValue,
    BigDecimal missingDisclosuresPct,
    BigDecimal undisclosedResidualValue,
    BigDecimal undisclosedResidualPct,
    String coverageState,
    String coverageNotice
) {}
