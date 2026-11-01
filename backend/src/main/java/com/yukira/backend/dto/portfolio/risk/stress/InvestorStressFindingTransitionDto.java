package com.yukira.backend.dto.portfolio.risk.stress;

import java.time.OffsetDateTime;

/**
 * Immutable DTO representing a finding lifecycle transition event (Task 101, 102).
 */
public record InvestorStressFindingTransitionDto(
    Long id,
    Long findingRecordId,
    String findingId,
    String previousStatus,
    String newStatus,
    String transitionBy,
    String transitionReason,
    String investorNotes,
    String calculationRunCode,
    OffsetDateTime createdAt
) {}

