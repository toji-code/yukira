package com.yukira.backend.scoring.population;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.MetricResultRepository;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.RiskFreeObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.scoring.config.ScoreMethodologyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeerPopulationServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private NavObservationRepository navObservationRepository;

    @Mock
    private MetricResultRepository metricResultRepository;

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Mock
    private RiskFreeObservationRepository riskFreeObservationRepository;

    @Mock
    private ScoreMethodologyConfig methodologyConfig;

    @InjectMocks
    private PeerPopulationService peerPopulationService;

    @Test
    @DisplayName("Evaluate population: retains provisional population code when peer sample is insufficient")
    void testEvaluatePopulationRetainsProvisional() {
        PeerPopulationCriteria criteria = PeerPopulationCriteria.defaultFlexiCap();

        SchemeOption opt1 = new SchemeOption();
        opt1.setId(1L);

        when(schemeOptionRepository.findAll(any(Specification.class))).thenReturn(List.of(opt1));
        when(navObservationRepository.countBySchemeOptionId(1L)).thenReturn(10L); // Only 10 observations, below 700

        PeerPopulationEvaluationReport report = peerPopulationService.evaluatePopulation(criteria);

        assertNotNull(report);
        assertEquals("Flexi Cap Fund", report.targetCategory());
        assertEquals("INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1", report.activeProvisionalPopulation());
        assertEquals("PROVISIONAL_INSUFFICIENT_DATA", report.status());
        assertEquals(1L, report.catalogUniverseCandidatesCount());
        assertEquals(0L, report.optionsWithSufficientNavCount());
        assertFalse(report.empiricallyReady());
        assertTrue(report.dataGaps().size() >= 3);
        assertTrue(report.governanceNote().contains("IMPLEMENTED != VALIDATED != APPROVED"));
    }

    @Test
    @DisplayName("Default criteria: benchmark code is the registered TRI benchmark, never the price-return alias")
    void testDefaultCriteriaUsesRegisteredTriBenchmark() {
        PeerPopulationCriteria criteria = PeerPopulationCriteria.defaultFlexiCap();

        assertEquals("NIFTY_500_TRI", criteria.benchmarkCode());
        assertNotEquals("NIFTY_500", criteria.benchmarkCode());
        assertTrue(criteria.requiresBenchmark());
        assertEquals(700, criteria.minRequiredObservations());
    }

    @Test
    @DisplayName("Full cohort report: no canonical MetricResults yields empty distributions, never an invented one")
    void testFullCohortReportWithoutMetricResultsIsEmptyAndNotReady() {
        PeerPopulationCriteria criteria = PeerPopulationCriteria.defaultFlexiCap();

        SchemeOption pilot = new SchemeOption();
        pilot.setId(1L);

        when(schemeOptionRepository.findAll(any(Specification.class))).thenReturn(List.of(pilot));
        when(navObservationRepository.countBySchemeOptionId(1L)).thenReturn(0L);

        PeerPopulationEvaluationReport report = peerPopulationService.generateFullCohortReport(criteria);

        assertNotNull(report);
        assertEquals("PROVISIONAL_INSUFFICIENT_DATA", report.status());
        assertFalse(report.empiricallyReady());
        assertEquals("INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1", report.activeProvisionalPopulation());
        assertEquals(0, report.peersWithAnyNavCount());
        assertEquals(0, report.calibrationEligiblePeerCount());

        // One readiness entry per canonical score metric, and every distribution must be empty.
        assertEquals(PeerPopulationConstants.CANONICAL_PEER_SCORE_METRIC_CODES.size(),
            report.metricReadiness().size());
        for (String code : PeerPopulationConstants.CANONICAL_PEER_SCORE_METRIC_CODES) {
            EmpiricalDistributionSummary d = report.candidateEmpiricalDistributions().get(code);
            assertNotNull(d, "Distribution entry must exist for " + code);
            assertEquals(code, d.metricCode());
            assertEquals(0, d.sampleSize());
            assertNull(d.median());
            assertNull(d.mean());
        }

        // The readiness ladder must never assert VALIDATED or APPROVED.
        assertEquals(6, report.readinessLadder().size());
        assertTrue(report.readinessLadder().stream().anyMatch(s -> s.startsWith("VALIDATED") && s.contains("FALSE")));
        assertTrue(report.readinessLadder().stream().anyMatch(s -> s.startsWith("APPROVED") && s.contains("FALSE")));
        assertTrue(report.governanceNote().contains("VALIDATED = FALSE"));
        assertTrue(report.governanceNote().contains("APPROVED = FALSE"));
    }

    @Test
    @DisplayName("Full cohort report: unregistered benchmark is reported, never silently ignored")
    void testFullCohortReportFlagsUnregisteredBenchmark() {
        PeerPopulationCriteria criteria = PeerPopulationCriteria.defaultFlexiCap();

        SchemeOption pilot = new SchemeOption();
        pilot.setId(1L);

        when(schemeOptionRepository.findAll(any(Specification.class))).thenReturn(List.of(pilot));
        when(navObservationRepository.countBySchemeOptionId(1L)).thenReturn(0L);
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.empty());

        PeerPopulationEvaluationReport report = peerPopulationService.generateFullCohortReport(criteria);

        assertNotNull(report.benchmarkCoverage());
        assertFalse(report.benchmarkCoverage().benchmarkRegistered());
        assertNull(report.benchmarkCoverage().benchmarkId());
        assertTrue(report.benchmarkCoverage().coverageGaps().stream()
            .anyMatch(g -> g.contains("NOT registered")));
        assertFalse(report.empiricallyReady());
    }

    @Test
    @DisplayName("Window distinctness: ingestion window 2021-01-01..2024-01-15 is distinguished from analytical window 2021-01-15..2024-01-15")
    void testIngestionVsAnalyticalWindowDistinctness() {
        PeerPopulationCriteria criteria = PeerPopulationCriteria.defaultFlexiCap();

        SchemeOption peer = new SchemeOption();
        peer.setId(10189L);

        when(schemeOptionRepository.findAll(any(Specification.class))).thenReturn(List.of(peer));
        when(navObservationRepository.countBySchemeOptionId(10189L)).thenReturn(749L);

        com.yukira.backend.domain.entity.NavObservation obsStart = new com.yukira.backend.domain.entity.NavObservation();
        obsStart.setEffectiveDate(PeerPopulationConstants.INGESTION_START);
        com.yukira.backend.domain.entity.NavObservation obsEnd = new com.yukira.backend.domain.entity.NavObservation();
        obsEnd.setEffectiveDate(PeerPopulationConstants.INGESTION_END);

        when(navObservationRepository.findBySchemeOptionIdAndEffectiveDateBetweenOrderByEffectiveDateAsc(
            10189L, PeerPopulationConstants.INGESTION_START, PeerPopulationConstants.INGESTION_END))
            .thenReturn(List.of(obsStart, obsEnd));

        PeerPopulationEvaluationReport report = peerPopulationService.generateFullCohortReport(criteria);

        assertNotNull(report);
        assertEquals(1, report.peerCoverages().size());
        PeerFundCoverageSummary summary = report.peerCoverages().get(0);
        assertEquals(PeerPopulationConstants.INGESTION_START, summary.firstObservationDate());
        assertEquals(PeerPopulationConstants.INGESTION_END, summary.lastObservationDate());
        assertEquals(749, summary.observationCount());

        assertEquals(PeerPopulationConstants.ANALYSIS_START, report.analysisStartDate());
        assertEquals(PeerPopulationConstants.ANALYSIS_END, report.analysisEndDate());
        assertTrue(report.dataGaps().stream().anyMatch(g -> g.contains("Window specification")));
    }

    @Test
    @DisplayName("Metric result resolution: CALCULATED row takes deterministic precedence over failed or INSUFFICIENT_DATA run")
    void testCalculatedPrecedenceOverFailedRun() {
        PeerPopulationCriteria criteria = PeerPopulationCriteria.defaultFlexiCap();

        SchemeOption peer = new SchemeOption();
        peer.setId(10189L);

        when(schemeOptionRepository.findAll(any(Specification.class))).thenReturn(List.of(peer));
        when(navObservationRepository.countBySchemeOptionId(10189L)).thenReturn(749L);

        com.yukira.backend.domain.entity.Benchmark bm = new com.yukira.backend.domain.entity.Benchmark();
        bm.setId(123L);
        bm.setCode("NIFTY_500_TRI");
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(bm));

        // Two projections: run 100 is CALCULATED with value 0.95; run 200 is INSUFFICIENT_DATA with null value
        TestProjection validCalc = new TestProjection("MKT-01", "3Y", BigDecimal.valueOf(0.95123456), "RATIO", "CALCULATED", 100L, 10189L);
        TestProjection failedLater = new TestProjection("MKT-01", "3Y", null, "RATIO", "INSUFFICIENT_DATA", 200L, 10189L);

        when(metricResultRepository.findCalibrationMetricResults(
            anyList(), anyLong(), anyLong(), any(), any(), anyList()))
            .thenReturn(List.of(validCalc, failedLater));

        PeerPopulationEvaluationReport report = peerPopulationService.generateFullCohortReport(criteria);

        EmpiricalDistributionSummary mkt01 = report.candidateEmpiricalDistributions().get("MKT-01");
        assertNotNull(mkt01);
        assertEquals(1, mkt01.sampleSize());
        assertEquals(0.95123456, mkt01.median());
        assertEquals(0, mkt01.missingCount());
    }

    @Test
    @DisplayName("Significance threshold: N=24 retains PROVISIONAL_INSUFFICIENT_DATA and empiricallyReady=false")
    void testCohortBelow25RetainsProvisional() {
        PeerPopulationCriteria criteria = PeerPopulationCriteria.defaultFlexiCap();

        // 24 peers, all with >= 700 obs
        List<SchemeOption> peers = new java.util.ArrayList<>();
        for (long i = 2; i <= 25; i++) {
            SchemeOption opt = new SchemeOption();
            opt.setId(i);
            peers.add(opt);
            when(navObservationRepository.countBySchemeOptionId(i)).thenReturn(749L);
        }

        when(schemeOptionRepository.findAll(any(Specification.class))).thenReturn(peers);

        PeerPopulationEvaluationReport report = peerPopulationService.generateFullCohortReport(criteria);

        assertNotNull(report);
        assertEquals(24, report.calibrationEligiblePeerCount());
        assertFalse(report.empiricallyReady());
        assertEquals("PROVISIONAL_INSUFFICIENT_DATA", report.status());
        assertEquals("INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1", report.activeProvisionalPopulation());
    }

    @Test
    @DisplayName("Pilot subject exclusion: scheme_option_id=1 is excluded from eligible calibration peers")
    void testPilotSubjectFundExclusion() {
        PeerPopulationCriteria criteria = PeerPopulationCriteria.defaultFlexiCap();

        SchemeOption pilot = new SchemeOption();
        pilot.setId(1L); // pilot subject
        SchemeOption peer = new SchemeOption();
        peer.setId(10189L);

        when(schemeOptionRepository.findAll(any(Specification.class))).thenReturn(List.of(pilot, peer));
        when(navObservationRepository.countBySchemeOptionId(1L)).thenReturn(1251L);
        when(navObservationRepository.countBySchemeOptionId(10189L)).thenReturn(749L);

        PeerPopulationEvaluationReport report = peerPopulationService.generateFullCohortReport(criteria);

        assertEquals(1, report.calibrationEligiblePeerCount());
        assertEquals(1, report.eligiblePeersExcludingPilotSubject());
    }

    record TestProjection(
        String metricCode,
        String periodType,
        BigDecimal numericValue,
        String units,
        String calculationStatus,
        Long calculationRunId,
        Long schemeOptionId
    ) implements PeerMetricResultProjection {
        @Override public String getMetricCode() { return metricCode; }
        @Override public String getPeriodType() { return periodType; }
        @Override public BigDecimal getNumericValue() { return numericValue; }
        @Override public String getUnits() { return units; }
        @Override public String getCalculationStatus() { return calculationStatus; }
        @Override public Long getCalculationRunId() { return calculationRunId; }
        @Override public Long getSchemeOptionId() { return schemeOptionId; }
    }
}
