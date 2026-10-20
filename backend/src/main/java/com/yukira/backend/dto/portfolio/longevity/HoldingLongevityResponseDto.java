package com.yukira.backend.dto.portfolio.longevity;

import java.time.LocalDate;
import java.util.List;

/**
 * Authoritative response envelope for Point-in-Time Holding Longevity,
 * Portfolio Turnover & Position Persistence Analysis.
 *
 * Epistemic & Methodological Guardrails (AGENTS.md & Phase 2H/2K):
 * 1. Descriptive Portfolio Analysis: Describes empirical presence across authentic disclosures.
 *    Does not compute manager skill scores or predictive style forecasts.
 * 2. Point-in-Time (PIT) Look-Ahead Bias Prevention:
 *    portfolioDate <= asOfDate AND availabilityTime <= knowledgeCutoff.
 * 3. Scope Shift Awareness: Disappearance from a partial top-holdings disclosure is classified
 *    as NOT_OBSERVED_IN_PARTIAL_DISCLOSURE, never as an actual portfolio exit.
 * 4. Strict Turnover Gating: Transaction-level turnover is gated when buy/sell logs and daily
 *    average net assets are missing; descriptive composition changes are clearly labeled.
 */
public record HoldingLongevityResponseDto(
    Long schemeOptionId,
    String fundName,
    String planType,
    String optionType,
    String amfiCode,
    String schemeCode,
    String analysisStatus,
    String statusReason,
    String methodologyVersion,
    String calculationRunId,
    String knowledgeCutoff,
    int totalSnapshotsFound,
    int eligibleSnapshotsCount,
    int skippedPeriodsCount,
    LocalDate observationWindowStartDate,
    LocalDate observationWindowEndDate,
    List<HoldingSnapshotSummaryDto> snapshots,
    List<PositionTenureBucketSummaryDto> tenureBuckets,
    List<PositionLongevityItemDto> persistentHoldings,
    List<PositionLongevityItemDto> allHoldings,
    List<CompositionChangeTransitionDto> transitions,
    TurnoverEvaluationDto turnoverEvaluation,
    List<String> dataQualityWarnings,
    List<String> investigationQuestions,
    List<String> methodologyNotes
) {}
