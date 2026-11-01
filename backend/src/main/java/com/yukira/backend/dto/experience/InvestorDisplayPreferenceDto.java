package com.yukira.backend.dto.experience;

import java.time.ZonedDateTime;

public record InvestorDisplayPreferenceDto(
    Long id,
    String defaultAnalysisPeriod,
    String defaultWorkspaceTab,
    String preferredTheme,
    Boolean showCandidateMetricWarnings,
    String dateDisplayFormat,
    ZonedDateTime updatedAt
) {}

