package com.yukira.backend.service.portfolio;

import com.yukira.backend.dto.portfolio.PortfolioDriftAnalysisDto;
import com.yukira.backend.dto.portfolio.PortfolioGoalAlignmentRequest;
import com.yukira.backend.dto.portfolio.PortfolioReportDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-model service for Portfolio Evidence Report V1.
 *
 * This service assembles investor-facing report evidence from existing persisted
 * portfolio read models only. It does not execute quant kernels, introduce new
 * methodology, or manufacture missing values.
 */
@Service
public class PortfolioEvidenceReportService {

    private final InvestorPortfolioService portfolioService;

    public PortfolioEvidenceReportService(InvestorPortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @Transactional(readOnly = true)
    public PortfolioReportDto generateReport(String auth0Subject, PortfolioGoalAlignmentRequest goalRequest) {
        PortfolioReportDto baseReport = portfolioService.generatePortfolioReport(auth0Subject, goalRequest);
        PortfolioDriftAnalysisDto driftAnalysis = portfolioService.evaluatePortfolioDrift(auth0Subject);

        return new PortfolioReportDto(
            baseReport.reportMetadata(),
            baseReport.portfolioSummary(),
            baseReport.goalAlignment(),
            baseReport.holdingDetails(),
            baseReport.dataQualityLimitations(),
            baseReport.investigationQuestions(),
            baseReport.disclaimers(),
            driftAnalysis
        );
    }
}