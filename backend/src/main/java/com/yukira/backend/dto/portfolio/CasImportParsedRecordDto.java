package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

public record CasImportParsedRecordDto(
    String rawSchemeName,
    String folioNumber,
    String isin,
    String amfiCode,
    BigDecimal units,
    BigDecimal costBasisAmount,
    BigDecimal marketValue,
    BigDecimal nav
) {}
