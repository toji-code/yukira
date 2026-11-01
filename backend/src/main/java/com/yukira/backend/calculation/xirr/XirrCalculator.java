package com.yukira.backend.calculation.xirr;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pure, deterministic money-weighted return (XIRR) calculator.
 *
 * Epistemic & Numerical Invariants:
 * 1. Day count convention: ACT/365.
 * 2. Sign convention: Investor cash outflow is negative, cash inflow/terminal value is positive.
 * 3. Solver: Newton-Raphson with analytical derivative and Bisection bracket fallback.
 * 4. Deterministic termination: max 100 iterations, tolerance 1e-7.
 * 5. Strict state categorization: Never fabricates return or defaults to 0% on failure.
 * 6. Root ambiguity handling: Multi-probe search detects multiple economically valid roots
 *    caused by repeated sign alternations, explicitly reporting AMBIGUOUS_ROOT rather than
 *    silently selecting an arbitrary local root.
 */
public final class XirrCalculator {

    private static final int MAX_ITERATIONS = 100;
    private static final double TOLERANCE = 1e-7;
    private static final double INITIAL_GUESS = 0.10; // 10% annualized
    private static final MathContext MC = MathContext.DECIMAL128;

    public enum XirrStatus {
        AVAILABLE,
        INSUFFICIENT_CASH_FLOWS,
        INSUFFICIENT_HISTORY,
        MISSING_ENDING_VALUATION,
        INCOMPLETE_PORTFOLIO_VALUATION,
        NO_VALID_ROOT,
        AMBIGUOUS_ROOT,
        NON_CONVERGENT
    }

    public record CashFlowItem(
        LocalDate date,
        BigDecimal amount,
        String description
    ) {
        public CashFlowItem(LocalDate date, BigDecimal amount) {
            this(date, amount, null);
        }
    }

    public record XirrCalculationResult(
        BigDecimal xirrAnnualized,
        XirrStatus status,
        String statusMessage,
        Integer iterationsCount,
        BigDecimal residualNpv,
        LocalDate firstCashFlowDate,
        LocalDate lastCashFlowDate,
        Integer totalCashFlowCount,
        BigDecimal totalOutflows, // Total invested (positive representation of negative flows)
        BigDecimal totalInflows,  // Total returned + terminal valuation
        BigDecimal netGainAmount,
        String dayCountConvention,
        String solverMethod,
        Integer signAlternationsCount,
        Boolean isMultipleRootsPossible,
        List<BigDecimal> candidateRoots
    ) {
        // Backwards-compatible 13-argument constructor
        public XirrCalculationResult(
            BigDecimal xirrAnnualized,
            XirrStatus status,
            String statusMessage,
            Integer iterationsCount,
            BigDecimal residualNpv,
            LocalDate firstCashFlowDate,
            LocalDate lastCashFlowDate,
            Integer totalCashFlowCount,
            BigDecimal totalOutflows,
            BigDecimal totalInflows,
            BigDecimal netGainAmount,
            String dayCountConvention,
            String solverMethod
        ) {
            this(
                xirrAnnualized,
                status,
                statusMessage,
                iterationsCount,
                residualNpv,
                firstCashFlowDate,
                lastCashFlowDate,
                totalCashFlowCount,
                totalOutflows,
                totalInflows,
                netGainAmount,
                dayCountConvention,
                solverMethod,
                0,
                false,
                xirrAnnualized != null ? List.of(xirrAnnualized) : List.of()
            );
        }

        public static XirrCalculationResult failure(
            XirrStatus status,
            String message,
            List<CashFlowItem> flows
        ) {
            LocalDate d1 = (flows != null && !flows.isEmpty()) ? flows.get(0).date() : null;
            LocalDate dN = (flows != null && !flows.isEmpty()) ? flows.get(flows.size() - 1).date() : null;
            int count = flows != null ? flows.size() : 0;
            return new XirrCalculationResult(
                null,
                status,
                message,
                0,
                null,
                d1,
                dN,
                count,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "ACT/365",
                "NEWTON_RAPHSON_WITH_BISECTION_FALLBACK",
                0,
                false,
                List.of()
            );
        }
    }

