package com.yukira.backend.dto.portfolio;

import java.util.List;

/**
 * Top-level response for the Historical Benchmark Factsheet Archive.
 *
 * Epistemic Rules (AGENTS.md & Section 5 Specification):
 * - Exposes chronological Point-in-Time composition snapshots under requested knowledge cutoff.
 * - Details known historical gaps where authentic archives are unavailable without synthetic fabrication.
 * - Documents statutory licensing constraints regarding constituent weights.
 */
public record BenchmarkFactsheetArchiveResponseDto(
    Long benchmarkId,
    String benchmarkCode,
    String benchmarkName,
    String provider,
    String methodologyDescription,
    String evaluationKnowledgeCutoff,
    int totalSnapshotsAvailable,
    List<BenchmarkFactsheetSnapshotDto> snapshots,
    List<String> knownHistoricalGaps,
    List<String> dataIntegrityDisclosures
) {}
