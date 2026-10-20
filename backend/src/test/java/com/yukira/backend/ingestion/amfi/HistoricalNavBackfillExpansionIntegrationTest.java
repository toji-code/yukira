package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HistoricalNavBackfillExpansionIntegrationTest {

    @Autowired
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Execute deterministic multi-AMC historical NAV backfill expansion and verify resumability")
    void testHistoricalNavBackfillExpansionAndResumability() {
        System.out.println("==================================================");
        System.out.println("PHASE 10 — HISTORICAL COVERAGE BEFORE VS AFTER");
        System.out.println("==================================================");

        // Measure BEFORE counts
        long beforeAny = jdbcTemplate.queryForObject("SELECT count(DISTINCT scheme_option_id) FROM nav_observation", Long.class);
        long before1Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 365) t", Long.class);
        long before3Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1095) t", Long.class);
        long before5Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1825) t", Long.class);

        System.out.println(String.format("BEFORE: Any NAV=%d, >=1Y=%d, >=3Y=%d, >=5Y=%d", beforeAny, before1Y, before3Y, before5Y));

        // Select multi-AMC cohort of 5 options across AMCs
        List<SchemeOption> cohort = historicalNavIngestionService.selectDeterministicCohort(5);
        assertFalse(cohort.isEmpty(), "Cohort should contain at least 1 scheme option");

        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 6, 30);

        System.out.println("Executing Run #1: Historical backfill for cohort size " + cohort.size() + " from " + startDate + " to " + endDate);
        HistoricalNavIngestionService.TargetedBackfillResult run1Result = historicalNavIngestionService.ingestHistoricalNavForSchemeOptions(
            cohort.stream().map(SchemeOption::getId).toList(), startDate, endDate
        );

        assertNotNull(run1Result);
        System.out.println("Run #1 Result: requested=" + run1Result.fundsRequested() + ", succeeded=" + run1Result.fundsSucceeded() +
            ", ingested=" + run1Result.totalObservationsIngested() + ", duplicatesSkipped=" + run1Result.totalDuplicatesSkipped());

        // Measure AFTER counts
        long afterAny = jdbcTemplate.queryForObject("SELECT count(DISTINCT scheme_option_id) FROM nav_observation", Long.class);
        long after1Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 365) t", Long.class);
        long after3Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1095) t", Long.class);
        long after5Y = jdbcTemplate.queryForObject("SELECT count(*) FROM (SELECT scheme_option_id FROM nav_observation GROUP BY scheme_option_id HAVING (max(effective_date) - min(effective_date)) >= 1825) t", Long.class);

        System.out.println(String.format("AFTER: Any NAV=%d, >=1Y=%d, >=3Y=%d, >=5Y=%d", afterAny, after1Y, after3Y, after5Y));

        // Execute Run #2 over identical cohort and window to prove strict resumability and idempotency
        System.out.println("Executing Run #2 (Resumability / Idempotency Check)...");
        HistoricalNavIngestionService.TargetedBackfillResult run2Result = historicalNavIngestionService.ingestHistoricalNavForSchemeOptions(
            cohort.stream().map(SchemeOption::getId).toList(), startDate, endDate
        );

        assertNotNull(run2Result);
        System.out.println("Run #2 Result: requested=" + run2Result.fundsRequested() + ", succeeded=" + run2Result.fundsSucceeded() +
            ", ingested=" + run2Result.totalObservationsIngested() + ", duplicatesSkipped=" + run2Result.totalDuplicatesSkipped());

        // In Run 2, zero new observations should be inserted because all artifacts were already recorded and processed
        assertEquals(0, run2Result.totalObservationsIngested(), "Run #2 must insert 0 new observations due to exact artifact resumability");
        System.out.println("==================================================");
    }
}
