package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.analysis.BetaDynamicsResponse;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-data end-to-end verification for the Benchmark Beta Dynamics vertical slice
 * (MKT-01 Standard Beta / MKT-02 Downside Beta / legacy upside-beta diagnostic).
 *
 * Runs the full stack against authoritative PostgreSQL ledger data:
 * PIT observation resolution -> calculation_run persistence -> quant engine dispatch
 * -> metric_result persistence -> BetaDynamicsResponse DTO assembly.
 */
@SpringBootTest
@SuppressWarnings({"null", "deprecation"})
class BetaDynamicsEndToEndIntegrationTest {

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private MetricResultRepository metricResultRepository;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    private static final LocalDate AS_OF_DATE = LocalDate.of(2024, 1, 15);
    private static final OffsetDateTime KNOWLEDGE_CUTOFF =
        OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

    @Test
    @DisplayName("Real-Data Verification: Execute MKT-01 & MKT-02 Beta Dynamics on Canonical Pilot vs NIFTY 500 TRI")
    void testRealDataBetaDynamics() {
        // 1. Ensure Canonical Pilot & Benchmark Data
        SchemeOption option = pilotBootstrapService.ensureCanonicalPilotMaster();
        assertNotNull(option);
        assertEquals("118955", option.getAmfiCode());
        assertEquals("INF179K01UT0", option.getIsin());

        pilotBootstrapService.bootstrapHistoricalHorizon(3);
        pilotBootstrapService.bootstrapHistoricalBenchmark();

        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
            .orElseThrow(() -> new IllegalStateException("NIFTY_500_TRI benchmark not registered"));

        // 2. Execute Beta Dynamics Analysis under the canonical PIT framework
        BetaDynamicsResponse response = analysisService.executeBetaCalculation(
            option.getId(),
            AS_OF_DATE,
            KNOWLEDGE_CUTOFF,
            benchmark.getId()
        );

        assertNotNull(response);
        assertNotNull(response.context());
        assertNotNull(response.metrics());
        assertNotNull(response.epistemic());

        System.out.println("==================================================================");
        System.out.println("REAL-DATA VERIFICATION: BENCHMARK BETA DYNAMICS (MKT-01/MKT-02)");
        System.out.println("Fund: " + response.context().fundName() + " (" + response.context().amfiCode() + ")");
        System.out.println("Benchmark: " + response.context().benchmarkName());
        System.out.println("Observation Window: " + response.context().startDate() + " to " + response.context().endDate());
        System.out.println("Knowledge Cutoff: " + response.context().knowledgeCutoffTime());
        System.out.println("Paired Days: " + response.metrics().totalPairedDays()
            + " (min " + response.metrics().minPairedRequired() + ")");
        System.out.println("Up / Down / Flat: " + response.metrics().upDaysCount()
            + " / " + response.metrics().downDaysCount()
            + " / " + response.metrics().flatDaysCount());
        System.out.println("MKT-01 Standard Beta: " + response.metrics().standardBeta()
            + " [" + response.metrics().standardBetaStatus() + "]");
        System.out.println("MKT-02 Downside Beta: " + response.metrics().downsideBeta()
            + " [" + response.metrics().downsideBetaStatus() + "]");
        System.out.println("REL-05 Upside Beta: " + response.metrics().upsideBeta()
            + " [" + response.metrics().upsideBetaStatus() + "]");
        System.out.println("Beta Asymmetry (up - down): " + response.metrics().betaAsymmetrySpread()
            + " [" + response.metrics().asymmetryStatus() + "]");
        System.out.println("Risk-Free Proxy: " + response.metrics().riskFreeProxy()
            + " aligned=" + response.metrics().riskFreeAligned());
        System.out.println("Calculation Run ID: " + response.epistemic().calculationRunId());
        System.out.println("Source SHA256: " + response.epistemic().sourceArtifactSha256());
        System.out.println("Epistemic Observation: " + response.epistemic().observation());
        System.out.println("Epistemic Limitation: " + response.epistemic().limitation());
        System.out.println("==================================================================");

        // 3. Context assertions: canonical pilot identity and synchronous pairing window
        assertEquals(option.getId(), response.context().schemeOptionId());
        assertEquals("HDFC Flexi Cap Fund", response.context().fundName());
        assertEquals("118955", response.context().amfiCode());
        assertEquals("INF179K01UT0", response.context().isin());
        assertEquals("DIRECT", response.context().planType());
        assertEquals("GROWTH", response.context().optionType());
        assertEquals(benchmark.getId(), response.context().benchmarkId());
        assertEquals("Nifty 500 Total Returns Index", response.context().benchmarkName());
        assertNotNull(response.context().startDate());
        assertNotNull(response.context().endDate());
        assertFalse(response.context().endDate().isAfter(AS_OF_DATE), "Window must never cross the analysis cutoff");
        assertEquals(KNOWLEDGE_CUTOFF, response.context().knowledgeCutoffTime());

        // 4. Metric sufficiency thresholds (deterministic insufficient-data contract)
        assertTrue(response.metrics().totalPairedDays() >= 700,
            "Standard beta requires >= 700 paired days, got " + response.metrics().totalPairedDays());
        assertEquals(700, response.metrics().minPairedRequired());
        assertEquals(100, response.metrics().minDownRequired());
        assertEquals(150, response.metrics().minUpRequired());
        assertEquals("CALCULATED", response.metrics().standardBetaStatus());
        assertEquals("CALCULATED", response.metrics().downsideBetaStatus());
        assertEquals("CALCULATED", response.metrics().upsideBetaStatus());
        assertTrue(response.metrics().isStandardSufficient());
        assertTrue(response.metrics().isDownsideSufficient());
        assertTrue(response.metrics().isUpsideSufficient());
        assertTrue(response.metrics().upDaysCount() >= 150);
        assertTrue(response.metrics().downDaysCount() >= 100);
        assertEquals(response.metrics().totalPairedDays(),
            response.metrics().upDaysCount() + response.metrics().downDaysCount() + response.metrics().flatDaysCount(),
            "Paired days must decompose exactly into up + down + flat");

        // 5. Canonical value verification (authoritative ledger baseline)
        double stdBeta = response.metrics().standardBeta().doubleValue();
        double downBeta = response.metrics().downsideBeta().doubleValue();
        double upBeta = response.metrics().upsideBeta().doubleValue();

        assertEquals(0.9590602545, stdBeta, 0.05, "MKT-01 canonical Beta mismatch");
        assertEquals(0.9678148690, downBeta, 0.05, "MKT-02 canonical Downside Beta mismatch");
        assertTrue(upBeta > 0.0 && upBeta < 3.0,
            "REL-05 Upside Beta must be a finite plausible ratio, got: " + upBeta);

        // Asymmetry invariant: spread == upside - downside (rounded half-up to 4dp)
        BigDecimal expectedSpread = response.metrics().upsideBeta()
            .subtract(response.metrics().downsideBeta())
            .setScale(4, RoundingMode.HALF_UP);
        assertEquals(0, expectedSpread.compareTo(response.metrics().betaAsymmetrySpread()),
            "Beta asymmetry must equal upside beta minus downside beta");
        assertEquals("CALCULATED", response.metrics().asymmetryStatus());

        // Risk-free alignment is mandatory for standard beta only
        assertTrue(response.metrics().riskFreeAligned());
        assertEquals("FBIL_91D_TBILL", response.metrics().riskFreeProxy());

        // 6. Persisted authoritative ledger verification (metric_result rows)
        Long runId = response.epistemic().calculationRunId();
        assertNotNull(runId, "Calculation run must be persisted");

        List<MetricResult> persisted = metricResultRepository.findByCalculationRunId(runId);
        Map<String, MetricResult> byCode = persisted.stream()
            .collect(Collectors.toMap(MetricResult::getMetricCode, Function.identity()));

        for (String code : List.of("MKT-01", "MKT-02", "REL-05")) {
            assertTrue(byCode.containsKey(code), "metric_result row missing for " + code);
            assertEquals("CALCULATED", byCode.get(code).getCalculationStatus(), code);
            assertNotNull(byCode.get(code).getNumericValue(), code);
            assertEquals("RATIO", byCode.get(code).getUnits(), code);
            assertEquals("3Y", byCode.get(code).getPeriodType(), code);
        }
        assertEquals(0, byCode.get("MKT-01").getNumericValue().compareTo(response.metrics().standardBeta()),
            "Persisted MKT-01 must equal API response");
        assertEquals(0, byCode.get("MKT-02").getNumericValue().compareTo(response.metrics().downsideBeta()),
            "Persisted MKT-02 must equal API response");
        assertEquals(0, byCode.get("REL-05").getNumericValue().compareTo(response.metrics().upsideBeta()),
            "Persisted REL-05 must equal API response");

        // 7. Calculation run manifest verification
        CalculationRun run = calculationRunRepository.findById(runId)
            .orElseThrow(() -> new IllegalStateException("Calculation run #" + runId + " not found"));
        assertTrue(List.of("SUCCESS", "PARTIAL_SUCCESS").contains(run.getRunStatus()),
            "Run status must reflect successful execution, got: " + run.getRunStatus());
        assertNotNull(run.getBenchmark());
        assertEquals(benchmark.getId(), run.getBenchmark().getId());
        assertEquals(AS_OF_DATE, run.getAsOfDate());
        assertTrue(run.getKnowledgeCutoffTime().isEqual(KNOWLEDGE_CUTOFF),
            "Calculation run knowledge cutoff must be the same instant as requested, got "
                + run.getKnowledgeCutoffTime() + " vs " + KNOWLEDGE_CUTOFF);
        assertNotNull(run.getInputSnapshotSha256());
        assertEquals(64, run.getInputSnapshotSha256().length());

        // 8. Provenance & epistemic disclosure verification
        String sourceHash = response.epistemic().sourceArtifactSha256();
        assertNotNull(sourceHash);
        assertTrue(sourceHash.matches("[0-9a-f]{64}"), "Source artifact hash must be a SHA-256 hex digest");
        assertEquals("SOURCE_ARTIFACT_VERIFIED", response.epistemic().dataQualityStatus());
        assertNotNull(response.epistemic().benchmarkLineage());
        assertTrue(response.epistemic().benchmarkLineage().contains("synchronously"));
        assertTrue(response.epistemic().observation().contains("Standard Beta"));
        assertTrue(response.epistemic().observation().contains("Downside Beta"));
        assertTrue(response.epistemic().observation().contains("Upside Beta"));
        assertNotNull(response.epistemic().interpretation());
        assertNotNull(response.epistemic().limitation());
        assertTrue(response.epistemic().limitation().contains("Zero interpolation"));
    }

