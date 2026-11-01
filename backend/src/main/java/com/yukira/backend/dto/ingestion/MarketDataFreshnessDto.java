package com.yukira.backend.dto.ingestion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Institutional aggregated market data freshness and operational visibility contract for YUKIRA V1.
 *
 * Epistemic & Methodological Guardrails:
 * 1. Strictly decouples temporal source freshness (recency against current trading days)
 *    from historical observation sufficiency (frozen 700 distinct trading days threshold).
 * 2. Decouples operational visibility and ingestion status from investment conclusions:
 *    A data ingestion issue or latency is never translated into a negative fund performance score.
 * 3. Transparently distinguishes between missing observations, stale feeds, and unavailable data sources.
 * 4. Never reports a source as fresh simply because an HTTP job or pipeline ran without error.
 * 5. Prohibits zero-substitution for missing holdings disclosures.
 */
public record MarketDataFreshnessDto(
    LocalDate referenceDate,
    String overallHealthStatus, // CURRENT, PARTIAL, STALE, UNAVAILABLE
    NavFreshnessSummary navFreshness,
    Map<String, BenchmarkDataFreshnessDto> benchmarkFreshnessByCategory,
    FbilDataFreshnessDto riskFreeFreshness,
    UniverseFreshnessSummary universeFreshness,
    HoldingsFreshnessSummary holdingsFreshness,
    MetricsCalculationFreshnessSummary metricsFreshness,
    CasProcessingStatusSummary casProcessingStatus,
    List<SourceDomainFreshnessDto> domainSummaries,
    OffsetDateTime evaluatedAt,
    String governanceDisclaimer
) {
    public record NavFreshnessSummary(
        String freshnessState, // FRESH, PARTIAL, STALE, UNAVAILABLE
        LocalDate latestNavDate,
        Long daysLag,
        long totalObservationsInLedger,
        int referenceUniverseTotalOptions,
        int referenceUniverseSufficient3Y,
        int referenceUniversePartial3Y,
        String explanation
    ) {}

    public record UniverseFreshnessSummary(
        int totalEligibleReferenceSchemes,
        int largeCapCount,
        int largeMidCapCount,
        int midCapCount,
        int smallCapCount,
        int flexiCapCount,
        String syncStatus, // CURRENT, STALE, UNRESOLVED
        String explanation
    ) {}

    public record HoldingsFreshnessSummary(
        int totalReferenceUniverse,
        int schemesWithUsableHoldings,
        LocalDate latestReportedPortfolioDate,
        Map<String, String> amcCoverageStatus,
        String overallHoldingsState, // CURRENT, PARTIAL, STALE, MISSING, DATA_QUALITY_LIMITED, UNRESOLVED
        String explanation
    ) {}

    public record MetricsCalculationFreshnessSummary(
        long totalCalculationRuns,
        int schemesWithCalculated3YMetrics,
        String calculationEngineVersion,
        String status, // CURRENT, OPERATIONAL, DEGRADED
        String explanation
    ) {}

    public record CasProcessingStatusSummary(
        long totalImportedStatements,
        String engineStatus, // OPERATIONAL, DEGRADED
        List<String> supportedRegistrars,
        String explanation
    ) {}

    public record SourceDomainFreshnessDto(
        String domainIdentifier, // "AMFI_NAV", "NSE_TRI_BENCHMARK", "MUTUAL_FUND_UNIVERSE", "AMC_PORTFOLIO_HOLDINGS", "HISTORICAL_METRIC_CALCULATION", "INVESTOR_CAS_IMPORT"
        String displayName,
        LocalDate latestObservationDate,
        OffsetDateTime latestSuccessfulIngestionTime,
        OffsetDateTime latestAttemptedIngestionTime,
        String status, // "CURRENT", "PARTIAL", "STALE", "MISSING", "DATA_QUALITY_LIMITED", "OPERATIONAL", "UNAVAILABLE"
        long recordsOrSchemesCovered,
        long totalTargetDenominator,
        String limitationExplanation,
        String sourceProvenanceOrRunId
    ) {}
}
