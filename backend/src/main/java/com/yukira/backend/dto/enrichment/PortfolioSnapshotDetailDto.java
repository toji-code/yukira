package com.yukira.backend.dto.enrichment;

import com.yukira.backend.dto.portfolio.HoldingDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Detailed Portfolio Snapshot DTO including provenance, total coverage, data quality, and position list.
 */
public record PortfolioSnapshotDetailDto(
    Long schemeOptionId,
    String schemeName,
    LocalDate portfolioDate,
    BigDecimal reportedTotalNetAssets,
    String formattedTotalNetAssets,
    Integer reportedHoldingsCount,
    BigDecimal sumReportedWeights,
    String formattedCoverage,
    Long sourceArtifactId,
    String sourceSha256Hash,
    String sourceDocumentTitle,
    String dataQualityStatus,
    String freshnessStatus,
    List<HoldingDto> holdings,
    List<String> dataQualityLimitations
) {
    public static PortfolioSnapshotDetailDto missing(Long schemeOptionId, String schemeName) {
        return new PortfolioSnapshotDetailDto(
            schemeOptionId, schemeName, null, null, null, 0, null, null,
            null, null, "SEBI Monthly Portfolio Disclosure", "MISSING", "UNAVAILABLE",
            List.of(), List.of("Pending SEBI monthly portfolio disclosure ingestion.")
        );
    }
}
