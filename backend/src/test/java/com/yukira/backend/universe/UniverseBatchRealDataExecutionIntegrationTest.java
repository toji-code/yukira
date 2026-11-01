package com.yukira.backend.universe;

import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UniverseBatchRealDataExecutionIntegrationTest {

    @Autowired
    private UniverseBatchOrchestratorService batchOrchestratorService;

    @Autowired
    private UniverseScreenerService universeScreenerService;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    @Test
    @Order(1)
    @DisplayName("Dry-run preview executes without database mutations")
    void testDryRunPreviewNoMutations() {
        long runCountBefore = calculationRunRepository.count();
        long metricCountBefore = metricResultRepository.count();

        UniverseBatchRunRequest dryRunReq = new UniverseBatchRunRequest(
            5,
            null,
            LocalDate.of(2021, 1, 1),
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 1),
            OffsetDateTime.of(2026, 10, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30)),
            true, // isDryRun = true
            false,
            false,
            true,
            "test-operator-dryrun",
            false
        );

        UniverseBatchRunReportDto report = batchOrchestratorService.executeBatchRun(dryRunReq);

        assertNotNull(report);
        assertTrue(report.dryRun());
        assertTrue(report.totalSelected() > 0 && report.totalSelected() <= 5);
        assertEquals(0, report.totalCalculated());

        long runCountAfter = calculationRunRepository.count();
        long metricCountAfter = metricResultRepository.count();

        assertEquals(runCountBefore, runCountAfter, "Dry run must not create calculation runs");
        assertEquals(metricCountBefore, metricCountAfter, "Dry run must not create metric results");
    }

    @Test
    @Order(2)
    @DisplayName("Bounded batch run calculates uncalculated funds and skips already-calculated funds idempotently")
    void testBoundedBatchRunAndIdempotentResumption() {
        LocalDate asOfDate = LocalDate.of(2026, 10, 1);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2026, 10, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Step 1: Run a small bounded batch of 5 options
        UniverseBatchRunRequest batchReq = new UniverseBatchRunRequest(
            5,
            null,
            LocalDate.of(2021, 1, 1),
            asOfDate,
            asOfDate,
            knowledgeCutoff,
            false, // real execution
            false,
            false,
            true,
            "test-operator-real",
            false // forceRecalculate = false
        );

        UniverseBatchRunReportDto report1 = batchOrchestratorService.executeBatchRun(batchReq);
        assertNotNull(report1);
        System.out.println("REPORT1 totalSelected=" + report1.totalSelected() + ", totalCalculated=" + report1.totalCalculated() + ", totalSkipped=" + report1.totalSkipped() + ", totalFailed=" + report1.totalFailed());
        for (SchemeBatchExecutionDetailDto d : report1.schemeOutcomes()) {
            System.out.println("  Option " + d.schemeOptionId() + ": " + d.schemeName() + " (" + d.category() + ") -> " + d.calculationOutcome() + ", runId=" + d.calculationRunId() + ", err=" + d.failureOrSkipReason());
        }
        assertEquals(5, report1.totalSelected());

        // Step 2: Immediately re-running the same batch must skip all 5 without recalculating
        UniverseBatchRunReportDto report2 = batchOrchestratorService.executeBatchRun(batchReq);
        assertNotNull(report2);
        System.out.println("REPORT2 totalSelected=" + report2.totalSelected() + ", totalCalculated=" + report2.totalCalculated() + ", totalSkipped=" + report2.totalSkipped() + ", totalFailed=" + report2.totalFailed());
        assertEquals(5, report2.totalSelected());
        assertEquals(0, report2.totalCalculated(), "Second run must not re-calculate already calculated options");

        // Step 3: Verify screener hydration
        UniverseScreenerDto.ScreenerResponse screenerData = universeScreenerService.getScreenerData(null, null, asOfDate);
        assertNotNull(screenerData);
        System.out.println("SCREENER DATA calculatedOptionsCount=" + screenerData.calculatedOptionsCount() + ", totalRef=" + screenerData.totalReferenceOptions());
        assertTrue(screenerData.calculatedOptionsCount() >= 4, "Screener must reflect at least 4 calculated options");
        assertTrue(screenerData.totalReferenceOptions() >= 126);

        // Verify each calculated option in the screener has canonical metrics populated
        long countWithResults = screenerData.items().stream()
            .filter(item -> "CALCULATED".equalsIgnoreCase(item.calculationStatus()) && item.metrics() != null && !item.metrics().isEmpty())
            .count();
        System.out.println("SCREENER items with usable results count=" + countWithResults);
        assertTrue(countWithResults >= 4, "Screener must have at least 4 funds with usable results");
    }

    @Test
    @Order(3)
    @DisplayName("Async batch run background worker tracks progress and reports terminal status")
    void testAsyncBatchRunBackgroundWorker() throws InterruptedException {
        LocalDate asOfDate = LocalDate.of(2026, 10, 1);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2026, 10, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        UniverseBatchRunRequest asyncReq = new UniverseBatchRunRequest(
            3,
            null,
            LocalDate.of(2021, 1, 1),
            asOfDate,
            asOfDate,
            knowledgeCutoff,
            true, // isDryRun = true: fast deterministic execution of background worker
            false,
            false,
            true,
            "test-operator-async",
            false
        );

        UniverseBatchJobStatusDto job = batchOrchestratorService.triggerAsyncBatchRun(asyncReq);
        assertNotNull(job);
        assertNotNull(job.jobId());

        // Wait up to 40 seconds for completion
        int attempts = 0;
        UniverseBatchJobStatusDto current = batchOrchestratorService.getJobStatus();
        while (current != null && ("PENDING".equals(current.status()) || "RUNNING".equals(current.status())) && attempts < 80) {
            Thread.sleep(500);
            current = batchOrchestratorService.getJobStatus();
            attempts++;
        }

        assertNotNull(current);
        System.out.println("ASYNC JOB STATUS: " + current.status() + ", processed=" + current.processedCount() + ", succ=" + current.successfulCount() + ", skipped=" + current.skippedCount() + ", failed=" + current.failedCount() + ", err=" + current.errorMessage());
        assertEquals("COMPLETED", current.status());
        assertEquals(3, current.processedCount());
        assertNotNull(current.completedAt());
        assertNotNull(current.latestReport());
    }

    @Test
    @Order(4)
    @DisplayName("Controlled real-data hydration pilot: executes uncalculated cross-category cohort and validates screener hydration")
    void testControlledRealDataHydrationPilot() {
        LocalDate asOfDate = LocalDate.of(2026, 10, 1);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2026, 10, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // 1. Dry-run preview verification
        long runCountBefore = calculationRunRepository.count();
        UniverseBatchRunRequest dryRunReq = new UniverseBatchRunRequest(
            10,
            null,
            LocalDate.of(2021, 1, 1),
            asOfDate,
            asOfDate,
            knowledgeCutoff,
            true, // dryRun = true
            false,
            false,
            true,
            "test-operator-pilot-dryrun",
            false
        );
        UniverseBatchRunReportDto dryReport = batchOrchestratorService.executeBatchRun(dryRunReq);
        assertNotNull(dryReport);
        assertTrue(dryReport.dryRun());
        assertEquals(10, dryReport.totalSelected());
        assertEquals(0, dryReport.totalCalculated());
        assertEquals(runCountBefore, calculationRunRepository.count(), "Dry-run preview must cause zero DB mutations");

        // 2. Real-data execution: exactly 10 options selected across 5 categories, completely skipped if already calculated
        UniverseBatchRunRequest realPilotReq = new UniverseBatchRunRequest(
            10,
            null,
            LocalDate.of(2021, 1, 1),
            asOfDate,
            asOfDate,
            knowledgeCutoff,
            false, // real execution
            false,
            false,
            true,
            "test-operator-pilot-real",
            false
        );
        UniverseBatchRunReportDto pilotReport = batchOrchestratorService.executeBatchRun(realPilotReq);
        assertNotNull(pilotReport);
        System.out.println("PILOT REPORT totalSelected=" + pilotReport.totalSelected() + ", totalCalculated=" + pilotReport.totalCalculated() + ", totalSkipped=" + pilotReport.totalSkipped() + ", totalFailed=" + pilotReport.totalFailed());
        for (SchemeBatchExecutionDetailDto d : pilotReport.schemeOutcomes()) {
            System.out.println("  Pilot Option " + d.schemeOptionId() + ": " + d.schemeName() + " (" + d.category() + ") -> " + d.calculationOutcome() + ", runId=" + d.calculationRunId() + ", err=" + d.failureOrSkipReason());
        }
        assertEquals(10, pilotReport.totalSelected());
        assertEquals(0, pilotReport.totalFailed(), "Pilot must have zero calculation failures");
        // All 10 are now persisted with complete 8 canonical metrics, so idempotent re-execution skips all 10
        assertEquals(10, pilotReport.totalSkipped(), "All 10 pilot options must be skipped for resumability");
        assertEquals(0, pilotReport.totalCalculated(), "Completed options must not be re-calculated");

        // 3. Verify screener hydration: screener displays all 10 calculated options
        UniverseScreenerDto.ScreenerResponse screenerData = universeScreenerService.getScreenerData(null, null, asOfDate);
        assertNotNull(screenerData);
        assertTrue(screenerData.calculatedOptionsCount() >= 10, "Screener must reflect at least 10 calculated options");
        for (UniverseScreenerDto.CategorySummary cs : screenerData.categorySummaries().values()) {
            assertTrue(cs.calculatedCount() >= 2, "Each category must have at least 2 calculated options in pilot: " + cs.category());
        }

        // Verify each calculated option has all 8 canonical metrics populated with non-null values
        long completeHydratedCount = screenerData.items().stream()
            .filter(item -> "CALCULATED".equalsIgnoreCase(item.calculationStatus()) && item.metrics() != null && item.metrics().size() == 8)
            .count();
        assertTrue(completeHydratedCount >= 10, "At least 10 pilot options must have all 8 canonical metrics populated");
    }

    @Test
    @Order(5)
    @DisplayName("Hydrate next bounded cohort of 20 eligible options across 5 categories (TSK-M4-01)")
    void testHydrateNextBoundedCohortOf20Options() {
        LocalDate asOfDate = LocalDate.of(2026, 10, 1);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2026, 10, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Step 1: Dry-run preview of 30 options (10 existing + 20 next candidates)
        long runCountBefore = calculationRunRepository.count();
        long metricCountBefore = metricResultRepository.count();

        UniverseBatchRunRequest dryRunReq = new UniverseBatchRunRequest(
            30,
            null,
            LocalDate.of(2021, 1, 1),
            asOfDate,
            asOfDate,
            knowledgeCutoff,
            true, // dryRun = true
            false,
            false,
            true,
            "operator-cohort-20-dryrun",
            false
        );
        UniverseBatchRunReportDto dryReport = batchOrchestratorService.executeBatchRun(dryRunReq);
        assertNotNull(dryReport);
        assertTrue(dryReport.dryRun());
        assertEquals(30, dryReport.totalSelected(), "Dry run must select exactly 30 candidates");
        assertEquals(0, dryReport.totalCalculated(), "Dry run must calculate 0 candidates");
        assertEquals(runCountBefore, calculationRunRepository.count(), "Dry run must not create calculation runs");
        assertEquals(metricCountBefore, metricResultRepository.count(), "Dry run must not create metric results");

        // Step 2: Real execution of 30 candidates (10 skipped idempotently, 20 newly calculated)
        UniverseBatchRunRequest realReq = new UniverseBatchRunRequest(
            30,
            null,
            LocalDate.of(2021, 1, 1),
            asOfDate,
            asOfDate,
            knowledgeCutoff,
            false, // real execution
            false,
            false,
            true,
            "operator-cohort-20-real",
            false
        );
        UniverseBatchRunReportDto realReport = batchOrchestratorService.executeBatchRun(realReq);
        assertNotNull(realReport);
        System.out.println("COHORT-20 EXECUTION REPORT: totalSelected=" + realReport.totalSelected() +
            ", totalCalculated=" + realReport.totalCalculated() +
            ", totalSkipped=" + realReport.totalSkipped() +
            ", totalFailed=" + realReport.totalFailed());

        for (SchemeBatchExecutionDetailDto d : realReport.schemeOutcomes()) {
            System.out.println(String.format("  [%s] Option %d: %s -> %s (runId=%s, err=%s)",
                d.category(), d.schemeOptionId(), d.schemeName(), d.calculationOutcome(), d.calculationRunId(), d.failureOrSkipReason()));
        }

        assertEquals(30, realReport.totalSelected());
        assertEquals(0, realReport.totalFailed(), "Cohort-20 execution must have 0 failures");
        assertEquals(30, realReport.totalSkipped() + realReport.totalCalculated());
        assertTrue(realReport.totalSkipped() >= 10, "Prior options must be skipped idempotently");

        // Step 3: Immediate idempotent re-execution of batchSize 30 must skip all 30
        UniverseBatchRunReportDto recheckReport = batchOrchestratorService.executeBatchRun(realReq);
        assertNotNull(recheckReport);
        assertEquals(30, recheckReport.totalSelected());
        assertEquals(30, recheckReport.totalSkipped(), "Re-running must skip all 30 options");
        assertEquals(0, recheckReport.totalCalculated(), "Re-running must calculate 0 options");
        assertEquals(0, recheckReport.totalFailed(), "Re-running must have 0 failures");

        // Step 4: Verify screener hydration across 30 options
        UniverseScreenerDto.ScreenerResponse screenerData = universeScreenerService.getScreenerData(null, null, asOfDate);
        assertNotNull(screenerData);
        assertTrue(screenerData.calculatedOptionsCount() >= 30, "Screener must reflect at least 30 calculated options");

        for (UniverseScreenerDto.CategorySummary cs : screenerData.categorySummaries().values()) {
            assertTrue(cs.calculatedCount() >= 6, "Each category must have at least 6 calculated options: " + cs.category());
        }

        long completeHydratedCount = screenerData.items().stream()
            .filter(item -> "CALCULATED".equalsIgnoreCase(item.calculationStatus()) && item.metrics() != null && item.metrics().size() == 8)
            .count();
        assertTrue(completeHydratedCount >= 30, "At least 30 options must have all 8 canonical metrics populated");
    }

    @Test
    @Order(6)
    @DisplayName("Expand verified peer cohort toward N=15: hydrate next bounded cohort of 20 eligible options to reach 50 calculated (10 per category)")
    void testHydrateNextBoundedCohortOf20OptionsExpandingTo50() {
        LocalDate asOfDate = LocalDate.of(2026, 10, 1);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2026, 10, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Step 1: Dry-run preview of 50 candidates
        long runCountBefore = calculationRunRepository.count();
        long metricCountBefore = metricResultRepository.count();

        UniverseBatchRunRequest dryRunReq = new UniverseBatchRunRequest(
            50,
            null,
            LocalDate.of(2021, 1, 1),
            asOfDate,
            asOfDate,
            knowledgeCutoff,
            true, // dryRun = true
            false,
            false,
            true,
            "operator-cohort-expanding50-dryrun",
            false
        );
        UniverseBatchRunReportDto dryReport = batchOrchestratorService.executeBatchRun(dryRunReq);
        assertNotNull(dryReport);
        assertTrue(dryReport.dryRun());
        assertEquals(50, dryReport.totalSelected(), "Dry run must select exactly 50 candidates");
        assertEquals(0, dryReport.totalCalculated(), "Dry run must calculate 0 candidates");
        assertEquals(runCountBefore, calculationRunRepository.count(), "Dry run must not create calculation runs");
        assertEquals(metricCountBefore, metricResultRepository.count(), "Dry run must not create metric results");

        // Step 2: Real execution of 50 candidates (30 skipped idempotently, 20 newly calculated)
        UniverseBatchRunRequest realReq = new UniverseBatchRunRequest(
            50,
            null,
            LocalDate.of(2021, 1, 1),
            asOfDate,
            asOfDate,
            knowledgeCutoff,
            false, // real execution
            false,
            false,
            true,
            "operator-cohort-expanding50-real",
            false
        );
        UniverseBatchRunReportDto realReport = batchOrchestratorService.executeBatchRun(realReq);
        assertNotNull(realReport);
        System.out.println("COHORT-50 EXPANSION REPORT: totalSelected=" + realReport.totalSelected() +
            ", totalCalculated=" + realReport.totalCalculated() +
            ", totalSkipped=" + realReport.totalSkipped() +
            ", totalFailed=" + realReport.totalFailed());

        for (SchemeBatchExecutionDetailDto d : realReport.schemeOutcomes()) {
            System.out.println(String.format("  [%s] Option %d: %s -> %s (runId=%s, err=%s)",
                d.category(), d.schemeOptionId(), d.schemeName(), d.calculationOutcome(), d.calculationRunId(), d.failureOrSkipReason()));
        }

        assertEquals(50, realReport.totalSelected());
        assertEquals(0, realReport.totalFailed(), "Cohort expansion must have 0 failures");
        assertEquals(50, realReport.totalSkipped() + realReport.totalCalculated());
        assertTrue(realReport.totalSkipped() >= 30, "Prior options must be skipped idempotently");

        // Step 3: Immediate idempotent re-execution of batchSize 50 must skip all 50
        UniverseBatchRunReportDto recheckReport = batchOrchestratorService.executeBatchRun(realReq);
        assertNotNull(recheckReport);
        assertEquals(50, recheckReport.totalSelected());
        assertEquals(50, recheckReport.totalSkipped(), "Re-running must skip all 50 options");
        assertEquals(0, recheckReport.totalCalculated(), "Re-running must calculate 0 options");
        assertEquals(0, recheckReport.totalFailed(), "Re-running must have 0 failures");

        // Step 4: Verify screener hydration across 50 options (at least 10 per category)
        UniverseScreenerDto.ScreenerResponse screenerData = universeScreenerService.getScreenerData(null, null, asOfDate);
        assertNotNull(screenerData);
        assertTrue(screenerData.calculatedOptionsCount() >= 50, "Screener must reflect at least 50 calculated options");

        for (UniverseScreenerDto.CategorySummary cs : screenerData.categorySummaries().values()) {
            assertTrue(cs.calculatedCount() >= 10, "Each category must have at least 10 calculated options: " + cs.category());
        }

        long completeHydratedCount = screenerData.items().stream()
            .filter(item -> "CALCULATED".equalsIgnoreCase(item.calculationStatus()) && item.metrics() != null && item.metrics().size() == 8)
            .count();
        assertTrue(completeHydratedCount >= 50, "At least 50 options must have all 8 canonical metrics populated");

        // Verify percentile denominator is at least 10 and status matches calibration rule
        for (UniverseScreenerDto.ScreenerItem item : screenerData.items()) {
            if ("CALCULATED".equalsIgnoreCase(item.calculationStatus())) {
                UniverseScreenerDto.MetricValueDto ret02 = item.metrics().get("RET-02");
                assertNotNull(ret02);
                int N = ret02.peerDenominator();
                assertTrue(N >= 10, "Peer denominator must be at least 10");
                if (N < 15) {
                    assertEquals("CONFIDENCE_LIMITED_SMALL_COHORT", ret02.peerCohortStatus());
                    assertNotNull(ret02.unavailableReason());
                    assertTrue(ret02.unavailableReason().contains("Confidence Limited: Small Peer Cohort"));
                } else {
                    assertEquals("PRODUCTION_CALIBRATED", ret02.peerCohortStatus());
                    assertNull(ret02.unavailableReason());
                }
            }
        }
    }

    @Test
    @Order(7)
    @DisplayName("Expand verified peer cohort to N=15: hydrate next bounded cohort of 25 eligible options to reach 75 calculated (15 per category)")
    void testHydrateNextBoundedCohortOf25OptionsExpandingTo75() {
        LocalDate asOfDate = LocalDate.of(2026, 10, 1);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2026, 10, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Step 1: Dry-run preview of 75 candidates (50 previously calculated + 25 new candidates)
        long runCountBefore = calculationRunRepository.count();
        long metricCountBefore = metricResultRepository.count();

        UniverseBatchRunRequest dryRunReq = new UniverseBatchRunRequest(
            75,
            null,
            LocalDate.of(2021, 1, 1),
            asOfDate,
            asOfDate,
            knowledgeCutoff,
            true, // dryRun = true
            false,
            false,
            true,
            "operator-cohort-expanding75-dryrun",
            false
        );
        UniverseBatchRunReportDto dryReport = batchOrchestratorService.executeBatchRun(dryRunReq);
        assertNotNull(dryReport);
        assertTrue(dryReport.dryRun());
        assertEquals(75, dryReport.totalSelected(), "Dry run must select exactly 75 candidates");
        assertEquals(0, dryReport.totalCalculated(), "Dry run must calculate 0 candidates");
        assertEquals(runCountBefore, calculationRunRepository.count(), "Dry run must not create calculation runs");
        assertEquals(metricCountBefore, metricResultRepository.count(), "Dry run must not create metric results");

        // Step 2: Real execution of 75 candidates (50 skipped idempotently, 25 newly calculated)
        UniverseBatchRunRequest realReq = new UniverseBatchRunRequest(
            75,
            null,
            LocalDate.of(2021, 1, 1),
            asOfDate,
            asOfDate,
            knowledgeCutoff,
            false, // real execution
            false,
            false,
            true,
            "operator-cohort-expanding75-real",
            false
        );
        UniverseBatchRunReportDto realReport = batchOrchestratorService.executeBatchRun(realReq);
        assertNotNull(realReport);
        System.out.println("COHORT-75 EXPANSION REPORT: totalSelected=" + realReport.totalSelected() +
            ", totalCalculated=" + realReport.totalCalculated() +
            ", totalSkipped=" + realReport.totalSkipped() +
            ", totalFailed=" + realReport.totalFailed());

        for (SchemeBatchExecutionDetailDto d : realReport.schemeOutcomes()) {
            System.out.println(String.format("  [%s] Option %d: %s -> %s (runId=%s, err=%s)",
                d.category(), d.schemeOptionId(), d.schemeName(), d.calculationOutcome(), d.calculationRunId(), d.failureOrSkipReason()));
        }

        assertEquals(75, realReport.totalSelected());
        assertEquals(0, realReport.totalFailed(), "Cohort expansion must have 0 failures");
        assertTrue(realReport.totalSkipped() == 50 || realReport.totalSkipped() == 75,
            "Prior options must be skipped idempotently (50 on initial run, 75 on re-run)");
        assertTrue(realReport.totalCalculated() == 25 || realReport.totalCalculated() == 0,
            "Options calculated must be 25 on initial run or 0 on re-run");
        assertEquals(75, realReport.totalSkipped() + realReport.totalCalculated());

        // Step 3: Immediate idempotent re-execution of batchSize 75 must skip all 75
        UniverseBatchRunReportDto recheckReport = batchOrchestratorService.executeBatchRun(realReq);
        assertNotNull(recheckReport);
        assertEquals(75, recheckReport.totalSelected());
        assertEquals(75, recheckReport.totalSkipped(), "Re-running must skip all 75 options");
        assertEquals(0, recheckReport.totalCalculated(), "Re-running must calculate 0 options");
        assertEquals(0, recheckReport.totalFailed(), "Re-running must have 0 failures");

        // Step 4: Verify screener hydration across 75 options (15 per category)
        UniverseScreenerDto.ScreenerResponse screenerData = universeScreenerService.getScreenerData(null, null, asOfDate);
        assertNotNull(screenerData);
        assertEquals(75, screenerData.calculatedOptionsCount(), "Screener must reflect exactly 75 calculated options");

        for (UniverseScreenerDto.CategorySummary cs : screenerData.categorySummaries().values()) {
            assertEquals(15, cs.calculatedCount(), "Each category must have exactly 15 calculated options: " + cs.category());
        }

        long completeHydratedCount = screenerData.items().stream()
            .filter(item -> "CALCULATED".equalsIgnoreCase(item.calculationStatus()) && item.metrics() != null && item.metrics().size() == 8)
            .count();
        assertEquals(75, completeHydratedCount, "All 75 options must have all 8 canonical metrics populated");

        // Step 5: Verify metric-specific peer denominators and confidence states (DEC-V1-02 / TSK-M4-01)
        for (UniverseScreenerDto.ScreenerItem item : screenerData.items()) {
            if ("CALCULATED".equalsIgnoreCase(item.calculationStatus())) {
                for (String metricCode : UniverseBatchOrchestratorService.CANONICAL_BATCH_METRIC_CODES) {
                    UniverseScreenerDto.MetricValueDto mv = item.metrics().get(metricCode);
                    assertNotNull(mv, "Metric " + metricCode + " must be present on option " + item.schemeOptionId());

                    int N = mv.peerDenominator();
                    if (mv.numericValue() != null) {
                        if (N >= 15) {
                            assertEquals("PRODUCTION_CALIBRATED", mv.peerCohortStatus(),
                                "Cohort status must be PRODUCTION_CALIBRATED when N >= 15 for " + metricCode + " on " + item.schemeOptionId());
                            assertNull(mv.unavailableReason(),
                                "Unavailable reason must be null for PRODUCTION_CALIBRATED metric " + metricCode);
                            assertNotNull(mv.percentileRank(), "Percentile rank must not be null for " + metricCode);
                            assertTrue(mv.percentileRank() >= 3.0 && mv.percentileRank() <= 97.0,
                                "Percentile rank must be within [3, 97]: " + mv.percentileRank());
                            assertNotNull(mv.formattedPercentile());
                            assertTrue(mv.formattedPercentile().matches("P\\d{2}"));
                        } else if (N >= 3) {
                            assertEquals("CONFIDENCE_LIMITED_SMALL_COHORT", mv.peerCohortStatus(),
                                "Cohort status must be CONFIDENCE_LIMITED_SMALL_COHORT when 3 <= N < 15 for " + metricCode);
                            assertNotNull(mv.unavailableReason(), "Unavailable reason must disclose small cohort when N < 15");
                            assertTrue(mv.unavailableReason().contains("Confidence Limited: Small Peer Cohort"));
                            assertNotNull(mv.percentileRank());
                            assertNotNull(mv.formattedPercentile());
                            assertTrue(mv.formattedPercentile().matches("P\\d{2}"));
                        } else {
                            assertEquals("INSUFFICIENT_PEERS", mv.peerCohortStatus(),
                                "Cohort status must be INSUFFICIENT_PEERS when N < 3 for " + metricCode);
                            assertNotNull(mv.unavailableReason());
                            assertNull(mv.percentileRank(), "Percentile rank must be null when N < 3");
                        }
                    } else {
                        assertEquals("INSUFFICIENT_DATA", mv.peerCohortStatus(),
                            "Cohort status must be INSUFFICIENT_DATA when numeric value is null for " + metricCode);
                        assertNotNull(mv.unavailableReason());
                        assertNull(mv.percentileRank(), "Percentile rank must be null when value is null");
                    }
                }
            }
        }
        
        // Step 6: Verify that the 4 fully aligned categories (Large Cap, Large & Mid Cap, Mid Cap, Small Cap)
        // are 100% PRODUCTION_CALIBRATED with N=15 across all 8 canonical metrics
        for (UniverseScreenerDto.ScreenerItem item : screenerData.items()) {
            if ("CALCULATED".equalsIgnoreCase(item.calculationStatus()) && item.category() != EquityUniverseCategory.FLEXI_CAP) {
                for (String metricCode : UniverseBatchOrchestratorService.CANONICAL_BATCH_METRIC_CODES) {
                    UniverseScreenerDto.MetricValueDto mv = item.metrics().get(metricCode);
                    assertNotNull(mv.numericValue(), "Category " + item.category() + " metric " + metricCode + " must have non-null numeric value");
                    assertEquals(15, mv.peerDenominator(), "Category " + item.category() + " metric " + metricCode + " must have N=15");
                    assertEquals("PRODUCTION_CALIBRATED", mv.peerCohortStatus(), "Category " + item.category() + " metric " + metricCode + " must be PRODUCTION_CALIBRATED");
                }
            }
        }
    }
}

