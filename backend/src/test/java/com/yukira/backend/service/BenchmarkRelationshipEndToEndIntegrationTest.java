package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.analysis.BenchmarkRelationshipResponse;
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
@SuppressWarnings("null")
class BenchmarkRelationshipEndToEndIntegrationTest {

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
    @DisplayName("Real-Data Verification: Execute REL-02 & REL-03 Benchmark Relationship on Canonical Pilot vs NIFTY 500 TRI")
    void testRealDataBenchmarkRelationship() {
        SchemeOption option = pilotBootstrapService.ensureCanonicalPilotMaster();
        assertNotNull(option);
        assertEquals("118955", option.getAmfiCode());
        assertEquals("INF179K01UT0", option.getIsin());

        pilotBootstrapService.bootstrapHistoricalHorizon(3);
        pilotBootstrapService.bootstrapHistoricalBenchmark();

        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
            .orElseThrow(() -> new IllegalStateException("NIFTY_500_TRI benchmark not registered"));

        BenchmarkRelationshipResponse response = analysisService.executeBenchmarkRelationshipAnalysis(
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
        System.out.println("REAL-DATA VERIFICATION: BENCHMARK RELATIONSHIP (REL-02/03)");
        System.out.println("Fund: " + response.context().fundName() + " (" + response.context().amfiCode() + ")");
        System.out.println("Benchmark: " + response.context().benchmarkName());
        System.out.println("Observation Window: " + response.context().startDate() + " to " + response.context().endDate());
        System.out.println("Knowledge Cutoff: " + response.context().knowledgeCutoffTime());
        System.out.println("Paired Observations: " + response.metrics().pairedObservationCount()
            + " (min " + response.metrics().minPairedRequired() + ")");
        System.out.println("Benchmark Correlation diagnostic: " + response.metrics().correlation()
            + " [" + response.metrics().correlationStatus() + "]");
        System.out.println("R-Squared diagnostic: " + response.metrics().rSquared()
            + " [" + response.metrics().rSquaredStatus() + "]");
        System.out.println("Calculation Run ID: " + response.epistemic().calculationRunId());
        System.out.println("Source SHA256: " + response.epistemic().sourceArtifactSha256());
        System.out.println("Epistemic Observation: " + response.epistemic().observation());
        System.out.println("Epistemic Limitation: " + response.epistemic().limitation());
        System.out.println("==================================================================");

        assertEquals(option.getId(), response.context().schemeOptionId());
        assertEquals("HDFC Flexi Cap Fund", response.context().fundName());
        assertEquals("118955", response.context().amfiCode());
        assertEquals("INF179K01UT0", response.context().isin());
        assertEquals("DIRECT", response.context().planType());
        assertEquals("GROWTH", response.context().optionType());
        assertEquals(benchmark.getId(), response.context().benchmarkId());
        assertEquals("Nifty 500 Total Returns Index", response.context().benchmarkName());
        assertFalse(response.context().endDate().isAfter(AS_OF_DATE));
        assertEquals(KNOWLEDGE_CUTOFF, response.context().knowledgeCutoffTime());

        assertTrue(response.metrics().pairedObservationCount() >= 700,
            "Correlation/R-Squared diagnostics require >= 700 paired observations, got "
                + response.metrics().pairedObservationCount());
        assertEquals(700, response.metrics().minPairedRequired());
        assertEquals("INSUFFICIENT_DATA", response.metrics().correlationStatus());
        assertEquals("INSUFFICIENT_DATA", response.metrics().rSquaredStatus());
        assertFalse(response.metrics().isSufficient());
        assertNull(response.metrics().correlation());
        assertNull(response.metrics().rSquared());
        assertNull(response.epistemic().calculationRunId(),
            "Correlation/R-Squared diagnostics must not fabricate a calculation_run while no frozen metric identity exists");

        String sourceHash = response.epistemic().sourceArtifactSha256();
        assertNotNull(sourceHash);
        assertTrue(sourceHash.matches("[0-9a-f]{64}"));
        assertEquals("SOURCE_ARTIFACT_VERIFIED", response.epistemic().dataQualityStatus());
        assertNotNull(response.epistemic().benchmarkLineage());
        assertTrue(response.epistemic().benchmarkLineage().contains("synchronously"));
        assertTrue(response.epistemic().observation().contains("not available"));
        assertTrue(response.epistemic().observation().contains("R-Squared"));
        assertTrue(response.epistemic().limitation().contains("zero interpolation"));
    }
}
