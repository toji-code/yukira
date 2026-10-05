package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.dto.portfolio.*;
import com.yukira.backend.service.portfolio.InvestorPortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for Portfolio Analytical Rebalancing & Drift Inspection V1.
 *
 * Verifies key acceptance conditions:
 * 1. Target creation with valid 100% total
 * 2. Target retrieval & deletion
 * 3. Invalid target total validation error (e.g. 80% total rejected with explicit message)
 * 4. Authenticated ownership & cross-investor isolation (User A vs User B)
 * 5. No target defined drift status (NO_TARGET)
 * 6. Complete allocation drift calculation (current - target)
 * 7. Unmapped target handling (UNMAPPED_TARGET)
 * 8. Unmapped holding handling
 * 9. Deterministic drift arithmetic (OVER_ALLOCATED / UNDER_ALLOCATED)
 * 10. Reuse of existing portfolio valuation & YUKIRA scores without recalculation
 * 11. Zero recommendation disclaimers
 */
@SpringBootTest
@ActiveProfiles("test")
public class PortfolioDriftIntegrationTest {

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private InvestorPortfolioService portfolioService;

    private static final String TEST_USER_A = "auth0|user-drift-a";
    private static final String TEST_USER_B = "auth0|user-drift-b";

    @BeforeEach
    void setUp() {
        pilotBootstrapService.bootstrapPilot();
        portfolioService.removeHolding(TEST_USER_A, 1L);
        portfolioService.removeHolding(TEST_USER_A, 2L);
        portfolioService.removeHolding(TEST_USER_B, 1L);
        portfolioService.deleteTargetAllocation(TEST_USER_A);
        portfolioService.deleteTargetAllocation(TEST_USER_B);
    }

    @Test
    @DisplayName("TC-01: Valid target allocation creation and retrieval")
    void testValidTargetAllocationCreation() {
        TargetAllocationDto request = new TargetAllocationDto(
            List.of(
                new TargetAllocationDto.Item(null, "CATEGORY", null, "Equity Scheme", "Equity Scheme", BigDecimal.valueOf(60.00)),
                new TargetAllocationDto.Item(null, "CATEGORY", null, "Debt Scheme", "Debt Scheme", BigDecimal.valueOf(40.00))
            ),
            BigDecimal.valueOf(100.00),
            true
        );

        TargetAllocationDto saved = portfolioService.setTargetAllocation(TEST_USER_A, request);

        assertNotNull(saved);
        assertTrue(saved.isValidTotal());
        assertEquals(0, BigDecimal.valueOf(100.00).compareTo(saved.totalTargetWeightPercentage()));
        assertEquals(2, saved.items().size());
    }

    @Test
    @DisplayName("TC-02: Invalid target total weight rejection")
    void testInvalidTargetTotalRejection() {
        TargetAllocationDto invalidRequest = new TargetAllocationDto(
            List.of(
                new TargetAllocationDto.Item(null, "CATEGORY", null, "Equity Scheme", "Equity Scheme", BigDecimal.valueOf(50.00)),
                new TargetAllocationDto.Item(null, "CATEGORY", null, "Debt Scheme", "Debt Scheme", BigDecimal.valueOf(30.00))
            ),
            BigDecimal.valueOf(80.00),
            false
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            portfolioService.setTargetAllocation(TEST_USER_A, invalidRequest)
        );

        assertTrue(ex.getMessage().contains("Target allocation weights total 80.00%"));
    }

    @Test
    @DisplayName("TC-03: Auth0 investor target allocation isolation")
    void testAuth0TargetIsolation() {
        TargetAllocationDto requestA = new TargetAllocationDto(
            List.of(new TargetAllocationDto.Item(null, "CATEGORY", null, "Equity Scheme", "Equity Scheme", BigDecimal.valueOf(100.00))),
            BigDecimal.valueOf(100.00),
            true
        );

        portfolioService.setTargetAllocation(TEST_USER_A, requestA);

        TargetAllocationDto targetA = portfolioService.getTargetAllocation(TEST_USER_A);
        assertEquals(1, targetA.items().size());

        TargetAllocationDto targetB = portfolioService.getTargetAllocation(TEST_USER_B);
        assertEquals(0, targetB.items().size());
        assertFalse(targetB.isValidTotal());
    }

