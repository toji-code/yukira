package com.yukira.backend.calculation.performance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pure, deterministic FIFO transaction lot matching and realized/unrealized performance calculator.
 *
 * Epistemic Discipline:
 * 1. Accounting convention: Documented FIFO (First-In, First-Out) lot matching.
 * 2. Realized gain/loss is strictly matched against dated purchase lots.
 * 3. Never labels the difference between current valuation and total gross purchases as "realized gain".
 * 4. Tracks remaining cost basis of open lots deterministically.
 * 5. Holding duration category (SHORT_TERM vs LONG_TERM) is purely mathematical (<365 days vs >=365 days);
 *    never described as tax liability or Indian capital gains tax computation.
 * 6. Internal transfers (SWITCH_OUT, STP_REDEMPTION) are tracked with isInternalTransfer=true so that
 *    portfolio-level external withdrawals are not confused with inter-scheme transfers.
 */
public final class CostBasisLotCalculator {

    public record PurchaseLot(
        Long transactionId,
        Long schemeOptionId,
        String schemeName,
        LocalDate purchaseDate,
        BigDecimal initialUnits,
        BigDecimal remainingUnits,
        BigDecimal purchaseNav,
        BigDecimal initialCostBasis,
        BigDecimal remainingCostBasis,
        BigDecimal currentNav,
        BigDecimal currentMarketValue,
        BigDecimal unrealizedGain,
        Long holdingPeriodDays,
        String holdingPeriodCategory, // SHORT_TERM (<365 days), LONG_TERM (>=365 days)
        Boolean isFullyConsumed
    ) {
        // Backwards-compatible 6-param constructor
        public PurchaseLot(
            Long transactionId,
            LocalDate purchaseDate,
            BigDecimal initialUnits,
            BigDecimal remainingUnits,
            BigDecimal purchaseNav,
            BigDecimal remainingCostBasis
        ) {
            this(
                transactionId,
                null,
                null,
                purchaseDate,
                initialUnits,
                remainingUnits,
                purchaseNav,
                initialUnits != null && purchaseNav != null ? initialUnits.multiply(purchaseNav).setScale(4, RoundingMode.HALF_UP) : remainingCostBasis,
                remainingCostBasis,
                null,
                null,
                null,
                0L,
                "SHORT_TERM",
                remainingUnits != null && remainingUnits.compareTo(BigDecimal.ZERO) == 0
            );
        }
    }

    public record RealizedGainRecord(
        Long redemptionTransactionId,
        Long schemeOptionId,
        String schemeName,
        LocalDate redemptionDate,
        BigDecimal unitsRedeemed,
        BigDecimal redemptionPrice,
        BigDecimal redemptionGrossAmount,
        BigDecimal costBasisRedeemed,
        BigDecimal netRealizedGain,
        Long matchedPurchaseTransactionId,
        LocalDate matchedPurchaseDate,
        BigDecimal matchedPurchasePrice,
        Long holdingPeriodDays,
        String holdingPeriodCategory,
        String redemptionType, // REDEMPTION, STP_REDEMPTION, SWITCH_OUT
        Boolean isInternalTransfer,
        BigDecimal chargesDeducted
    ) {
        // Backwards-compatible 10-param constructor
        public RealizedGainRecord(
            Long redemptionTransactionId,
            LocalDate redemptionDate,
            BigDecimal unitsRedeemed,
            BigDecimal redemptionPrice,
            BigDecimal redemptionGrossAmount,
            BigDecimal costBasisRedeemed,
            BigDecimal netRealizedGain,
            Long matchedPurchaseTransactionId,
            LocalDate matchedPurchaseDate,
            BigDecimal matchedPurchasePrice
        ) {
            this(
                redemptionTransactionId,
                null,
                null,
                redemptionDate,
                unitsRedeemed,
                redemptionPrice,
                redemptionGrossAmount,
                costBasisRedeemed,
                netRealizedGain,
                matchedPurchaseTransactionId,
                matchedPurchaseDate,
                matchedPurchasePrice,
                (redemptionDate != null && matchedPurchaseDate != null)
                    ? ChronoUnit.DAYS.between(matchedPurchaseDate, redemptionDate)
                    : 0L,
                (redemptionDate != null && matchedPurchaseDate != null && ChronoUnit.DAYS.between(matchedPurchaseDate, redemptionDate) >= 365)
                    ? "LONG_TERM"
                    : "SHORT_TERM",
                "REDEMPTION",
                false,
                BigDecimal.ZERO
            );
        }
    }

