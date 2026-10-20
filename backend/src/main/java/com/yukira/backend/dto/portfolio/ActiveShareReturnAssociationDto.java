package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Historical association between Point-in-Time Off-Benchmark Allocation and
 * observed subsequent/contemporaneous Active Returns (R_A = R_p - R_b).
 *
 * Epistemic Rules (AGENTS.md):
 * 1. Association is strictly descriptive (DESCRIPTIVE_ASSOCIATION_ONLY).
 * 2. Off-benchmark allocation does not prove or establish manager skill or alpha causation.
 * 3. Does not substitute for Active Share when benchmark weights are incomplete.
 */
public record ActiveShareReturnAssociationDto(
    LocalDate periodStartDate,
    LocalDate periodEndDate,
    BigDecimal startOffBenchmarkAllocationPct,
    BigDecimal endOffBenchmarkAllocationPct,
    BigDecimal fundReturnPct,
    BigDecimal benchmarkReturnPct,
    BigDecimal activeReturnPct,
    long sampleDays,
    String associationLabel, // "DESCRIPTIVE_ASSOCIATION_ONLY"
    String methodologicalCaveat
) {}
