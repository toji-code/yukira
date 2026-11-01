package com.yukira.backend.dto.portfolio.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateManualTransactionRequest(
    Long schemeOptionId,
    String transactionType, // PURCHASE, REDEMPTION, SWITCH_IN, SWITCH_OUT, SIP_PURCHASE, STP_PURCHASE, STP_REDEMPTION, DIVIDEND_PAYOUT, DIVIDEND_REINVESTMENT, BONUS, OTHER
    LocalDate transactionDate,
    LocalDate settlementDate,
    BigDecimal units,
    BigDecimal nav,
    BigDecimal grossAmount,
    BigDecimal chargesOrTaxes,
    BigDecimal dividendAmount,
    String originalDescription,
    String sourceDocumentRef
) {}

