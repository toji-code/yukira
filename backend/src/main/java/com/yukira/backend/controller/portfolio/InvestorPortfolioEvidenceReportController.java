package com.yukira.backend.controller.portfolio;

import com.yukira.backend.dto.portfolio.PortfolioGoalAlignmentRequest;
import com.yukira.backend.dto.portfolio.PortfolioReportDto;
import com.yukira.backend.service.portfolio.PortfolioEvidenceReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Investor-scoped read-only endpoint for Portfolio Evidence Report V1.
 */
@RestController
@RequestMapping("/api/v1/investor/portfolio")
public class InvestorPortfolioEvidenceReportController {

    private final PortfolioEvidenceReportService reportService;

    public InvestorPortfolioEvidenceReportController(PortfolioEvidenceReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/report")
    public ResponseEntity<PortfolioReportDto> generateReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String goalCategory,
            @RequestParam(required = false) Integer horizonYears,
            @RequestParam(required = false) String riskTolerance,
            @RequestParam(required = false) String investmentMode,
            @RequestParam(required = false) String fundCategory) {
        String subject = jwt != null ? jwt.getSubject() : null;
        PortfolioGoalAlignmentRequest goalRequest = null;
        if (goalCategory != null || horizonYears != null || riskTolerance != null || investmentMode != null || fundCategory != null) {
            goalRequest = new PortfolioGoalAlignmentRequest(goalCategory, horizonYears, riskTolerance, investmentMode, fundCategory);
        }

        return ResponseEntity.ok(reportService.generateReport(subject, goalRequest));
    }
}