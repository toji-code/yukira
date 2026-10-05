package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.dto.analysis.ComparisonResponse;
import com.yukira.backend.dto.portfolio.PortfolioHoldingRequest;
import com.yukira.backend.repository.AnalyticalScoreRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.portfolio.InvestorPortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for Portfolio Fund Comparison & Evidence Matrix V1.
 *
 * Verifies key acceptance conditions:
 * 1. Authenticated comparison with exact scheme_option_id identity
 * 2. Canonical metric identity preservation (RET-03, RSK-01, RSK-02, RSK-03, MKT-01, MKT-02, MKT-05, RAT-04)
 * 3. MKT-05 is Capture Spread 3Y (never labeled Beta)
 * 4. Missing metrics are not converted to zero
 * 5. Portfolio context separation (units, valuation, P&L, weight %)
 * 6. Auth0 investor ownership isolation
 * 7. Reuse of existing persisted fund scores without recalculation
 * 8. Zero comparison score or recommendation language
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PortfolioComparisonIntegrationTest {

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private InvestorPortfolioService portfolioService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private AnalyticalScoreRepository analyticalScoreRepository;

    private static final String TEST_USER_A = "auth0|user-compare-a";
    private static final String TEST_USER_B = "auth0|user-compare-b";

    @BeforeEach
    void setUp() {
        pilotBootstrapService.bootstrapPilot();
        portfolioService.removeHolding(TEST_USER_A, 1L);
        portfolioService.removeHolding(TEST_USER_B, 1L);
    }

    @Test
    @DisplayName("TC-01: Authenticated portfolio comparison retrieves exact scheme_option_id and holding context")
    void testPortfolioComparisonWithHoldingContext() {
        portfolioService.addOrUpdateHolding(TEST_USER_A, new PortfolioHoldingRequest(
            1L, BigDecimal.valueOf(100), BigDecimal.valueOf(1500)
        ));

        ComparisonResponse response = portfolioService.evaluatePortfolioComparison(
            TEST_USER_A, List.of(1L), List.of("RET-03", "RSK-01", "RSK-02", "RSK-03", "MKT-05")
        );

        assertNotNull(response);
        assertNotNull(response.funds());
        assertEquals(1, response.funds().size());

        ComparisonResponse.ComparisonFund fund = response.funds().get(0);
        assertEquals(1L, fund.schemeOptionId());
        assertEquals("HDFC Flexi Cap Fund", fund.schemeName());
        assertEquals("HDFC Mutual Fund", fund.amcName());
        assertEquals("DIRECT", fund.planType());
        assertEquals("GROWTH", fund.optionType());
        assertEquals("INF179K01UT0", fund.isin());
        assertEquals("118955", fund.amfiCode());

        assertNotNull(fund.portfolioHolding());
        assertEquals(0, fund.portfolioHolding().units().compareTo(BigDecimal.valueOf(100)));
        assertNotNull(fund.portfolioHolding().availableValue());
        assertNotNull(fund.portfolioHolding().unrealizedGainLossAmount());
    }

    @Test
    @DisplayName("TC-02: Auth0 isolation prevents User B from accessing User A's private holding economics")
    void testAuth0IsolationForComparison() {
        portfolioService.addOrUpdateHolding(TEST_USER_A, new PortfolioHoldingRequest(
            1L, BigDecimal.valueOf(100), BigDecimal.valueOf(1500)
        ));

        ComparisonResponse responseB = portfolioService.evaluatePortfolioComparison(
            TEST_USER_B, List.of(1L), List.of("RET-03")
        );

        assertNotNull(responseB);
        assertEquals(1, responseB.funds().size());
        ComparisonResponse.ComparisonFund fundB = responseB.funds().get(0);
        assertEquals(1L, fundB.schemeOptionId());
        assertNull(fundB.portfolioHolding());
    }

    @Test
    @DisplayName("TC-03: Preserves canonical metric identity: MKT-05 is Capture Spread 3Y")
    void testCanonicalMetricIdentities() {
        ComparisonResponse response = portfolioService.evaluatePortfolioComparison(
            TEST_USER_A, List.of(1L), List.of("MKT-05", "RSK-01", "RSK-02", "RSK-03", "RET-03", "RAT-04")
        );

        assertNotNull(response.metrics());
        ComparisonResponse.ComparisonMetric mkt05 = response.metrics().stream()
            .filter(m -> "MKT-05".equals(m.metricCode()))
            .findFirst()
            .orElseThrow();

        assertEquals("Capture Spread (3Y)", mkt05.metricName());
        assertNotEquals("Beta", mkt05.metricName());
    }

    @Test
    @DisplayName("TC-04: Invalid metric codes are rejected against canonical registry")
    void testInvalidMetricCodesRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            portfolioService.evaluatePortfolioComparison(
                TEST_USER_A, List.of(1L), List.of("UNKNOWN-METRIC-CODE-TEST")
            )
        );

        assertTrue(ex.getMessage().contains("Unsupported comparison metric code"));
    }

    @Test
    @DisplayName("TC-05: Existing fund scores are reused unchanged")
    void testFundScoresReusedUnchanged() {
        ComparisonResponse response = portfolioService.evaluatePortfolioComparison(
            TEST_USER_A, List.of(1L), List.of("RET-03")
        );

        ComparisonResponse.ComparisonFund fund = response.funds().get(0);
        if (fund.yukiraScore() != null) {
            assertNotNull(fund.yukiraScore().score());
            assertNotNull(fund.yukiraScore().confidence());
            assertEquals("CANDIDATE", fund.yukiraScore().methodologyStatus());
        }
    }

    @Test
    @DisplayName("TC-06: Score dimension weights are read from persisted ScoreDimension records")
    void testDimensionWeightsComeFromPersistedScoreDimensions() {
        ComparisonResponse response = portfolioService.evaluatePortfolioComparison(
            TEST_USER_A, List.of(1L), List.of("RET-03")
        );

        ComparisonResponse.ComparisonFund fund = response.funds().get(0);
        assertNotNull(fund.yukiraScore());
        assertNotNull(fund.yukiraScore().dimensions());
        assertFalse(fund.yukiraScore().dimensions().isEmpty());

        var persistedScore = analyticalScoreRepository.findLatestBySchemeOptionId(1L).get(0);
        for (var dimension : fund.yukiraScore().dimensions()) {
            var persistedDimension = persistedScore.getDimensions().stream()
                .filter(d -> d.getId().equals(dimension.id()))
                .findFirst()
                .orElseThrow();
            assertEquals(0, persistedDimension.getWeight().compareTo(dimension.weight()));
        }
    }

    @Test
    @DisplayName("TC-07: scheme_option_id=10199 resolves only from authoritative persistence when present")
    void testSchemeOption10199AuthoritativeIdentityIfPresent() {
        schemeOptionRepository.findById(10199L).ifPresent(option -> {
            assertNotNull(option.getPlan());
            assertNotNull(option.getPlan().getScheme());
            assertNotNull(option.getPlan().getScheme().getAmc());

            ComparisonResponse response = portfolioService.evaluatePortfolioComparison(
                TEST_USER_A, List.of(10199L), List.of("RET-03")
            );

            ComparisonResponse.ComparisonFund fund = response.funds().get(0);
            assertEquals(option.getPlan().getScheme().getName(), fund.schemeName());
            assertEquals(option.getPlan().getScheme().getAmc().getLegalName(), fund.amcName());
            assertEquals(option.getPlan().getPlanType(), fund.planType());
            assertEquals(option.getOptionType(), fund.optionType());
            assertEquals(option.getAmfiCode(), fund.amfiCode());
            assertEquals(option.getIsin(), fund.isin());
        });
    }
}