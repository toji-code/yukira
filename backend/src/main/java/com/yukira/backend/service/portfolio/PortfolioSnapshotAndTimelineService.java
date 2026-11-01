package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.snapshot.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.InvestorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for holding-period valuation history, cash-flow timelines, and point-in-time
 * portfolio research snapshots (Tasks 185, 187, 190).
 */
@Service
public class PortfolioSnapshotAndTimelineService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioSnapshotAndTimelineService.class);

    private final PortfolioValuationHistoryRepository valuationHistoryRepository;
    private final InvestorPortfolioResearchSnapshotRepository researchSnapshotRepository;
    private final InvestorPortfolioAllocationSnapshotRepository allocationSnapshotRepository;
    private final InvestorTransactionRepository transactionRepository;
    private final InvestorService investorService;

    public PortfolioSnapshotAndTimelineService(
        PortfolioValuationHistoryRepository valuationHistoryRepository,
        InvestorPortfolioResearchSnapshotRepository researchSnapshotRepository,
        InvestorPortfolioAllocationSnapshotRepository allocationSnapshotRepository,
        InvestorTransactionRepository transactionRepository,
        InvestorService investorService
    ) {
        this.valuationHistoryRepository = valuationHistoryRepository;
        this.researchSnapshotRepository = researchSnapshotRepository;
        this.allocationSnapshotRepository = allocationSnapshotRepository;
        this.transactionRepository = transactionRepository;
        this.investorService = investorService;
    }

    @Transactional(readOnly = true)
    public List<PortfolioValuationHistoryDto> getValuationHistory(String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        return valuationHistoryRepository.findByInvestorOrderByValuationDateDesc(investor).stream()
            .map(this::toValuationDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public PortfolioValuationHistoryDto recordValuation(
        String auth0Subject,
        Long portfolioId,
        LocalDate valuationDate,
        ZonedDateTime knowledgeCutoff,
        BigDecimal totalMarketValue,
        BigDecimal totalCostBasis,
        int valuedPositions,
        int unvaluedPositions,
        String sourceContext
    ) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        PortfolioValuationHistory v = new PortfolioValuationHistory();
        v.setInvestor(investor);
        v.setPortfolioId(portfolioId);
        v.setValuationDate(valuationDate != null ? valuationDate : LocalDate.now());
        v.setKnowledgeCutoff(knowledgeCutoff != null ? knowledgeCutoff : ZonedDateTime.now());
        v.setTotalMarketValue(totalMarketValue != null ? totalMarketValue : BigDecimal.ZERO);
        v.setTotalCostBasis(totalCostBasis != null ? totalCostBasis : BigDecimal.ZERO);
        v.setUnrealizedGainLoss(v.getTotalMarketValue().subtract(v.getTotalCostBasis()));
        v.setValuedPositionsCount(valuedPositions);
        v.setUnvaluedPositionsCount(unvaluedPositions);
        v.setValuationSourceContext(sourceContext);

        PortfolioValuationHistory saved = valuationHistoryRepository.save(v);
        log.info("Recorded portfolio valuation history for investor {}: date={}, marketVal={}",
            investor.getId(), saved.getValuationDate(), saved.getTotalMarketValue());
        return toValuationDto(saved);
    }

    @Transactional(readOnly = true)
    public PortfolioCashFlowTimelineDto getCashFlowTimeline(String auth0Subject, LocalDate asOfDate) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        LocalDate evalDate = asOfDate != null ? asOfDate : LocalDate.now();

        List<InvestorTransaction> txs = transactionRepository.findByInvestorAndIsReversedFalseOrderByTransactionDateAsc(investor);

        BigDecimal cumulativeInflow = BigDecimal.ZERO;
        BigDecimal cumulativeOutflow = BigDecimal.ZERO;
        List<PortfolioCashFlowTimelineDto.CashFlowEventDto> events = new ArrayList<>();

        for (InvestorTransaction tx : txs) {
            if (tx.getTransactionDate().isAfter(evalDate)) {
                continue;
            }
            String type = tx.getTransactionType();
            String classification = tx.getClassification() != null ? tx.getClassification() : "EXTERNAL_INVESTMENT";
            BigDecimal amt = tx.getAmount() != null ? tx.getAmount().abs() : BigDecimal.ZERO;
            String direction = "PURCHASE".equalsIgnoreCase(type) || "SIP".equalsIgnoreCase(type) || "SWITCH_IN".equalsIgnoreCase(type)
                ? "INFLOW"
                : ("REDEMPTION".equalsIgnoreCase(type) || "SWP".equalsIgnoreCase(type) || "SWITCH_OUT".equalsIgnoreCase(type)
                    ? "OUTFLOW" : "NEUTRAL");

            if ("INFLOW".equals(direction)) {
                cumulativeInflow = cumulativeInflow.add(amt);
            } else if ("OUTFLOW".equals(direction)) {
                cumulativeOutflow = cumulativeOutflow.add(amt);
            }

            String schemeName = tx.getSchemeOption() != null && tx.getSchemeOption().getPlan() != null && tx.getSchemeOption().getPlan().getScheme() != null
                ? tx.getSchemeOption().getPlan().getScheme().getName()
                : "Portfolio Position";

            events.add(new PortfolioCashFlowTimelineDto.CashFlowEventDto(
                tx.getId(),
                tx.getTransactionDate(),
                tx.getTransactionType(),
                classification,
                schemeName,
                tx.getAmount(),
                tx.getUnits(),
                tx.getNav(),
                direction
            ));
        }

        BigDecimal netInvested = cumulativeInflow.subtract(cumulativeOutflow);

        return new PortfolioCashFlowTimelineDto(
            investor.getId(),
            evalDate,
            netInvested,
            BigDecimal.ZERO, // Latest valuation evaluated separately
            cumulativeInflow,
            cumulativeOutflow,
            events
        );
    }

    @Transactional(readOnly = true)
    public List<PortfolioResearchSnapshotDto> getResearchSnapshots(String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        return researchSnapshotRepository.findByInvestorOrderByCreatedAtDesc(investor).stream()
            .map(this::toSnapshotDto)
            .collect(Collectors.toList());
    }

    @Transactional
    public PortfolioResearchSnapshotDto createResearchSnapshot(
        String auth0Subject,
        CreatePortfolioResearchSnapshotRequest request
    ) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);

        InvestorPortfolioAllocationSnapshot alloc = null;
        if (request.allocationSnapshotId() != null) {
            alloc = allocationSnapshotRepository.findById(request.allocationSnapshotId()).orElse(null);
        }

        LocalDate asOf = request.asOfDate() != null ? request.asOfDate() : LocalDate.now();
        ZonedDateTime cutoff = request.knowledgeCutoff() != null ? request.knowledgeCutoff() : ZonedDateTime.now();

        String rawFingerprint = investor.getId() + ":" + asOf + ":" + cutoff + ":" + request.totalValue();
        String hash = computeSha256(rawFingerprint);

        InvestorPortfolioResearchSnapshot s = new InvestorPortfolioResearchSnapshot();
        s.setInvestor(investor);
        s.setSnapshotTitle(request.snapshotTitle() != null ? request.snapshotTitle() : "Portfolio Research Snapshot " + asOf);
        s.setAllocationSnapshot(alloc);
        s.setAsOfDate(asOf);
        s.setKnowledgeCutoff(cutoff);
        s.setFingerprint(hash);
        s.setXirrValue(request.xirrValue());
        s.setTotalValue(request.totalValue() != null ? request.totalValue() : BigDecimal.ZERO);
        s.setCashFlowSummaryJson(request.cashFlowSummaryJson());
        s.setResearchThesis(request.researchThesis());
        s.setInvestigationNotes(request.investigationNotes());

        InvestorPortfolioResearchSnapshot saved = researchSnapshotRepository.save(s);
        log.info("Created point-in-time portfolio research snapshot #{} for investor {}: '{}'",
            saved.getId(), investor.getId(), saved.getSnapshotTitle());
        return toSnapshotDto(saved);
    }

    private PortfolioValuationHistoryDto toValuationDto(PortfolioValuationHistory v) {
        return new PortfolioValuationHistoryDto(
            v.getId(),
            v.getPortfolioId(),
            v.getValuationDate(),
            v.getKnowledgeCutoff(),
            v.getTotalMarketValue(),
            v.getTotalCostBasis(),
            v.getUnrealizedGainLoss(),
            v.getValuedPositionsCount(),
            v.getUnvaluedPositionsCount(),
            v.getValuationSourceContext(),
            v.getCreatedAt()
        );
    }

    private PortfolioResearchSnapshotDto toSnapshotDto(InvestorPortfolioResearchSnapshot s) {
        return new PortfolioResearchSnapshotDto(
            s.getId(),
            s.getSnapshotTitle(),
            s.getAllocationSnapshot() != null ? s.getAllocationSnapshot().getId() : null,
            s.getAsOfDate(),
            s.getKnowledgeCutoff(),
            s.getFingerprint(),
            s.getXirrValue(),
            s.getTotalValue(),
            s.getCashFlowSummaryJson(),
            s.getResearchThesis(),
            s.getInvestigationNotes(),
            s.getCreatedAt()
        );
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}

