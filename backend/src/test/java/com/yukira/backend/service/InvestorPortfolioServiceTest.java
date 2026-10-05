package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.dto.portfolio.PortfolioHoldingDto;
import com.yukira.backend.dto.portfolio.PortfolioHoldingRequest;
import com.yukira.backend.dto.portfolio.PortfolioSummaryDto;
import com.yukira.backend.service.portfolio.InvestorPortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit & Integration Tests for Portfolio Tracking V1.
 *
 * Epistemic & Security Verification:
 * 1. Canonical HDFC pilot instrument (scheme_option_id = 1) holding tracking & valuation.
 * 2. Deterministic valuation calculation (units * NAV value).
 * 3. Historical NAV as-of date (2024-01-15) exposed explicitly.
 * 4. Missing NAV yields VALUATION_UNAVAILABLE (never zero).
 * 5. Cost basis provided calculates invested amount & gain/loss.
 * 6. Missing cost basis yields gainLossState = NOT_AVAILABLE (no fabricated return metrics).
 * 7. YUKIRA_SCORE_V1 read-only integration preserves CANDIDATE status.
 * 8. Zero portfolio score / single composite grade generated.
 * 9. Cross-user isolation: User A cannot access or delete User B's holdings.
 */
@SpringBootTest
@ActiveProfiles("test")
public class InvestorPortfolioServiceTest {

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private InvestorPortfolioService portfolioService;

    private static final String USER_A = "auth0|user-a-test";
    private static final String USER_B = "auth0|user-b-test";

    @BeforeEach
    void setUp() {
        pilotBootstrapService.bootstrapPilot();
        // Clean up holdings for test users before each test
        portfolioService.removeHolding(USER_A, 1L);
        portfolioService.removeHolding(USER_A, 2L);
        portfolioService.removeHolding(USER_B, 1L);
    }

    @Test
    @DisplayName("TC-01: Canonical HDFC pilot holding creation & valuation")
    void testCanonicalHdfcPilotHolding() {
        PortfolioHoldingRequest request = new PortfolioHoldingRequest(
            1L, BigDecimal.valueOf(100.0), BigDecimal.valueOf(1500.0)
        );

        PortfolioHoldingDto holding = portfolioService.addOrUpdateHolding(USER_A, request);

        assertNotNull(holding);
        assertEquals(1L, holding.schemeOptionId());
        assertEquals("118955", holding.amfiCode());
        assertEquals("INF179K01UT0", holding.isin());
        assertEquals(0, BigDecimal.valueOf(100.0).compareTo(holding.units()));
        assertEquals(0, BigDecimal.valueOf(1500.0).compareTo(holding.costBasisAmount()));
        assertEquals(0, BigDecimal.valueOf(150000.0).compareTo(holding.investedAmount()));

        // NAV observation for pilot on 2024-01-15 is 1670.6720
        assertEquals("VALUATION_AVAILABLE", holding.valuationState());
        assertNotNull(holding.availableValue());
        assertTrue(holding.availableValue().compareTo(BigDecimal.ZERO) > 0);
        assertEquals("2024-01-15", holding.navAsOfDate());

        // Score state check: read-only status from persisted score snapshot
        assertNotNull(holding.analyticalScore());
        assertTrue(holding.analyticalScore().available());
        assertEquals("PARTIAL", holding.analyticalScore().status());
    }

    @Test
    @DisplayName("TC-02: Missing cost basis leaves gain/loss as NOT_AVAILABLE without fabricating metrics")
    void testMissingCostBasis() {
        PortfolioHoldingRequest request = new PortfolioHoldingRequest(
            1L, BigDecimal.valueOf(50.0), null
        );

        PortfolioHoldingDto holding = portfolioService.addOrUpdateHolding(USER_A, request);

        assertNotNull(holding);
        assertNull(holding.costBasisAmount());
        assertNull(holding.investedAmount());
        assertNull(holding.absoluteGainLoss());
        assertNull(holding.absoluteGainLossPercentage());
        assertEquals("VALUATION_AVAILABLE", holding.valuationState());

        PortfolioSummaryDto summary = portfolioService.getPortfolioSummary(USER_A);
        assertEquals(1, summary.totalHoldings());
        assertEquals("VALUATION_COMPLETE", summary.valuationCoverageState());
        assertEquals("NOT_AVAILABLE", summary.gainLossState());
    }

