package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;
import java.util.List;

public record HoldingRiskEvidenceDto(
    Long schemeOptionId,
    String fundName,
    BigDecimal portfolioWeight,
    Double yukiraScore,
    String scoreStatus,
    boolean hasRiskEvidence,
    String asOfDate,
    List<HoldingRiskMetricDto> metrics
) {}
