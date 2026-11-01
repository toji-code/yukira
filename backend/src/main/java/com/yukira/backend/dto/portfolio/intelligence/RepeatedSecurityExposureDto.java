package com.yukira.backend.dto.portfolio.intelligence;

import java.math.BigDecimal;
import java.util.List;

public record RepeatedSecurityExposureDto(
    String canonicalName,
    String isin,
    String sector,
    int fundCount,
    BigDecimal combinedExposureValue,
    BigDecimal combinedExposurePct,
    List<FundSecurityExposureContributionDto> contributingFunds
) {}
