package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Immutable response DTO representing Portfolio Construction Risk and Holdings Data Reliability.
 *
 * Epistemic & Calculation Standards:
 * 1. Weight Reconciliation: Never silently normalizes weights to 100%. Discloses exact reported sum,
 *    gross vs net asset accounting deviations, and source rounding margins.
 * 2. Issuer Concentration: Aggregates multiple instruments issued by the same parent company under canonical issuer.
 * 3. Concentration Metrics:
 *    - HHI (Herfindahl-Hirschman Index) = sum_{j=1}^M (w_j * 100)^2 (Standard scale: 0 to 10,000).
 *    - Effective Number of Holdings N_eff = 10,000 / HHI.
 * 4. Evidence-Based Comparability: Replaces arbitrary coverage thresholds with multi-factor evidence:
 *    disclosed weight delta, classification coverage delta, time interval, and disclosure completeness type.
 * 5. Data Gap Transparency: Explicitly highlights historical gaps (e.g. 29 months between Jan 2024 and Jun 2026).
 * 6. Governance Status: CANDIDATE — not validated for production investment advice.
 */
public record PortfolioConstructionRiskResponseDto(
    Long schemeOptionId,
    String fundName,
    String amfiCode,
    String schemeCode,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    String status,
    String message,
    DataReliabilitySummaryDto dataReliability,
    IssuerConcentrationDto issuerConcentration,
    SectorConcentrationDto sectorConcentration,
    MarketCapReliabilityDto marketCapReliability,
    List<SnapshotReliabilityDetailDto> snapshotHistory,
    List<HistoricalComparabilityPairDto> comparabilityMatrix,
    List<InvestigationItemDto> investigationItems,
    PortfolioConstructionMethodologyDto methodology
) {
    public record DataReliabilitySummaryDto(
        LocalDate snapshotDate,
        OffsetDateTime availabilityTime,
        BigDecimal sumReportedWeights,
        String sumReportedWeightsPct,
        Integer totalHoldingsDisclosed,
        Integer uniqueSecuritiesCount,
        Integer duplicateHoldingsCount,
        List<DuplicateHoldingDetailDto> duplicateHoldings,
        Integer invalidWeightsCount,
        String weightReconciliationStatus,
        String weightReconciliationCode,
        String weightReconciliationExplanation,
        BigDecimal mappedWeightPct,
        BigDecimal unmappedWeightPct,
        BigDecimal classifiedWeightPct,
        BigDecimal unclassifiedWeightPct,
        BigDecimal cashAndEquivalentsWeightPct,
        BigDecimal equityWeightPct,
        String completenessType,
        String sourceArtifactUri,
        String sourceArtifactSha256,
        Long sourceArtifactByteSize
    ) {}

    public record DuplicateHoldingDetailDto(
        Long securityId,
        String canonicalName,
        String sector,
        Integer occurrenceCount,
        BigDecimal totalReportedWeightPct,
        String note
    ) {}

    public record IssuerConcentrationDto(
        Integer totalIssuersDisclosed,
        BigDecimal top5IssuerWeightPct,
        BigDecimal top10IssuerWeightPct,
        BigDecimal top20IssuerWeightPct,
        BigDecimal issuerHhi,
        BigDecimal effectiveNumberOfIssuers,
        String hhiConcentrationTier,
        BigDecimal securityHhi,
        BigDecimal effectiveNumberOfSecurities,
        List<IssuerExposureDetailDto> topIssuers
    ) {}

    public record IssuerExposureDetailDto(
        Integer rank,
        String issuerName,
        BigDecimal totalWeightPct,
        Integer instrumentsHeldCount,
        String primarySector,
        String primaryAssetClass
    ) {}

    public record SectorConcentrationDto(
        Integer totalSectorsDisclosed,
        BigDecimal top1SectorWeightPct,
        String top1SectorName,
        BigDecimal top3SectorWeightPct,
        BigDecimal unclassifiedSectorWeightPct,
        String taxonomyType,
        String taxonomyDisclosure,
        List<SectorExposureDetailDto> sectors
    ) {}

    public record SectorExposureDetailDto(
        Integer rank,
        String sectorName,
        BigDecimal weightPct,
        Integer holdingsCount
    ) {}

    public record MarketCapReliabilityDto(
        BigDecimal largeCapWeightPct,
        BigDecimal midCapWeightPct,
        BigDecimal smallCapWeightPct,
        BigDecimal cashWeightPct,
        BigDecimal debtOtherWeightPct,
        BigDecimal unclassifiedWeightPct,
        String classificationStandard,
        String classificationProvenance,
        String historicalPitStatus,
        String methodologyLimitation
    ) {}

    public record SnapshotReliabilityDetailDto(
        Long snapshotId,
        LocalDate portfolioDate,
        OffsetDateTime availabilityTime,
        Integer holdingsCount,
        Integer uniqueSecuritiesCount,
        BigDecimal sumReportedWeightsPct,
        String weightReconciliationCode,
        String completenessType,
        Boolean hasDuplicateSecurities,
        String sourceArtifactSha256
    ) {}

    public record HistoricalComparabilityPairDto(
        LocalDate earlierDate,
        LocalDate laterDate,
        Long intervalDays,
        BigDecimal earlierSumWeightPct,
        BigDecimal laterSumWeightPct,
        BigDecimal weightDeltaPct,
        Integer earlierHoldingsCount,
        Integer laterHoldingsCount,
        Integer holdingsDeltaCount,
        String earlierCompleteness,
        String laterCompleteness,
        Boolean isDataGapExceeded,
        String comparabilityStatus,
        List<String> reasonCodes,
        String assessmentSummary
    ) {}

    public record InvestigationItemDto(
        String category,
        String severity,
        String title,
        String empiricalEvidence,
        String dueDiligenceQuestion
    ) {}

    public record PortfolioConstructionMethodologyDto(
        String methodologyVersion,
        String governanceStatus,
        String hhiFormula,
        String effectiveHoldingsFormula,
        String weightReconciliationPolicy,
        String comparabilityAssessmentStandard,
        String liquidityDiscretionDisclaimer
    ) {}
}
