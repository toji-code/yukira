package com.yukira.backend.universe;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Investor-facing Comparative Screener DTOs (TSK-M1-08).
 *
 * Epistemic & Governance Constraints:
 * 1. Zero synthetic or fabricated figures. If an option is missing or incomplete, explicit exclusion reason is stated.
 * 2. All category benchmarks remain CANDIDATE_METHODOLOGY_MAPPING under Phase 2H.
 * 3. Side-by-side metric comparison strictly preserves canonical formula identities, units, and evaluation windows.
 * 4. Zero composite score, star ratings, or buy/hold recommendations.
 */
public class UniverseScreenerDto {

    public record ScreenerResponse(
        @JsonProperty("asOfDate") LocalDate asOfDate,
        @JsonProperty("totalReferenceOptions") int totalReferenceOptions,
        @JsonProperty("eligibleOptionsCount") int eligibleOptionsCount,
        @JsonProperty("partialOptionsCount") int partialOptionsCount,
        @JsonProperty("zeroHistoryCount") int zeroHistoryCount,
        @JsonProperty("calculatedOptionsCount") int calculatedOptionsCount,
        @JsonProperty("categorySummaries") Map<String, CategorySummary> categorySummaries,
        @JsonProperty("metricDefinitions") List<MetricDefinitionDto> metricDefinitions,
        @JsonProperty("items") List<ScreenerItem> items,
        @JsonProperty("governanceDisclaimers") List<String> governanceDisclaimers,
        @JsonProperty("generatedAt") OffsetDateTime generatedAt,
        @JsonProperty("allTimeSufficientCount") Integer allTimeSufficientCount,
        @JsonProperty("allTimePartialCount") Integer allTimePartialCount,
        @JsonProperty("threeYearSufficientCount") Integer threeYearSufficientCount,
        @JsonProperty("horizon") String horizon,
        @JsonProperty("evaluationWindow") String evaluationWindow,
        @JsonProperty("horizonStartDate") LocalDate horizonStartDate,
        @JsonProperty("horizonThreshold") Integer horizonThreshold
    ) {
        public ScreenerResponse(
            LocalDate asOfDate,
            int totalReferenceOptions,
            int eligibleOptionsCount,
            int partialOptionsCount,
            int zeroHistoryCount,
            int calculatedOptionsCount,
            Map<String, CategorySummary> categorySummaries,
            List<MetricDefinitionDto> metricDefinitions,
            List<ScreenerItem> items,
            List<String> governanceDisclaimers,
            OffsetDateTime generatedAt
        ) {
            this(asOfDate, totalReferenceOptions, eligibleOptionsCount, partialOptionsCount, zeroHistoryCount,
                calculatedOptionsCount, categorySummaries, metricDefinitions, items, governanceDisclaimers,
                generatedAt, null, null, eligibleOptionsCount, "3Y", "3 Years (>= 700 aligned daily observations)",
                asOfDate != null ? asOfDate.minusYears(3) : null, 700);
        }

        public ScreenerResponse(
            LocalDate asOfDate,
            int totalReferenceOptions,
            int eligibleOptionsCount,
            int partialOptionsCount,
            int zeroHistoryCount,
            int calculatedOptionsCount,
            Map<String, CategorySummary> categorySummaries,
            List<MetricDefinitionDto> metricDefinitions,
            List<ScreenerItem> items,
            List<String> governanceDisclaimers,
            OffsetDateTime generatedAt,
            Integer allTimeSufficientCount,
            Integer allTimePartialCount,
            Integer threeYearSufficientCount
        ) {
            this(asOfDate, totalReferenceOptions, eligibleOptionsCount, partialOptionsCount, zeroHistoryCount,
                calculatedOptionsCount, categorySummaries, metricDefinitions, items, governanceDisclaimers,
                generatedAt, allTimeSufficientCount, allTimePartialCount, threeYearSufficientCount,
                "3Y", "3 Years (>= 700 aligned daily observations)",
                asOfDate != null ? asOfDate.minusYears(3) : null, 700);
        }
    }

