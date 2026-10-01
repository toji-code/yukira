package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.analysis.TrackingConsistencyResponse;
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
class TrackingConsistencyEndToEndIntegrationTest {

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private AnalysisService analysisService;

    @Test
    @DisplayName("Real-Data Verification: Execute MKT-01 & MKT-02 Tracking Consistency Analysis on Canonical Pilot against NIFTY 500 TRI")
    void testRealDataTrackingConsistency() {
        // 1. Ensure Canonical Pilot & Benchmark Data
        SchemeOption option = pilotBootstrapService.ensureCanonicalPilotMaster();
        assertNotNull(option);
        assertEquals("118955", option.getAmfiCode());
        assertEquals("INF179K01UT0", option.getIsin());

        pilotBootstrapService.bootstrapHistoricalHorizon(3);
        pilotBootstrapService.bootstrapHistoricalBenchmark();

        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
            .orElseThrow(() -> new IllegalStateException("NIFTY_500_TRI benchmark not registered"));

        // 2. Define Point-in-Time parameters
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // 3. Execute Tracking Consistency Analysis
        TrackingConsistencyResponse response = analysisService.executeTrackingConsistencyAnalysis(
            option.getId(),
            asOfDate,
            knowledgeCutoff,
            benchmark.getId()
        );

        assertNotNull(response);
        assertNotNull(response.context());
        assertNotNull(response.metrics());
        assertNotNull(response.epistemic());

        System.out.println("==================================================================");
        System.out.println("REAL-DATA VERIFICATION: MKT-01 & MKT-02 TRACKING CONSISTENCY");
        System.out.println("Fund: " + response.context().fundName() + " (" + response.context().amfiCode() + ")");
        System.out.println("Benchmark: " + response.context().benchmarkName());
        System.out.println("Observation Window: " + response.context().startDate() + " to " + response.context().endDate());
        System.out.println("Paired Observations: " + response.metrics().pairedObservationsCount() + " (min required: " + response.metrics().minPairedObservationsRequired() + ")");
        System.out.println("Sufficient Observations: " + response.metrics().isSufficientObservations());
        System.out.println("MKT-01 Annualized Tracking Error: " + response.metrics().trackingErrorAnnualized());
        System.out.println("MKT-01 Status: " + response.metrics().trackingErrorStatus());
        System.out.println("Mean Daily Excess Return: " + response.metrics().meanDailyExcessReturn());
        System.out.println("Annualized Mean Active Return: " + response.metrics().annualizedMeanActiveReturn());
        System.out.println("MKT-02 Information Ratio: " + response.metrics().informationRatio());
        System.out.println("MKT-02 Status: " + response.metrics().informationRatioStatus());
        System.out.println("Zero Tracking Error: " + response.metrics().zeroTrackingError());
        System.out.println("Calculation Run ID: " + response.epistemic().calculationRunId());
        System.out.println("Source SHA256: " + response.epistemic().sourceArtifactSha256());
        System.out.println("Epistemic Observation: " + response.epistemic().observation());
        System.out.println("Epistemic Interpretation: " + response.epistemic().interpretation());
        System.out.println("Epistemic Limitation: " + response.epistemic().limitation());
        System.out.println("==================================================================");

        // 4. Assertions
        assertTrue(response.metrics().pairedObservationsCount() >= 700, "Must satisfy N >= 700 paired observations");
        assertTrue(response.metrics().isSufficientObservations());
        assertEquals("CALCULATED", response.metrics().trackingErrorStatus());
        assertEquals("CALCULATED", response.metrics().informationRatioStatus());
        assertFalse(response.metrics().zeroTrackingError());

        assertNotNull(response.metrics().trackingErrorAnnualized());
        assertNotNull(response.metrics().annualizedMeanActiveReturn());
        assertNotNull(response.metrics().informationRatio());

        // Tracking error should be positive and realistic (typically 3% - 15% annualized for Indian active equity)
        double te = response.metrics().trackingErrorAnnualized().doubleValue();
        assertTrue(te > 1.0 && te < 30.0, "Tracking error should be within reasonable equity range (1% to 30%), got: " + te);

        // Mathematical invariant: IR = Annualized Active Mean / Annualized Tracking Error
        double meanActive = response.metrics().annualizedMeanActiveReturn().doubleValue();
        double ir = response.metrics().informationRatio().doubleValue();
        double expectedIr = meanActive / te;
        assertEquals(expectedIr, ir, 1e-2, "Information ratio must equal annualized active mean / annualized tracking error");

        assertNotNull(response.epistemic().calculationRunId());
        assertNotNull(response.epistemic().sourceArtifactSha256());
    }
}
