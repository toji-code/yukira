package com.yukira.backend.universe;

import java.time.LocalDate;

/**
 * Institutional analytical coverage horizons supported by YUKIRA (Phase A3).
 *
 * Epistemic & Quantitative Rules:
 * 1. THREE_YEAR (3Y): Authoritative reference horizon under frozen Phase 2H methodology.
 *    Threshold is frozen at 700 distinct trading days. Status: APPROVED.
 * 2. FIVE_YEAR (5Y): Expanded horizon for mature funds. Evaluated against candidate threshold of
 *    1,200 distinct trading days (~240 days/yr * 5). Status: CANDIDATE_METHODOLOGY.
 * 3. TEN_YEAR (10Y): Multi-cycle horizon for mature funds. Evaluated against candidate threshold of
 *    2,400 distinct trading days (~240 days/yr * 10). Status: CANDIDATE_METHODOLOGY.
 * 4. Distinct trading days must be verified from authentic latest-revision observations.
 * 5. Inception bounds must be respected: a fund launched within a horizon cannot physically satisfy it.
 */
public enum AnalyticalHorizon {

    THREE_YEAR("3Y", 3, 700, "YUKIRA_V1_M1_700_DAY_THRESHOLD", true, "APPROVED"),
    FIVE_YEAR("5Y", 5, 1200, "YUKIRA_V1_CANDIDATE_5Y_1200_DAY_THRESHOLD", false, "CANDIDATE"),
    TEN_YEAR("10Y", 10, 2400, "YUKIRA_V1_CANDIDATE_10Y_2400_DAY_THRESHOLD", false, "CANDIDATE");

    private final String code;
    private final int years;
    private final int defaultThreshold;
    private final String ruleVersion;
    private final boolean frozen;
    private final String methodologyStatus;

    AnalyticalHorizon(
        String code,
        int years,
        int defaultThreshold,
        String ruleVersion,
        boolean frozen,
        String methodologyStatus
    ) {
        this.code = code;
        this.years = years;
        this.defaultThreshold = defaultThreshold;
        this.ruleVersion = ruleVersion;
        this.frozen = frozen;
        this.methodologyStatus = methodologyStatus;
    }

    public String getCode() {
        return code;
    }

    public int getYears() {
        return years;
    }

    public int getDefaultThreshold() {
        return defaultThreshold;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public String getMethodologyStatus() {
        return methodologyStatus;
    }

    /**
     * Calculates the window start date relative to a reference date.
     */
    public LocalDate calculateStartDate(LocalDate referenceDate) {
        if (referenceDate == null) {
            throw new IllegalArgumentException("referenceDate must not be null");
        }
        return referenceDate.minusYears(years);
    }

    /**
     * Parses a string representation into an AnalyticalHorizon.
     * Supports codes (3Y, 5Y, 10Y), numbers (3, 5, 10), and enum names.
     * Defaults to THREE_YEAR if null or unrecognized.
     */
    public static AnalyticalHorizon fromCode(String code) {
        if (code == null || code.isBlank()) {
            return THREE_YEAR;
        }
        String clean = code.trim().toUpperCase();
        return switch (clean) {
            case "5Y", "5", "FIVE", "FIVE_YEAR", "P5Y" -> FIVE_YEAR;
            case "10Y", "10", "TEN", "TEN_YEAR", "P10Y" -> TEN_YEAR;
            case "3Y", "3", "THREE", "THREE_YEAR", "P3Y" -> THREE_YEAR;
            default -> {
                for (AnalyticalHorizon h : values()) {
                    if (h.name().equalsIgnoreCase(clean) || h.code.equalsIgnoreCase(clean)) {
                        yield h;
                    }
                }
                yield THREE_YEAR;
            }
        };
    }
}
