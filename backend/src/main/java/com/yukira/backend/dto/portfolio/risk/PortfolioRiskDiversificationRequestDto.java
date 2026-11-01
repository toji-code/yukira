package com.yukira.backend.dto.portfolio.risk;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PortfolioRiskDiversificationRequestDto(
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    String benchmarkCode,
    List<Long> overrideSchemeOptionIds
) {}

