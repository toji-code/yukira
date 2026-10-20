package com.yukira.backend.dto.portfolio.longevity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Metadata and disclosure scope for an individual portfolio snapshot.
 */
public record HoldingSnapshotSummaryDto(
    Long snapshotId,
    LocalDate portfolioDate,
    OffsetDateTime availabilityTime,
    int reportedHoldingsCount,
    BigDecimal sumReportedWeightsPct,
    String disclosureScope,
    String sourceArtifactSha256,
    String sourceUri
) {}
