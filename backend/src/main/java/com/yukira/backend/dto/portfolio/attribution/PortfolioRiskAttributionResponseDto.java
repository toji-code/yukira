package com.yukira.backend.dto.portfolio.attribution;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record PortfolioRiskAttributionResponseDto(
    String evaluationStatus,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    LocalDate calculationStartDate,
    LocalDate calculationEndDate,
    int alignedObservationCount,
    int annualizationFactor,
    String annualizationConvention,
    String benchmarkCode,
    String benchmarkName,
    List<RiskOverviewMetricDto> overviewMetrics,
    List<FundRiskContributionDto> fundContributions,
    RiskReconciliationDto reconciliation,
    RiskExposureContextDto exposureContext,
    RiskInterpretationPanelDto interpretation,
    List<String> dataQualityWarnings,
    Map<String, String> methodologyNotes
) {}
