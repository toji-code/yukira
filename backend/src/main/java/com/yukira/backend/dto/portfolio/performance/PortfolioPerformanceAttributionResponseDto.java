package com.yukira.backend.dto.portfolio.performance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Complete, authoritative portfolio performance attribution and contribution response.
 *
 * Epistemic Invariants:
 * 1. Fully deterministic calculations computed in Spring Boot backend.
 * 2. Unexplained residuals are preserved and disclosed without synthetic adjustments.
 * 3. Point-in-time observations respect knowledge cutoff.
 * 4. Clear separation between fund-unit performance attribution and underlying-security look-through.
 */
public record PortfolioPerformanceAttributionResponseDto(
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff,
    PortfolioFinancialDecompositionDto financialDecomposition,
    List<SchemePerformanceContributionDto> schemeContributions,
    ConsolidatedRealizedPerformanceDto realizedPerformance,
    ConsolidatedUnrealizedPerformanceDto unrealizedPerformance,
    DividendIncomeContributionDto dividendContribution,
    CashFlowTimingContextDto cashFlowTiming,
    XirrAmbiguityAndDiagnosticsDto xirrDiagnostics,
    List<HoldingLotPerformanceDto> holdingLots,
    List<String> researchFindings,
    List<String> methodologyDisclaimers
) {}

