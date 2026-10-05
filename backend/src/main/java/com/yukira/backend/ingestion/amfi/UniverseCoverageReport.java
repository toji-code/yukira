package com.yukira.backend.ingestion.amfi;

import java.util.List;
import java.util.Map;

/**
 * Machine-readable report representing mutual fund universe catalog coverage,
 * data quality findings, and historical NAV status.
 */
public record UniverseCoverageReport(
    long totalAmcs,
    long totalSchemes,
    long totalPlans,
    long totalOptions,
    long activeOptionsCount,
    long inactiveOptionsCount,
    long directOptionsCount,
    long regularOptionsCount,
    long growthOptionsCount,
    long idcwOptionsCount,
    long missingIsinCount,
    long missingAmfiCodeCount,
    long missingCategoryCount,
    long missingSubcategoryCount,
    long missingBenchmarkCount,
    long duplicateIdentityFindings,
    long malformedRecordFindings,
    long ingestionFailureCount,
    Map<String, Object> controlledSampleNavCoverage,
    List<String> qualityNotes
) {}
