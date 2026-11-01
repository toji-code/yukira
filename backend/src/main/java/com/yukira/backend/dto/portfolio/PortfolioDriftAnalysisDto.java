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
    public boolean isTargetConfigured() {
        return !"NO_TARGETS".equalsIgnoreCase(status);
    }

    public BigDecimal maxDriftPercentage() {
        if (itemDrifts == null || itemDrifts.isEmpty()) return BigDecimal.ZERO;
        return itemDrifts.stream()
            .map(ItemDriftDto::absoluteDriftPercentagePoints)
            .filter(java.util.Objects::nonNull)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    }

    public String maxDriftCategoryName() {
        if (itemDrifts == null || itemDrifts.isEmpty()) return "N/A";
        return itemDrifts.stream()
            .filter(d -> d.absoluteDriftPercentagePoints() != null)
            .max(java.util.Comparator.comparing(ItemDriftDto::absoluteDriftPercentagePoints))
            .map(ItemDriftDto::displayName)
            .orElse("N/A");
    }

    public BigDecimal totalDriftMagnitude() {
        return totalAbsoluteDriftPercentagePoints != null ? totalAbsoluteDriftPercentagePoints : BigDecimal.ZERO;
    }

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
