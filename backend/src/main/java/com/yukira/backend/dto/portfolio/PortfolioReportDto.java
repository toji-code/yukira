package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.util.List;

/**
 * Consolidated Portfolio Evidence Report & Investor Snapshot V1.
 *
 * Follows strict Epistemic Triad:
 * EVIDENCE â†’ ANALYSIS â†’ EXPLANATION â†’ LIMITATIONS
 *
 * Invariants:
 * 1. Read-only assembly from persisted entities & existing services.
 * 2. Zero frontend math or live calculation.
 * 3. Exact scheme_option_id identity preserved.
 * 4. Zero recommendation language (BUY/SELL/HOLD/BEST/Ideal).
 * 5. Distinct as-of timestamps (valuation, score, risk, knowledge cutoff).
 */
public record PortfolioReportDto(
    ReportMetadataDto reportMetadata,
    PortfolioSummaryDto portfolioSummary,
    PortfolioGoalAlignmentDto goalAlignment,
    List<ReportHoldingDetailDto> holdingDetails,
    List<String> dataQualityLimitations,
    List<String> investigationQuestions,
    List<String> disclaimers,
    PortfolioDriftAnalysisDto targetDriftAnalysis
) {
    public PortfolioReportDto(
        ReportMetadataDto reportMetadata,
        PortfolioSummaryDto portfolioSummary,
        PortfolioGoalAlignmentDto goalAlignment,
        List<ReportHoldingDetailDto> holdingDetails,
        List<String> dataQualityLimitations,
        List<String> investigationQuestions,
        List<String> disclaimers
    ) {
        this(
            reportMetadata,
            portfolioSummary,
            goalAlignment,
            holdingDetails,
            dataQualityLimitations,
            investigationQuestions,
            disclaimers,
            null
        );
    }
    public record ReportMetadataDto(
        String reportId,
        String generationTimestamp,
        String valuationAsOfDate,
        String scoreAsOfDate,
        String knowledgeCutoff,
        String databaseEnvironment,
        String scoreVersion,
        String scoreMethodologyStatus,
        String referencePopulation
    ) {}

    public record ReportHoldingDetailDto(
        Long schemeOptionId,
        Long schemeId,
        String fundName,
        String amcName,
        String category,
        String subcategory,
        String planType,
        String optionType,
        String amfiCode,
        String isin,
        BigDecimal units,
        BigDecimal costBasisAmount,
        BigDecimal investedAmount,
        BigDecimal navValue,
        String navAsOfDate,
        String valuationState,
        BigDecimal availableValue,
        BigDecimal absoluteGainLoss,
        BigDecimal absoluteGainLossPercentage,
        BigDecimal portfolioWeightPercentage,
        AnalyticalScoreDetailDto scoreDetail,
        HoldingRiskEvidenceDto riskEvidence,
        HoldingGoalAlignmentDto goalAlignmentDetail,
        String sourceArtifactHash
    ) {}

    public record AnalyticalScoreDetailDto(
        boolean available,
        BigDecimal scoreValue,
        String confidence,
        String status,
        String scoreVersion,
        String asOfDate,
        List<DimensionDetailDto> dimensions
    ) {}

    public record DimensionDetailDto(
        String dimensionCode,
        String dimensionName,
        BigDecimal scoreValue,
        BigDecimal weightPercentage,
        String status
    ) {}
}