    public record PerformanceDecompositionResult(
        BigDecimal totalInvestedCapital,
        BigDecimal totalRedeemedCapital,
        BigDecimal totalDividendIncome,
        BigDecimal totalDividendReinvested,
        BigDecimal realizedGainOrLoss,
        BigDecimal remainingCostBasis,
        BigDecimal currentMarketValue,
        BigDecimal unrealizedGainOrLoss,
        BigDecimal totalEconomicGain, // Realized + Unrealized + Dividends
        BigDecimal totalChargesRecorded,
        BigDecimal remainingUnits,
        List<PurchaseLot> openLots,
        List<PurchaseLot> allLots,
        List<RealizedGainRecord> realizedGainsHistory,
        String accountingMethod,
        String accountingNotes
    ) {
        // Backwards-compatible 13-param constructor
        public PerformanceDecompositionResult(
            BigDecimal totalInvestedCapital,
            BigDecimal totalRedeemedCapital,
            BigDecimal totalDividendIncome,
            BigDecimal realizedGainOrLoss,
            BigDecimal remainingCostBasis,
            BigDecimal currentMarketValue,
            BigDecimal unrealizedGainOrLoss,
            BigDecimal totalEconomicGain,
            BigDecimal remainingUnits,
            List<PurchaseLot> openLots,
            List<RealizedGainRecord> realizedGainsHistory,
            String accountingMethod,
            String accountingNotes
        ) {
            this(
                totalInvestedCapital,
                totalRedeemedCapital,
                totalDividendIncome,
                BigDecimal.ZERO,
                realizedGainOrLoss,
                remainingCostBasis,
                currentMarketValue,
                unrealizedGainOrLoss,
                totalEconomicGain,
                BigDecimal.ZERO,
                remainingUnits,
                openLots,
                openLots,
                realizedGainsHistory,
                accountingMethod,
                accountingNotes
            );
        }
    }

    public record TransactionInput(
        Long id,
        String type, // PURCHASE, SIP_PURCHASE, STP_PURCHASE, SWITCH_IN, REDEMPTION, STP_REDEMPTION, SWITCH_OUT, DIVIDEND_PAYOUT, DIVIDEND_REINVESTMENT, BONUS
        LocalDate date,
        BigDecimal units,
        BigDecimal nav,
        BigDecimal grossAmount,
        BigDecimal charges, // stamp duty, STT, exit load
        Long schemeOptionId,
        String schemeName
    ) {
        public TransactionInput(
            Long id,
            String type,
            LocalDate date,
            BigDecimal units,
            BigDecimal nav,
            BigDecimal grossAmount
        ) {
            this(id, type, date, units, nav, grossAmount, BigDecimal.ZERO, null, null);
        }
    }

    public static PerformanceDecompositionResult calculateFifo(
        List<TransactionInput> transactions,
        BigDecimal latestNav
    ) {
        return calculateFifo(transactions, latestNav, LocalDate.now());
    }

