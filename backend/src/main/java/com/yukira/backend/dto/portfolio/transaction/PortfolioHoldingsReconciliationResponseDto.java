package com.yukira.backend.dto.portfolio.transaction;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public record PortfolioHoldingsReconciliationResponseDto(
    Integer totalHoldingsEvaluated,
    Integer reconciledCount,
    Integer partiallyReconciledCount,
    Integer unreconciledCount,
    Integer insufficientHistoryCount,
    Integer sourceConflictCount,
    String overallStatus, // RECONCILED, PARTIAL_DISCREPANCIES, UNRECONCILED
    LocalDate asOfDate,
    ZonedDateTime evaluatedAt,
    List<HoldingsReconciliationItemDto> holdings,
    List<String> reconciliationWarnings
) {}

