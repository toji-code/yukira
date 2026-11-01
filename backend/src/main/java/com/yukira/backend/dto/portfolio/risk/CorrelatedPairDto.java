package com.yukira.backend.dto.portfolio.risk;

import java.math.BigDecimal;

public record CorrelatedPairDto(
    Long schemeOptionIdA,
    String fundNameA,
    Long schemeOptionIdB,
    String fundNameB,
    BigDecimal correlation,
    int pairwiseObservationCount,
    int overlapHoldingCount,
    String interpretation
) {}