    public record ScreenerItem(
        @JsonProperty("schemeOptionId") Long schemeOptionId,
        @JsonProperty("schemeId") Long schemeId,
        @JsonProperty("schemeCode") String schemeCode,
        @JsonProperty("schemeName") String schemeName,
        @JsonProperty("amcId") Long amcId,
        @JsonProperty("amcName") String amcName,
        @JsonProperty("amfiCode") String amfiCode,
        @JsonProperty("isin") String isin,
        @JsonProperty("category") EquityUniverseCategory category,
        @JsonProperty("categoryDisplayName") String categoryDisplayName,
        @JsonProperty("distinctNavDates") long distinctNavDates,
        @JsonProperty("sufficiencyStatus") String sufficiencyStatus,
        @JsonProperty("isEligibleFor3Y") boolean isEligibleFor3Y,
        @JsonProperty("exclusionReason") String exclusionReason,
        @JsonProperty("candidateBenchmarkCode") String candidateBenchmarkCode,
        @JsonProperty("candidateBenchmarkName") String candidateBenchmarkName,
        @JsonProperty("benchmarkGovernanceStatus") String benchmarkGovernanceStatus,
        @JsonProperty("benchmarkObservationCount") int benchmarkObservationCount,
        @JsonProperty("benchmarkCoverageStatus") String benchmarkCoverageStatus,
        @JsonProperty("latestCalculationRunId") Long latestCalculationRunId,
        @JsonProperty("asOfDate") LocalDate asOfDate,
        @JsonProperty("knowledgeCutoffTime") OffsetDateTime knowledgeCutoffTime,
        @JsonProperty("calculationStatus") String calculationStatus,
        @JsonProperty("validMetricCount") int validMetricCount,
        @JsonProperty("metrics") Map<String, MetricValueDto> metrics,
        @JsonProperty("allTimeNavDates") Long allTimeNavDates,
        @JsonProperty("earliestNavDate") LocalDate earliestNavDate,
        @JsonProperty("latestNavDate") LocalDate latestNavDate,
        @JsonProperty("dataFreshnessStatus") String dataFreshnessStatus,
        @JsonProperty("planType") String planType,
        @JsonProperty("optionType") String optionType,
        @JsonProperty("horizon") String horizon,
        @JsonProperty("isEligibleForHorizon") Boolean isEligibleForHorizon
    ) {
        public ScreenerItem(
            Long schemeOptionId, Long schemeId, String schemeCode, String schemeName,
            Long amcId, String amcName, String amfiCode, String isin,
            EquityUniverseCategory category, String categoryDisplayName,
            long distinctNavDates, String sufficiencyStatus, boolean isEligibleFor3Y, String exclusionReason,
            String candidateBenchmarkCode, String candidateBenchmarkName, String benchmarkGovernanceStatus,
            int benchmarkObservationCount, String benchmarkCoverageStatus,
            Long latestCalculationRunId, LocalDate asOfDate, OffsetDateTime knowledgeCutoffTime,
            String calculationStatus, int validMetricCount, Map<String, MetricValueDto> metrics,
            Long allTimeNavDates, LocalDate earliestNavDate, LocalDate latestNavDate, String dataFreshnessStatus
        ) {
            this(schemeOptionId, schemeId, schemeCode, schemeName, amcId, amcName, amfiCode, isin,
                category, categoryDisplayName, distinctNavDates, sufficiencyStatus, isEligibleFor3Y, exclusionReason,
                candidateBenchmarkCode, candidateBenchmarkName, benchmarkGovernanceStatus, benchmarkObservationCount, benchmarkCoverageStatus,
                latestCalculationRunId, asOfDate, knowledgeCutoffTime, calculationStatus, validMetricCount, metrics,
                allTimeNavDates, earliestNavDate, latestNavDate, dataFreshnessStatus,
                "DIRECT", "GROWTH", "3Y", isEligibleFor3Y);
        }

        public ScreenerItem(
            Long schemeOptionId, Long schemeId, String schemeCode, String schemeName,
            Long amcId, String amcName, String amfiCode, String isin,
            EquityUniverseCategory category, String categoryDisplayName,
            long distinctNavDates, String sufficiencyStatus, boolean isEligibleFor3Y, String exclusionReason,
            String candidateBenchmarkCode, String candidateBenchmarkName, String benchmarkGovernanceStatus,
            int benchmarkObservationCount, String benchmarkCoverageStatus,
            Long latestCalculationRunId, LocalDate asOfDate, OffsetDateTime knowledgeCutoffTime,
            String calculationStatus, int validMetricCount, Map<String, MetricValueDto> metrics
        ) {
            this(schemeOptionId, schemeId, schemeCode, schemeName, amcId, amcName, amfiCode, isin,
                category, categoryDisplayName, distinctNavDates, sufficiencyStatus, isEligibleFor3Y, exclusionReason,
                candidateBenchmarkCode, candidateBenchmarkName, benchmarkGovernanceStatus, benchmarkObservationCount, benchmarkCoverageStatus,
                latestCalculationRunId, asOfDate, knowledgeCutoffTime, calculationStatus, validMetricCount, metrics,
                null, null, null, null,
                "DIRECT", "GROWTH", "3Y", isEligibleFor3Y);
        }

        public ScreenerItem(
            Long schemeOptionId, Long schemeId, String schemeCode, String schemeName,
            Long amcId, String amcName, String amfiCode, String isin,
            EquityUniverseCategory category, String categoryDisplayName,
            long distinctNavDates, String sufficiencyStatus, boolean isEligibleFor3Y, String exclusionReason,
            String candidateBenchmarkCode, String candidateBenchmarkName, String benchmarkGovernanceStatus,
            int benchmarkObservationCount, String benchmarkCoverageStatus,
            Long latestCalculationRunId, LocalDate asOfDate, String calculationStatus,
            Map<String, MetricValueDto> metrics
        ) {
            this(schemeOptionId, schemeId, schemeCode, schemeName, amcId, amcName, amfiCode, isin,
                category, categoryDisplayName, distinctNavDates, sufficiencyStatus, isEligibleFor3Y, exclusionReason,
                candidateBenchmarkCode, candidateBenchmarkName, benchmarkGovernanceStatus, benchmarkObservationCount, benchmarkCoverageStatus,
                latestCalculationRunId, asOfDate, null, calculationStatus,
                metrics != null ? (int) metrics.values().stream().filter(m -> m != null && m.numericValue() != null).count() : 0,
                metrics, null, null, null, null,
                "DIRECT", "GROWTH", "3Y", isEligibleFor3Y);
        }
    }

