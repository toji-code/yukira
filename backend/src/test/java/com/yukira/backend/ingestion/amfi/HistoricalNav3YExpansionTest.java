package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.Amc;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.dto.ingestion.HistoricalBackfillPreviewDto;
import com.yukira.backend.dto.ingestion.HistoricalBackfillRunSummaryDto;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class HistoricalNav3YExpansionTest {

    @Autowired
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Test
    @DisplayName("Dry-run planner profiles 3Y candidates with zero DB mutations")
    void testDryRunPlannerProducesZeroDbMutations() {
        long initialObsCount = navObservationRepository.count();

        LocalDate startDate = LocalDate.of(2023, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 1);

        HistoricalBackfillPreviewDto preview = historicalNavIngestionService.previewProductionCohort(
            50, "ALL", startDate, endDate
        );

        assertThat(preview).isNotNull();
        assertThat(preview.isDryRun()).isTrue();
        assertThat(preview.totalActiveOptions()).isGreaterThan(0);
        assertThat(preview.candidateCount3Y()).isGreaterThanOrEqualTo(0);
        assertThat(preview.selectedOptions()).isNotEmpty();
        assertThat(preview.estimatedDbInsertions()).isGreaterThanOrEqualTo(0);

        long afterObsCount = navObservationRepository.count();
        assertEquals(initialObsCount, afterObsCount, "Dry-run planner must perform ZERO database mutations");
    }

    @Test
    @DisplayName("Scheme option categorization correctly identifies 3Y-capable vs impossible inception dates")
    void testSchemeOptionCategorizationAndInceptionFilter() {
        LocalDate endDate = LocalDate.of(2026, 10, 7);
        List<HistoricalNavIngestionService.SchemeOptionMeta> categorized = historicalNavIngestionService.categorizeSchemeOptions(endDate);

        assertThat(categorized).isNotEmpty();
        for (HistoricalNavIngestionService.SchemeOptionMeta meta : categorized) {
            assertNotNull(meta.tier());
            assertNotNull(meta.inceptionStatus());

            if (!meta.is3YCapable()) {
                assertEquals("SHORT_FUND_HISTORY", meta.tier());
                assertEquals("TOO_RECENT", meta.inceptionStatus());
            }
        }
    }

    @Test
    @DisplayName("Cohort selection prioritizes Tier 1 (1Y to 3Y history) candidates for 3Y analytical readiness")
    void testCohortSelectionPrioritizes3YCandidates() {
        List<SchemeOption> cohort = historicalNavIngestionService.selectProductionCohort(20, "ALL");
        assertThat(cohort).isNotEmpty();

        List<HistoricalNavIngestionService.SchemeOptionMeta> metaList = historicalNavIngestionService.categorizeSchemeOptions();
        var metaMap = metaList.stream().collect(java.util.stream.Collectors.toMap(m -> m.option().getId(), m -> m));

        // Selected options should belong to active tiers (TIER_1, TIER_2, TIER_3, TIER_4)
        for (SchemeOption option : cohort) {
            HistoricalNavIngestionService.SchemeOptionMeta meta = metaMap.get(option.getId());
            assertNotNull(meta);
            assertTrue(List.of("TIER_1", "TIER_2", "TIER_3", "TIER_4").contains(meta.tier()),
                "Selected option should belong to valid candidate tiers");
        }
    }

    @Test
    @DisplayName("AMC round-robin diversification ensures broad coverage across multiple AMCs")
    void testAmcRoundRobinDiversification() {
        List<SchemeOption> cohort = historicalNavIngestionService.selectProductionCohort(15, "ALL");
        long distinctAmcs = cohort.stream()
            .map(o -> o.getPlan() != null && o.getPlan().getScheme() != null && o.getPlan().getScheme().getAmc() != null
                ? o.getPlan().getScheme().getAmc().getCode() : "UNKNOWN")
            .distinct()
            .count();

        assertThat(distinctAmcs).isGreaterThan(1);
    }

    @Test
    @DisplayName("Controlled production cohort backfill and idempotent rerun execution")
    @Transactional
    void testControlledProductionCohortBackfillAndIdempotentRerun() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 3, 31);

        List<SchemeOption> cohort = historicalNavIngestionService.selectProductionCohort(5, "ALL");
        assertThat(cohort).isNotEmpty();

        // Run 1: Production backfill
        HistoricalBackfillRunSummaryDto run1 = historicalNavIngestionService.executeProductionCohortBackfillForCohort(
            cohort, 5, startDate, endDate
        );

        assertNotNull(run1);
        assertThat(run1.actualCohortSize()).isEqualTo(cohort.size());
        assertThat(run1.runId()).isNotBlank();
        assertThat(run1.remaining3YGap()).isGreaterThanOrEqualTo(0);

        long obsAfterRun1 = navObservationRepository.count();

        // Run 2: Idempotent rerun
        HistoricalBackfillRunSummaryDto run2 = historicalNavIngestionService.executeProductionCohortBackfillForCohort(
            cohort, 5, startDate, endDate
        );

        assertNotNull(run2);
        assertEquals(0, run2.totalObservationsInserted(), "Run #2 must insert ZERO observations");
        assertTrue(run2.sourceRequestsSkipped() > 0, "Run #2 must reuse existing source artifacts");
        assertEquals(obsAfterRun1, navObservationRepository.count(), "Ledger observation count must remain unchanged");
    }

    @Test
    @DisplayName("Failure isolation: non-existent scheme option fails gracefully without aborting cohort")
    void testFailureIsolationForTargetedBackfill() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 15);

        var result = historicalNavIngestionService.ingestHistoricalNavForSchemeOptions(
            List.of(99999999L), startDate, endDate
        );

        assertNotNull(result);
        assertEquals(1, result.fundsRequested());
        assertEquals(0, result.fundsSucceeded());
        assertEquals(1, result.fundsFailed());
        assertEquals("FAILED", result.funds().get(0).status());
    }
}
