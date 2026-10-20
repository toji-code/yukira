package com.yukira.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Universe NAV Coverage Audit — enforces the canonical denominator distinction:
 *
 * <p>ACTIVE_UNIVERSE  = scheme_option WHERE status = 'ACTIVE'
 * TOTAL_CATALOG     = all scheme_option rows
 *
 * <p>All investor-facing analytical readiness metrics MUST use ACTIVE_UNIVERSE as denominator.
 * TOTAL_CATALOG is reported separately for data completeness only.
 *
 * <p>This distinction is a regression guardrail: a metric calculated over the total catalog
 * (13,535+ rows) would significantly understate readiness versus the active universe (9,127 rows).
 */
@SpringBootTest
@ActiveProfiles("test")
class UniverseNavCoverageAuditTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // =========================================================================
    // CANONICAL COVERAGE DEFINITIONS (A–F)
    // =========================================================================

    /** A. ACTIVE_UNIVERSE: all scheme options currently marked ACTIVE. Investor-facing denominator. */
    private static final String SQL_ACTIVE_UNIVERSE =
        "SELECT count(*) FROM scheme_option WHERE status = 'ACTIVE'";

    /** B. TOTAL_CATALOG_UNIVERSE: all scheme option rows regardless of status. */
    private static final String SQL_TOTAL_CATALOG =
        "SELECT count(*) FROM scheme_option";

    /** C. ACTIVE_WITH_NAV: active options that have at least one NAV observation. */
    private static final String SQL_ACTIVE_WITH_NAV =
        "SELECT count(DISTINCT n.scheme_option_id) FROM nav_observation n " +
        "JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE'";

    /** D. ACTIVE_1Y_READY: active options whose NAV span (max-min) >= 365 days. */
    private static final String SQL_ACTIVE_1Y =
        "SELECT count(*) FROM (SELECT n.scheme_option_id FROM nav_observation n " +
        "JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' " +
        "GROUP BY n.scheme_option_id HAVING (max(n.effective_date) - min(n.effective_date)) >= 365) t";

    /** E. ACTIVE_3Y_READY: active options whose NAV span >= 1095 days. */
    private static final String SQL_ACTIVE_3Y =
        "SELECT count(*) FROM (SELECT n.scheme_option_id FROM nav_observation n " +
        "JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' " +
        "GROUP BY n.scheme_option_id HAVING (max(n.effective_date) - min(n.effective_date)) >= 1095) t";

    /** F. ACTIVE_5Y_READY: active options whose NAV span >= 1825 days. */
    private static final String SQL_ACTIVE_5Y =
        "SELECT count(*) FROM (SELECT n.scheme_option_id FROM nav_observation n " +
        "JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' " +
        "GROUP BY n.scheme_option_id HAVING (max(n.effective_date) - min(n.effective_date)) >= 1825) t";

    // =========================================================================
    // TEST 1: Full audit report (reporting test — always passes if DB is reachable)
    // =========================================================================

    @Test
    @DisplayName("Full universe and NAV coverage audit report")
    void runUniverseAndNavCoverageAudit() {
        System.out.println("==================================================");
        System.out.println("PHASE 0 & PHASE 8 — REAL DATABASE COVERAGE AUDIT");
        System.out.println("==================================================");

        Long amcCount = jdbcTemplate.queryForObject("SELECT count(*) FROM amc", Long.class);
        Long schemeCount = jdbcTemplate.queryForObject("SELECT count(*) FROM scheme", Long.class);
        Long schemePlanCount = jdbcTemplate.queryForObject("SELECT count(*) FROM scheme_plan", Long.class);
        Long schemeOptionCount = jdbcTemplate.queryForObject(SQL_TOTAL_CATALOG, Long.class);
        Long activeOptionCount = jdbcTemplate.queryForObject(SQL_ACTIVE_UNIVERSE, Long.class);
        Long inactiveOptionCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM scheme_option WHERE status <> 'ACTIVE'", Long.class);

        Long totalNavObservations = jdbcTemplate.queryForObject("SELECT count(*) FROM nav_observation", Long.class);
        Long distinctOptionsWithNav = jdbcTemplate.queryForObject(
            "SELECT count(DISTINCT scheme_option_id) FROM nav_observation", Long.class);
        Long activeOptionsWithNav = jdbcTemplate.queryForObject(SQL_ACTIVE_WITH_NAV, Long.class);
        Long activeZeroNav = activeOptionCount - activeOptionsWithNav;

        String minNavDate = jdbcTemplate.queryForObject("SELECT min(effective_date) FROM nav_observation", String.class);
        String maxNavDate = jdbcTemplate.queryForObject("SELECT max(effective_date) FROM nav_observation", String.class);

        Long missingIsin = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM scheme_option WHERE isin IS NULL", Long.class);

        Long directOptionsWithNav = jdbcTemplate.queryForObject(
            "SELECT count(DISTINCT so.id) FROM scheme_option so " +
            "JOIN scheme_plan sp ON so.plan_id = sp.id " +
            "JOIN nav_observation n ON n.scheme_option_id = so.id " +
            "WHERE sp.plan_type = 'DIRECT'", Long.class);
        Long regularOptionsWithNav = jdbcTemplate.queryForObject(
            "SELECT count(DISTINCT so.id) FROM scheme_option so " +
            "JOIN scheme_plan sp ON so.plan_id = sp.id " +
            "JOIN nav_observation n ON n.scheme_option_id = so.id " +
            "WHERE sp.plan_type = 'REGULAR'", Long.class);
        Long growthOptionsWithNav = jdbcTemplate.queryForObject(
            "SELECT count(DISTINCT so.id) FROM scheme_option so " +
            "JOIN nav_observation n ON n.scheme_option_id = so.id " +
            "WHERE so.option_type = 'GROWTH'", Long.class);
        Long idcwOptionsWithNav = jdbcTemplate.queryForObject(
            "SELECT count(DISTINCT so.id) FROM scheme_option so " +
            "JOIN nav_observation n ON n.scheme_option_id = so.id " +
            "WHERE so.option_type LIKE '%IDCW%' OR so.option_type LIKE '%DIVIDEND%'", Long.class);

        // Total-catalog history (for data completeness reporting only)
        Long options1YHistoryTotal = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation " +
            "GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 365) t", Long.class);
        Long options3YHistoryTotal = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation " +
            "GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1095) t", Long.class);
        Long options5YHistoryTotal = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation " +
            "GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1825) t", Long.class);

        // Active-universe history (canonical investor-facing metrics)
        Long active1Y = jdbcTemplate.queryForObject(SQL_ACTIVE_1Y, Long.class);
        Long active3Y = jdbcTemplate.queryForObject(SQL_ACTIVE_3Y, Long.class);
        Long active5Y = jdbcTemplate.queryForObject(SQL_ACTIVE_5Y, Long.class);

        System.out.println("AUDIT_SUMMARY_START");
        System.out.println("AMC Count: " + amcCount);
        System.out.println("Scheme Count: " + schemeCount);
        System.out.println("Scheme Plan Count: " + schemePlanCount);

        System.out.println("\n--- UNIVERSE DENOMINATORS ---");
        System.out.println("B. TOTAL_CATALOG_UNIVERSE (all scheme_option rows): " + schemeOptionCount);
        System.out.println("A. ACTIVE_UNIVERSE (investor-facing denominator): " + activeOptionCount);
        System.out.println("   Inactive Options: " + inactiveOptionCount);

        System.out.println("\n--- ACTIVE UNIVERSE COVERAGE (denominator = ACTIVE_UNIVERSE) ---");
        System.out.printf("C. ACTIVE_WITH_NAV: %d (%.2f%% of active)%n",
            activeOptionsWithNav, pct(activeOptionsWithNav, activeOptionCount));
        System.out.printf("   ACTIVE_ZERO_NAV:  %d (%.2f%% of active)%n",
            activeZeroNav, pct(activeZeroNav, activeOptionCount));
        System.out.printf("D. ACTIVE_1Y_READY: %d (%.2f%% of active)%n",
            active1Y, pct(active1Y, activeOptionCount));
        System.out.printf("E. ACTIVE_3Y_READY: %d (%.2f%% of active)%n",
            active3Y, pct(active3Y, activeOptionCount));
        System.out.printf("F. ACTIVE_5Y_READY: %d (%.2f%% of active)%n",
            active5Y, pct(active5Y, activeOptionCount));

        System.out.println("\n--- TOTAL CATALOG COVERAGE (denominator = TOTAL_CATALOG — NOT for investor metrics) ---");
        System.out.println("Total NAV Observations: " + totalNavObservations);
        System.out.println("Distinct Options with NAV (all statuses): " + distinctOptionsWithNav);
        System.out.println("Options with >= 1Y History (all statuses): " + options1YHistoryTotal);
        System.out.println("Options with >= 3Y History (all statuses): " + options3YHistoryTotal);
        System.out.println("Options with >= 5Y History (all statuses): " + options5YHistoryTotal);

        System.out.println("\n--- SUPPLEMENTAL INFO ---");
        System.out.println("Earliest NAV Date: " + minNavDate);
        System.out.println("Latest NAV Date: " + maxNavDate);
        System.out.println("Missing ISIN: " + missingIsin);
        System.out.println("Direct Options with NAV: " + directOptionsWithNav);
        System.out.println("Regular Options with NAV: " + regularOptionsWithNav);
        System.out.println("Growth Options with NAV: " + growthOptionsWithNav);
        System.out.println("IDCW Options with NAV: " + idcwOptionsWithNav);

        System.out.println("\nTOP 10 AMCS BY NAV OBSERVATIONS:");
        List<Map<String, Object>> amcStats = jdbcTemplate.queryForList(
            "SELECT a.legal_name, count(n.id) as obs_count FROM amc a " +
            "JOIN scheme s ON s.amc_id = a.id " +
            "JOIN scheme_plan sp ON sp.scheme_id = s.id " +
            "JOIN scheme_option so ON so.plan_id = sp.id " +
            "JOIN nav_observation n ON n.scheme_option_id = so.id " +
            "GROUP BY a.legal_name ORDER BY obs_count DESC LIMIT 10");
        for (Map<String, Object> row : amcStats) {
            System.out.println("  - " + row.get("legal_name") + ": " + row.get("obs_count") + " observations");
        }
        System.out.println("AUDIT_SUMMARY_END");
        System.out.println("==================================================");

        assertNotNull(schemeOptionCount);
    }

    // =========================================================================
    // TEST 2: Active-vs-total denominator regression guard
    // =========================================================================

    @Test
    @DisplayName("Active universe must be strictly smaller than or equal to total catalog — denominator regression guard")
    void activeDenominatorMustNotExceedTotalCatalog() {
        Long activeCount = jdbcTemplate.queryForObject(SQL_ACTIVE_UNIVERSE, Long.class);
        Long totalCount = jdbcTemplate.queryForObject(SQL_TOTAL_CATALOG, Long.class);

        assertNotNull(activeCount, "ACTIVE_UNIVERSE count must not be null");
        assertNotNull(totalCount, "TOTAL_CATALOG count must not be null");
        assertTrue(activeCount <= totalCount,
            String.format("ACTIVE_UNIVERSE (%d) must never exceed TOTAL_CATALOG (%d). " +
                "Investor-facing metrics must use the active denominator.", activeCount, totalCount));
        assertTrue(activeCount > 0,
            "ACTIVE_UNIVERSE must be greater than zero — catalog is not empty");
    }

    // =========================================================================
    // TEST 3: 1Y readiness funnel uses active universe denominator
    // =========================================================================

    @Test
    @DisplayName("1Y readiness funnel buckets use ACTIVE_UNIVERSE denominator and are internally consistent")
    void readinessFunnelUsesActiveDenominator() {
        Long activeCount   = jdbcTemplate.queryForObject(SQL_ACTIVE_UNIVERSE, Long.class);
        Long activeWithNav = jdbcTemplate.queryForObject(SQL_ACTIVE_WITH_NAV, Long.class);
        Long active1Y      = jdbcTemplate.queryForObject(SQL_ACTIVE_1Y, Long.class);
        Long active3Y      = jdbcTemplate.queryForObject(SQL_ACTIVE_3Y, Long.class);
        Long active5Y      = jdbcTemplate.queryForObject(SQL_ACTIVE_5Y, Long.class);

        assertNotNull(activeCount);
        assertNotNull(activeWithNav);
        assertNotNull(active1Y);
        assertNotNull(active3Y);
        assertNotNull(active5Y);

        // Funnel invariant: each tier is a strict subset of the one above
        assertTrue(activeWithNav <= activeCount,
            "ACTIVE_WITH_NAV must be <= ACTIVE_UNIVERSE");
        assertTrue(active1Y <= activeWithNav,
            "ACTIVE_1Y_READY must be <= ACTIVE_WITH_NAV (can't be 1Y ready without any NAV)");
        assertTrue(active3Y <= active1Y,
            "ACTIVE_3Y_READY must be <= ACTIVE_1Y_READY (3Y >= 1Y subset)");
        assertTrue(active5Y <= active3Y,
            "ACTIVE_5Y_READY must be <= ACTIVE_3Y_READY (5Y >= 3Y subset)");

        System.out.printf("1Y Readiness Funnel (Active Universe = %d):%n", activeCount);
        System.out.printf("  Any NAV:  %d (%.2f%%)%n", activeWithNav, pct(activeWithNav, activeCount));
        System.out.printf("  >=1Y:     %d (%.2f%%)%n", active1Y,      pct(active1Y,      activeCount));
        System.out.printf("  >=3Y:     %d (%.2f%%)%n", active3Y,      pct(active3Y,      activeCount));
        System.out.printf("  >=5Y:     %d (%.2f%%)%n", active5Y,      pct(active5Y,      activeCount));
    }

    // =========================================================================
    // TEST 4: Zero-NAV active options — count is deterministic
    // =========================================================================

    @Test
    @DisplayName("Zero-NAV active option count is non-negative and consistent with active-with-NAV count")
    void zeroNavActiveOptionsCountIsConsistent() {
        Long activeCount   = jdbcTemplate.queryForObject(SQL_ACTIVE_UNIVERSE, Long.class);
        Long activeWithNav = jdbcTemplate.queryForObject(SQL_ACTIVE_WITH_NAV, Long.class);

        assertNotNull(activeCount);
        assertNotNull(activeWithNav);

        long zeroNavActive = activeCount - activeWithNav;
        assertTrue(zeroNavActive >= 0,
            String.format("Zero-NAV active count must be non-negative (active=%d, withNav=%d)", activeCount, activeWithNav));

        System.out.printf("Zero-NAV Active Options: %d of %d active (%.2f%%)%n",
            zeroNavActive, activeCount, pct(zeroNavActive, activeCount));
    }

    // =========================================================================
    // TEST 5: Regression guard — total-catalog figures must exceed or equal active figures
    // =========================================================================

    @Test
    @DisplayName("Total-catalog NAV coverage must always be >= active coverage (regression guard)")
    void totalCatalogCoverageGeActiveCoverage() {
        Long totalCatalog   = jdbcTemplate.queryForObject(SQL_TOTAL_CATALOG, Long.class);
        Long activeUniverse = jdbcTemplate.queryForObject(SQL_ACTIVE_UNIVERSE, Long.class);

        Long totalWithNav = jdbcTemplate.queryForObject(
            "SELECT count(DISTINCT scheme_option_id) FROM nav_observation", Long.class);
        Long activeWithNav = jdbcTemplate.queryForObject(SQL_ACTIVE_WITH_NAV, Long.class);

        Long total1Y = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation " +
            "GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 365) t", Long.class);
        Long active1Y = jdbcTemplate.queryForObject(SQL_ACTIVE_1Y, Long.class);

        assertTrue(totalCatalog >= activeUniverse,
            "Total catalog must be >= active universe");
        assertTrue(totalWithNav >= activeWithNav,
            "Total-catalog options with NAV must be >= active options with NAV");
        assertTrue(total1Y >= active1Y,
            "Total-catalog 1Y-ready options must be >= active 1Y-ready options");

        System.out.printf("Catalog coverage check PASSED: totalCatalog=%d >= active=%d, " +
            "totalWithNav=%d >= activeWithNav=%d, total1Y=%d >= active1Y=%d%n",
            totalCatalog, activeUniverse, totalWithNav, activeWithNav, total1Y, active1Y);
    }

    // =========================================================================
    // TEST 6: 1Y readiness funnel — 180–364 day bucket
    // =========================================================================

    @Test
    @DisplayName("1Y readiness funnel — 180-364 day bucket is non-negative and bounded by active-with-nav")
    void readinessFunnel180To364DayBucket() {
        Long activeWithNav = jdbcTemplate.queryForObject(SQL_ACTIVE_WITH_NAV, Long.class);
        Long active1Y      = jdbcTemplate.queryForObject(SQL_ACTIVE_1Y, Long.class);

        Long days180To364 = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM (" +
            "  SELECT n.scheme_option_id FROM nav_observation n " +
            "  JOIN scheme_option so ON n.scheme_option_id = so.id " +
            "  WHERE so.status = 'ACTIVE' " +
            "  GROUP BY n.scheme_option_id " +
            "  HAVING (max(n.effective_date) - min(n.effective_date)) >= 180 " +
            "     AND (max(n.effective_date) - min(n.effective_date)) < 365" +
            ") t", Long.class);

        assertNotNull(days180To364);
        assertTrue(days180To364 >= 0, "180-364 day bucket must be non-negative");
        assertTrue(days180To364 <= activeWithNav,
            "180-364 day bucket must be <= active options with any NAV");

        System.out.printf("180-364 Day Bucket: %d active options%n", days180To364);
    }

    // =========================================================================
    // HELPER
    // =========================================================================

    private static double pct(long part, long total) {
        return total > 0 ? (part * 100.0 / total) : 0.0;
    }
}
