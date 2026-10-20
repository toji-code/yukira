package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PortfolioSnapshotSummaryDto(
    Long snapshotId,
    Long schemeOptionId,
    String schemeName,
    String amfiCode,
    LocalDate portfolioDate,
    OffsetDateTime availabilityTime,
    Integer revisionSeq,
    Boolean isLatestRevision,
    Integer reportedHoldingsCount,
    BigDecimal sumReportedWeightsPct,
    String sumReportedWeightsPctFormatted,
    Long sourceArtifactId,
    String sourceArtifactSha256,
    String sourceProvider,
    String analyticalEligibility,
    String qualityState
) {}
