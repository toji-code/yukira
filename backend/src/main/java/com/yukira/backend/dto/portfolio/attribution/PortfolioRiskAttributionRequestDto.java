package com.yukira.backend.dto.portfolio.attribution;

import com.yukira.backend.dto.portfolio.lookthrough.ResearchPortfolioFundItemDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PortfolioRiskAttributionRequestDto(
    List<ResearchPortfolioFundItemDto> funds,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    String benchmarkCode
) {}
