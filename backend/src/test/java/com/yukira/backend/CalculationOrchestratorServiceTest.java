package com.yukira.backend;

import com.yukira.backend.client.QuantEngineClient;
import com.yukira.backend.client.dto.CalculationDtos.*;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.CalculationOrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@Transactional
class CalculationOrchestratorServiceTest {

    @Autowired
    private CalculationOrchestratorService calculationOrchestratorService;

    @Autowired
    private AmcRepository amcRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private SchemePlanRepository schemePlanRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Autowired
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Autowired
    private RiskFreeObservationRepository riskFreeObservationRepository;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    @Autowired
    private CalculationRunInputObservationRepository calculationRunInputObservationRepository;

    @Autowired
    private MethodologyVersionRepository methodologyVersionRepository;

    @MockitoBean
    private QuantEngineClient quantEngineClient;

    private SchemeOption schemeOption;
    private Benchmark benchmark;

    @BeforeEach
    void setUp() {
        Amc amc = amcRepository.save(new Amc("SBI Funds Management", "SBI_MF"));
        Scheme scheme = schemeRepository.save(new Scheme(amc, "SBI Bluechip Fund", "SBI_BLUE", LocalDate.of(2006, 1, 1)));
        SchemePlan plan = schemePlanRepository.save(new SchemePlan(scheme, "DIRECT", "SBI_BLUE_DIR"));
        schemeOption = schemeOptionRepository.save(new SchemeOption(plan, "GROWTH", "119598", "INF200K01198"));
        benchmark = benchmarkRepository.save(new Benchmark("NIFTY50_TRI", "NIFTY 50 Total Returns Index", "NSE Indices", "TRI"));
    }

