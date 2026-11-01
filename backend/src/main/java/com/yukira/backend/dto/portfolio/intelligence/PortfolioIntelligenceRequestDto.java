package com.yukira.backend.dto.portfolio.intelligence;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PortfolioIntelligenceRequestDto(
    String allocationMode, // "ACTUAL_CAPITAL" (default) or "EXPLORATORY_EQUAL_WEIGHT"
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff
) {}
