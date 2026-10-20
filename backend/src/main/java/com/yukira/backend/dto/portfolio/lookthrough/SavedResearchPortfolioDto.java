package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;
import java.util.List;

public record SavedResearchPortfolioDto(
    String portfolioName,
    List<ResearchPortfolioFundItemDto> funds,
    BigDecimal totalAllocationPct,
    boolean isValid
) {}
