package com.yukira.backend.ingestion.amfi;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AmfiNavRecord(
    int lineNumber,
    String rawLine,
    String schemeCode,
    String schemeName,
    String isinGrowth,
    String isinReinvestment,
    BigDecimal navValue,
    LocalDate navDate,
    boolean isValid,
    String parseErrorMessage
) {
    public static AmfiNavRecord valid(
        int lineNumber,
        String rawLine,
        String schemeCode,
        String schemeName,
        String isinGrowth,
        String isinReinvestment,
        BigDecimal navValue,
        LocalDate navDate
    ) {
        return new AmfiNavRecord(
            lineNumber, rawLine, schemeCode, schemeName, isinGrowth, isinReinvestment, navValue, navDate, true, null
        );
    }

    public static AmfiNavRecord malformed(int lineNumber, String rawLine, String errorMessage) {
        return new AmfiNavRecord(
            lineNumber, rawLine, null, null, null, null, null, null, false, errorMessage
        );
    }
}
