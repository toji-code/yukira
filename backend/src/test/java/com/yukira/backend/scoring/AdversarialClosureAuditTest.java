package com.yukira.backend.scoring;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.scoring.config.ScoreMethodologyConfig;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.ScoreCalculationRequest;
import com.yukira.backend.scoring.dto.UniverseScoringReport;
import com.yukira.backend.scoring.normalization.MetricNormalizer;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AdversarialClosureAuditTest {

    private static final Logger log = LoggerFactory.getLogger(AdversarialClosureAuditTest.class);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private AnalyticalScoreRepository analyticalScoreRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private AnalyticalScoringService scoringService;

    @Autowired
    private ScoreMethodologyConfig methodologyConfig;

    @Autowired
    private MetricNormalizer normalizer;

    @Test
    @Transactional
    @Rollback(false)
    @DisplayName("Adversarial Score Integrity Closure Audit")
    void performAdversarialClosureAudit() {
        log.info("============================================================");
        log.info("YUKIRA ADVERSARIAL CLOSURE AUDIT EXECUTION");
        log.info("============================================================");

        // ------------------------------------------------------------------
        // GATE 1 & SECTION 2: BENCHMARK IDENTITY AUDIT (ID 1 vs 123)
        // ------------------------------------------------------------------
        log.info("\n=== GATE 1 & SECTION 2: BENCHMARK IDENTITY AUDIT ===");
        @SuppressWarnings("unchecked")
        List<Object[]> benchmarkRows = entityManager.createNativeQuery(
            "SELECT id, code, name FROM benchmark"
        ).getResultList();

        log.info("Total Benchmark records in DB: {}", benchmarkRows.size());
        for (Object[] row : benchmarkRows) {
            log.info("  - Benchmark ID: {}, Code: '{}', Name: '{}'", row[0], row[1], row[2]);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> bmObsCounts = entityManager.createNativeQuery(
            "SELECT benchmark_id, COUNT(*) FROM benchmark_observation GROUP BY benchmark_id"
        ).getResultList();
        for (Object[] row : bmObsCounts) {
            log.info("  - BenchmarkObservation table group: Benchmark ID {} has {} observations", row[0], row[1]);
        }

        @SuppressWarnings("unchecked")
        List<Object> calcRunBms = entityManager.createNativeQuery(
            "SELECT DISTINCT benchmark_id FROM calculation_run WHERE benchmark_id IS NOT NULL"
        ).getResultList();
        log.info("CalculationRun benchmark_id references in DB: {}", calcRunBms);

        // ------------------------------------------------------------------
        // GATE 2 & SECTION 3: HDFC OBSERVATION COUNT RECONCILIATION
        // ------------------------------------------------------------------
        log.info("\n=== GATE 2 & SECTION 3: HDFC OBSERVATION RECONCILIATION ===");
        
        Number rawInWindow = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM nav_observation WHERE scheme_option_id = 1 AND effective_date >= '2021-01-15' AND effective_date <= '2024-01-15'"
        ).getSingleResult();

        Number pitInWindow = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM nav_observation WHERE scheme_option_id = 1 AND effective_date >= '2021-01-15' AND effective_date <= '2024-01-15' " +
            "AND availability_time <= '2024-01-31 23:59:59+05:30'"
        ).getSingleResult();

        Number latestRevisionCount = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM nav_observation WHERE scheme_option_id = 1 AND effective_date >= '2021-01-15' AND effective_date <= '2024-01-15' " +
            "AND availability_time <= '2024-01-31 23:59:59+05:30' AND is_latest_revision = true"
        ).getSingleResult();

        Number uniqueDatesCount = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(DISTINCT effective_date) FROM nav_observation WHERE scheme_option_id = 1 AND effective_date >= '2021-01-15' AND effective_date <= '2024-01-15' " +
            "AND availability_time <= '2024-01-31 23:59:59+05:30'"
        ).getSingleResult();

        Number preBufferCount = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(DISTINCT effective_date) FROM nav_observation WHERE scheme_option_id = 1 AND effective_date >= '2021-01-01' AND effective_date < '2021-01-15'"
        ).getSingleResult();

        log.info("HDFC Raw Window Rows: {}", rawInWindow);
        log.info("HDFC PIT Filtered Rows: {}", pitInWindow);
        log.info("HDFC Latest Revision Rows: {}", latestRevisionCount);
        log.info("HDFC Unique Date Obs (2021-01-15 to 2024-01-15): {}", uniqueDatesCount);
        log.info("HDFC Pre-Analytical Ingestion Buffer Dates (2021-01-01 to 2021-01-14): {}", preBufferCount);

        @SuppressWarnings("unchecked")
        List<Object[]> pairedRows = entityManager.createNativeQuery(
            "SELECT n.effective_date, n.nav_value, b.index_level " +
            "FROM nav_observation n " +
            "JOIN benchmark_observation b ON n.effective_date = b.effective_date " +
            "JOIN benchmark bm ON b.benchmark_id = bm.id " +
            "WHERE n.scheme_option_id = 1 " +
            "  AND bm.code = 'NIFTY_500_TRI' " +
            "  AND n.effective_date >= '2021-01-15' AND n.effective_date <= '2024-01-15' " +
            "  AND n.availability_time <= '2024-01-31 23:59:59+05:30' " +
            "  AND b.availability_time <= '2024-01-31 23:59:59+05:30' " +
            "  AND n.is_latest_revision = true AND b.is_latest_revision = true " +
            "ORDER BY n.effective_date ASC"
        ).getResultList();

        log.info("HDFC Paired NAV & NIFTY_500_TRI Date Count: {}", pairedRows.size());
        log.info("HDFC Paired Daily Return Periods (N-1): {}", Math.max(0, pairedRows.size() - 1));

        @SuppressWarnings("unchecked")
        List<Object> bmOnlyDates = entityManager.createNativeQuery(
            "SELECT b.effective_date FROM benchmark_observation b " +
            "JOIN benchmark bm ON b.benchmark_id = bm.id " +
            "LEFT JOIN nav_observation n ON b.effective_date = n.effective_date AND n.scheme_option_id = 1 " +
            "WHERE bm.code = 'NIFTY_500_TRI' AND b.effective_date >= '2021-01-15' AND b.effective_date <= '2024-01-15' " +
            "  AND n.id IS NULL"
        ).getResultList();
        log.info("Benchmark-only dates (NIFTY_500_TRI traded, HDFC published no NAV): {} -> Dates: {}",
            bmOnlyDates.size(), bmOnlyDates);

        // ------------------------------------------------------------------
        // GATE 6 & 7: 25-FUND UNIVERSE AND REFERENCE POPULATION AUDIT
        // ------------------------------------------------------------------
        log.info("\n=== GATE 6 & 7: 25-FUND UNIVERSE & REFERENCE POPULATION AUDIT ===");
        
        @SuppressWarnings("unchecked")
        List<Object[]> eligibleUniverseRows = entityManager.createNativeQuery(
            "SELECT so.id, so.amfi_code, so.isin, s.name, s.category, s.subcategory, p.plan_type, so.option_type, " +
            "       COUNT(DISTINCT n.effective_date) as nav_count, MIN(n.effective_date) as min_date, MAX(n.effective_date) as max_date, " +
            "       so.status, a.id as score_id, a.score, a.confidence, a.status as score_status, a.calculation_run_id " +
            "FROM scheme_option so " +
            "JOIN scheme_plan p ON so.plan_id = p.id " +
            "JOIN scheme s ON p.scheme_id = s.id " +
            "JOIN nav_observation n ON n.scheme_option_id = so.id " +
            "LEFT JOIN analytical_score a ON a.scheme_option_id = so.id AND a.as_of_date = '2024-01-15' AND a.score_version = 'YUKIRA_SCORE_V1' " +
            "WHERE (so.status = 'ACTIVE' OR so.status IS NULL) " +
            "  AND p.plan_type = 'DIRECT' " +
            "  AND so.option_type = 'GROWTH' " +
            "  AND n.effective_date >= '2021-01-15' AND n.effective_date <= '2024-01-15' " +
            "  AND n.availability_time <= '2024-01-31 23:59:59+05:30' " +
            "GROUP BY so.id, so.amfi_code, so.isin, s.name, s.category, s.subcategory, p.plan_type, so.option_type, so.status, a.id, a.score, a.confidence, a.status, a.calculation_run_id " +
            "HAVING COUNT(DISTINCT n.effective_date) >= 700 " +
            "ORDER BY so.id ASC"
        ).getResultList();

        log.info("25-Fund Universe Database Verification Result Count: {}", eligibleUniverseRows.size());
        assertEquals(25, eligibleUniverseRows.size());

        List<Long> fundObsList = new ArrayList<>();
        for (Object[] row : eligibleUniverseRows) {
            Long sid = ((Number) row[0]).longValue();
            String amfi = (String) row[1];
            String isin = (String) row[2];
            String name = (String) row[3];
            String cat = row[4] != null ? (String) row[4] : "N/A";
            String subcat = row[5] != null ? (String) row[5] : "N/A";
            String plan = (String) row[6];
            String opt = (String) row[7];
            Long cnt = ((Number) row[8]).longValue();
            Object minD = row[9];
            Object maxD = row[10];
            String activeStatus = (String) row[11];
            Object scoreId = row[12];
            Object scoreVal = row[13];
            Object confVal = row[14];
            String scStatus = (String) row[15];
            Object calcRunId = row[16];

            fundObsList.add(cnt);

            log.info(String.format("ID:%-5d | AMFI:%-6s | ISIN:%-12s | Name:%-35s | Cat:%-15s | Obs:%d | ScoreID:%s | Score:%s | Status:%s | RunID:%s",
                sid, amfi, isin, truncate(name, 35), cat, cnt, scoreId, scoreVal, scStatus, calcRunId));
        }

        log.info("\n=== SECTION 8: OBSERVATION DISTRIBUTION PROOF ===");
        log.info("  - Minimum NAV Count: {}", fundObsList.stream().min(Long::compare).orElse(0L));
        log.info("  - Maximum NAV Count: {}", fundObsList.stream().max(Long::compare).orElse(0L));
        log.info("  - Median NAV Count: {}", getMedian(fundObsList));
        log.info("  - Unique NAV Count Values: {}", new HashSet<>(fundObsList));
        assertEquals(1, new HashSet<>(fundObsList).size());
        assertTrue(new HashSet<>(fundObsList).contains(739L));

        // ------------------------------------------------------------------
        // GATE 9 & SECTION 9: 500-699 OBSERVATION GROUP AUDIT
        // ------------------------------------------------------------------
        log.info("\n=== GATE 9 & SECTION 9: 500-699 OBSERVATION GROUP AUDIT ===");
        @SuppressWarnings("unchecked")
        List<Object[]> partialObsGroup = entityManager.createNativeQuery(
            "SELECT so.id, so.amfi_code, so.isin, s.name, p.plan_type, so.option_type, " +
            "       COUNT(DISTINCT n.effective_date) as nav_count, a.id as score_id, a.score, a.status " +
            "FROM scheme_option so " +
            "JOIN scheme_plan p ON so.plan_id = p.id " +
            "JOIN scheme s ON p.scheme_id = s.id " +
            "JOIN nav_observation n ON n.scheme_option_id = so.id " +
            "LEFT JOIN analytical_score a ON a.scheme_option_id = so.id AND a.as_of_date = '2024-01-15' AND a.score_version = 'YUKIRA_SCORE_V1' " +
            "WHERE (so.status = 'ACTIVE' OR so.status IS NULL) " +
            "  AND p.plan_type = 'DIRECT' AND so.option_type = 'GROWTH' " +
            "  AND n.effective_date >= '2021-01-15' AND n.effective_date <= '2024-01-15' " +
            "  AND n.availability_time <= '2024-01-31 23:59:59+05:30' " +
            "GROUP BY so.id, so.amfi_code, so.isin, s.name, p.plan_type, so.option_type, a.id, a.score, a.status " +
            "HAVING COUNT(DISTINCT n.effective_date) >= 500 AND COUNT(DISTINCT n.effective_date) < 700 " +
            "ORDER BY so.id ASC"
        ).getResultList();

        log.info("500-699 Observation Group Count: {}", partialObsGroup.size());
        assertEquals(3, partialObsGroup.size());
        for (Object[] row : partialObsGroup) {
            log.info("  - Option #{}, AMFI: {}, Name: '{}', NAV Count: {}, Score ID: {}, Score: {}, Status: {}",
                row[0], row[1], row[3], row[6], row[7], row[8], row[9]);
            assertNull(row[8], "Score MUST be null for funds in 500-699 observation range under YUKIRA_SCORE_V1");
            assertEquals("INSUFFICIENT_DATA", row[9]);
        }

        // ------------------------------------------------------------------
        // GATE 11 & SECTION 11 & 12: NOT_APPLICABLE POPULATION RECONCILIATION
        // ------------------------------------------------------------------
        log.info("\n=== GATE 11 & SECTION 11 & 12: NOT_APPLICABLE POPULATION RECONCILIATION ===");
        
        Number nonEquityCount = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(DISTINCT so.id) FROM scheme_option so " +
            "JOIN scheme_plan p ON so.plan_id = p.id " +
            "JOIN scheme s ON p.scheme_id = s.id " +
            "WHERE (so.status = 'ACTIVE' OR so.status IS NULL) " +
            "  AND (LOWER(s.category) LIKE '%debt%' OR LOWER(s.category) LIKE '%income%' OR LOWER(s.category) LIKE '%gilt%' OR LOWER(s.category) LIKE '%money market%' OR LOWER(s.category) LIKE '%hybrid%' OR LOWER(s.category) LIKE '%solution%' OR LOWER(s.category) LIKE '%other%')"
        ).getSingleResult();

        Number regularPlanCount = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(DISTINCT so.id) FROM scheme_option so " +
            "JOIN scheme_plan p ON so.plan_id = p.id " +
            "WHERE (so.status = 'ACTIVE' OR so.status IS NULL) AND p.plan_type = 'REGULAR'"
        ).getSingleResult();

        Number idcwOptionCount = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(DISTINCT so.id) FROM scheme_option so " +
            "WHERE (so.status = 'ACTIVE' OR so.status IS NULL) AND so.option_type != 'GROWTH'"
        ).getSingleResult();

        Number zeroNavCount = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(DISTINCT so.id) FROM scheme_option so " +
            "LEFT JOIN nav_observation n ON n.scheme_option_id = so.id AND n.effective_date >= '2021-01-15' AND n.effective_date <= '2024-01-15' " +
            "WHERE (so.status = 'ACTIVE' OR so.status IS NULL) AND n.id IS NULL"
        ).getSingleResult();

        log.info("NOT_APPLICABLE Breakdown by Criteria:");
        log.info("  - Non-Equity / Fixed Income / Hybrid Category Count: {}", nonEquityCount);
        log.info("  - Regular Plan Count: {}", regularPlanCount);
        log.info("  - IDCW Payout/Reinvestment Option Count: {}", idcwOptionCount);
        log.info("  - Zero NAV Observations in 3Y Window Count: {}", zeroNavCount);

        @SuppressWarnings("unchecked")
        List<Object[]> statusCounts = entityManager.createNativeQuery(
            "SELECT status, COUNT(*) FROM analytical_score WHERE as_of_date = '2024-01-15' GROUP BY status"
        ).getResultList();

        long sumStatus = 0;
        for (Object[] r : statusCounts) {
            String st = (String) r[0];
            Long cnt = ((Number) r[1]).longValue();
            sumStatus += cnt;
            log.info("  - AnalyticalScore DB status breakdown: [{}] = {}", st, cnt);
        }
        log.info("Sum of AnalyticalScore status rows: {} (Total Active Options: 9125)", sumStatus);
        assertEquals(9125, sumStatus);

        // ------------------------------------------------------------------
        // GATE 13 & SECTION 13: HDFC SCORE REPRODUCIBILITY & ARITHMETIC
        // ------------------------------------------------------------------
        log.info("\n=== GATE 13 & SECTION 13: HDFC SCORE REPRODUCIBILITY & ARITHMETIC ===");
        
        AnalyticalScoreResponse hdfcResp = scoringService.calculateScore(new ScoreCalculationRequest(
            1L, null, LocalDate.of(2024, 1, 15), OffsetDateTime.parse("2024-01-31T23:59:59+05:30"), null
        ));

        assertNotNull(hdfcResp);
        log.info("Calculated HDFC Score Response: Score={}, Confidence={}, Status={}",
            hdfcResp.score(), hdfcResp.confidence(), hdfcResp.status());
        assertEquals(67.94, hdfcResp.score().doubleValue(), 0.001);
        assertEquals(95.00, hdfcResp.confidence().doubleValue(), 0.001);
        assertEquals("PARTIAL", hdfcResp.status());

        // ------------------------------------------------------------------
        // GATE 15 & SECTION 15: IDEMPOTENCY AUDIT
        // ------------------------------------------------------------------
        log.info("\n=== GATE 15 & SECTION 15: IDEMPOTENCY AUDIT ===");
        long beforeCount = analyticalScoreRepository.count();
        UniverseScoringReport rerunReport = scoringService.scoreActiveUniverse(
            LocalDate.of(2024, 1, 15), OffsetDateTime.parse("2024-01-31T23:59:59+05:30")
        );
        long afterCount = analyticalScoreRepository.count();

        log.info("AnalyticalScore DB total rows before rerun: {}", beforeCount);
        log.info("AnalyticalScore DB total rows after rerun:  {}", afterCount);
        assertEquals(beforeCount, afterCount, "Idempotent rerun MUST generate zero duplicates and zero row drift");

        log.info("============================================================");
        log.info("ALL ADVERSARIAL CLOSURE AUDIT CHECKS COMPLETED SUCCESSFULLY!");
        log.info("============================================================");
    }

    private static String truncate(String text, int length) {
        if (text == null) return "";
        return text.length() <= length ? text : text.substring(0, length - 3) + "...";
    }

    private static double getMedian(List<Long> list) {
        if (list == null || list.isEmpty()) return 0.0;
        List<Long> sorted = new ArrayList<>(list);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        } else {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        }
    }
}
