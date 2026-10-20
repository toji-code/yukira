package com.yukira.backend.calculation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure, deterministic Brinson-Fachler (1985) portfolio active-return attribution calculator.
 *
 * Epistemic Rules:
 * 1. Strict mathematical reconciliation:
 *    R_A = R_p - R_b = Sum(Allocation) + Sum(Selection) + Sum(Interaction) + CoverageResidual
 * 2. Deterministic floating-point discipline using BigDecimal (scale 8, HALF_UP).
 * 3. Never silently normalize weights or fabricate benchmark returns.
 */
public final class BrinsonFachlerCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.000001");

    public record SegmentInput(
        String segmentName,
        BigDecimal portfolioWeight,
        BigDecimal benchmarkWeight,
        BigDecimal portfolioReturn,
        BigDecimal benchmarkReturn
    ) {}

    public record SegmentAttribution(
        String segmentName,
        BigDecimal portfolioWeight,
        BigDecimal benchmarkWeight,
        BigDecimal portfolioReturn,
        BigDecimal benchmarkReturn,
        BigDecimal allocationEffect,
        BigDecimal selectionEffect,
        BigDecimal interactionEffect,
        BigDecimal totalSegmentEffect
    ) {}

    public record BrinsonSummary(
        BigDecimal portfolioReturn,
        BigDecimal benchmarkReturn,
        BigDecimal activeReturn,
        BigDecimal totalAllocationEffect,
        BigDecimal totalSelectionEffect,
        BigDecimal totalInteractionEffect,
        BigDecimal coverageResidual,
        BigDecimal reconciledTotal,
        BigDecimal reconciliationDifference,
        boolean isReconciled,
        List<SegmentAttribution> segments
    ) {}

    private BrinsonFachlerCalculator() {}

    /**
     * Executes the Brinson-Fachler attribution decomposition across the provided segments.
     */
    public static BrinsonSummary calculate(
        List<SegmentInput> inputs,
        BigDecimal totalPortfolioReturnOverride,
        BigDecimal totalBenchmarkReturnOverride
    ) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("At least one segment input is required for Brinson attribution.");
        }

        BigDecimal sumWp = BigDecimal.ZERO;
        BigDecimal sumWb = BigDecimal.ZERO;
        BigDecimal calculatedRp = BigDecimal.ZERO;
        BigDecimal calculatedRb = BigDecimal.ZERO;

        for (SegmentInput in : inputs) {
            if (in.portfolioWeight() == null || in.benchmarkWeight() == null
                || in.portfolioReturn() == null || in.benchmarkReturn() == null) {
                throw new IllegalArgumentException("Segment " + in.segmentName() + " has null weights or returns.");
            }
            sumWp = sumWp.add(in.portfolioWeight(), MC);
            sumWb = sumWb.add(in.benchmarkWeight(), MC);
            calculatedRp = calculatedRp.add(in.portfolioWeight().multiply(in.portfolioReturn(), MC), MC);
            calculatedRb = calculatedRb.add(in.benchmarkWeight().multiply(in.benchmarkReturn(), MC), MC);
        }

        BigDecimal rP = totalPortfolioReturnOverride != null ? totalPortfolioReturnOverride : calculatedRp;
        BigDecimal rB = totalBenchmarkReturnOverride != null ? totalBenchmarkReturnOverride : calculatedRb;
        BigDecimal rA = rP.subtract(rB, MC);

        BigDecimal weightDiff = sumWp.subtract(sumWb, MC);
        BigDecimal coverageResidual = weightDiff.abs().compareTo(TOLERANCE) > 0
            ? rB.multiply(weightDiff, MC).negate()
            : BigDecimal.ZERO;

        BigDecimal totAlloc = BigDecimal.ZERO;
        BigDecimal totSelect = BigDecimal.ZERO;
        BigDecimal totInter = BigDecimal.ZERO;

        List<SegmentAttribution> segments = new ArrayList<>();

        for (SegmentInput in : inputs) {
            BigDecimal wp = in.portfolioWeight();
            BigDecimal wb = in.benchmarkWeight();
            BigDecimal rp = in.portfolioReturn();
            BigDecimal rb = in.benchmarkReturn();

            BigDecimal deltaW = wp.subtract(wb, MC);
            BigDecimal deltaR = rp.subtract(rb, MC);
            BigDecimal bmExcess = rb.subtract(rB, MC);

            // Brinson-Fachler Allocation: (w_p - w_b) * (R_b,s - R_b)
            BigDecimal alloc = deltaW.multiply(bmExcess, MC);

            // Selection: w_b * (R_p,s - R_b,s)
            BigDecimal select = wb.multiply(deltaR, MC);

            // Interaction: (w_p - w_b) * (R_p,s - R_b,s)
            BigDecimal inter = deltaW.multiply(deltaR, MC);

            BigDecimal totSeg = alloc.add(select, MC).add(inter, MC);

            totAlloc = totAlloc.add(alloc, MC);
            totSelect = totSelect.add(select, MC);
            totInter = totInter.add(inter, MC);

            segments.add(new SegmentAttribution(
                in.segmentName(),
                wp.setScale(6, RoundingMode.HALF_UP),
                wb.setScale(6, RoundingMode.HALF_UP),
                rp.setScale(6, RoundingMode.HALF_UP),
                rb.setScale(6, RoundingMode.HALF_UP),
                alloc.setScale(8, RoundingMode.HALF_UP),
                select.setScale(8, RoundingMode.HALF_UP),
                inter.setScale(8, RoundingMode.HALF_UP),
                totSeg.setScale(8, RoundingMode.HALF_UP)
            ));
        }

        BigDecimal reconciledTotal = totAlloc.add(totSelect, MC).add(totInter, MC).add(coverageResidual, MC);
        BigDecimal diff = reconciledTotal.subtract(rA, MC).abs();
        boolean isReconciled = diff.compareTo(TOLERANCE) <= 0;

        return new BrinsonSummary(
            rP.setScale(6, RoundingMode.HALF_UP),
            rB.setScale(6, RoundingMode.HALF_UP),
            rA.setScale(6, RoundingMode.HALF_UP),
            totAlloc.setScale(8, RoundingMode.HALF_UP),
            totSelect.setScale(8, RoundingMode.HALF_UP),
            totInter.setScale(8, RoundingMode.HALF_UP),
            coverageResidual.setScale(8, RoundingMode.HALF_UP),
            reconciledTotal.setScale(8, RoundingMode.HALF_UP),
            diff.setScale(8, RoundingMode.HALF_UP),
            isReconciled,
            Collections.unmodifiableList(segments)
        );
    }
}
