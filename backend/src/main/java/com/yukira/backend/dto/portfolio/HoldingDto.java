package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable DTO representing a portfolio holding with deterministic formatted weight,
 * classification, security identifier (ISIN), as-of date, and cryptographic provenance.
 */
public record HoldingDto(
    String securityName,
    BigDecimal weight,
    String formattedWeight,
    String category,
    String assetClass,
    String sector,
    String isin,
    LocalDate asOfDate,
    String source,
    String dataQuality
) {
    /**
     * Backward-compatible constructor for legacy tests and callers.
     */
    public HoldingDto(String securityName, BigDecimal weight, String category, LocalDate asOfDate, String source) {
        this(
            securityName,
            weight,
            weight != null ? String.format("%.2f%%", weight.multiply(BigDecimal.valueOf(100))) : null,
            category,
            category,
            null,
            null,
            asOfDate,
            source,
            "VERIFIED"
        );
    }
}
