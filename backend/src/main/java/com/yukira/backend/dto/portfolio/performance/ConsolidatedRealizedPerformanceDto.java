package com.yukira.backend.dto.portfolio.performance;

import java.math.BigDecimal;
import java.util.List;

/**
 * Consolidated realized gains/losses across all historical disposals.
 *
 * Epistemic Rules:
 * 1. Distinguishes external withdrawals (REDEMPTION) from internal portfolio transfers (SWITCH_OUT, STP_REDEMPTION).
 * 2. Does NOT describe FIFO calculations as tax liability or tax advice.
 */
public record ConsolidatedRealizedPerformanceDto(
    BigDecimal totalRealizedGains,
    BigDecimal totalRealizedLosses,
    BigDecimal netRealizedGainOrLoss,
    BigDecimal totalRedemptionGrossProceeds,
    BigDecimal totalCostBasisRedeemed,
    BigDecimal totalChargesDeducted,
    BigDecimal externalWithdrawalsRealizedGains,
    BigDecimal internalTransfersRealizedGains,
    Integer disposedLotsCount,
    List<RealizedGainItemDto> records,
    String taxDisclaimer
) {}

