package com.yukira.backend.dto.portfolio.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CasImportTransactionReviewDto(
    Long portfolioImportId,
    String sourceFilename,
    String statementPeriod,
    Integer totalTransactionsDetected,
    Integer resolvedTransactionsCount,
    Integer unresolvedTransactionsCount,
    Integer duplicateTransactionsCount,
    String importStatus, // IMPORTED, PARTIALLY_IMPORTED, REQUIRES_REVIEW, FAILED
    List<CasImportTransactionItemDto> transactions,
    List<String> reviewNotes
) {
    public record CasImportTransactionItemDto(
        Long id,
        String rawSchemeName,
        String folioNumber,
        String isin,
        String amfiCode,
        LocalDate transactionDate,
        String rawTypeDescription,
        String normalizedType,
        BigDecimal units,
        BigDecimal nav,
        BigDecimal amount,
        Long resolvedSchemeOptionId,
        String resolvedSchemeName,
        String resolutionStatus,
        String resolutionNotes
    ) {}
}

