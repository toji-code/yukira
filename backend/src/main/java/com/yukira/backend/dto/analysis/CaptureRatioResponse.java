package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Immutable API response DTO for MKT-03 (Upside Capture), MKT-04 (Downside Capture),
 * and MKT-05 (Capture Spread) analysis against benchmark.
 */
public record CaptureRatioResponse(
    CaptureContext context,
    CaptureMetrics metrics,
    CaptureEpistemic epistemic
) {
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
