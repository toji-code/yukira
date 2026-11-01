package com.yukira.backend.dto.watchlist;

import java.time.OffsetDateTime;

/**
 * Immutable DTO representing a historical version of an investor's research thesis (Task 120, 121).
 */
public record InvestorResearchThesisHistoryDto(
    Long id,
    Long watchlistId,
    Long investorId,
    Long schemeId,
    Long schemeOptionId,
    Integer thesisVersion,
    String thesisStatus,
    String reasonSaved,
    String investigationQuestion,
    String nextStep,
    String associatedFindingId,
    String evidenceContext,
    String reconsiderationConditions,
    OffsetDateTime createdAt
) {}

