package com.yukira.backend.dto.portfolio.snapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

public record CreatePortfolioResearchSnapshotRequest(
    String snapshotTitle,
    Long allocationSnapshotId,
    LocalDate asOfDate,
    ZonedDateTime knowledgeCutoff,
    BigDecimal xirrValue,
    BigDecimal totalValue,
    String cashFlowSummaryJson,
    String researchThesis,
    String investigationNotes
) {}

