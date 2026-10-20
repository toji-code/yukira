package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Immutable API response DTO for Benchmark Beta Dynamics & Systematic Covariance
 * (MKT-01 Standard Beta, MKT-02 Downside Beta, Upside Beta, Rolling Beta Dynamics,
 * Exposure Stability Analysis, and Historical Market Regime Comparison).
 */
public record BetaDynamicsResponse(
    BetaContext context,
    BetaMetrics metrics,
    ExposureStability stability,
    List<RollingBetaPoint> rollingSeries,
    List<BetaRegimeSummary> regimes,
    BetaEpistemic epistemic
) {
    public BetaDynamicsResponse(
        BetaContext context,
        BetaMetrics metrics,
        BetaEpistemic epistemic
    ) {
        this(context, metrics, null, List.of(), List.of(), epistemic);
    }

    public record BetaContext(
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
        String windowHorizon,
        OffsetDateTime knowledgeCutoffTime,
        OffsetDateTime evaluatedAt
    ) {
        public BetaContext(
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
        ) {
            this(
                schemeOptionId, fundName, amfiCode, isin, planType, optionType,
                benchmarkId, benchmarkName, startDate, endDate, "36M",
                knowledgeCutoffTime, evaluatedAt
            );
        }
    }

    public record BetaMetrics(
        BigDecimal standardBeta,
        String standardBetaStatus,
        BigDecimal downsideBeta,
        String downsideBetaStatus,
        BigDecimal upsideBeta,
        String upsideBetaStatus,
        BigDecimal betaAsymmetrySpread,
        String asymmetryStatus,
        Integer totalPairedDays,
        Integer upDaysCount,
        Integer downDaysCount,
        Integer flatDaysCount,
        Integer minPairedRequired,
        Integer minDownRequired,
        Integer minUpRequired,
        Boolean isStandardSufficient,
        Boolean isDownsideSufficient,
        Boolean isUpsideSufficient,
        String riskFreeProxy,
        Boolean riskFreeAligned
    ) {}

    public record ExposureStability(
        BigDecimal medianBeta,
        BigDecimal iqrBeta,
        BigDecimal minBeta,
        LocalDate minBetaDate,
        BigDecimal maxBeta,
        LocalDate maxBetaDate,
        BigDecimal startBeta,
        LocalDate startDate,
        BigDecimal endBeta,
        LocalDate endDate,
        BigDecimal betaChange,
        Integer totalWindows,
        Integer validWindows,
        BigDecimal pctAboveParity,
        BigDecimal pctBelowParity,
        BigDecimal meanDownsideBeta,
        BigDecimal meanUpsideBeta,
        BigDecimal upsideDownsideSpread
    ) {}

    public record RollingBetaPoint(
        LocalDate windowStart,
        LocalDate windowEnd,
        BigDecimal beta,
        BigDecimal downsideBeta,
        BigDecimal upsideBeta,
        BigDecimal betaSpread,
        Integer pairedDays,
        Integer upDays,
        Integer downDays,
        String status
    ) {}

    public record BetaRegimeSummary(
        String regimeId,
        String regimeName,
        LocalDate startDate,
        LocalDate endDate,
        Integer pairedDays,
        Integer upDays,
        Integer downDays,
        BigDecimal fullPeriodBeta,
        BigDecimal upsideBeta,
        BigDecimal downsideBeta,
        BigDecimal medianRollingBeta,
        BigDecimal minRollingBeta,
        BigDecimal maxRollingBeta,
        String coverageStatus,
        String description
    ) {}

    public record BetaEpistemic(
        String observation,
        String interpretation,
        String limitation,
        String dataQualityStatus,
        String benchmarkLineage,
        String sourceArtifactSha256,
        Long calculationRunId
    ) {}
}
