package com.yukira.backend.dto.portfolio.scenario;

import com.yukira.backend.dto.portfolio.lookthrough.ResearchPortfolioFundItemDto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request payload to compare two scenarios under identical portfolio and market snapshots.
 */
public record ScenarioComparisonRequestDto(
    List<ResearchPortfolioFundItemDto> funds,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    String benchmarkCode,
    ScenarioLabRequestDto scenarioA,
    ScenarioLabRequestDto scenarioB
) {}
