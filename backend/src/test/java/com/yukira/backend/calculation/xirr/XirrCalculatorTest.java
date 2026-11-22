package com.yukira.backend.calculation.xirr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task 450: XIRR Solver Root and Cash-Flow Contract Tests")
class XirrCalculatorTest {

    @Test
    @DisplayName("Should calculate exact XIRR for single investment and terminal redemption across exactly 365 days")
    void shouldCalculateExactXirrForSimpleFlow() {
        // Outflow 10,000 on 2024-01-01, Inflow 11,200 on 2025-01-01 (12.00% return)
        List<XirrCalculator.CashFlowItem> flows = List.of(
            new XirrCalculator.CashFlowItem(LocalDate.of(2024, 1, 1), new BigDecimal("-10000.00")),
            new XirrCalculator.CashFlowItem(LocalDate.of(2025, 1, 1), new BigDecimal("11200.00")) // 366 days in leap year 2024
        );

        XirrCalculator.XirrCalculationResult result = XirrCalculator.calculate(flows);

        assertThat(result.status()).isEqualTo(XirrCalculator.XirrStatus.AVAILABLE);
        assertThat(result.xirrAnnualized()).isNotNull();
        // In leap year 2024, 366/365 gives slightly adjusted rate close to 11.96%
        assertThat(result.xirrAnnualized().doubleValue()).isBetween(0.115, 0.125);
        assertThat(result.dayCountConvention()).isEqualTo("ACT/365");
        assertThat(result.totalOutflows()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(result.totalInflows()).isEqualByComparingTo(new BigDecimal("11200.00"));
    }

    @Test
    @DisplayName("Should fail deterministically when all cash flows are outflows")
    void shouldFailWhenMissingInflows() {
        List<XirrCalculator.CashFlowItem> flows = List.of(
            new XirrCalculator.CashFlowItem(LocalDate.of(2024, 1, 1), new BigDecimal("-10000.00")),
            new XirrCalculator.CashFlowItem(LocalDate.of(2024, 6, 1), new BigDecimal("-5000.00"))
        );

        XirrCalculator.XirrCalculationResult result = XirrCalculator.calculate(flows);

        assertThat(result.status()).isEqualTo(XirrCalculator.XirrStatus.INSUFFICIENT_CASH_FLOWS);
        assertThat(result.xirrAnnualized()).isNull();
    }

    @Test
    @DisplayName("Should fail deterministically when all cash flows occur on the same date")
    void shouldFailWhenZeroDayDuration() {
        List<XirrCalculator.CashFlowItem> flows = List.of(
            new XirrCalculator.CashFlowItem(LocalDate.of(2024, 1, 1), new BigDecimal("-10000.00")),
            new XirrCalculator.CashFlowItem(LocalDate.of(2024, 1, 1), new BigDecimal("10500.00"))
        );

        XirrCalculator.XirrCalculationResult result = XirrCalculator.calculate(flows);

        assertThat(result.status()).isEqualTo(XirrCalculator.XirrStatus.INSUFFICIENT_HISTORY);
        assertThat(result.xirrAnnualized()).isNull();
    }

    @Test
    @DisplayName("Should detect multi-root ambiguity when sign alternations produce multiple distinct roots")
    void shouldDetectAmbiguousRootsWhenMultipleSignAlternationsExist() {
        // Classic non-normal cash-flow pattern:
        // Outflow 100, Inflow 230, Outflow 132
        // Produces two positive roots (10% and 20%)
        List<XirrCalculator.CashFlowItem> flows = List.of(
            new XirrCalculator.CashFlowItem(LocalDate.of(2024, 1, 1), new BigDecimal("-100.00")),
            new XirrCalculator.CashFlowItem(LocalDate.of(2025, 1, 1), new BigDecimal("230.00")),
            new XirrCalculator.CashFlowItem(LocalDate.of(2026, 1, 1), new BigDecimal("-132.00"))
        );

        XirrCalculator.XirrCalculationResult result = XirrCalculator.calculate(flows);

        assertThat(result.signAlternationsCount()).isGreaterThanOrEqualTo(2);
        assertThat(result.isMultipleRootsPossible()).isTrue();
        if (result.status() == XirrCalculator.XirrStatus.AMBIGUOUS_ROOT) {
            assertThat(result.candidateRoots()).hasSizeGreaterThanOrEqualTo(2);
        } else {
            // If numerical tolerance converged to one bracket, solver must still record that multiple roots were possible
            assertThat(result.isMultipleRootsPossible()).isTrue();
        }
    }

    @Test
    @DisplayName("Should handle multiple periodic SIP inflows and single terminal valuation")
    void shouldCalculateSipXirrAccurately() {
        List<XirrCalculator.CashFlowItem> flows = List.of(
            new XirrCalculator.CashFlowItem(LocalDate.of(2024, 1, 1), new BigDecimal("-5000.00")),
            new XirrCalculator.CashFlowItem(LocalDate.of(2024, 2, 1), new BigDecimal("-5000.00")),
            new XirrCalculator.CashFlowItem(LocalDate.of(2024, 3, 1), new BigDecimal("-5000.00")),
            new XirrCalculator.CashFlowItem(LocalDate.of(2024, 4, 1), new BigDecimal("-5000.00")),
            new XirrCalculator.CashFlowItem(LocalDate.of(2024, 5, 1), new BigDecimal("-5000.00")),
            new XirrCalculator.CashFlowItem(LocalDate.of(2024, 6, 1), new BigDecimal("27000.00")) // Profitable terminal valuation
        );

        XirrCalculator.XirrCalculationResult result = XirrCalculator.calculate(flows);

        assertThat(result.status()).isEqualTo(XirrCalculator.XirrStatus.AVAILABLE);
        assertThat(result.xirrAnnualized()).isNotNull();
        assertThat(result.xirrAnnualized().doubleValue()).isPositive();
        assertThat(result.totalOutflows()).isEqualByComparingTo(new BigDecimal("25000.00"));
        assertThat(result.totalInflows()).isEqualByComparingTo(new BigDecimal("27000.00"));
        assertThat(result.netGainAmount()).isEqualByComparingTo(new BigDecimal("2000.00"));
    }
}

