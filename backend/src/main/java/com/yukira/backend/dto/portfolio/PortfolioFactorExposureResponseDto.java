package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Immutable response DTO representing Portfolio Factor Exposure, Style Drift,
 * and Evidence-Based Return Attribution status for a scheme option.
 */
public record PortfolioFactorExposureResponseDto(
    Long schemeOptionId,
    String fundName,
    String amfiCode,
    String schemeCode,
    String benchmarkCode,
    String benchmarkName,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoffTime,
    String analysisStatus,
    String statusReason,
    SnapshotExposureDetailDto currentExposure,
    List<HistoricalSnapshotExposureDto> driftHistory,
    List<ObservedExposureShiftDto> largestObservedShifts,
    FactorReturnAttributionDto returnAttribution,
    FactorExposureEpistemicDto epistemic
) {

    public record SnapshotExposureDetailDto(
        Long snapshotId,
        LocalDate portfolioDate,
        OffsetDateTime availabilityTime,
        Long sourceArtifactId,
        String sourceArtifactSha256,
        int totalHoldingsDisclosed,
        BigDecimal sumReportedWeightsPct,
        String sumReportedWeightsPctFormatted,
        BigDecimal mappedWeightPct,
        String mappedWeightPctFormatted,
        BigDecimal unmappedWeightPct,
        String unmappedWeightPctFormatted,
        BigDecimal classificationCoveragePct,
        String classificationCoveragePctFormatted,
        MarketCapExposureDto marketCap,
        FactorDimensionsSummaryDto otherFactors,
        ConcentrationSummaryDto concentration,
        List<SectorWeightDto> topSectors,
        String completenessState,
        List<String> coverageLimitations
    ) {}

    public record MarketCapExposureDto(
        BigDecimal largeCapWeightPct,
        String largeCapWeightPctFormatted,
        BigDecimal midCapWeightPct,
        String midCapWeightPctFormatted,
        BigDecimal smallCapWeightPct,
        String smallCapWeightPctFormatted,
        BigDecimal cashWeightPct,
        String cashWeightPctFormatted,
        BigDecimal debtOtherWeightPct,
        String debtOtherWeightPctFormatted,
        BigDecimal unclassifiedWeightPct,
        String unclassifiedWeightPctFormatted,
        String classificationMethodology,
        String classificationStatus,
        String sourceRankingPeriod,
        LocalDate sourcePublicationDate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Long sourceArtifactId,
        String sourceArtifactSha256,
        int totalClassifiedHoldingsCount,
        int totalUnclassifiedHoldingsCount
    ) {}

    public record FactorDimensionsSummaryDto(
        String valueStatus,
        String qualityStatus,
        String momentumStatus,
        String documentation
    ) {}

    public record ConcentrationSummaryDto(
        BigDecimal top5WeightPct,
        String top5WeightPctFormatted,
        BigDecimal top10WeightPct,
        String top10WeightPctFormatted,
        BigDecimal hhi,
        String hhiClassification
    ) {}

    public record SectorWeightDto(
        String sector,
        BigDecimal weightPct,
        String weightPctFormatted,
        boolean isUnclassified
    ) {}

    public record HistoricalSnapshotExposureDto(
        Long snapshotId,
        LocalDate portfolioDate,
        OffsetDateTime availabilityTime,
        int totalHoldingsDisclosed,
        BigDecimal sumReportedWeightsPct,
        BigDecimal mappedWeightPct,
        BigDecimal classificationCoveragePct,
        BigDecimal largeCapWeightPct,
        BigDecimal midCapWeightPct,
        BigDecimal smallCapWeightPct,
        BigDecimal cashWeightPct,
        BigDecimal debtOtherWeightPct,
        BigDecimal unclassifiedWeightPct,
        BigDecimal top5WeightPct,
        BigDecimal top10WeightPct,
        BigDecimal hhi,
        String classificationStatus,
        String rankingPeriod,
        SnapshotDriftDeltaDto deltaFromPrevious
    ) {}

    public record SnapshotDriftDeltaDto(
        LocalDate previousDate,
        BigDecimal largeCapDeltaPct,
        String largeCapDeltaPctFormatted,
        BigDecimal midCapDeltaPct,
        String midCapDeltaPctFormatted,
        BigDecimal smallCapDeltaPct,
        String smallCapDeltaPctFormatted,
        BigDecimal cashDeltaPct,
        String cashDeltaPctFormatted,
        BigDecimal top10DeltaPct,
        String top10DeltaPctFormatted,
        BigDecimal hhiDelta,
        BigDecimal coverageDeltaPct,
        String coverageDeltaPctFormatted,
        boolean isComparable,
        String comparabilityNote,
        StyleDriftAttributionDto styleDriftAttribution
    ) {}

    public record StyleDriftAttributionDto(
        BigDecimal largeCapRebalanceDeltaPct,
        BigDecimal largeCapReclassifyDeltaPct,
        BigDecimal largeCapCoverageDeltaPct,
        BigDecimal largeCapNetDeltaPct,
        BigDecimal midCapRebalanceDeltaPct,
        BigDecimal midCapReclassifyDeltaPct,
        BigDecimal midCapCoverageDeltaPct,
        BigDecimal midCapNetDeltaPct,
        BigDecimal smallCapRebalanceDeltaPct,
        BigDecimal smallCapReclassifyDeltaPct,
        BigDecimal smallCapCoverageDeltaPct,
        BigDecimal smallCapNetDeltaPct,
        String attributionMethodology,
        String attributionExplanation,
        List<ReclassifiedHoldingDto> reclassifiedHoldings
    ) {}

    public record ReclassifiedHoldingDto(
        String isin,
        String companyName,
        String previousCategory,
        Integer previousRank,
        String currentCategory,
        Integer currentRank,
        BigDecimal portfolioWeightPct,
        String portfolioWeightPctFormatted,
        String shiftDescription
    ) {}

    public record ObservedExposureShiftDto(
        String shiftDimension,
        String description,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal magnitudeDeltaPct,
        String magnitudeDeltaPctFormatted,
        boolean coincidesWithCoverageChange,
        String evidenceNote
    ) {}

    public record FactorReturnAttributionDto(
        String modelStatus,
        String modelSpecification,
        List<AttributionPrerequisiteItemDto> missingPrerequisites,
        List<String> epistemicLimitations,
        String governanceDisclaimer,
        ActiveReturnAttributionResponseDto activeAttribution
    ) {
        public FactorReturnAttributionDto(
            String modelStatus,
            String modelSpecification,
            List<AttributionPrerequisiteItemDto> missingPrerequisites,
            List<String> epistemicLimitations,
            String governanceDisclaimer
        ) {
            this(modelStatus, modelSpecification, missingPrerequisites, epistemicLimitations, governanceDisclaimer, null);
        }
    }

    public record AttributionPrerequisiteItemDto(
        String prerequisiteCode,
        String prerequisiteName,
        String currentStatus,
        String technicalRequirement
    ) {}

    public record FactorExposureEpistemicDto(
        String methodologyVersion,
        String governanceStatus,
        String disclaimer,
        List<String> factualObservations,
        List<String> investigationQuestions
    ) {}
}
