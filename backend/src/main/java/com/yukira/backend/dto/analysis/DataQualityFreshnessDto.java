package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Factual, institutional DTO conveying empirical Data Quality & Freshness evidence
 * across NAV observation history, monthly holdings disclosures, and analytical metric eligibility.
 *
 * Epistemic Rules:
 * 1. Zero financial math or estimations in presentation tier.
 * 2. NAV freshness is evaluated strictly independent of holdings freshness.
 * 3. Holdings reporting date is strictly separated from availabilityTime (PIT cutoff) and ingestion timestamps.
 * 4. Raw reported holdings weights are never forced or normalized to 100%.
 * 5. Explicitly documents whether important metrics are calculable based on available inputs.
 */
public record DataQualityFreshnessDto(
    Long schemeOptionId,
    String schemeCode,
    String schemeName,
    String amcName,
    String planType,
    String optionType,
    String amfiCode,
    String isin,
    LocalDate evaluationDate,
    OffsetDateTime knowledgeCutoff,
    NavFreshnessDetailDto navEvidence,
    HoldingsFreshnessDetailDto holdingsEvidence,
    List<MetricEligibilityItemDto> metricEligibility,
    List<DataQualityFindingDto> qualityFindings,
    SevenQuestionsAnswerDto sevenQuestions
) {

    public record NavFreshnessDetailDto(
        LocalDate latestNavDate,
        Long observationCount,
        String usableDateRange,
        String freshnessState, // CURRENT, STALE, UNAVAILABLE, UNKNOWN
        String cadencePolicy,
        Long observationAgeDays,
        Long sourceArtifactId,
        String sha256Hash,
        List<String> warnings,
        String explanation
    ) {}

    public record HoldingsFreshnessDetailDto(
        LocalDate latestReportingDate,
        OffsetDateTime availabilityTime,
        OffsetDateTime ingestionTimestamp,
        String freshnessState, // CURRENT, STALE, UNAVAILABLE, UNKNOWN
        String coverageState,  // COMPLETE, PARTIAL, UNAVAILABLE
        String cadencePolicy,
        Long reportingAgeDays,
        Integer positionCount,
        BigDecimal rawSumWeights,
        Double rawSumWeightsPercentage,
        Double unclassifiedExposurePercentage,
        Integer snapshotCount,
        List<String> usableComparisonPeriods,
        Long sourceArtifactId,
        String sha256Hash,
        String sourceUri,
        String discoveryStatus, // OFFICIAL_DISCLOSURE_ACCESSIBLE, DISCOVERY_RESTRICTED, SOURCE_UNAVAILABLE, CONTROLLED_FIXTURE
        List<String> warnings,
        String explanation
    ) {}

    public record MetricEligibilityItemDto(
        String metricCode,
        String metricName,
        String governanceTier, // APPROVED, VALIDATED, CANDIDATE
        String eligibilityState, // ELIGIBLE, INSUFFICIENT_HISTORY, INPUT_UNAVAILABLE, UNVALIDATED_CANDIDATE
        String requiredEvidence,
        String availableEvidence,
        String explanation
    ) {}

    public record DataQualityFindingDto(
        String category, // NAV_CADENCE, HOLDINGS_DISCLOSURE, SECURITY_IDENTITY, SECTOR_CLASSIFICATION, SNAPSHOT_HISTORY
        String severity, // INFO, WARNING, LIMITATION
        String title,
        String explanation
    ) {}

    public record SevenQuestionsAnswerDto(
        String q1LatestNavObservation,
        String q2LatestHoldingsReportingDate,
        String q3HoldingsSourceAvailabilityDate,
        String q4IsDataCurrentRelativeToCadence,
        String q5CompletenessOfDisclosedHoldings,
        String q6MetricCalculabilityStatus,
        String q7EvidenceOrLimitationsToInvestigate
    ) {}
}