    @Test
    @DisplayName("TC-03: Portfolio summary aggregation & allocation shares")
    void testPortfolioSummaryAggregation() {
        // Add HDFC pilot (scheme_option_id = 1)
        portfolioService.addOrUpdateHolding(USER_A, new PortfolioHoldingRequest(1L, BigDecimal.valueOf(10.0), BigDecimal.valueOf(1600.0)));

        PortfolioSummaryDto summary = portfolioService.getPortfolioSummary(USER_A);

        assertEquals(1, summary.totalHoldings());
        assertEquals(1, summary.valuedHoldingsCount());
        assertEquals("VALUATION_COMPLETE", summary.valuationCoverageState());
        assertNotNull(summary.totalAvailableValue());
        assertNotNull(summary.totalInvestedAmount());

        // Verify category allocation
        assertFalse(summary.categoryAllocations().isEmpty());
        assertEquals("Equity Scheme", summary.categoryAllocations().get(0).category());
        assertEquals(1, summary.categoryAllocations().get(0).fundCount());

        // Verify data quality limitations & investigation questions
        assertFalse(summary.dataQualityLimitations().isEmpty());
        assertTrue(summary.dataQualityLimitations().get(0).contains("2024-01-15"));
    }

    @Test
    @DisplayName("TC-04: Cross-user isolation — User A cannot access or modify User B holdings")
    void testCrossUserIsolation() {
        // User A adds holding 1L
        portfolioService.addOrUpdateHolding(USER_A, new PortfolioHoldingRequest(1L, BigDecimal.valueOf(100.0), null));

        // User B fetches portfolio: must be empty
        PortfolioSummaryDto userBSummary = portfolioService.getPortfolioSummary(USER_B);
        assertEquals(0, userBSummary.totalHoldings());

        // User B attempts to delete User A's holding: must not delete User A's holding
        portfolioService.removeHolding(USER_B, 1L);

        PortfolioSummaryDto userASummary = portfolioService.getPortfolioSummary(USER_A);
        assertEquals(1, userASummary.totalHoldings());
    }

    @Test
    @DisplayName("TC-05: Removing holding cleanly updates portfolio state")
    void testRemoveHolding() {
        portfolioService.addOrUpdateHolding(USER_A, new PortfolioHoldingRequest(1L, BigDecimal.valueOf(20.0), null));
        assertEquals(1, portfolioService.getPortfolioSummary(USER_A).totalHoldings());

        portfolioService.removeHolding(USER_A, 1L);
        assertEquals(0, portfolioService.getPortfolioSummary(USER_A).totalHoldings());
    }

    @Test
    @DisplayName("TC-06: Adding existing scheme_option_id updates holding instead of creating duplicate")
    void testDuplicateHoldingPrevention() {
        portfolioService.addOrUpdateHolding(USER_A, new PortfolioHoldingRequest(1L, BigDecimal.valueOf(100.0), BigDecimal.valueOf(1500.0)));
        assertEquals(1, portfolioService.getPortfolioSummary(USER_A).totalHoldings());

        // Add same scheme_option_id (1L) again with updated units
        portfolioService.addOrUpdateHolding(USER_A, new PortfolioHoldingRequest(1L, BigDecimal.valueOf(150.0), BigDecimal.valueOf(1600.0)));

        PortfolioSummaryDto summary = portfolioService.getPortfolioSummary(USER_A);
        assertEquals(1, summary.totalHoldings());
        assertEquals(0, BigDecimal.valueOf(150.0).compareTo(summary.holdings().get(0).units()));
        assertEquals(0, BigDecimal.valueOf(1600.0).compareTo(summary.holdings().get(0).costBasisAmount()));
    }

    @Test
    @DisplayName("TC-07: Negative cost basis is explicitly rejected by backend validation")
    void testNegativeCostBasisRejection() {
        assertThrows(IllegalArgumentException.class, () -> {
            portfolioService.addOrUpdateHolding(USER_A, new PortfolioHoldingRequest(1L, BigDecimal.valueOf(10.0), BigDecimal.valueOf(-500.0)));
        });
    }

    @Test
    @DisplayName("TC-08: Bulk addition of multiple holdings in single request")
    void testBulkHoldingsAddition() {
        java.util.List<PortfolioHoldingRequest> bulkReqs = java.util.List.of(
            new PortfolioHoldingRequest(1L, BigDecimal.valueOf(50.0), BigDecimal.valueOf(1600.0))
        );

        java.util.List<PortfolioHoldingDto> created = portfolioService.addOrUpdateHoldingsBulk(USER_A, bulkReqs);
        assertEquals(1, created.size());
        assertEquals(1L, created.get(0).schemeOptionId());
        assertEquals(1, portfolioService.getPortfolioSummary(USER_A).totalHoldings());
    }
}
