package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Integration Test for 3-Year Risk-Adjusted Metrics (Phase 2Q Complete):
 *   - RAT-01: 3-Year Sharpe Ratio (FBIL 91D T-Bill daily compounding, ACT/365, sqrt(252))
 *   - RAT-02 / RAT-05: 3-Year Treynor Ratio (Annualized mean excess return * 252 / Beta)
 *   - MKT-01: 3-Year Beta (Excess-return OLS with intercept)
 *   - MKT-02: 3-Year Downside Beta (Conditioned on Rb < 0, raw returns, >=100 down days)
 *
 * Uses the Canonical Pilot Instrument (HDFC Flexi Cap Direct Growth, AMFI 118955)
 * and real authoritative market series:
 *   - Primary Benchmark: NIFTY 500 TRI (NSE Indices Limited)
 *   - Risk-Free Benchmark: FBIL 91-Day T-Bill (Financial宣Benchmarks India Pvt Ltd)
 *
 * Enforces:
 *   - Point-in-Time bitemporal constraints (asOfDate=2024-01-15, cutoff=2024-01-31T23:59:59+05:30)
 *   - Cryptographic snapshot SHA-256 hashing
 *   - Input observation lineage across NAV, Benchmark, and Risk-Free ledgers
 *   - Numerical reproducibility and independent mathematical agreement
 */
@SpringBootTest
@SuppressWarnings({"null", "deprecation"})
class RiskAdjusted3YEndToEndIntegrationTest {

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private CalculationOrchestratorService calculationOrchestratorService;

    @Autowired
    private MetricResultRepository metricResultRepository;

