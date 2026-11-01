package com.yukira.backend.dto.portfolio.risk.stress;

import com.yukira.backend.dto.portfolio.risk.RiskInvestigationFindingDto;
import com.yukira.backend.dto.portfolio.risk.UnvaluedPositionDto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Structured, auditable historical stress evidence record for export and investor research documentation (Tasks 58 & 73).
 *
 * Epistemic Guardrails:
 * - Provides an immutable, self-contained record of calculated stress proxy evidence, observation accounting,
 *   data-quality signals, limitations, and investor research findings.
 * - Explicitly states that portfolio stress results represent a static current-allocation proxy
 *   and NOT an actual historical investor performance statement.
 * - Schema versioned ("1.1.0") for reproducible offline auditing and compliance retention (Task 73).
 * - Content-addressed calculation input fingerprint and source lineage state ensure auditable integrity.
 */
public record PortfolioStressEvidenceRecordDto(
    String schemaVersion,
    String recordId,
    String investorSubject,
    PortfolioAllocationSnapshotDto allocationSnapshot,
    StressCalculationContextDto calculationContext,
    StressBenchmarkReferenceDto benchmarkReference,
    PortfolioStressSummaryDto summary,
    List<PortfolioStressEpisodeDto> episodes,
    List<RiskInvestigationFindingDto> attachedFindings,
    List<UnvaluedPositionDto> unvaluedPositions,
    BigDecimal knownValueCoveragePct,
    List<String> governanceDisclaimers,
    Map<String, String> methodologyNotes,
    OffsetDateTime exportedAt,
    String exportNotice,
    String calculationInputFingerprint,
    String sourceLineageState
) {
    /**
     * Backward-compatible 14-parameter constructor (Task 58 compatibility).
     */
    public PortfolioStressEvidenceRecordDto(
        String recordId,
        String investorSubject,
        PortfolioAllocationSnapshotDto allocationSnapshot,
        StressCalculationContextDto calculationContext,
        StressBenchmarkReferenceDto benchmarkReference,
        PortfolioStressSummaryDto summary,
        List<PortfolioStressEpisodeDto> episodes,
        List<RiskInvestigationFindingDto> attachedFindings,
        List<UnvaluedPositionDto> unvaluedPositions,
        BigDecimal knownValueCoveragePct,
        List<String> governanceDisclaimers,
        Map<String, String> methodologyNotes,
        OffsetDateTime exportedAt,
        String exportNotice
    ) {
        this(
            "1.1.0",
            recordId,
            investorSubject,
            allocationSnapshot,
            calculationContext,
            benchmarkReference,
            summary,
            episodes,
            attachedFindings,
            unvaluedPositions,
            knownValueCoveragePct,
            governanceDisclaimers,
            methodologyNotes,
            exportedAt != null ? exportedAt : OffsetDateTime.now(),
            exportNotice,
            calculationContext != null ? calculationContext.calculationInputFingerprint() : null,
            calculationContext != null ? calculationContext.sourceLineageState() : "PARTIAL_SOURCE_LINEAGE_AVAILABLE"
        );
    }
}
