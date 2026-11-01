package com.yukira.backend.universe;

/**
 * Standardized exclusion reason codes for mutual fund schemes and options
 * evaluated against the YUKIRA initial analytical reference universe.
 */
public enum UniverseExclusionReason {

    PLAN_NOT_DIRECT(
        "Plan type is not DIRECT; only DIRECT plans are eligible for the analytical reference universe."
    ),
    OPTION_NOT_GROWTH(
        "Option type is not GROWTH; only GROWTH options are eligible for the analytical reference universe."
    ),
    CATEGORY_OUT_OF_SCOPE(
        "Scheme category is outside the 5 initial core equity categories (Flexi Cap, Large Cap, Large & Mid Cap, Mid Cap, Small Cap)."
    ),
    CATEGORY_UNCLASSIFIED(
        "Scheme category metadata is missing or unclassified in source feeds."
    ),
    RECORD_INACTIVE(
        "Scheme option is marked INACTIVE or defunct in master catalog."
    ),
    DATA_QUALITY_LIMITED(
        "Scheme option is marked DATA_QUALITY_LIMITED due to source conflict."
    );

    private final String defaultExplanation;

    UniverseExclusionReason(String defaultExplanation) {
        this.defaultExplanation = defaultExplanation;
    }

    public String getDefaultExplanation() {
        return defaultExplanation;
    }
}
