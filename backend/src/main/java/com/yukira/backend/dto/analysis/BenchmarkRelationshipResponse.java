package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Immutable API response DTO for benchmark relationship and explanatory power
 * (benchmark correlation and R-Squared; these are panel diagnostics, not frozen REL registry codes).
 */
public record BenchmarkRelationshipResponse(
    RelationshipContext context,
    RelationshipMetrics metrics,
    RelationshipEpistemic epistemic
) {
    public record RelationshipContext(
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

    public record RelationshipMetrics(
        BigDecimal correlation,
        String correlationStatus,
        BigDecimal rSquared,
        String rSquaredStatus,
        Integer pairedObservationCount,
        Integer minPairedRequired,
        Boolean isSufficient,
        String resultState
    ) {}

    public record RelationshipEpistemic(
        String observation,
        String interpretation,
        String limitation,
        String dataQualityStatus,
        String benchmarkLineage,
        String sourceArtifactSha256,
        Long calculationRunId
    ) {}
}
