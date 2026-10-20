package com.yukira.backend.dto.portfolio;

import java.util.List;

/**
 * Full investor-facing response for historical Point-in-Time Sector & Industry Exposure Drift.
 */
public record SectorDriftResponseDto(
    Long schemeOptionId,
    String fundName,
    String planType,
    String optionType,
    Long benchmarkId,
    String benchmarkCode,
    String benchmarkName,
    String overallStatus,
    String statusReason,
    String methodologyVersion,
    String runId,
    String knowledgeCutoff,
    int totalSnapshotsFound,
    int eligibleObservationsCount,
    int skippedPeriodsCount,
    List<SectorDriftObservationDto> observations,
    List<SectorDriftChangeDto> sectorDriftChanges,
    List<IndustryDriftChangeDto> industryDriftChanges,
    List<String> dataQualityLimitations,
    List<String> missingDataRequirements,
    List<String> investigationPrompts
) {}

