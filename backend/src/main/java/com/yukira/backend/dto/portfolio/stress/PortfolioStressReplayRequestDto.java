package com.yukira.backend.dto.portfolio.stress;

import com.yukira.backend.dto.portfolio.lookthrough.ResearchPortfolioFundItemDto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request payload for replaying a historical market episode or custom date range.
 */
public record PortfolioStressReplayRequestDto(
    List<ResearchPortfolioFundItemDto> funds,
    String episodeId,
    LocalDate startDate,
    LocalDate endDate,
    String benchmarkCode,
    OffsetDateTime knowledgeCutoff
) {}
