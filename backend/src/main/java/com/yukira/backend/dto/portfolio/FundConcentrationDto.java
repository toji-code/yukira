package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FundConcentrationDto(
    Long schemeOptionId,
    String fundName,
    LocalDate portfolioDate,
    int positionCount,
    BigDecimal sumDisclosedWeightsPct,
    String formattedDisclosedCoverage,
    BigDecimal top5WeightPct,
    String formattedTop5Weight,
    BigDecimal top10WeightPct,
    String formattedTop10Weight,
    BigDecimal hhi,
    String hhiClassification,
    List<TopHoldingDetailDto> topHoldings,
    String qualityState,
    List<String> limitations
) {
    public record TopHoldingDetailDto(
        int rank,
        String securityName,
        String isin,
        String sector,
        BigDecimal weightPct,
        String formattedWeight
    ) {}
}
