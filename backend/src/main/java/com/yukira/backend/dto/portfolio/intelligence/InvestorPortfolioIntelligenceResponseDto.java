package com.yukira.backend.dto.portfolio.intelligence;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record InvestorPortfolioIntelligenceResponseDto(
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    String allocationMode,
    PortfolioValuationBasisDto valuationBasis,
    PortfolioHoldingsCoverageSummaryDto holdingsCoverage,
    List<ConsolidatedPortfolioSecurityDto> consolidatedSecurities,
    PortfolioConcentrationAnalysisDto concentrationAnalysis,
    PortfolioSectorAndAssetClassDto sectorAndAssetClassExposure,
    PortfolioOverlapAndDuplicationDto overlapAndDuplication,
    PortfolioExposureDriftDto exposureDrift,
    PortfolioRiskContextDto riskContext,
    PortfolioInvestorPerformanceDto investorPerformance,
    PortfolioResearchBriefDto researchBrief,
    List<String> dataLimitations,
    Map<String, String> provenanceMetadata
) {}
