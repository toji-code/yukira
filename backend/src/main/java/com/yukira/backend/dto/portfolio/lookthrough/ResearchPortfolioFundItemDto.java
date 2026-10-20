package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;

public record ResearchPortfolioFundItemDto(
    Long schemeOptionId,
    BigDecimal allocationWeight
) {}
