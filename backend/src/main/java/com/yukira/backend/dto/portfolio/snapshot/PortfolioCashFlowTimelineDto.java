package com.yukira.backend.dto.portfolio.snapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioCashFlowTimelineDto(
    Long investorId,
    LocalDate asOfDate,
    BigDecimal netCumulativeInvested,
    BigDecimal currentPortfolioValue,
    BigDecimal totalCashInflows,
    BigDecimal totalCashOutflows,
    List<CashFlowEventDto> cashFlows
) {
    public record CashFlowEventDto(
        Long transactionId,
        LocalDate transactionDate,
        String transactionType,
        String classification,
        String schemeName,
        BigDecimal amount,
        BigDecimal units,
        BigDecimal nav,
        String flowDirection // INFLOW, OUTFLOW, INTERNAL_TRANSFER
    ) {}
}