    public static PerformanceDecompositionResult calculateFifo(
        List<TransactionInput> transactions,
        BigDecimal latestNav,
        LocalDate asOfDate
    ) {
        if (transactions == null || transactions.isEmpty()) {
            return new PerformanceDecompositionResult(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(),
                List.of(),
                List.of(),
                "FIFO_TRANSACTION_LOT",
                "No transaction history available for cost basis decomposition."
            );
        }

        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : LocalDate.now();

        // Sort chronologically ascending
        List<TransactionInput> sorted = new ArrayList<>(transactions);
        sorted.sort(Comparator.comparing(TransactionInput::date).thenComparing(TransactionInput::id));

        Deque<MutablePurchaseLot> openLotQueue = new ArrayDeque<>();
        List<MutablePurchaseLot> allLotsHistorical = new ArrayList<>();
        List<RealizedGainRecord> realizedRecords = new ArrayList<>();

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalRedeemed = BigDecimal.ZERO;
        BigDecimal totalDividends = BigDecimal.ZERO;
        BigDecimal totalDividendsReinvested = BigDecimal.ZERO;
        BigDecimal totalRealizedGain = BigDecimal.ZERO;
        BigDecimal totalCharges = BigDecimal.ZERO;

        for (TransactionInput tx : sorted) {
            String t = tx.type() != null ? tx.type().toUpperCase(Locale.ROOT) : "OTHER";
            BigDecimal u = tx.units() != null ? tx.units().abs() : BigDecimal.ZERO;
            BigDecimal amt = tx.grossAmount() != null ? tx.grossAmount().abs() : BigDecimal.ZERO;
            BigDecimal charges = tx.charges() != null ? tx.charges().abs() : BigDecimal.ZERO;
            totalCharges = totalCharges.add(charges);

            BigDecimal price = tx.nav() != null && tx.nav().compareTo(BigDecimal.ZERO) > 0
                ? tx.nav()
                : (u.compareTo(BigDecimal.ZERO) > 0 ? amt.divide(u, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO);

            switch (t) {
                case "PURCHASE", "SIP_PURCHASE", "STP_PURCHASE", "SWITCH_IN", "BONUS" -> {
                    totalInvested = totalInvested.add(amt);
                    if (u.compareTo(BigDecimal.ZERO) > 0) {
                        MutablePurchaseLot lot = new MutablePurchaseLot(
                            tx.id(),
                            tx.schemeOptionId(),
                            tx.schemeName(),
                            tx.date(),
                            u,
                            u,
                            price,
                            amt,
                            amt
                        );
                        openLotQueue.addLast(lot);
                        allLotsHistorical.add(lot);
                    }
                }
                case "DIVIDEND_REINVESTMENT" -> {
                    totalDividendsReinvested = totalDividendsReinvested.add(amt);
                    if (u.compareTo(BigDecimal.ZERO) > 0) {
                        MutablePurchaseLot lot = new MutablePurchaseLot(
                            tx.id(),
                            tx.schemeOptionId(),
                            tx.schemeName(),
                            tx.date(),
                            u,
                            u,
                            price,
                            amt,
                            amt
                        );
                        openLotQueue.addLast(lot);
                        allLotsHistorical.add(lot);
                    }
                }
                case "DIVIDEND_PAYOUT" -> {
                    totalDividends = totalDividends.add(amt);
                }
                case "REDEMPTION", "STP_REDEMPTION", "SWITCH_OUT" -> {
                    totalRedeemed = totalRedeemed.add(amt);
                    BigDecimal unitsToRedeem = u;
                    boolean isInternal = t.equals("SWITCH_OUT") || t.equals("STP_REDEMPTION");

                    while (unitsToRedeem.compareTo(BigDecimal.ZERO) > 0 && !openLotQueue.isEmpty()) {
                        MutablePurchaseLot earliestLot = openLotQueue.peekFirst();
                        BigDecimal lotAvailable = earliestLot.remainingUnits;

                        long holdingDays = ChronoUnit.DAYS.between(earliestLot.purchaseDate, tx.date());
                        String holdingCat = holdingDays >= 365 ? "LONG_TERM" : "SHORT_TERM";

                        if (lotAvailable.compareTo(unitsToRedeem) <= 0) {
                            // Entire lot consumed
                            BigDecimal costForLot = lotAvailable.multiply(earliestLot.purchaseNav).setScale(4, RoundingMode.HALF_UP);
                            BigDecimal grossForLot = lotAvailable.multiply(price).setScale(4, RoundingMode.HALF_UP);
                            BigDecimal gain = grossForLot.subtract(costForLot);

                            realizedRecords.add(new RealizedGainRecord(
                                tx.id(),
                                tx.schemeOptionId(),
                                tx.schemeName(),
                                tx.date(),
                                lotAvailable,
                                price,
                                grossForLot,
                                costForLot,
                                gain,
                                earliestLot.transactionId,
                                earliestLot.purchaseDate,
                                earliestLot.purchaseNav,
                                holdingDays,
                                holdingCat,
                                t,
                                isInternal,
                                charges
                            ));

                            totalRealizedGain = totalRealizedGain.add(gain);
                            unitsToRedeem = unitsToRedeem.subtract(lotAvailable);
                            earliestLot.remainingUnits = BigDecimal.ZERO;
                            earliestLot.remainingCostBasis = BigDecimal.ZERO;
                            openLotQueue.pollFirst();
                        } else {
                            // Partial lot consumed
                            BigDecimal costForPartial = unitsToRedeem.multiply(earliestLot.purchaseNav).setScale(4, RoundingMode.HALF_UP);
                            BigDecimal grossForPartial = unitsToRedeem.multiply(price).setScale(4, RoundingMode.HALF_UP);
                            BigDecimal gain = grossForPartial.subtract(costForPartial);

                            realizedRecords.add(new RealizedGainRecord(
                                tx.id(),
                                tx.schemeOptionId(),
                                tx.schemeName(),
                                tx.date(),
                                unitsToRedeem,
                                price,
                                grossForPartial,
                                costForPartial,
                                gain,
                                earliestLot.transactionId,
                                earliestLot.purchaseDate,
                                earliestLot.purchaseNav,
                                holdingDays,
                                holdingCat,
                                t,
                                isInternal,
                                charges
                            ));

                            totalRealizedGain = totalRealizedGain.add(gain);
                            earliestLot.remainingUnits = earliestLot.remainingUnits.subtract(unitsToRedeem);
                            earliestLot.remainingCostBasis = earliestLot.remainingUnits.multiply(earliestLot.purchaseNav).setScale(4, RoundingMode.HALF_UP);
                            unitsToRedeem = BigDecimal.ZERO;
                        }
                    }
                }
                default -> {
                    // Non-standard types skip lot matching
                }
            }
        }

        // Aggregate open lots and all lots
        BigDecimal totalRemainingUnits = BigDecimal.ZERO;
        BigDecimal totalRemainingCostBasis = BigDecimal.ZERO;
        List<PurchaseLot> openLotsList = new ArrayList<>();
        List<PurchaseLot> allLotsList = new ArrayList<>();

        for (MutablePurchaseLot mLot : allLotsHistorical) {
            long daysHeld = ChronoUnit.DAYS.between(mLot.purchaseDate, effectiveAsOf);
            String cat = daysHeld >= 365 ? "LONG_TERM" : "SHORT_TERM";
            boolean isConsumed = mLot.remainingUnits.compareTo(BigDecimal.ZERO) == 0;

            BigDecimal mktVal = (latestNav != null && latestNav.compareTo(BigDecimal.ZERO) > 0)
                ? mLot.remainingUnits.multiply(latestNav).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            BigDecimal unrlz = mktVal.compareTo(BigDecimal.ZERO) > 0
                ? mktVal.subtract(mLot.remainingCostBasis).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            PurchaseLot lotDto = new PurchaseLot(
                mLot.transactionId,
                mLot.schemeOptionId,
                mLot.schemeName,
                mLot.purchaseDate,
                mLot.initialUnits,
                mLot.remainingUnits,
                mLot.purchaseNav,
                mLot.initialCostBasis,
                mLot.remainingCostBasis,
                latestNav,
                mktVal,
                unrlz,
                daysHeld,
                cat,
                isConsumed
            );

            allLotsList.add(lotDto);

            if (!isConsumed) {
                totalRemainingUnits = totalRemainingUnits.add(mLot.remainingUnits);
                totalRemainingCostBasis = totalRemainingCostBasis.add(mLot.remainingCostBasis);
                openLotsList.add(lotDto);
            }
        }

        BigDecimal currentMktValue = (latestNav != null && latestNav.compareTo(BigDecimal.ZERO) > 0)
            ? totalRemainingUnits.multiply(latestNav).setScale(4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal unrealizedGain = currentMktValue.compareTo(BigDecimal.ZERO) > 0
            ? currentMktValue.subtract(totalRemainingCostBasis)
            : BigDecimal.ZERO;

        BigDecimal totalEconGain = totalRealizedGain.add(unrealizedGain).add(totalDividends).subtract(totalCharges);

        return new PerformanceDecompositionResult(
            totalInvested.setScale(4, RoundingMode.HALF_UP),
            totalRedeemed.setScale(4, RoundingMode.HALF_UP),
            totalDividends.setScale(4, RoundingMode.HALF_UP),
            totalDividendsReinvested.setScale(4, RoundingMode.HALF_UP),
            totalRealizedGain.setScale(4, RoundingMode.HALF_UP),
            totalRemainingCostBasis.setScale(4, RoundingMode.HALF_UP),
            currentMktValue,
            unrealizedGain.setScale(4, RoundingMode.HALF_UP),
            totalEconGain.setScale(4, RoundingMode.HALF_UP),
            totalCharges.setScale(4, RoundingMode.HALF_UP),
            totalRemainingUnits.setScale(4, RoundingMode.HALF_UP),
            openLotsList,
            allLotsList,
            realizedRecords,
            "FIFO_TRANSACTION_LOT",
            "Matched against dated purchase lots under First-In First-Out (FIFO) discipline."
        );
    }

    private static class MutablePurchaseLot {
        Long transactionId;
        Long schemeOptionId;
        String schemeName;
        LocalDate purchaseDate;
        BigDecimal initialUnits;
        BigDecimal remainingUnits;
        BigDecimal purchaseNav;
        BigDecimal initialCostBasis;
        BigDecimal remainingCostBasis;

        MutablePurchaseLot(
            Long transactionId,
            Long schemeOptionId,
            String schemeName,
            LocalDate purchaseDate,
            BigDecimal initialUnits,
            BigDecimal remainingUnits,
            BigDecimal purchaseNav,
            BigDecimal initialCostBasis,
            BigDecimal remainingCostBasis
        ) {
            this.transactionId = transactionId;
            this.schemeOptionId = schemeOptionId;
            this.schemeName = schemeName;
            this.purchaseDate = purchaseDate;
            this.initialUnits = initialUnits;
            this.remainingUnits = remainingUnits;
            this.purchaseNav = purchaseNav;
            this.initialCostBasis = initialCostBasis;
            this.remainingCostBasis = remainingCostBasis;
        }
    }
}
