package com.yukira.backend.controller.portfolio;

import com.yukira.backend.dto.portfolio.performance.*;
import com.yukira.backend.dto.portfolio.snapshot.*;
import com.yukira.backend.dto.portfolio.transaction.*;
import com.yukira.backend.service.portfolio.InvestorTransactionService;
import com.yukira.backend.service.portfolio.PortfolioSnapshotAndTimelineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Controller exposing the Investor Transaction Ledger, Holdings Reconciliation,
 * and Investor-Specific Performance Intelligence (XIRR, realized/unrealized).
 */
@RestController
@RequestMapping("/api/v1/portfolio")
public class InvestorTransactionController {

    private final InvestorTransactionService transactionService;
    private final PortfolioSnapshotAndTimelineService snapshotAndTimelineService;

    public InvestorTransactionController(
        InvestorTransactionService transactionService,
        @org.springframework.beans.factory.annotation.Autowired(required = false) PortfolioSnapshotAndTimelineService snapshotAndTimelineService
    ) {
        this.transactionService = transactionService;
        this.snapshotAndTimelineService = snapshotAndTimelineService;
    }

    /**
     * Lists investor transactions with optional filtering.
     */
    @GetMapping("/transactions")
    public ResponseEntity<List<InvestorTransactionDto>> listTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long schemeOptionId,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        String subject = jwt != null ? jwt.getSubject() : null;
        List<InvestorTransactionDto> result = transactionService.listTransactions(
            subject, schemeOptionId, transactionType, startDate, endDate
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Records a manual investor transaction.
     */
    @PostMapping("/transactions")
    public ResponseEntity<InvestorTransactionDto> createManualTransaction(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateManualTransactionRequest request) {
        String subject = jwt != null ? jwt.getSubject() : null;
        InvestorTransactionDto created = transactionService.createManualTransaction(subject, request);
        return ResponseEntity.ok(created);
    }

    /**
     * Reverses an existing transaction with an auditable compensating record.
     */
    @PostMapping("/transactions/{id}/reverse")
    public ResponseEntity<InvestorTransactionDto> reverseTransaction(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody(required = false) ReverseTransactionRequest request) {
        String subject = jwt != null ? jwt.getSubject() : null;
        InvestorTransactionDto reversed = transactionService.reverseTransaction(subject, id, request);
        return ResponseEntity.ok(reversed);
    }

    /**
     * Retrieves holdings reconciliation comparing ledger units vs reported units.
     */
    @GetMapping("/reconciliation")
    public ResponseEntity<PortfolioHoldingsReconciliationResponseDto> getReconciliation(
            @AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        PortfolioHoldingsReconciliationResponseDto result = transactionService.reconcileHoldings(subject);
        return ResponseEntity.ok(result);
    }

    /**
     * Retrieves investor-specific return analytics (XIRR, realized/unrealized, cash flows).
     */
    @GetMapping("/performance")
    public ResponseEntity<PortfolioInvestorPerformanceResponseDto> getPerformance(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) LocalDate asOfDate,
            @RequestParam(required = false) OffsetDateTime knowledgeCutoff) {
        String subject = jwt != null ? jwt.getSubject() : null;
        PortfolioInvestorPerformanceResponseDto result = transactionService.calculateInvestorPerformance(
            subject, asOfDate, knowledgeCutoff
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Reviews CAS import transaction details and unresolved mappings for an import batch.
     */
    @GetMapping("/imports/{id}/transactions")
    public ResponseEntity<CasImportTransactionReviewDto> reviewImportTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        String subject = jwt != null ? jwt.getSubject() : null;
        CasImportTransactionReviewDto review = transactionService.reviewImportTransactions(subject, id);
        return ResponseEntity.ok(review);
    }

    /**
     * Retrieves comprehensive performance attribution, financial decomposition,
     * scheme contribution ranking, realized vs unrealized decomposition, and XIRR root diagnostics.
     */
    @GetMapping("/attribution")
    public ResponseEntity<PortfolioPerformanceAttributionResponseDto> getPerformanceAttribution(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) LocalDate asOfDate,
            @RequestParam(required = false) OffsetDateTime knowledgeCutoff) {
        String subject = jwt != null ? jwt.getSubject() : null;
        PortfolioPerformanceAttributionResponseDto result = transactionService.calculatePerformanceAttribution(
            subject, asOfDate, knowledgeCutoff
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Lists holding lots for the investor, optionally filtered by schemeOptionId.
     */
    @GetMapping("/lots")
    public ResponseEntity<List<HoldingLotPerformanceDto>> listHoldingLots(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long schemeOptionId) {
        String subject = jwt != null ? jwt.getSubject() : null;
        List<HoldingLotPerformanceDto> result = transactionService.listHoldingLots(subject, schemeOptionId);
        return ResponseEntity.ok(result);
    }

    /**
     * Retrieves cumulative cash flows and timeline events (Task 187).
     */
    @GetMapping("/transactions/timeline")
    public ResponseEntity<PortfolioCashFlowTimelineDto> getCashFlowTimeline(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) LocalDate asOfDate) {
        String subject = jwt != null ? jwt.getSubject() : null;
        if (snapshotAndTimelineService == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(snapshotAndTimelineService.getCashFlowTimeline(subject, asOfDate));
    }

    /**
     * Retrieves historical portfolio valuation records (Task 185).
     */
    @GetMapping("/valuation-history")
    public ResponseEntity<List<PortfolioValuationHistoryDto>> getValuationHistory(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        if (snapshotAndTimelineService == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(snapshotAndTimelineService.getValuationHistory(subject));
    }

    /**
     * Retrieves point-in-time portfolio research snapshots (Task 190).
     */
    @GetMapping("/research-snapshots")
    public ResponseEntity<List<PortfolioResearchSnapshotDto>> getResearchSnapshots(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        if (snapshotAndTimelineService == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(snapshotAndTimelineService.getResearchSnapshots(subject));
    }

    /**
     * Creates a new point-in-time portfolio research snapshot (Task 190).
     */
    @PostMapping("/research-snapshots")
    public ResponseEntity<PortfolioResearchSnapshotDto> createResearchSnapshot(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreatePortfolioResearchSnapshotRequest request) {
        String subject = jwt != null ? jwt.getSubject() : null;
        if (snapshotAndTimelineService == null) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        PortfolioResearchSnapshotDto created = snapshotAndTimelineService.createResearchSnapshot(subject, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}

