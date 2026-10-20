package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record MultiFundLookThroughResponseDto(
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    BigDecimal inputTotalAllocation,
    boolean isFullyAllocated,
    String qualityState,
    List<ConstituentFundSummaryDto> constituentFunds,
    List<ConsolidatedSecurityExposureDto> consolidatedSecurities,
    List<ConsolidatedSectorExposureDto> consolidatedSectors,
    List<ConsolidatedMarketCapExposureDto> consolidatedMarketCap,
    List<PairwiseOverlapResultDto> pairwiseOverlapMatrix,
    PortfolioConcentrationMetricsDto concentrationMetrics,
    List<SharedExposureDiagnosticDto> sharedDiagnostics,
    List<String> limitations,
    Map<String, String> epistemicDisclosure,
    PortfolioRiskDecompositionDto riskDecomposition
) {
    public MultiFundLookThroughResponseDto(
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff,
        BigDecimal inputTotalAllocation,
        boolean isFullyAllocated,
        String qualityState,
        List<ConstituentFundSummaryDto> constituentFunds,
        List<ConsolidatedSecurityExposureDto> consolidatedSecurities,
        List<ConsolidatedSectorExposureDto> consolidatedSectors,
        List<ConsolidatedMarketCapExposureDto> consolidatedMarketCap,
        List<PairwiseOverlapResultDto> pairwiseOverlapMatrix,
        PortfolioConcentrationMetricsDto concentrationMetrics,
        List<SharedExposureDiagnosticDto> sharedDiagnostics,
        List<String> limitations,
        Map<String, String> epistemicDisclosure
    ) {
        this(asOfDate, knowledgeCutoff, inputTotalAllocation, isFullyAllocated, qualityState,
             constituentFunds, consolidatedSecurities, consolidatedSectors, consolidatedMarketCap,
             pairwiseOverlapMatrix, concentrationMetrics, sharedDiagnostics, limitations,
             epistemicDisclosure, null);
    }
}
