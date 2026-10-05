package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioRiskEvidenceSummaryDto(
    String coverageState,
    int totalHoldingCount,
    int valuedHoldingCount,
    int holdingsWithRiskEvidenceCount,
    BigDecimal coveredPortfolioValue,
    BigDecimal totalValuedPortfolioValue,
    BigDecimal coveredPortfolioWeight,
    int volatilityCoverageCount,
    int downsideSemideviationCoverageCount,
    int drawdownCoverageCount,
    int betaCoverageCount,
    int downsideBetaCoverageCount,
    List<HoldingRiskEvidenceDto> holdingRiskEvidences,
    List<String> investigationPrompts,
    List<String> dataLimitations
) {}
