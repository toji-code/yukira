package com.yukira.backend.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class CalculationDtos {

    public record ObservationItemDto(
        @JsonProperty("effective_date") String effectiveDate,
        @JsonProperty("value") Double value,
        @JsonProperty("availability_time") String availabilityTime,
        @JsonProperty("revision_seq") Integer revisionSeq
    ) {}

    public record CalculationRequestDto(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("scheme_id") String schemeId,
        @JsonProperty("benchmark_id") String benchmarkId,
        @JsonProperty("as_of_date") String asOfDate,
        @JsonProperty("knowledge_cutoff_time") String knowledgeCutoffTime,
        @JsonProperty("methodology_version") String methodologyVersion,
        @JsonProperty("metric_codes") List<String> metricCodes,
        @JsonProperty("nav_series") List<ObservationItemDto> navSeries,
        @JsonProperty("benchmark_series") List<ObservationItemDto> benchmarkSeries,
        @JsonProperty("risk_free_series") List<ObservationItemDto> riskFreeSeries,
        @JsonProperty("parameters") Map<String, Object> parameters
    ) {
        public CalculationRequestDto(
            String requestId,
            String schemeId,
            String benchmarkId,
            String asOfDate,
            String knowledgeCutoffTime,
            String methodologyVersion,
            List<String> metricCodes,
            List<ObservationItemDto> navSeries,
            List<ObservationItemDto> benchmarkSeries,
            Map<String, Object> parameters
        ) {
            this(requestId, schemeId, benchmarkId, asOfDate, knowledgeCutoffTime, methodologyVersion,
                 metricCodes, navSeries, benchmarkSeries, null, parameters);
        }
    }

    public record MetricOutputItemDto(
        @JsonProperty("metric_code") String metricCode,
        @JsonProperty("period_type") String periodType,
        @JsonProperty("numeric_value") BigDecimal numericValue,
        @JsonProperty("string_value") String stringValue,
        @JsonProperty("units") String units,
        @JsonProperty("status") String status,
        @JsonProperty("diagnostics") Map<String, Object> diagnostics,
        @JsonProperty("error_message") String errorMessage
    ) {}

    public record CalculationResponseDto(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("as_of_date") String asOfDate,
        @JsonProperty("knowledge_cutoff_time") String knowledgeCutoffTime,
        @JsonProperty("engine_version") String engineVersion,
        @JsonProperty("git_commit_hash") String gitCommitHash,
        @JsonProperty("execution_duration_ms") Double executionDurationMs,
        @JsonProperty("results") List<MetricOutputItemDto> results,
        @JsonProperty("status") String status,
        @JsonProperty("error_message") String errorMessage
    ) {}

    public record HealthResponseDto(
        @JsonProperty("status") String status,
        @JsonProperty("engine_version") String engineVersion,
        @JsonProperty("candidate_metrics") List<String> candidateMetrics,
        @JsonProperty("methodology_status") String methodologyStatus,
        @JsonProperty("empirical_findings") String empiricalFindings
    ) {}
}
