package com.yukira.backend.dto.portfolio.performance;

import java.math.BigDecimal;
import java.util.List;

/**
 * Portfolio-level financial reconciliation and outcome decomposition.
 *
 * Epistemic Formulation:
 * Opening Portfolio Value (V_start)
 * + External Investor Contributions (C_in)
 * - External Investor Withdrawals (C_out)
 * + Realized Capital Gains/Losses (G_realized)
 * + Unrealized Capital Gains/Losses (G_unrealized)
 * + Investment Dividend Income (I_dividend)
 * - Recorded Costs & Charges (E_charges)
 * +/- Supported Adjustments (A_adj)
 * = Expected Closing Value (V_expected)
 *
 * Unexplained Residual (R) = V_closing - V_expected.
 * Never force-balanced or smoothed. Discloses exact cause if R != 0.
 */
public record PortfolioFinancialDecompositionDto(
    BigDecimal openingPortfolioValue,
    BigDecimal externalInvestedCapital,
    BigDecimal externalRedeemedCapital,
    BigDecimal realizedCapitalGains,
    BigDecimal unrealizedCapitalGains,
    BigDecimal disclosedDividendIncome,
    BigDecimal dividendReinvestmentAmount,
    BigDecimal recordedChargesAndCosts,
    BigDecimal supportedAdjustments,
    BigDecimal closingPortfolioValue,
    BigDecimal calculatedExpectedClosingValue,
    BigDecimal unexplainedResidual,
    String reconciliationStatus, // RECONCILED, EXPLAINED_DISCREPANCY, UNRECONCILED_RESIDUAL
    List<String> residualCauses,
    String reconciliationFormula,
    String provenanceNotes
) {}

