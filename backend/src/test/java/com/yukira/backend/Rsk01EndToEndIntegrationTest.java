package com.yukira.backend;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.Rsk01AnalysisResponse;
import com.yukira.backend.dto.analysis.Rsk01CalculationRequest;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.AnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@SuppressWarnings("null")
public class Rsk01EndToEndIntegrationTest {

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    @Autowired
    private CalculationRunInputObservationRepository inputObservationRepository;

    @Test
    @DisplayName("RSK-01 Real end-to-end execution against canonical pilot with 741 observations, persistence and provenance verification")
    void testCanonicalPilotRsk01EndToEnd() {
        // 1. Identify canonical pilot: HDFC Flexi Cap Fund Direct Growth (AMFI 118955, ISIN INF179K01UT0)
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        assertEquals("INF179K01UT0", pilot.getIsin());
        assertEquals("GROWTH", pilot.getOptionType());

        // 2. Parameters:
        // End date: 2024-01-15
        // Knowledge cutoff: 2024-01-31T23:59:59+05:30
        LocalDate endDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        long runCountBefore = calculationRunRepository.count();

        // 3. Execute RSK-01 via AnalysisService
        Rsk01CalculationRequest request = new Rsk01CalculationRequest(
            pilot.getId(),
            endDate,
            knowledgeCutoff,
            "CANDIDATE_V1"
        );

        Rsk01AnalysisResponse response = analysisService.executeRsk01Analysis(request);

        // Verify calculation run created
        long runCountAfter = calculationRunRepository.count();
        assertEquals(runCountBefore + 1, runCountAfter, "Exactly one calculation run must be created");

        // 4. Verify API Response Contract
        assertNotNull(response);
        System.out.println("RSK-01 Actual Obs Count: " + response.window().observationCount());
        System.out.println("RSK-01 Actual Start Date: " + response.window().actualStartDate());
        System.out.println("RSK-01 Actual End Date: " + response.window().actualEndDate());
        System.out.println("RSK-01 Actual Numeric: " + response.result().numericValue());
        System.out.println("RSK-01 Actual Formatted: " + response.result().formattedValue());

        assertEquals("RSK-01", response.result().metricCode());
        assertEquals("3-Year Annualized Volatility", response.result().metricName());
        assertEquals("CALCULATED", response.result().calculationStatus());
        assertEquals("PERCENTAGE", response.result().units());

        // Window verification
        assertEquals(LocalDate.of(2021, 1, 15), response.window().requestedStartDate());
        assertEquals(LocalDate.of(2024, 1, 15), response.window().requestedEndDate());
        assertEquals(LocalDate.of(2021, 1, 15), response.window().actualStartDate());
        assertEquals(LocalDate.of(2024, 1, 15), response.window().actualEndDate());
        assertEquals(739, response.window().observationCount());
        assertEquals(700, response.window().minObservationsRequired());
        assertEquals(36, response.window().windowMonths());

        // Methodology & Governance verification
        assertEquals("RSK_01_3Y_VOLATILITY", response.methodology().methodologyCode());
        assertEquals("CANDIDATE_V1", response.methodology().methodologyVersion());
        assertEquals("CANDIDATE", response.methodology().approvalStatus());
        assertTrue(response.methodology().isCandidate());
        assertEquals("SQRT_252_CANDIDATE", response.methodology().annualizationConvention());
        assertEquals("N_MINUS_ONE_CANDIDATE", response.methodology().denominatorConvention());

        // Benchmark disclosure verification
        assertFalse(response.benchmark().benchmarkRequired());
        assertNull(response.benchmark().benchmarkId());
        assertTrue(response.benchmark().benchmarkNotice().contains("not required"));

        // Limitations verification
        assertTrue(response.limitations().candidateAnnualizationApplied());
        assertTrue(response.limitations().candidateDenominatorApplied());
        assertFalse(response.limitations().insufficientEvidence());
        assertEquals(739, response.limitations().observationCount());
        assertEquals(700, response.limitations().minObservationsRequired());

        // Provenance & Linkages verification
        assertNotNull(response.provenance().calculationRunId());
        assertEquals("COMPLETED", response.provenance().runStatus());
        assertNotNull(response.provenance().inputSnapshotSha256());
        assertEquals(739, response.provenance().inputObservations().size());

        // 5. Database Persistence & Immutability Verification
        CalculationRun persistedRun = calculationRunRepository.findById(response.provenance().calculationRunId())
            .orElseThrow(() -> new AssertionError("CalculationRun not found in DB"));
        assertEquals("COMPLETED", persistedRun.getRunStatus());
        assertEquals(endDate, persistedRun.getAsOfDate());
        assertEquals(knowledgeCutoff, persistedRun.getKnowledgeCutoffTime());
        assertNotNull(persistedRun.getInputSnapshotSha256());
        assertNotNull(persistedRun.getMethodologyVersion());
        assertTrue(persistedRun.getMethodologyVersion().isLocked());

        List<MetricResult> metricResults = metricResultRepository.findByCalculationRunIdAndMetricCode(persistedRun.getId(), "RSK-01");
        assertEquals(1, metricResults.size());
        MetricResult persistedMetric = metricResults.get(0);
        assertEquals("3Y", persistedMetric.getPeriodType());
        assertEquals(0.150571584544, persistedMetric.getNumericValue().doubleValue(), 0.0001);
        assertEquals("CALCULATED", persistedMetric.getCalculationStatus());

        List<CalculationRunInputObservation> inputObsLinks = inputObservationRepository.findByCalculationRunId(persistedRun.getId());
        assertEquals(739, inputObsLinks.size(), "All 739 input observations must have immutable database links");

        // 6. Direct retrieval via getAnalysisByRunId
        Optional<Object> retrieved = analysisService.getAnalysisByRunId(persistedRun.getId());
        assertTrue(retrieved.isPresent());
        assertInstanceOf(Rsk01AnalysisResponse.class, retrieved.get());
        Rsk01AnalysisResponse retrievedRsk01 = (Rsk01AnalysisResponse) retrieved.get();
        assertEquals(response.result().formattedValue(), retrievedRsk01.result().formattedValue());
    }

