package com.yukira.backend.ingestion.holdings;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO exposing the point-in-time holdings verification status for a canonical pilot fund.
 */
public record PilotFundHoldingsStatusDto(
    Long schemeOptionId,
    String amcCode,
    String schemeName,
    String amfiCode,
    String isin,
    LocalDate latestPortfolioDate,
    Integer latestPositionCount,
    BigDecimal latestSumWeights,
    Long latestSourceArtifactId,
    String latestArtifactSha256,
    String freshnessStatus,
    String qualityState,
    String provenanceType,
    int totalSnapshotsInDb
) {}
