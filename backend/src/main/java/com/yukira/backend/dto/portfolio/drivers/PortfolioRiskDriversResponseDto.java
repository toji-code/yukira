package com.yukira.backend.dto.portfolio.drivers;

import com.yukira.backend.dto.portfolio.lookthrough.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Institutional response envelope for Phase 2P: Risk Drivers & Concentration Investigation.
 * Aggregates underlying securities concentration, cross-fund overlap, sector/market-cap distributions,
 * and deterministic investigation findings.
 */
public record PortfolioRiskDriversResponseDto(
    String evaluationStatus,
    String unavailabilityReason,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    BigDecimal totalAllocationPct,
    boolean isFullyAllocated,
    List<ConstituentFundSummaryDto> constituentFunds,
    ConcentrationSummaryDto concentrationSummary,
    List<ConsolidatedSecurityExposureDto> underlyingSecurities,
    List<PairwiseOverlapResultDto> pairwiseOverlap,
    List<ConsolidatedSectorExposureDto> sectors,
    List<ConsolidatedMarketCapExposureDto> marketCaps,
    List<RiskDriverFindingDto> findings,
    List<String> dataQualityWarnings,
    Map<String, String> methodologyNotes
) {}
