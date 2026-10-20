package com.yukira.backend.calculation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrinsonFachlerCalculatorTest {

    @Test
    @DisplayName("Hand-calculated 3-segment Brinson-Fachler attribution test")
    void testHandCalculated3SegmentAttribution() {
        var large = new BrinsonFachlerCalculator.SegmentInput(
            "Large Cap",
            new BigDecimal("0.60"),
            new BigDecimal("0.70"),
            new BigDecimal("0.12"),
            new BigDecimal("0.10")
        );
        var mid = new BrinsonFachlerCalculator.SegmentInput(
            "Mid Cap",
            new BigDecimal("0.30"),
            new BigDecimal("0.20"),
            new BigDecimal("0.15"),
            new BigDecimal("0.12")
        );
        var small = new BrinsonFachlerCalculator.SegmentInput(
            "Small Cap",
            new BigDecimal("0.10"),
            new BigDecimal("0.10"),
            new BigDecimal("0.08"),
            new BigDecimal("0.06")
        );

        var summary = BrinsonFachlerCalculator.calculate(List.of(large, mid, small), null, null);

        assertThat(summary.portfolioReturn()).isEqualByComparingTo("0.125000");
        assertThat(summary.benchmarkReturn()).isEqualByComparingTo("0.100000");
        assertThat(summary.activeReturn()).isEqualByComparingTo("0.025000");

        // Allocation: Mid is +0.0020
        assertThat(summary.totalAllocationEffect()).isEqualByComparingTo("0.00200000");
        // Selection: Large +0.0140, Mid +0.0060, Small +0.0020 -> Total = +0.0220
        assertThat(summary.totalSelectionEffect()).isEqualByComparingTo("0.02200000");
        // Interaction: Large -0.0020, Mid +0.0030, Small 0 -> Total = +0.0010
        assertThat(summary.totalInteractionEffect()).isEqualByComparingTo("0.00100000");

        // Total = 0.0020 + 0.0220 + 0.0010 = 0.0250
        assertThat(summary.isReconciled()).isTrue();
        assertThat(summary.reconciliationDifference()).isEqualByComparingTo("0.00000000");
        assertThat(summary.reconciledTotal()).isEqualByComparingTo("0.02500000");
    }

    @Test
    @DisplayName("Cash drag segment attribution")
    void testCashSegmentAttribution() {
        var equities = new BrinsonFachlerCalculator.SegmentInput(
            "Equities",
            new BigDecimal("0.95"),
            new BigDecimal("1.00"),
            new BigDecimal("0.10"),
            new BigDecimal("0.10")
        );
        var cash = new BrinsonFachlerCalculator.SegmentInput(
            "Cash",
            new BigDecimal("0.05"),
            new BigDecimal("0.00"),
            new BigDecimal("0.02"),
            new BigDecimal("0.02")
        );

        var summary = BrinsonFachlerCalculator.calculate(List.of(equities, cash), null, null);

        // Portfolio: 0.95*0.10 + 0.05*0.02 = 0.096 (9.6%)
        // Benchmark: 1.00*0.10 = 0.100 (10.0%)
        // Active Return = -0.004 (-0.40%)
        assertThat(summary.activeReturn()).isEqualByComparingTo("-0.004000");
        assertThat(summary.isReconciled()).isTrue();
        assertThat(summary.reconciledTotal()).isEqualByComparingTo("-0.00400000");
    }

    @Test
    @DisplayName("Identical portfolio and benchmark yields zero active return")
    void testIdenticalPortfolioAndBenchmark() {
        var seg = new BrinsonFachlerCalculator.SegmentInput(
            "Total",
            BigDecimal.ONE,
            BigDecimal.ONE,
            new BigDecimal("0.08"),
            new BigDecimal("0.08")
        );

        var summary = BrinsonFachlerCalculator.calculate(List.of(seg), null, null);

        assertThat(summary.activeReturn()).isEqualByComparingTo("0.000000");
        assertThat(summary.totalAllocationEffect()).isEqualByComparingTo("0.00000000");
        assertThat(summary.totalSelectionEffect()).isEqualByComparingTo("0.00000000");
        assertThat(summary.totalInteractionEffect()).isEqualByComparingTo("0.00000000");
        assertThat(summary.isReconciled()).isTrue();
    }

    @Test
    @DisplayName("Empty inputs list throws IllegalArgumentException")
    void testEmptyInputsThrowsException() {
        assertThatThrownBy(() -> BrinsonFachlerCalculator.calculate(List.of(), null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
