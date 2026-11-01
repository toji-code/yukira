package com.yukira.backend.dto.portfolio.risk;

import java.math.BigDecimal;

public record UnvaluedPositionDto(
    Long schemeOptionId,
    String fundName,
    String amfiCode,
    BigDecimal units,
    String reason
) {}

