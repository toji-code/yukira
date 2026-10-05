package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.util.List;

public record TargetAllocationDto(
    List<Item> items,
    BigDecimal totalTargetWeightPercentage,
    boolean isValidTotal
) {
    public record Item(
        Long id,
        String targetType,
        Long schemeOptionId,
        String categoryName,
        String displayName,
        BigDecimal targetWeightPercentage
    ) {}
}