    @Test
    @DisplayName("RSK-01 Insufficient observations (< 700) returns INSUFFICIENT_DATA and does not fabricate result")
    void testRsk01InsufficientObservations() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        // Request end date 2019-06-01 where observations only exist from 2019-01-01 (~100 trading days < 700)
        LocalDate endDate = LocalDate.of(2019, 6, 1);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2019, 6, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        Rsk01CalculationRequest request = new Rsk01CalculationRequest(
            pilot.getId(),
            endDate,
            knowledgeCutoff,
            "CANDIDATE_V1"
        );

        Rsk01AnalysisResponse response = analysisService.executeRsk01Analysis(request);

        assertNotNull(response);
        assertEquals("RSK-01", response.result().metricCode());
        assertEquals("INSUFFICIENT_DATA", response.result().calculationStatus());
        assertNull(response.result().numericValue());
        assertNull(response.result().formattedValue());
        assertNotNull(response.result().errorMessage());
        assertTrue(response.result().errorMessage().contains("minimum 700 required"));
        assertTrue(response.limitations().insufficientEvidence());
    }

    @Test
    @DisplayName("RSK-01 Strict PIT: Missing knowledgeCutoffTime is rejected")
    void testRsk01MissingKnowledgeCutoffRejected() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        Rsk01CalculationRequest request = new Rsk01CalculationRequest(
            pilot.getId(),
            LocalDate.of(2024, 1, 15),
            null,
            "CANDIDATE_V1"
        );

        assertThrows(NullPointerException.class, () -> analysisService.executeRsk01Analysis(request));
    }

    @Test
    @DisplayName("RSK-01 Strict PIT: requestedEndDate after knowledgeCutoff is rejected")
    void testRsk01FutureKnowledgeCutoffViolation() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        LocalDate endDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2023, 12, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        Rsk01CalculationRequest request = new Rsk01CalculationRequest(
            pilot.getId(),
            endDate,
            knowledgeCutoff,
            "CANDIDATE_V1"
        );

        assertThrows(IllegalArgumentException.class, () -> analysisService.executeRsk01Analysis(request));
    }
}
