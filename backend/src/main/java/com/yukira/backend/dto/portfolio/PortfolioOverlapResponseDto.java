package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioOverlapResponseDto(
    Long schemeOptionIdA,
    String fundNameA,
    LocalDate portfolioDateA,
    Long schemeOptionIdB,
    String fundNameB,
    LocalDate portfolioDateB,
    BigDecimal overlapPercentage,
    String formattedOverlapPercentage,
    int commonSecurityCount,
    int holdingsCountA,
    int holdingsCountB,
    int uniqueHoldingsCountA,
    int uniqueHoldingsCountB,
    List<OverlappingSecurityDto> topOverlappingSecurities,
    String qualityState,
    List<String> limitations
) {
    public record OverlappingSecurityDto(
        String securityName,
        String isin,
        String sector,
        BigDecimal weightInA,
        String formattedWeightA,
        BigDecimal weightInB,
        String formattedWeightB,
        BigDecimal overlapContribution,
        String formattedOverlapContribution
    ) {}
}