    /**
     * Deterministically calculates XIRR from a collection of dated cash flows.
     */
    public static XirrCalculationResult calculate(List<CashFlowItem> inputFlows) {
        if (inputFlows == null || inputFlows.isEmpty()) {
            return XirrCalculationResult.failure(
                XirrStatus.INSUFFICIENT_HISTORY,
                "No cash flows provided for XIRR calculation.",
                null
            );
        }

        // Sort by date ascending
        List<CashFlowItem> sortedFlows = new ArrayList<>(inputFlows);
        sortedFlows.sort(Comparator.comparing(CashFlowItem::date));

        if (sortedFlows.size() < 2) {
            return XirrCalculationResult.failure(
                XirrStatus.INSUFFICIENT_HISTORY,
                "XIRR requires at least two distinct dated cash flows.",
                sortedFlows
            );
        }

        LocalDate firstDate = sortedFlows.get(0).date();
        LocalDate lastDate = sortedFlows.get(sortedFlows.size() - 1).date();

        if (firstDate.equals(lastDate)) {
            return XirrCalculationResult.failure(
                XirrStatus.INSUFFICIENT_HISTORY,
                "XIRR requires cash flows spanning more than zero days.",
                sortedFlows
            );
        }

        // Validate sign changes and count alternations
        boolean hasPositive = false;
        boolean hasNegative = false;
        BigDecimal totalOutflows = BigDecimal.ZERO;
        BigDecimal totalInflows = BigDecimal.ZERO;

        double[] amounts = new double[sortedFlows.size()];
        double[] timeYears = new double[sortedFlows.size()];

        int signAlternations = 0;
        double lastNonZeroSign = 0.0;

        for (int i = 0; i < sortedFlows.size(); i++) {
            CashFlowItem flow = sortedFlows.get(i);
            BigDecimal amt = flow.amount();
            if (amt == null) {
                return XirrCalculationResult.failure(
                    XirrStatus.INSUFFICIENT_CASH_FLOWS,
                    "Cash flow record has null amount on date " + flow.date(),
                    sortedFlows
                );
            }

            if (amt.compareTo(BigDecimal.ZERO) > 0) {
                hasPositive = true;
                totalInflows = totalInflows.add(amt);
            } else if (amt.compareTo(BigDecimal.ZERO) < 0) {
                hasNegative = true;
                totalOutflows = totalOutflows.add(amt.abs());
            }

            double amtDbl = amt.doubleValue();
            amounts[i] = amtDbl;
            long days = ChronoUnit.DAYS.between(firstDate, flow.date());
            timeYears[i] = days / 365.0;

            if (amtDbl != 0.0) {
                double currentSign = Math.signum(amtDbl);
                if (lastNonZeroSign != 0.0 && currentSign != lastNonZeroSign) {
                    signAlternations++;
                }
                lastNonZeroSign = currentSign;
            }
        }

        if (!hasPositive || !hasNegative) {
            return XirrCalculationResult.failure(
                XirrStatus.INSUFFICIENT_CASH_FLOWS,
                "Cash flows must contain both outflows (investments) and inflows (redemptions/valuations).",
                sortedFlows
            );
        }

        boolean multipleRootsPossible = signAlternations > 1;

        // Multi-probe root discovery to test uniqueness when multiple sign alternations exist
        List<Double> discoveredRoots = new ArrayList<>();
        double[] probeGuesses = multipleRootsPossible
            ? new double[]{-0.5, 0.10, 0.75, 2.5}
            : new double[]{INITIAL_GUESS};

        for (double guess : probeGuesses) {
            Double rootFound = solveNewtonRaphson(amounts, timeYears, guess);
            if (rootFound != null && rootFound > -0.999 && rootFound < 10.0) {
                boolean isDuplicate = false;
                for (Double r : discoveredRoots) {
                    if (Math.abs(r - rootFound) < 0.005) {
                        isDuplicate = true;
                        break;
                    }
                }
                if (!isDuplicate) {
                    discoveredRoots.add(rootFound);
                }
            }
        }

        // If multiple distinct roots were found, report AMBIGUOUS_ROOT
        if (discoveredRoots.size() > 1) {
            List<BigDecimal> candidateRoots = discoveredRoots.stream()
                .sorted()
                .map(r -> BigDecimal.valueOf(r).setScale(6, RoundingMode.HALF_UP))
                .toList();

            String rootSummary = discoveredRoots.stream()
                .sorted()
                .map(r -> String.format(Locale.ROOT, "%.2f%%", r * 100))
                .reduce((a, b) -> a + " and " + b)
                .orElse("");

            return new XirrCalculationResult(
                null,
                XirrStatus.AMBIGUOUS_ROOT,
                "Cash-flow series contains multiple sign reversals (" + signAlternations +
                ") and exhibits multiple economically valid roots (" + rootSummary +
                "). A unique XIRR cannot be determined.",
                100,
                BigDecimal.ZERO,
                firstDate,
                lastDate,
                sortedFlows.size(),
                totalOutflows.setScale(4, RoundingMode.HALF_UP),
                totalInflows.setScale(4, RoundingMode.HALF_UP),
                totalInflows.subtract(totalOutflows).setScale(4, RoundingMode.HALF_UP),
                "ACT/365",
                "MULTI_START_NEWTON_RAPHSON_AMBIGUITY_DETECTOR",
                signAlternations,
                true,
                candidateRoots
            );
        }

        // Standard solve: Newton-Raphson with Bisection fallback
        double rate = !discoveredRoots.isEmpty() ? discoveredRoots.get(0) : INITIAL_GUESS;
        int iter = 0;
        boolean converged = !discoveredRoots.isEmpty();

        if (!converged) {
            for (; iter < MAX_ITERATIONS; iter++) {
                double npv = 0.0;
                double npvPrime = 0.0;

                for (int i = 0; i < amounts.length; i++) {
                    double t = timeYears[i];
                    double factor = Math.pow(1.0 + rate, t);
                    if (Double.isNaN(factor) || Double.isInfinite(factor) || factor <= 0.0) {
                        break;
                    }
                    npv += amounts[i] / factor;
                    npvPrime += -t * amounts[i] / (factor * (1.0 + rate));
                }

                if (Double.isNaN(npv) || Double.isInfinite(npv)) {
                    break;
                }

                if (Math.abs(npv) < TOLERANCE) {
                    converged = true;
                    break;
                }

                if (Math.abs(npvPrime) < 1e-14) {
                    break; // Near zero slope, switch to bisection
                }

                double nextRate = rate - (npv / npvPrime);

                // If step jumps outside valid economic boundaries [-0.99, 10.0], break to bisection
                if (nextRate <= -0.999 || nextRate > 10.0 || Double.isNaN(nextRate)) {
                    break;
                }

                if (Math.abs(nextRate - rate) < TOLERANCE) {
                    rate = nextRate;
                    converged = true;
                    break;
                }

                rate = nextRate;
            }

            // If Newton-Raphson did not converge cleanly, attempt Bisection / Bracket search
            if (!converged) {
                double lowRate = -0.99;
                double highRate = 5.0;
                double npvLow = evaluateNpv(amounts, timeYears, lowRate);
                double npvHigh = evaluateNpv(amounts, timeYears, highRate);

                if (npvLow * npvHigh <= 0.0) {
                    for (int bIter = 0; bIter < MAX_ITERATIONS; bIter++) {
                        double midRate = 0.5 * (lowRate + highRate);
                        double npvMid = evaluateNpv(amounts, timeYears, midRate);

                        if (Math.abs(npvMid) < TOLERANCE || (highRate - lowRate) < TOLERANCE) {
                            rate = midRate;
                            converged = true;
                            iter += bIter;
                            break;
                        }

                        if (npvLow * npvMid <= 0.0) {
                            highRate = midRate;
                            npvHigh = npvMid;
                        } else {
                            lowRate = midRate;
                            npvLow = npvMid;
                        }
                    }
                } else {
                    // Try extended positive bracket for high-growth short-term SIPs
                    highRate = 20.0;
                    npvHigh = evaluateNpv(amounts, timeYears, highRate);
                    if (npvLow * npvHigh <= 0.0) {
                        for (int bIter = 0; bIter < MAX_ITERATIONS; bIter++) {
                            double midRate = 0.5 * (lowRate + highRate);
                            double npvMid = evaluateNpv(amounts, timeYears, midRate);

                            if (Math.abs(npvMid) < TOLERANCE || (highRate - lowRate) < TOLERANCE) {
                                rate = midRate;
                                converged = true;
                                iter += bIter;
                                break;
                            }

                            if (npvLow * npvMid <= 0.0) {
                                highRate = midRate;
                                npvHigh = npvMid;
                            } else {
                                lowRate = midRate;
                                npvLow = npvMid;
                            }
                        }
                    }
                }
            }
        }

        if (!converged || Double.isNaN(rate) || Double.isInfinite(rate) || rate <= -1.0) {
            return XirrCalculationResult.failure(
                XirrStatus.NON_CONVERGENT,
                "Root-finding solver could not converge to an economically valid XIRR root within 100 iterations.",
                sortedFlows
            );
        }

        BigDecimal xirr = BigDecimal.valueOf(rate).setScale(6, RoundingMode.HALF_UP);
        BigDecimal residual = BigDecimal.valueOf(evaluateNpv(amounts, timeYears, rate)).setScale(6, RoundingMode.HALF_UP);
        BigDecimal netGain = totalInflows.subtract(totalOutflows);

        return new XirrCalculationResult(
            xirr,
            XirrStatus.AVAILABLE,
            "Deterministic XIRR converged successfully in " + iter + " iterations.",
            iter,
            residual,
            firstDate,
            lastDate,
            sortedFlows.size(),
            totalOutflows.setScale(4, RoundingMode.HALF_UP),
            totalInflows.setScale(4, RoundingMode.HALF_UP),
            netGain.setScale(4, RoundingMode.HALF_UP),
            "ACT/365",
            "NEWTON_RAPHSON_WITH_BISECTION_FALLBACK",
            signAlternations,
            multipleRootsPossible,
            List.of(xirr)
        );
    }

