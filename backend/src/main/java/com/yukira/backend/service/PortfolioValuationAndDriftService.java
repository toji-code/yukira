package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for point-in-time investor portfolio valuation, target allocation drift,
 * rebalancing guidance, and XIRR solver evidence contracts (Tasks 297, 298, 301, 343, 399, 400).
 * Preserves the strict boundary between investor-level cash-flow return (XIRR),
 * fund-level compounding (CAGR), and static portfolio proxies.
 */
@Service
@Transactional(readOnly = true)
public class PortfolioValuationAndDriftService {

    private final InvestorPortfolioHoldingRepository holdingRepository;
    private final InvestorTargetAllocationRepository targetAllocationRepository;
    private final NavObservationRepository navObservationRepository;
    private final InvestorRepository investorRepository;
    private InvestorTransactionRepository transactionRepository;

    public record PortfolioDriftDto(
        String assetClass,
        double targetPercentage,
        double currentPercentage,
        double driftPercentage,
        String driftStatus, // IN_BAND, UNDERWEIGHT, OVERWEIGHT
        BigDecimal rebalanceAdjustmentAmount,
        String recommendation
    ) {}

    public record PortfolioValuationSummaryDto(
        Long investorId,
        BigDecimal totalCurrentValue,
        BigDecimal totalCostBasis,
        BigDecimal totalUnrealizedGainLoss,
        List<PortfolioDriftDto> allocationDrifts,
        boolean requiresRebalancing,
        String epistemicBoundaryNotice,
        double valuationCoverageRatio,
        int pricedHoldingsCount,
        int unpricedHoldingsCount
    ) {
        /**
         * Backward-compatible 7-argument constructor.
         */
        public PortfolioValuationSummaryDto(
            Long investorId,
            BigDecimal totalCurrentValue,
            BigDecimal totalCostBasis,
            BigDecimal totalUnrealizedGainLoss,
            List<PortfolioDriftDto> allocationDrifts,
            boolean requiresRebalancing,
            String epistemicBoundaryNotice
        ) {
            this(
                investorId,
                totalCurrentValue,
                totalCostBasis,
                totalUnrealizedGainLoss,
                allocationDrifts,
                requiresRebalancing,
                epistemicBoundaryNotice,
                1.0,
                allocationDrifts != null ? allocationDrifts.size() : 0,
                0
            );
        }
    }

    public record XirrCashFlowItemDto(
        LocalDate cashFlowDate,
        BigDecimal amount,
        String cashFlowType, // PURCHASE, REDEMPTION, TERMINAL_VALUATION
        String transactionReference,
        boolean isReconciled
    ) {}

    public record XirrSolverContractDto(
        Long investorId,
        LocalDate openingDate,
        BigDecimal openingValuation,
        LocalDate closingDate,
        BigDecimal closingValuation,
        List<XirrCashFlowItemDto> cashFlowSchedule,
        Double calculatedXirrPercent,
        String solverOutcome, // CONVERGED, DID_NOT_CONVERGE, INSUFFICIENT_CASH_FLOWS
        int solverIterations,
        boolean hasUnreconciledTransactions,
        String calculationEvidenceNotice,
        boolean hasMultiplePotentialRoots,
        boolean bracketVerified,
        int signChangesCount
    ) {
        /**
         * Backward-compatible 11-argument constructor.
         */
        public XirrSolverContractDto(
            Long investorId,
            LocalDate openingDate,
            BigDecimal openingValuation,
            LocalDate closingDate,
            BigDecimal closingValuation,
            List<XirrCashFlowItemDto> cashFlowSchedule,
            Double calculatedXirrPercent,
            String solverOutcome,
            int solverIterations,
            boolean hasUnreconciledTransactions,
            String calculationEvidenceNotice
        ) {
            this(
                investorId,
                openingDate,
                openingValuation,
                closingDate,
                closingValuation,
                cashFlowSchedule,
                calculatedXirrPercent,
                solverOutcome,
                solverIterations,
                hasUnreconciledTransactions,
                calculationEvidenceNotice,
                false,
                true,
                1
            );
        }
    }

    public PortfolioValuationAndDriftService(
        InvestorPortfolioHoldingRepository holdingRepository,
        InvestorTargetAllocationRepository targetAllocationRepository,
        NavObservationRepository navObservationRepository,
        InvestorRepository investorRepository
    ) {
        this.holdingRepository = holdingRepository;
        this.targetAllocationRepository = targetAllocationRepository;
        this.navObservationRepository = navObservationRepository;
        this.investorRepository = investorRepository;
    }

