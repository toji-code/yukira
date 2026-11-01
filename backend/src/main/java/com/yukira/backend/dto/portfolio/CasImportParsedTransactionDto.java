package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CasImportParsedTransactionDto(
    String rawSchemeName,
    String folioNumber,
    String isin,
    String amfiCode,
    LocalDate transactionDate,
    String rawTypeDescription,
    BigDecimal units,
    BigDecimal nav,
    BigDecimal amount
) {}

