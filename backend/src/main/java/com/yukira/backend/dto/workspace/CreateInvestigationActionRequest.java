package com.yukira.backend.dto.workspace;

import java.time.LocalDate;

public record CreateInvestigationActionRequest(
    String actionTitle,
    String actionDescription,
    String targetType,
    String targetId,
    String priority,
    LocalDate dueDate
) {}

