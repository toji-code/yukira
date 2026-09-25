package com.yukira.backend.ingestion.nse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Immutable parsed record from an authoritative NSE Indices Total Return Index (TRI) source.
 */
public record NiftyTriRecord(
    LocalDate effectiveDate,
    String indexName,
    BigDecimal totalReturnsIndex,
    OffsetDateTime availabilityTime,
    Integer revisionSeq
) {
    public NiftyTriRecord {
        if (effectiveDate == null) {
            throw new IllegalArgumentException("effectiveDate must not be null");
        }
        if (totalReturnsIndex == null) {
            throw new IllegalArgumentException("totalReturnsIndex must not be null");
        }
        if (indexName == null || indexName.isBlank()) {
            indexName = "Nifty 500";
        }
        if (revisionSeq == null || revisionSeq < 1) {
            revisionSeq = 1;
        }
    }
}
