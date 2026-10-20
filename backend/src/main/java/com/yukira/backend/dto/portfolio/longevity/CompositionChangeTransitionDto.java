package com.yukira.backend.dto.portfolio.longevity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Observed portfolio composition change between consecutive eligible portfolio disclosures.
 *
 * Epistemic Guardrails:
 * 1. Descriptive Portfolio Change: Measures changes in disclosed weights.
 *    Does not represent regulatory or economic turnover.
 * 2. Scope Comparability: Gated with NON_COMPARABLE_DISCLOSURE_SCOPE_SHIFT
 *    if one or both snapshots represent partial top-holdings disclosures.
 * 3. Temporal Comparability: Gated with NON_COMPARABLE_TEMPORAL_GAP
 *    if the observation interval exceeds 45 days.
 */
public record CompositionChangeTransitionDto(
    LocalDate earlierDate,
    LocalDate laterDate,
    long daysBetween,
    String earlierDisclosureScope,
    String laterDisclosureScope,
    String comparabilityStatus,
    String comparabilityReason,
    boolean isComparable,
    int newEntriesCount,
    BigDecimal newEntriesWeightPct,
    int completeExitsCount,
    BigDecimal completeExitsWeightPct,
    int continuingPositionsCount,
    BigDecimal continuingWeightIncreaseSumPct,
    BigDecimal continuingWeightDecreaseSumPct,
    BigDecimal sumAbsWeightChangesPct,
    BigDecimal halfSumCompositionChangePct,
    BigDecimal unresolvedDiscrepancyPct,
    List<String> topEntries,
    List<String> topExits,
    List<String> largestWeightChanges
) {}
