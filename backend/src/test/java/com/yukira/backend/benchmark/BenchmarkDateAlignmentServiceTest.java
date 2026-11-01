package com.yukira.backend.benchmark;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.BenchmarkResolutionService;
import com.yukira.backend.service.BenchmarkResolutionService.BenchmarkResolutionResult;
import com.yukira.backend.universe.EquityUniverseCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TSK-M1-04: Date-Aligned Calendar Synchronization and Metric Readiness Verification")
class BenchmarkDateAlignmentServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private NavObservationRepository navObservationRepository;

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Mock
    private RiskFreeObservationRepository riskFreeObservationRepository;

    @Mock
    private BenchmarkResolutionService benchmarkResolutionService;

    private BenchmarkDateAlignmentService alignmentService;

    private SchemeOption testOption;
    private Benchmark testBenchmark;

    @BeforeEach
    void setUp() {
        alignmentService = new BenchmarkDateAlignmentService(
            schemeOptionRepository,
            navObservationRepository,
            benchmarkRepository,
            benchmarkObservationRepository,
            riskFreeObservationRepository,
            benchmarkResolutionService
        );

        Amc amc = new Amc("HDFC Asset Management", "HDFC_MF");
        Scheme scheme = new Scheme(amc, "HDFC Flexi Cap Fund", "HDFC_FLEXI", LocalDate.of(1995, 1, 1), "Equity Scheme", "Flexi Cap Fund");
        scheme.setId(101L);

        SchemePlan plan = new SchemePlan(scheme, "Direct Plan", "DIRECT");
        testOption = new SchemeOption(plan, "118955", "INF179K01UT0", "GROWTH");
        testOption.setId(1001L);

        testBenchmark = new Benchmark("NIFTY_500_TRI", "NIFTY 500 Total Returns Index", "NSE Indices Limited", "TRI");
        testBenchmark.setId(501L);
    }

    @Test
    @DisplayName("Correctly calculates common trading dates, missing dates, and overlap ratio")
    void testSynchronousDateAlignmentAndOverlapDiagnostics() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 5);
        LocalDate asOf = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(testOption), any(LocalDate.class)))
            .thenReturn(new BenchmarkResolutionResult(
                testBenchmark, false, "CATEGORY_DEFAULT_MAPPING", null, "Resolved candidate benchmark",
                "CANDIDATE_METHODOLOGY_MAPPING", EquityUniverseCategory.FLEXI_CAP, null
            ));

        // Fund observations on Jan 1, 2, 3, 5 (missing Jan 4)
        List<NavObservation> fundObs = List.of(
            createNavObs(testOption, LocalDate.of(2024, 1, 1), "1630.7330"),
            createNavObs(testOption, LocalDate.of(2024, 1, 2), "1625.1540"),
            createNavObs(testOption, LocalDate.of(2024, 1, 3), "1626.4560"),
            createNavObs(testOption, LocalDate.of(2024, 1, 5), "1645.4000")
        );
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1001L), eq(asOf), eq(cutoff)))
            .thenReturn(fundObs);

        // Benchmark observations on Jan 1, 2, 4, 5 (missing Jan 3)
        List<BenchmarkObservation> bmObs = List.of(
            createBmObs(testBenchmark, LocalDate.of(2024, 1, 1), "27110.50"),
            createBmObs(testBenchmark, LocalDate.of(2024, 1, 2), "27050.20"),
            createBmObs(testBenchmark, LocalDate.of(2024, 1, 4), "27200.00"),
            createBmObs(testBenchmark, LocalDate.of(2024, 1, 5), "27350.80")
        );
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(501L), eq(asOf), eq(cutoff)))
            .thenReturn(bmObs);

        when(riskFreeObservationRepository.countByBenchmarkCodeAndDateRange(anyString(), any(), any()))
            .thenReturn(0L);

        BenchmarkDateAlignmentResultDto result = alignmentService.evaluateAlignment(
            testOption, start, end, asOf, cutoff
        );

        assertNotNull(result);
        assertEquals(4, result.fundDistinctValidDates());
        assertEquals(4, result.benchmarkDistinctValidDates());
        assertEquals(3, result.alignedTradingDays()); // Jan 1, 2, 5
        assertEquals(1, result.fundDatesWithoutBenchmarkCount()); // Jan 3
        assertEquals(1, result.benchmarkDatesWithoutFundCount()); // Jan 4
        assertEquals(LocalDate.of(2024, 1, 1), result.earliestAlignedDate());
        assertEquals(LocalDate.of(2024, 1, 5), result.latestAlignedDate());
        assertEquals(new BigDecimal("0.7500"), result.overlapRatio()); // 3 / 4

        // Absolute return metric (RET-02, requires >= 2 fund points) is ready
        assertTrue(result.metricReadiness().get("RET-02").isReady());

        // Relative metrics require >= 60 minimal or >= 700 standard, so disqualified
        assertFalse(result.metricReadiness().get("MKT-01").isReady());
        assertEquals("INSUFFICIENT_OVERLAP", result.alignmentStatus());
    }

    @Test
    @DisplayName("Non-TRI benchmark is rejected with NON_TRI_INCOMPATIBLE status")
    void testNonTriBenchmarkDisqualification() {
        Benchmark priBenchmark = new Benchmark("NIFTY_500_PRI", "NIFTY 500 Price Index", "NSE Indices Limited", "PRI");
        priBenchmark.setId(502L);

        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(testOption), any(LocalDate.class)))
            .thenReturn(new BenchmarkResolutionResult(
                priBenchmark, true, "EXPLICIT_HISTORICAL_MAPPING", LocalDate.of(2020, 1, 1), "Explicit PRI mapping",
                "AUTHORIZED_EXPLICIT_MAPPING", null, null
            ));

        // Generate 750 fund observations
        List<NavObservation> fundObs = generateNavObservations(testOption, 750);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(anyLong(), any(), any()))
            .thenReturn(fundObs);

        // Generate 750 benchmark observations
        List<BenchmarkObservation> bmObs = generateBenchmarkObservations(priBenchmark, 750);
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(anyLong(), any(), any()))
            .thenReturn(bmObs);

        LocalDate asOf = LocalDate.now();
        BenchmarkDateAlignmentResultDto result = alignmentService.evaluateAlignment(
            testOption, asOf.minusYears(3), asOf, asOf, null
        );

        assertNotNull(result);
        assertFalse(result.isTri());
        assertEquals("NON_TRI_INCOMPATIBLE", result.alignmentStatus());

        // Absolute metrics remain ready
        assertTrue(result.metricReadiness().get("RET-02").isReady());
        assertTrue(result.metricReadiness().get("RET-03").isReady());
        assertTrue(result.metricReadiness().get("RSK-01").isReady());
        assertTrue(result.metricReadiness().get("RSK-02").isReady());

        // Benchmark-relative metrics are strictly disqualified
        assertFalse(result.metricReadiness().get("MKT-01").isReady());
        assertFalse(result.metricReadiness().get("REL-01").isReady());
        assertFalse(result.metricReadiness().get("RAT-04").isReady());
        assertTrue(result.metricReadiness().get("MKT-01").disqualificationReasons().stream()
            .anyMatch(r -> r.contains("Total Returns Index")));
    }

    @Test
    @DisplayName("Missing benchmark data prevents false relative metric readiness")
    void testMissingBenchmarkDataHandling() {
        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(testOption), any(LocalDate.class)))
            .thenReturn(new BenchmarkResolutionResult(
                testBenchmark, false, "CATEGORY_DEFAULT_MAPPING", null, "Resolved candidate benchmark",
                "CANDIDATE_METHODOLOGY_MAPPING", EquityUniverseCategory.FLEXI_CAP, null
            ));

        List<NavObservation> fundObs = generateNavObservations(testOption, 750);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(anyLong(), any(), any()))
            .thenReturn(fundObs);

        // Zero benchmark observations exist
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(anyLong(), any(), any()))
            .thenReturn(List.of());

        LocalDate asOf = LocalDate.now();
        BenchmarkDateAlignmentResultDto result = alignmentService.evaluateAlignment(
            testOption, asOf.minusYears(3), asOf, asOf, null
        );

        assertEquals("BENCHMARK_DATA_MISSING", result.alignmentStatus());
        assertEquals(0, result.benchmarkDistinctValidDates());
        assertEquals(0, result.alignedTradingDays());

        assertFalse(result.metricReadiness().get("MKT-01").isReady());
        assertTrue(result.metricReadiness().get("MKT-01").disqualificationReasons().stream()
            .anyMatch(r -> r.contains("zero observations")));
    }

    @Test
    @DisplayName("Unmapped scheme produces BENCHMARK_UNMAPPED and blocks benchmark-relative metrics")
    void testUnmappedSchemeHandling() {
        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(testOption), any(LocalDate.class)))
            .thenReturn(new BenchmarkResolutionResult(
                null, false, "UNMAPPED", null, "Outside reference universe",
                "UNMAPPED", null, null
            ));

        List<NavObservation> fundObs = generateNavObservations(testOption, 750);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(anyLong(), any(), any()))
            .thenReturn(fundObs);

        LocalDate asOf = LocalDate.now();
        BenchmarkDateAlignmentResultDto result = alignmentService.evaluateAlignment(
            testOption, asOf.minusYears(3), asOf, asOf, null
        );

        assertEquals("BENCHMARK_UNMAPPED", result.alignmentStatus());
        assertNull(result.benchmarkCode());
        assertFalse(result.metricReadiness().get("MKT-01").isReady());
        assertTrue(result.metricReadiness().get("MKT-01").disqualificationReasons().stream()
            .anyMatch(r -> r.contains("No benchmark assigned")));
    }

    @Test
    @DisplayName("Sample sufficiency thresholds: 700+ days marks READY, 60-699 days marks READY_CANDIDATE_ONLY")
    void testSampleSufficiencyThresholdTransitions() {
        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(testOption), any(LocalDate.class)))
            .thenReturn(new BenchmarkResolutionResult(
                testBenchmark, false, "CATEGORY_DEFAULT_MAPPING", null, "Resolved candidate benchmark",
                "CANDIDATE_METHODOLOGY_MAPPING", EquityUniverseCategory.FLEXI_CAP, null
            ));

        // Case 1: 150 aligned days (satisfies minimal regression sample >= 60, but not standard 3Y >= 700)
        List<NavObservation> fund150 = generateNavObservations(testOption, 150);
        List<BenchmarkObservation> bm150 = generateBenchmarkObservations(testBenchmark, 150);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(anyLong(), any(), any()))
            .thenReturn(fund150);
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(anyLong(), any(), any()))
            .thenReturn(bm150);

        LocalDate asOf = LocalDate.now();
        BenchmarkDateAlignmentResultDto candidateResult = alignmentService.evaluateAlignment(
            testOption, asOf.minusYears(3), asOf, asOf, null
        );

        assertEquals("ALIGNED_MINIMAL_SAMPLE_SUFFICIENT", candidateResult.alignmentStatus());
        assertEquals("READY_CANDIDATE_ONLY", candidateResult.metricReadiness().get("MKT-01").readinessState());
        assertTrue(candidateResult.metricReadiness().get("MKT-01").isReady());
        assertFalse(candidateResult.metricReadiness().get("RET-03").isReady()); // Requires 700

        // Case 2: 720 aligned days (full standard 3Y readiness)
        List<NavObservation> fund720 = generateNavObservations(testOption, 720);
        List<BenchmarkObservation> bm720 = generateBenchmarkObservationsWithDownDays(testBenchmark, 720, 200);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(anyLong(), any(), any()))
            .thenReturn(fund720);
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(anyLong(), any(), any()))
            .thenReturn(bm720);

        BenchmarkDateAlignmentResultDto standardResult = alignmentService.evaluateAlignment(
            testOption, asOf.minusYears(3), asOf, asOf, null
        );

        assertEquals("ALIGNED_3Y_SUFFICIENT", standardResult.alignmentStatus());
        assertEquals("READY", standardResult.metricReadiness().get("MKT-01").readinessState());
        assertEquals("READY", standardResult.metricReadiness().get("MKT-02").readinessState());
        assertEquals("READY", standardResult.metricReadiness().get("RET-03").readinessState());
        assertEquals("READY", standardResult.metricReadiness().get("RSK-01").readinessState());
        assertEquals("READY", standardResult.metricReadiness().get("RSK-02").readinessState());
        assertEquals("READY", standardResult.metricReadiness().get("REL-01").readinessState());
        assertEquals("READY", standardResult.metricReadiness().get("RAT-04").readinessState());
    }

    @Test
    @DisplayName("Down-market trading days threshold governs MKT-02 readiness")
    void testMkt02DownMarketDaysThreshold() {
        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(testOption), any(LocalDate.class)))
            .thenReturn(new BenchmarkResolutionResult(
                testBenchmark, false, "CATEGORY_DEFAULT_MAPPING", null, "Resolved candidate benchmark",
                "CANDIDATE_METHODOLOGY_MAPPING", EquityUniverseCategory.FLEXI_CAP, null
            ));

        // 720 aligned days, but only 30 down days (less than required 100)
        List<NavObservation> fund720 = generateNavObservations(testOption, 720);
        List<BenchmarkObservation> bm720 = generateBenchmarkObservationsWithDownDays(testBenchmark, 720, 30);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(anyLong(), any(), any()))
            .thenReturn(fund720);
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(anyLong(), any(), any()))
            .thenReturn(bm720);

        LocalDate asOf = LocalDate.now();
        BenchmarkDateAlignmentResultDto result = alignmentService.evaluateAlignment(
            testOption, asOf.minusYears(3), asOf, asOf, null
        );

        assertEquals("ALIGNED_3Y_SUFFICIENT", result.alignmentStatus());
        assertTrue(result.metricReadiness().get("MKT-01").isReady());
        assertFalse(result.metricReadiness().get("MKT-02").isReady());
        assertTrue(result.metricReadiness().get("MKT-02").disqualificationReasons().stream()
            .anyMatch(r -> r.contains("at least 100 down-market benchmark trading days")));
    }

    @Test
    @DisplayName("Invalid and non-positive observations are excluded from calendar alignment")
    void testInvalidObservationsExcludedFromAlignment() {
        when(benchmarkResolutionService.resolveBenchmarkForOption(eq(testOption), any(LocalDate.class)))
            .thenReturn(new BenchmarkResolutionResult(
                testBenchmark, false, "CATEGORY_DEFAULT_MAPPING", null, "Resolved candidate benchmark",
                "CANDIDATE_METHODOLOGY_MAPPING", EquityUniverseCategory.FLEXI_CAP, null
            ));

        LocalDate baseDate = LocalDate.of(2024, 1, 1);
        NavObservation valid1 = createNavObs(testOption, baseDate, "100.00");
        NavObservation invalidObs = createNavObs(testOption, baseDate.plusDays(1), "105.00");
        invalidObs.setQualityAssessment("INVALID");
        NavObservation nonPositiveObs = createNavObs(testOption, baseDate.plusDays(2), "0.00");
        NavObservation valid2 = createNavObs(testOption, baseDate.plusDays(3), "110.00");

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(anyLong(), any(), any()))
            .thenReturn(List.of(valid1, invalidObs, nonPositiveObs, valid2));

        BenchmarkObservation bmValid1 = createBmObs(testBenchmark, baseDate, "1000.00");
        BenchmarkObservation bmNonPositive = createBmObs(testBenchmark, baseDate.plusDays(1), "-10.00");
        BenchmarkObservation bmValid2 = createBmObs(testBenchmark, baseDate.plusDays(3), "1020.00");

        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(anyLong(), any(), any()))
            .thenReturn(List.of(bmValid1, bmNonPositive, bmValid2));

        BenchmarkDateAlignmentResultDto result = alignmentService.evaluateAlignment(
            testOption, baseDate, baseDate.plusDays(3), baseDate.plusDays(10), null
        );

        assertEquals(2, result.fundDistinctValidDates()); // valid1 and valid2
        assertEquals(2, result.benchmarkDistinctValidDates()); // bmValid1 and bmValid2
        assertEquals(2, result.alignedTradingDays()); // day 0 and day 3
        assertEquals(3, result.duplicateOrInvalidObservationsExcluded()); // 2 fund invalid/nonpositive + 1 bm nonpositive
    }

    // Helper methods
    private NavObservation createNavObs(SchemeOption option, LocalDate date, String nav) {
        NavObservation obs = new NavObservation(option, date, new BigDecimal(nav), 1, date.atTime(21, 0).atOffset(ZoneOffset.ofHoursMinutes(5, 30)));
        obs.setQualityAssessment("VALID");
        return obs;
    }

    private BenchmarkObservation createBmObs(Benchmark bm, LocalDate date, String level) {
        return new BenchmarkObservation(bm, date, new BigDecimal(level), 1, date.atTime(18, 0).atOffset(ZoneOffset.ofHoursMinutes(5, 30)));
    }

    private List<NavObservation> generateNavObservations(SchemeOption option, int count) {
        List<NavObservation> list = new ArrayList<>();
        LocalDate asOf = LocalDate.now();
        LocalDate start = asOf.minusDays(count);
        for (int i = 0; i < count; i++) {
            list.add(createNavObs(option, start.plusDays(i), "1000.00"));
        }
        return list;
    }

    private List<BenchmarkObservation> generateBenchmarkObservations(Benchmark bm, int count) {
        List<BenchmarkObservation> list = new ArrayList<>();
        LocalDate asOf = LocalDate.now();
        LocalDate start = asOf.minusDays(count);
        for (int i = 0; i < count; i++) {
            list.add(createBmObs(bm, start.plusDays(i), "20000.00"));
        }
        return list;
    }

    private List<BenchmarkObservation> generateBenchmarkObservationsWithDownDays(Benchmark bm, int totalCount, int downDaysCount) {
        List<BenchmarkObservation> list = new ArrayList<>();
        LocalDate asOf = LocalDate.now();
        LocalDate start = asOf.minusDays(totalCount);
        double level = 20000.0;
        list.add(createBmObs(bm, start, String.format(Locale.ENGLISH, "%.2f", level)));

        for (int i = 1; i < totalCount; i++) {
            if (i <= downDaysCount) {
                level -= 10.0; // down day
            } else {
                level += 15.0; // up day
            }
            list.add(createBmObs(bm, start.plusDays(i), String.format(Locale.ENGLISH, "%.2f", level)));
        }
        return list;
    }
}
