package com.yukira.backend.dto.portfolio.risk.stress;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Descriptive data-quality signal for observation sequences during a stress episode (Tasks 38, 51, 66, 68).
 *
 * Epistemic & Lifecycle Guardrails:
 * - Deterministic signal identity (signalId) and explicit lifecycle state management prevent
 *   repeated calculation runs from creating noisy, duplicated investor warnings (Task 51 & 66).
 * - Distinguishes newly detected signals from persisting or revised source data signals across runs (Task 66).
 * - Explicitly tracks benchmark observation anomalies alongside constituent signals (Task 68).
 * - Distinguishes potential observation anomalies from confirmed data defects without altering mathematical calculations.
 * - Confirmed data defect status strictly requires authoritative source corroboration.
 */
public record StressDataQualitySignalDto(
    String signalId,
    String signalLifecycleState,     // NEWLY_DETECTED, PERSISTING, CHANGED_BY_SOURCE_REVISION, SUPERSEDED_BY_REVISED_SOURCE, INVESTOR_REVIEWED, RESOLVED, NO_ANOMALY_DETECTED
    String signalType,               // OBSERVATION_GAP_DETECTED, POTENTIAL_STALE_NAV, BENCHMARK_OBSERVATION_GAP, POTENTIAL_NON_TRADING, SOURCE_REPORTING_DELAY_POSSIBLE, DATA_QUALITY_REVIEW_REQUIRED, NUMERICAL_DOMAIN_INVALIDITY, NO_ANOMALY_DETECTED
    String severity,                 // INFO, WARNING, REVIEW_RECOMMENDED, CONFIRMED_DATA_DEFECT
    Long schemeOptionId,
    String fundName,
    LocalDate startDate,
    LocalDate endDate,
    int affectedDaysCount,
    String description,
    boolean isCalculationWithholding,
    String firstDetectedRunId,
    OffsetDateTime detectionTimestamp,
    String sourceRevisionReference,
    boolean isBenchmarkSignal,
    String benchmarkCode
) {
    /**
     * Backward-compatible 13-parameter constructor (Task 51 compatibility).
     */
    public StressDataQualitySignalDto(
        String signalId,
        String signalLifecycleState,
        String signalType,
        String severity,
        Long schemeOptionId,
        String fundName,
        LocalDate startDate,
        LocalDate endDate,
        int affectedDaysCount,
        String description,
        boolean isCalculationWithholding,
        String firstDetectedRunId,
        OffsetDateTime detectionTimestamp
    ) {
        this(
            signalId,
            signalLifecycleState != null ? signalLifecycleState : "NEWLY_DETECTED",
            signalType,
            severity,
            schemeOptionId,
            fundName,
            startDate,
            endDate,
            affectedDaysCount,
            description,
            isCalculationWithholding,
            firstDetectedRunId,
            detectionTimestamp != null ? detectionTimestamp : OffsetDateTime.now(),
            null, // sourceRevisionReference
            schemeOptionId == null && fundName == null, // isBenchmarkSignal default
            null // benchmarkCode
        );
    }

    /**
     * Backward-compatible 9-parameter constructor (Task 38 compatibility).
     */
    public StressDataQualitySignalDto(
        String signalType,
        String severity,
        Long schemeOptionId,
        String fundName,
        LocalDate startDate,
        LocalDate endDate,
        int affectedDaysCount,
        String description,
        boolean isCalculationWithholding
    ) {
        this(
            generateSignalId(signalType, schemeOptionId, startDate, endDate),
            "NEWLY_DETECTED",
            signalType,
            severity,
            schemeOptionId,
            fundName,
            startDate,
            endDate,
            affectedDaysCount,
            description,
            isCalculationWithholding,
            null,
            OffsetDateTime.now()
        );
    }

    /**
     * Backward-compatible 3-parameter constructor.
     */
    public StressDataQualitySignalDto(
        String signalType,
        String severity,
        String description
    ) {
        this(signalType, severity, null, null, null, null, 0, description, false);
    }

    public StressDataQualitySignalDto(
        String signalId,
        String signalType,
        String severity,
        Long schemeOptionId,
        String fundName,
        LocalDate startDate,
        LocalDate endDate,
        int affectedDaysCount,
        String description,
        boolean isCalculationWithholding,
        String signalLifecycleState,
        String firstDetectedRunId,
        OffsetDateTime detectionTimestamp
    ) {
        this(
            signalId,
            signalLifecycleState != null ? signalLifecycleState : "NEWLY_DETECTED",
            signalType,
            severity,
            schemeOptionId,
            fundName,
            startDate,
            endDate,
            affectedDaysCount,
            description,
            isCalculationWithholding,
            firstDetectedRunId,
            detectionTimestamp != null ? detectionTimestamp : OffsetDateTime.now(),
            null,
            schemeOptionId == null && fundName == null,
            null
        );
    }

    public String dimension() { return "QUALITY"; }
    public String constituentId() { return schemeOptionId != null ? String.valueOf(schemeOptionId) : (fundName != null ? fundName : "BENCHMARK"); }
    public String message() { return description; }
    public String evidenceContext() { return sourceRevisionReference != null ? sourceRevisionReference : description; }

    public static String generateSignalId(String signalType, Long schemeOptionId, LocalDate start, LocalDate end) {
        return "SIG-" + (signalType != null ? signalType : "ANOMALY")
            + "-" + (schemeOptionId != null ? schemeOptionId : "BENCHMARK")
            + "-" + (start != null ? start : "UNDATED")
            + "-" + (end != null ? end : "UNDATED");
    }
}