    public record MetricValueDto(
        @JsonProperty("metricCode") String metricCode,
        @JsonProperty("numericValue") BigDecimal numericValue,
        @JsonProperty("formattedValue") String formattedValue,
        @JsonProperty("periodType") String periodType,
        @JsonProperty("units") String units,
        @JsonProperty("calculationStatus") String calculationStatus,
        @JsonProperty("percentileRank") Double percentileRank,
        @JsonProperty("formattedPercentile") String formattedPercentile,
        @JsonProperty("peerDenominator") Integer peerDenominator,
        @JsonProperty("rankingDirection") String rankingDirection,
        @JsonProperty("peerCohortStatus") String peerCohortStatus,
        @JsonProperty("unavailableReason") String unavailableReason,
        @JsonProperty("errorMessage") String errorMessage
    ) {
        public MetricValueDto(
            String metricCode,
            BigDecimal numericValue,
            String formattedValue,
            String periodType,
            String units,
            String calculationStatus,
            String errorMessage
        ) {
            this(metricCode, numericValue, formattedValue, periodType, units, calculationStatus, null, null, null, null, null, errorMessage != null ? errorMessage : null, errorMessage);
        }

        public MetricValueDto(
            String metricCode,
            BigDecimal numericValue,
            String formattedValue,
            String periodType,
            String units,
            String calculationStatus
        ) {
            this(metricCode, numericValue, formattedValue, periodType, units, calculationStatus, null);
        }

        public MetricValueDto withPercentile(
            Double percentileRank,
            String formattedPercentile,
            Integer peerDenominator,
            String rankingDirection,
            String peerCohortStatus,
            String unavailableReason
        ) {
            return new MetricValueDto(
                this.metricCode,
                this.numericValue,
                this.formattedValue,
                this.periodType,
                this.units,
                this.calculationStatus,
                percentileRank,
                formattedPercentile,
                peerDenominator,
                rankingDirection,
                peerCohortStatus,
                unavailableReason != null ? unavailableReason : this.unavailableReason,
                this.errorMessage
            );
        }
    }

    public record CategorySummary(
        @JsonProperty("category") EquityUniverseCategory category,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("candidateBenchmarkCode") String candidateBenchmarkCode,
        @JsonProperty("totalOptions") int totalOptions,
        @JsonProperty("eligibleCount") int eligibleCount,
        @JsonProperty("partialCount") int partialCount,
        @JsonProperty("calculatedCount") int calculatedCount
    ) {}

    public record MetricDefinitionDto(
        @JsonProperty("metricCode") String metricCode,
        @JsonProperty("metricName") String metricName,
        @JsonProperty("category") String category,
        @JsonProperty("units") String units,
        @JsonProperty("evaluationWindow") String evaluationWindow,
        @JsonProperty("benchmarkDependent") boolean benchmarkDependent,
        @JsonProperty("formulaDescription") String formulaDescription,
        @JsonProperty("epistemicStatus") String epistemicStatus,
        @JsonProperty("rankingDirection") String rankingDirection,
        @JsonProperty("percentileInterpretation") String percentileInterpretation
    ) {
        public MetricDefinitionDto(
            String metricCode,
            String metricName,
            String category,
            String units,
            String evaluationWindow,
            boolean benchmarkDependent,
            String formulaDescription,
            String epistemicStatus
        ) {
            this(metricCode, metricName, category, units, evaluationWindow, benchmarkDependent, formulaDescription, epistemicStatus, null, null);
        }
    }
}

