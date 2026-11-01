package com.yukira.backend.universe;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-data integration verification for TSK-M1-08 Investor Comparative Screener service.
 *
 * Verifies live execution against authoritative PostgreSQL database records:
 * 1. Derivation of 193 reference-universe options across all 5 categories.
 * 2. History sufficiency partition: exactly 126 options with >= 700 trading days, 67 partial (< 700), 0 zero.
 * 3. Accurate category breakdown matching reference universe definitions.
 * 4. Preserved candidate methodology governance status for all 5 category benchmarks.
 * 5. Presence of authoritative metric definitions for all 8 canonical screener metrics.
 * 6. Explicit exclusion reasons for ineligible / partial funds.
 * 7. Category and eligibility filtering behavior.
 */
@SpringBootTest
@Transactional
class UniverseScreenerRealDataIntegrationTest {

    @Autowired
    private UniverseScreenerService screenerService;

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 10, 1);

    @BeforeEach
    void setUp() {
        pilotBootstrapService.ensureCanonicalPilotMaster();
    }

    @Test
    @DisplayName("Real-Data: Screener service retrieves 193-option dataset with 126 eligible and 67 partial options")
    void testRealDataScreenerDatasetIntegrity() {
        UniverseScreenerDto.ScreenerResponse response = screenerService.getScreenerData(null, false, AS_OF_DATE);

        assertNotNull(response);
        assertEquals(AS_OF_DATE, response.asOfDate());
        assertEquals(193, response.totalReferenceOptions(), "Total reference options must equal 193");
        assertEquals(126, response.allTimeSufficientCount(), "All-time sufficient options (>= 700 dates across all history) must equal 126");
        assertEquals(67, response.allTimePartialCount(), "All-time partial options (< 700 dates across all history) must equal 67");
        assertEquals(23, response.threeYearSufficientCount(), "3-year sufficient options (>= 700 dates in 3Y window) must equal 23");
        assertEquals(23, response.eligibleOptionsCount(), "Eligible options (3Y window >= 700 dates) must equal 23");
        assertEquals(170, response.partialOptionsCount(), "Partial options (3Y window < 700 dates) must equal 170");
        assertEquals(0, response.zeroHistoryCount(), "Zero-history options must equal 0");
        assertEquals(193, response.items().size(), "Unfiltered response must return all 193 reference options");

        // Category Summaries Verification (Strict 3Y Window Sufficiency)
        Map<String, UniverseScreenerDto.CategorySummary> catSums = response.categorySummaries();
        assertNotNull(catSums);
        assertEquals(5, catSums.size(), "Must cover all 5 equity reference categories");

        assertEquals(47, catSums.get("FLEXI_CAP").totalOptions());
        assertEquals(3, catSums.get("FLEXI_CAP").eligibleCount());
        assertEquals(44, catSums.get("FLEXI_CAP").partialCount());
        assertEquals("NIFTY_500_TRI", catSums.get("FLEXI_CAP").candidateBenchmarkCode());

        assertEquals(39, catSums.get("LARGE_CAP").totalOptions());
        assertEquals(6, catSums.get("LARGE_CAP").eligibleCount());
        assertEquals(33, catSums.get("LARGE_CAP").partialCount());
        assertEquals("NIFTY_100_TRI", catSums.get("LARGE_CAP").candidateBenchmarkCode());

        assertEquals(36, catSums.get("LARGE_AND_MID_CAP").totalOptions());
        assertEquals(6, catSums.get("LARGE_AND_MID_CAP").eligibleCount());
        assertEquals(30, catSums.get("LARGE_AND_MID_CAP").partialCount());
        assertEquals("NIFTY_LARGEMIDCAP_250_TRI", catSums.get("LARGE_AND_MID_CAP").candidateBenchmarkCode());

        assertEquals(34, catSums.get("MID_CAP").totalOptions());
        assertEquals(4, catSums.get("MID_CAP").eligibleCount());
        assertEquals(30, catSums.get("MID_CAP").partialCount());
        assertEquals("NIFTY_MIDCAP_150_TRI", catSums.get("MID_CAP").candidateBenchmarkCode());

        assertEquals(37, catSums.get("SMALL_CAP").totalOptions());
        assertEquals(4, catSums.get("SMALL_CAP").eligibleCount());
        assertEquals(33, catSums.get("SMALL_CAP").partialCount());
        assertEquals("NIFTY_SMALLCAP_250_TRI", catSums.get("SMALL_CAP").candidateBenchmarkCode());

        // Metric Definitions Verification
        assertEquals(8, response.metricDefinitions().size());
        for (UniverseScreenerDto.MetricDefinitionDto def : response.metricDefinitions()) {
            assertNotNull(def.metricCode());
            assertNotNull(def.metricName());
            assertNotNull(def.category());
            assertNotNull(def.units());
            assertNotNull(def.evaluationWindow());
            assertNotNull(def.formulaDescription());
        }

        // Governance Disclaimers Verification
        assertFalse(response.governanceDisclaimers().isEmpty());
        assertTrue(response.governanceDisclaimers().stream()
            .anyMatch(d -> d.contains("CANDIDATE_METHODOLOGY_MAPPING") || d.contains("candidate methodology")));
    }

    @Test
    @DisplayName("Real-Data: Screener filters by category and analytical eligibility correctly")
    void testRealDataScreenerFiltering() {
        // Filter by LARGE_CAP only
        UniverseScreenerDto.ScreenerResponse largeCapOnly =
            screenerService.getScreenerData(EquityUniverseCategory.LARGE_CAP, false, AS_OF_DATE);
        assertEquals(39, largeCapOnly.items().size(), "Large Cap category filter must return 39 options");
        for (UniverseScreenerDto.ScreenerItem item : largeCapOnly.items()) {
            assertEquals(EquityUniverseCategory.LARGE_CAP, item.category());
            assertEquals("NIFTY_100_TRI", item.candidateBenchmarkCode());
            assertEquals("CANDIDATE_METHODOLOGY_MAPPING", item.benchmarkGovernanceStatus());
        }

        // Filter by Eligible (>= 700 trading days in 3Y window) only
        UniverseScreenerDto.ScreenerResponse eligibleOnly =
            screenerService.getScreenerData(null, true, AS_OF_DATE);
        assertEquals(23, eligibleOnly.items().size(), "Eligible-only filter must return exactly 23 options with 3Y window sufficiency");
        for (UniverseScreenerDto.ScreenerItem item : eligibleOnly.items()) {
            assertTrue(item.isEligibleFor3Y());
            assertTrue(item.distinctNavDates() >= 700);
            assertNull(item.exclusionReason());
            assertNotNull(item.dataFreshnessStatus());
            assertNotNull(item.latestNavDate());
        }

        // Verify partial fund has an explicit exclusion reason
        UniverseScreenerDto.ScreenerResponse allResponse =
            screenerService.getScreenerData(null, false, AS_OF_DATE);
        UniverseScreenerDto.ScreenerItem partialItem = allResponse.items().stream()
            .filter(i -> !i.isEligibleFor3Y())
            .findFirst()
            .orElse(null);

        assertNotNull(partialItem, "At least one partial option must exist");
        assertNotNull(partialItem.exclusionReason());
        assertTrue(partialItem.exclusionReason().contains("700 required"));
    }

    @Test
    @DisplayName("Real-Data: TSK-M4-01 category percentiles are deterministically enriched with ECDF midpoint formula")
    void testRealDataCategoryPercentileRankings() {
        UniverseScreenerDto.ScreenerResponse response = screenerService.getScreenerData(null, false, AS_OF_DATE);
        assertNotNull(response);

        // Verify Metric Definitions contain rankingDirection and percentileInterpretation
        for (UniverseScreenerDto.MetricDefinitionDto def : response.metricDefinitions()) {
            assertNotNull(def.rankingDirection(), "Metric " + def.metricCode() + " must have rankingDirection");
            assertNotNull(def.percentileInterpretation(), "Metric " + def.metricCode() + " must have percentileInterpretation");
        }

        // Check Flexi Cap calculated items (at least 10 calculated funds in database)
        long calculatedFlexi = response.items().stream()
            .filter(i -> i.category() == EquityUniverseCategory.FLEXI_CAP && "CALCULATED".equals(i.calculationStatus()))
            .count();
        assertTrue(calculatedFlexi >= 10, "Flexi Cap must have at least 10 calculated funds, found: " + calculatedFlexi);

        for (UniverseScreenerDto.ScreenerItem item : response.items()) {
            if ("CALCULATED".equals(item.calculationStatus())) {
                UniverseScreenerDto.MetricValueDto ret02 = item.metrics().get("RET-02");
                assertNotNull(ret02, "Calculated item must have RET-02");
                assertNotNull(ret02.numericValue(), "RET-02 must have numericValue");

                int N = ret02.peerDenominator();
                assertTrue(N >= 10, "Peer denominator must be at least 10, found: " + N);
                assertEquals("HIGHER_IS_BETTER", ret02.rankingDirection());

                if (N < 15) {
                    assertEquals("CONFIDENCE_LIMITED_SMALL_COHORT", ret02.peerCohortStatus());
                    assertNotNull(ret02.unavailableReason());
                    assertTrue(ret02.unavailableReason().contains("Confidence Limited: Small Peer Cohort"));
                } else {
                    assertEquals("PRODUCTION_CALIBRATED", ret02.peerCohortStatus());
                    assertNull(ret02.unavailableReason(), "Production calibrated metric must not have unavailableReason warning");
                }

                assertNotNull(ret02.percentileRank(), "Percentile rank must not be null");
                assertTrue(ret02.percentileRank() > 0.0 && ret02.percentileRank() < 100.0,
                    "Percentile rank must be within (0, 100)");
                assertNotNull(ret02.formattedPercentile(), "Formatted percentile must not be null");
                assertTrue(ret02.formattedPercentile().matches("P\\d{2}"),
                    "Formatted percentile must match Pxx pattern: " + ret02.formattedPercentile());
            } else {
                // Uncalculated funds must have null percentile or INSUFFICIENT_DATA
                UniverseScreenerDto.MetricValueDto ret02 = item.metrics().get("RET-02");
                if (ret02 != null) {
                    assertNull(ret02.percentileRank());
                    assertNull(ret02.formattedPercentile());
                }
            }
        }
    }
}
