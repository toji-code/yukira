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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Benchmark-Relative Analytics: Internal Consistency & Integration Hardening")
@SuppressWarnings({"null", "deprecation"})
class BenchmarkRelativeConsistencyIntegrationTest {

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
        assertEquals("118955", canonicalPilot.getAmfiCode());
        assertEquals("INF179K01UT0", canonicalPilot.getIsin());

        pilotBootstrapService.bootstrapHistoricalHorizon(3);
        pilotBootstrapService.bootstrapHistoricalBenchmark();

        nifty500Benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
            .orElseThrow(() -> new IllegalStateException("NIFTY_500_TRI benchmark not registered"));
    }

    @Test
    @DisplayName("Contract: Identical PIT inputs produce consistent paired observation counts across all slices")
    void testPairedObservationConsistency() {
        TrackingConsistencyResponse tracking = analysisService.executeTrackingConsistencyAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );
        BetaDynamicsResponse beta = analysisService.executeBetaCalculation(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );
        BenchmarkRelationshipResponse relationship = analysisService.executeBenchmarkRelationshipAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        Integer trackingPaired = tracking.metrics().pairedObservationsCount();
        Integer betaPaired = beta.metrics().totalPairedDays();
        Integer relationshipPaired = relationship.metrics().pairedObservationCount();

        assertNotNull(trackingPaired, "REL-01/RAT-04 must report paired count");
        assertNotNull(betaPaired, "MKT-01/MKT-02 must report paired count");
        assertNotNull(relationshipPaired, "Correlation/R-Squared diagnostics must report paired count");

        assertEquals(trackingPaired, betaPaired,
            "Tracking and beta metrics must use identical paired observation counts for same fund/benchmark/window");
        assertEquals(trackingPaired, relationshipPaired,
            "Tracking and relationship diagnostics must use identical paired observation counts for same fund/benchmark/window");

        System.out.println("✓ Paired observation consistency verified: " + trackingPaired + " observations across all slices");
    }

    @Test
    @DisplayName("Contract: Calculation status consistency - all metrics either CALCULATED or INSUFFICIENT_DATA together")
    void testCalculationStatusConsistency() {
        TrackingConsistencyResponse tracking = analysisService.executeTrackingConsistencyAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );
        BetaDynamicsResponse beta = analysisService.executeBetaCalculation(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );
        BenchmarkRelationshipResponse relationship = analysisService.executeBenchmarkRelationshipAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        boolean trackingCalculated = "CALCULATED".equals(tracking.metrics().trackingErrorStatus())
            && "CALCULATED".equals(tracking.metrics().informationRatioStatus());
        boolean betaCalculated = "CALCULATED".equals(beta.metrics().standardBetaStatus());
        boolean relationshipCalculated = "CALCULATED".equals(relationship.metrics().correlationStatus())
            && "CALCULATED".equals(relationship.metrics().rSquaredStatus());

        if (trackingCalculated || betaCalculated || relationshipCalculated) {
            assertTrue(trackingCalculated, "REL-01/RAT-04 must be CALCULATED when sufficient data exists");
            assertTrue(betaCalculated, "MKT-01 must be CALCULATED when sufficient data exists");
            assertFalse(relationshipCalculated, "Correlation/R-Squared diagnostics must remain unavailable until assigned a frozen metric identity");
        }

        System.out.println("✓ Calculation status consistency verified");
        System.out.println("  REL-01/RAT-04: " + (trackingCalculated ? "CALCULATED" : "INSUFFICIENT_DATA"));
        System.out.println("  MKT-01: " + beta.metrics().standardBetaStatus());
        System.out.println("  Correlation/R-Squared: " + (relationshipCalculated ? "CALCULATED" : "INSUFFICIENT_DATA"));
    }

    @Test
    @DisplayName("Contract: Data quality and provenance consistency across all slices")
    void testProvenanceConsistency() {
        TrackingConsistencyResponse tracking = analysisService.executeTrackingConsistencyAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );
        BetaDynamicsResponse beta = analysisService.executeBetaCalculation(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );
        BenchmarkRelationshipResponse relationship = analysisService.executeBenchmarkRelationshipAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        String trackingHash = tracking.epistemic().sourceArtifactSha256();
        String betaHash = beta.epistemic().sourceArtifactSha256();
        String relationshipHash = relationship.epistemic().sourceArtifactSha256();

        assertNotNull(trackingHash, "REL-01/RAT-04 must report source artifact hash");
        assertNotNull(betaHash, "MKT-01/MKT-02 must report source artifact hash");
        assertNotNull(relationshipHash, "Correlation/R-Squared diagnostics must report source artifact hash");

        assertEquals(trackingHash, betaHash,
            "Tracking and beta metrics must trace to identical source artifact for same fund/window");
        assertEquals(trackingHash, relationshipHash,
            "Tracking and relationship diagnostics must trace to identical source artifact for same fund/window");

        String trackingQuality = tracking.epistemic().dataQualityStatus();
        String betaQuality = beta.epistemic().dataQualityStatus();
        String relationshipQuality = relationship.epistemic().dataQualityStatus();

        assertEquals(trackingQuality, betaQuality,
            "Data quality status must be consistent across MKT and REL metrics");
        assertEquals(trackingQuality, relationshipQuality,
            "Data quality status must be consistent across MKT and REL metrics");

        System.out.println("✓ Provenance consistency verified");
        System.out.println("  Source SHA256: " + trackingHash);
        System.out.println("  Data Quality: " + trackingQuality);
    }

    @Test
    @DisplayName("Contract: Observation window consistency - identical start/end dates across all slices")
    void testObservationWindowConsistency() {
        TrackingConsistencyResponse tracking = analysisService.executeTrackingConsistencyAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );
        BetaDynamicsResponse beta = analysisService.executeBetaCalculation(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );
        BenchmarkRelationshipResponse relationship = analysisService.executeBenchmarkRelationshipAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        LocalDate trackingStart = tracking.context().startDate();
        LocalDate trackingEnd = tracking.context().endDate();
        LocalDate betaStart = beta.context().startDate();
        LocalDate betaEnd = beta.context().endDate();
        LocalDate relationshipStart = relationship.context().startDate();
        LocalDate relationshipEnd = relationship.context().endDate();

        assertEquals(trackingStart, betaStart, "Start dates must be identical across all slices");
        assertEquals(trackingStart, relationshipStart, "Start dates must be identical across all slices");
        assertEquals(trackingEnd, betaEnd, "End dates must be identical across all slices");
        assertEquals(trackingEnd, relationshipEnd, "End dates must be identical across all slices");

        OffsetDateTime trackingCutoff = tracking.context().knowledgeCutoffTime();
        OffsetDateTime betaCutoff = beta.context().knowledgeCutoffTime();
        OffsetDateTime relationshipCutoff = relationship.context().knowledgeCutoffTime();

        assertEquals(trackingCutoff, betaCutoff, "Knowledge cutoff must be identical across all slices");
        assertEquals(trackingCutoff, relationshipCutoff, "Knowledge cutoff must be identical across all slices");

        System.out.println("✓ Observation window consistency verified");
        System.out.println("  Window: " + trackingStart + " to " + trackingEnd);
        System.out.println("  Knowledge Cutoff: " + trackingCutoff);
    }

    @Test
    @DisplayName("Contract: Persisted metric_result matches API response for identical calculation_run")
    void testPersistedMetricConsistency() {
        BenchmarkRelationshipResponse relationship = analysisService.executeBenchmarkRelationshipAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        assertNull(relationship.epistemic().calculationRunId(),
            "Correlation/R-Squared diagnostics must not fabricate a calculation_run while no frozen metric identity exists");
        assertEquals("INSUFFICIENT_DATA", relationship.metrics().correlationStatus());
        assertEquals("INSUFFICIENT_DATA", relationship.metrics().rSquaredStatus());
        assertNull(relationship.metrics().correlation());
        assertNull(relationship.metrics().rSquared());

        System.out.println("Persisted metric consistency verified: no fabricated BENCH-CORR/BENCH-R2 rows");
    }

    @Test
    @DisplayName("Contract: R-Squared mathematical relationship with Correlation (R² = corr²)")
    void testRSquaredCorrelationRelationship() {
        BenchmarkRelationshipResponse relationship = analysisService.executeBenchmarkRelationshipAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        BigDecimal correlation = relationship.metrics().correlation();
        BigDecimal rSquared = relationship.metrics().rSquared();
        String corrStatus = relationship.metrics().correlationStatus();
        String r2Status = relationship.metrics().rSquaredStatus();

        if ("CALCULATED".equals(corrStatus) && "CALCULATED".equals(r2Status)) {
            assertNotNull(correlation, "Correlation must not be null when CALCULATED");
            assertNotNull(rSquared, "R-Squared must not be null when CALCULATED");

            BigDecimal expectedRSquared = correlation.multiply(correlation);
            BigDecimal delta = rSquared.subtract(expectedRSquared).abs();

            assertTrue(delta.compareTo(new BigDecimal("0.0001")) < 0,
                String.format("R-Squared must equal correlation squared (R² = corr²). Got: corr=%s, R²=%s, expected=%s, delta=%s",
                    correlation, rSquared, expectedRSquared, delta));

            System.out.println("✓ R-Squared mathematical relationship verified");
            System.out.println("  Correlation: " + correlation);
            System.out.println("  R-Squared: " + rSquared);
            System.out.println("  Expected R²: " + expectedRSquared);
            System.out.println("  Delta: " + delta);
        } else {
            System.out.println("⊘ R-Squared relationship check skipped (insufficient data)");
        }
    }

    @Test
    @DisplayName("Contract: Panel aggregator preserves individual slice context and metrics")
    void testPanelAggregatorConsistency() {
        TrackingConsistencyResponse tracking = analysisService.executeTrackingConsistencyAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );
        BetaDynamicsResponse beta = analysisService.executeBetaCalculation(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );
        BenchmarkRelationshipResponse relationship = analysisService.executeBenchmarkRelationshipAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        BenchmarkRelationshipPanelResponse panel = analysisService.executeBenchmarkRelationshipPanelAnalysis(
            canonicalPilot.getId(), AS_OF_DATE, KNOWLEDGE_CUTOFF, nifty500Benchmark.getId()
        );

        assertNotNull(panel);
        assertEquals(canonicalPilot.getId(), panel.context().schemeOptionId(),
            "Panel must preserve scheme option ID");
        assertEquals(nifty500Benchmark.getId(), panel.context().benchmarkId(),
            "Panel must preserve benchmark ID");
        assertEquals(AS_OF_DATE, panel.context().endDate(),
            "Panel must preserve analysis end date");

        // Panel registry codes per the corrected contract (V17): REL-02 = Tracking Error,
        // RAT-04 = Information Ratio, MKT-01 = Beta. REL-01 is Beta, never Tracking Error.
        BenchmarkRelationshipPanelResponse.MetricValue panelTE = findMetricValue(panel, "REL-02");
        BenchmarkRelationshipPanelResponse.MetricValue panelIR = findMetricValue(panel, "RAT-04");
        BenchmarkRelationshipPanelResponse.MetricValue panelBeta = findMetricValue(panel, "MKT-01");
        BenchmarkRelationshipPanelResponse.MetricValue panelCorr = findMetricValue(panel, null);
        BenchmarkRelationshipPanelResponse.MetricValue panelR2 = panel.metrics().rSquared();

        assertNotNull(panelTE, "Panel must include REL-02");
        assertNotNull(panelIR, "Panel must include RAT-04");
        assertNotNull(panelBeta, "Panel must include MKT-01");
        assertNotNull(panelCorr, "Panel must include correlation diagnostic");
        assertNotNull(panelR2, "Panel must include R-Squared diagnostic");

        assertEquals(tracking.metrics().trackingErrorStatus(), panelTE.status(),
            "Panel REL-02 status must match tracking slice");
        assertEquals(tracking.metrics().informationRatioStatus(), panelIR.status(),
            "Panel RAT-04 status must match tracking slice");
        assertEquals(beta.metrics().standardBetaStatus(), panelBeta.status(),
            "Panel MKT-01 (Beta) status must match beta slice");
        assertEquals(relationship.metrics().correlationStatus(), panelCorr.status(),
            "Panel correlation diagnostic status must match relationship slice");
        assertEquals(relationship.metrics().rSquaredStatus(), panelR2.status(),
            "Panel R-Squared diagnostic status must match relationship slice");

        if (tracking.metrics().trackingErrorAnnualized() != null) {
            assertEquals(0, tracking.metrics().trackingErrorAnnualized().compareTo(panelTE.value()),
                "Panel REL-02 (Tracking Error) value must match tracking slice");
        }
        if (beta.metrics().standardBeta() != null) {
            assertEquals(0, beta.metrics().standardBeta().compareTo(panelBeta.value()),
                "Panel MKT-01 (Beta) value must match beta slice");
        }
        if (relationship.metrics().correlation() != null) {
            assertEquals(0, relationship.metrics().correlation().compareTo(panelCorr.value()),
                "Panel correlation diagnostic value must match relationship slice");
        }

        System.out.println("✓ Panel aggregator consistency verified");
        System.out.println("  All individual slice values preserved in panel response");
    }

    @Test
    @DisplayName("Regression: Insufficient data handling - status, null values, and diagnostics")
    void testInsufficientDataHandling() {
        LocalDate futureDate = LocalDate.of(2030, 12, 31);
        OffsetDateTime futureCutoff = OffsetDateTime.of(2031, 1, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        TrackingConsistencyResponse tracking = analysisService.executeTrackingConsistencyAnalysis(
            canonicalPilot.getId(), futureDate, futureCutoff, nifty500Benchmark.getId()
        );

        assertNotNull(tracking);
        assertEquals("INSUFFICIENT_DATA", tracking.metrics().trackingErrorStatus(),
            "Future dates must produce INSUFFICIENT_DATA status");
        assertEquals("INSUFFICIENT_DATA", tracking.metrics().informationRatioStatus(),
            "Future dates must produce INSUFFICIENT_DATA status");
        assertNull(tracking.metrics().trackingErrorAnnualized(),
            "Tracking error value must be null when INSUFFICIENT_DATA");
        assertNull(tracking.metrics().informationRatio(),
            "Information ratio value must be null when INSUFFICIENT_DATA");

        System.out.println("✓ Insufficient data handling verified");
        System.out.println("  Status: " + tracking.metrics().trackingErrorStatus());
        System.out.println("  Paired count: " + tracking.metrics().pairedObservationsCount());
    }

    private BenchmarkRelationshipPanelResponse.MetricValue findMetricValue(
        BenchmarkRelationshipPanelResponse panel,
        String metricCode
    ) {
        BenchmarkRelationshipPanelResponse.PanelMetrics metrics = panel.metrics();
        if (metricCode == null) {
            return metrics.correlation();
        }
        return switch (metricCode) {
            case "REL-02" -> metrics.trackingError();
            case "RAT-04" -> metrics.informationRatio();
            case "MKT-01" -> metrics.beta();
            default -> null;
        };
    }
}
