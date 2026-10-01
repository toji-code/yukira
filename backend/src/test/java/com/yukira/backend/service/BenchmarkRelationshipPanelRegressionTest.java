package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.analysis.BenchmarkRelationshipPanelResponse;
import com.yukira.backend.dto.analysis.BenchmarkRelationshipResponse;
import com.yukira.backend.dto.analysis.BetaDynamicsResponse;
import com.yukira.backend.dto.analysis.TrackingConsistencyResponse;
import com.yukira.backend.repository.BenchmarkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Benchmark Relationship Panel: Regression & Contract Hardening")
@SuppressWarnings({"null", "deprecation"})
class BenchmarkRelationshipPanelRegressionTest {

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private AnalysisService analysisService;

    private static final LocalDate AS_OF_DATE = LocalDate.of(2024, 1, 15);
    private static final OffsetDateTime KNOWLEDGE_CUTOFF =
        OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

    private SchemeOption canonicalPilot;
    private Benchmark nifty500Benchmark;

    @BeforeEach
    void setUp() {
        canonicalPilot = pilotBootstrapService.ensureCanonicalPilotMaster();
        assertNotNull(canonicalPilot);
        pilotBootstrapService.bootstrapHistoricalHorizon(3);
        pilotBootstrapService.bootstrapHistoricalBenchmark();
        nifty500Benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
            .orElseThrow(() -> new IllegalStateException("NIFTY_500_TRI not found"));
    }

    @Test
    @DisplayName("All six panel metrics use identical fund/benchmark/PIT context")
    void testPanelContextUniformity() {
        BenchmarkRelationshipPanelResponse panel = analysisService.executeBenchmarkRelationshipPanelAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        assertNotNull(panel);
        BenchmarkRelationshipPanelResponse.PanelContext ctx = panel.context();
        assertEquals(canonicalPilot.getId(), ctx.schemeOptionId());
        assertEquals(nifty500Benchmark.getId(), ctx.benchmarkId());
        assertEquals(AS_OF_DATE, ctx.endDate());
        assertEquals(KNOWLEDGE_CUTOFF, ctx.knowledgeCutoffTime());

        BenchmarkRelationshipPanelResponse.PanelMetrics metrics = panel.metrics();
        assertNotNull(metrics.activeReturn(), "ActiveReturn metric must be present");
        assertNotNull(metrics.trackingError(), "TrackingError metric must be present");
        assertNotNull(metrics.informationRatio(), "InformationRatio metric must be present");
        assertNotNull(metrics.correlation(), "Correlation metric must be present");
        assertNotNull(metrics.beta(), "Beta metric must be present");
        assertNotNull(metrics.rSquared(), "RSquared metric must be present");

        System.out.println("✓ All six panel metrics share identical context:");
        System.out.println("  Fund: " + ctx.fundName() + " (" + ctx.amfiCode() + ")");
        System.out.println("  Benchmark: " + ctx.benchmarkName());
        System.out.println("  Window: " + ctx.startDate() + " to " + ctx.endDate());
    }

    @Test
    @DisplayName("Persisted values exactly match API response values")
    void testPersistedValuesPrecision() {
        BenchmarkRelationshipPanelResponse panel = analysisService.executeBenchmarkRelationshipPanelAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        assertNotNull(panel);
        BenchmarkRelationshipPanelResponse.PanelMetrics metrics = panel.metrics();

        TrackingConsistencyResponse tracking = analysisService.executeTrackingConsistencyAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );
        BetaDynamicsResponse beta = analysisService.executeBetaCalculation(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );
        BenchmarkRelationshipResponse relationship = analysisService.executeBenchmarkRelationshipAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        if (tracking.metrics().trackingErrorAnnualized() != null) {
            assertEquals(0, tracking.metrics().trackingErrorAnnualized().compareTo(metrics.trackingError().value()),
                "Panel REL-02 (Tracking Error) value must exactly match tracking slice");
        }
        if (tracking.metrics().informationRatio() != null) {
            assertEquals(0, tracking.metrics().informationRatio().compareTo(metrics.informationRatio().value()),
                "Panel RAT-04 (Information Ratio) value must exactly match tracking slice");
        }
        if (beta.metrics().standardBeta() != null) {
            assertEquals(0, beta.metrics().standardBeta().compareTo(metrics.beta().value()),
                "Panel MKT-01 (Beta) value must exactly match beta slice");
        }
        if (relationship.metrics().correlation() != null) {
            assertEquals(0, relationship.metrics().correlation().compareTo(metrics.correlation().value()),
                "Panel correlation diagnostic must exactly match relationship slice");
        }
        if (relationship.metrics().rSquared() != null) {
            assertEquals(0, relationship.metrics().rSquared().compareTo(metrics.rSquared().value()),
                "Panel R-Squared diagnostic must exactly match relationship slice");
        }

