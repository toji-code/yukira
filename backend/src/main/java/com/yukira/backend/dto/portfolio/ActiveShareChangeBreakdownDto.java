package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Explains observed changes between consecutive historical snapshots across
 * five epistemically separated categories without inventing causality (AGENTS.md):
 *
 * 1. PORTFOLIO_WEIGHT_CHANGE: reported security weights changed between eligible fund disclosures.
 * 2. BENCHMARK_MEMBERSHIP_CHANGE: verified benchmark constituents entered or exited between eligible snapshots.
 * 3. CLASSIFICATION_CHANGE: point-in-time classification changed under documented classification source.
 * 4. COVERAGE_IDENTITY_CHANGE: source coverage, mapping, or data quality changed.
 * 5. UNRESOLVED_CHANGE: available evidence cannot distinguish the underlying cause.
 */
public record ActiveShareChangeBreakdownDto(
    LocalDate earlierDate,
    LocalDate laterDate,
    String changeCategory, // PORTFOLIO_WEIGHT_CHANGE, BENCHMARK_MEMBERSHIP_CHANGE, CLASSIFICATION_CHANGE, COVERAGE_IDENTITY_CHANGE, UNRESOLVED_CHANGE
    String isin,
    String securityName,
    String description,
    BigDecimal priorWeightPct,
    BigDecimal laterWeightPct,
    BigDecimal weightImpactPct,
    String sourceEvidence,
    String epistemicCaution
) {}
