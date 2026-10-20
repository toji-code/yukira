package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;

public record SharedSecurityItemDto(
    String securityName,
    String isin,
    String sector,
    BigDecimal weightInA,
    BigDecimal weightInB,
    BigDecimal overlapWeightPct
) {}