    @Test
    @DisplayName("Real-Data Deterministic Insufficient-Data: Beta trio returns NULL values below approved thresholds")
    void testRealDataInsufficientBetaWindow() {
        SchemeOption option = pilotBootstrapService.ensureCanonicalPilotMaster();
        pilotBootstrapService.bootstrapHistoricalHorizon(3);
        pilotBootstrapService.bootstrapHistoricalBenchmark();
        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
            .orElseThrow(() -> new IllegalStateException("NIFTY_500_TRI benchmark not registered"));

        // Early window: far fewer than 700 paired trading days exist after 2021-01-15.
        LocalDate shortWindowAsOf = LocalDate.of(2021, 6, 30);
        BetaDynamicsResponse response = analysisService.executeBetaCalculation(
            option.getId(),
            shortWindowAsOf,
            KNOWLEDGE_CUTOFF,
            benchmark.getId()
        );

        assertNotNull(response);
        System.out.println("==================================================================");
        System.out.println("REAL-DATA INSUFFICIENT-DATA VERIFICATION (short window to "
            + shortWindowAsOf + ")");
        System.out.println("Paired Days: " + response.metrics().totalPairedDays());
        System.out.println("MKT-01: " + response.metrics().standardBeta()
            + " [" + response.metrics().standardBetaStatus() + "]");
        System.out.println("MKT-02: " + response.metrics().downsideBeta()
            + " [" + response.metrics().downsideBetaStatus() + "]");
        System.out.println("REL-05: " + response.metrics().upsideBeta()
            + " [" + response.metrics().upsideBetaStatus() + "]");
        System.out.println("==================================================================");

        assertTrue(response.metrics().totalPairedDays() < 700,
            "Short window must remain below the 700-day threshold");
        assertNull(response.metrics().standardBeta(), "Insufficient data must never fabricate a value");
        assertNull(response.metrics().downsideBeta());
        assertNull(response.metrics().upsideBeta());
        assertNull(response.metrics().betaAsymmetrySpread());
        assertEquals("INSUFFICIENT_DATA", response.metrics().standardBetaStatus());
        assertEquals("INSUFFICIENT_DATA", response.metrics().downsideBetaStatus());
        assertEquals("INSUFFICIENT_DATA", response.metrics().upsideBetaStatus());
        assertEquals("INSUFFICIENT_DATA", response.metrics().asymmetryStatus());
        assertFalse(response.metrics().isStandardSufficient());
        assertFalse(response.metrics().isDownsideSufficient());
        assertFalse(response.metrics().isUpsideSufficient());
        assertNotNull(response.epistemic().calculationRunId(), "Insufficient runs are still auditable");
        assertNotNull(response.epistemic().observation());
    }
}
