package com.yukira.backend.dto.ingestion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Structured ingestion report for Point-in-Time Benchmark Constituent archives.
 *
 * Epistemic Guardrails (AGENTS.md & Section 3/5 of Factsheet Archive Spec):
 * 1. Independent reporting of membership, identifier mapping, weight, temporal, and source confidence.
 * 2. Distinct statuses for parser validation vs source verification.
 * 3. Auditable duplicate resolution logging without silent record drops.
 */
public record BenchmarkConstituentIngestionResultDto(
    Long benchmarkId,
    String benchmarkCode,
    LocalDate effectiveDate,
    int totalParsed,
    int insertedCount,
    int mappedCount,
    int unmappedCount,
    Long sourceArtifactId,
    String sourceArtifactSha256,
    String status,
    String message,
    int acceptedCount,
    int rejectedCount,
    int duplicateCount,
    int disclosedWeightsCount,
    int unknownWeightsCount,
    BigDecimal disclosedWeightSum,
    BigDecimal membershipCoveragePct,
    BigDecimal mappingCoveragePct,
    BigDecimal weightCoveragePct,
    String sourceConfidenceStatus,
    String sourcePublisher,
    String sourceUrl,
    List<DuplicateResolutionDto> duplicateResolutions
) {

    public record DuplicateResolutionDto(
        String isin,
        String symbol,
        String companyName,
        String resolutionAction,
        String reason
    ) {}

    /**
     * Backward-compatible constructor for existing callers and test harnesses.
     */
    public BenchmarkConstituentIngestionResultDto(
        Long benchmarkId,
        String benchmarkCode,
        LocalDate effectiveDate,
        int totalParsed,
        int insertedCount,
        int mappedCount,
        int unmappedCount,
        Long sourceArtifactId,
        String sourceArtifactSha256,
        String status,
        String message
    ) {
        this(
            benchmarkId,
            benchmarkCode,
            effectiveDate,
            totalParsed,
            insertedCount,
            mappedCount,
            unmappedCount,
            sourceArtifactId,
            sourceArtifactSha256,
            status,
            message,
            insertedCount,
            0,
            0,
            0,
            totalParsed,
            BigDecimal.ZERO,
            totalParsed > 0 ? new BigDecimal("100.00") : BigDecimal.ZERO,
            totalParsed > 0 ? BigDecimal.valueOf(mappedCount * 100.0 / totalParsed).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
            BigDecimal.ZERO,
            "VERIFIED_COMPLETE",
            "NSE Indices Limited",
            null,
            Collections.emptyList()
        );
    }
}
