package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

public record AmcAllocationDto(
    String amcName,
    BigDecimal totalValue,
    BigDecimal percentageShare,
    int fundCount
) {}
