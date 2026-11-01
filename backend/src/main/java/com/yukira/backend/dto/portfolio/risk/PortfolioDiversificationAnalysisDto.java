package com.yukira.backend.dto.portfolio.risk;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioDiversificationAnalysisDto(
    BigDecimal weightedStandaloneVolatilityPct,
    BigDecimal portfolioVolatilityProxyPct,
    BigDecimal diversificationEffectPct,
    BigDecimal diversificationRatio,
    String diversificationBenefitClassification,
    String diversificationEvidenceState,
    boolean isEvidenceSupported,
    List<CorrelatedPairDto> highlyCorrelatedPairs,
    List<CorrelatedPairDto> lowOrNegativeCorrelationPairs,
    int sharedSecuritiesCount,
    BigDecimal sharedSecuritiesWeightPct,
    String sectorOverlapContext,
    String diversificationCaveat
) {}
