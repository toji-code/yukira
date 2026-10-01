package com.yukira.backend;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.AnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class Rsk06To07EndToEndIntegrationTest {

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

    private static final LocalDate END_DATE = LocalDate.of(2024, 1, 15);
    private static final OffsetDateTime KNOWLEDGE_CUTOFF = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

    @Test
    @DisplayName("RSK-06 Real end-to-end execution against canonical pilot: Historical VaR 95% (3Y)")
    void testCanonicalPilotRsk06EndToEnd() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        Rsk06CalculationRequest request = new Rsk06CalculationRequest(
            pilot.getId(),
            END_DATE,
            KNOWLEDGE_CUTOFF,
            "CANDIDATE_V1"
        );

        RiskAnalysisResponse response = analysisService.executeRsk06Analysis(request);

        assertNotNull(response);
        assertEquals("RSK-06", response.result().metricCode());
        assertEquals("Historical Value at Risk (95% 3Y)", response.result().metricName());
        assertEquals("CALCULATED", response.result().calculationStatus());
        assertEquals("PERCENTAGE", response.result().units());
        assertNotNull(response.result().numericValue());
        assertTrue(response.result().numericValue().doubleValue() > 0.0, "Historical VaR 95% loss magnitude must be positive");
        assertNotNull(response.result().formattedValue());

        // Window verification
        assertEquals(739, response.window().observationCount());
        assertEquals(700, response.window().minObservationsRequired());

        // Methodology & Governance
        assertEquals("RSK_06_3Y_HISTORICAL_VAR_95", response.methodology().methodologyCode());
        assertEquals("CANDIDATE_V1", response.methodology().methodologyVersion());
        assertEquals("CANDIDATE", response.methodology().approvalStatus());
        assertTrue(response.methodology().isCandidate());

        // Provenance & DB verification
        assertNotNull(response.provenance().calculationRunId());
        assertEquals(739, response.provenance().inputObservations().size());

        CalculationRun persistedRun = calculationRunRepository.findById(response.provenance().calculationRunId()).orElseThrow();
        assertEquals("COMPLETED", persistedRun.getRunStatus());
        assertEquals(END_DATE, persistedRun.getAsOfDate());

        List<MetricResult> results = metricResultRepository.findByCalculationRunIdAndMetricCode(persistedRun.getId(), "RSK-06");
        assertEquals(1, results.size());
        assertEquals("3Y", results.get(0).getPeriodType());
        assertEquals("CALCULATED", results.get(0).getCalculationStatus());

        List<CalculationRunInputObservation> links = inputObservationRepository.findByCalculationRunId(persistedRun.getId());
        assertEquals(739, links.size());
    }

    @Test
    @DisplayName("RSK-07 Real end-to-end execution against canonical pilot: Historical Expected Shortfall / CVaR 95% (3Y)")
    void testCanonicalPilotRsk07EndToEnd() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        Rsk07CalculationRequest request = new Rsk07CalculationRequest(
            pilot.getId(),
            END_DATE,
            KNOWLEDGE_CUTOFF,
            "CANDIDATE_V1"
        );

        RiskAnalysisResponse response = analysisService.executeRsk07Analysis(request);

        assertNotNull(response);
        assertEquals("RSK-07", response.result().metricCode());
        assertEquals("Historical Expected Shortfall (95% 3Y)", response.result().metricName());
        assertEquals("CALCULATED", response.result().calculationStatus());
        assertEquals("PERCENTAGE", response.result().units());
        assertNotNull(response.result().numericValue());
        assertTrue(response.result().numericValue().doubleValue() > 0.0, "Historical Expected Shortfall must be positive");

        // Expected Shortfall must be >= VaR at the same confidence level
        Rsk06CalculationRequest varRequest = new Rsk06CalculationRequest(pilot.getId(), END_DATE, KNOWLEDGE_CUTOFF, "CANDIDATE_V1");
        RiskAnalysisResponse varResponse = analysisService.executeRsk06Analysis(varRequest);
        assertTrue(response.result().numericValue().compareTo(varResponse.result().numericValue()) >= 0,
            "Expected Shortfall (" + response.result().numericValue() + ") must be >= VaR (" + varResponse.result().numericValue() + ")");

        assertEquals(739, response.window().observationCount());
        assertEquals("RSK_07_3Y_EXPECTED_SHORTFALL_95", response.methodology().methodologyCode());
        assertEquals("CANDIDATE_V1", response.methodology().methodologyVersion());

        CalculationRun persistedRun = calculationRunRepository.findById(response.provenance().calculationRunId()).orElseThrow();
        List<MetricResult> results = metricResultRepository.findByCalculationRunIdAndMetricCode(persistedRun.getId(), "RSK-07");
        assertEquals(1, results.size());
        assertEquals("CALCULATED", results.get(0).getCalculationStatus());
    }

    @Test
    @DisplayName("RSK-06 and RSK-07 Insufficient observations (< 700) returns INSUFFICIENT_DATA")
    void testInsufficientObservationsHandling() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        LocalDate shortEndDate = LocalDate.of(2019, 6, 1);
        OffsetDateTime cutoff = OffsetDateTime.of(2019, 6, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        Rsk06CalculationRequest request06 = new Rsk06CalculationRequest(
            pilot.getId(),
            shortEndDate,
            cutoff,
            "CANDIDATE_V1"
        );

        RiskAnalysisResponse response06 = analysisService.executeRsk06Analysis(request06);
        assertEquals("INSUFFICIENT_DATA", response06.result().calculationStatus());
        assertNull(response06.result().numericValue());
        assertTrue(response06.limitations().insufficientEvidence());

        Rsk07CalculationRequest request07 = new Rsk07CalculationRequest(
            pilot.getId(),
            shortEndDate,
            cutoff,
            "CANDIDATE_V1"
        );

        RiskAnalysisResponse response07 = analysisService.executeRsk07Analysis(request07);
        assertEquals("INSUFFICIENT_DATA", response07.result().calculationStatus());
        assertNull(response07.result().numericValue());
        assertTrue(response07.limitations().insufficientEvidence());
    }

    @Test
    @DisplayName("RSK-06 and RSK-07 Strict PIT: requestedEndDate after knowledgeCutoff is rejected")
    void testStrictPitRejection() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        LocalDate futureEndDate = LocalDate.of(2024, 2, 15);
        OffsetDateTime pastCutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        Rsk06CalculationRequest request06 = new Rsk06CalculationRequest(
            pilot.getId(),
            futureEndDate,
            pastCutoff,
            "CANDIDATE_V1"
        );

        assertThrows(IllegalArgumentException.class, () -> analysisService.executeRsk06Analysis(request06));

        Rsk07CalculationRequest request07 = new Rsk07CalculationRequest(
            pilot.getId(),
            futureEndDate,
            pastCutoff,
            "CANDIDATE_V1"
        );

        assertThrows(IllegalArgumentException.class, () -> analysisService.executeRsk07Analysis(request07));
    }
}