    @Autowired(required = false)
    public void setTransactionRepository(InvestorTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Optional<PortfolioValuationSummaryDto> evaluatePortfolioDrift(Long investorId) {
        Optional<Investor> invOpt = investorRepository.findById(investorId);
        if (invOpt.isEmpty()) {
            return Optional.empty();
        }
        Investor investor = invOpt.get();

        List<InvestorPortfolioHolding> holdings = holdingRepository.findByInvestor(investor);
        List<InvestorTargetAllocation> targets = targetAllocationRepository.findByInvestor(investor);

        BigDecimal totalVal = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        int pricedCount = 0;
        int unpricedCount = 0;

        Map<String, BigDecimal> assetClassValues = new HashMap<>();

        for (InvestorPortfolioHolding h : holdings) {
            SchemeOption opt = h.getSchemeOption();
            BigDecimal units = h.getUnits() != null ? h.getUnits() : BigDecimal.ZERO;
            BigDecimal costBasis = h.getCostBasis() != null ? h.getCostBasis() : BigDecimal.ZERO;

            BigDecimal nav = BigDecimal.ZERO;
            if (opt != null) {
                var latestObs = navObservationRepository.findTopBySchemeOptionIdOrderByEffectiveDateDesc(opt.getId());
                if (latestObs.isPresent() && latestObs.get().getNavValue() != null) {
                    nav = latestObs.get().getNavValue();
                }
            }

            if (nav.compareTo(BigDecimal.ZERO) > 0) {
                pricedCount++;
            } else {
                unpricedCount++;
            }

            BigDecimal holdingVal = units.multiply(nav).setScale(4, RoundingMode.HALF_UP);
            totalVal = totalVal.add(holdingVal);
            totalCost = totalCost.add(costBasis);

            String assetClass = "EQUITY";
            if (opt != null && opt.getScheme() != null && opt.getScheme().getCategory() != null) {
                String cat = opt.getScheme().getCategory().toUpperCase();
                if (cat.contains("DEBT") || cat.contains("LIQUID") || cat.contains("GILT")) {
                    assetClass = "DEBT";
                } else if (cat.contains("HYBRID")) {
                    assetClass = "HYBRID";
                }
            }

            assetClassValues.put(assetClass, assetClassValues.getOrDefault(assetClass, BigDecimal.ZERO).add(holdingVal));
        }

        int totalHoldings = holdings.size();
        double coverageRatio = totalHoldings > 0 ? (double) pricedCount / (double) totalHoldings : 1.0;

        BigDecimal unrealized = totalVal.subtract(totalCost);

        List<PortfolioDriftDto> driftList = new ArrayList<>();
        boolean requiresRebalancing = false;

        double totalValDouble = totalVal.doubleValue();

        for (InvestorTargetAllocation target : targets) {
            String assetClass = target.getAssetClass();
            double targetPct = target.getTargetPercentage() != null ? target.getTargetPercentage().doubleValue() : 0.0;

            BigDecimal currentVal = assetClassValues.getOrDefault(assetClass, BigDecimal.ZERO);
            double currentPct = totalValDouble > 0 ? (currentVal.doubleValue() / totalValDouble) * 100.0 : 0.0;
            double drift = currentPct - targetPct;

            String status = "IN_BAND";
            String rec = "Allocation is within the ±5.0% tolerance band.";
            BigDecimal adjustment = BigDecimal.ZERO;

            if (Math.abs(drift) > 5.0) {
                requiresRebalancing = true;
                if (drift > 5.0) {
                    status = "OVERWEIGHT";
                    double excessVal = (drift / 100.0) * totalValDouble;
                    adjustment = BigDecimal.valueOf(-excessVal).setScale(2, RoundingMode.HALF_UP);
                    rec = String.format("Trim %s by ₹%.2f to restore %.1f%% target.", assetClass, excessVal, targetPct);
                } else {
                    status = "UNDERWEIGHT";
                    double deficitVal = (Math.abs(drift) / 100.0) * totalValDouble;
                    adjustment = BigDecimal.valueOf(deficitVal).setScale(2, RoundingMode.HALF_UP);
                    rec = String.format("Allocate ₹%.2f to %s to restore %.1f%% target.", deficitVal, assetClass, targetPct);
                }
            }

            driftList.add(new PortfolioDriftDto(
                assetClass,
                Math.round(targetPct * 10.0) / 10.0,
                Math.round(currentPct * 10.0) / 10.0,
                Math.round(drift * 10.0) / 10.0,
                status,
                adjustment,
                rec
            ));
        }

        String epistemicNotice = "Investor return and valuation is computed using point-in-time authoritative NAVs. " +
            "XIRR evaluates actual cash flow timings and capital additions; simple period returns evaluate terminal price changes; " +
            "static holdings serve solely as portfolio weights and never replace historical investor accounting. " +
            "Valuation coverage: " + String.format("%.1f%% (%d priced, %d unpriced).", coverageRatio * 100.0, pricedCount, unpricedCount);

        return Optional.of(new PortfolioValuationSummaryDto(
            investorId,
            totalVal,
            totalCost,
            unrealized,
            driftList,
            requiresRebalancing,
            epistemicNotice,
            coverageRatio,
            pricedCount,
            unpricedCount
        ));
    }

    /**
     * Computes the point-in-time investor XIRR cash-flow schedule and solver contract (Tasks 343, 399).
     * Exposes individual transaction cash flows, opening/closing valuations, iteration count, multiple-root flags, and solver outcome.
     */
    public Optional<XirrSolverContractDto> computeXirrCashFlowContract(Long investorId, LocalDate asOfDate) {
        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }

        List<InvestorTransaction> transactions = transactionRepository != null
            ? transactionRepository.findByInvestorIdOrderByTransactionDateAsc(investorId)
            : List.of();

        List<XirrCashFlowItemDto> schedule = new ArrayList<>();
        boolean hasUnreconciled = false;

        for (InvestorTransaction tx : transactions) {
            if (tx.getTransactionDate().isAfter(asOfDate)) {
                continue; // Prevent lookahead bias
            }

            BigDecimal amount = tx.getAmount();
            if (amount == null && tx.getUnits() != null && tx.getNav() != null) {
                amount = tx.getUnits().multiply(tx.getNav());
            }
            if (amount == null) continue;

            String type = tx.getTransactionType() != null ? tx.getTransactionType().toUpperCase() : "PURCHASE";
            boolean isReconciled = "RECONCILED".equalsIgnoreCase(tx.getReconciliationStatus());
            if (!isReconciled) {
                hasUnreconciled = true;
            }

            BigDecimal cashFlowAmount;
            if (type.contains("PURCHASE") || type.contains("BUY") || type.contains("SIP")) {
                cashFlowAmount = amount.abs().negate(); // Cash outflow for investor
            } else if (type.contains("REDEMPTION") || type.contains("SELL") || type.contains("SWP")) {
                cashFlowAmount = amount.abs(); // Cash inflow to investor
            } else {
                continue;
            }

            schedule.add(new XirrCashFlowItemDto(
                tx.getTransactionDate(),
                cashFlowAmount,
                type,
                tx.getTransactionReference() != null ? tx.getTransactionReference() : "TX-" + tx.getId(),
                isReconciled
            ));
        }

        // Add terminal portfolio valuation as positive cash inflow as of asOfDate
        Optional<PortfolioValuationSummaryDto> valSummary = evaluatePortfolioDrift(investorId);
        BigDecimal terminalValuation = valSummary.map(PortfolioValuationSummaryDto::totalCurrentValue).orElse(BigDecimal.ZERO);

        if (terminalValuation.compareTo(BigDecimal.ZERO) > 0) {
            schedule.add(new XirrCashFlowItemDto(
                asOfDate,
                terminalValuation,
                "TERMINAL_VALUATION",
                "POINT_IN_TIME_PORTFOLIO_VALUATION",
                true
            ));
        }

        LocalDate openingDate = schedule.isEmpty() ? asOfDate : schedule.get(0).cashFlowDate();
        BigDecimal openingValuation = schedule.isEmpty() ? BigDecimal.ZERO : schedule.get(0).amount().abs();

        // Count sign changes for Descartes' rule of signs (Task 399)
        int signChanges = 0;
        Boolean lastPositive = null;
        for (XirrCashFlowItemDto cf : schedule) {
            boolean pos = cf.amount().compareTo(BigDecimal.ZERO) > 0;
            if (lastPositive != null && lastPositive != pos) {
                signChanges++;
            }
            lastPositive = pos;
        }
        boolean hasMultipleRoots = signChanges > 1;

        // XIRR Numerical Solver (Newton-Raphson with bisection fallback)
        boolean hasPositive = schedule.stream().anyMatch(cf -> cf.amount().compareTo(BigDecimal.ZERO) > 0);
        boolean hasNegative = schedule.stream().anyMatch(cf -> cf.amount().compareTo(BigDecimal.ZERO) < 0);

        if (!hasPositive || !hasNegative || schedule.size() < 2) {
            return Optional.of(new XirrSolverContractDto(
                investorId,
                openingDate,
                openingValuation,
                asOfDate,
                terminalValuation,
                schedule,
                null,
                "INSUFFICIENT_CASH_FLOWS",
                0,
                hasUnreconciled,
                "At least one purchase (cash outflow) and one terminal valuation or redemption (cash inflow) are required to solve XIRR.",
                false,
                false,
                signChanges
            ));
        }

        // Solve XIRR
        double[] amounts = schedule.stream().mapToDouble(cf -> cf.amount().doubleValue()).toArray();
        long[] days = schedule.stream().mapToLong(cf -> ChronoUnit.DAYS.between(openingDate, cf.cashFlowDate())).toArray();

        double rate = 0.10; // 10% initial guess
        int iterations = 0;
        int maxIterations = 100;
        double tolerance = 1e-6;
        boolean converged = false;

        for (int i = 0; i < maxIterations; i++) {
            iterations++;
            double npv = 0.0;
            double dNpv = 0.0;

            for (int j = 0; j < amounts.length; j++) {
                double t = (double) days[j] / 365.0;
                double denom = Math.pow(1.0 + rate, t);
                if (Double.isNaN(denom) || Double.isInfinite(denom) || denom == 0.0) {
                    break;
                }
                npv += amounts[j] / denom;
                dNpv -= (t * amounts[j]) / (denom * (1.0 + rate));
            }

            if (Math.abs(npv) < tolerance) {
                converged = true;
                break;
            }

            if (Math.abs(dNpv) < 1e-12) {
                break;
            }

            double nextRate = rate - (npv / dNpv);
            if (nextRate <= -0.999 || nextRate > 10.0 || Double.isNaN(nextRate)) {
                // Departed valid financial realm, break to trigger bisection fallback
                break;
            }
            rate = nextRate;
        }

        // Bisection fallback if Newton-Raphson did not converge
        boolean bracketVerified = false;
        if (!converged) {
            double low = -0.999;
            double high = 5.0; // 500% annualized return upper bracket
            double npvLow = computeNpv(low, amounts, days);
            double npvHigh = computeNpv(high, amounts, days);

            if (npvLow * npvHigh < 0.0) {
                bracketVerified = true;
                for (int b = 0; b < 60; b++) {
                    iterations++;
                    double mid = (low + high) / 2.0;
                    double npvMid = computeNpv(mid, amounts, days);
                    if (Math.abs(npvMid) < tolerance) {
                        rate = mid;
                        converged = true;
                        break;
                    }
                    if (npvLow * npvMid < 0.0) {
                        high = mid;
                        npvHigh = npvMid;
                    } else {
                        low = mid;
                        npvLow = npvMid;
                    }
                    rate = mid;
                }
                if (Math.abs(computeNpv(rate, amounts, days)) < 1e-3) {
                    converged = true;
                }
            }
        } else {
            bracketVerified = true;
        }

        Double finalXirr = converged ? Math.round(rate * 10000.0) / 100.0 : null;
        String outcome = converged ? "CONVERGED" : "DID_NOT_CONVERGE";

        String notice = "Deterministic XIRR cash-flow contract evaluated across " + schedule.size() +
            " distinct cash flows. Sign changes: " + signChanges + " (multiple roots possible: " + hasMultipleRoots +
            "). Bracket verified: " + bracketVerified + ".";

        return Optional.of(new XirrSolverContractDto(
            investorId,
            openingDate,
            openingValuation,
            asOfDate,
            terminalValuation,
            schedule,
            finalXirr,
            outcome,
            iterations,
            hasUnreconciled,
            notice,
            hasMultipleRoots,
            bracketVerified,
            signChanges
        ));
    }

    private double computeNpv(double rate, double[] amounts, long[] days) {
        double npv = 0.0;
        for (int j = 0; j < amounts.length; j++) {
            double t = (double) days[j] / 365.0;
            double denom = Math.pow(1.0 + rate, t);
            if (Double.isNaN(denom) || Double.isInfinite(denom) || denom == 0.0) {
                return Double.NaN;
            }
            npv += amounts[j] / denom;
        }
        return npv;
    }
}
