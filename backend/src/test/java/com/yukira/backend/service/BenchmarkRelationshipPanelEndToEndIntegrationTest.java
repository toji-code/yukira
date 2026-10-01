package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.analysis.BenchmarkRelationshipPanelResponse;
import com.yukira.backend.repository.BenchmarkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BenchmarkRelationshipPanelEndToEndIntegrationTest {

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private AnalysisService analysisService;

    private static final LocalDate AS_OF_DATE = LocalDate.of(2024, 1, 15);
    private static final OffsetDateTime KNOWLEDGE_CUTOFF =
        OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

    @Test
    @DisplayName("Real-Data Verification: Benchmark Relationship panel aggregates existing auditable outputs")
    void testRealDataBenchmarkRelationshipPanel() {
        SchemeOption option = pilotBootstrapService.ensureCanonicalPilotMaster();
        assertEquals("118955", option.getAmfiCode());
        assertEquals("INF179K01UT0", option.getIsin());

        pilotBootstrapService.bootstrapHistoricalHorizon(3);
        pilotBootstrapService.bootstrapHistoricalBenchmark();

        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
            .orElseThrow(() -> new IllegalStateException("NIFTY_500_TRI benchmark not registered"));

        BenchmarkRelationshipPanelResponse response = analysisService.executeBenchmarkRelationshipPanelAnalysis(
            option.getId(),
            AS_OF_DATE,
            KNOWLEDGE_CUTOFF,
            benchmark.getId()
        );

        assertNotNull(response);
        assertEquals(option.getId(), response.context().schemeOptionId());
        assertEquals("HDFC Flexi Cap Fund", response.context().fundName());
        assertEquals("Nifty 500 Total Returns Index", response.context().benchmarkName());
        assertEquals(KNOWLEDGE_CUTOFF, response.context().knowledgeCutoffTime());
        assertEquals("CALCULATED", response.metrics().resultState());
        assertTrue(response.metrics().isSufficient());
        assertTrue(response.metrics().pairedObservationCount() >= 700);
        assertEquals(700, response.metrics().minPairedObservationsRequired());

        assertEquals("CALCULATED", response.metrics().activeReturn().status());
        assertEquals("CALCULATED", response.metrics().trackingError().status());
        assertEquals("CALCULATED", response.metrics().informationRatio().status());
        assertEquals("INSUFFICIENT_DATA", response.metrics().correlation().status());
        assertEquals("CALCULATED", response.metrics().beta().status());
        assertEquals("INSUFFICIENT_DATA", response.metrics().rSquared().status());

        assertEquals(6.24, response.metrics().activeReturn().value().doubleValue(), 0.01);
        assertEquals(5.90, response.metrics().trackingError().value().doubleValue(), 0.01);
        assertEquals(1.06, response.metrics().informationRatio().value().doubleValue(), 0.01);
        assertNull(response.metrics().correlation().value());
        assertEquals(0.9590602545, response.metrics().beta().value().doubleValue(), 0.05);
        assertNull(response.metrics().rSquared().value());

        assertEquals("SOURCE_ARTIFACT_VERIFIED", response.epistemic().dataQualityStatus());
        assertEquals(3, response.epistemic().calculationEvidence().size());
        response.epistemic().calculationEvidence().forEach(evidence -> {
            if (!"BENCHMARK_RELATIONSHIP".equals(evidence.sourceSlice())) {
                assertNotNull(evidence.calculationRunId());
            }
            assertNotNull(evidence.sourceArtifactSha256());
            assertTrue(evidence.sourceArtifactSha256().matches("[0-9a-f]{64}"));
            assertNotNull(evidence.benchmarkLineage());
        });

        System.out.println("==================================================================");
        System.out.println("REAL-DATA VERIFICATION: BENCHMARK RELATIONSHIP PANEL");
        System.out.println("Fund: " + response.context().fundName() + " (" + response.context().amfiCode() + ")");
        System.out.println("Benchmark: " + response.context().benchmarkName());
        System.out.println("Observation Window: " + response.context().startDate() + " to " + response.context().endDate());
        System.out.println("Paired Observations: " + response.metrics().pairedObservationCount());
        System.out.println("Active Return: " + response.metrics().activeReturn().value());
        System.out.println("Tracking Error: " + response.metrics().trackingError().value());
        System.out.println("Information Ratio: " + response.metrics().informationRatio().value());
        System.out.println("Correlation: " + response.metrics().correlation().value());
        System.out.println("Beta: " + response.metrics().beta().value());
        System.out.println("R-Squared: " + response.metrics().rSquared().value());
        System.out.println("Evidence Count: " + response.epistemic().calculationEvidence().size());
        System.out.println("==================================================================");
    }
}
