package com.yukira.backend.dto.portfolio.attribution;

import java.math.BigDecimal;
import java.util.List;

public record RiskInterpretationPanelDto(
    List<String> primaryRiskDrivers,
    List<String> diversificationInsights,
    String concentrationAssessment,
    BigDecimal riskHhi,
    List<String> coverageLimitations,
    List<String> investigationPrompts
) {}
