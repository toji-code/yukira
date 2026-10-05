package com.yukira.backend.dto.enrichment;

import java.time.LocalDate;

public record FundManagerDto(
    String name,
    String role,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate asOfDate,
    Long sourceArtifactId,
    String sourceTitle,
    String dataQuality,
    String status
) {
    public static FundManagerDto missing() {
        return new FundManagerDto(null, null, null, null, null, null, null, "MISSING", "MISSING");
    }
}
