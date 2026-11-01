package com.yukira.backend.dto.portfolio.performance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RealizedGainItemDto(
    Long redemptionTransactionId,
    Long schemeOptionId,
    String schemeName,
    LocalDate redemptionDate,
    BigDecimal unitsRedeemed,
    BigDecimal redemptionPrice,
    BigDecimal redemptionGrossProceeds,
    BigDecimal costBasisRedeemed,
    BigDecimal netRealizedGain,
    Long matchedPurchaseTransactionId,
    LocalDate matchedPurchaseDate,
    BigDecimal matchedPurchasePrice,
    Long holdingPeriodDays,
    String holdingPeriodCategory,
    String redemptionType,
    Boolean isInternalTransfer,
    BigDecimal chargesDeducted
) {}

