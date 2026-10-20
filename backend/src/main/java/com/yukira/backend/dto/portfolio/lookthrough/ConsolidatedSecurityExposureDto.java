package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;
import java.util.List;

public record ConsolidatedSecurityExposureDto(
    String canonicalName,
    String isin,
    String sector,
    String assetClass,
    String marketCapCategory,
    BigDecimal consolidatedWeightPct,
    int fundCount,
    List<FundSecurityContributionDto> contributions,
    String identityConfidence,
    String classificationProvenance
) {}
