package com.yukira.backend.dto.enrichment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AumDto(
    BigDecimal amount,
    String formattedAmount,
    String currency,
    String unit,
    LocalDate asOfDate,
    Long sourceArtifactId,
    String sourceTitle,
    String dataQuality,
    String status
) {
    public static AumDto missing() {
        return new AumDto(null, null, "INR", "Crores", null, null, null, "MISSING", "MISSING");
    }
}
