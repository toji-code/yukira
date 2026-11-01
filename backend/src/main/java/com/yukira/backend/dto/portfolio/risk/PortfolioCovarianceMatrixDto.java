package com.yukira.backend.dto.portfolio.risk;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioCovarianceMatrixDto(
    List<Long> fundIds,
    List<String> fundNames,
    List<String> amfiCodes,
    List<List<BigDecimal>> covarianceMatrix,
    List<List<BigDecimal>> correlationMatrix,
    List<List<Integer>> pairwiseObservationCounts,
    List<List<String>> pairwiseEvidenceStates,
    String matrixCompleteness,
    boolean isMathematicallySuitable,
    String matrixEvidenceState,
    boolean isSymmetric,
    boolean isFinite,
    boolean hasNonNegativeDiagonal,
    boolean isPositiveSemiDefinite,
    boolean isNearSingular,
    BigDecimal minimumEigenvalue,
    BigDecimal maximumEigenvalue,
    BigDecimal conditionNumber,
    BigDecimal determinant,
    String matrixSuitabilityState,
    String matrixSuitabilityExplanation
) {}
