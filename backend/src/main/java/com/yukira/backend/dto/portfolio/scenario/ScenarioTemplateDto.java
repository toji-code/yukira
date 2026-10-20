package com.yukira.backend.dto.portfolio.scenario;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Predefined scenario template for the Portfolio Scenario Lab.
 */
public record ScenarioTemplateDto(
    String templateId,
    String name,
    String category,
    String scenarioType,
    String description,
    BigDecimal broadMarketShockPct,
    BigDecimal largeCapShockPct,
    BigDecimal midCapShockPct,
    BigDecimal smallCapShockPct,
    Map<String, BigDecimal> sectorShocks
) {}
