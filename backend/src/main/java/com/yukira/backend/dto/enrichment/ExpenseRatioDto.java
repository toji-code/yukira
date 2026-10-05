package com.yukira.backend.dto.enrichment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseRatioDto(
    BigDecimal expenseRatio,
    String formattedPercentage,
    String planType,
    String optionType,
    BigDecimal regularPlanRatio,
    String formattedRegularPlanPercentage,
    LocalDate asOfDate,
    Long sourceArtifactId,
    String sourceTitle,
    String dataQuality,
    String status
) {
    public static ExpenseRatioDto missing(String planType, String optionType) {
        return new ExpenseRatioDto(
            null, null, planType, optionType, null, null, null, null, null, "MISSING", "MISSING"
        );
    }
}
