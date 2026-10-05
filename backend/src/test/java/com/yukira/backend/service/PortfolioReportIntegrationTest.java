package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.dto.portfolio.*;
import com.yukira.backend.service.portfolio.InvestorPortfolioService;
import com.yukira.backend.service.portfolio.PortfolioEvidenceReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for Portfolio Evidence Export & Investor Report V1.
 *
 * Verifies key acceptance conditions:
 * 1. Authenticated report generation for canonical HDFC pilot
 * 2. Auth0 ownership isolation (User A vs User B)
 * 3. Preservation of exact scheme_option_id identity across report DTOs
 * 4. Portfolio valuation retrieval & investor P&L precision
 * 5. Fund-level YUKIRA score & dimension breakdown retrieval
 * 6. Fund-level risk evidence integration
 * 7. Goal alignment inclusion when requested vs exclusion when omitted
 * 8. Provenance artifact hash verification
 * 9. Explicit PIT as-of metadata labeling (2024-01-15 valuation / 2024-01-31 cutoff)
 * 10. Empty portfolio report handling
 */
@SpringBootTest
@ActiveProfiles("test")
public class PortfolioReportIntegrationTest {

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private InvestorPortfolioService portfolioService;

    @Autowired
    private PortfolioEvidenceReportService reportService;

    private static final String TEST_USER_A = "auth0|user-report-a";
    private static final String TEST_USER_B = "auth0|user-report-b";

    @BeforeEach
    void setUp() {
        pilotBootstrapService.bootstrapPilot();
        portfolioService.removeHolding(TEST_USER_A, 1L);
        portfolioService.removeHolding(TEST_USER_A, 2L);
        portfolioService.removeHolding(TEST_USER_B, 1L);
    }

    @Test
    @DisplayName("TC-01: Canonical HDFC pilot portfolio report generation")
    void testCanonicalHdfcReportGeneration() {
        portfolioService.addOrUpdateHolding(TEST_USER_A, new PortfolioHoldingRequest(
            1L, BigDecimal.valueOf(100.0), BigDecimal.valueOf(1500.0)
        ));

        PortfolioReportDto report = portfolioService.generatePortfolioReport(TEST_USER_A, null);

        assertNotNull(report);
        assertNotNull(report.reportMetadata());
        assertEquals("2024-01-15", report.reportMetadata().valuationAsOfDate());
        assertEquals("2024-01-31 23:59:59+05:30", report.reportMetadata().knowledgeCutoff());
        assertEquals("YUKIRA_SCORE_V1", report.reportMetadata().scoreVersion());
        assertEquals("CANDIDATE", report.reportMetadata().scoreMethodologyStatus());

        assertNotNull(report.portfolioSummary());
        assertEquals(1, report.portfolioSummary().totalHoldings());
        assertEquals(1, report.portfolioSummary().valuedHoldingsCount());

        assertNotNull(report.holdingDetails());
        assertEquals(1, report.holdingDetails().size());

        PortfolioReportDto.ReportHoldingDetailDto holding = report.holdingDetails().get(0);
        assertEquals(1L, holding.schemeOptionId());
        assertEquals("118955", holding.amfiCode());
        assertEquals("INF179K01UT0", holding.isin());
        assertTrue(holding.fundName().contains("HDFC Flexi Cap Fund"));
        assertEquals(0, BigDecimal.valueOf(100.0).compareTo(holding.units()));
        assertEquals(0, BigDecimal.valueOf(1500.0).compareTo(holding.costBasisAmount()));
        assertEquals(0, BigDecimal.valueOf(150000.0).compareTo(holding.investedAmount()));
        assertEquals(0, BigDecimal.valueOf(1670.672).compareTo(holding.navValue()));
        assertEquals(0, BigDecimal.valueOf(167067.20).compareTo(holding.availableValue()));
        assertEquals(0, BigDecimal.valueOf(17067.20).compareTo(holding.absoluteGainLoss()));

        // Check fund score detail
        assertNotNull(holding.scoreDetail());
        assertTrue(holding.scoreDetail().available());
        assertEquals(0, BigDecimal.valueOf(67.09).compareTo(holding.scoreDetail().scoreValue()));

        // Check provenance artifact hash
        assertEquals("900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259", holding.sourceArtifactHash());

        // Check disclaimers & limitations
        assertFalse(report.dataQualityLimitations().isEmpty());
        assertFalse(report.disclaimers().isEmpty());
        assertTrue(report.disclaimers().stream().anyMatch(d -> d.contains("does NOT constitute financial advice")));
    }

