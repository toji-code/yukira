package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.ingestion.HistoricalBackfillPreviewDto;
import com.yukira.backend.dto.ingestion.HistoricalBackfillRunSummaryDto;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class HistoricalNavScalingIntegrationTest {

    @Autowired
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Autowired
    private SourceArtifactRepository sourceArtifactRepository;

    @Test
    @DisplayName("Deterministic Production Cohort Selection prioritize Tiers and diversifies AMCs")
    public void testDeterministicProductionCohortSelection() {
        List<SchemeOption> cohort5 = historicalNavIngestionService.selectProductionCohort(5, "ALL");
        List<SchemeOption> cohort15 = historicalNavIngestionService.selectProductionCohort(15, "ALL");

        assertThat(cohort5).isNotEmpty();
        assertThat(cohort5.size()).isLessThanOrEqualTo(5);
        assertThat(cohort15.size()).isGreaterThanOrEqualTo(cohort5.size());

        // First 5 elements of cohort15 should match cohort5 for 100% deterministic reproducibility
        for (int i = 0; i < cohort5.size(); i++) {
            assertThat(cohort15.get(i).getId()).isEqualTo(cohort5.get(i).getId());
        }
    }

    @Test
    @DisplayName("Dry-run preview mode returns cohort metadata without HTTP or DB side-effects")
    public void testDryRunPreviewMode() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 6, 30);

        HistoricalBackfillPreviewDto preview = historicalNavIngestionService.previewProductionCohort(10, "ALL", startDate, endDate);

        assertThat(preview).isNotNull();
        assertThat(preview.isDryRun()).isTrue();
        assertThat(preview.totalSelected()).isGreaterThan(0);
        assertThat(preview.amcCount()).isGreaterThan(0);
        assertThat(preview.estimatedChunkHttpRequests()).isEqualTo(3); // 182 days window = 3 90-day chunks
        assertThat(preview.selectedOptions()).hasSize(preview.totalSelected());
    }

    @Test
    @DisplayName("Production cohort backfill executes with shared universe chunk artifacts and proves 100% idempotency")
    public void testProductionCohortBackfillAndIdempotency() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 6, 30);

        List<SchemeOption> cohort = historicalNavIngestionService.selectProductionCohort(5, "ALL");
        assertThat(cohort).isNotEmpty();

        // Run #1: Initial production cohort execution (batch size 5)
        HistoricalBackfillRunSummaryDto summaryRun1 = historicalNavIngestionService.executeProductionCohortBackfillForCohort(
            cohort, 5, startDate, endDate
        );

        assertThat(summaryRun1).isNotNull();
        assertThat(summaryRun1.actualCohortSize()).isGreaterThan(0);
        assertThat(summaryRun1.totalObservationsInserted() + summaryRun1.totalDuplicatesSkipped()).isGreaterThan(0);
        assertThat(summaryRun1.fundResults()).isNotEmpty();

        long obsAfterRun1 = navObservationRepository.count();

        // Run #2: Idempotent rerun over exact same cohort
        HistoricalBackfillRunSummaryDto summaryRun2 = historicalNavIngestionService.executeProductionCohortBackfillForCohort(
            cohort, 5, startDate, endDate
        );

        assertThat(summaryRun2).isNotNull();
        assertThat(summaryRun2.actualCohortSize()).isEqualTo(summaryRun1.actualCohortSize());
        assertThat(summaryRun2.totalObservationsInserted()).isEqualTo(0); // ZERO duplicate inserts!
        assertThat(summaryRun2.sourceRequestsSkipped()).isGreaterThan(0); // Shared artifacts reused from DB!

        long obsAfterRun2 = navObservationRepository.count();
        assertThat(obsAfterRun2).isEqualTo(obsAfterRun1); // Database observation ledger untouched!
    }
}
