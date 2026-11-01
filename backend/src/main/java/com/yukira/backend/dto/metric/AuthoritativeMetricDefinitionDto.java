package com.yukira.backend.dto.metric;

import java.util.List;
import java.util.Map;

/**
 * YUKIRA Authoritative Metric Registry Specification DTO.
 * Consolidates the complete specification required across backend and frontend tiers.
 *
 * Epistemic Governance Rules:
 * 1. Only RET-02 is OPERATIONAL_BASELINE / APPROVED.
 * 2. All other implemented metrics are IMPLEMENTED_CANDIDATE.
 * 3. Never fabricate or extrapolate observations.
 * 4. Zero financial math on the presentation tier.
 */
public record AuthoritativeMetricDefinitionDto(
    String metricCode,
    String canonicalName,
    String analyticalDimension,
    String categoryLabel,
    String financialMeaning,
    String mathematicalDefinition,
    String formulaDisplay,
    List<String> requiredInputs,
    String returnConvention,
    String annualizationConvention,
    String measurementHorizon,
    String benchmarkDependency,
    String primaryBenchmarkCode,
    String riskFreeDependency,
    String riskFreeRateProxy,
    String rankingDirection,
    String availabilityRequirements,
    String missingDataBehavior,
    String methodologyVersion,
    String governanceStatus,
    Map<String, String> assumptions,
    String limitations,
    String investigationQuestion,
    String unit,
    Boolean isDiagnostic,
    String parentMetricCode,
    List<String> aliases
) {
    /**
     * Backward-compatible 23-parameter constructor for existing callers.
     */
    public AuthoritativeMetricDefinitionDto(
        String metricCode,
        String canonicalName,
        String analyticalDimension,
        String categoryLabel,
        String financialMeaning,
        String mathematicalDefinition,
        String formulaDisplay,
        List<String> requiredInputs,
        String returnConvention,
        String annualizationConvention,
        String measurementHorizon,
        String benchmarkDependency,
        String primaryBenchmarkCode,
        String riskFreeDependency,
        String riskFreeRateProxy,
        String rankingDirection,
        String availabilityRequirements,
        String missingDataBehavior,
        String methodologyVersion,
        String governanceStatus,
        Map<String, String> assumptions,
        String limitations,
        String investigationQuestion
    ) {
        this(
            metricCode,
            canonicalName,
            analyticalDimension,
            categoryLabel,
            financialMeaning,
            mathematicalDefinition,
            formulaDisplay,
            requiredInputs,
            returnConvention,
            annualizationConvention,
            measurementHorizon,
            benchmarkDependency,
            primaryBenchmarkCode,
            riskFreeDependency,
            riskFreeRateProxy,
            rankingDirection,
            availabilityRequirements,
            missingDataBehavior,
            methodologyVersion,
            governanceStatus,
            assumptions,
            limitations,
            investigationQuestion,
            deriveUnit(returnConvention, metricCode),
            false,
            null,
            List.of()
        );
    }

    private static String deriveUnit(String returnConvention, String code) {
        if ("CALENDAR_DAYS".equalsIgnoreCase(returnConvention) || "RSK-04".equalsIgnoreCase(code)) return "DAYS";
        if ("PERCENTAGE_POINTS".equalsIgnoreCase(returnConvention) || "SQUARED_WEIGHT_SUM".equalsIgnoreCase(returnConvention)) return "POINTS";
        if ("RATIO".equalsIgnoreCase(returnConvention) || (code != null && (code.startsWith("RAT-") || code.startsWith("MKT-01") || code.startsWith("MKT-02")))) return "RATIO";
        return "PERCENTAGE";
    }
}