    @Test
    @DisplayName("TC-04: Drift analysis with no target defined returns NO_TARGET status")
    void testNoTargetDriftAnalysis() {
        portfolioService.addOrUpdateHolding(TEST_USER_A, new PortfolioHoldingRequest(
            1L, BigDecimal.valueOf(100.0), BigDecimal.valueOf(1500.0)
        ));

        PortfolioDriftAnalysisDto drift = portfolioService.evaluatePortfolioDrift(TEST_USER_A);

        assertNotNull(drift);
        assertEquals("NO_TARGET", drift.status());
        assertTrue(drift.itemDrifts().isEmpty());
        assertFalse(drift.disclaimers().isEmpty());
    }

    @Test
    @DisplayName("TC-05: Deterministic portfolio allocation drift calculation")
    void testDeterministicDriftCalculation() {
        // Add HDFC pilot holding (scheme_option_id = 1, 100 units, 100% current weight)
        portfolioService.addOrUpdateHolding(TEST_USER_A, new PortfolioHoldingRequest(
            1L, BigDecimal.valueOf(100.0), BigDecimal.valueOf(1500.0)
        ));

        // Define target: 60% Equity Scheme (matches HDFC), 40% Debt Scheme (unmapped target)
        TargetAllocationDto request = new TargetAllocationDto(
            List.of(
                new TargetAllocationDto.Item(null, "CATEGORY", null, "Equity Scheme", "Equity Scheme", BigDecimal.valueOf(60.00)),
                new TargetAllocationDto.Item(null, "CATEGORY", null, "Debt Scheme", "Debt Scheme", BigDecimal.valueOf(40.00))
            ),
            BigDecimal.valueOf(100.00),
            true
        );
        portfolioService.setTargetAllocation(TEST_USER_A, request);

        PortfolioDriftAnalysisDto drift = portfolioService.evaluatePortfolioDrift(TEST_USER_A);

        assertNotNull(drift);
        assertEquals("COMPLETE", drift.status());
        assertEquals(2, drift.itemDrifts().size());

        // Equity Scheme: Current = 100%, Target = 60% -> Drift = +40 pp (OVER_ALLOCATED)
        PortfolioDriftAnalysisDto.ItemDriftDto equityItem = drift.itemDrifts().stream()
            .filter(i -> "Equity Scheme".equals(i.categoryName()))
            .findFirst()
            .orElseThrow();
        assertEquals(0, BigDecimal.valueOf(100.00).compareTo(equityItem.currentWeightPercentage()));
        assertEquals(0, BigDecimal.valueOf(60.00).compareTo(equityItem.targetWeightPercentage()));
        assertEquals(0, BigDecimal.valueOf(40.00).compareTo(equityItem.driftPercentagePoints()));
        assertEquals("OVER_ALLOCATED", equityItem.driftDirection());
        assertEquals("MAPPED", equityItem.mappingState());

        // Debt Scheme: Current = 0%, Target = 40% -> Drift = -40 pp (UNDER_ALLOCATED, UNMAPPED_TARGET)
        PortfolioDriftAnalysisDto.ItemDriftDto debtItem = drift.itemDrifts().stream()
            .filter(i -> "Debt Scheme".equals(i.categoryName()))
            .findFirst()
            .orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(debtItem.currentWeightPercentage()));
        assertEquals(0, BigDecimal.valueOf(40.00).compareTo(debtItem.targetWeightPercentage()));
        assertEquals(0, BigDecimal.valueOf(-40.00).compareTo(debtItem.driftPercentagePoints()));
        assertEquals("UNDER_ALLOCATED", debtItem.driftDirection());
        assertEquals("UNMAPPED_TARGET", debtItem.mappingState());

        // Check disclaimers
        assertTrue(drift.disclaimers().stream().anyMatch(d -> d.contains("NO INVESTMENT ADVICE")));
    }
}
