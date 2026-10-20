package com.yukira.backend.dto.portfolio;

import java.math.BigDecimal;

public record ReconstitutionMemberDto(
    String isin,
    String symbol,
    String companyName,
    String industry,
    Long securityId,
    String canonicalName,
    BigDecimal weightFrom,
    BigDecimal weightTo,
    String changeType, // "ENTERED", "EXITED", "RETAINED"
    String notes // e.g. "Stock split ISIN migration", "New listing", "Merger/Delisted"
) {}