        System.out.println("✓ All panel values exactly match source slices");
    }

    @Test
    @DisplayName("Provenance is internally consistent across all panel metrics")
    void testProvenanceConsistency() {
        BenchmarkRelationshipPanelResponse panel = analysisService.executeBenchmarkRelationshipPanelAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        assertNotNull(panel);
        BenchmarkRelationshipPanelResponse.PanelEpistemic epistemic = panel.epistemic();
        List<BenchmarkRelationshipPanelResponse.CalculationEvidence> evidence = epistemic.calculationEvidence();

        assertEquals(3, evidence.size(), "Panel must report exactly 3 calculation slices");

        String trackingHash = evidence.stream()
            .filter(e -> "TRACKING_CONSISTENCY".equals(e.sourceSlice()))
            .map(BenchmarkRelationshipPanelResponse.CalculationEvidence::sourceArtifactSha256)
            .findFirst()
            .orElse(null);

        String betaHash = evidence.stream()
            .filter(e -> "BETA_DYNAMICS".equals(e.sourceSlice()))
            .map(BenchmarkRelationshipPanelResponse.CalculationEvidence::sourceArtifactSha256)
            .findFirst()
            .orElse(null);

        String relationshipHash = evidence.stream()
            .filter(e -> "BENCHMARK_RELATIONSHIP".equals(e.sourceSlice()))
            .map(BenchmarkRelationshipPanelResponse.CalculationEvidence::sourceArtifactSha256)
            .findFirst()
            .orElse(null);

        assertNotNull(trackingHash, "TRACKING_CONSISTENCY must have source hash");
        assertNotNull(betaHash, "BETA_DYNAMICS must have source hash");
        assertNotNull(relationshipHash, "BENCHMARK_RELATIONSHIP must have source hash");

        assertEquals(trackingHash, betaHash,
            "All slices must share identical source artifact hash");
        assertEquals(trackingHash, relationshipHash,
            "All slices must share identical source artifact hash");

        String trackingQuality = evidence.stream()
            .filter(e -> "TRACKING_CONSISTENCY".equals(e.sourceSlice()))
            .map(BenchmarkRelationshipPanelResponse.CalculationEvidence::dataQualityStatus)
            .findFirst()
            .orElse(null);

        String relationshipQuality = evidence.stream()
            .filter(e -> "BENCHMARK_RELATIONSHIP".equals(e.sourceSlice()))
            .map(BenchmarkRelationshipPanelResponse.CalculationEvidence::dataQualityStatus)
            .findFirst()
            .orElse(null);

        assertEquals(trackingQuality, relationshipQuality,
            "Data quality status must be consistent across slices");

        System.out.println("✓ Provenance consistency verified");
        System.out.println("  Source hash: " + trackingHash);
        System.out.println("  Data quality: " + trackingQuality);
    }

    @Test
    @DisplayName("CANDIDATE status is preserved across all panel metrics")
    void testCandidateStatusPreservation() {
        BenchmarkRelationshipPanelResponse panel = analysisService.executeBenchmarkRelationshipPanelAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        assertNotNull(panel);
        BenchmarkRelationshipPanelResponse.PanelMetrics metrics = panel.metrics();

        List<BenchmarkRelationshipPanelResponse.MetricValue> allMetrics = Arrays.asList(
            metrics.activeReturn(),
            metrics.trackingError(),
            metrics.informationRatio(),
            metrics.beta(),
            metrics.correlation(),
            metrics.rSquared()
        );

        boolean allCandidateOrInsufficient = allMetrics.stream()
            .allMatch(m -> "CALCULATED".equals(m.status()) || "INSUFFICIENT_DATA".equals(m.status()));

        assertTrue(allCandidateOrInsufficient,
            "All metrics must report either CALCULATED or INSUFFICIENT_DATA");

        boolean allCandidateMethodology = allMetrics.stream()
            .allMatch(m -> "CANDIDATE".equals(m.methodologyStatus()));

        assertTrue(allCandidateMethodology,
            "All panel metrics must report CANDIDATE methodology status");

        System.out.println("✓ CANDIDATE status preserved across all metrics");
        allMetrics.forEach(m -> System.out.println("  " + m.metricCode() + ": " + m.methodologyStatus()));
    }

    @Test
    @DisplayName("INSUFFICIENT_DATA is represented consistently")
    void testInsufficientDataConsistency() {
        LocalDate futureDate = LocalDate.of(2030, 12, 31);
        OffsetDateTime futureCutoff = OffsetDateTime.of(2031, 1, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        BenchmarkRelationshipPanelResponse panel = analysisService.executeBenchmarkRelationshipPanelAnalysis(
            canonicalPilot.getId(), futureDate, futureCutoff, nifty500Benchmark.getId()
        );

        assertNotNull(panel);
        BenchmarkRelationshipPanelResponse.PanelMetrics metrics = panel.metrics();

        List<BenchmarkRelationshipPanelResponse.MetricValue> allMetrics = Arrays.asList(
            metrics.activeReturn(),
            metrics.trackingError(),
            metrics.informationRatio(),
            metrics.beta(),
            metrics.correlation(),
            metrics.rSquared()
        );

        assertTrue(allMetrics.stream().allMatch(m -> "INSUFFICIENT_DATA".equals(m.status())),
            "Future dates must produce INSUFFICIENT_DATA for all metrics");

        assertEquals("INSUFFICIENT_DATA", panel.metrics().resultState(),
            "Panel overall state must be INSUFFICIENT_DATA");

        assertTrue(allMetrics.stream().allMatch(m -> m.value() == null),
            "All metric values must be null on insufficient data");

        System.out.println("✓ INSUFFICIENT_DATA represented consistently");
        System.out.println("  Panel state: " + panel.metrics().resultState());
    }

    @Test
    @DisplayName("Correlation/R-Squared diagnostics satisfy R² = correlation² mathematical invariant")
    void testRSquaredMathematicalInvariant() {
        BenchmarkRelationshipPanelResponse panel = analysisService.executeBenchmarkRelationshipPanelAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        assertNotNull(panel);
        BenchmarkRelationshipPanelResponse.PanelMetrics metrics = panel.metrics();

        BigDecimal correlation = metrics.correlation().value();
        BigDecimal rSquared = metrics.rSquared().value();
        String corrStatus = metrics.correlation().status();
        String r2Status = metrics.rSquared().status();

        if ("CALCULATED".equals(corrStatus) && "CALCULATED".equals(r2Status)) {
            assertNotNull(correlation, "Correlation must not be null when CALCULATED");
            assertNotNull(rSquared, "R-Squared must not be null when CALCULATED");

            BigDecimal expectedRSquared = correlation.multiply(correlation);
            BigDecimal delta = rSquared.subtract(expectedRSquared).abs();

            assertTrue(delta.compareTo(new BigDecimal("0.0001")) < 0,
                String.format("R² must equal corr². Got: corr=%s, R²=%s, expected=%s, delta=%s",
                    correlation, rSquared, expectedRSquared, delta));

            System.out.println("✓ R² mathematical invariant verified");
            System.out.println("  Correlation: " + correlation);
            System.out.println("  R-Squared: " + rSquared);
            System.out.println("  Expected R²: " + expectedRSquared);
        } else {
            System.out.println("⊘ R² invariant check skipped (insufficient data)");
        }
    }

    @Test
    @DisplayName("No metric silently substitutes another benchmark or fabricated provenance")
    void testNoFabrication() {
        BenchmarkRelationshipPanelResponse panel = analysisService.executeBenchmarkRelationshipPanelAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        assertNotNull(panel);
        BenchmarkRelationshipPanelResponse.PanelContext ctx = panel.context();
        String expectedBenchmarkName = "Nifty 500 Total Returns Index";

        assertEquals(expectedBenchmarkName, ctx.benchmarkName(),
            "Panel must report correct benchmark name");
        assertEquals(nifty500Benchmark.getId(), ctx.benchmarkId(),
            "Panel must report correct benchmark ID");

        BenchmarkRelationshipPanelResponse.PanelEpistemic epistemic = panel.epistemic();
        String benchmarkLineage = epistemic.benchmarkLineage();

        assertTrue(benchmarkLineage.toLowerCase().contains("nifty"),
            "Benchmark lineage must reference the correct benchmark, not a substitute");
        assertTrue(benchmarkLineage.toLowerCase().contains("sync"),
            "Benchmark lineage must describe synchronous alignment mechanism");

        List<BenchmarkRelationshipPanelResponse.CalculationEvidence> evidence = epistemic.calculationEvidence();
        for (BenchmarkRelationshipPanelResponse.CalculationEvidence e : evidence) {
            if (!"BENCHMARK_RELATIONSHIP".equals(e.sourceSlice())) {
                assertNotNull(e.calculationRunId(), "Quant-backed slices must report a calculation_run ID");
            }
            assertNotNull(e.sourceArtifactSha256(), "Each slice must report source artifact hash");
            assertNotNull(e.dataQualityStatus(), "Each slice must report data quality status");
        }

        System.out.println("✓ No fabrication detected");
        System.out.println("  Benchmark: " + ctx.benchmarkName());
        System.out.println("  Calculation runs tracked for quant-backed slices");
    }

    @Test
    @DisplayName("Paired observation count is consistent across all metrics")
    void testPairedObservationCountConsistency() {
        TrackingConsistencyResponse tracking = analysisService.executeTrackingConsistencyAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );
        BetaDynamicsResponse beta = analysisService.executeBetaCalculation(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );
        BenchmarkRelationshipResponse relationship = analysisService.executeBenchmarkRelationshipAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        Integer trackingCount = tracking.metrics().pairedObservationsCount();
        Integer betaCount = beta.metrics().totalPairedDays();
        Integer relationshipCount = relationship.metrics().pairedObservationCount();

        assertEquals(trackingCount, betaCount, "MKT and REL-01 must share paired count");
        assertEquals(trackingCount, relationshipCount, "MKT and REL-02/03 must share paired count");

        BenchmarkRelationshipPanelResponse panel = analysisService.executeBenchmarkRelationshipPanelAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        assertEquals(trackingCount, panel.metrics().pairedObservationCount(),
            "Panel must report same paired count as individual slices");

        System.out.println("✓ Paired observation count consistent: " + trackingCount);
    }

    @Test
    @DisplayName("Panel aggregation preserves slice-level epistemic context")
    void testPanelSliceEpistemicPreservation() {
        BenchmarkRelationshipPanelResponse panel = analysisService.executeBenchmarkRelationshipPanelAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        assertNotNull(panel);
        BenchmarkRelationshipPanelResponse.PanelEpistemic epistemic = panel.epistemic();

        String panelObservation = epistemic.observation();
        String panelInterpretation = epistemic.interpretation();
        String panelLimitation = epistemic.limitation();

        assertTrue(panelObservation.contains("paired"), "Panel observation must explain paired alignment");
        assertTrue(panelInterpretation.contains("benchmark"), "Panel interpretation must reference benchmark relationship");
        assertTrue(panelLimitation.contains("historical"), "Panel limitation must acknowledge historical nature");

        System.out.println("✓ Panel epistemic context preserved");
        System.out.println("  Observation: " + panelObservation.substring(0, Math.min(60, panelObservation.length())) + "...");
    }

    @Test
    @DisplayName("Panel paired count meets minimum requirement")
    void testPairedCountMinimumRequirement() {
        BenchmarkRelationshipPanelResponse panel = analysisService.executeBenchmarkRelationshipPanelAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        assertNotNull(panel);
        BenchmarkRelationshipPanelResponse.PanelMetrics metrics = panel.metrics();

        Integer pairedCount = metrics.pairedObservationCount();
        Integer minRequired = metrics.minPairedObservationsRequired();

        assertNotNull(pairedCount, "Paired count must not be null");
        assertNotNull(minRequired, "Minimum required must not be null");

        assertTrue(pairedCount >= minRequired,
            "Paired observations must meet minimum requirement: " + pairedCount + " >= " + minRequired);

        assertTrue(metrics.isSufficient(), "Data sufficiency must be true when paired count meets minimum");

        System.out.println("✓ Paired count meets minimum: " + pairedCount + " >= " + minRequired);
    }
}
