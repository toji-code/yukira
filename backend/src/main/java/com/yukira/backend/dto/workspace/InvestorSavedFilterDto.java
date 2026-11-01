package com.yukira.backend.dto.workspace;

import java.time.ZonedDateTime;

public record InvestorSavedFilterDto(
    Long id,
    String filterName,
    String filterCriteriaJson,
    String filterType,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {}

