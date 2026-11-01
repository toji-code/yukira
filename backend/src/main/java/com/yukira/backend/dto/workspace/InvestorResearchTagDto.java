package com.yukira.backend.dto.workspace;

import java.time.ZonedDateTime;

public record InvestorResearchTagDto(
    Long id,
    String tagName,
    String targetType,
    String targetId,
    ZonedDateTime createdAt
) {}

