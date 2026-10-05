package com.yukira.backend.dto.enrichment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HoldingsSummaryDto(
    LocalDate asOfDate,
    Integer reportedHoldingsCount,
    BigDecimal sumReportedWeights,
    String formattedTopConcentration,
    Long sourceArtifactId,
    String sourceTitle,
    String dataQuality,
    String status
) {
    public static HoldingsSummaryDto missing() {
        return new HoldingsSummaryDto(null, 0, null, null, null, null, "MISSING", "MISSING");
    }
}
