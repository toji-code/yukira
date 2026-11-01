package com.yukira.backend.universe;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-data integration verification for TSK-M1-06 bounded universe expansion and calculation workflow.
 *
 * Verifies live execution against authoritative PostgreSQL database records and quant-engine daemon:
 * 1. Reference Universe 5-category coverage inspection.
 * 2. Deterministic batch selection and dry-run preview safety.
 * 3. Real multi-fund calculation run for eligible Flexi Cap cohort against NIFTY_500_TRI.
 * 4. Missing benchmark blocking (Large Cap with NIFTY_100_TRI having 0 observations).
 * 5. Full calculation_run and metric_result persistence with provenance audit.
 */
@SpringBootTest
@Transactional
class UniverseBatchOrchestratorRealDataIntegrationTest {

    @Autowired
    private UniverseBatchOrchestratorService orchestratorService;

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    private static final LocalDate AS_OF_DATE = LocalDate.of(2024, 1, 15);
    private static final LocalDate START_DATE = LocalDate.of(2021, 1, 1);
    private static final OffsetDateTime KNOWLEDGE_CUTOFF =
        LocalDate.of(2024, 1, 15).atTime(23, 59, 59).atOffset(ZoneOffset.ofHoursMinutes(5, 30));

    @BeforeEach
    void setUp() {
        pilotBootstrapService.ensureCanonicalPilotMaster();
    }

    @Test
    @DisplayName("Real-Data: Inspect comprehensive 5-category reference universe data and benchmark coverage")
    void testRealDataUniverseCoverageStatus() {
        UniverseCoverageStatusDto coverage = orchestratorService.getUniverseCoverageStatus();

        assertNotNull(coverage);
        assertTrue(coverage.totalMasterOptions() >= 10000, "Master options count should exceed 10000 in real database");
        assertTrue(coverage.totalReferenceUniverseOptions() >= 150, "Reference universe should have >= 150 options across 5 categories");
        assertTrue(coverage.totalSufficientOptions() >= 100, "At least 100 options should already have >= 700 distinct dates");

        // Verify Flexi Cap category has NIFTY_500_TRI with sufficient 3Y coverage (> 1400 observations)
        CategoryCoverageDto flexiCat = coverage.categoryBreakdown().get("FLEXI_CAP");
        assertNotNull(flexiCat);
        assertEquals("NIFTY_500_TRI", flexiCat.candidateBenchmarkCode());
        assertEquals("CANDIDATE_METHODOLOGY_MAPPING", flexiCat.benchmarkGovernanceStatus());
        assertEquals("SUFFICIENT_3Y_COVERAGE", flexiCat.benchmarkCoverageStatus());
        assertTrue(flexiCat.benchmarkObservationCount() >= 1400);

        // Verify all 5 reference universe categories now have authentic candidate benchmarks with sufficient 3Y coverage
        for (EquityUniverseCategory cat : EquityUniverseCategory.values()) {
            CategoryCoverageDto catDto = coverage.categoryBreakdown().get(cat.name());
            assertNotNull(catDto, "Category breakdown must exist for " + cat);
            assertNotNull(catDto.candidateBenchmarkCode(), "Benchmark code must exist for " + cat);
            assertEquals("CANDIDATE_METHODOLOGY_MAPPING", catDto.benchmarkGovernanceStatus());
            assertEquals("SUFFICIENT_3Y_COVERAGE", catDto.benchmarkCoverageStatus(),
                "Benchmark " + catDto.candidateBenchmarkCode() + " for " + cat + " must have sufficient 3Y coverage");
            assertTrue(catDto.benchmarkObservationCount() >= 1400,
                "Benchmark " + catDto.candidateBenchmarkCode() + " must have >= 1400 observations");
        }
    }

