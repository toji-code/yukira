package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Immutable API response DTO for Benchmark Beta Dynamics & Systematic Covariance
 * (MKT-01 Standard Beta, MKT-02 Downside Beta, and legacy upside-beta diagnostic).
 */
public record BetaDynamicsResponse(
    BetaContext context,
    BetaMetrics metrics,
    BetaEpistemic epistemic
) {
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
        OffsetDateTime knowledgeCutoffTime,
        OffsetDateTime evaluatedAt
    ) {}

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
