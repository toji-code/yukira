package com.yukira.backend.controller.portfolio;

import com.yukira.backend.dto.portfolio.PortfolioHoldingDto;
import com.yukira.backend.dto.portfolio.PortfolioHoldingRequest;
import com.yukira.backend.dto.portfolio.PortfolioSummaryDto;
import com.yukira.backend.service.portfolio.InvestorPortfolioService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Primary REST controller for Portfolio Tracking V1.
 *
 * Exposes deterministic investor portfolio valuation, holdings tracking,
 * category allocation, and data-quality evidence.
 *
 * Epistemic Contract:
 * - Ownership is derived strictly from authenticated Jwt principal (or local dev context).
 * - All financial metrics (valuations, gain/loss, allocations) are computed in backend.
 * - Zero portfolio recommendation, zero composite portfolio score.
 */
@RestController
@RequestMapping("/api/v1/portfolio")
public class InvestorPortfolioController {

    private final InvestorPortfolioService portfolioService;

    public InvestorPortfolioController(InvestorPortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    /**
     * Retrieves complete portfolio analysis and holdings list for the authenticated investor.
     */
    @GetMapping
    public ResponseEntity<PortfolioSummaryDto> getPortfolio(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        PortfolioSummaryDto summary = portfolioService.getPortfolioSummary(subject);
        return ResponseEntity.ok(summary);
    }

    /**
     * Adds or updates an external mutual fund holding for the authenticated investor.
     */
    @PostMapping("/holdings")
    public ResponseEntity<PortfolioHoldingDto> addOrUpdateHolding(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody PortfolioHoldingRequest request) {
        String subject = jwt != null ? jwt.getSubject() : null;
        PortfolioHoldingDto holding = portfolioService.addOrUpdateHolding(subject, request);
        return ResponseEntity.ok(holding);
    }

    /**
     * Bulk adds or updates external mutual fund holdings for the authenticated investor.
     */
    @PostMapping("/holdings/bulk")
    public ResponseEntity<java.util.List<PortfolioHoldingDto>> addOrUpdateHoldingsBulk(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody java.util.List<PortfolioHoldingRequest> requests) {
        String subject = jwt != null ? jwt.getSubject() : null;
        java.util.List<PortfolioHoldingDto> holdings = portfolioService.addOrUpdateHoldingsBulk(subject, requests);
        return ResponseEntity.ok(holdings);
    }

    /**
     * Removes an external mutual fund holding by scheme_option_id for the authenticated investor.
     */
    @DeleteMapping("/holdings/{schemeOptionId}")
    public ResponseEntity<Void> removeHolding(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long schemeOptionId) {
        String subject = jwt != null ? jwt.getSubject() : null;
        portfolioService.removeHolding(subject, schemeOptionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Evaluates goal alignment for the authenticated investor's portfolio (POST).
     */
    @PostMapping("/goal-alignment")
    public ResponseEntity<com.yukira.backend.dto.portfolio.PortfolioGoalAlignmentDto> evaluateGoalAlignmentPost(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) com.yukira.backend.dto.portfolio.PortfolioGoalAlignmentRequest request) {
        String subject = jwt != null ? jwt.getSubject() : null;
        com.yukira.backend.dto.portfolio.PortfolioGoalAlignmentDto result = portfolioService.evaluateGoalAlignment(subject, request);
        return ResponseEntity.ok(result);
    }

    /**
     * Evaluates goal alignment for the authenticated investor's portfolio (GET with query parameters).
     */
    @GetMapping("/goal-alignment")
    public ResponseEntity<com.yukira.backend.dto.portfolio.PortfolioGoalAlignmentDto> evaluateGoalAlignmentGet(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false, defaultValue = "WEALTH_CREATION") String goalCategory,
            @RequestParam(required = false, defaultValue = "5") Integer horizonYears,
            @RequestParam(required = false, defaultValue = "MODERATE") String riskTolerance,
            @RequestParam(required = false, defaultValue = "SIP") String investmentMode,
            @RequestParam(required = false, defaultValue = "ANY") String fundCategory) {
        String subject = jwt != null ? jwt.getSubject() : null;
        com.yukira.backend.dto.portfolio.PortfolioGoalAlignmentRequest req = new com.yukira.backend.dto.portfolio.PortfolioGoalAlignmentRequest(
            goalCategory, horizonYears, riskTolerance, investmentMode, fundCategory
        );
        com.yukira.backend.dto.portfolio.PortfolioGoalAlignmentDto result = portfolioService.evaluateGoalAlignment(subject, req);
        return ResponseEntity.ok(result);
    }

    /**
     * Generates consolidated Portfolio Evidence Report & Investor Snapshot V1 (POST).
     */
    @PostMapping("/report")
    public ResponseEntity<com.yukira.backend.dto.portfolio.PortfolioReportDto> generateReportPost(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) com.yukira.backend.dto.portfolio.PortfolioGoalAlignmentRequest goalRequest) {
        String subject = jwt != null ? jwt.getSubject() : null;
        com.yukira.backend.dto.portfolio.PortfolioReportDto report = portfolioService.generatePortfolioReport(subject, goalRequest);
        return ResponseEntity.ok(report);
    }

