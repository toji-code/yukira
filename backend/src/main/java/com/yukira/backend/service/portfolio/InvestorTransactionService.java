package com.yukira.backend.service.portfolio;

import com.yukira.backend.calculation.performance.CostBasisLotCalculator;
import com.yukira.backend.calculation.xirr.XirrCalculator;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.transaction.*;
import com.yukira.backend.dto.portfolio.performance.*;

import com.yukira.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class InvestorTransactionService {

    private static final Logger log = LoggerFactory.getLogger(InvestorTransactionService.class);

    private final InvestorRepository investorRepository;
    private final InvestorTransactionRepository transactionRepository;
    private final InvestorPortfolioHoldingRepository portfolioHoldingRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final NavObservationRepository navObservationRepository;
    private final PortfolioImportRepository importRepository;
    private final PortfolioImportTransactionRepository importTransactionRepository;
    private final InvestorPortfolioService portfolioService;

    public InvestorTransactionService(
        InvestorRepository investorRepository,
        InvestorTransactionRepository transactionRepository,
        InvestorPortfolioHoldingRepository portfolioHoldingRepository,
        SchemeOptionRepository schemeOptionRepository,
        NavObservationRepository navObservationRepository,
        PortfolioImportRepository importRepository,
        PortfolioImportTransactionRepository importTransactionRepository,
        InvestorPortfolioService portfolioService
    ) {
        this.investorRepository = investorRepository;
        this.transactionRepository = transactionRepository;
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.navObservationRepository = navObservationRepository;
        this.importRepository = importRepository;
        this.importTransactionRepository = importTransactionRepository;
        this.portfolioService = portfolioService;
    }

    /**
     * Lists filtered transactions for the authenticated investor.
     */
    @Transactional(readOnly = true)
    public List<InvestorTransactionDto> listTransactions(
        String auth0Subject,
        Long schemeOptionId,
        String txType,
        LocalDate startDate,
        LocalDate endDate
    ) {
        Investor investor = portfolioService.resolveInvestor(auth0Subject);
        List<InvestorTransaction> txList = transactionRepository.findFilteredTransactions(
            investor.getId(),
            schemeOptionId,
            (txType != null && !txType.isBlank()) ? txType.toUpperCase(Locale.ROOT) : null,
            startDate,
            endDate
        );

        return txList.stream().map(this::toDto).toList();
    }

    /**
     * Records a manual investor transaction with auditable provenance.
     */
    public InvestorTransactionDto createManualTransaction(
        String auth0Subject,
        CreateManualTransactionRequest req
    ) {
        if (req == null) {
            throw new IllegalArgumentException("Transaction request cannot be null.");
        }
        if (req.schemeOptionId() == null) {
            throw new IllegalArgumentException("schemeOptionId is required for manual transaction entry.");
        }
        if (req.transactionType() == null || req.transactionType().isBlank()) {
            throw new IllegalArgumentException("transactionType is required.");
        }
        if (req.transactionDate() == null) {
            throw new IllegalArgumentException("transactionDate is required.");
        }

        Investor investor = portfolioService.resolveInvestor(auth0Subject);
        SchemeOption option = schemeOptionRepository.findById(req.schemeOptionId())
            .orElseThrow(() -> new IllegalArgumentException("Scheme option not found: " + req.schemeOptionId()));

        String normalizedType = normalizeTransactionType(req.transactionType());
        BigDecimal units = req.units() != null ? req.units().abs() : BigDecimal.ZERO;
        BigDecimal grossAmt = req.grossAmount() != null ? req.grossAmount().abs() : BigDecimal.ZERO;
        BigDecimal charges = req.chargesOrTaxes() != null ? req.chargesOrTaxes().abs() : BigDecimal.ZERO;
        BigDecimal netAmt = grossAmt.subtract(charges);
        BigDecimal dividendAmt = req.dividendAmount() != null ? req.dividendAmount().abs() : BigDecimal.ZERO;

        BigDecimal nav = req.nav();
        if (nav == null && units.compareTo(BigDecimal.ZERO) > 0 && grossAmt.compareTo(BigDecimal.ZERO) > 0) {
            nav = grossAmt.divide(units, 4, RoundingMode.HALF_UP);
        }

        String dedupHash = computeDedupHash(
            investor.getId(),
            option.getId(),
            req.transactionDate(),
            normalizedType,
            units,
            netAmt
        );

        InvestorTransaction tx = new InvestorTransaction();
        tx.setInvestor(investor);
        tx.setSchemeOption(option);
        tx.setTransactionType(normalizedType);
        tx.setTransactionDate(req.transactionDate());
        tx.setSettlementDate(req.settlementDate() != null ? req.settlementDate() : req.transactionDate());
        tx.setUnits(units);
        tx.setNav(nav);
        tx.setGrossAmount(grossAmt);
        tx.setNetAmount(netAmt);
        tx.setChargesOrTaxes(charges);
        tx.setDividendAmount(dividendAmt);
        tx.setSourceAttribution("MANUAL_ENTRY");
        tx.setSourceDocumentRef(req.sourceDocumentRef());
        tx.setOriginalDescription(req.originalDescription() != null ? req.originalDescription() : "Manual " + normalizedType);
        tx.setReconciliationStatus("UNRECONCILED");
        tx.setIsReversed(false);
        tx.setDedupHash(dedupHash);

        InvestorTransaction saved = transactionRepository.save(tx);
        log.info("Recorded manual transaction #{} for investor {} in scheme {}",
            saved.getId(), investor.getId(), option.getId());

        return toDto(saved);
    }

    /**
     * Auditable transaction reversal: sets isReversed on target and persists compensating record.
     */
    public InvestorTransactionDto reverseTransaction(
        String auth0Subject,
        Long transactionId,
        ReverseTransactionRequest req
    ) {
        Investor investor = portfolioService.resolveInvestor(auth0Subject);
        InvestorTransaction target = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        if (!target.getInvestor().getId().equals(investor.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized access to transaction.");
        }

        if (Boolean.TRUE.equals(target.getIsReversed())) {
            throw new IllegalStateException("Transaction #" + transactionId + " is already reversed.");
        }

        target.setIsReversed(true);
        transactionRepository.save(target);

        // Create compensating reversal transaction
        InvestorTransaction reversal = new InvestorTransaction();
        reversal.setInvestor(investor);
        reversal.setSchemeOption(target.getSchemeOption());
        reversal.setTransactionType("REVERSAL");
        reversal.setTransactionDate(LocalDate.now());
        reversal.setSettlementDate(LocalDate.now());
        reversal.setUnits(target.getUnits().negate());
        reversal.setNav(target.getNav());
        reversal.setGrossAmount(target.getGrossAmount().negate());
        reversal.setNetAmount(target.getNetAmount().negate());
        reversal.setChargesOrTaxes(BigDecimal.ZERO);
        reversal.setDividendAmount(BigDecimal.ZERO);
        reversal.setSourceAttribution("MANUAL_ENTRY");
        reversal.setOriginalDescription("Reversal of Tx #" + target.getId() + ": " + (req != null && req.reversalReason() != null ? req.reversalReason() : "User requested correction"));
        reversal.setReversalReference(target);
        reversal.setIsReversed(false);

        InvestorTransaction savedReversal = transactionRepository.save(reversal);
        log.info("Created reversal transaction #{} targeting original #{}", savedReversal.getId(), target.getId());

        return toDto(savedReversal);
    }

    /**
     * Reconciles transaction ledger derived units against reported portfolio holdings.
     */
    @Transactional(readOnly = true)
    public PortfolioHoldingsReconciliationResponseDto reconcileHoldings(String auth0Subject) {
        Investor investor = portfolioService.resolveInvestor(auth0Subject);
        List<InvestorPortfolioHolding> holdings = portfolioHoldingRepository.findByInvestorId(investor.getId());

        int reconciled = 0;
        int partiallyReconciled = 0;
        int unreconciled = 0;
        int insufficientHistory = 0;
        int sourceConflict = 0;

        List<HoldingsReconciliationItemDto> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (InvestorPortfolioHolding h : holdings) {
            SchemeOption opt = h.getSchemeOption();
            BigDecimal reportedUnits = h.getUnits() != null ? h.getUnits() : BigDecimal.ZERO;

            List<InvestorTransaction> txs = transactionRepository.findByInvestorIdAndSchemeOptionIdAndIsReversedFalseOrderByTransactionDateAsc(
                investor.getId(),
                opt.getId()
            );

            if (txs.isEmpty()) {
                insufficientHistory++;
                items.add(new HoldingsReconciliationItemDto(
                    opt.getId(),
                    getSchemeName(opt),
                    opt.getPlan() != null ? getPlanType(opt) : "DIRECT",
                    opt.getOptionType(),
                    opt.getAmfiCode(),
                    opt.getIsin(),
                    reportedUnits,
                    BigDecimal.ZERO,
                    reportedUnits,
                    "INSUFFICIENT_HISTORY",
                    0,
                    null,
                    null,
                    "No transactions recorded in ledger. Position exists as reported holding only."
                ));
                warnings.add("Scheme " + getSchemeName(opt) + ": Zero ledger transactions recorded.");
                continue;
            }

            BigDecimal derivedUnits = BigDecimal.ZERO;
            LocalDate earliestDate = txs.get(0).getTransactionDate();
            LocalDate latestDate = txs.get(txs.size() - 1).getTransactionDate();

            for (InvestorTransaction tx : txs) {
                String type = tx.getTransactionType().toUpperCase(Locale.ROOT);
                BigDecimal u = tx.getUnits() != null ? tx.getUnits() : BigDecimal.ZERO;

                switch (type) {
                    case "PURCHASE", "SIP_PURCHASE", "STP_PURCHASE", "SWITCH_IN", "DIVIDEND_REINVESTMENT", "BONUS" ->
                        derivedUnits = derivedUnits.add(u);
                    case "REDEMPTION", "STP_REDEMPTION", "SWITCH_OUT" ->
                        derivedUnits = derivedUnits.subtract(u);
                    case "REVERSAL" ->
                        derivedUnits = derivedUnits.add(u); // Reversal units are already signed
                    default -> {}
                }
            }

            BigDecimal diff = reportedUnits.subtract(derivedUnits).setScale(4, RoundingMode.HALF_UP);
            String status;
            String note;

            if (diff.abs().compareTo(new BigDecimal("0.001")) <= 0) {
                status = "RECONCILED";
                note = "Ledger units match reported holdings exactly.";
                reconciled++;
            } else if (derivedUnits.compareTo(BigDecimal.ZERO) > 0 && derivedUnits.compareTo(reportedUnits) < 0) {
                status = "PARTIALLY_RECONCILED";
                note = "Derived units (" + derivedUnits + ") lower than reported (" + reportedUnits + "). Missing historical opening balance or purchases predating statement.";
                partiallyReconciled++;
                warnings.add("Scheme " + getSchemeName(opt) + ": Opening balance missing (" + diff + " units unexplained).");
            } else if (derivedUnits.compareTo(reportedUnits) > 0) {
                status = "SOURCE_CONFLICT";
                note = "Derived units (" + derivedUnits + ") exceed reported (" + reportedUnits + "). Unrecorded redemptions or duplicate purchases present.";
                sourceConflict++;
                warnings.add("Scheme " + getSchemeName(opt) + ": Ledger units exceed reported position by " + diff.abs() + " units.");
            } else {
                status = "UNRECONCILED";
                note = "Discrepancy detected between ledger (" + derivedUnits + ") and reported (" + reportedUnits + ").";
                unreconciled++;
            }

            items.add(new HoldingsReconciliationItemDto(
                opt.getId(),
                getSchemeName(opt),
                opt.getPlan() != null ? getPlanType(opt) : "DIRECT",
                opt.getOptionType(),
                opt.getAmfiCode(),
                opt.getIsin(),
                reportedUnits,
                derivedUnits,
                diff,
                status,
                txs.size(),
                earliestDate,
                latestDate,
                note
            ));
        }

        String overallStatus = unreconciled > 0 || sourceConflict > 0
            ? "UNRECONCILED"
            : (partiallyReconciled > 0 || insufficientHistory > 0 ? "PARTIAL_DISCREPANCIES" : "RECONCILED");

        return new PortfolioHoldingsReconciliationResponseDto(
            holdings.size(),
            reconciled,
            partiallyReconciled,
            unreconciled,
            insufficientHistory,
            sourceConflict,
            overallStatus,
            LocalDate.now(),
            ZonedDateTime.now(),
            items,
            warnings
        );
    }

    /**
     * Calculates comprehensive money-weighted returns (XIRR), cost basis decomposition,
     * and realized/unrealized performance for schemes and portfolio.
     */
    @Transactional(readOnly = true)
    public PortfolioInvestorPerformanceResponseDto calculateInvestorPerformance(
        String auth0Subject,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff
    ) {
        Investor investor = portfolioService.resolveInvestor(auth0Subject);
        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : LocalDate.now();
        OffsetDateTime effectiveCutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();

        List<InvestorPortfolioHolding> holdings = portfolioHoldingRepository.findByInvestorId(investor.getId());
        List<InvestorTransaction> allTransactions = transactionRepository.findByInvestorIdAndIsReversedFalseOrderByTransactionDateAsc(investor.getId());

        List<SchemeInvestorPerformanceDto> schemeList = new ArrayList<>();
        List<String> limitations = new ArrayList<>();

        BigDecimal portfolioInvestedCapital = BigDecimal.ZERO;
        BigDecimal portfolioCurrentValue = BigDecimal.ZERO;
        BigDecimal portfolioRedeemedCapital = BigDecimal.ZERO;
        BigDecimal portfolioDividendIncome = BigDecimal.ZERO;
        BigDecimal portfolioRealizedGain = BigDecimal.ZERO;
        BigDecimal portfolioRemainingCostBasis = BigDecimal.ZERO;
        BigDecimal portfolioUnrealizedGain = BigDecimal.ZERO;

        List<XirrCalculator.CashFlowItem> portfolioExternalCashFlows = new ArrayList<>();
        boolean anyEndingValuationMissing = false;

        for (InvestorPortfolioHolding holding : holdings) {
            SchemeOption opt = holding.getSchemeOption();
            BigDecimal reportedUnits = holding.getUnits() != null ? holding.getUnits() : BigDecimal.ZERO;

            // Retrieve latest authoritative NAV as of cutoff
            Optional<NavObservation> navOpt = navObservationRepository.findLatestAuthoritativeObservationAsOfCutoff(
                opt.getId(),
                effectiveAsOf,
                effectiveCutoff
            );

            BigDecimal latestNav = navOpt.map(NavObservation::getNavValue).orElse(null);
            LocalDate navDate = navOpt.map(NavObservation::getEffectiveDate).orElse(null);

            if (latestNav == null) {
                anyEndingValuationMissing = true;
                limitations.add("Scheme " + getSchemeName(opt) + ": NAV observation unavailable as of " + effectiveAsOf + ".");
            }

            BigDecimal currentSchemeMktValue = (latestNav != null && reportedUnits.compareTo(BigDecimal.ZERO) > 0)
                ? reportedUnits.multiply(latestNav).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            portfolioCurrentValue = portfolioCurrentValue.add(currentSchemeMktValue);

            // Fetch scheme transactions
            List<InvestorTransaction> schemeTxs = allTransactions.stream()
                .filter(t -> t.getSchemeOption() != null && t.getSchemeOption().getId().equals(opt.getId()))
                .toList();

            // Prepare lot inputs
            List<CostBasisLotCalculator.TransactionInput> lotInputs = schemeTxs.stream()
                .map(t -> new CostBasisLotCalculator.TransactionInput(
                    t.getId(),
                    t.getTransactionType(),
                    t.getTransactionDate(),
                    t.getUnits(),
                    t.getNav(),
                    t.getGrossAmount()
                )).toList();

            CostBasisLotCalculator.PerformanceDecompositionResult decomp = CostBasisLotCalculator.calculateFifo(
                lotInputs,
                latestNav
            );

            portfolioInvestedCapital = portfolioInvestedCapital.add(decomp.totalInvestedCapital());
            portfolioRedeemedCapital = portfolioRedeemedCapital.add(decomp.totalRedeemedCapital());
            portfolioDividendIncome = portfolioDividendIncome.add(decomp.totalDividendIncome());
            portfolioRealizedGain = portfolioRealizedGain.add(decomp.realizedGainOrLoss());
            portfolioRemainingCostBasis = portfolioRemainingCostBasis.add(decomp.remainingCostBasis());
            portfolioUnrealizedGain = portfolioUnrealizedGain.add(decomp.unrealizedGainOrLoss());

            // Build Scheme-level cash flows for XIRR
            List<XirrCalculator.CashFlowItem> schemeFlows = new ArrayList<>();
            for (InvestorTransaction tx : schemeTxs) {
                String type = tx.getTransactionType().toUpperCase(Locale.ROOT);
                BigDecimal amt = tx.getGrossAmount() != null ? tx.getGrossAmount().abs() : BigDecimal.ZERO;

                switch (type) {
                    case "PURCHASE", "SIP_PURCHASE", "STP_PURCHASE", "SWITCH_IN" -> {
                        if (amt.compareTo(BigDecimal.ZERO) > 0) {
                            schemeFlows.add(new XirrCalculator.CashFlowItem(tx.getTransactionDate(), amt.negate(), type));
                        }
                    }
                    case "REDEMPTION", "STP_REDEMPTION", "SWITCH_OUT" -> {
                        if (amt.compareTo(BigDecimal.ZERO) > 0) {
                            schemeFlows.add(new XirrCalculator.CashFlowItem(tx.getTransactionDate(), amt, type));
                        }
                    }
                    case "DIVIDEND_PAYOUT" -> {
                        if (amt.compareTo(BigDecimal.ZERO) > 0) {
                            schemeFlows.add(new XirrCalculator.CashFlowItem(tx.getTransactionDate(), amt, type));
                        }
                    }
                    default -> {}
                }
            }

            // Append terminal market value if positive
            if (currentSchemeMktValue.compareTo(BigDecimal.ZERO) > 0) {
                schemeFlows.add(new XirrCalculator.CashFlowItem(
                    navDate != null ? navDate : effectiveAsOf,
                    currentSchemeMktValue,
                    "TERMINAL_MARKET_VALUE"
                ));
            }

            XirrCalculator.XirrCalculationResult schemeXirrRes = XirrCalculator.calculate(schemeFlows);

            schemeList.add(new SchemeInvestorPerformanceDto(
                opt.getId(),
                getSchemeName(opt),
                opt.getPlan() != null ? getPlanType(opt) : "DIRECT",
                opt.getOptionType(),
                opt.getAmfiCode(),
                opt.getIsin(),
                reportedUnits,
                latestNav,
                navDate,
                currentSchemeMktValue,
                decomp.totalInvestedCapital(),
                decomp.totalRedeemedCapital(),
                decomp.totalDividendIncome(),
                decomp.remainingCostBasis(),
                decomp.realizedGainOrLoss(),
                decomp.unrealizedGainOrLoss(),
                decomp.totalEconomicGain(),
                schemeXirrRes.xirrAnnualized(),
                schemeXirrRes.status().name(),
                schemeXirrRes.statusMessage(),
                schemeFlows.size(),
                schemeXirrRes.firstCashFlowDate(),
                schemeXirrRes.lastCashFlowDate(),
                null, // fundNavCagr3Y from analytical profile where available
                "Investor XIRR measures actual cash flow timing; Fund CAGR assumes static buy-and-hold without cash flows.",
                decomp.accountingMethod()
            ));
        }

        // Build portfolio external cash flows
        // Strict boundary: switches and dividend reinvestments are NOT external cash flows!
        for (InvestorTransaction tx : allTransactions) {
            String type = tx.getTransactionType().toUpperCase(Locale.ROOT);
            BigDecimal amt = tx.getGrossAmount() != null ? tx.getGrossAmount().abs() : BigDecimal.ZERO;

            switch (type) {
                case "PURCHASE", "SIP_PURCHASE", "STP_PURCHASE" -> {
                    if (amt.compareTo(BigDecimal.ZERO) > 0) {
                        portfolioExternalCashFlows.add(new XirrCalculator.CashFlowItem(tx.getTransactionDate(), amt.negate(), "EXTERNAL_PURCHASE"));
                    }
                }
                case "REDEMPTION", "STP_REDEMPTION" -> {
                    if (amt.compareTo(BigDecimal.ZERO) > 0) {
                        portfolioExternalCashFlows.add(new XirrCalculator.CashFlowItem(tx.getTransactionDate(), amt, "EXTERNAL_REDEMPTION"));
                    }
                }
                case "DIVIDEND_PAYOUT" -> {
                    if (amt.compareTo(BigDecimal.ZERO) > 0) {
                        portfolioExternalCashFlows.add(new XirrCalculator.CashFlowItem(tx.getTransactionDate(), amt, "DIVIDEND_PAYOUT"));
                    }
                }
                default -> {
                    // SWITCH_IN, SWITCH_OUT, DIVIDEND_REINVESTMENT, BONUS do not inject external portfolio capital
                }
            }
        }

        // Add portfolio terminal market value
        if (portfolioCurrentValue.compareTo(BigDecimal.ZERO) > 0) {
            portfolioExternalCashFlows.add(new XirrCalculator.CashFlowItem(
                effectiveAsOf,
                portfolioCurrentValue,
                "PORTFOLIO_TERMINAL_MARKET_VALUE"
            ));
        }

        XirrCalculator.XirrCalculationResult portfolioXirrRes;
        if (anyEndingValuationMissing && portfolioCurrentValue.compareTo(BigDecimal.ZERO) <= 0) {
            portfolioXirrRes = XirrCalculator.XirrCalculationResult.failure(
                XirrCalculator.XirrStatus.INCOMPLETE_PORTFOLIO_VALUATION,
                "Portfolio valuation incomplete due to missing NAV observations. Terminal valuation cannot be established.",
                portfolioExternalCashFlows
            );
        } else {
            portfolioXirrRes = XirrCalculator.calculate(portfolioExternalCashFlows);
        }

        BigDecimal totalEconGain = portfolioRealizedGain.add(portfolioUnrealizedGain).add(portfolioDividendIncome);

        return new PortfolioInvestorPerformanceResponseDto(
            portfolioInvestedCapital,
            portfolioCurrentValue,
            portfolioRedeemedCapital,
            portfolioDividendIncome,
            portfolioRealizedGain,
            portfolioRemainingCostBasis,
            portfolioUnrealizedGain,
            totalEconGain,
            portfolioXirrRes.xirrAnnualized(),
            portfolioXirrRes.status().name(),
            portfolioXirrRes.statusMessage(),
            portfolioExternalCashFlows.size(),
            portfolioXirrRes.firstCashFlowDate(),
            portfolioXirrRes.lastCashFlowDate(),
            "UNAVAILABLE",
            "Sub-period portfolio valuations immediately preceding and following external cash flows are not recorded in historical CAS statements. True TWR requires daily portfolio unitization or intermediate NAV snapshots at each external cash flow date.",
            "FIFO_TRANSACTION_LOT",
            "ACT/365",
            effectiveAsOf,
            ZonedDateTime.now(),
            schemeList,
            limitations
        );
    }

    /**
     * Reviews CAS import transactions for a specific import batch.
     */
    @Transactional(readOnly = true)
    public CasImportTransactionReviewDto reviewImportTransactions(String auth0Subject, Long importId) {
        Investor investor = portfolioService.resolveInvestor(auth0Subject);
        PortfolioImport imp = importRepository.findById(importId)
            .orElseThrow(() -> new IllegalArgumentException("Import batch not found: " + importId));

        if (!imp.getInvestor().getId().equals(investor.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized access to import batch.");
        }

        List<PortfolioImportTransaction> txList = importTransactionRepository.findByPortfolioImportIdOrderByTransactionDateDesc(importId);

        int resolved = 0;
        int unresolved = 0;
        int duplicates = 0;
        List<CasImportTransactionReviewDto.CasImportTransactionItemDto> items = new ArrayList<>();

        for (PortfolioImportTransaction pit : txList) {
            if ("RESOLVED".equals(pit.getResolutionStatus())) {
                resolved++;
            } else if ("DUPLICATE".equals(pit.getResolutionStatus())) {
                duplicates++;
            } else {
                unresolved++;
            }

            items.add(new CasImportTransactionReviewDto.CasImportTransactionItemDto(
                pit.getId(),
                pit.getRawSchemeName(),
                pit.getFolioNumber(),
                pit.getIsin(),
                pit.getAmfiCode(),
                pit.getTransactionDate(),
                pit.getRawTypeDescription(),
                pit.getNormalizedType(),
                pit.getUnits(),
                pit.getNav(),
                pit.getAmount(),
                pit.getResolvedSchemeOption() != null ? pit.getResolvedSchemeOption().getId() : null,
                pit.getResolvedSchemeOption() != null ? getSchemeName(pit.getResolvedSchemeOption()) : null,
                pit.getResolutionStatus(),
                pit.getResolutionNotes()
            ));
        }

        String importStatus = unresolved > 0 ? "REQUIRES_REVIEW" : (txList.isEmpty() ? "IMPORTED" : "IMPORTED");

        return new CasImportTransactionReviewDto(
            imp.getId(),
            imp.getSourceFilename(),
            imp.getStatementDate() != null ? imp.getStatementDate().toString() : "Unknown",
            txList.size(),
            resolved,
            unresolved,
            duplicates,
            importStatus,
            items,
            List.of("CAS transaction extraction complete. Review unmapped records before confirming.")
        );
    }

    private InvestorTransactionDto toDto(InvestorTransaction t) {
        SchemeOption opt = t.getSchemeOption();
        return new InvestorTransactionDto(
            t.getId(),
            opt != null ? opt.getId() : null,
            opt != null ? getSchemeName(opt) : "Unknown / Unresolved",
            opt != null && opt.getPlan() != null ? getPlanType(opt) : "DIRECT",
            opt != null ? opt.getOptionType() : "GROWTH",
            opt != null ? opt.getAmfiCode() : null,
            opt != null ? opt.getIsin() : null,
            t.getTransactionType(),
            t.getTransactionDate(),
            t.getSettlementDate(),
            t.getUnits(),
            t.getNav(),
            t.getGrossAmount(),
            t.getNetAmount(),
            t.getChargesOrTaxes(),
            t.getDividendAmount(),
            t.getSourceAttribution(),
            t.getSourceDocumentRef(),
            t.getOriginalDescription(),
            t.getImportBatchId(),
            t.getReconciliationStatus(),
            t.getReversalReference() != null ? t.getReversalReference().getId() : null,
            t.getIsReversed(),
            t.getDedupHash(),
            t.getCreatedAt()
        );
    }

    private String normalizeTransactionType(String raw) {
        if (raw == null) return "OTHER";
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.contains("SIP") || s.contains("SYSTEMATIC")) return "SIP_PURCHASE";
        if (s.contains("STP IN") || s.contains("STP PURCHASE")) return "STP_PURCHASE";
        if (s.contains("STP OUT") || s.contains("STP REDEMPTION")) return "STP_REDEMPTION";
        if (s.contains("SWITCH IN") || s.contains("SWITCH-IN")) return "SWITCH_IN";
        if (s.contains("SWITCH OUT") || s.contains("SWITCH-OUT")) return "SWITCH_OUT";
        if (s.contains("PURCHASE") || s.contains("SUBSCRIPTION") || s.contains("NEW FUND")) return "PURCHASE";
        if (s.contains("REDEMPTION") || s.contains("REPURCHASE") || s.contains("WITHDRAWAL")) return "REDEMPTION";
        if (s.contains("DIVIDEND REINVEST") || s.contains("IDCW REINVEST")) return "DIVIDEND_REINVESTMENT";
        if (s.contains("DIVIDEND") || s.contains("IDCW")) return "DIVIDEND_PAYOUT";
        if (s.contains("BONUS")) return "BONUS";
        if (s.contains("REVERSAL")) return "REVERSAL";
        return "OTHER";
    }

    private String computeDedupHash(Long investorId, Long schemeOptionId, LocalDate date, String type, BigDecimal units, BigDecimal netAmt) {
        String raw = investorId + ":" + schemeOptionId + ":" + date + ":" + type + ":" + units + ":" + netAmt;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString();
        }
    }

    private String getSchemeName(SchemeOption opt) {
        if (opt == null || opt.getPlan() == null || opt.getPlan().getScheme() == null) {
            return "Unknown / Unresolved";
        }
        return opt.getPlan().getScheme().getName();
    }

    private String getPlanType(SchemeOption opt) {
        if (opt == null || opt.getPlan() == null) {
            return "DIRECT";
        }
        return opt.getPlan().getPlanType();
    }


    /**
     * Calculates complete portfolio- and scheme-level performance attribution and contribution decomposition.
     */
    @Transactional(readOnly = true)
    public PortfolioPerformanceAttributionResponseDto calculatePerformanceAttribution(
        String auth0Subject,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoff
    ) {
        Investor investor = portfolioService.resolveInvestor(auth0Subject);
        LocalDate effectiveAsOf = asOfDate != null ? asOfDate : LocalDate.now();
        OffsetDateTime effectiveCutoff = knowledgeCutoff != null ? knowledgeCutoff : OffsetDateTime.now();

        List<InvestorPortfolioHolding> holdings = portfolioHoldingRepository.findByInvestorId(investor.getId());
        List<InvestorTransaction> allTransactions = transactionRepository.findByInvestorIdAndIsReversedFalseOrderByTransactionDateAsc(investor.getId());

        // Collect all distinct scheme options (both currently held and previously held/redeemed)
        Set<Long> schemeOptionIds = new LinkedHashSet<>();
        Map<Long, InvestorPortfolioHolding> holdingByScheme = new HashMap<>();
        for (InvestorPortfolioHolding h : holdings) {
            if (h.getSchemeOption() != null) {
                schemeOptionIds.add(h.getSchemeOption().getId());
                holdingByScheme.put(h.getSchemeOption().getId(), h);
            }
        }
        for (InvestorTransaction t : allTransactions) {
            if (t.getSchemeOption() != null) {
                schemeOptionIds.add(t.getSchemeOption().getId());
            }
        }

        BigDecimal portfolioCurrentValue = BigDecimal.ZERO;
        BigDecimal portfolioInvestedCapital = BigDecimal.ZERO;
        BigDecimal portfolioRedeemedCapital = BigDecimal.ZERO;
        BigDecimal portfolioDividendIncome = BigDecimal.ZERO;
        BigDecimal portfolioDividendReinvested = BigDecimal.ZERO;
        BigDecimal portfolioRealizedGain = BigDecimal.ZERO;
        BigDecimal portfolioRealizedLoss = BigDecimal.ZERO;
        BigDecimal portfolioRemainingCostBasis = BigDecimal.ZERO;
        BigDecimal portfolioUnrealizedGain = BigDecimal.ZERO;
        BigDecimal portfolioCharges = BigDecimal.ZERO;

        List<SchemePerformanceContributionDto> rawSchemeContribs = new ArrayList<>();
        List<HoldingLotPerformanceDto> allHoldingLots = new ArrayList<>();
        List<RealizedGainItemDto> allRealizedRecords = new ArrayList<>();
        List<SchemeUnrealizedItemDto> unrealizedItems = new ArrayList<>();
        List<SchemeDividendItemDto> dividendItems = new ArrayList<>();
        List<XirrCalculator.CashFlowItem> portfolioExternalCashFlows = new ArrayList<>();

        int valuedSchemesCount = 0;
        int unvaluedSchemesCount = 0;
        int staleValuationSchemesCount = 0;
        List<String> residualCauses = new ArrayList<>();

        for (Long sId : schemeOptionIds) {
            SchemeOption opt = schemeOptionRepository.findById(sId).orElse(null);
            if (opt == null) continue;

            InvestorPortfolioHolding h = holdingByScheme.get(sId);
            BigDecimal reportedUnits = (h != null && h.getUnits() != null) ? h.getUnits() : BigDecimal.ZERO;

            Optional<NavObservation> navOpt = navObservationRepository.findLatestAuthoritativeObservationAsOfCutoff(
                opt.getId(),
                effectiveAsOf,
                effectiveCutoff
            );

            BigDecimal latestNav = navOpt.map(NavObservation::getNavValue).orElse(null);
            LocalDate navDate = navOpt.map(NavObservation::getEffectiveDate).orElse(null);
            boolean isStale = (navDate != null && ChronoUnit.DAYS.between(navDate, effectiveAsOf) > 5);

            if (latestNav != null) {
                valuedSchemesCount++;
                if (isStale) staleValuationSchemesCount++;
            } else if (reportedUnits.compareTo(BigDecimal.ZERO) > 0) {
                unvaluedSchemesCount++;
            }

            BigDecimal currentSchemeMktValue = (latestNav != null && reportedUnits.compareTo(BigDecimal.ZERO) > 0)
                ? reportedUnits.multiply(latestNav).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            portfolioCurrentValue = portfolioCurrentValue.add(currentSchemeMktValue);

            List<InvestorTransaction> schemeTxs = allTransactions.stream()
                .filter(t -> t.getSchemeOption() != null && t.getSchemeOption().getId().equals(opt.getId()))
                .toList();

            List<CostBasisLotCalculator.TransactionInput> lotInputs = schemeTxs.stream()
                .map(t -> new CostBasisLotCalculator.TransactionInput(
                    t.getId(),
                    t.getTransactionType(),
                    t.getTransactionDate(),
                    t.getUnits(),
                    t.getNav(),
                    t.getGrossAmount(),
                    t.getChargesOrTaxes() != null ? t.getChargesOrTaxes() : BigDecimal.ZERO,
                    opt.getId(),
                    getSchemeName(opt)
                )).toList();

            CostBasisLotCalculator.PerformanceDecompositionResult decomp = CostBasisLotCalculator.calculateFifo(
                lotInputs,
                latestNav,
                effectiveAsOf
            );

            portfolioInvestedCapital = portfolioInvestedCapital.add(decomp.totalInvestedCapital());
            portfolioRedeemedCapital = portfolioRedeemedCapital.add(decomp.totalRedeemedCapital());
            portfolioDividendIncome = portfolioDividendIncome.add(decomp.totalDividendIncome());
            portfolioDividendReinvested = portfolioDividendReinvested.add(decomp.totalDividendReinvested());
            if (decomp.realizedGainOrLoss().compareTo(BigDecimal.ZERO) >= 0) {
                portfolioRealizedGain = portfolioRealizedGain.add(decomp.realizedGainOrLoss());
            } else {
                portfolioRealizedLoss = portfolioRealizedLoss.add(decomp.realizedGainOrLoss().abs());
            }
            portfolioRemainingCostBasis = portfolioRemainingCostBasis.add(decomp.remainingCostBasis());
            portfolioUnrealizedGain = portfolioUnrealizedGain.add(decomp.unrealizedGainOrLoss());
            portfolioCharges = portfolioCharges.add(decomp.totalChargesRecorded());

            // Collect all lots
            for (CostBasisLotCalculator.PurchaseLot lot : decomp.allLots()) {
                allHoldingLots.add(new HoldingLotPerformanceDto(
                    lot.transactionId(),
                    opt.getId(),
                    getSchemeName(opt),
                    lot.transactionId(),
                    lot.purchaseDate(),
                    lot.initialUnits(),
                    lot.purchaseNav(),
                    lot.initialCostBasis(),
                    lot.remainingUnits(),
                    lot.remainingCostBasis(),
                    lot.currentNav(),
                    lot.currentMarketValue(),
                    lot.unrealizedGain(),
                    lot.initialCostBasis().subtract(lot.remainingCostBasis()),
                    lot.holdingPeriodDays(),
                    lot.holdingPeriodCategory(),
                    lot.isFullyConsumed(),
                    lot.isFullyConsumed() ? "FULLY_REDEEMED" : (lot.remainingUnits().compareTo(lot.initialUnits()) < 0 ? "PARTIALLY_REDEEMED" : "ACTIVE")
                ));
            }

            // Collect realized records
            for (CostBasisLotCalculator.RealizedGainRecord rg : decomp.realizedGainsHistory()) {
                allRealizedRecords.add(new RealizedGainItemDto(
                    rg.redemptionTransactionId(),
                    opt.getId(),
                    getSchemeName(opt),
                    rg.redemptionDate(),
                    rg.unitsRedeemed(),
                    rg.redemptionPrice(),
                    rg.redemptionGrossAmount(),
                    rg.costBasisRedeemed(),
                    rg.netRealizedGain(),
                    rg.matchedPurchaseTransactionId(),
                    rg.matchedPurchaseDate(),
                    rg.matchedPurchasePrice(),
                    rg.holdingPeriodDays(),
                    rg.holdingPeriodCategory(),
                    rg.redemptionType(),
                    rg.isInternalTransfer(),
                    rg.chargesDeducted()
                ));
            }

            // Unrealized item
            BigDecimal unrlzPct = (decomp.remainingCostBasis().compareTo(BigDecimal.ZERO) > 0)
                ? decomp.unrealizedGainOrLoss().divide(decomp.remainingCostBasis(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

            boolean isRecon = (reportedUnits.compareTo(decomp.remainingUnits()) == 0);
            if (!isRecon && reportedUnits.compareTo(BigDecimal.ZERO) > 0) {
                residualCauses.add("UNRECONCILED_UNITS (" + getSchemeName(opt) + "): reported " + reportedUnits + " vs ledger " + decomp.remainingUnits());
            }

            unrealizedItems.add(new SchemeUnrealizedItemDto(
                opt.getId(),
                getSchemeName(opt),
                reportedUnits,
                latestNav,
                navDate,
                isStale,
                currentSchemeMktValue,
                decomp.remainingCostBasis(),
                decomp.unrealizedGainOrLoss(),
                unrlzPct,
                isRecon ? "RECONCILED" : "PARTIAL",
                latestNav != null ? (isStale ? "STALE_VALUATION" : "VERIFIED_POINT_IN_TIME") : "MISSING_NAV"
            ));

            // Dividend item
            dividendItems.add(new SchemeDividendItemDto(
                opt.getId(),
                getSchemeName(opt),
                decomp.totalDividendIncome(),
                decomp.totalDividendReinvested(),
                (int) schemeTxs.stream().filter(t -> t.getTransactionType().toUpperCase(Locale.ROOT).contains("DIVIDEND") || t.getTransactionType().toUpperCase(Locale.ROOT).contains("IDCW")).count(),
                schemeTxs.stream().filter(t -> t.getTransactionType().toUpperCase(Locale.ROOT).contains("DIVIDEND")).map(InvestorTransaction::getTransactionDate).max(LocalDate::compareTo).orElse(null),
                schemeTxs.isEmpty() ? "NO_HISTORY" : "CAS_AND_MANUAL_LEDGER"
            ));

            // Scheme cash flows for XIRR
            List<XirrCalculator.CashFlowItem> schemeFlows = new ArrayList<>();
            for (InvestorTransaction tx : schemeTxs) {
                String type = tx.getTransactionType().toUpperCase(Locale.ROOT);
                BigDecimal amt = tx.getGrossAmount() != null ? tx.getGrossAmount().abs() : BigDecimal.ZERO;
                switch (type) {
                    case "PURCHASE", "SIP_PURCHASE", "STP_PURCHASE", "SWITCH_IN" -> {
                        if (amt.compareTo(BigDecimal.ZERO) > 0) schemeFlows.add(new XirrCalculator.CashFlowItem(tx.getTransactionDate(), amt.negate(), type));
                    }
                    case "REDEMPTION", "STP_REDEMPTION", "SWITCH_OUT", "DIVIDEND_PAYOUT" -> {
                        if (amt.compareTo(BigDecimal.ZERO) > 0) schemeFlows.add(new XirrCalculator.CashFlowItem(tx.getTransactionDate(), amt, type));
                    }
                    default -> {}
                }
            }
            if (currentSchemeMktValue.compareTo(BigDecimal.ZERO) > 0) {
                schemeFlows.add(new XirrCalculator.CashFlowItem(navDate != null ? navDate : effectiveAsOf, currentSchemeMktValue, "TERMINAL_VALUATION"));
            }
            XirrCalculator.XirrCalculationResult sXirr = XirrCalculator.calculate(schemeFlows);

            BigDecimal netSchemeContrib = decomp.realizedGainOrLoss()
                .add(decomp.unrealizedGainOrLoss())
                .add(decomp.totalDividendIncome())
                .subtract(decomp.totalChargesRecorded());

            LocalDate covStart = schemeTxs.stream().map(InvestorTransaction::getTransactionDate).min(LocalDate::compareTo).orElse(null);
            LocalDate covEnd = schemeTxs.stream().map(InvestorTransaction::getTransactionDate).max(LocalDate::compareTo).orElse(null);

            rawSchemeContribs.add(new SchemePerformanceContributionDto(
                opt.getId(),
                getSchemeName(opt),
                getPlanType(opt),
                opt.getOptionType(),
                opt.getAmfiCode(),
                opt.getIsin(),
                reportedUnits,
                latestNav,
                navDate,
                isStale,
                currentSchemeMktValue,
                decomp.totalInvestedCapital(),
                decomp.totalRedeemedCapital(),
                decomp.remainingCostBasis(),
                decomp.realizedGainOrLoss(),
                decomp.unrealizedGainOrLoss(),
                decomp.totalDividendIncome(),
                decomp.totalDividendReinvested(),
                decomp.totalChargesRecorded(),
                netSchemeContrib,
                BigDecimal.ZERO, // will set below
                BigDecimal.ZERO,
                "",
                0,
                schemeTxs.isEmpty() ? "UNRECONCILED" : (covStart != null && ChronoUnit.DAYS.between(covStart, effectiveAsOf) < 90 ? "PARTIAL_HISTORY" : "COMPLETE"),
                covStart,
                covEnd,
                schemeTxs.size(),
                isRecon ? "RECONCILED" : "PARTIAL",
                sXirr.xirrAnnualized(),
                sXirr.status().name(),
                null,
                "3Y_ANNUALIZED_CAGR",
                covStart,
                effectiveAsOf,
                null,
                "Investor XIRR measures actual cash flow timing; Fund CAGR assumes static buy-and-hold without cash flows. This return difference is not a measure of portfolio alpha or timing skill."
            ));
        }

        // 2. Compute Scheme Contribution Ranking and Percentages
        BigDecimal totalPositiveGains = rawSchemeContribs.stream()
            .map(SchemePerformanceContributionDto::netFinancialContribution)
            .filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        rawSchemeContribs.sort((a, b) -> b.netFinancialContribution().compareTo(a.netFinancialContribution()));

        List<SchemePerformanceContributionDto> rankedContributions = new ArrayList<>();
        int rank = 1;
        for (SchemePerformanceContributionDto sc : rawSchemeContribs) {
            BigDecimal pct = (totalPositiveGains.compareTo(BigDecimal.ZERO) > 0 && sc.netFinancialContribution().compareTo(BigDecimal.ZERO) > 0)
                ? sc.netFinancialContribution().divide(totalPositiveGains, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

            rankedContributions.add(new SchemePerformanceContributionDto(
                sc.schemeOptionId(),
                sc.schemeName(),
                sc.planType(),
                sc.optionType(),
                sc.amfiCode(),
                sc.isin(),
                sc.currentUnits(),
                sc.currentNav(),
                sc.navEffectiveDate(),
                sc.isNavStale(),
                sc.currentMarketValue(),
                sc.investedCapital(),
                sc.redeemedCapital(),
                sc.remainingCostBasis(),
                sc.realizedGains(),
                sc.unrealizedGains(),
                sc.dividendIncome(),
                sc.reinvestedDividends(),
                sc.recordedCharges(),
                sc.netFinancialContribution(),
                pct,
                totalPositiveGains,
                "Sum of all positive scheme financial contributions (Rs. " + totalPositiveGains.setScale(2, RoundingMode.HALF_UP) + ")",
                sc.netFinancialContribution().compareTo(BigDecimal.ZERO) >= 0 ? rank++ : -1,
                sc.contributionStatus(),
                sc.transactionCoverageStartDate(),
                sc.transactionCoverageEndDate(),
                sc.transactionCount(),
                sc.holdingsReconciliationStatus(),
                sc.schemeXirr(),
                sc.schemeXirrStatus(),
                sc.fundNavCagr(),
                sc.fundNavCagrPeriod(),
                sc.cagrStartDate(),
                sc.cagrEndDate(),
                sc.returnDifference(),
                sc.returnDifferenceContext()
            ));
        }

        // 3. Build Portfolio-Level Financial Outcome & Reconciliation
        BigDecimal openingValue = BigDecimal.ZERO;
        BigDecimal externalContributions = BigDecimal.ZERO;
        BigDecimal externalWithdrawals = BigDecimal.ZERO;

        for (InvestorTransaction tx : allTransactions) {
            String type = tx.getTransactionType().toUpperCase(Locale.ROOT);
            BigDecimal amt = tx.getGrossAmount() != null ? tx.getGrossAmount().abs() : BigDecimal.ZERO;

            switch (type) {
                case "PURCHASE", "SIP_PURCHASE", "STP_PURCHASE" -> {
                    if (amt.compareTo(BigDecimal.ZERO) > 0) {
                        externalContributions = externalContributions.add(amt);
                        portfolioExternalCashFlows.add(new XirrCalculator.CashFlowItem(tx.getTransactionDate(), amt.negate(), "EXTERNAL_CONTRIBUTION"));
                    }
                }
                case "REDEMPTION" -> {
                    if (amt.compareTo(BigDecimal.ZERO) > 0) {
                        externalWithdrawals = externalWithdrawals.add(amt);
                        portfolioExternalCashFlows.add(new XirrCalculator.CashFlowItem(tx.getTransactionDate(), amt, "EXTERNAL_WITHDRAWAL"));
                    }
                }
                case "DIVIDEND_PAYOUT" -> {
                    if (amt.compareTo(BigDecimal.ZERO) > 0) {
                        portfolioExternalCashFlows.add(new XirrCalculator.CashFlowItem(tx.getTransactionDate(), amt, "DIVIDEND_PAYOUT"));
                    }
                }
                default -> {}
            }
        }

        BigDecimal netGains = portfolioRealizedGain.subtract(portfolioRealizedLoss)
            .add(portfolioUnrealizedGain)
            .add(portfolioDividendIncome)
            .subtract(portfolioCharges);

        BigDecimal expectedClosing = openingValue
            .add(externalContributions)
            .subtract(externalWithdrawals)
            .add(netGains);

        BigDecimal unexplainedResidual = portfolioCurrentValue.subtract(expectedClosing).setScale(2, RoundingMode.HALF_UP);

        if (unvaluedSchemesCount > 0) {
            residualCauses.add("UNVALUED_HOLDINGS: Current NAV observation missing for " + unvaluedSchemesCount + " position(s).");
        }
        if (portfolioCharges.compareTo(BigDecimal.ZERO) == 0 && !allTransactions.isEmpty()) {
            residualCauses.add("ABSENT_TRANSACTION_CHARGES: External source feed did not disclose discrete STT / stamp duty line items.");
        }

        String reconStatus = (unexplainedResidual.abs().compareTo(BigDecimal.valueOf(1.00)) <= 0)
            ? "RECONCILED"
            : (!residualCauses.isEmpty() ? "EXPLAINED_DISCREPANCY" : "UNRECONCILED_RESIDUAL");

        PortfolioFinancialDecompositionDto decompDto = new PortfolioFinancialDecompositionDto(
            openingValue,
            externalContributions.setScale(2, RoundingMode.HALF_UP),
            externalWithdrawals.setScale(2, RoundingMode.HALF_UP),
            portfolioRealizedGain.subtract(portfolioRealizedLoss).setScale(2, RoundingMode.HALF_UP),
            portfolioUnrealizedGain.setScale(2, RoundingMode.HALF_UP),
            portfolioDividendIncome.setScale(2, RoundingMode.HALF_UP),
            portfolioDividendReinvested.setScale(2, RoundingMode.HALF_UP),
            portfolioCharges.setScale(2, RoundingMode.HALF_UP),
            BigDecimal.ZERO,
            portfolioCurrentValue.setScale(2, RoundingMode.HALF_UP),
            expectedClosing.setScale(2, RoundingMode.HALF_UP),
            unexplainedResidual,
            reconStatus,
            residualCauses,
            "V_expected = V_start + C_in - C_out + G_realized + G_unrealized + I_dividend - E_charges",
            "Calculated strictly from authoritative ledger transactions and point-in-time NAV observations."
        );

        // 4. Consolidated Realized Performance
        BigDecimal extRealized = allRealizedRecords.stream()
            .filter(r -> !r.isInternalTransfer())
            .map(RealizedGainItemDto::netRealizedGain)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal intRealized = allRealizedRecords.stream()
            .filter(RealizedGainItemDto::isInternalTransfer)
            .map(RealizedGainItemDto::netRealizedGain)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRedempGross = allRealizedRecords.stream()
            .map(RealizedGainItemDto::redemptionGrossProceeds)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCostBasisRedeemed = allRealizedRecords.stream()
            .map(RealizedGainItemDto::costBasisRedeemed)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalChargesDeducted = allRealizedRecords.stream()
            .map(RealizedGainItemDto::chargesDeducted)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        ConsolidatedRealizedPerformanceDto realizedDto = new ConsolidatedRealizedPerformanceDto(
            portfolioRealizedGain.setScale(2, RoundingMode.HALF_UP),
            portfolioRealizedLoss.setScale(2, RoundingMode.HALF_UP),
            portfolioRealizedGain.subtract(portfolioRealizedLoss).setScale(2, RoundingMode.HALF_UP),
            totalRedempGross.setScale(2, RoundingMode.HALF_UP),
            totalCostBasisRedeemed.setScale(2, RoundingMode.HALF_UP),
            totalChargesDeducted.setScale(2, RoundingMode.HALF_UP),
            extRealized.setScale(2, RoundingMode.HALF_UP),
            intRealized.setScale(2, RoundingMode.HALF_UP),
            allRealizedRecords.size(),
            allRealizedRecords,
            "FIFO accounting records represent mathematical cost-basis matching. They do NOT constitute tax computation, tax liability determination, or tax advice under Indian Income Tax Act."
        );

        // 5. Consolidated Unrealized Performance
        BigDecimal unrlzPctTotal = (portfolioRemainingCostBasis.compareTo(BigDecimal.ZERO) > 0)
            ? portfolioUnrealizedGain.divide(portfolioRemainingCostBasis, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        ConsolidatedUnrealizedPerformanceDto unrealizedDto = new ConsolidatedUnrealizedPerformanceDto(
            portfolioCurrentValue.setScale(2, RoundingMode.HALF_UP),
            portfolioRemainingCostBasis.setScale(2, RoundingMode.HALF_UP),
            portfolioUnrealizedGain.setScale(2, RoundingMode.HALF_UP),
            unrlzPctTotal.setScale(2, RoundingMode.HALF_UP),
            (int) allHoldingLots.stream().filter(l -> !l.isFullyConsumed()).count(),
            valuedSchemesCount,
            unvaluedSchemesCount,
            staleValuationSchemesCount,
            unrealizedItems,
            unvaluedSchemesCount > 0 ? "Warning: " + unvaluedSchemesCount + " position(s) have unverified or missing NAV observations." : "All positions valued with verified point-in-time NAV observations."
        );

        // 6. Dividend Income Contribution
        int cashEvents = (int) allTransactions.stream().filter(t -> t.getTransactionType().equalsIgnoreCase("DIVIDEND_PAYOUT")).count();
        int reinvestEvents = (int) allTransactions.stream().filter(t -> t.getTransactionType().equalsIgnoreCase("DIVIDEND_REINVESTMENT")).count();

        DividendIncomeContributionDto dividendDto = new DividendIncomeContributionDto(
            portfolioDividendIncome.setScale(2, RoundingMode.HALF_UP),
            portfolioDividendReinvested.setScale(2, RoundingMode.HALF_UP),
            cashEvents + reinvestEvents,
            cashEvents,
            reinvestEvents,
            dividendItems,
            "Dividend payouts represent external cash receipts. Dividend reinvestments represent internal unit creations and are not counted as external cash inflows or duplicate cash income."
        );

        // 7. Cash Flow Timing Context
        Map<String, List<InvestorTransaction>> byMonth = allTransactions.stream()
            .collect(Collectors.groupingBy(t -> t.getTransactionDate().toString().substring(0, 7), TreeMap::new, Collectors.toList()));

        List<MonthlyCashFlowAggregateDto> monthlyAggs = new ArrayList<>();
        BigDecimal totalLumpSum = BigDecimal.ZERO;
        BigDecimal totalSip = BigDecimal.ZERO;

        for (Map.Entry<String, List<InvestorTransaction>> entry : byMonth.entrySet()) {
            BigDecimal mLump = BigDecimal.ZERO;
            BigDecimal mSip = BigDecimal.ZERO;
            BigDecimal mRed = BigDecimal.ZERO;
            BigDecimal mDiv = BigDecimal.ZERO;
            BigDecimal mSw = BigDecimal.ZERO;

            for (InvestorTransaction t : entry.getValue()) {
                String type = t.getTransactionType().toUpperCase(Locale.ROOT);
                BigDecimal amt = t.getGrossAmount() != null ? t.getGrossAmount().abs() : BigDecimal.ZERO;
                switch (type) {
                    case "PURCHASE", "STP_PURCHASE" -> mLump = mLump.add(amt);
                    case "SIP_PURCHASE" -> mSip = mSip.add(amt);
                    case "REDEMPTION" -> mRed = mRed.add(amt);
                    case "DIVIDEND_PAYOUT" -> mDiv = mDiv.add(amt);
                    case "SWITCH_IN", "SWITCH_OUT", "STP_REDEMPTION", "DIVIDEND_REINVESTMENT" -> mSw = mSw.add(amt);
                    default -> {}
                }
            }

            totalLumpSum = totalLumpSum.add(mLump);
            totalSip = totalSip.add(mSip);
            BigDecimal netMonthly = (mLump.add(mSip)).subtract(mRed).subtract(mDiv);

            monthlyAggs.add(new MonthlyCashFlowAggregateDto(
                entry.getKey(),
                mLump.setScale(2, RoundingMode.HALF_UP),
                mSip.setScale(2, RoundingMode.HALF_UP),
                mLump.add(mSip).setScale(2, RoundingMode.HALF_UP),
                mRed.setScale(2, RoundingMode.HALF_UP),
                mDiv.setScale(2, RoundingMode.HALF_UP),
                mSw.setScale(2, RoundingMode.HALF_UP),
                netMonthly.setScale(2, RoundingMode.HALF_UP)
            ));
        }

        LocalDate covStart = allTransactions.stream().map(InvestorTransaction::getTransactionDate).min(LocalDate::compareTo).orElse(null);
        LocalDate covEnd = allTransactions.stream().map(InvestorTransaction::getTransactionDate).max(LocalDate::compareTo).orElse(null);

        List<String> timingObs = new ArrayList<>();
        if (totalSip.compareTo(BigDecimal.ZERO) > 0) {
            timingObs.add("Systematic Investment Plan (SIP) contributions total Rs. " + totalSip.setScale(2, RoundingMode.HALF_UP) + " across recurring installments.");
        }
        if (totalLumpSum.compareTo(BigDecimal.ZERO) > 0) {
            timingObs.add("Discretionary lump-sum additions total Rs. " + totalLumpSum.setScale(2, RoundingMode.HALF_UP) + ".");
        }
        if (externalWithdrawals.compareTo(BigDecimal.ZERO) > 0) {
            timingObs.add("External capital withdrawals total Rs. " + externalWithdrawals.setScale(2, RoundingMode.HALF_UP) + " across historical redemptions.");
        }
        timingObs.add("Return differences between personal XIRR and fund CAGR reflect the chronological sequence and sizing of capital deployed; they do not represent predictive market-timing conclusions.");

        CashFlowTimingContextDto timingDto = new CashFlowTimingContextDto(
            covStart,
            covEnd,
            (int) allTransactions.stream().filter(t -> t.getTransactionType().toUpperCase(Locale.ROOT).contains("PURCHASE")).count(),
            totalLumpSum.setScale(2, RoundingMode.HALF_UP),
            totalSip.setScale(2, RoundingMode.HALF_UP),
            (int) allTransactions.stream().filter(t -> t.getTransactionType().equalsIgnoreCase("REDEMPTION")).count(),
            (int) allTransactions.stream().filter(t -> t.getTransactionType().toUpperCase(Locale.ROOT).contains("SWITCH") || t.getTransactionType().toUpperCase(Locale.ROOT).contains("STP")).count(),
            monthlyAggs,
            timingObs
        );

        // 8. XIRR Diagnostics & Ambiguity
        if (portfolioCurrentValue.compareTo(BigDecimal.ZERO) > 0) {
            portfolioExternalCashFlows.add(new XirrCalculator.CashFlowItem(
                effectiveAsOf,
                portfolioCurrentValue,
                "PORTFOLIO_TERMINAL_MARKET_VALUE"
            ));
        }

        XirrCalculator.XirrCalculationResult pXirrRes = XirrCalculator.calculate(portfolioExternalCashFlows);

        XirrAmbiguityAndDiagnosticsDto xirrDiagDto = new XirrAmbiguityAndDiagnosticsDto(
            pXirrRes.xirrAnnualized(),
            pXirrRes.status().name(),
            pXirrRes.solverMethod(),
            pXirrRes.iterationsCount(),
            pXirrRes.residualNpv(),
            pXirrRes.dayCountConvention(),
            pXirrRes.firstCashFlowDate(),
            pXirrRes.lastCashFlowDate(),
            effectiveAsOf,
            pXirrRes.totalCashFlowCount(),
            pXirrRes.signAlternationsCount(),
            pXirrRes.isMultipleRootsPossible(),
            pXirrRes.candidateRoots(),
            pXirrRes.statusMessage(),
            "Investor XIRR measures actual cash flow timing; Fund CAGR assumes static buy-and-hold without cash flows. This return difference is not a measure of portfolio alpha or timing skill."
        );

        // 9. Research Findings
        List<String> findings = new ArrayList<>();
        findings.add("[FACT] Total invested capital in ledger: Rs. " + externalContributions.setScale(2, RoundingMode.HALF_UP) + "; Current portfolio market valuation: Rs. " + portfolioCurrentValue.setScale(2, RoundingMode.HALF_UP) + ".");
        findings.add("[FACT] Realized capital gains: Rs. " + portfolioRealizedGain.subtract(portfolioRealizedLoss).setScale(2, RoundingMode.HALF_UP) + " across " + allRealizedRecords.size() + " disposed lot tranche(s).");
        if (!rankedContributions.isEmpty()) {
            SchemePerformanceContributionDto top = rankedContributions.get(0);
            findings.add("[CALCULATED_EXPOSURE] Top contributing scheme '" + top.schemeName() + "' generated Rs. " + top.netFinancialContribution().setScale(2, RoundingMode.HALF_UP) + " (" + top.contributionToPortfolioAbsoluteGainsPercent().setScale(1, RoundingMode.HALF_UP) + "% of portfolio positive gains).");
        }
        if (pXirrRes.status() == XirrCalculator.XirrStatus.AVAILABLE && pXirrRes.xirrAnnualized() != null) {
            findings.add("[CALCULATED_EXPOSURE] Portfolio-level money-weighted return (XIRR) is " + pXirrRes.xirrAnnualized().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "% annualized (ACT/365 convention, " + pXirrRes.totalCashFlowCount() + " cash-flow observations).");
        } else if (pXirrRes.status() == XirrCalculator.XirrStatus.AMBIGUOUS_ROOT) {
            findings.add("[RISK_LIMITATION] Portfolio XIRR cannot be reported uniquely due to multiple polynomial roots from repeated sign reversals in cash flows.");
        }
        if (reconStatus.equals("RECONCILED")) {
            findings.add("[FACT] Financial outcome is reconciled: Closing value matches capital flows and gains within Rs. " + unexplainedResidual.abs() + ".");
        } else {
            findings.add("[RISK_LIMITATION] Financial reconciliation residual of Rs. " + unexplainedResidual + " exists (" + reconStatus + "). Causes: " + String.join("; ", residualCauses) + ".");
        }
        findings.add("[RISK_LIMITATION] Time-Weighted Return (TWR) is unavailable because intermediate portfolio valuations around external cash-flow boundaries are unrecorded in periodic statements.");

        List<String> disclaimers = List.of(
            "Financial calculations are deterministic and point-in-time verified under Project YUKIRA quantitative governance.",
            "FIFO cost-basis matching and realized gains represent mathematical accounting records and do not constitute tax advice or tax liability determination.",
            "Money-weighted return (XIRR) and time-weighted fund return (CAGR) measure fundamentally distinct financial properties.",
            "Attribution is computed strictly at the mutual fund unit layer; look-through stock exposures are not used to infer underlying security returns."
        );

        return new PortfolioPerformanceAttributionResponseDto(
            effectiveAsOf,
            effectiveCutoff,
            decompDto,
            rankedContributions,
            realizedDto,
            unrealizedDto,
            dividendDto,
            timingDto,
            xirrDiagDto,
            allHoldingLots,
            findings,
            disclaimers
        );
    }

        /**
     * Lists holding lots for the investor, optionally filtered by schemeOptionId.
     */
    @Transactional(readOnly = true)
    public List<HoldingLotPerformanceDto> listHoldingLots(String auth0Subject, Long schemeOptionId) {
        PortfolioPerformanceAttributionResponseDto attr = calculatePerformanceAttribution(auth0Subject, LocalDate.now(), OffsetDateTime.now());
        if (schemeOptionId == null) {
            return attr.holdingLots();
        }
        return attr.holdingLots().stream()
            .filter(l -> l.schemeOptionId() != null && l.schemeOptionId().equals(schemeOptionId))
            .toList();
    }
}
