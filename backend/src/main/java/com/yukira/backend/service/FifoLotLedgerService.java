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
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class FifoLotLedgerService {

    private final FifoLotLedgerRepository fifoLotRepository;
    private final InvestorTransactionRepository transactionRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private IndianMutualFundTaxClassificationService taxClassificationService;

    public FifoLotLedgerService(
        FifoLotLedgerRepository fifoLotRepository,
        InvestorTransactionRepository transactionRepository,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository
    ) {
        this.fifoLotRepository = fifoLotRepository;
        this.transactionRepository = transactionRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
    }

    @Autowired(required = false)
    public void setTaxClassificationService(IndianMutualFundTaxClassificationService taxClassificationService) {
        this.taxClassificationService = taxClassificationService;
    }

    public record ActiveTaxLotDto(
        Long lotId,
        Long schemeOptionId,
        String schemeName,
        LocalDate acquisitionDate,
        BigDecimal unitsAcquired,
        BigDecimal unitsRemaining,
        BigDecimal acquisitionNav,
        BigDecimal totalCostBasis,
        BigDecimal currentNav,
        BigDecimal currentMarketValue,
        BigDecimal unrealizedGainLoss,
        long holdingDays,
        String taxClassification, // LTCG, STCG, DEEMED_STCG
        String statutorySection, // Section 112A, Section 111A, Section 50AA, Section 112
        String provisionalNotice
    ) {}

    public record ConsumedLotBreakdownDto(
        Long lotId,
        LocalDate acquisitionDate,
        BigDecimal unitsConsumed,
        BigDecimal acquisitionNav,
        BigDecimal redemptionNav,
        BigDecimal costBasis,
        BigDecimal realizedProceeds,
        BigDecimal realizedGainLoss,
        long holdingDays,
        String taxClassification,
        String statutorySection,
        String provisionalNotice
    ) {}

    public record RedemptionOutcomeDto(
        Long transactionId,
        BigDecimal totalUnitsRedeemed,
        BigDecimal totalProceeds,
        BigDecimal totalRealizedGainLoss,
        List<ConsumedLotBreakdownDto> consumedLots
    ) {}

    public void processPurchaseTransaction(InvestorTransaction tx) {
        if (!"PURCHASE".equalsIgnoreCase(tx.getTransactionType()) && !"BUY".equalsIgnoreCase(tx.getTransactionType())) {
            return;
        }

        FifoLotLedger lot = new FifoLotLedger();
        lot.setInvestor(tx.getInvestor());
        lot.setSchemeOption(tx.getSchemeOption());
        lot.setAcquisitionTransaction(tx);
        lot.setAcquisitionDate(tx.getTransactionDate());
        lot.setUnitsAcquired(tx.getUnits());
        lot.setUnitsRemaining(tx.getUnits());
        lot.setAcquisitionNav(tx.getNav());
        BigDecimal cost = tx.getUnits() != null && tx.getNav() != null
            ? tx.getUnits().multiply(tx.getNav()).setScale(4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        lot.setTotalCost(cost);
        lot.setIsDepleted(false);

        fifoLotRepository.save(lot);
    }

    public RedemptionOutcomeDto processRedemptionFifo(Long investorId, Long schemeOptionId, Long redemptionTxId) {
        InvestorTransaction redemptionTx = transactionRepository.findById(redemptionTxId)
            .orElseThrow(() -> new IllegalArgumentException("Redemption transaction not found: " + redemptionTxId));

        List<FifoLotLedger> activeLots = fifoLotRepository
            .findByInvestorIdAndSchemeOptionIdAndIsDepletedFalseOrderByAcquisitionDateAsc(investorId, schemeOptionId);

        BigDecimal unitsToRedeem = redemptionTx.getUnits();
        BigDecimal redemptionNav = redemptionTx.getNav() != null ? redemptionTx.getNav() : BigDecimal.ZERO;

        List<ConsumedLotBreakdownDto> consumedList = new ArrayList<>();
        BigDecimal totalRealizedGainLoss = BigDecimal.ZERO;
        BigDecimal totalProceeds = BigDecimal.ZERO;

        for (FifoLotLedger lot : activeLots) {
            if (unitsToRedeem.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal lotRemaining = lot.getUnitsRemaining();
            BigDecimal unitsFromLot = lotRemaining.min(unitsToRedeem);

            // Cost basis for units consumed
            BigDecimal costBasis = unitsFromLot.multiply(lot.getAcquisitionNav()).setScale(4, RoundingMode.HALF_UP);
            BigDecimal proceeds = unitsFromLot.multiply(redemptionNav).setScale(4, RoundingMode.HALF_UP);
            BigDecimal gainLoss = proceeds.subtract(costBasis);

            long days = ChronoUnit.DAYS.between(lot.getAcquisitionDate(), redemptionTx.getTransactionDate());
            
            String category = lot.getSchemeOption() != null && lot.getSchemeOption().getScheme() != null
                ? lot.getSchemeOption().getScheme().getCategory() : "Equity";
            
            var taxOutcome = taxClassificationService != null
                ? taxClassificationService.classifyTaxLot(category, lot.getAcquisitionDate(), redemptionTx.getTransactionDate())
                : new IndianMutualFundTaxClassificationService.TaxClassificationOutcomeDto(
                    days >= 365 ? "Section 112A" : "Section 111A",
                    days >= 365 ? "LTCG" : "STCG",
                    days,
                    false,
                    days >= 365 ? 12.5 : 20.0,
                    "Equity standard",
                    IndianMutualFundTaxClassificationService.PROVISIONAL_TAX_NOTICE
                );

            consumedList.add(new ConsumedLotBreakdownDto(
                lot.getId(),
                lot.getAcquisitionDate(),
                unitsFromLot,
                lot.getAcquisitionNav(),
                redemptionNav,
                costBasis,
                proceeds,
                gainLoss,
                days,
                taxOutcome.taxClassification(),
                taxOutcome.statutorySection(),
                taxOutcome.provisionalNotice()
            ));

            totalRealizedGainLoss = totalRealizedGainLoss.add(gainLoss);
            totalProceeds = totalProceeds.add(proceeds);

            BigDecimal newRemaining = lotRemaining.subtract(unitsFromLot);
            lot.setUnitsRemaining(newRemaining);
            if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
                lot.setIsDepleted(true);
            }
            fifoLotRepository.save(lot);

            unitsToRedeem = unitsToRedeem.subtract(unitsFromLot);
        }

        return new RedemptionOutcomeDto(
            redemptionTx.getId(),
            redemptionTx.getUnits(),
            totalProceeds,
            totalRealizedGainLoss,
            consumedList
        );
    }

    public List<ActiveTaxLotDto> getActiveLots(Long investorId, Long schemeOptionId) {
        List<FifoLotLedger> lots = fifoLotRepository
            .findByInvestorIdAndSchemeOptionIdAndIsDepletedFalseOrderByAcquisitionDateAsc(investorId, schemeOptionId);

        LocalDate today = LocalDate.now();

        // Get latest NAV
        var latestObs = navObservationRepository.findTopBySchemeOptionIdOrderByEffectiveDateDesc(schemeOptionId);
        BigDecimal currentNav = latestObs.map(NavObservation::getNavValue).orElse(BigDecimal.ZERO);

        List<ActiveTaxLotDto> result = new ArrayList<>();
        for (FifoLotLedger lot : lots) {
            BigDecimal mktVal = lot.getUnitsRemaining().multiply(currentNav).setScale(4, RoundingMode.HALF_UP);
            BigDecimal costBasis = lot.getUnitsRemaining().multiply(lot.getAcquisitionNav()).setScale(4, RoundingMode.HALF_UP);
            BigDecimal unrealized = mktVal.subtract(costBasis);

            long days = ChronoUnit.DAYS.between(lot.getAcquisitionDate(), today);
            
            String category = lot.getSchemeOption() != null && lot.getSchemeOption().getScheme() != null
                ? lot.getSchemeOption().getScheme().getCategory() : "Equity";
            
            var taxOutcome = taxClassificationService != null
                ? taxClassificationService.classifyTaxLot(category, lot.getAcquisitionDate(), today)
                : new IndianMutualFundTaxClassificationService.TaxClassificationOutcomeDto(
                    days >= 365 ? "Section 112A" : "Section 111A",
                    days >= 365 ? "LTCG" : "STCG",
                    days,
                    false,
                    days >= 365 ? 12.5 : 20.0,
                    "Equity standard",
                    IndianMutualFundTaxClassificationService.PROVISIONAL_TAX_NOTICE
                );

            result.add(new ActiveTaxLotDto(
                lot.getId(),
                schemeOptionId,
                lot.getSchemeOption() != null ? lot.getSchemeOption().getOptionName() : "Unknown",
                lot.getAcquisitionDate(),
                lot.getUnitsAcquired(),
                lot.getUnitsRemaining(),
                lot.getAcquisitionNav(),
                costBasis,
                currentNav,
                mktVal,
                unrealized,
                days,
                taxOutcome.taxClassification(),
                taxOutcome.statutorySection(),
                taxOutcome.provisionalNotice()
            ));
        }

        return result;
    }

    public record CorporateActionTransitionOutcomeDto(
        Long investorId,
        Long sourceOptionId,
        Long targetOptionId,
        int migratedLotsCount,
        BigDecimal totalSourceUnitsDepleted,
        BigDecimal totalTargetUnitsCreated,
        String corporateActionRef,
        String lineageIntegrityNotice
    ) {}

    /**
     * Corporate Actions in FIFO Lot Ledger (Task 397).
     * Carries forward original acquisition date and cost basis during scheme mergers,
     * plan switches (e.g. Regular to Direct), or splits without triggering artificial tax events.
     */
    public CorporateActionTransitionOutcomeDto processCorporateActionSwitchOrMerge(
        Long investorId,
        Long sourceOptionId,
        Long targetOptionId,
        BigDecimal unitConversionRatio,
        String corporateActionRef
    ) {
        if (unitConversionRatio == null || unitConversionRatio.compareTo(BigDecimal.ZERO) <= 0) {
            unitConversionRatio = BigDecimal.ONE;
        }

        SchemeOption targetOption = schemeOptionRepository.findById(targetOptionId)
            .orElseThrow(() -> new IllegalArgumentException("Target scheme option not found: " + targetOptionId));

        List<FifoLotLedger> activeSourceLots = fifoLotRepository
            .findByInvestorIdAndSchemeOptionIdAndIsDepletedFalseOrderByAcquisitionDateAsc(investorId, sourceOptionId);

        BigDecimal totalDepletedUnits = BigDecimal.ZERO;
        BigDecimal totalCreatedUnits = BigDecimal.ZERO;
        int count = 0;

        for (FifoLotLedger srcLot : activeSourceLots) {
            BigDecimal unitsRemaining = srcLot.getUnitsRemaining();
            if (unitsRemaining.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Compute converted units
            BigDecimal targetUnits = unitsRemaining.multiply(unitConversionRatio).setScale(4, RoundingMode.HALF_UP);
            // Effective NAV adjusted so total cost basis is identical
            BigDecimal adjustedNav = srcLot.getTotalCost().divide(targetUnits, 4, RoundingMode.HALF_UP);

            // Deplete source lot with corporate action ref
            srcLot.setUnitsRemaining(BigDecimal.ZERO);
            srcLot.setIsDepleted(true);
            fifoLotRepository.save(srcLot);

            // Create target lot preserving original acquisition date
            FifoLotLedger newLot = new FifoLotLedger();
            newLot.setInvestor(srcLot.getInvestor());
            newLot.setSchemeOption(targetOption);
            newLot.setAcquisitionTransaction(srcLot.getAcquisitionTransaction());
            newLot.setAcquisitionDate(srcLot.getAcquisitionDate()); // Preserves holding duration
            newLot.setUnitsAcquired(targetUnits);
            newLot.setUnitsRemaining(targetUnits);
            newLot.setAcquisitionNav(adjustedNav);
            newLot.setIsDepleted(false);
            fifoLotRepository.save(newLot);

            totalDepletedUnits = totalDepletedUnits.add(unitsRemaining);
            totalCreatedUnits = totalCreatedUnits.add(targetUnits);
            count++;
        }

        return new CorporateActionTransitionOutcomeDto(
            investorId,
            sourceOptionId,
            targetOptionId,
            count,
            totalDepletedUnits,
            totalCreatedUnits,
            corporateActionRef != null ? corporateActionRef : "SCHEME_REORGANIZATION_MANDATE",
            "Original acquisition dates and grandfathered cost baselines preserved pursuant to Section 47(xix) / 47(xviii) of Income-tax Act, 1961."
        );
    }

    /**
     * Canonical Transaction Reconciliation Lifecycle (Task 398).
     * Transitions transaction status across: INGESTED -> PARSED -> LOT_ALLOCATED -> RECONCILED -> DISCREPANCY_FLAGGED.
     */
    public boolean reconcileTransactionLifecycle(Long transactionId, String targetStatus, String diagnosticNote) {
        return transactionRepository.findById(transactionId).map(tx -> {
            tx.setReconciliationStatus(targetStatus);
            if (diagnosticNote != null) {
                tx.setReconciliationNotes(diagnosticNote);
            }
            transactionRepository.save(tx);
            return true;
        }).orElse(false);
    }
}
