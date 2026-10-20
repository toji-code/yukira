package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Deterministic pairwise Pearson correlation DTO for daily NAV return co-movement.
 *
 * Epistemic Rules:
 * 1. Evaluated strictly over synchronous intersecting trading dates.
 * 2. Requires minimum N >= 30 paired observations for calculation, otherwise reports INSUFFICIENT_DATA.
 * 3. Marked with Candidate methodology disclaimer: correlation measures linear co-movement, not downside protection.
 */
public record PairwiseCorrelationDto(
    Long schemeOptionIdA,
    String fundNameA,
    Long schemeOptionIdB,
    String fundNameB,
    BigDecimal correlation,
    String formattedCorrelation,
    Integer pairedObservationsCount,
    LocalDate commonStartDate,
    LocalDate commonEndDate,
    String calculationStatus,
    String comparabilityStatus,
    List<String> limitations,
    String interpretation
) {}
