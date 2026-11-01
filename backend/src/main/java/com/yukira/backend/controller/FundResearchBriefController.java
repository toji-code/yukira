package com.yukira.backend.controller;

import com.yukira.backend.service.*;
import com.yukira.backend.service.portfolio.PortfolioDriftRebalanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/research")
public class FundResearchBriefController {

    private final FundResearchBriefService researchBriefService;
    private final FundComparisonWorkflowService comparisonWorkflowService;
    private final HoldingsOverlapService holdingsOverlapService;
    private final HoldingsConstructionService holdingsConstructionService;
    private final FifoLotLedgerService fifoLotLedgerService;
    private final PortfolioDriftRebalanceService portfolioDriftRebalanceService;

    public FundResearchBriefController(
        FundResearchBriefService researchBriefService,
        FundComparisonWorkflowService comparisonWorkflowService,
        HoldingsOverlapService holdingsOverlapService,
        HoldingsConstructionService holdingsConstructionService,
        FifoLotLedgerService fifoLotLedgerService,
        PortfolioDriftRebalanceService portfolioDriftRebalanceService
    ) {
        this.researchBriefService = researchBriefService;
        this.comparisonWorkflowService = comparisonWorkflowService;
        this.holdingsOverlapService = holdingsOverlapService;
        this.holdingsConstructionService = holdingsConstructionService;
        this.fifoLotLedgerService = fifoLotLedgerService;
        this.portfolioDriftRebalanceService = portfolioDriftRebalanceService;
    }

    /**
     * One-click investor fund research brief dossier (Task 256).
     */
    @GetMapping("/brief/{schemeOptionId}")
    public ResponseEntity<?> getResearchBrief(@PathVariable Long schemeOptionId) {
        return researchBriefService.generateResearchBrief(schemeOptionId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Multi-fund side-by-side comparison (Task 255).
     */
    @GetMapping("/comparison")
    public ResponseEntity<?> getComparison(
        @RequestParam(name = "options") String optionIdsCsv
    ) {
        List<Long> ids = Arrays.stream(optionIdsCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Long::parseLong)
            .collect(Collectors.toList());

        return ResponseEntity.ok(comparisonWorkflowService.compareFunds(ids));
    }

    /**
     * Pairwise holdings overlap between two funds (Task 237).
     */
    @GetMapping("/overlap")
    public ResponseEntity<?> getPairwiseOverlap(
        @RequestParam(name = "optionA") Long optionIdA,
        @RequestParam(name = "optionB") Long optionIdB
    ) {
        return ResponseEntity.ok(holdingsOverlapService.calculatePairwiseOverlap(optionIdA, optionIdB));
    }

    /**
     * Fund construction & holdings breakdown (Tasks 232, 234, 235, 236).
     */
    @GetMapping("/holdings-construction/{schemeOptionId}")
    public ResponseEntity<?> getHoldingsConstruction(
        @PathVariable Long schemeOptionId,
        @RequestParam(name = "asOfDate", required = false) String asOfDate
    ) {
        LocalDate dt = (asOfDate != null && !asOfDate.isBlank()) ? LocalDate.parse(asOfDate) : null;
        return holdingsConstructionService.getHoldingsConstruction(schemeOptionId, dt)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Active FIFO tax-lots breakdown for portfolio holding (Task 245).
     */
    @GetMapping("/tax-lots/{schemeOptionId}")
    public ResponseEntity<?> getTaxLots(
        @PathVariable Long schemeOptionId,
        @RequestParam(name = "investorId", defaultValue = "1") Long investorId
    ) {
        return ResponseEntity.ok(fifoLotLedgerService.getActiveLots(investorId, schemeOptionId));
    }

    /**
     * Target allocation drift and rebalancing trigger report (Task 247).
     */
    @GetMapping("/portfolio-drift")
    public ResponseEntity<?> getPortfolioDrift(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(name = "threshold", defaultValue = "5.0") double threshold
    ) {
        String subject = jwt != null ? jwt.getSubject() : "dev-investor";
        return ResponseEntity.ok(portfolioDriftRebalanceService.evaluateDrift(subject, threshold));
    }
}

