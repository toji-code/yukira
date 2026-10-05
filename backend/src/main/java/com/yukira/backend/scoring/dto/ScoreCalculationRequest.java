package com.yukira.backend.scoring.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ScoreCalculationRequest(
    Long schemeOptionId,
    Long calculationRunId,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoffTime,
    Long benchmarkId
) {}
