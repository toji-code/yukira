package com.yukira.backend.dto.portfolio.transaction;

public record ReverseTransactionRequest(
    String reversalReason,
    String notes
) {}

