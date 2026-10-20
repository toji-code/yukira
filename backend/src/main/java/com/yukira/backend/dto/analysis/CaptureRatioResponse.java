package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Immutable API response DTO for MKT-03 (Upside Capture), MKT-04 (Downside Capture),
 * MKT-05 (Capture Spread), and Historical Stress-Event Asymmetry Analysis.
 */
public record CaptureRatioResponse(
    CaptureContext context,
    CaptureMetrics metrics,
    List<HistoricalStressEvent> stressEvents,
    CaptureEpistemic epistemic
) {
    public CaptureRatioResponse(
        CaptureContext context,
        CaptureMetrics metrics,
        CaptureEpistemic epistemic
    ) {
        this(context, metrics, List.of(), epistemic);
    }

    public record CaptureContext(
        Long schemeOptionId,
        String fundName,
        String amfiCode,
        String isin,
        String planType,
        String optionType,
        Long benchmarkId,
        String benchmarkName,
        LocalDate startDate,
        LocalDate endDate,
        OffsetDateTime knowledgeCutoffTime,
        OffsetDateTime evaluatedAt
    ) {}

    public record CaptureMetrics(
        BigDecimal upsideCaptureRatio,
        String upsideStatus,
        Integer upDaysCount,
        Integer minUpDaysRequired,
        Boolean isUpSufficient,
        BigDecimal fundUpCumulativeReturn,
        BigDecimal benchUpCumulativeReturn,
        BigDecimal downsideCaptureRatio,
        String downsideStatus,
        Integer downDaysCount,
        Integer minDownDaysRequired,
        Boolean isDownSufficient,
        BigDecimal fundDownCumulativeReturn,
        BigDecimal benchDownCumulativeReturn,
        Boolean isInverseCaptureGain,
        BigDecimal captureSpread,
        String spreadStatus,
        Integer totalPairedDays,
        Integer flatDaysCount
    ) {}

    public record HistoricalStressEvent(
        String eventId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal fundReturn,
        BigDecimal benchmarkReturn,
        BigDecimal excessReturn,
        BigDecimal fundMaxDrawdown,
        BigDecimal benchmarkMaxDrawdown,
        Integer tradingDays,
        String coverageStatus,
        String classificationBasis,
        String retrospectiveDisclaimer,
        List<StressTrajectoryPoint> trajectory
    ) {}

    public record StressTrajectoryPoint(
        String date,
        BigDecimal fundIndexed,
        BigDecimal benchmarkIndexed,
        BigDecimal fundDailyReturn,
        BigDecimal benchmarkDailyReturn
    ) {}

    public record CaptureEpistemic(
        String observation,
        String interpretation,
        String limitation,
        String dataQualityStatus,
        String benchmarkLineage,
        String sourceArtifactSha256,
        Long calculationRunId
    ) {}
}