    @Test
    @DisplayName("Real-Data: Dry-run preview accurately reports selected cohort and causes zero database mutations")
    void testRealDataDryRunPreview() {
        long initialRunCount = calculationRunRepository.count();

        UniverseBatchRunRequest req = new UniverseBatchRunRequest(
            3, EquityUniverseCategory.FLEXI_CAP, START_DATE, AS_OF_DATE, AS_OF_DATE,
            KNOWLEDGE_CUTOFF, true, false, false, true, "test-operator-dry"
        );

        UniverseBatchRunReportDto preview = orchestratorService.previewBatchRun(req);

        assertNotNull(preview);
        assertTrue(preview.dryRun());
        assertEquals(3, preview.totalSelected());
        assertEquals(3, preview.schemeOutcomes().size());

        for (SchemeBatchExecutionDetailDto detail : preview.schemeOutcomes()) {
            assertEquals("DRY_RUN_PREVIEW", detail.ingestionOutcome());
            assertEquals("DRY_RUN_PREVIEW", detail.calculationOutcome());
            assertNull(detail.calculationRunId());
        }

        assertEquals(initialRunCount, calculationRunRepository.count(), "Dry run must not create calculation_run records");
    }

    @Test
    @DisplayName("Real-Data: Bounded batch calculation executes real quant calculations and persists results for Flexi Cap cohort")
    void testRealDataBatchCalculationFlexiCapCohort() {
        UniverseBatchRunRequest req = new UniverseBatchRunRequest(
            2, EquityUniverseCategory.FLEXI_CAP, START_DATE, AS_OF_DATE, AS_OF_DATE,
            KNOWLEDGE_CUTOFF, false, false, false, true, "test-operator-live"
        );

        UniverseBatchRunReportDto report = orchestratorService.executeBatchRun(req);

        assertNotNull(report);
        assertFalse(report.dryRun());
        assertEquals(2, report.totalSelected());
        assertEquals(2, report.totalCalculated(), "Both eligible Flexi Cap schemes should calculate successfully");
        assertEquals(0, report.totalFailed());

        for (SchemeBatchExecutionDetailDto detail : report.schemeOutcomes()) {
            assertEquals("CALCULATED", detail.calculationOutcome());
            assertNotNull(detail.calculationRunId());
            assertEquals("NIFTY_500_TRI", detail.benchmarkCode());
            assertEquals("CANDIDATE_METHODOLOGY_MAPPING", detail.benchmarkGovernanceStatus());
            assertTrue(detail.finalDistinctTradingDays() >= 700);

            // Verify calculation_run entity in DB
            CalculationRun run = calculationRunRepository.findById(detail.calculationRunId()).orElse(null);
            assertNotNull(run);
            assertEquals("SUCCESS", run.getRunStatus());
            assertEquals(AS_OF_DATE, run.getAsOfDate());

            // Verify metric_result entities in DB
            List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
            assertFalse(results.isEmpty(), "Metric results should be persisted in DB");
            List<String> metricCodes = results.stream().map(MetricResult::getMetricCode).toList();
            assertTrue(metricCodes.contains("RET-02"));
            assertTrue(metricCodes.contains("RET-03"));
            assertTrue(metricCodes.contains("RSK-01"));
            assertTrue(metricCodes.contains("RSK-03"));
            assertTrue(metricCodes.contains("MKT-01"));
            assertTrue(metricCodes.contains("MKT-02"));
        }
    }

