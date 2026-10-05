package com.yukira.backend.dto.portfolio;

/**
 * Request payload for evaluating portfolio goal alignment and horizon planning.
 */
public record PortfolioGoalAlignmentRequest(
    String goalCategory,
    Integer horizonYears,
    String riskTolerance,
    String investmentMode,
    String fundCategory
) {
    public PortfolioGoalAlignmentRequest {
        if (goalCategory == null || goalCategory.isBlank()) {
            goalCategory = "WEALTH_CREATION";
        }
        if (horizonYears == null || horizonYears <= 0) {
            horizonYears = 5;
        }
        if (riskTolerance == null || riskTolerance.isBlank()) {
            riskTolerance = "MODERATE";
        }
        if (investmentMode == null || investmentMode.isBlank()) {
            investmentMode = "SIP";
        }
        if (fundCategory == null || fundCategory.isBlank()) {
            fundCategory = "ANY";
        }
    }
}
