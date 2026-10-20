package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PairwiseOverlapResultDto(
    Long fundAId,
    String fundAName,
    Long fundBId,
    String fundBName,
    BigDecimal overlapWeightPct,
    int sharedSecurityCount,
    LocalDate observationDateA,
    LocalDate observationDateB,
    int temporalDeltaDays,
    String comparabilityState,
    List<SharedSecurityItemDto> topSharedSecurities
) {}
