package com.yukira.backend.service;

import com.yukira.backend.client.QuantEngineClient;
import com.yukira.backend.client.dto.CalculationDtos.CalculationRequestDto;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
@SuppressWarnings({"null", "deprecation"})
class PeriodReturnCalculationServiceTest {

    @Autowired
    private PeriodReturnCalculationService calculationService;

    @Autowired
    private AmcRepository amcRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private SchemePlanRepository schemePlanRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    @Autowired
    private CalculationRunInputObservationRepository inputObsRepository;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private PitObservationResolutionService pitResolutionService;

    @Autowired
    private MethodologyVersionRepository methodologyVersionRepository;

    @Autowired
    private MethodologyGovernanceService methodologyGovernanceService;

    private SchemeOption schemeOption;

    @BeforeEach
    void setUp() {
        Amc amc = amcRepository.findByCode("HDFC_MF")
            .orElseGet(() -> amcRepository.save(new Amc("HDFC Mutual Fund", "HDFC_MF")));
        Scheme scheme = schemeRepository.findByCode("HDFC_FLEXI")
            .orElseGet(() -> schemeRepository.save(new Scheme(amc, "HDFC Flexi Cap Fund", "HDFC_FLEXI", LocalDate.of(1995, 1, 1))));
        SchemePlan plan = schemePlanRepository.findByCode("HDFC_FLEXI_DIR")
            .orElseGet(() -> schemePlanRepository.save(new SchemePlan(scheme, "DIRECT", "HDFC_FLEXI_DIR")));
        schemeOption = schemeOptionRepository.findByAmfiCode("TEST_CALC_01")
            .orElseGet(() -> schemeOptionRepository.save(new SchemeOption(plan, "GROWTH", "TEST_CALC_01", "INF_TEST_CALC_01")));
    }

    @Test
    @DisplayName("RET-02 Test 1: Exact positive return calculation with boundary-date observations")
    void testExactPositiveReturn() {
        OffsetDateTime t = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 15);

        navObservationRepository.save(new NavObservation(schemeOption, start, new BigDecimal("100.00000000"), 1, t));
        navObservationRepository.save(new NavObservation(schemeOption, end, new BigDecimal("110.00000000"), 1, t));

        CalculationRun run = calculationService.executeRet02Calculation(
            schemeOption.getId(), start, end, t, "CANDIDATE_V1"
        );

        assertNotNull(run.getId());
        assertEquals("COMPLETED", run.getRunStatus());
        assertNotNull(run.getInputSnapshotSha256());
        assertNull(run.getBenchmark(), "benchmark must be null for RET-02 standalone calculation (no synthetic benchmark entity)");

        List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
        assertEquals(1, results.size());
        MetricResult res = results.get(0);
        assertEquals("RET-02", res.getMetricCode());
        assertEquals("PERCENTAGE", res.getUnits());
        assertEquals("CALCULATED", res.getCalculationStatus());
        // Return is (110 - 100) / 100 = 0.1000000000
        assertEquals(0, new BigDecimal("0.10").compareTo(res.getNumericValue().setScale(2, java.math.RoundingMode.HALF_UP)));

