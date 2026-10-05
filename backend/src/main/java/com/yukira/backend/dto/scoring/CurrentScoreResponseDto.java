package com.yukira.backend.dto.scoring;

import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CurrentScoreResponseDto(
    Long scoreId,
    Long schemeOptionId,
    String schemeName,
    String amfiCode,
    String isin,
    BigDecimal score,
    BigDecimal confidence,
    String status,
    String scoreVersion,
    String methodologyStatus,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoffTime,
    Long calculationRunId,
    String referencePopulation,
    String summary,
    String disclaimer,
    boolean isCurrent,
    String dataFreshnessState,
    List<AnalyticalScoreResponse.DimensionScoreDto> dimensions,
    AnalyticalScoreResponse.EvidenceConfidenceDto evidenceConfidence
) {}
