package com.yukira.backend.ingestion.fbil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Immutable parsed record from an authoritative FBIL Treasury Bill benchmark yield source.
 */
public record FbilTBillRecord(
    LocalDate effectiveDate,
    String benchmarkCode,
    String tenor,
    BigDecimal quotedYield,
    String daycountConvention,
    OffsetDateTime availabilityTime,
    Integer revisionSeq
) {
    public FbilTBillRecord {
        if (effectiveDate == null) {
            throw new IllegalArgumentException("effectiveDate must not be null");
        }
        if (quotedYield == null) {
            throw new IllegalArgumentException("quotedYield must not be null");
        }
        if (benchmarkCode == null || benchmarkCode.isBlank()) {
            benchmarkCode = "FBIL_91D_TBILL";
        }
        if (daycountConvention == null || daycountConvention.isBlank()) {
            daycountConvention = "ACT_365";
        }
        if (revisionSeq == null || revisionSeq < 1) {
            revisionSeq = 1;
        }
    }
}
