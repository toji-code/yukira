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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class Production1YExpansionRunnerTest {

    @Autowired
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Execute production-scale 1Y coverage expansion across candidate universe in bounded cohorts & audit Zero-NAV root cause")
    public void executeProduction1YCoverageExpansionBatches() {
        System.out.println("==================================================");
        System.out.println("PRODUCTION 1Y NAV COVERAGE EXPANSION & ZERO-NAV ROOT CAUSE AUDIT");
        System.out.println("==================================================");

        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2025, 1, 15);
        int batchSize = 250;
        int maxBatches = 1;

        // 1. STEP 1: DRY-RUN PLANNER PREVIEW & ZERO-NAV ROOT CAUSE AUDIT
        System.out.println("\n--- STEP 1: DRY-RUN PLANNER PREVIEW & ZERO-NAV ROOT CAUSE ANALYSIS ---");
        long obsCountInitial = navObservationRepository.count();
        HistoricalBackfillPreviewDto preview = historicalNavIngestionService.previewProductionCohort(
            batchSize, "TIER_2", startDate, endDate
        );

        assertNotNull(preview);
        assertThat(preview.isDryRun()).isTrue();
        assertEquals(obsCountInitial, navObservationRepository.count(), "Dry-run preview must NOT mutate DB");

        System.out.println(String.format("ACTIVE UNIVERSE DENOMINATOR (N = %d):", preview.totalActiveOptions()));
        System.out.println(String.format("  - >=5Y Ready: %d", preview.ready5YCount()));
        System.out.println(String.format("  - >=3Y Ready (but <5Y): %d", preview.ready3YCount()));
        System.out.println(String.format("  - >=1Y Ready (but <3Y): %d", preview.ready1YCount()));
        System.out.println(String.format("  - 180-364 Days: %d", preview.days180To364Count()));
        System.out.println(String.format("  - 90-179 Days: %d", preview.days90To179Count()));
        System.out.println(String.format("  - 1-89 Days: %d", preview.days1To89Count()));
        System.out.println(String.format("  - Zero NAV Observations: %d", preview.zeroNavCount()));
        System.out.println(String.format("  - Short Inception (<1Y old): %d", preview.shortInception1YCount()));
        System.out.println(String.format("  - 1Y-Capable Universe: %d", preview.capable1YCount()));

        System.out.println("\nZERO-NAV ACTIVE OPTIONS ROOT-CAUSE BREAKDOWN:");
        System.out.println(String.format("  - Category A (New Fund <1Y Old): %d", preview.zeroNavNewFundCount()));
        System.out.println(String.format("  - Category B (Source Has History / Not Ingested): %d", preview.zeroNavSourceNotIngestedCount()));
        System.out.println(String.format("  - Category C (Source Has No History Published): %d", preview.zeroNavSourceNoHistoryCount()));
        System.out.println(String.format("  - Category D (Identity / AMFI Mapping Issue): %d", preview.zeroNavIdentityMappingCount()));
        System.out.println(String.format("  - Category E (Catalog Data Quality / Stale Option): %d", preview.zeroNavCatalogQualityCount()));
        System.out.println(String.format("  - Category F (Other): %d", preview.zeroNavOtherCount()));

        System.out.println(String.format("\nWorkload Estimate: HTTP Universe Chunks=%d, Selected In First Cohort=%d, Estimated DB Insertions=%d",
            preview.estimatedChunkHttpRequests(), preview.totalSelected(), preview.estimatedDbInsertions()));

        // 2. STEP 2: REAL DB COVERAGE AUDIT (BEFORE BATCHES)
        System.out.println("\n--- STEP 2: REAL DB COVERAGE AUDIT (BEFORE BATCHES) ---");
        long activeCount = jdbcTemplate.queryForObject("SELECT count(*) FROM scheme_option WHERE status = 'ACTIVE'", Long.class);
        long totalCatalogCount = jdbcTemplate.queryForObject("SELECT count(*) FROM scheme_option", Long.class);
        long beforeAnyActive = jdbcTemplate.queryForObject("SELECT count(DISTINCT scheme_option_id) FROM nav_observation n JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE'", Long.class);
        long before1YActive = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation n JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 365) t", Long.class);
        long before3YActive = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation n JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1095) t", Long.class);
        long before5YActive = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation n JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1825) t", Long.class);

        System.out.println(String.format("BEFORE AUDIT (Active Denominator N = %d | Total Catalog N = %d):", activeCount, totalCatalogCount));
        System.out.println(String.format("  - Any NAV Active: %d (%.2f%%)", beforeAnyActive, (beforeAnyActive * 100.0 / activeCount)));
        System.out.println(String.format("  - >=1Y Active: %d (%.2f%%)", before1YActive, (before1YActive * 100.0 / activeCount)));
        System.out.println(String.format("  - >=3Y Active: %d (%.2f%%)", before3YActive, (before3YActive * 100.0 / activeCount)));
        System.out.println(String.format("  - >=5Y Active: %d (%.2f%%)", before5YActive, (before5YActive * 100.0 / activeCount)));

        // 3. STEP 3: SEQUENTIAL PRODUCTION BATCHES FOR 1Y EXPANSION
        System.out.println("\n--- STEP 3: EXECUTING SEQUENTIAL PRODUCTION BATCHES FOR 1Y EXPANSION ---");

        int batchNumber = 1;
        long totalInsertedAcrossBatches = 0;
        long totalDuplicatesAcrossBatches = 0;
        long totalRequestsMadeAcrossBatches = 0;
        long totalRequestsSkippedAcrossBatches = 0;
        long totalDurationMsAcrossBatches = 0;

        List<SchemeOption> lastExecutedCohort = null;
        Set<Long> processedOptionIds = new HashSet<>();

        while (batchNumber <= maxBatches) {
            List<SchemeOption> rawCohort = historicalNavIngestionService.selectProductionCohort(batchSize, "TIER_2", endDate);
            List<SchemeOption> cohort = rawCohort.stream()
                .filter(o -> !processedOptionIds.contains(o.getId()))
                .collect(Collectors.toList());

            if (cohort.isEmpty()) {
                System.out.println("\nNo remaining unprocessed Tier-2 candidates found. All candidate cohorts processed!");
                break;
            }
            cohort.forEach(o -> processedOptionIds.add(o.getId()));

            long batchStart1Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation n JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 365) t", Long.class);
            long batchStart3Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation n JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1095) t", Long.class);

            System.out.println(String.format("\nExecuting Batch #%d (Selected Candidates=%d, Limit=%d)...", batchNumber, cohort.size(), batchSize));

            Map<String, Long> amcCounts = cohort.stream()
                .map(o -> o.getPlan() != null && o.getPlan().getScheme() != null && o.getPlan().getScheme().getAmc() != null
                    ? o.getPlan().getScheme().getAmc().getCode() : "UNKNOWN")
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
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

            long batchEnd1Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation n JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 365) t", Long.class);
            long batchEnd3Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation n JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1095) t", Long.class);
            long newly1YInBatch = Math.max(0, batchEnd1Y - batchStart1Y);
            long newly3YInBatch = Math.max(0, batchEnd3Y - batchStart3Y);

            System.out.println(String.format("Batch #%d Summary: Duration=%d ms (%.2f opt/min), Inserted=%d obs, Duplicates Skipped=%d, HTTP Made=%d, HTTP Skipped=%d",
                batchNumber, summary.durationMs(), summary.optionsPerMin(), summary.totalObservationsInserted(),
                summary.totalDuplicatesSkipped(), summary.sourceRequestsMade(), summary.sourceRequestsSkipped()));
            System.out.println(String.format("Batch #%d Fund Results: Completed=%d, Partial/SourceLimited=%d, ShortHistory=%d, NoData=%d, Failed=%d",
                batchNumber, summary.completedCount(), summary.partialCount(), summary.shortHistoryCount(),
                summary.noDataCount(), summary.failedCount()));
            System.out.println(String.format("Batch #%d Coverage Gains: Newly 1Y-Ready=+%d (Total 1Y: %d), Newly 3Y-Ready=+%d (Total 3Y: %d)",
                batchNumber, newly1YInBatch, batchEnd1Y, newly3YInBatch, batchEnd3Y));

            batchNumber++;
        }

        // 4. STEP 4: REAL DB COVERAGE AUDIT (AFTER BATCHES)
        System.out.println("\n--- STEP 4: REAL DB COVERAGE AUDIT (AFTER BATCHES) ---");
        long afterAnyActive = jdbcTemplate.queryForObject("SELECT count(DISTINCT scheme_option_id) FROM nav_observation n JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE'", Long.class);
        long after1YActive = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation n JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 365) t", Long.class);
        long after3YActive = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation n JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1095) t", Long.class);
        long after5YActive = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation n JOIN scheme_option so ON n.scheme_option_id = so.id WHERE so.status = 'ACTIVE' GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1825) t", Long.class);

        System.out.println(String.format("AFTER AUDIT (Active Denominator N = %d):", activeCount));
        System.out.println(String.format("  - Any NAV Active: %d (%.2f%%)", afterAnyActive, (afterAnyActive * 100.0 / activeCount)));
        System.out.println(String.format("  - >=1Y Active: %d (%.2f%%)", after1YActive, (after1YActive * 100.0 / activeCount)));
        System.out.println(String.format("  - >=3Y Active: %d (%.2f%%)", after3YActive, (after3YActive * 100.0 / activeCount)));
        System.out.println(String.format("  - >=5Y Active: %d (%.2f%%)", after5YActive, (after5YActive * 100.0 / activeCount)));

        System.out.println(String.format("\nCUMULATIVE EXPANSION SUMMARY Across %d Batches:", batchNumber - 1));
        System.out.println(String.format("  - Total Duration: %d ms (%.2f minutes)", totalDurationMsAcrossBatches, totalDurationMsAcrossBatches / 60000.0));
        System.out.println(String.format("  - Total Observations Inserted: %d", totalInsertedAcrossBatches));
        System.out.println(String.format("  - Total Duplicates Skipped: %d", totalDuplicatesAcrossBatches));
        System.out.println(String.format("  - Total HTTP Requests Made: %d, Skipped (Cached Artifacts): %d", totalRequestsMadeAcrossBatches, totalRequestsSkippedAcrossBatches));
        System.out.println(String.format("  - 1Y Analytical Readiness Gain: +%d options (%d → %d / %d active, %.2f%%)",
            (after1YActive - before1YActive), before1YActive, after1YActive, activeCount, (after1YActive * 100.0 / activeCount)));
        System.out.println(String.format("  - 3Y Analytical Readiness Follow-on Gain: +%d options (%d → %d / %d active, %.2f%%)",
            (after3YActive - before3YActive), before3YActive, after3YActive, activeCount, (after3YActive * 100.0 / activeCount)));

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
        System.out.println("PRODUCTION 1Y UNIVERSE EXPANSION VERIFICATION COMPLETE: PASS");
        System.out.println("==================================================");
    }
}
