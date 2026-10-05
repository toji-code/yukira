package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioDriftAnalysisDto(
    String status,
    BigDecimal totalTargetWeightPercentage,
    BigDecimal totalCurrentWeightPercentage,
    BigDecimal totalAbsoluteDriftPercentagePoints,
    BigDecimal coveredPortfolioValue,
    List<ItemDriftDto> itemDrifts,
    List<UnmappedHoldingDto> unmappedHoldings,
    List<UnmappedTargetDto> unmappedTargets,
    List<String> investigationQuestions,
    List<String> limitations,
    List<String> disclaimers
) {
    public record ItemDriftDto(
        String targetType,
        Long schemeOptionId,
        String categoryName,
        String displayName,
        BigDecimal targetWeightPercentage,
        BigDecimal currentWeightPercentage,
        BigDecimal driftPercentagePoints,
        BigDecimal absoluteDriftPercentagePoints,
        String driftDirection,
        String mappingState,
        BigDecimal currentValue,
        BigDecimal yukiraScore,
        String scoreStatus,
        String isin,
        String amfiCode
    ) {}

    public record UnmappedHoldingDto(
        Long schemeOptionId,
        String fundName,
        String category,
        BigDecimal currentWeightPercentage,
        BigDecimal currentValue
    ) {}

    public record UnmappedTargetDto(
        String targetType,
        Long schemeOptionId,
        String categoryName,
        String displayName,
        BigDecimal targetWeightPercentage
    ) {}
}
