package com.yukira.backend.universe;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.ingestion.amfi.HistoricalNavIngestionService;
import com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.BenchmarkResolutionService;
import com.yukira.backend.service.BenchmarkResolutionService.BenchmarkResolutionResult;
import com.yukira.backend.service.CalculationOrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UniverseBatchOrchestratorServiceTest {

    @Mock
    private ReferenceUniverseService referenceUniverseService;

    @Mock
    private HistorySufficiencyService historySufficiencyService;

    @Mock
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Mock
    private BenchmarkResolutionService benchmarkResolutionService;

    @Mock
    private NiftyBenchmarkIngestionService benchmarkIngestionService;

    @Mock
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private CalculationOrchestratorService calculationOrchestratorService;

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private NavObservationRepository navObservationRepository;

    @Mock
    private CalculationRunRepository calculationRunRepository;

    @Mock
    private MetricResultRepository metricResultRepository;

    private UniverseBatchOrchestratorService service;

    private Benchmark nifty500;
    private Benchmark nifty100;

    @BeforeEach
    void setUp() {
        service = new UniverseBatchOrchestratorService(
            referenceUniverseService,
            historySufficiencyService,
            historicalNavIngestionService,
            benchmarkResolutionService,
            benchmarkIngestionService,
            calculationOrchestratorService,
            schemeOptionRepository,
            navObservationRepository,
            benchmarkRepository,
            benchmarkObservationRepository,
            calculationRunRepository,
            metricResultRepository
        );

        nifty500 = new Benchmark("NIFTY_500_TRI", "Nifty 500 TRI", "NSE", "TRI");
        nifty500.setId(100L);

        nifty100 = new Benchmark("NIFTY_100_TRI", "Nifty 100 TRI", "NSE", "TRI");
        nifty100.setId(101L);
    }

    private SchemeOption createSchemeOption(Long id, String amfiCode, String schemeName, String subcategory) {
        Amc amc = new Amc("Test AMC", "TEST_MF");
        amc.setId(10L);
        Scheme scheme = new Scheme(amc, schemeName, "SCHEME_" + id, null, "Equity Scheme", subcategory);
        scheme.setId(20L + id);
        SchemePlan plan = new SchemePlan(scheme, "DIRECT", "TEST_PLAN");
        plan.setId(30L + id);
        SchemeOption opt = new SchemeOption(plan, "GROWTH", amfiCode, "INF" + amfiCode);
        opt.setId(id);
        opt.setStatus("ACTIVE");
        return opt;
    }

    @Test
    @DisplayName("Batch size validation rejects non-positive or excessive bounds")
    void testBatchSizeBoundsValidation() {
        assertThrows(IllegalArgumentException.class, () -> new UniverseBatchRunRequest(
            0, null, LocalDate.now().minusYears(3), LocalDate.now(), LocalDate.now(),
            OffsetDateTime.now(), true, false, false, false, "admin"
        ));

        assertThrows(IllegalArgumentException.class, () -> new UniverseBatchRunRequest(
            51, null, LocalDate.now().minusYears(3), LocalDate.now(), LocalDate.now(),
            OffsetDateTime.now(), true, false, false, false, "admin"
        ));
    }

    @Test
    @DisplayName("Date range validation rejects startDate after endDate")
    void testDateRangeValidation() {
        assertThrows(IllegalArgumentException.class, () -> new UniverseBatchRunRequest(
            5, null, LocalDate.of(2024, 1, 15), LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 15),
            OffsetDateTime.now(), true, false, false, false, "admin"
        ));
    }

    @Test
    @DisplayName("Deterministic sorting prioritizes 0 observations, then partial (<700), then eligible (>=700)")
    void testDeterministicSortingPrioritizesInsufficientHistory() {
        SchemeOption optZero = createSchemeOption(1L, "10001", "Fund Zero", "Flexi Cap Fund");
        SchemeOption optPartial = createSchemeOption(2L, "10002", "Fund Partial", "Flexi Cap Fund");
        SchemeOption optSufficient = createSchemeOption(3L, "10003", "Fund Sufficient", "Flexi Cap Fund");

        when(referenceUniverseService.getEligibleReferenceUniverseOptions())
            .thenReturn(List.of(optSufficient, optZero, optPartial));

        when(referenceUniverseService.evaluateOption(optZero))
            .thenReturn(UniverseEvaluationResult.ofIncluded(1L, "10001", "INF10001", "Fund Zero", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP));
        when(referenceUniverseService.evaluateOption(optPartial))
            .thenReturn(UniverseEvaluationResult.ofIncluded(2L, "10002", "INF10002", "Fund Partial", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP));
        when(referenceUniverseService.evaluateOption(optSufficient))
            .thenReturn(UniverseEvaluationResult.ofIncluded(3L, "10003", "INF10003", "Fund Sufficient", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP));

        List<Object[]> summaries = new ArrayList<>();
        summaries.add(new Object[]{2L, LocalDate.now().minusYears(1), LocalDate.now(), 350L});
        summaries.add(new Object[]{3L, LocalDate.now().minusYears(3), LocalDate.now(), 750L});
        when(navObservationRepository.findDistinctValidNavSummaries()).thenReturn(summaries);

        when(historySufficiencyService.evaluateOptionAsOf(eq(1L), any(), any()))
            .thenReturn(HistorySufficiencyResult.of(1L, "10001", "INF10001", "Fund Zero", "Test AMC",
                EquityUniverseCategory.FLEXI_CAP, true, HistorySufficiencyStatus.NO_HISTORY, 0, 700, null, null, "Zero obs"));
        when(historySufficiencyService.evaluateOptionAsOf(eq(2L), any(), any()))
            .thenReturn(HistorySufficiencyResult.of(2L, "10002", "INF10002", "Fund Partial", "Test AMC",
                EquityUniverseCategory.FLEXI_CAP, true, HistorySufficiencyStatus.INSUFFICIENT_HISTORY, 350, 700, null, null, "Partial obs"));
        when(historySufficiencyService.evaluateOptionAsOf(eq(3L), any(), any()))
            .thenReturn(HistorySufficiencyResult.of(3L, "10003", "INF10003", "Fund Sufficient", "Test AMC",
                EquityUniverseCategory.FLEXI_CAP, true, HistorySufficiencyStatus.ELIGIBLE, 750, 700, null, null, "Sufficient obs"));

        UniverseBatchRunRequest req = new UniverseBatchRunRequest(
            3, EquityUniverseCategory.FLEXI_CAP, LocalDate.now().minusYears(3), LocalDate.now(), LocalDate.now(),
            OffsetDateTime.now(), true, false, false, false, "test-op"
        );

        UniverseBatchRunReportDto report = service.previewBatchRun(req);

        assertEquals(3, report.totalSelected());
        List<SchemeBatchExecutionDetailDto> details = report.schemeOutcomes();
        assertEquals(1L, details.get(0).schemeOptionId(), "First should be 0-obs option");
        assertEquals(2L, details.get(1).schemeOptionId(), "Second should be partial 350-obs option");
        assertEquals(3L, details.get(2).schemeOptionId(), "Third should be eligible 750-obs option");
    }

    @Test
    @DisplayName("Dry run preview does not invoke isolated ingestion or calculation runs")
    void testDryRunPreviewSafety() {
        SchemeOption opt = createSchemeOption(1L, "10001", "Fund Dry", "Flexi Cap Fund");
        when(referenceUniverseService.getEligibleReferenceUniverseOptions())
            .thenReturn(List.of(opt));
        when(referenceUniverseService.evaluateOption(opt))
            .thenReturn(UniverseEvaluationResult.ofIncluded(1L, "10001", "INF10001", "Fund Dry", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP));

        List<Object[]> summaries = new ArrayList<>();
        summaries.add(new Object[]{1L, LocalDate.now().minusYears(3), LocalDate.now(), 800L});
        when(navObservationRepository.findDistinctValidNavSummaries()).thenReturn(summaries);

        when(historySufficiencyService.evaluateOptionAsOf(eq(1L), any(), any()))
            .thenReturn(HistorySufficiencyResult.of(1L, "10001", "INF10001", "Fund Dry", "Test AMC",
                EquityUniverseCategory.FLEXI_CAP, true, HistorySufficiencyStatus.ELIGIBLE, 800, 700, null, null, "Eligible"));

        UniverseBatchRunRequest req = new UniverseBatchRunRequest(
            1, EquityUniverseCategory.FLEXI_CAP, LocalDate.now().minusYears(3), LocalDate.now(), LocalDate.now(),
            OffsetDateTime.now(), true, true, true, true, "test-op"
        );

        UniverseBatchRunReportDto report = service.previewBatchRun(req);

        assertTrue(report.dryRun());
        assertEquals("DRY_RUN_PREVIEW", report.schemeOutcomes().get(0).ingestionOutcome());
        assertEquals("DRY_RUN_PREVIEW", report.schemeOutcomes().get(0).calculationOutcome());

        verify(historicalNavIngestionService, never()).ingestHistoricalNavForSchemeOptionIsolated(anyLong(), any(), any());
        verify(calculationOrchestratorService, never()).executeCalculationRun(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Missing benchmark blocks relative metric calculation with SKIPPED_BENCHMARK_MISSING")
    void testMissingBenchmarkBlocksCalculation() {
        SchemeOption opt = createSchemeOption(10L, "10010", "Large Cap Fund", "Large Cap Fund");
        when(referenceUniverseService.getEligibleReferenceUniverseOptions())
            .thenReturn(List.of(opt));
        when(referenceUniverseService.evaluateOption(opt))
            .thenReturn(UniverseEvaluationResult.ofIncluded(10L, "10010", "INF10010", "Large Cap Fund", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.LARGE_CAP));

        List<Object[]> summaries = new ArrayList<>();
        summaries.add(new Object[]{10L, LocalDate.now().minusYears(3), LocalDate.now(), 750L});
        when(navObservationRepository.findDistinctValidNavSummaries()).thenReturn(summaries);

        when(historySufficiencyService.evaluateOptionAsOf(eq(10L), any(), any()))
            .thenReturn(HistorySufficiencyResult.of(10L, "10010", "INF10010", "Large Cap Fund", "Test AMC",
                EquityUniverseCategory.LARGE_CAP, true, HistorySufficiencyStatus.ELIGIBLE, 750, 700, null, null, "Eligible"));

        // Resolved benchmark is Nifty 100 TRI, but it has 0 observations
        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(opt), any()))
            .thenReturn(new BenchmarkResolutionResult(nifty100, false, "CATEGORY_DEFAULT", LocalDate.now().minusYears(3), "Candidate mapping"));
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(101L), any(), any()))
            .thenReturn(Collections.emptyList());

        UniverseBatchRunRequest req = new UniverseBatchRunRequest(
            1, EquityUniverseCategory.LARGE_CAP, LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 15),
            OffsetDateTime.now(), false, false, false, true, "test-op"
        );

        UniverseBatchRunReportDto report = service.executeBatchRun(req);

        assertEquals(1, report.schemeOutcomes().size());
        SchemeBatchExecutionDetailDto detail = report.schemeOutcomes().get(0);
        assertEquals("SKIPPED_BENCHMARK_MISSING", detail.calculationOutcome());
        assertTrue(detail.failureOrSkipReason().contains("NIFTY_100_TRI has 0 observations in window"));
        verify(calculationOrchestratorService, never()).executeCalculationRun(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Insufficient history (<700 dates) skips calculation with SKIPPED_INSUFFICIENT_HISTORY")
    void testInsufficientHistorySkipsCalculation() {
        SchemeOption opt = createSchemeOption(20L, "10020", "Small Cap Fund", "Small Cap Fund");
        when(referenceUniverseService.getEligibleReferenceUniverseOptions())
            .thenReturn(List.of(opt));
        when(referenceUniverseService.evaluateOption(opt))
            .thenReturn(UniverseEvaluationResult.ofIncluded(20L, "10020", "INF10020", "Small Cap Fund", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.SMALL_CAP));

        List<Object[]> summaries = new ArrayList<>();
        summaries.add(new Object[]{20L, LocalDate.now().minusYears(3), LocalDate.now(), 400L});
        when(navObservationRepository.findDistinctValidNavSummaries()).thenReturn(summaries);

        when(historySufficiencyService.evaluateOptionAsOf(eq(20L), any(), any()))
            .thenReturn(HistorySufficiencyResult.of(20L, "10020", "INF10020", "Small Cap Fund", "Test AMC",
                EquityUniverseCategory.SMALL_CAP, true, HistorySufficiencyStatus.INSUFFICIENT_HISTORY, 400, 700, null, null, "Insufficient"));

        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(opt), any()))
            .thenReturn(new BenchmarkResolutionResult(nifty500, false, "CATEGORY_DEFAULT", LocalDate.now().minusYears(3), "Candidate"));

        UniverseBatchRunRequest req = new UniverseBatchRunRequest(
            1, EquityUniverseCategory.SMALL_CAP, LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 15),
            OffsetDateTime.now(), false, false, false, true, "test-op"
        );

        UniverseBatchRunReportDto report = service.executeBatchRun(req);

        assertEquals(1, report.schemeOutcomes().size());
        SchemeBatchExecutionDetailDto detail = report.schemeOutcomes().get(0);
        assertEquals("SKIPPED_INSUFFICIENT_HISTORY", detail.calculationOutcome());
        assertTrue(detail.failureOrSkipReason().contains("400 distinct trading dates; 700 required"));
        verify(calculationOrchestratorService, never()).executeCalculationRun(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Successful end-to-end execution for eligible scheme and sufficient benchmark coverage")
    void testSuccessfulExecutionForEligibleScheme() {
        SchemeOption opt = createSchemeOption(30L, "10030", "Flexi Fund Alpha", "Flexi Cap Fund");
        when(referenceUniverseService.getEligibleReferenceUniverseOptions())
            .thenReturn(List.of(opt));
        when(referenceUniverseService.evaluateOption(opt))
            .thenReturn(UniverseEvaluationResult.ofIncluded(30L, "10030", "INF10030", "Flexi Fund Alpha", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP));

        List<Object[]> summaries = new ArrayList<>();
        summaries.add(new Object[]{30L, LocalDate.now().minusYears(3), LocalDate.now(), 750L});
        when(navObservationRepository.findDistinctValidNavSummaries()).thenReturn(summaries);

        when(historySufficiencyService.evaluateOptionAsOf(eq(30L), any(), any()))
            .thenReturn(HistorySufficiencyResult.of(30L, "10030", "INF10030", "Flexi Fund Alpha", "Test AMC",
                EquityUniverseCategory.FLEXI_CAP, true, HistorySufficiencyStatus.ELIGIBLE, 750, 700, null, null, "Eligible"));

        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(opt), any()))
            .thenReturn(new BenchmarkResolutionResult(nifty500, false, "CATEGORY_DEFAULT", LocalDate.now().minusYears(3), "Candidate"));

        // Simulate 750 benchmark observations
        List<BenchmarkObservation> bmObsList = new ArrayList<>();
        LocalDate cur = LocalDate.of(2021, 1, 1);
        for (int i = 0; i < 750; i++) {
            BenchmarkObservation obs = new BenchmarkObservation(nifty500, cur.plusDays(i), BigDecimal.valueOf(1000 + i), 1, OffsetDateTime.now());
            bmObsList.add(obs);
        }
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(100L), any(), any()))
            .thenReturn(bmObsList);

        CalculationRun mockRun = new CalculationRun();
        mockRun.setId(999L);
        mockRun.setRunStatus("SUCCESS");
        when(calculationOrchestratorService.executeCalculationRun(eq(30L), eq(100L), any(), any(), any(), any(), any()))
            .thenReturn(mockRun);

        UniverseBatchRunRequest req = new UniverseBatchRunRequest(
            1, EquityUniverseCategory.FLEXI_CAP, LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 15),
            OffsetDateTime.now(), false, false, false, true, "test-op"
        );

        UniverseBatchRunReportDto report = service.executeBatchRun(req);

        assertEquals(1, report.schemeOutcomes().size());
        SchemeBatchExecutionDetailDto detail = report.schemeOutcomes().get(0);
        assertEquals("CALCULATED", detail.calculationOutcome());
        assertEquals(999L, detail.calculationRunId());
        assertEquals(8, detail.metricsCalculated().size());
        assertEquals(1, report.totalCalculated());
        assertEquals(0, report.totalFailed());
    }

    @Test
    @DisplayName("Per-scheme failure isolation continues processing remainder of batch when one scheme fails")
    void testPerSchemeFailureIsolation() {
        SchemeOption optFailing = createSchemeOption(41L, "10041", "Failing Ingestion Fund", "Flexi Cap Fund");
        SchemeOption optSuccess = createSchemeOption(42L, "10042", "Success Fund", "Flexi Cap Fund");

        when(referenceUniverseService.getEligibleReferenceUniverseOptions())
            .thenReturn(List.of(optFailing, optSuccess));

        when(referenceUniverseService.evaluateOption(optFailing))
            .thenReturn(UniverseEvaluationResult.ofIncluded(41L, "10041", "INF10041", "Failing Ingestion Fund", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP));
        when(referenceUniverseService.evaluateOption(optSuccess))
            .thenReturn(UniverseEvaluationResult.ofIncluded(42L, "10042", "INF10042", "Success Fund", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP));

        List<Object[]> summaries = new ArrayList<>();
        summaries.add(new Object[]{41L, LocalDate.now().minusYears(3), LocalDate.now(), 100L});
        summaries.add(new Object[]{42L, LocalDate.now().minusYears(3), LocalDate.now(), 800L});
        when(navObservationRepository.findDistinctValidNavSummaries()).thenReturn(summaries);

        // First scheme backfill fails with exception
        when(historicalNavIngestionService.ingestHistoricalNavForSchemeOptionIsolated(eq(41L), any(), any()))
            .thenThrow(new RuntimeException("Simulated network timeout for AMFI"));

        when(historySufficiencyService.evaluateOptionAsOf(eq(41L), any(), any()))
            .thenReturn(HistorySufficiencyResult.of(41L, "10041", "INF10041", "Failing Ingestion Fund", "Test AMC",
                EquityUniverseCategory.FLEXI_CAP, true, HistorySufficiencyStatus.INSUFFICIENT_HISTORY, 100, 700, null, null, "Failed"));

        when(historySufficiencyService.evaluateOptionAsOf(eq(42L), any(), any()))
            .thenReturn(HistorySufficiencyResult.of(42L, "10042", "INF10042", "Success Fund", "Test AMC",
                EquityUniverseCategory.FLEXI_CAP, true, HistorySufficiencyStatus.ELIGIBLE, 800, 700, null, null, "Eligible"));

        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(optFailing), any()))
            .thenReturn(new BenchmarkResolutionResult(nifty500, false, "CATEGORY_DEFAULT", LocalDate.now().minusYears(3), "Candidate"));
        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(optSuccess), any()))
            .thenReturn(new BenchmarkResolutionResult(nifty500, false, "CATEGORY_DEFAULT", LocalDate.now().minusYears(3), "Candidate"));

        List<BenchmarkObservation> bmObsList = new ArrayList<>();
        LocalDate cur = LocalDate.of(2021, 1, 1);
        for (int i = 0; i < 750; i++) {
            bmObsList.add(new BenchmarkObservation(nifty500, cur.plusDays(i), BigDecimal.valueOf(1000 + i), 1, OffsetDateTime.now()));
        }
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(100L), any(), any()))
            .thenReturn(bmObsList);

        CalculationRun mockRun = new CalculationRun();
        mockRun.setId(1001L);
        mockRun.setRunStatus("SUCCESS");
        when(calculationOrchestratorService.executeCalculationRun(eq(42L), eq(100L), any(), any(), any(), any(), any()))
            .thenReturn(mockRun);

        UniverseBatchRunRequest req = new UniverseBatchRunRequest(
            2, EquityUniverseCategory.FLEXI_CAP, LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 15),
            OffsetDateTime.now(), false, true, false, true, "test-op"
        );

        UniverseBatchRunReportDto report = service.executeBatchRun(req);

        assertEquals(2, report.schemeOutcomes().size());
        SchemeBatchExecutionDetailDto failDetail = report.schemeOutcomes().get(0);
        assertEquals("FAILED", failDetail.ingestionOutcome());
        assertTrue(failDetail.failureOrSkipReason().contains("Simulated network timeout"));

        SchemeBatchExecutionDetailDto succDetail = report.schemeOutcomes().get(1);
        assertEquals("CALCULATED", succDetail.calculationOutcome());
        assertEquals(1001L, succDetail.calculationRunId());

        assertEquals(1, report.totalCalculated());
        assertEquals(1, report.totalFailed());
    }

    @Test
    @DisplayName("Universe coverage status correctly tallies categories and benchmark status")
    void testGetUniverseCoverageStatus() {
        SchemeOption optFlexi1 = createSchemeOption(51L, "10051", "Flexi 1", "Flexi Cap Fund");
        when(referenceUniverseService.getEligibleReferenceUniverseOptions())
            .thenReturn(List.of(optFlexi1));
        when(referenceUniverseService.evaluateOption(optFlexi1))
            .thenReturn(UniverseEvaluationResult.ofIncluded(51L, "10051", "INF10051", "Flexi 1", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP));

        when(schemeOptionRepository.count()).thenReturn(100L);
        List<Object[]> summaries = new ArrayList<>();
        summaries.add(new Object[]{51L, LocalDate.now().minusYears(3), LocalDate.now(), 720L});
        when(navObservationRepository.findDistinctValidNavSummaries()).thenReturn(summaries);

        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(nifty500));
        when(benchmarkRepository.findByCode("NIFTY_100_TRI")).thenReturn(Optional.of(nifty100));
        when(benchmarkRepository.findByCode("NIFTY_LARGEMIDCAP_250_TRI")).thenReturn(Optional.empty());
        when(benchmarkRepository.findByCode("NIFTY_MIDCAP_150_TRI")).thenReturn(Optional.empty());
        when(benchmarkRepository.findByCode("NIFTY_SMALLCAP_250_TRI")).thenReturn(Optional.empty());

        List<BenchmarkObservation> nifty500Obs = new ArrayList<>();
        LocalDate cur = LocalDate.of(2021, 1, 1);
        for (int i = 0; i < 750; i++) {
            nifty500Obs.add(new BenchmarkObservation(nifty500, cur.plusDays(i), BigDecimal.valueOf(1000 + i), 1, OffsetDateTime.now()));
        }
        when(benchmarkObservationRepository.findByBenchmarkId(eq(100L))).thenReturn(nifty500Obs);
        when(benchmarkObservationRepository.findByBenchmarkId(eq(101L))).thenReturn(Collections.emptyList());

        UniverseCoverageStatusDto status = service.getUniverseCoverageStatus();

        assertNotNull(status);
        assertEquals(1, status.totalReferenceUniverseOptions());
        assertEquals(1, status.totalSufficientOptions());
        assertEquals(0, status.totalPartialOptions());
        assertEquals(0, status.totalZeroOptions());

        CategoryCoverageDto flexiCat = status.categoryBreakdown().get("FLEXI_CAP");
        assertNotNull(flexiCat);
        assertEquals(1, flexiCat.sufficientOptions());
        assertEquals("NIFTY_500_TRI", flexiCat.candidateBenchmarkCode());
        assertEquals("SUFFICIENT_3Y_COVERAGE", flexiCat.benchmarkCoverageStatus());

        CategoryCoverageDto largeCat = status.categoryBreakdown().get("LARGE_CAP");
        assertNotNull(largeCat);
        assertEquals("NO_OBSERVATIONS", largeCat.benchmarkCoverageStatus());
    }

    @Test
    @DisplayName("Option with verified 3Y metrics is skipped with SKIPPED_ALREADY_CALCULATED when forceRecalculate is false")
    void testSkipAlreadyCalculated() {
        SchemeOption opt = createSchemeOption(61L, "10061", "Already Calculated Fund", "Flexi Cap Fund");
        when(referenceUniverseService.getEligibleReferenceUniverseOptions())
            .thenReturn(List.of(opt));
        when(referenceUniverseService.evaluateOption(opt))
            .thenReturn(UniverseEvaluationResult.ofIncluded(61L, "10061", "INF10061", "Already Calculated Fund", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP));

        List<Object[]> summaries = new ArrayList<>();
        summaries.add(new Object[]{61L, LocalDate.now().minusYears(3), LocalDate.now(), 750L});
        when(navObservationRepository.findDistinctValidNavSummaries()).thenReturn(summaries);

        when(historySufficiencyService.evaluateOptionAsOf(eq(61L), any(), any()))
            .thenReturn(HistorySufficiencyResult.of(61L, "10061", "INF10061", "Already Calculated Fund", "Test AMC",
                EquityUniverseCategory.FLEXI_CAP, true, HistorySufficiencyStatus.ELIGIBLE, 750, 700, null, null, "Eligible"));

        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(opt), any()))
            .thenReturn(new BenchmarkResolutionResult(nifty500, false, "CATEGORY_DEFAULT", LocalDate.now().minusYears(3), "Candidate"));

        // Setup prior successful run with all 8 canonical metrics
        CalculationRun priorRun = new CalculationRun();
        priorRun.setId(8888L);
        priorRun.setSchemeOption(opt);
        priorRun.setRunStatus("SUCCESS");
        priorRun.setAsOfDate(LocalDate.of(2024, 1, 15));

        when(calculationRunRepository.findComparisonRunsBySchemeOptionIdsAndAsOfDate(eq(List.of(61L)), eq(LocalDate.of(2024, 1, 15))))
            .thenReturn(List.of(priorRun));

        List<MetricResult> mrList = new ArrayList<>();
        for (String code : UniverseBatchOrchestratorService.CANONICAL_BATCH_METRIC_CODES) {
            mrList.add(new MetricResult(priorRun, code, "3Y", BigDecimal.ONE, "RATIO", "CALCULATED"));
        }
        when(metricResultRepository.findByCalculationRunId(8888L)).thenReturn(mrList);

        UniverseBatchRunRequest req = new UniverseBatchRunRequest(
            1, EquityUniverseCategory.FLEXI_CAP, LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 15),
            OffsetDateTime.now(), false, false, false, true, "test-op", false // forceRecalculate = false
        );

        UniverseBatchRunReportDto report = service.executeBatchRun(req);

        assertEquals(1, report.schemeOutcomes().size());
        SchemeBatchExecutionDetailDto detail = report.schemeOutcomes().get(0);
        assertEquals("SKIPPED_ALREADY_CALCULATED", detail.calculationOutcome());
        assertEquals(8888L, detail.calculationRunId());
        assertEquals(8, detail.metricsCalculated().size());
        assertEquals(0, report.totalCalculated());
        assertEquals(1, report.totalSkipped());
        assertEquals("COMPLETED", report.overallStatus());

        // Ensure calculationOrchestrator was never called!
        verify(calculationOrchestratorService, never()).executeCalculationRun(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Option with verified 3Y metrics is recalculated when forceRecalculate is true")
    void testForceRecalculateReruns() {
        SchemeOption opt = createSchemeOption(62L, "10062", "Force Recalc Fund", "Flexi Cap Fund");
        when(referenceUniverseService.getEligibleReferenceUniverseOptions())
            .thenReturn(List.of(opt));
        when(referenceUniverseService.evaluateOption(opt))
            .thenReturn(UniverseEvaluationResult.ofIncluded(62L, "10062", "INF10062", "Force Recalc Fund", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP));

        List<Object[]> summaries = new ArrayList<>();
        summaries.add(new Object[]{62L, LocalDate.now().minusYears(3), LocalDate.now(), 750L});
        when(navObservationRepository.findDistinctValidNavSummaries()).thenReturn(summaries);

        when(historySufficiencyService.evaluateOptionAsOf(eq(62L), any(), any()))
            .thenReturn(HistorySufficiencyResult.of(62L, "10062", "INF10062", "Force Recalc Fund", "Test AMC",
                EquityUniverseCategory.FLEXI_CAP, true, HistorySufficiencyStatus.ELIGIBLE, 750, 700, null, null, "Eligible"));

        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(opt), any()))
            .thenReturn(new BenchmarkResolutionResult(nifty500, false, "CATEGORY_DEFAULT", LocalDate.now().minusYears(3), "Candidate"));

        List<BenchmarkObservation> bmObsList = new ArrayList<>();
        LocalDate cur = LocalDate.of(2021, 1, 1);
        for (int i = 0; i < 750; i++) {
            bmObsList.add(new BenchmarkObservation(nifty500, cur.plusDays(i), BigDecimal.valueOf(1000 + i), 1, OffsetDateTime.now()));
        }
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(100L), any(), any()))
            .thenReturn(bmObsList);

        CalculationRun freshRun = new CalculationRun();
        freshRun.setId(9999L);
        freshRun.setRunStatus("SUCCESS");
        when(calculationOrchestratorService.executeCalculationRun(eq(62L), eq(100L), any(), any(), any(), any(), any()))
            .thenReturn(freshRun);

        UniverseBatchRunRequest req = new UniverseBatchRunRequest(
            1, EquityUniverseCategory.FLEXI_CAP, LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 15),
            OffsetDateTime.now(), false, false, false, true, "test-op", true // forceRecalculate = true
        );

        UniverseBatchRunReportDto report = service.executeBatchRun(req);

        assertEquals(1, report.schemeOutcomes().size());
        SchemeBatchExecutionDetailDto detail = report.schemeOutcomes().get(0);
        assertEquals("CALCULATED", detail.calculationOutcome());
        assertEquals(9999L, detail.calculationRunId());
        assertEquals(1, report.totalCalculated());
        assertEquals(0, report.totalSkipped());

        verify(calculationOrchestratorService, times(1)).executeCalculationRun(eq(62L), eq(100L), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("triggerAsyncBatchRun enforces concurrency lock and rejects concurrent duplicate requests")
    void testTriggerAsyncBatchRunAndConcurrencyLock() {
        SchemeOption opt = createSchemeOption(70L, "10070", "Async Fund", "Flexi Cap Fund");
        when(referenceUniverseService.getEligibleReferenceUniverseOptions())
            .thenReturn(List.of(opt));
        when(referenceUniverseService.evaluateOption(opt))
            .thenReturn(UniverseEvaluationResult.ofIncluded(70L, "10070", "INF10070", "Async Fund", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP));

        when(historySufficiencyService.evaluateOptionAsOf(eq(70L), any(), any()))
            .thenReturn(HistorySufficiencyResult.of(70L, "10070", "INF10070", "Async Fund", "Test AMC",
                EquityUniverseCategory.FLEXI_CAP, true, HistorySufficiencyStatus.ELIGIBLE, 750, 700, null, null, "Eligible"));
        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(opt), any()))
            .thenReturn(new BenchmarkResolutionService.BenchmarkResolutionResult(nifty500, false, "CATEGORY_FALLBACK", LocalDate.of(2021, 1, 1), "Candidate benchmark"));

        UniverseBatchRunRequest req = new UniverseBatchRunRequest(
            1, EquityUniverseCategory.FLEXI_CAP, LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 15), LocalDate.of(2024, 1, 15),
            OffsetDateTime.now(), true, false, false, true, "test-async"
        );

        UniverseBatchJobStatusDto job = service.triggerAsyncBatchRun(req);
        assertNotNull(job);
        assertNotNull(job.jobId());

        // Attempting to trigger another while running should throw IllegalStateException
        if (service.isJobRunning()) {
            assertThrows(IllegalStateException.class, () -> service.triggerAsyncBatchRun(req));
        }

        // Wait briefly for daemon execution to finish
        try {
            Thread.sleep(300);
        } catch (InterruptedException ignored) {}

        UniverseBatchJobStatusDto terminal = service.getJobStatus();
        assertNotNull(terminal);
    }
}
