package com.yukira.backend.dto.analysis;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ComparisonRequest(
    List<Long> schemeOptionIds,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoffTime,
    List<String> metricCodes
) {}