    /**
     * Generates consolidated Portfolio Evidence Report & Investor Snapshot V1 (GET).
     */
    @GetMapping("/report")
    public ResponseEntity<com.yukira.backend.dto.portfolio.PortfolioReportDto> generateReportGet(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String goalCategory,
            @RequestParam(required = false) Integer horizonYears,
            @RequestParam(required = false) String riskTolerance,
            @RequestParam(required = false) String investmentMode,
            @RequestParam(required = false) String fundCategory) {
        String subject = jwt != null ? jwt.getSubject() : null;
        com.yukira.backend.dto.portfolio.PortfolioGoalAlignmentRequest goalReq = null;
        if (goalCategory != null || horizonYears != null || riskTolerance != null || investmentMode != null || fundCategory != null) {
            goalReq = new com.yukira.backend.dto.portfolio.PortfolioGoalAlignmentRequest(
                goalCategory, horizonYears, riskTolerance, investmentMode, fundCategory
            );
        }
        com.yukira.backend.dto.portfolio.PortfolioReportDto report = portfolioService.generatePortfolioReport(subject, goalReq);
        return ResponseEntity.ok(report);
    }

    /**
     * Retrieves the investor-defined target allocation.
     */
    @GetMapping("/target-allocation")
    public ResponseEntity<com.yukira.backend.dto.portfolio.TargetAllocationDto> getTargetAllocation(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        com.yukira.backend.dto.portfolio.TargetAllocationDto target = portfolioService.getTargetAllocation(subject);
        return ResponseEntity.ok(target);
    }

    /**
     * Sets/saves the investor-defined target allocation.
     * Validates that SUM(weights) == 100%. Rejects invalid total weights with HTTP 400.
     */
    @PostMapping("/target-allocation")
    public ResponseEntity<?> setTargetAllocation(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody com.yukira.backend.dto.portfolio.TargetAllocationDto request) {
        String subject = jwt != null ? jwt.getSubject() : null;
        try {
            com.yukira.backend.dto.portfolio.TargetAllocationDto result = portfolioService.setTargetAllocation(subject, request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    /**
     * Clears/deletes the investor-defined target allocation.
     */
    @DeleteMapping("/target-allocation")
    public ResponseEntity<Void> deleteTargetAllocation(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        portfolioService.deleteTargetAllocation(subject);
        return ResponseEntity.noContent().build();
    }

    /**
     * Evaluates portfolio allocation drift between current portfolio weights and investor-defined target weights.
     */
    @GetMapping("/drift-analysis")
    public ResponseEntity<com.yukira.backend.dto.portfolio.PortfolioDriftAnalysisDto> getDriftAnalysis(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        com.yukira.backend.dto.portfolio.PortfolioDriftAnalysisDto drift = portfolioService.evaluatePortfolioDrift(subject);
        return ResponseEntity.ok(drift);
    }

    /**
     * Evaluates portfolio fund comparison & evidence matrix V1 for the authenticated investor's holdings (GET).
     */
    @GetMapping("/compare")
    public ResponseEntity<com.yukira.backend.dto.analysis.ComparisonResponse> evaluatePortfolioComparisonGet(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) java.util.List<Long> schemeOptionIds,
            @RequestParam(required = false) java.util.List<String> metricCodes) {
        String subject = jwt != null ? jwt.getSubject() : null;
        com.yukira.backend.dto.analysis.ComparisonResponse response = portfolioService.evaluatePortfolioComparison(subject, schemeOptionIds, metricCodes);
        return ResponseEntity.ok(response);
    }

    /**
     * Evaluates portfolio fund comparison & evidence matrix V1 for the authenticated investor's holdings (POST).
     */
    @PostMapping("/compare")
    public ResponseEntity<com.yukira.backend.dto.analysis.ComparisonResponse> evaluatePortfolioComparisonPost(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) com.yukira.backend.dto.analysis.ComparisonRequest request) {
        String subject = jwt != null ? jwt.getSubject() : null;
        java.util.List<Long> optionIds = request != null ? request.schemeOptionIds() : null;
        java.util.List<String> codes = request != null ? request.metricCodes() : null;
        com.yukira.backend.dto.analysis.ComparisonResponse response = portfolioService.evaluatePortfolioComparison(subject, optionIds, codes);
        return ResponseEntity.ok(response);
    }
}
