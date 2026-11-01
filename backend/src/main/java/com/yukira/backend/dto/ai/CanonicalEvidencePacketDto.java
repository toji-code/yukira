package com.yukira.backend.dto.ai;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Deterministic evidence packet standard for AI generation and auditability (Tasks 196, 374, 375, 404).
 * Binds AI generation strictly to cryptographic source artifacts and exact observation revisions.
 */
public record CanonicalEvidencePacketDto(
    String packetFingerprint,
    Long schemeOptionId,
    String schemeName,
    String isin,
    String amfiCode,
    String categoryName,
    LocalDate asOfDate,
    ZonedDateTime knowledgeCutoff,
    String primaryBenchmarkCode,
    List<AuthoritativeMetricRecord> metrics,
    Map<String, String> dataQualityFlags,
    List<String> dataLimitations,
    List<String> missingFields,
    ZonedDateTime generatedAt,
    List<String> sourceArtifactHashes,
    List<String> observationRevisionIds,
    String exactEvidenceScope
) {
    public CanonicalEvidencePacketDto(
        String packetFingerprint,
        Long schemeOptionId,
        String schemeName,
        String isin,
        String amfiCode,
        String categoryName,
        LocalDate asOfDate,
        ZonedDateTime knowledgeCutoff,
        String primaryBenchmarkCode,
        List<AuthoritativeMetricRecord> metrics,
        Map<String, String> dataQualityFlags,
        List<String> dataLimitations,
        List<String> missingFields,
        ZonedDateTime generatedAt
    ) {
        this(
            packetFingerprint,
            schemeOptionId,
            schemeName,
            isin,
            amfiCode,
            categoryName,
            asOfDate,
            knowledgeCutoff,
            primaryBenchmarkCode,
            metrics,
            dataQualityFlags,
            dataLimitations,
            missingFields,
            generatedAt,
            List.of(),
            List.of(),
            "EXACT_OBSERVATION_SUBSET_BOUNDED"
        );
    }

    public record AuthoritativeMetricRecord(
        String metricCode,
        String metricName,
        String formattedValue,
        Double numericValue,
        String methodologyStatus,
        String methodologyVersion,
        Long calculationRunId,
        String runFingerprint
    ) {}
}
