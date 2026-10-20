package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Authoritative DTO representing evidence-based portfolio holdings drift and sector shifts.
 *
 * Epistemic & Calculation Rules:
 * 1. Absolute weight change: Delta w_i = w_{i,t2} - w_{i,t1} in percentage points.
 * 2. Absolute movement: |Delta w_i|.
 * 3. Additions (NEWLY_REPORTED) and exits (NO_LONGER_REPORTED) are explicitly distinguished from weight adjustments.
 * 4. Disclosed weights are preserved exactly as reported (never normalized to 100%).
 * 5. Changes in portfolio weights must never be termed investment purchases or sales.
 * 6. Sector drift aggregates disclosed weights by reported sector, explicitly accounting for unclassified/unmapped exposure.
 */
public record PortfolioDriftResponseDto(
    Long schemeOptionId,
    String schemeName,
    String amfiCode,
    String status, // COMPARABLE, INSUFFICIENT_HISTORY, UNAVAILABLE, INVALID_SELECTION
    String statusReason,
    SnapshotDriftHeaderDto earlierSnapshot,
    SnapshotDriftHeaderDto laterSnapshot,
    BigDecimal top5PctDelta,
    BigDecimal top10PctDelta,
    BigDecimal hhiDelta,
    DriftClassificationSummaryDto classificationSummary,
    List<AssetClassDriftDto> assetClassDrift,
    List<SectorDriftDto> sectorDrift,
    SectorDriftCoverageDto sectorCoverage,
    List<HoldingDriftItemDto> holdingChanges,
    List<HoldingDriftItemDto> topIncreases,
    List<HoldingDriftItemDto> topDecreases,
    List<HoldingDriftItemDto> additions,
    List<HoldingDriftItemDto> exits,
    Boolean isComparable,
    List<String> comparabilityNotes,
    String qualityState,
    String methodologyVersion,
    List<String> investigationPrompts
) {

    /**
     * Backward-compatible constructor for existing 17-parameter invocations.
     */
    public PortfolioDriftResponseDto(
        Long schemeOptionId,
        String schemeName,
        String amfiCode,
        String status,
        String statusReason,
        SnapshotDriftHeaderDto earlierSnapshot,
        SnapshotDriftHeaderDto laterSnapshot,
        BigDecimal top5PctDelta,
        BigDecimal top10PctDelta,
        BigDecimal hhiDelta,
        DriftClassificationSummaryDto classificationSummary,
        List<AssetClassDriftDto> assetClassDrift,
        List<HoldingDriftItemDto> holdingChanges,
        Boolean isComparable,
        List<String> comparabilityNotes,
        String qualityState,
        String methodologyVersion
    ) {
        this(
            schemeOptionId,
            schemeName,
            amfiCode,
            status,
            statusReason,
            earlierSnapshot,
            laterSnapshot,
            top5PctDelta,
            top10PctDelta,
            hhiDelta,
            classificationSummary,
            assetClassDrift,
            Collections.emptyList(),
            null,
            holdingChanges,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            isComparable,
            comparabilityNotes,
            qualityState,
            methodologyVersion,
            Collections.emptyList()
        );
    }

    public record DriftClassificationSummaryDto(
        Integer totalComparedPositions,
        Integer newlyReportedCount,
        Integer noLongerReportedCount,
        Integer weightIncreasedCount,
        Integer weightDecreasedCount,
        Integer unchangedCount,
        Integer unclassifiedCount
    ) {}

    public record SnapshotDriftHeaderDto(
        Long snapshotId,
        LocalDate portfolioDate,
        OffsetDateTime availabilityTime,
        Long sourceArtifactId,
        String sourceArtifactSha256,
        Integer holdingsCount,
        BigDecimal sumWeightsPct,
        BigDecimal top5Pct,
        BigDecimal top10Pct,
        BigDecimal hhi
    ) {}

    public record AssetClassDriftDto(
        String assetClass,
        BigDecimal earlierWeightPct,
        String earlierWeightPctFormatted,
        BigDecimal laterWeightPct,
        String laterWeightPctFormatted,
        BigDecimal weightDeltaPct,
        String weightDeltaPctFormatted
    ) {}

    public record SectorDriftDto(
        String sector,
        BigDecimal earlierWeightPct,
        String earlierWeightPctFormatted,
        BigDecimal laterWeightPct,
        String laterWeightPctFormatted,
        BigDecimal weightDeltaPct,
        String weightDeltaPctFormatted
    ) {}

    public record SectorDriftCoverageDto(
        BigDecimal earlierCoveragePct,
        String earlierCoveragePctFormatted,
        BigDecimal laterCoveragePct,
        String laterCoveragePctFormatted,
        BigDecimal unclassifiedWeightPctEarlier,
        String unclassifiedWeightPctEarlierFormatted,
        BigDecimal unclassifiedWeightPctLater,
        String unclassifiedWeightPctLaterFormatted,
        String coverageStatus, // ADEQUATE_COVERAGE, PARTIAL_COVERAGE, UNAVAILABLE
        List<String> coverageLimitationNotes
    ) {}

    public record HoldingDriftItemDto(
        String securityName,
        String isin,
        String sector,
        String assetClass,
        String changeType, // WEIGHT_INCREASED, WEIGHT_DECREASED, NEWLY_REPORTED, NO_LONGER_REPORTED, UNCHANGED
        BigDecimal earlierWeightPct,
        String earlierWeightPctFormatted,
        BigDecimal laterWeightPct,
        String laterWeightPctFormatted,
        BigDecimal weightDeltaPct,
        String weightDeltaPctFormatted,
        String changeDescription
    ) {}
}
