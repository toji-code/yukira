package com.yukira.backend.dto.portfolio.snapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

public record PortfolioValuationHistoryDto(
    Long id,
    Long portfolioId,
    LocalDate valuationDate,
    ZonedDateTime knowledgeCutoff,
    BigDecimal totalMarketValue,
    BigDecimal totalCostBasis,
    BigDecimal unrealizedGainLoss,
    Integer valuedPositionsCount,
    Integer unvaluedPositionsCount,
    String valuationSourceContext,
    ZonedDateTime createdAt
) {}

