package com.yukira.backend.dto.telemetry;

import java.time.ZonedDateTime;

public record OperationalJobExecutionDto(
    Long id,
    String jobName,
    String executionTrigger,
    String executionStatus,
    ZonedDateTime startedAt,
    ZonedDateTime completedAt,
    String scopeDescription,
    Integer recordsProcessed,
    Integer recordsFailed,
    String failureReason,
    String telemetryJson,
    ZonedDateTime createdAt
) {}

