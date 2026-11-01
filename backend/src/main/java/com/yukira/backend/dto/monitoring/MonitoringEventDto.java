package com.yukira.backend.dto.monitoring;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Detailed DTO for an individual monitoring event.
 * Preserves evidence integrity, previous and latest states, provenance, and data-artifact distinction.
 */
public record MonitoringEventDto(
    Long id,
    Long investorId,
    Long schemeId,
    Long schemeOptionId,
    String schemeCode,
    String schemeName,
    String planType,
    String optionType,
    String portfolioScope,
    String eventClassification,
    String severity,
    String lifecycleStatus,
    String metricOrEvidenceId,
    String metricName,
    String previousValue,
    String currentValue,
    BigDecimal changeMagnitude,
    String unitOrFormat,
    String thresholdClassification,
    String evidenceState,
    LocalDate effectiveDate,
    ZonedDateTime availabilityTime,
    ZonedDateTime ingestionTimestamp,
    String sourceRevision,
    LocalDate previousObservationDate,
    LocalDate currentObservationDate,
    ZonedDateTime knowledgeCutoff,
    String sourceReference,
    Long calculationRunId,
    Long executionId,
    String metricRegistryVersion,
    String methodologyVersion,
    String evidenceCoverageInfo,
    String explanation,
    String triggerRationale,
    String knownLimitations,
    Boolean isDataArtifact,
    String artifactExplanation,
    String dedupHash,
    String associatedThesisKey,
    Long associatedConditionId,
    String associatedFollowUpId,
    String investigationNotes,
    ZonedDateTime acknowledgedAt,
    ZonedDateTime investigatingAt,
    ZonedDateTime resolvedAt,
    ZonedDateTime dismissedAt,
    String resolutionNote,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt,
    List<MonitoringEventNoteDto> notes
) {}
