package com.yukira.backend.dto.portfolio.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

public record InvestorTransactionDto(
    Long id,
    Long schemeOptionId,
    String schemeName,
    String planType,
    String optionType,
    String amfiCode,
    String isin,
    String transactionType,
    LocalDate transactionDate,
    LocalDate settlementDate,
    BigDecimal units,
    BigDecimal nav,
    BigDecimal grossAmount,
    BigDecimal netAmount,
    BigDecimal chargesOrTaxes,
    BigDecimal dividendAmount,
    String sourceAttribution,
    String sourceDocumentRef,
    String originalDescription,
    String importBatchId,
    String reconciliationStatus,
    Long reversalReferenceId,
    Boolean isReversed,
    String dedupHash,
    ZonedDateTime createdAt
) {}

