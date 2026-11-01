package com.yukira.backend.dto.workspace;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public record InvestorInvestigationActionDto(
    Long id,
    String actionTitle,
    String actionDescription,
    String targetType,
    String targetId,
    String status,
    String priority,
    LocalDate dueDate,
    ZonedDateTime completedAt,
    String outcomeNotes,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {}

