package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

public record PortfolioHoldingRequest(
    Long schemeOptionId,
    BigDecimal units,
    BigDecimal costBasisAmount
) {}
