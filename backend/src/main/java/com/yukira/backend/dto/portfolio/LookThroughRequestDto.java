package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.util.Map;

public record LookThroughRequestDto(
    Map<Long, BigDecimal> fundWeights
) {}
