package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Immutable auditable response DTO for Rolling Return and Outperformance Consistency Analysis.
 * Implements Phase 2H methodology §RET-05 (3Y Rolling Return Mean & Distribution)
 * and §RET-06 (Rolling Outperformance % vs NIFTY 500 TRI).
 *
 * Governed strictly by YUKIRA epistemic rules:
 * - Deterministic mathematical computation
 * - Strict Point-in-Time (PIT) knowledge-cutoff isolation
 * - Synchronous paired calendar alignment without date fabrication
 * - Tri-partite epistemic disclosure (Observation, Interpretation, Limitation)
 */
public record RollingConsistencyResponse(
    RollingContext context,
    RollingHorizonResult primary3Y,
    RollingHorizonResult supporting1Y,
    List<RollingWindowSample> sampleWindows,
    RollingEpistemic epistemic
) {
    public record RollingContext(
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
        OffsetDateTime executionCompletedAt
    ) {}

    public record RollingHorizonResult(
        String periodType,
        int windowYears,
        int minWindowsRequired,
        boolean sufficientData,
        String returnStatus,
        Integer totalWindows,
        BigDecimal meanReturn,
        BigDecimal medianReturn,
        BigDecimal minReturn,
        BigDecimal maxReturn,
        BigDecimal p25Return,
        BigDecimal p75Return,
        BigDecimal stdDev,
        String outperformanceStatus,
        Integer pairedWindows,
        Integer outperformingWindows,
        Integer underperformingWindows,
        BigDecimal outperformancePercentage,
        BigDecimal meanExcessReturn,
        String statusReason
    ) {}

    public record RollingWindowSample(
        String startDate,
        String endDate,
        BigDecimal fundReturn,
        BigDecimal benchmarkReturn,
        Boolean outperforming
    ) {}

    public record RollingEpistemic(
        String observation,
        String interpretation,
        String limitation,
        String dataQualityStatus,
        String benchmarkIntegrityDisclosure,
        String sourceArtifactSha256,
        Long calculationRunId
    ) {}
}
