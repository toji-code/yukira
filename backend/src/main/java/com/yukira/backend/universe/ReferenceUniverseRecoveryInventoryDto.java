package com.yukira.backend.universe;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Complete empirical inventory of historical NAV coverage across the 193 reference-universe options (Phase A1).
 *
 * Epistemic Guardrails:
 * - Total reference options strictly equals the 193 Direct-Growth options in the 5 authorized categories.
 * - Categorizes each option strictly according to frozen 700-day sufficiency rules and source-backed evidence.
 * - Distinctly separates all-time observation depth from 3-year observation sufficiency.
 */
public record ReferenceUniverseRecoveryInventoryDto(
    LocalDate windowStart,
    LocalDate windowEnd,
    int sufficiencyThreshold,
    int totalReferenceOptions,
    int sufficientCount,
    int partialCount,
    int storedArtifactReadyCount,
    int inceptionBoundedCount,
    int historicalGap3YCount,
    int olderDiscontinuityCount,
    Map<String, Integer> categoryBreakdown,
    List<NavRecoveryCandidateDto> candidates
) {}