    @Test
    @DisplayName("TC-02: Auth0 investor isolation in report generation")
    void testAuth0UserIsolation() {
        portfolioService.addOrUpdateHolding(TEST_USER_A, new PortfolioHoldingRequest(
            1L, BigDecimal.valueOf(100.0), BigDecimal.valueOf(1500.0)
        ));

        PortfolioReportDto reportA = portfolioService.generatePortfolioReport(TEST_USER_A, null);
        assertEquals(1, reportA.portfolioSummary().totalHoldings());

        PortfolioReportDto reportB = portfolioService.generatePortfolioReport(TEST_USER_B, null);
        assertEquals(0, reportB.portfolioSummary().totalHoldings());
        assertTrue(reportB.holdingDetails().isEmpty());
    }

    @Test
    @DisplayName("TC-03: Report generation with Goal Alignment request")
    void testReportWithGoalAlignment() {
        portfolioService.addOrUpdateHolding(TEST_USER_A, new PortfolioHoldingRequest(
            1L, BigDecimal.valueOf(100.0), BigDecimal.valueOf(1500.0)
        ));

        PortfolioGoalAlignmentRequest goalRequest = new PortfolioGoalAlignmentRequest(
            "WEALTH_CREATION", 5, "MODERATE", "SIP", "Equity Scheme"
        );

        PortfolioReportDto report = portfolioService.generatePortfolioReport(TEST_USER_A, goalRequest);

        assertNotNull(report.goalAlignment());
        assertEquals("COMPLETE", report.goalAlignment().coverageState());
        assertEquals(1, report.goalAlignment().totalHoldingsCount());
        assertEquals("WEALTH_CREATION", report.goalAlignment().goalRequirements().goalCategory());

        // Holding should also have goalAlignmentDetail attached
        PortfolioReportDto.ReportHoldingDetailDto holding = report.holdingDetails().get(0);
        assertNotNull(holding.goalAlignmentDetail());
        assertEquals("PARTIALLY_EVALUATED", holding.goalAlignmentDetail().alignmentState());
    }

    @Test
    @DisplayName("TC-04: Report generation without Goal Alignment specifies non-eval state")
    void testReportWithoutGoalAlignment() {
        portfolioService.addOrUpdateHolding(TEST_USER_A, new PortfolioHoldingRequest(
            1L, BigDecimal.valueOf(100.0), BigDecimal.valueOf(1500.0)
        ));

        PortfolioReportDto report = portfolioService.generatePortfolioReport(TEST_USER_A, null);

        assertNull(report.goalAlignment());
        assertTrue(report.disclaimers().stream().anyMatch(d -> d.contains("Goal alignment not evaluated")));
    }

    @Test
    @DisplayName("TC-05: Empty portfolio report handling")
    void testEmptyPortfolioReport() {
        PortfolioReportDto report = portfolioService.generatePortfolioReport(TEST_USER_B, null);

        assertNotNull(report);
        assertEquals(0, report.portfolioSummary().totalHoldings());
        assertEquals("EMPTY", report.portfolioSummary().valuationCoverageState());
        assertTrue(report.holdingDetails().isEmpty());
        assertFalse(report.disclaimers().isEmpty());
    }

    @Test
    @DisplayName("TC-06: Dedicated evidence report service includes target drift evidence")
    void testDedicatedReportServiceIncludesTargetDriftEvidence() {
        portfolioService.addOrUpdateHolding(TEST_USER_A, new PortfolioHoldingRequest(
            1L, BigDecimal.valueOf(100.0), BigDecimal.valueOf(1500.0)
        ));

        PortfolioReportDto report = reportService.generateReport(TEST_USER_A, null);

        assertNotNull(report.targetDriftAnalysis());
        assertEquals("NO_TARGET", report.targetDriftAnalysis().status());
        assertTrue(report.targetDriftAnalysis().investigationQuestions().stream()
            .anyMatch(q -> q.contains("No target allocation")));
    }
}
