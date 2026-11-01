package com.yukira.backend.universe;

/**
 * Result of evaluating a SchemeOption against the initial 5-category Direct-Growth
 * analytical reference universe criteria.
 */
public record UniverseEvaluationResult(
    Long schemeOptionId,
    String amfiCode,
    String isin,
    String schemeName,
    String amcName,
    String planType,
    String optionType,
    String status,
    boolean included,
    EquityUniverseCategory category,
    UniverseExclusionReason exclusionReason,
    String explanation
) {
    public static UniverseEvaluationResult ofIncluded(
        Long schemeOptionId,
        String amfiCode,
        String isin,
        String schemeName,
        String amcName,
        String planType,
        String optionType,
        String status,
        EquityUniverseCategory category
    ) {
        return new UniverseEvaluationResult(
            schemeOptionId,
            amfiCode,
            isin,
            schemeName,
            amcName,
            planType,
            optionType,
            status,
            true,
            category,
            null,
            "Included in initial 5-category Direct-Growth analytical reference universe (" + category.getDisplayName() + ")."
        );
    }

    public static UniverseEvaluationResult ofExcluded(
        Long schemeOptionId,
        String amfiCode,
        String isin,
        String schemeName,
        String amcName,
        String planType,
        String optionType,
        String status,
        UniverseExclusionReason reason,
        String customExplanation
    ) {
        return new UniverseEvaluationResult(
            schemeOptionId,
            amfiCode,
            isin,
            schemeName,
            amcName,
            planType,
            optionType,
            status,
            false,
            null,
            reason,
            customExplanation != null ? customExplanation : reason.getDefaultExplanation()
        );
    }
}
