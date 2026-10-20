package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable DTO representing auditable Fund-versus-Benchmark Historical Composition Comparison,
 * Holdings Overlap, Sector Spreads, and Brinson-Fachler Data-Readiness Assessment.
 *
 * Epistemic Guardrail (AGENTS.md):
 * Structural holdings overlap and sector differences are distinct from return attribution.
 * YUKIRA explicitly informs investors that composition spreads do NOT constitute manager skill.
 */
public record BenchmarkCompositionComparisonResponseDto(
    Long schemeOptionId,
    String fundName,
    String planType,
    String optionType,
    LocalDate portfolioDate,
    int fundHoldingsCount,
    BigDecimal sumFundWeightsPct,

    Long benchmarkId,
    String benchmarkCode,
    String benchmarkName,
    LocalDate benchmarkCompositionDate,
    int benchmarkConstituentCount,
    int mappedConstituentCount,
    int unmappedConstituentCount,
    String weightCoverageStatus,
    BigDecimal sumDisclosedBenchmarkWeightsPct,

    int commonConstituentCount,
    int fundSpecificCount,
    int benchmarkSpecificCount,
    BigDecimal holdingsOverlapPct,
    BigDecimal unmappedHoldingsWeightPct,

    List<ActiveSecurityPositionDto> activePositions,
    List<SectorExposureComparisonDto> sectorExposures,
    List<MarketCapExposureComparisonDto> marketCapExposures,

    String brinsonAttributionStatus,
    String brinsonBlockerReason,
    String investorOrientation,
    List<String> dueDiligencePrompts,

    Long sourceArtifactId,
    String sourceArtifactSha256,
    String sourceUrl,
    String knowledgeCutoff,
    String methodologyVersion
) {
    public record ActiveSecurityPositionDto(
        String isin,
        String symbol,
        String companyName,
        BigDecimal fundWeightPct,
        BigDecimal benchmarkWeightPct,
        BigDecimal activeWeightPct,
        String marketCapCategory,
        String industry,
        String positionStatus
    ) {}

    public record SectorExposureComparisonDto(
        String sectorName,
        BigDecimal fundSectorWeightPct,
        BigDecimal benchmarkSectorWeightPct,
        BigDecimal activeSectorExposurePct,
        int fundStockCount,
        int benchmarkStockCount
    ) {}

    public record MarketCapExposureComparisonDto(
        String category,
        BigDecimal fundWeightPct,
        BigDecimal benchmarkWeightPct,
        BigDecimal activeExposurePct
    ) {}
}
