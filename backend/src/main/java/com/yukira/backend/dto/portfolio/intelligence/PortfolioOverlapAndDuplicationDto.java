package com.yukira.backend.dto.portfolio.intelligence;

import com.yukira.backend.dto.portfolio.lookthrough.PairwiseOverlapResultDto;
import java.math.BigDecimal;
import java.util.List;

public record PortfolioOverlapAndDuplicationDto(
    List<PairwiseOverlapResultDto> pairwiseOverlapMatrix,
    BigDecimal averagePairwiseOverlapPct,
    BigDecimal maxPairwiseOverlapPct,
    String maxOverlapPair,
    List<RepeatedSecurityExposureDto> repeatedSecurities,
    BigDecimal totalRepeatedExposureValue,
    BigDecimal repeatedExposurePct,
    String epistemicExplanation
) {}
