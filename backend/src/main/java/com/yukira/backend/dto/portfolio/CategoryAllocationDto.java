package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

public record CategoryAllocationDto(
    String category,
    BigDecimal totalValue,
    BigDecimal percentageShare,
    int fundCount
) {}
