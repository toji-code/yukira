package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CandidateSimulationRequestDto(
    List<ResearchPortfolioFundItemDto> currentFunds,
    Long candidateSchemeOptionId,
    BigDecimal proposedAllocationPct,
    String fundingMethod,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoff
) {}
