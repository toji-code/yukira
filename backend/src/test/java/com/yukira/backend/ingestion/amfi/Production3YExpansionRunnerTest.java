package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.ingestion.HistoricalBackfillPreviewDto;
import com.yukira.backend.dto.ingestion.HistoricalBackfillRunSummaryDto;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class Production3YExpansionRunnerTest {

    @Autowired
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Execute production-scale 3Y coverage expansion across all remaining Tier-1 candidates in bounded batches")
    public void executeRemainingTier1ProductionExpansionBatches() {
        System.out.println("==================================================");
        System.out.println("PRODUCTION 3Y NAV COVERAGE EXPANSION: ALL REMAINING TIER-1 UNIVERSE");
        System.out.println("==================================================");

        LocalDate startDate = LocalDate.of(2023, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 1);
        int batchSize = 200;

        // 1. STEP 1: DRY-RUN PLANNER PREVIEW
        System.out.println("\n--- STEP 1: DRY-RUN PLANNER PREVIEW ---");
        long obsCountInitial = navObservationRepository.count();
        HistoricalBackfillPreviewDto preview = historicalNavIngestionService.previewProductionCohort(
            batchSize, "TIER_1", startDate, endDate
        );

        assertNotNull(preview);
        assertThat(preview.isDryRun()).isTrue();
        assertEquals(obsCountInitial, navObservationRepository.count(), "Dry-run must not mutate DB");

        System.out.println(String.format("Dry-Run Report: Total Active=%d, First Batch Selected=%d, Candidate 3Y Pool=%d (Tier-1 >=1Y & <3Y: %d, Tier-2 <1Y: %d, Tier-3 0 NAV: %d, Tier-4 >=3Y: %d)",
            preview.totalActiveOptions(), preview.totalSelected(), preview.candidateCount3Y(),
            preview.already1YCount(), preview.lessThan1YCount(), preview.zeroNavCount(), preview.already3YCount()));
        System.out.println(String.format("Inception Profiling: 3Y-Capable=%d, Too Recent Inception=%d",
            preview.capable3YCount(), preview.incapableInceptionCount()));
        System.out.println(String.format("Workload Estimate: HTTP Universe Chunks=%d, Estimated DB Insertions=%d",
            preview.estimatedChunkHttpRequests(), preview.estimatedDbInsertions()));

        System.out.println("\nTop AMCs needing 3Y coverage:");
        preview.topUnderCoveredAmcs().forEach((amc, count) -> System.out.println(String.format("  - %s: %d candidate options", amc, count)));

        // 2. STEP 2: MEASURE BEFORE AUDIT STATS
        System.out.println("\n--- STEP 2: REAL DB COVERAGE AUDIT (BEFORE BATCHES) ---");
        long activeCount = jdbcTemplate.queryForObject("SELECT count(*) FROM scheme_option WHERE status = 'ACTIVE'", Long.class);
        long beforeAny = jdbcTemplate.queryForObject("SELECT count(DISTINCT scheme_option_id) FROM nav_observation", Long.class);
        long before1Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 365) t", Long.class);
        long before3Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1095) t", Long.class);
        long before5Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1825) t", Long.class);

        System.out.println(String.format("BEFORE AUDIT: Total Active=%d | Any NAV=%d (%.2f%%) | >=1Y=%d (%.2f%%) | >=3Y=%d (%.2f%%) | >=5Y=%d (%.2f%%)",
            activeCount, beforeAny, (beforeAny * 100.0 / activeCount), before1Y, (before1Y * 100.0 / activeCount),
            before3Y, (before3Y * 100.0 / activeCount), before5Y, (before5Y * 100.0 / activeCount)));

        // 3. STEP 3: SEQUENTIAL PRODUCTION BATCHES
        System.out.println("\n--- STEP 3: EXECUTING SEQUENTIAL PRODUCTION BATCHES ---");

        int batchNumber = 1;
        long totalInsertedAcrossBatches = 0;
        long totalDuplicatesAcrossBatches = 0;
        long totalRequestsMadeAcrossBatches = 0;
        long totalRequestsSkippedAcrossBatches = 0;
        long totalDurationMsAcrossBatches = 0;

        List<SchemeOption> lastExecutedCohort = null;
        java.util.Set<Long> processedOptionIds = new java.util.HashSet<>();

        while (true) {
            List<SchemeOption> rawCohort = historicalNavIngestionService.selectProductionCohort(batchSize, "TIER_1", endDate);
            List<SchemeOption> cohort = rawCohort.stream()
                .filter(o -> !processedOptionIds.contains(o.getId()))
                .collect(java.util.stream.Collectors.toList());

            if (cohort.isEmpty()) {
                System.out.println(String.format("\nNo remaining unprocessed Tier-1 candidates found. All Tier-1 candidates have been processed!"));
                break;
            }
            cohort.forEach(o -> processedOptionIds.add(o.getId()));

            long batchStart3Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1095) t", Long.class);

            System.out.println(String.format("\nExecuting Batch #%d (Selected Options=%d, Batch Size Limit=%d)...", batchNumber, cohort.size(), batchSize));

            // AMC distribution for batch
            Map<String, Long> amcCounts = cohort.stream()
                .map(o -> o.getPlan() != null && o.getPlan().getScheme() != null && o.getPlan().getScheme().getAmc() != null
                    ? o.getPlan().getScheme().getAmc().getCode() : "UNKNOWN")
                .collect(java.util.stream.Collectors.groupingBy(s -> s, java.util.stream.Collectors.counting()));
            System.out.println(String.format("Batch #%d AMC Diversity: %d distinct AMCs represented", batchNumber, amcCounts.size()));

            HistoricalBackfillRunSummaryDto summary = historicalNavIngestionService.executeProductionCohortBackfillForCohort(
                cohort, batchSize, startDate, endDate
            );

            assertNotNull(summary);
            lastExecutedCohort = cohort;

            totalInsertedAcrossBatches += summary.totalObservationsInserted();
            totalDuplicatesAcrossBatches += summary.totalDuplicatesSkipped();
            totalRequestsMadeAcrossBatches += summary.sourceRequestsMade();
            totalRequestsSkippedAcrossBatches += summary.sourceRequestsSkipped();
            totalDurationMsAcrossBatches += summary.durationMs();

            long batchEnd3Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1095) t", Long.class);
            long newly3YInBatch = Math.max(0, batchEnd3Y - batchStart3Y);

            System.out.println(String.format("Batch #%d Summary: Duration=%d ms (%.2f opt/min), Inserted=%d obs, Duplicates Skipped=%d, HTTP Made=%d, HTTP Skipped=%d",
                batchNumber, summary.durationMs(), summary.optionsPerMin(), summary.totalObservationsInserted(),
                summary.totalDuplicatesSkipped(), summary.sourceRequestsMade(), summary.sourceRequestsSkipped()));
            System.out.println(String.format("Batch #%d Fund Results: Completed=%d, Partial/SourceLimited=%d, ShortHistory=%d, NoData=%d, Failed=%d",
                batchNumber, summary.completedCount(), summary.partialCount(), summary.shortHistoryCount(),
                summary.noDataCount(), summary.failedCount()));
            System.out.println(String.format("Batch #%d Coverage Gain: Newly 3Y-Ready=+%d options (Total 3Y in DB: %d)",
                batchNumber, newly3YInBatch, batchEnd3Y));

            batchNumber++;
        }

        // 4. STEP 4: REAL DB COVERAGE AUDIT (AFTER ALL BATCHES)
        System.out.println("\n--- STEP 4: REAL DB COVERAGE AUDIT (AFTER ALL BATCHES) ---");
        long afterAny = jdbcTemplate.queryForObject("SELECT count(DISTINCT scheme_option_id) FROM nav_observation", Long.class);
        long after1Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 365) t", Long.class);
        long after3Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1095) t", Long.class);
        long after5Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1825) t", Long.class);

        System.out.println(String.format("AFTER AUDIT: Total Active=%d | Any NAV=%d (%.2f%%) | >=1Y=%d (%.2f%%) | >=3Y=%d (%.2f%%) | >=5Y=%d (%.2f%%)",
            activeCount, afterAny, (afterAny * 100.0 / activeCount), after1Y, (after1Y * 100.0 / activeCount),
            after3Y, (after3Y * 100.0 / activeCount), after5Y, (after5Y * 100.0 / activeCount)));

        System.out.println(String.format("\nCUMULATIVE EXPANSION SUMMARY Across %d Batches:", batchNumber - 1));
        System.out.println(String.format("  - Total Duration: %d ms (%.2f minutes)", totalDurationMsAcrossBatches, totalDurationMsAcrossBatches / 60000.0));
        System.out.println(String.format("  - Total Observations Inserted: %d", totalInsertedAcrossBatches));
        System.out.println(String.format("  - Total Duplicates Skipped: %d", totalDuplicatesAcrossBatches));
        System.out.println(String.format("  - Total HTTP Requests Made: %d, Skipped (Cached Artifacts): %d", totalRequestsMadeAcrossBatches, totalRequestsSkippedAcrossBatches));
        System.out.println(String.format("  - 3Y Analytical Readiness Gain: +%d options (%d → %d / %d active, %.2f%%)",
            (after3Y - before3Y), before3Y, after3Y, activeCount, (after3Y * 100.0 / activeCount)));
        System.out.println(String.format("  - 5Y Analytical Readiness Gain: +%d options (%d → %d / %d active, %.2f%%)",
            (after5Y - before5Y), before5Y, after5Y, activeCount, (after5Y * 100.0 / activeCount)));

        // 5. STEP 5: IDEMPOTENCY VERIFICATION (RERUN LAST BATCH)
        System.out.println("\n--- STEP 5: EXECUTING IDEMPOTENT RERUN (FINAL BATCH RERUN) ---");
        assertNotNull(lastExecutedCohort, "Must have executed at least one batch");
        long obsCountBeforeRerun = navObservationRepository.count();

        HistoricalBackfillRunSummaryDto rerunSummary = historicalNavIngestionService.executeProductionCohortBackfillForCohort(
            lastExecutedCohort, batchSize, startDate, endDate
        );

        assertNotNull(rerunSummary);
        System.out.println(String.format("Idempotency Rerun Summary: Duration=%d ms, Inserted=%d obs, Duplicates Skipped=%d, HTTP Made=%d, HTTP Skipped=%d",
            rerunSummary.durationMs(), rerunSummary.totalObservationsInserted(), rerunSummary.totalDuplicatesSkipped(),
            rerunSummary.sourceRequestsMade(), rerunSummary.sourceRequestsSkipped()));

        assertEquals(0, rerunSummary.totalObservationsInserted(), "Rerun must insert 0 new observations");
        assertThat(rerunSummary.sourceRequestsSkipped()).isGreaterThan(0);
        assertEquals(obsCountBeforeRerun, navObservationRepository.count(), "Database observation count must be exactly preserved");

        System.out.println("==================================================");
        System.out.println("PRODUCTION 3Y UNIVERSE EXPANSION VERIFICATION COMPLETE: PASS");
        System.out.println("==================================================");
    }
}
