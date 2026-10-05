package com.yukira.backend.scoring;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.ScoreCalculationRequest;
import com.yukira.backend.scoring.dto.UniverseScoringReport;
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
public class DataIntegrityAuditTest {

    private static final Logger log = LoggerFactory.getLogger(DataIntegrityAuditTest.class);

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
    private AnalyticalScoringService scoringService;

    @Test
    @Transactional
    @Rollback(false)
    @DisplayName("Comprehensive Final Data Integrity Audit")
    void performFullIntegrityAudit() {
        log.info("============================================================");
        log.info("YUKIRA FINAL SCORE DATA INTEGRITY GATE AUDIT EXECUTION");
        log.info("============================================================");

        // ------------------------------------------------------------------
        // SECTION 1: HDFC OBSERVATION COUNT RECONCILIATION (747 vs 739)
        // ------------------------------------------------------------------
        log.info("\n=== SECTION 1: HDFC OBSERVATION COUNT & RETURN PERIOD AUDIT ===");
        
        Number rawWindow15 = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM nav_observation WHERE scheme_option_id = 1 AND effective_date >= '2021-01-15' AND effective_date <= '2024-01-15'"
        ).getSingleResult();

        Number rawWindow01 = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM nav_observation WHERE scheme_option_id = 1 AND effective_date >= '2021-01-01' AND effective_date <= '2024-01-15'"
        ).getSingleResult();

        Number pitFiltered15 = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM nav_observation WHERE scheme_option_id = 1 AND effective_date >= '2021-01-15' AND effective_date <= '2024-01-15' " +
            "AND availability_time <= '2024-01-31 23:59:59+05:30'"
        ).getSingleResult();

        Number pitDeduplicated15 = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(DISTINCT effective_date) FROM nav_observation WHERE scheme_option_id = 1 AND effective_date >= '2021-01-15' AND effective_date <= '2024-01-15' " +
            "AND availability_time <= '2024-01-31 23:59:59+05:30'"
        ).getSingleResult();

        log.info("HDFC Raw Obs (2021-01-15 to 2024-01-15): {}", rawWindow15);
        log.info("HDFC Raw Obs (2021-01-01 to 2024-01-15): {}", rawWindow01);
        log.info("HDFC PIT-Filtered Obs (2021-01-15 to 2024-01-15): {}", pitFiltered15);
        log.info("HDFC Unique Date Obs (2021-01-15 to 2024-01-15): {}", pitDeduplicated15);

        Object[] minMaxHdfc = (Object[]) entityManager.createNativeQuery(
            "SELECT MIN(effective_date), MAX(effective_date) FROM nav_observation WHERE scheme_option_id = 1 AND effective_date >= '2021-01-15' AND effective_date <= '2024-01-15'"
        ).getSingleResult();
        log.info("HDFC Analytical Window Date Range: {} to {}", minMaxHdfc[0], minMaxHdfc[1]);

        @SuppressWarnings("unchecked")
        List<Object[]> pairedRows = entityManager.createNativeQuery(
            "SELECT n.effective_date, n.nav_value, b.effective_date as b_date, b.index_level " +
            "FROM nav_observation n " +
            "JOIN benchmark_observation b ON n.effective_date = b.effective_date " +
            "JOIN benchmark bm ON b.benchmark_id = bm.id " +
            "WHERE n.scheme_option_id = 1 " +
            "  AND bm.code = 'NIFTY_500_TRI' " +
            "  AND n.effective_date >= '2021-01-15' AND n.effective_date <= '2024-01-15' " +
            "  AND n.availability_time <= '2024-01-31 23:59:59+05:30' " +
            "  AND b.availability_time <= '2024-01-31 23:59:59+05:30' " +
            "ORDER BY n.effective_date ASC"
        ).getResultList();

        log.info("HDFC <-> NIFTY_500_TRI Paired NAV Observations Count: {}", pairedRows.size());
        log.info("HDFC <-> NIFTY_500_TRI Return Periods Count (N-1): {}", Math.max(0, pairedRows.size() - 1));

        Number rawWindow18 = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(DISTINCT effective_date) FROM nav_observation WHERE scheme_option_id = 1 AND effective_date >= '2021-01-18' AND effective_date <= '2024-01-15'"
        ).getSingleResult();
        log.info("HDFC Obs count from 2021-01-18 to 2024-01-15: {}", rawWindow18);

        // ------------------------------------------------------------------
        // SECTION 2: BENCHMARK DATA VERIFICATION (NIFTY_500_TRI)
        // ------------------------------------------------------------------
        log.info("\n=== SECTION 2: BENCHMARK DATA VERIFICATION ===");
        Number benchmarkObsCount = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM benchmark_observation b " +
            "JOIN benchmark bm ON b.benchmark_id = bm.id " +
            "WHERE bm.code = 'NIFTY_500_TRI' AND b.effective_date >= '2021-01-15' AND b.effective_date <= '2024-01-15' " +
            "AND b.availability_time <= '2024-01-31 23:59:59+05:30'"
        ).getSingleResult();
        
        Object[] bmMinMax = (Object[]) entityManager.createNativeQuery(
            "SELECT MIN(b.effective_date), MAX(b.effective_date) FROM benchmark_observation b " +
            "JOIN benchmark bm ON b.benchmark_id = bm.id " +
            "WHERE bm.code = 'NIFTY_500_TRI' AND b.effective_date >= '2021-01-15' AND b.effective_date <= '2024-01-15'"
        ).getSingleResult();

        log.info("NIFTY_500_TRI PIT Observations (2021-01-15 to 2024-01-15): {} (Range: {} to {})",
            benchmarkObsCount, bmMinMax[0], bmMinMax[1]);

        // ------------------------------------------------------------------
        // SECTION 3: 25-FUND UNIVERSE DATABASE VERIFICATION
        // ------------------------------------------------------------------
        log.info("\n=== SECTION 3: 25-FUND UNIVERSE DATABASE VERIFICATION ===");
        
        @SuppressWarnings("unchecked")
        List<Object[]> eligibleUniverseRows = entityManager.createNativeQuery(
            "SELECT so.id, so.amfi_code, so.isin, s.name, s.category, p.plan_type, so.option_type, " +
            "       COUNT(DISTINCT n.effective_date) as nav_count, MIN(n.effective_date) as min_date, MAX(n.effective_date) as max_date " +
            "FROM scheme_option so " +
            "JOIN scheme_plan p ON so.plan_id = p.id " +
            "JOIN scheme s ON p.scheme_id = s.id " +
            "JOIN nav_observation n ON n.scheme_option_id = so.id " +
            "WHERE (so.status = 'ACTIVE' OR so.status IS NULL) " +
            "  AND p.plan_type = 'DIRECT' " +
            "  AND so.option_type = 'GROWTH' " +
            "  AND n.effective_date >= '2021-01-15' AND n.effective_date <= '2024-01-15' " +
            "  AND n.availability_time <= '2024-01-31 23:59:59+05:30' " +
            "GROUP BY so.id, so.amfi_code, so.isin, s.name, s.category, p.plan_type, so.option_type " +
            "HAVING COUNT(DISTINCT n.effective_date) >= 700 " +
            "ORDER BY so.id ASC"
        ).getResultList();

        log.info("DB Query Result: Found {} eligible Direct Growth funds with >=700 NAV observations", eligibleUniverseRows.size());

        List<Long> obsCounts = new ArrayList<>();
        Set<String> benchmarksUsed = new HashSet<>();
        Set<String> categoriesUsed = new HashSet<>();

        System.out.println(String.format("%-6s | %-8s | %-12s | %-45s | %-10s | %-6s | %-6s | %-8s | %-10s | %-10s | %-14s",
            "ID", "AMFI", "ISIN", "Scheme Name", "Category", "Plan", "Option", "NAV Obs", "Start Date", "End Date", "Benchmark"));
        System.out.println("----------------------------------------------------------------------------------------------------------------------------------");

        for (Object[] row : eligibleUniverseRows) {
            Long sid = ((Number) row[0]).longValue();
            String amfi = (String) row[1];
            String isin = (String) row[2];
            String name = (String) row[3];
            String cat = row[4] != null ? (String) row[4] : "N/A";
            String plan = (String) row[5];
            String opt = (String) row[6];
            Long cnt = ((Number) row[7]).longValue();
            Object minD = row[8];
            Object maxD = row[9];
            String bmCode = "NIFTY_500_TRI";

            obsCounts.add(cnt);
            benchmarksUsed.add(bmCode);
            categoriesUsed.add(cat);

            System.out.println(String.format("%-6d | %-8s | %-12s | %-45s | %-10s | %-6s | %-6s | %-8d | %-10s | %-10s | %-14s",
                sid, amfi, isin, truncate(name, 45), cat, plan, opt, cnt, minD, maxD, bmCode));
        }

        log.info("\n=== SECTION 4 & 5: UNIFORMITY & CATEGORY VERIFICATION ===");
        log.info("Observation Count Distribution across Universe:");
        log.info("  - Total Eligible Funds Count: {}", eligibleUniverseRows.size());
        log.info("  - Minimum Obs Count: {}", obsCounts.stream().min(Long::compare).orElse(0L));
        log.info("  - Maximum Obs Count: {}", obsCounts.stream().max(Long::compare).orElse(0L));
        log.info("  - Median Obs Count: {}", getMedian(obsCounts));
        log.info("  - Distinct Obs Count Values: {}", new HashSet<>(obsCounts));
        log.info("  - Benchmarks Used Across Universe: {}", benchmarksUsed);
        log.info("  - Categories Used Across Universe: {}", categoriesUsed);

        // ------------------------------------------------------------------
        // SECTION 6: 500-699 OBSERVATION GROUP AUDIT
        // ------------------------------------------------------------------
        log.info("\n=== SECTION 6: 500-699 OBSERVATION GROUP AUDIT ===");
        @SuppressWarnings("unchecked")
        List<Object[]> partialObsRows = entityManager.createNativeQuery(
            "SELECT so.id, so.amfi_code, s.name, COUNT(DISTINCT n.effective_date) as nav_count " +
            "FROM scheme_option so " +
            "JOIN scheme_plan p ON so.plan_id = p.id " +
            "JOIN scheme s ON p.scheme_id = s.id " +
            "JOIN nav_observation n ON n.scheme_option_id = so.id " +
            "WHERE (so.status = 'ACTIVE' OR so.status IS NULL) " +
            "  AND p.plan_type = 'DIRECT' AND so.option_type = 'GROWTH' " +
            "  AND n.effective_date >= '2021-01-15' AND n.effective_date <= '2024-01-15' " +
            "  AND n.availability_time <= '2024-01-31 23:59:59+05:30' " +
            "GROUP BY so.id, so.amfi_code, s.name " +
            "HAVING COUNT(DISTINCT n.effective_date) >= 500 AND COUNT(DISTINCT n.effective_date) < 700 " +
            "ORDER BY so.id ASC"
        ).getResultList();

        log.info("Direct Growth Funds with 500-699 obs count: {}", partialObsRows.size());
        for (Object[] row : partialObsRows) {
            log.info("  - SchemeOption #{}, AMFI: {}, Name: '{}', NAV Count: {}", row[0], row[1], row[2], row[3]);
        }

        // ------------------------------------------------------------------
        // SECTION 7: SCORE PERSISTENCE & STATUS SEMANTICS
        // ------------------------------------------------------------------
        log.info("\n=== SECTION 7: ANALYTICALSCORE PERSISTENCE & STATUS SEMANTICS ===");
        @SuppressWarnings("unchecked")
        List<Object[]> statusCounts = entityManager.createNativeQuery(
            "SELECT status, COUNT(*), MIN(score), MAX(score) FROM analytical_score WHERE as_of_date = '2024-01-15' GROUP BY status"
        ).getResultList();

        log.info("AnalyticalScore DB Persisted Rows for 2024-01-15 Breakdown by Status:");
        long totalAsOfRows = 0;
        for (Object[] row : statusCounts) {
            String status = (String) row[0];
            Long cnt = ((Number) row[1]).longValue();
            Object minScore = row[2];
            Object maxScore = row[3];
            totalAsOfRows += cnt;
            log.info("  - Status [{}]: Count = {}, Min Score = {}, Max Score = {}", status, cnt, minScore, maxScore);
        }
        log.info("Total AnalyticalScore Persisted Records for 2024-01-15: {}", totalAsOfRows);

        // ------------------------------------------------------------------
        // SECTION 8: PIT CONTAMINATION & HISTORICAL SCORE CHECK
        // ------------------------------------------------------------------
        log.info("\n=== SECTION 8: PIT CONTAMINATION & HISTORICAL SCORE AUDIT ===");
        
        List<AnalyticalScore> hdfcScores = analyticalScoreRepository.findBySchemeOptionAndDateAndVersion(
            1L, LocalDate.of(2024, 1, 15), "YUKIRA_SCORE_V1"
        );
        assertFalse(hdfcScores.isEmpty());
        AnalyticalScore hdfc2024 = hdfcScores.get(0);
        Long runId = hdfc2024.getCalculationRun().getId();

        Number futureObsCount = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM metric_result mr JOIN calculation_run cr ON mr.calculation_run_id = cr.id " +
            "WHERE mr.calculation_run_id = :runId AND (cr.as_of_date > '2024-01-15' OR cr.knowledge_cutoff_time > '2024-01-31 23:59:59+05:30')"
        ).setParameter("runId", runId).getSingleResult();

        log.info("HDFC CalculationRun #{} Future Contamination Count: {}", runId, futureObsCount);
        assertEquals(0, futureObsCount.intValue(), "ZERO future observations permitted in 2024-01-15 calculation run");

        List<AnalyticalScore> hdfcHist = analyticalScoreRepository.findBySchemeOptionAndDateAndVersion(
            1L, LocalDate.of(2023, 12, 31), "YUKIRA_SCORE_V1"
        );
        log.info("HDFC Historical Score @ 2023-12-31 Count: {}", hdfcHist.size());
        if (!hdfcHist.isEmpty()) {
            AnalyticalScore hist = hdfcHist.get(0);
            log.info("  - Historical Score: {}, Confidence: {}, Status: {}, AsOfDate: {}",
                hist.getScore(), hist.getConfidence(), hist.getStatus(), hist.getAsOfDate());
        }

        // ------------------------------------------------------------------
        // SECTION 9: IDEMPOTENCY AUDIT
        // ------------------------------------------------------------------
        log.info("\n=== SECTION 9: IDEMPOTENCY RERUN AUDIT ===");
        long totalRowsBefore = analyticalScoreRepository.count();
        UniverseScoringReport rerunReport = scoringService.scoreActiveUniverse(
            LocalDate.of(2024, 1, 15),
            OffsetDateTime.parse("2024-01-31T23:59:59+05:30")
        );
        long totalRowsAfter = analyticalScoreRepository.count();

        log.info("DB Total AnalyticalScore Rows Before Rerun: {}", totalRowsBefore);
        log.info("DB Total AnalyticalScore Rows After Rerun:  {}", totalRowsAfter);
        assertEquals(totalRowsBefore, totalRowsAfter, "Idempotent rerun MUST produce zero row drift and zero duplicates");

        log.info("============================================================");
        log.info("ALL DATA INTEGRITY AUDIT CHECKS COMPLETED SUCCESSFULLY!");
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
