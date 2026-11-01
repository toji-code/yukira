package com.yukira.backend.dto.portfolio.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HoldingsReconciliationItemDto(
    Long schemeOptionId,
    String schemeName,
    String planType,
    String optionType,
    String amfiCode,
    String isin,
    BigDecimal reportedUnits,
    BigDecimal ledgerDerivedUnits,
    BigDecimal unitDifference,
    String reconciliationStatus, // RECONCILED, PARTIALLY_RECONCILED, UNRECONCILED, INSUFFICIENT_HISTORY, SOURCE_CONFLICT
    Integer totalTransactionsCount,
    LocalDate earliestTransactionDate,
    LocalDate latestTransactionDate,
    String reconciliationNotes
) {}

