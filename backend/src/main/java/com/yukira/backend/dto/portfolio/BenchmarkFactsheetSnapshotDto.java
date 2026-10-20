package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Historical Point-in-Time benchmark factsheet composition snapshot with 5 independent coverage dimensions.
 *
 * Epistemic Rules (AGENTS.md & Historical Benchmark Factsheet Archive Spec):
 * 1. Independent reporting of membership, identifier mapping, weight, temporal comparability, and source confidence.
 * 2. Unweighted tail constituents remain strictly null / unknown; never assumed to be zero.
 * 3. Cryptographic provenance to verified source_artifact (SHA-256 hash and URI).
 */
public record BenchmarkFactsheetSnapshotDto(
    LocalDate effectiveDate,
    OffsetDateTime availabilityTime,
    Long sourceArtifactId,
    String sourceArtifactSha256,
    String sourceStorageUri,
    String sourcePublisher,
    int totalConstituentsCount,
    int weightedConstituentsCount,
    int unknownWeightsCount,
    BigDecimal disclosedWeightSum,
    BigDecimal membershipCoveragePct,
    BigDecimal identifierMappingCoveragePct,
    BigDecimal weightCoveragePct,
    String membershipStatus,         // "VERIFIED_COMPLETE" | "VERIFIED_PARTIAL" | "UNAVAILABLE"
    String identifierMappingStatus,   // "VERIFIED_COMPLETE" (>=95%) | "VERIFIED_PARTIAL"
    String weightCoverageStatus,     // "GATED_INCOMPLETE_BENCHMARK_WEIGHTS" | "VERIFIED_COMPLETE"
    String sourceConfidenceStatus,   // "VERIFIED_COMPLETE" | "UNVERIFIED_SOURCE"
    String licensingConstraint,      // Explanation of proprietary tail weights
    String historicalContextNotes    // Reconstitution notes and corporate action alignment
) {}
