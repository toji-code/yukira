package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

public record PortfolioHoldingRequest(
    Long schemeOptionId,
    BigDecimal units,
    BigDecimal costBasisAmount,
    String reasonSaved,
    String investigationQuestion,
    String nextStep
) {
    public PortfolioHoldingRequest(Long schemeOptionId, BigDecimal units, BigDecimal costBasisAmount) {
        this(schemeOptionId, units, costBasisAmount, null, null, null);
    }
}
