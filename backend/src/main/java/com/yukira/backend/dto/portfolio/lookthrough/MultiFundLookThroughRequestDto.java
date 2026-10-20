package com.yukira.backend.dto.portfolio.lookthrough;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record MultiFundLookThroughRequestDto(
    List<ResearchPortfolioFundItemDto> funds,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    String rebalanceMethod
) {}
