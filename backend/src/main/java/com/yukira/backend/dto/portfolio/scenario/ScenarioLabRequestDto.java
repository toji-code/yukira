package com.yukira.backend.dto.portfolio.scenario;

import com.yukira.backend.dto.portfolio.lookthrough.ResearchPortfolioFundItemDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request payload for Portfolio Scenario Lab calculation.
 */
public record ScenarioLabRequestDto(
    List<ResearchPortfolioFundItemDto> funds,
    String scenarioType, // BROAD_MARKET, MARKET_CAP, SECTOR, COMBINED
    String scenarioName,
    String benchmarkCode,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    BigDecimal broadMarketShockPct,
    BigDecimal largeCapShockPct,
    BigDecimal midCapShockPct,
    BigDecimal smallCapShockPct,
    BigDecimal cashShockPct,
    Map<String, BigDecimal> sectorShocks,
    String unmodeledTreatment, // EXPLICIT_ZERO_RETURN, UNMODELED_RESIDUAL
    String overlapRule // MUTUALLY_EXCLUSIVE_PARTITION, ADDITIVE_EXCESS_TILTS
) {}