    private static Double solveNewtonRaphson(double[] amounts, double[] timeYears, double initialGuess) {
        double r = initialGuess;
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            double npv = 0.0;
            double npvPrime = 0.0;

            for (int i = 0; i < amounts.length; i++) {
                double t = timeYears[i];
                double factor = Math.pow(1.0 + r, t);
                if (Double.isNaN(factor) || Double.isInfinite(factor) || factor <= 0.0) {
                    return null;
                }
                npv += amounts[i] / factor;
                npvPrime += -t * amounts[i] / (factor * (1.0 + r));
            }

            if (Double.isNaN(npv) || Double.isInfinite(npv)) {
                return null;
            }

            if (Math.abs(npv) < TOLERANCE) {
                return r;
            }

            if (Math.abs(npvPrime) < 1e-14) {
                return null;
            }

            double nextR = r - (npv / npvPrime);
            if (nextR <= -0.999 || nextR > 10.0 || Double.isNaN(nextR)) {
                return null;
            }

            if (Math.abs(nextR - r) < TOLERANCE) {
                return nextR;
            }

            r = nextR;
        }
        return null;
    }

    private static double evaluateNpv(double[] amounts, double[] timeYears, double rate) {
        double npv = 0.0;
        for (int i = 0; i < amounts.length; i++) {
            double factor = Math.pow(1.0 + rate, timeYears[i]);
            if (Double.isNaN(factor) || factor <= 0.0) {
                return Double.NaN;
            }
            npv += amounts[i] / factor;
        }
        return npv;
    }
}
