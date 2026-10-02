package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HoldingDto(
    String securityName,
    BigDecimal weight,
    String category,
    LocalDate asOfDate,
    String source
) {}
