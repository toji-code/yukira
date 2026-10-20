package com.yukira.backend.ingestion.holdings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Machine-readable result contract for monthly holdings source acquisition and ingestion jobs.
 */
public record MonthlyHoldingsAcquisitionResult(
    MonthlyHoldingsSourceDescriptor descriptor,
    Status status,
    String statusMessage,
    Integer httpStatusCode,
    Long sourceArtifactId,
    String sha256Hash,
    Long byteSize,
    LocalDate portfolioDate,
    Long schemeOptionId,
    Long snapshotId,
    Integer revisionSeq,
    Integer parsedRows,
    Integer validRows,
    Integer holdingsCount,
    BigDecimal sumWeights,
    OffsetDateTime executionTime,
    List<String> messages
) {
    public enum Status {
        ACQUIRED,
        INGESTED,
        DUPLICATE,
        RESTATED_REVISION,
        SOURCE_UNAVAILABLE,
        HTTP_ERROR,
        INVALID_RESPONSE,
        UNSUPPORTED_FORMAT,
        SCHEME_UNRESOLVED,
        SOURCE_NOT_CONFIGURED,
        PARSE_FAILED,
        VALIDATION_FAILED,
        NOT_YET_PUBLISHED,
        AMBIGUOUS_SOURCE
    }

    public boolean isSuccessful() {
        return status == Status.INGESTED || status == Status.DUPLICATE || status == Status.RESTATED_REVISION;
    }
}