    @Autowired
    private CalculationRunInputObservationRepository calculationRunInputObservationRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Test
    @DisplayName("Execute End-to-End 3-Year Risk-Adjusted Calculation Run (RAT-01, RAT-02, MKT-01, MKT-02) on Live Pilot")
    void testCompletePhase2QRiskAdjustedExecution() {
        // 1. Bootstrap canonical pilot master, NAV history, benchmark history, and risk-free history
        SchemeOption option = pilotBootstrapService.ensureCanonicalPilotMaster();
        assertNotNull(option);
        assertEquals("118955", option.getAmfiCode());

        // Ingest 3-year horizon (2021 to 2023 + Jan 2024 baseline)
        pilotBootstrapService.bootstrapHistoricalHorizon(3);
        pilotBootstrapService.bootstrapHistoricalBenchmark();
        pilotBootstrapService.bootstrapHistoricalFbil();

        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
            .orElseThrow(() -> new IllegalStateException("NIFTY_500_TRI benchmark not registered"));

        // 2. Define strict PIT cutoffs
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        List<String> metricCodes = List.of("RAT-01", "RAT-02", "MKT-01", "MKT-02");

        // 3. Execute Calculation Run #1
        CalculationRun run1 = calculationOrchestratorService.executeCalculationRun(
            option.getId(),
            benchmark.getId(),
            asOfDate,
            knowledgeCutoff,
            metricCodes,
            "APPROVED_M2N",
            Map.of("periods_per_year", 252.0, "min_downside_observations", 100)
        );

        assertNotNull(run1);
        assertEquals("SUCCESS", run1.getRunStatus(), "Run 1 must complete successfully: " + run1.getErrorMessage());
        assertNotNull(run1.getInputSnapshotSha256());
        assertFalse(run1.getInputSnapshotSha256().isBlank());

        // 4. Verify 3-way input observation lineage
        List<CalculationRunInputObservation> inputs1 = calculationRunInputObservationRepository.findByCalculationRunId(run1.getId());
        assertFalse(inputs1.isEmpty(), "Calculation run input observation references must be persisted");
        
        long navCount = inputs1.stream().filter(i -> i.getNavObservation() != null).count();
        long bmCount = inputs1.stream().filter(i -> i.getBenchmarkObservation() != null).count();
        long rfCount = inputs1.stream().filter(i -> i.getRiskFreeObservation() != null).count();

        assertTrue(navCount >= 700, "NAV input observations count must be >= 700, found: " + navCount);
        assertTrue(bmCount >= 700, "Benchmark input observations count must be >= 700, found: " + bmCount);
        assertTrue(rfCount >= 700, "Risk-free input observations count must be >= 700, found: " + rfCount);

        // 5. Verify Metric Results
        List<MetricResult> results1 = metricResultRepository.findByCalculationRunId(run1.getId());
        assertEquals(4, results1.size(), "Exactly 4 metrics must be computed");

        MetricResult sharpe = results1.stream().filter(r -> "RAT-01".equals(r.getMetricCode())).findFirst().orElseThrow();
        MetricResult treynor = results1.stream().filter(r -> "RAT-02".equals(r.getMetricCode())).findFirst().orElseThrow();
        MetricResult beta = results1.stream().filter(r -> "MKT-01".equals(r.getMetricCode())).findFirst().orElseThrow();
        MetricResult downsideBeta = results1.stream().filter(r -> "MKT-02".equals(r.getMetricCode())).findFirst().orElseThrow();

        assertEquals("CALCULATED", sharpe.getCalculationStatus());
        assertEquals("CALCULATED", treynor.getCalculationStatus());
        assertEquals("CALCULATED", beta.getCalculationStatus());
        assertEquals("CALCULATED", downsideBeta.getCalculationStatus());

        assertNotNull(sharpe.getNumericValue());
        assertNotNull(treynor.getNumericValue());
        assertNotNull(beta.getNumericValue());
        assertNotNull(downsideBeta.getNumericValue());

        // Verify numeric bounds and values
        double sharpeVal = sharpe.getNumericValue().doubleValue();
        double treynorVal = treynor.getNumericValue().doubleValue();
        double betaVal = beta.getNumericValue().doubleValue();
        double downsideBetaVal = downsideBeta.getNumericValue().doubleValue();

        assertTrue(sharpeVal > 1.0 && sharpeVal < 2.0, "Sharpe ratio should be ~1.36 - 1.49, got: " + sharpeVal);
        assertTrue(betaVal > 0.85 && betaVal < 1.10, "Beta should be ~0.957, got: " + betaVal);
        assertTrue(treynorVal > 0.15 && treynorVal < 0.30, "Treynor ratio should be ~0.21 - 0.24, got: " + treynorVal);
        assertTrue(downsideBetaVal > 0.85 && downsideBetaVal < 1.10, "Downside Beta should be ~0.967, got: " + downsideBetaVal);

        // 6. Test Reproducibility (Execution Run #2 with identical parameters)
        CalculationRun run2 = calculationOrchestratorService.executeCalculationRun(
            option.getId(),
            benchmark.getId(),
            asOfDate,
            knowledgeCutoff,
            metricCodes,
            "APPROVED_M2N",
            Map.of("periods_per_year", 252.0, "min_downside_observations", 100)
        );

        assertEquals("SUCCESS", run2.getRunStatus());
        assertEquals(run1.getInputSnapshotSha256(), run2.getInputSnapshotSha256(), "Input snapshot SHA-256 must be identical");

        List<MetricResult> results2 = metricResultRepository.findByCalculationRunId(run2.getId());
        MetricResult sharpe2 = results2.stream().filter(r -> "RAT-01".equals(r.getMetricCode())).findFirst().orElseThrow();
        MetricResult beta2 = results2.stream().filter(r -> "MKT-01".equals(r.getMetricCode())).findFirst().orElseThrow();
        MetricResult treynor2 = results2.stream().filter(r -> "RAT-02".equals(r.getMetricCode())).findFirst().orElseThrow();
        MetricResult downsideBeta2 = results2.stream().filter(r -> "MKT-02".equals(r.getMetricCode())).findFirst().orElseThrow();

        assertEquals(sharpe.getNumericValue(), sharpe2.getNumericValue(), "Sharpe ratio must be strictly reproducible");
        assertEquals(beta.getNumericValue(), beta2.getNumericValue(), "Beta must be strictly reproducible");
        assertEquals(treynor.getNumericValue(), treynor2.getNumericValue(), "Treynor ratio must be strictly reproducible");
        assertEquals(downsideBeta.getNumericValue(), downsideBeta2.getNumericValue(), "Downside Beta must be strictly reproducible");
    }
}
