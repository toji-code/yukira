package com.yukira.backend.ingestion.holdings;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Deterministic refresh result for an individual mutual fund in a monthly holdings refresh run.
 */
public record FundRefreshResult(
    String amcCode,
    String amfiCode,
    String schemeName,
    Long schemeOptionId,
    MonthlyHoldingsAcquisitionResult.Status status,
    String statusMessage,
    Long sourceArtifactId,
    String sha256Hash,
    LocalDate reportingDate,
    Integer revisionSeq,
    Integer holdingsCount,
    BigDecimal sumWeights,
    Long durationMs
) {
    public boolean isSuccessful() {
        return status == MonthlyHoldingsAcquisitionResult.Status.INGESTED
            || status == MonthlyHoldingsAcquisitionResult.Status.DUPLICATE
            || status == MonthlyHoldingsAcquisitionResult.Status.RESTATED_REVISION;
    }
}