    @Test
    @DisplayName("Test full calculation orchestration, snapshot hashing, and 3-way calculation input observation linkage (NAV + BM + RF)")
    void testCalculationOrchestrationAndPersistence() {
        LocalDate asOfDate = LocalDate.of(2026, 1, 15);
        OffsetDateTime tCutoff = OffsetDateTime.of(2026, 1, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Seed 3 historical NAV points
        NavObservation nav1 = navObservationRepository.save(new NavObservation(schemeOption, LocalDate.of(2025, 1, 1), new BigDecimal("100.00"), 1, tCutoff.minusDays(370)));
        NavObservation nav2 = navObservationRepository.save(new NavObservation(schemeOption, LocalDate.of(2025, 6, 1), new BigDecimal("110.00"), 1, tCutoff.minusDays(220)));
        NavObservation nav3 = navObservationRepository.save(new NavObservation(schemeOption, LocalDate.of(2026, 1, 1), new BigDecimal("120.00"), 1, tCutoff.minusDays(14)));

        // Seed 3 historical Benchmark points
        BenchmarkObservation bm1 = benchmarkObservationRepository.save(new BenchmarkObservation(benchmark, LocalDate.of(2025, 1, 1), new BigDecimal("1000.00"), 1, tCutoff.minusDays(370)));
        BenchmarkObservation bm2 = benchmarkObservationRepository.save(new BenchmarkObservation(benchmark, LocalDate.of(2025, 6, 1), new BigDecimal("1050.00"), 1, tCutoff.minusDays(220)));
        BenchmarkObservation bm3 = benchmarkObservationRepository.save(new BenchmarkObservation(benchmark, LocalDate.of(2026, 1, 1), new BigDecimal("1150.00"), 1, tCutoff.minusDays(14)));

        // Seed 3 historical Risk-Free points
        RiskFreeObservation rf1 = riskFreeObservationRepository.save(new RiskFreeObservation("FBIL_91D_TBILL", LocalDate.of(2025, 1, 1), new BigDecimal("0.06950000"), 1, tCutoff.minusDays(370)));
        RiskFreeObservation rf2 = riskFreeObservationRepository.save(new RiskFreeObservation("FBIL_91D_TBILL", LocalDate.of(2025, 6, 1), new BigDecimal("0.06850000"), 1, tCutoff.minusDays(220)));
        RiskFreeObservation rf3 = riskFreeObservationRepository.save(new RiskFreeObservation("FBIL_91D_TBILL", LocalDate.of(2026, 1, 1), new BigDecimal("0.06900000"), 1, tCutoff.minusDays(14)));

        // Mock Quant Engine response
        CalculationResponseDto mockResponse = new CalculationResponseDto(
            "REQ-12345",
            asOfDate.toString(),
            tCutoff.toString(),
            "0.1.0-alpha",
            "mock-git-sha-1234567890abcdef",
            12.5,
            List.of(
                new MetricOutputItemDto("RET-01", "1Y", new BigDecimal("0.20000000"), null, "PERCENTAGE", "CALCULATED", Map.of("methodology_status", "CANDIDATE"), null),
                new MetricOutputItemDto("RSK-01", "3Y", new BigDecimal("0.14500000"), null, "PERCENTAGE", "CALCULATED", Map.of("methodology_status", "CANDIDATE"), null),
                new MetricOutputItemDto("RAT-01", "3Y", new BigDecimal("1.25000000"), null, "RATIO", "CALCULATED", Map.of("methodology_status", "APPROVED"), null),
                new MetricOutputItemDto("REL-01", "3Y", new BigDecimal("0.95000000"), null, "RATIO", "CALCULATED", Map.of("methodology_status", "APPROVED"), null)
            ),
            "SUCCESS",
            null
        );

        Mockito.when(quantEngineClient.executeCalculation(any())).thenReturn(mockResponse);

        CalculationRun run = calculationOrchestratorService.executeCalculationRun(
            schemeOption.getId(),
            benchmark.getId(),
            asOfDate,
            tCutoff,
            List.of("RET-01", "RSK-01", "RAT-01", "REL-01"),
            "CANDIDATE-V1",
            Map.of("risk_free_rate", 0.065)
        );

        assertNotNull(run.getId(), "CalculationRun must be persisted with an ID");
        assertEquals("SUCCESS", run.getRunStatus());
        assertNotNull(run.getInputSnapshotSha256(), "Must generate an input snapshot SHA-256 hash");
        assertEquals(64, run.getInputSnapshotSha256().length(), "SHA-256 hash must be 64 hex characters");

        List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
        assertEquals(4, results.size(), "Must persist exactly 4 metric results");
        assertTrue(results.stream().anyMatch(r -> r.getMetricCode().equals("RET-01")));
        assertTrue(results.stream().anyMatch(r -> r.getMetricCode().equals("RSK-01")));
        assertTrue(results.stream().anyMatch(r -> r.getMetricCode().equals("RAT-01")));
        assertTrue(results.stream().anyMatch(r -> r.getMetricCode().equals("REL-01")));

        // Verify CalculationRun -> exact PIT-resolved observation IDs/revisions linkage (3 NAV + 3 BM + 3 RF = 9)
        List<CalculationRunInputObservation> inputObs = calculationRunInputObservationRepository
            .findByCalculationRunId(run.getId());
        assertEquals(9, inputObs.size(), "Must persist exactly 9 input observation references (3 NAV + 3 BM + 3 RF)");

        List<CalculationRunInputObservation> navInputs = inputObs.stream()
            .filter(i -> i.getNavObservation() != null)
            .toList();
        assertEquals(3, navInputs.size(), "Must have exactly 3 NAV observation references");
        for (CalculationRunInputObservation navRef : navInputs) {
            assertNotNull(navRef.getNavObservation().getId(), "Referenced NAV observation ID must not be null");
            assertNull(navRef.getBenchmarkObservation(), "Benchmark observation must be null for NAV reference");
            assertNull(navRef.getRiskFreeObservation(), "RiskFree observation must be null for NAV reference");
            assertTrue(navObservationRepository.existsById(navRef.getNavObservation().getId()), "Referenced NAV observation must exist in DB");
            assertEquals(1, navRef.getRevisionSeq(), "Revision seq must match observed sequence");
        }

        List<CalculationRunInputObservation> bmInputs = inputObs.stream()
            .filter(i -> i.getBenchmarkObservation() != null)
            .toList();
        assertEquals(3, bmInputs.size(), "Must have exactly 3 Benchmark observation references");
        for (CalculationRunInputObservation bmRef : bmInputs) {
            assertNotNull(bmRef.getBenchmarkObservation().getId(), "Referenced Benchmark observation ID must not be null");
            assertNull(bmRef.getNavObservation(), "NAV observation must be null for Benchmark reference");
            assertNull(bmRef.getRiskFreeObservation(), "RiskFree observation must be null for Benchmark reference");
            assertTrue(benchmarkObservationRepository.existsById(bmRef.getBenchmarkObservation().getId()), "Referenced Benchmark observation must exist in DB");
            assertEquals(1, bmRef.getRevisionSeq(), "Revision seq must match observed sequence");
        }

        List<CalculationRunInputObservation> rfInputs = inputObs.stream()
            .filter(i -> i.getRiskFreeObservation() != null)
            .toList();
        assertEquals(3, rfInputs.size(), "Must have exactly 3 RiskFree observation references");
        for (CalculationRunInputObservation rfRef : rfInputs) {
            assertNotNull(rfRef.getRiskFreeObservation().getId(), "Referenced RiskFree observation ID must not be null");
            assertNull(navRefOrBm(rfRef), "NAV and BM observations must be null for RiskFree reference");
            assertTrue(riskFreeObservationRepository.existsById(rfRef.getRiskFreeObservation().getId()), "Referenced RiskFree observation must exist in DB");
            assertEquals(1, rfRef.getRevisionSeq(), "Revision seq must match observed sequence");
        }

        // Verify request DTO sent to quant engine has all 3 series populated
        ArgumentCaptor<CalculationRequestDto> captor = ArgumentCaptor.forClass(CalculationRequestDto.class);
        Mockito.verify(quantEngineClient).executeCalculation(captor.capture());
        CalculationRequestDto capturedRequest = captor.getValue();
        assertEquals(3, capturedRequest.navSeries().size());
        assertEquals(3, capturedRequest.benchmarkSeries().size());
        assertNotNull(capturedRequest.riskFreeSeries());
        assertEquals(3, capturedRequest.riskFreeSeries().size());
    }

    private Object navRefOrBm(CalculationRunInputObservation ref) {
        if (ref.getNavObservation() != null) return ref.getNavObservation();
        if (ref.getBenchmarkObservation() != null) return ref.getBenchmarkObservation();
        return null;
    }

    @Test
    @DisplayName("Test database-enforced referential integrity on calculation_run_input_observation (CHECK constraint for exactly one FK)")
    void testCalculationInputReferentialIntegrityCheckConstraint() {
        MethodologyVersion mv = methodologyVersionRepository.save(new MethodologyVersion(
            "METH-INTEGRITY-TEST", "v1.0-test", "CANDIDATE", "mock-commit-integrity"
        ));
        CalculationRun run = calculationRunRepository.save(new CalculationRun(
            schemeOption, benchmark, LocalDate.of(2026, 1, 15), OffsetDateTime.now(), mv, "TEST-ENGINE"
        ));

        // All null -> violates chk_run_input_exactly_one_fk
        CalculationRunInputObservation invalidAllNull = new CalculationRunInputObservation();
        invalidAllNull.setCalculationRun(run);
        invalidAllNull.setEffectiveDate(LocalDate.of(2026, 1, 15));
        invalidAllNull.setRevisionSeq(1);
        invalidAllNull.setNavObservation(null);
        invalidAllNull.setBenchmarkObservation(null);
        invalidAllNull.setRiskFreeObservation(null);

        assertThrows(Exception.class, () -> {
            calculationRunInputObservationRepository.saveAndFlush(invalidAllNull);
        }, "Database CHECK constraint must reject row where all 3 FKs are null");
    }

    @Test
    @DisplayName("Test valid single RiskFree observation FK in calculation_run_input_observation succeeds")
    void testRiskFreeInputObservationLinkageSucceeds() {
        MethodologyVersion mv = methodologyVersionRepository.save(new MethodologyVersion(
            "METH-RF-TEST", "v1.0-test", "CANDIDATE", "mock-commit-rf"
        ));
        CalculationRun run = calculationRunRepository.save(new CalculationRun(
            schemeOption, benchmark, LocalDate.of(2026, 1, 15), OffsetDateTime.now(), mv, "TEST-ENGINE"
        ));

        RiskFreeObservation rf = riskFreeObservationRepository.save(new RiskFreeObservation(
            "FBIL_91D_TBILL", LocalDate.of(2026, 1, 15), new BigDecimal("0.06900000"), 1, OffsetDateTime.now()
        ));

        CalculationRunInputObservation validRf = new CalculationRunInputObservation(run, rf, rf.getEffectiveDate(), rf.getRevisionSeq());
        CalculationRunInputObservation saved = calculationRunInputObservationRepository.saveAndFlush(validRf);
        assertNotNull(saved.getId());
        assertEquals(rf.getId(), saved.getRiskFreeObservation().getId());
        assertNull(saved.getNavObservation());
        assertNull(saved.getBenchmarkObservation());
    }
}
