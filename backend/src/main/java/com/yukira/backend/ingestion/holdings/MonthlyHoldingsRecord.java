package com.yukira.backend.ingestion.holdings;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable DTO representing a single raw/parsed holding row from a monthly portfolio disclosure artifact.
 */
public record MonthlyHoldingsRecord(
    int lineNumber,
    String rawLine,
    String schemeCode,
    String isin,
    String securityName,
    String sector,
    String assetClass,
    BigDecimal quantity,
    BigDecimal marketValue,
    BigDecimal weight,
    LocalDate portfolioDate,
    boolean isValid,
    String parseErrorMessage
) {
    public static MonthlyHoldingsRecord invalid(int lineNumber, String rawLine, String errorMessage) {
        return new MonthlyHoldingsRecord(
            lineNumber, rawLine, null, null, null, null, null, null, null, null, null, false, errorMessage
        );
    }
}
