package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

public record CasImportHoldingDetailDto(
    Long id,
    String rawSchemeName,
    String folioNumber,
    String isin,
    String amfiCode,
    BigDecimal units,
    BigDecimal costBasisAmount,
    BigDecimal marketValue,
    BigDecimal nav,
    Long resolvedSchemeOptionId,
    String resolvedFundName,
    String resolvedAmcName,
    String resolutionStatus, // RESOLVED, AMBIGUOUS, UNRESOLVED, INVALID
    String resolutionMethod, // ISIN_EXACT, AMFI_EXACT, NAME_EXACT, UNRESOLVED
    String resolutionNotes
) {}
