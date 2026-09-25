package com.yukira.backend.dto.analysis;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request DTO to trigger a unified Phase 2R 3-Year Institutional Profile calculation.
 */
public record ProfileCalculationRequest(
    Long schemeOptionId,
    Long benchmarkId,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoffTime,
    String methodologyTag,
    List<String> metricCodes,
    Map<String, Object> parameters
) {}
