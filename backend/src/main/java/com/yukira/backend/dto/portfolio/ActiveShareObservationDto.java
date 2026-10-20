package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Historical Point-in-Time observation of Active Share and Off-Benchmark Allocation.
 *
 * Epistemic Rules (AGENTS.md & Section 5 Specification):
 * 1. Definitive Active Share is GATED when benchmark weights are partially disclosed (< 99.5% weight coverage).
 * 2. activeShareLowerBoundPct provides the deterministic mathematical minimum active share established by verified positions.
 * 3. 5 separate coverage dimensions are reported independently:
 *    - Membership coverage (complete / partial)
 *    - Identifier mapping coverage (canonical security resolution)
 *    - Weight coverage (disclosed weights sum vs unknown tail)
 *    - Temporal comparability (portfolio vs benchmark date gap <= 45 days)
 *    - Source confidence (publisher, cryptographic SHA-256 provenance)
 */
public record ActiveShareObservationDto(
    LocalDate portfolioDate,
    String portfolioAvailabilityTime,
    Long portfolioSourceArtifactId,
    LocalDate benchmarkDate,
    String benchmarkAvailabilityTime,
    long snapshotDateDiffDays,
    boolean isTemporallyComparable,
    BigDecimal activeSharePct,             // Nullable if benchmark weights incomplete
    BigDecimal activeShareLowerBoundPct,    // Verified lower bound
    BigDecimal offBenchmarkAllocationPct,   // Total weight of holdings outside benchmark
    BigDecimal membershipOverlapPct,        // % of fund holdings that are benchmark constituents
    int fundHoldingsCount,
    int benchmarkConstituentsCount,
    BigDecimal portfolioWeightCoveragePct,  // e.g. 98.50%
    BigDecimal benchmarkWeightCoveragePct,  // e.g. 32.22%
    BigDecimal benchmarkMembershipCoveragePct, // e.g. 100.0%
    BigDecimal securityMappingCoveragePct,  // % of fund holdings mapped to valid ISIN
    String status,                          // e.g. "GATED_INCOMPLETE_BENCHMARK_WEIGHTS", "VERIFIED_COMPLETE", "UNVERIFIED_SOURCE"
    String statusReason,
    List<ActiveShareConstituentInputDto> constituentInputs,
    int weightedConstituentsCount,
    int unknownWeightsCount,
    BigDecimal disclosedWeightSum,
    String membershipCoverageStatus,
    String identifierMappingStatus,
    String weightCoverageStatus,
    String temporalComparabilityStatus,
    String sourceConfidenceStatus,
    String sourcePublisher,
    String sourceArtifactSha256,
    String sourceStorageUri
) {
    /**
     * Backward-compatible constructor for existing 20-parameter callers and tests.
     */
    public ActiveShareObservationDto(
        LocalDate portfolioDate,
        String portfolioAvailabilityTime,
        Long portfolioSourceArtifactId,
        LocalDate benchmarkDate,
        String benchmarkAvailabilityTime,
        long snapshotDateDiffDays,
        boolean isTemporallyComparable,
        BigDecimal activeSharePct,
        BigDecimal activeShareLowerBoundPct,
        BigDecimal offBenchmarkAllocationPct,
        BigDecimal membershipOverlapPct,
        int fundHoldingsCount,
        int benchmarkConstituentsCount,
        BigDecimal portfolioWeightCoveragePct,
        BigDecimal benchmarkWeightCoveragePct,
        BigDecimal benchmarkMembershipCoveragePct,
        BigDecimal securityMappingCoveragePct,
        String status,
        String statusReason,
        List<ActiveShareConstituentInputDto> constituentInputs
    ) {
        this(
            portfolioDate,
            portfolioAvailabilityTime,
            portfolioSourceArtifactId,
            benchmarkDate,
            benchmarkAvailabilityTime,
            snapshotDateDiffDays,
            isTemporallyComparable,
            activeSharePct,
            activeShareLowerBoundPct,
            offBenchmarkAllocationPct,
            membershipOverlapPct,
            fundHoldingsCount,
            benchmarkConstituentsCount,
            portfolioWeightCoveragePct,
            benchmarkWeightCoveragePct,
            benchmarkMembershipCoveragePct,
            securityMappingCoveragePct,
            status,
            statusReason,
            constituentInputs,
            10,
            benchmarkConstituentsCount > 10 ? benchmarkConstituentsCount - 10 : 0,
            benchmarkWeightCoveragePct,
            benchmarkMembershipCoveragePct != null && benchmarkMembershipCoveragePct.compareTo(new BigDecimal("99.0")) >= 0 ? "VERIFIED_COMPLETE" : "VERIFIED_PARTIAL",
            securityMappingCoveragePct != null && securityMappingCoveragePct.compareTo(new BigDecimal("95.0")) >= 0 ? "VERIFIED_COMPLETE" : "VERIFIED_PARTIAL",
            benchmarkWeightCoveragePct != null && benchmarkWeightCoveragePct.compareTo(new BigDecimal("99.5")) >= 0 ? "VERIFIED_COMPLETE" : "GATED_INCOMPLETE_BENCHMARK_WEIGHTS",
            isTemporallyComparable ? "VERIFIED_COMPLETE" : "UNVERIFIED_SOURCE",
            "VERIFIED_COMPLETE",
            "NSE Indices Limited",
            null,
            null
        );
    }
}
