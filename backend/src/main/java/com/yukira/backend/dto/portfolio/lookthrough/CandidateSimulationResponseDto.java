package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;
import java.util.List;

public record CandidateSimulationResponseDto(
    Long candidateSchemeOptionId,
    String candidateFundName,
    String candidateAmfiCode,
    BigDecimal proposedAllocationPct,
    String fundingMethod,
    MultiFundLookThroughResponseDto baselinePortfolio,
    MultiFundLookThroughResponseDto simulatedPortfolio,
    BigDecimal deltaTop5WeightPct,
    BigDecimal deltaTop10WeightPct,
    BigDecimal deltaHhi,
    BigDecimal deltaSharedSecuritiesWeightPct,
    int deltaUniqueSecuritiesCount,
    int newSecuritiesIntroducedCount,
    int augmentedSecuritiesCount,
    List<AugmentedSecurityComparisonDto> topAugmentedSecurities,
    List<ConsolidatedSecurityExposureDto> topNewSecurities,
    List<CategoryDeltaDto> sectorDeltas,
    List<CategoryDeltaDto> marketCapDeltas,
    List<String> analyticalObservations,
    List<String> limitations,
    BigDecimal deltaVolatilityPct,
    BigDecimal deltaDiversificationBenefitPct,
    BigDecimal deltaBeta,
    BigDecimal deltaMaxDrawdownPct
) {
    public CandidateSimulationResponseDto(
        Long candidateSchemeOptionId,
        String candidateFundName,
        String candidateAmfiCode,
        BigDecimal proposedAllocationPct,
        String fundingMethod,
        MultiFundLookThroughResponseDto baselinePortfolio,
        MultiFundLookThroughResponseDto simulatedPortfolio,
        BigDecimal deltaTop5WeightPct,
        BigDecimal deltaTop10WeightPct,
        BigDecimal deltaHhi,
        BigDecimal deltaSharedSecuritiesWeightPct,
        int deltaUniqueSecuritiesCount,
        int newSecuritiesIntroducedCount,
        int augmentedSecuritiesCount,
        List<AugmentedSecurityComparisonDto> topAugmentedSecurities,
        List<ConsolidatedSecurityExposureDto> topNewSecurities,
        List<CategoryDeltaDto> sectorDeltas,
        List<CategoryDeltaDto> marketCapDeltas,
        List<String> analyticalObservations,
        List<String> limitations
    ) {
        this(candidateSchemeOptionId, candidateFundName, candidateAmfiCode, proposedAllocationPct,
             fundingMethod, baselinePortfolio, simulatedPortfolio, deltaTop5WeightPct, deltaTop10WeightPct,
             deltaHhi, deltaSharedSecuritiesWeightPct, deltaUniqueSecuritiesCount,
             newSecuritiesIntroducedCount, augmentedSecuritiesCount, topAugmentedSecurities,
             topNewSecurities, sectorDeltas, marketCapDeltas, analyticalObservations, limitations,
             null, null, null, null);
    }
}
