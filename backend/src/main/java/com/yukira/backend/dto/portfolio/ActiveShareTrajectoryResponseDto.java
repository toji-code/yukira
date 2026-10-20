package com.yukira.backend.dto.portfolio;

import java.util.List;

/**
 * Top-level response DTO for Point-in-Time Active Share & Off-Benchmark Allocation Trajectory.
 */
public record ActiveShareTrajectoryResponseDto(
    Long schemeOptionId,
    String fundName,
    String planType,
    String optionType,
    Long benchmarkId,
    String benchmarkCode,
    String benchmarkName,
    String overallStatus, // "VERIFIED_COMPLETE", "GATED_INCOMPLETE_BENCHMARK_WEIGHTS", "INSUFFICIENT_DATA", "VERIFIED_PARTIAL"
    String statusReason,
    String methodologyVersion,
    String calculationRunId,
    String knowledgeCutoff,
    int totalSnapshotsEvaluated,
    int eligibleObservationsCount,
    int skippedPeriodsCount,
    List<String> missingDataRequirements,
    List<ActiveShareObservationDto> observations,
    List<ActiveShareChangeBreakdownDto> apparentChanges,
    List<ActiveShareReturnAssociationDto> returnAssociations,
    List<String> epistemicDisclaimers
) {}