    @Test
    @DisplayName("Real-Data: Large Cap batch calculation succeeds and persists results with authentic NIFTY_100_TRI history")
    void testRealDataLargeCapBatchCalculationSucceeds() {
        LocalDate largeAsOfDate = LocalDate.of(2026, 10, 1);
        LocalDate largeStartDate = LocalDate.of(2023, 1, 1);
        OffsetDateTime largeKnowledgeCutoff = largeAsOfDate.atTime(23, 59, 59).atOffset(ZoneOffset.ofHoursMinutes(5, 30));

        UniverseBatchRunRequest req = new UniverseBatchRunRequest(
            2, EquityUniverseCategory.LARGE_CAP, largeStartDate, largeAsOfDate, largeAsOfDate,
            largeKnowledgeCutoff, false, false, false, true, "test-operator-large"
        );

        UniverseBatchRunReportDto report = orchestratorService.executeBatchRun(req);

        assertNotNull(report);
        assertEquals(2, report.totalSelected());
        assertEquals(2, report.totalCalculated(), "Both Large Cap schemes must calculate successfully with authentic benchmark data");
        assertEquals(0, report.totalSkipped());
        assertEquals(0, report.totalFailed());

        for (SchemeBatchExecutionDetailDto detail : report.schemeOutcomes()) {
            assertEquals("CALCULATED", detail.calculationOutcome());
            assertEquals("NIFTY_100_TRI", detail.benchmarkCode());
            assertEquals("CANDIDATE_METHODOLOGY_MAPPING", detail.benchmarkGovernanceStatus());
            assertNotNull(detail.calculationRunId());
            assertTrue(detail.finalDistinctTradingDays() >= 700);

            // Verify calculation run in DB
            CalculationRun run = calculationRunRepository.findById(detail.calculationRunId()).orElse(null);
            assertNotNull(run);
            assertEquals("SUCCESS", run.getRunStatus());

            // Verify metric results in DB
            List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
            assertFalse(results.isEmpty());
            List<String> metricCodes = results.stream().map(MetricResult::getMetricCode).toList();
            assertTrue(metricCodes.contains("RET-02"));
            assertTrue(metricCodes.contains("RET-03"));
            assertTrue(metricCodes.contains("RSK-01"));
            assertTrue(metricCodes.contains("RSK-03"));
            assertTrue(metricCodes.contains("MKT-01"));
            assertTrue(metricCodes.contains("MKT-02"));
        }
    }

    @Test
    @DisplayName("Real-Data: Cross-category stratified batch calculation calculates and persists 8 metrics across all 5 categories")
    void testRealDataCrossCategoryCohortExecution() {
        LocalDate asOfDate = LocalDate.of(2026, 10, 1);
        LocalDate startDate = LocalDate.of(2023, 1, 1);
        OffsetDateTime knowledgeCutoff = asOfDate.atTime(23, 59, 59).atOffset(ZoneOffset.ofHoursMinutes(5, 30));

        // Category null triggers stratified round-robin selection across the 5 categories
        UniverseBatchRunRequest req = new UniverseBatchRunRequest(
            5, null, startDate, asOfDate, asOfDate,
            knowledgeCutoff, false, false, false, true, "test-operator-cross-cat"
        );

        UniverseBatchRunReportDto report = orchestratorService.executeBatchRun(req);

        assertNotNull(report);
        assertEquals(5, report.totalSelected());
        assertEquals(5, report.totalCalculated(), "All 5 cross-category schemes must calculate successfully");
        assertEquals(0, report.totalFailed());

        // Verify all 5 categories are represented
        Set<EquityUniverseCategory> cats = report.schemeOutcomes().stream()
            .map(SchemeBatchExecutionDetailDto::category)
            .collect(Collectors.toSet());
        assertEquals(5, cats.size(), "All 5 categories must be represented in stratified cohort");

        for (SchemeBatchExecutionDetailDto detail : report.schemeOutcomes()) {
            assertEquals("CALCULATED", detail.calculationOutcome());
            assertEquals("CANDIDATE_METHODOLOGY_MAPPING", detail.benchmarkGovernanceStatus());
            assertNotNull(detail.calculationRunId());
            assertTrue(detail.finalDistinctTradingDays() >= 700);

            CalculationRun run = calculationRunRepository.findById(detail.calculationRunId()).orElse(null);
            assertNotNull(run);
            assertEquals("SUCCESS", run.getRunStatus());

            List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
            assertEquals(8, results.size(), "Each calculated scheme must persist exactly 8 metrics");
        }
    }
}
