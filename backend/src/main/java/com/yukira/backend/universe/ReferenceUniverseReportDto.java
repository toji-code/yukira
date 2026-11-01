package com.yukira.backend.universe;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable report detailing the current YUKIRA V1 reference universe denominator,
 * category composition, exclusion reasons, and data quality provenance.
 */
public record ReferenceUniverseReportDto(
    String ruleVersion,
    String countingUnit,
    boolean isProvisional,
    long totalEvaluatedOptions,
    long directGrowthOptionsCount,
    long coreFiveCategoryCandidatesCount,
    long includedUniverseCount,
    Map<String, Long> includedCountsByCategory,
    Map<String, Long> excludedCountsByReason,
    OffsetDateTime latestArtifactRetrievalTime,
    String sourceArtifactSha256,
    List<String> limitations,
    List<String> governanceNotes
) {}