        // Verify input references linkage
        List<CalculationRunInputObservation> inputs = inputObsRepository.findByCalculationRunId(run.getId());
        assertEquals(2, inputs.size());
    }

    @Test
    @DisplayName("RET-02 Test 1b: Verify CalculationRun structurally distinguishes 'benchmark not required' with zero synthetic benchmark entities")
    void testRet02CalculationRunWithNullBenchmarkAndNoSyntheticEntity() {
        OffsetDateTime t = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 15);

        navObservationRepository.save(new NavObservation(schemeOption, start, new BigDecimal("100.00000000"), 1, t));
        navObservationRepository.save(new NavObservation(schemeOption, end, new BigDecimal("110.00000000"), 1, t));

        long benchmarkCountBefore = benchmarkRepository.count();

        CalculationRun run = calculationService.executeRet02Calculation(
            schemeOption.getId(), start, end, t, "CANDIDATE_V1"
        );

        long benchmarkCountAfter = benchmarkRepository.count();
        assertEquals(benchmarkCountBefore, benchmarkCountAfter, "No synthetic benchmark entity must be created");
        assertNull(run.getBenchmark(), "CalculationRun.benchmark must be null for benchmark-free calculations");
    }

    @Test
    @DisplayName("RET-02 Test 2: Negative return calculation (NAV decrease)")
    void testNegativeReturn() {
        OffsetDateTime t = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 15);

        navObservationRepository.save(new NavObservation(schemeOption, start, new BigDecimal("100.00000000"), 1, t));
        navObservationRepository.save(new NavObservation(schemeOption, end, new BigDecimal("95.00000000"), 1, t));

        CalculationRun run = calculationService.executeRet02Calculation(
            schemeOption.getId(), start, end, t, "CANDIDATE_V1"
        );

        List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
        assertEquals(0, new BigDecimal("-0.05").compareTo(results.get(0).getNumericValue().setScale(2, java.math.RoundingMode.HALF_UP)));
    }

    @Test
    @DisplayName("RET-02 Test 3: Zero return (identical start and end NAV)")
    void testZeroReturn() {
        OffsetDateTime t = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 15);

        navObservationRepository.save(new NavObservation(schemeOption, start, new BigDecimal("100.00000000"), 1, t));
        navObservationRepository.save(new NavObservation(schemeOption, end, new BigDecimal("100.00000000"), 1, t));

        CalculationRun run = calculationService.executeRet02Calculation(
            schemeOption.getId(), start, end, t, "CANDIDATE_V1"
        );

        List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
        assertEquals(0, BigDecimal.ZERO.compareTo(results.get(0).getNumericValue().setScale(2, java.math.RoundingMode.HALF_UP)));
    }

    @Test
    @DisplayName("RET-02 Test 4: Candidate 4-day lookback resolves non-trading day requested date")
    void testCandidateLookbackResolution() {
        OffsetDateTime t = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        // Observation on Friday 2024-01-12
        LocalDate friday = LocalDate.of(2024, 1, 12);
        navObservationRepository.save(new NavObservation(schemeOption, friday, new BigDecimal("100.00000000"), 1, t));

        // Observation on Monday 2024-01-22
        LocalDate monday = LocalDate.of(2024, 1, 22);
        navObservationRepository.save(new NavObservation(schemeOption, monday, new BigDecimal("105.00000000"), 1, t));

        // Request start on Sunday 2024-01-14 (2 days after Friday 12th)
        LocalDate requestedSunday = LocalDate.of(2024, 1, 14);

        CalculationRun run = calculationService.executeRet02Calculation(
            schemeOption.getId(), requestedSunday, monday, t, "CANDIDATE_V1"
        );

        assertEquals("COMPLETED", run.getRunStatus());
        List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
        assertEquals(0, new BigDecimal("0.05").compareTo(results.get(0).getNumericValue().setScale(2, java.math.RoundingMode.HALF_UP)));
        assertTrue(results.get(0).getDiagnostics().contains("start_lookback_days_used\":2"));
    }

    @Test
    @DisplayName("RET-02 Test 5: Insufficient evidence fails cleanly without zero substitution")
    void testInsufficientEvidenceFailsCleanly() {
        OffsetDateTime t = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        // Only start exists, end date has no observations within 4 days
        LocalDate start = LocalDate.of(2024, 1, 1);
        navObservationRepository.save(new NavObservation(schemeOption, start, new BigDecimal("100.00000000"), 1, t));

        LocalDate end = LocalDate.of(2024, 1, 20);

        CalculationRun run = calculationService.executeRet02Calculation(
            schemeOption.getId(), start, end, t, "CANDIDATE_V1"
        );

        assertEquals("FAILED", run.getRunStatus());
        assertTrue(run.getErrorMessage().contains("Insufficient evidence"));

        List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
        assertEquals(1, results.size());
        assertEquals("INSUFFICIENT_DATA", results.get(0).getCalculationStatus());
        assertNull(results.get(0).getNumericValue(), "Numeric value must be null (NO zero substitution)");
    }

    @Test
    @DisplayName("RET-02 Test 6: Reproducibility: Identical inputs produce identical SHA-256 snapshot hash and metric")
    void testReproducibility() {
        OffsetDateTime t = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 15);

        navObservationRepository.save(new NavObservation(schemeOption, start, new BigDecimal("100.00000000"), 1, t));
        navObservationRepository.save(new NavObservation(schemeOption, end, new BigDecimal("110.00000000"), 1, t));

        CalculationRun run1 = calculationService.executeRet02Calculation(schemeOption.getId(), start, end, t, "CANDIDATE_V1");
        CalculationRun run2 = calculationService.executeRet02Calculation(schemeOption.getId(), start, end, t, "CANDIDATE_V1");

        assertEquals(run1.getInputSnapshotSha256(), run2.getInputSnapshotSha256(), "Input snapshot hash must be identical");

        MetricResult m1 = metricResultRepository.findByCalculationRunId(run1.getId()).get(0);
        MetricResult m2 = metricResultRepository.findByCalculationRunId(run2.getId()).get(0);
        assertEquals(m1.getNumericValue(), m2.getNumericValue(), "Calculated numeric value must be identical");
    }

    /**
     * Governance regression (AGENTS.md 6: the Quant Engine is the sole numerical authority).
     *
     * PeriodReturnCalculationService previously caught ANY exception from the engine dispatch
     * and silently recomputed RET-02 in Java (BigDecimal scale 10 / HALF_UP), persisting the
     * result as CALCULATED. That behaviour:
     *   - created a second, divergent numerical authority outside the deterministic kernel,
     *   - masked genuine engine failures as successful calculations, destroying the
     *     failed-validation data-quality state that the analytical contract must preserve,
     *   - produced values that differ from the engine's 64-bit IEEE double arithmetic.
     *
     * The backend must now fail loudly and never substitute its own arithmetic.
     */
    @Test
    @DisplayName("RET-02 Test 7: Engine failure must surface as FAILED with null value (no backend substitution)")
    void testEngineFailureDoesNotSubstituteBackendArithmetic() throws Exception {
        OffsetDateTime t = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 15);

        navObservationRepository.save(new NavObservation(schemeOption, start, new BigDecimal("100.00000000"), 1, t));
        navObservationRepository.save(new NavObservation(schemeOption, end, new BigDecimal("110.00000000"), 1, t));

        // Force the engine dispatch to fail deterministically. Every other collaborator is the
        // real Spring bean so that only the numerical authority is perturbed.
        QuantEngineClient failingClient = mock(QuantEngineClient.class);
        when(failingClient.executeCalculation(any(CalculationRequestDto.class)))
            .thenThrow(new IllegalStateException("simulated engine unavailability"));

        PeriodReturnCalculationService isolated = new PeriodReturnCalculationService(
            pitResolutionService,
            schemeOptionRepository,
            methodologyVersionRepository,
            calculationRunRepository,
            metricResultRepository,
            inputObsRepository,
            failingClient,
            methodologyGovernanceService
        );

        CalculationRun run = isolated.executeRet02Calculation(
            schemeOption.getId(), start, end, t, "CANDIDATE_V1");

        assertEquals("FAILED", run.getRunStatus(),
            "A failed engine dispatch must NOT be reported as COMPLETED");

        List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
        assertEquals(1, results.size());
        MetricResult result = results.get(0);
        assertEquals("RET-02", result.getMetricCode());
        assertEquals("FAILED", result.getCalculationStatus());
        assertNull(result.getNumericValue(),
            "Backend must never substitute its own arithmetic for the Quant Engine");
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getDiagnostics().contains("QUANT_ENGINE_ONLY_NO_BACKEND_SUBSTITUTION"),
            "Diagnostics must disclose the numerical-authority convention");
        assertFalse(result.getDiagnostics().contains("local_fallback"),
            "The local_fallback substitution path must no longer exist");
    }
}
