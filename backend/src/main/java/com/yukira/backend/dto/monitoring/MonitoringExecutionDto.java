package com.yukira.backend.dto.monitoring;

import java.time.ZonedDateTime;

/**
 * DTO representing an execution cycle of the continuous monitoring engine.
 * Records telemetry, scope counts, deduplication stats, and failure details.
 */
public record MonitoringExecutionDto(
    Long id,
    Long investorId,
    String executionTrigger, // SCHEDULED, INVESTOR_INITIATED
    String executionStatus,  // PENDING, RUNNING, COMPLETED, PARTIALLY_COMPLETED, FAILED, SKIPPED
    ZonedDateTime startedAt,
    ZonedDateTime completedAt,
    ZonedDateTime knowledgeCutoff,
    Integer fundsEvaluatedCount,
    Integer portfolioScopesEvaluatedCount,
    Integer conditionsEvaluatedCount,
    Integer eventsGeneratedCount,
    Integer eventsDeduplicatedCount,
    Integer unavailableEvidenceCount,
    Integer staleSourceCount,
    String errorMessage,
    String summaryNotes,
    ZonedDateTime createdAt
) {}

