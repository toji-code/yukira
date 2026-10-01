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
public class Rsk02To05EndToEndIntegrationTest {

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
    @DisplayName("RSK-02 Real end-to-end execution against canonical pilot: Downside Semideviation")
    void testCanonicalPilotRsk02EndToEnd() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        Rsk02CalculationRequest request = new Rsk02CalculationRequest(
            pilot.getId(),
            END_DATE,
            KNOWLEDGE_CUTOFF,
            "CANDIDATE_V1"
        );

        RiskAnalysisResponse response = analysisService.executeRsk02Analysis(request);

        assertNotNull(response);
        assertEquals("RSK-02", response.result().metricCode());
        assertEquals("Downside Semideviation (3Y)", response.result().metricName());
        assertEquals("CALCULATED", response.result().calculationStatus());
        assertEquals("PERCENTAGE", response.result().units());
        assertNotNull(response.result().numericValue());
        assertTrue(response.result().numericValue().doubleValue() > 0.0, "Downside semideviation must be positive");
        assertNotNull(response.result().formattedValue());

        // Window verification
        assertEquals(739, response.window().observationCount());
        assertEquals(700, response.window().minObservationsRequired());

        // Methodology & Governance
        assertEquals("RSK_02_3Y_DOWNSIDE_DEVIATION", response.methodology().methodologyCode());
        assertEquals("CANDIDATE_V1", response.methodology().methodologyVersion());
        assertEquals("CANDIDATE", response.methodology().approvalStatus());
        assertTrue(response.methodology().isCandidate());

        // Provenance & DB
        assertNotNull(response.provenance().calculationRunId());
        assertEquals(739, response.provenance().inputObservations().size());

        CalculationRun persistedRun = calculationRunRepository.findById(response.provenance().calculationRunId()).orElseThrow();
        assertEquals("COMPLETED", persistedRun.getRunStatus());
        assertEquals(END_DATE, persistedRun.getAsOfDate());

        List<MetricResult> results = metricResultRepository.findByCalculationRunIdAndMetricCode(persistedRun.getId(), "RSK-02");
        assertEquals(1, results.size());
        assertEquals("3Y", results.get(0).getPeriodType());
        assertEquals("CALCULATED", results.get(0).getCalculationStatus());

        List<CalculationRunInputObservation> links = inputObservationRepository.findByCalculationRunId(persistedRun.getId());
        assertEquals(739, links.size());
    }

    @Test
    @DisplayName("RSK-03 Real end-to-end execution against canonical pilot: Maximum Drawdown, 3Y")
    void testCanonicalPilotRsk03EndToEnd() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        Rsk03CalculationRequest request = new Rsk03CalculationRequest(
            pilot.getId(),
            END_DATE,
            KNOWLEDGE_CUTOFF,
            "CANDIDATE_V1"
        );

        RiskAnalysisResponse response = analysisService.executeRsk03Analysis(request);

        assertNotNull(response);
        assertEquals("RSK-03", response.result().metricCode());
        assertEquals("3-Year Maximum Drawdown", response.result().metricName());
        assertEquals("CALCULATED", response.result().calculationStatus());
        assertEquals("PERCENTAGE", response.result().units());
        assertNotNull(response.result().numericValue());
        assertTrue(response.result().numericValue().doubleValue() <= 0.0, "Max drawdown must be non-positive");

        assertEquals(739, response.window().observationCount());
        assertEquals("RSK_03_3Y_MAX_DRAWDOWN", response.methodology().methodologyCode());
        assertEquals("CANDIDATE_V1", response.methodology().methodologyVersion());

        CalculationRun persistedRun = calculationRunRepository.findById(response.provenance().calculationRunId()).orElseThrow();
        List<MetricResult> results = metricResultRepository.findByCalculationRunIdAndMetricCode(persistedRun.getId(), "RSK-03");
        assertEquals(1, results.size());
        assertEquals("CALCULATED", results.get(0).getCalculationStatus());
    }

    @Test
    @DisplayName("RSK-04 Real end-to-end execution against canonical pilot: Maximum Drawdown Duration")
    void testCanonicalPilotRsk04EndToEnd() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        Rsk04CalculationRequest request = new Rsk04CalculationRequest(
            pilot.getId(),
            END_DATE,
            KNOWLEDGE_CUTOFF,
            "CANDIDATE_V1"
        );

        RiskAnalysisResponse response = analysisService.executeRsk04Analysis(request);

        assertNotNull(response);
        assertEquals("RSK-04", response.result().metricCode());
        assertEquals("Maximum Drawdown Duration", response.result().metricName());
        assertEquals("CALCULATED", response.result().calculationStatus());
        assertEquals("DAYS", response.result().units());
        assertNotNull(response.result().numericValue());
        assertTrue(response.result().numericValue().doubleValue() >= 0.0, "Duration must be non-negative");

        assertEquals(739, response.window().observationCount());
        assertEquals("RSK_04_MAX_DRAWDOWN_DURATION", response.methodology().methodologyCode());

        CalculationRun persistedRun = calculationRunRepository.findById(response.provenance().calculationRunId()).orElseThrow();
        List<MetricResult> results = metricResultRepository.findByCalculationRunIdAndMetricCode(persistedRun.getId(), "RSK-04");
        assertEquals(1, results.size());
        assertEquals("DAYS", results.get(0).getUnits());
    }

    @Test
    @DisplayName("RSK-05 Real end-to-end execution against canonical pilot: Ulcer Index")
    void testCanonicalPilotRsk05EndToEnd() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        Rsk05CalculationRequest request = new Rsk05CalculationRequest(
            pilot.getId(),
            END_DATE,
            KNOWLEDGE_CUTOFF,
            "CANDIDATE_V1"
        );

        RiskAnalysisResponse response = analysisService.executeRsk05Analysis(request);

        assertNotNull(response);
        assertEquals("RSK-05", response.result().metricCode());
        assertEquals("Ulcer Index (3Y)", response.result().metricName());
        assertEquals("CALCULATED", response.result().calculationStatus());
        assertEquals("POINTS", response.result().units());
        assertNotNull(response.result().numericValue());
        assertTrue(response.result().numericValue().doubleValue() >= 0.0, "Ulcer index must be non-negative");

        assertEquals(739, response.window().observationCount());
        assertEquals("RSK_05_3Y_ULCER_INDEX", response.methodology().methodologyCode());

        CalculationRun persistedRun = calculationRunRepository.findById(response.provenance().calculationRunId()).orElseThrow();
        List<MetricResult> results = metricResultRepository.findByCalculationRunIdAndMetricCode(persistedRun.getId(), "RSK-05");
        assertEquals(1, results.size());
        assertEquals("POINTS", results.get(0).getUnits());
    }

    @Test
    @DisplayName("RSK-02 to RSK-05 Insufficient observations (< 700) returns INSUFFICIENT_DATA")
    void testRsk02To05InsufficientObservations() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        LocalDate shortEndDate = LocalDate.of(2019, 6, 1);
        OffsetDateTime cutoff = OffsetDateTime.of(2019, 6, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        Rsk02CalculationRequest rsk02Req = new Rsk02CalculationRequest(pilot.getId(), shortEndDate, cutoff, "CANDIDATE_V1");
        RiskAnalysisResponse rsk02Resp = analysisService.executeRsk02Analysis(rsk02Req);
        assertEquals("INSUFFICIENT_DATA", rsk02Resp.result().calculationStatus());
        assertTrue(rsk02Resp.limitations().insufficientEvidence());

        Rsk03CalculationRequest rsk03Req = new Rsk03CalculationRequest(pilot.getId(), shortEndDate, cutoff, "CANDIDATE_V1");
        RiskAnalysisResponse rsk03Resp = analysisService.executeRsk03Analysis(rsk03Req);
        assertEquals("INSUFFICIENT_DATA", rsk03Resp.result().calculationStatus());
        assertTrue(rsk03Resp.limitations().insufficientEvidence());

        Rsk04CalculationRequest rsk04Req = new Rsk04CalculationRequest(pilot.getId(), shortEndDate, cutoff, "CANDIDATE_V1");
        RiskAnalysisResponse rsk04Resp = analysisService.executeRsk04Analysis(rsk04Req);
        assertEquals("INSUFFICIENT_DATA", rsk04Resp.result().calculationStatus());
        assertTrue(rsk04Resp.limitations().insufficientEvidence());

        Rsk05CalculationRequest rsk05Req = new Rsk05CalculationRequest(pilot.getId(), shortEndDate, cutoff, "CANDIDATE_V1");
        RiskAnalysisResponse rsk05Resp = analysisService.executeRsk05Analysis(rsk05Req);
        assertEquals("INSUFFICIENT_DATA", rsk05Resp.result().calculationStatus());
        assertTrue(rsk05Resp.limitations().insufficientEvidence());
    }

    @Test
    @DisplayName("RSK-02 to RSK-05 Strict PIT: requestedEndDate after knowledgeCutoff is rejected")
    void testRsk02To05FutureKnowledgeCutoffViolation() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        LocalDate endDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime pastCutoff = OffsetDateTime.of(2023, 12, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        assertThrows(IllegalArgumentException.class, () ->
            analysisService.executeRsk02Analysis(new Rsk02CalculationRequest(pilot.getId(), endDate, pastCutoff, "CANDIDATE_V1")));

        assertThrows(IllegalArgumentException.class, () ->
            analysisService.executeRsk03Analysis(new Rsk03CalculationRequest(pilot.getId(), endDate, pastCutoff, "CANDIDATE_V1")));

        assertThrows(IllegalArgumentException.class, () ->
            analysisService.executeRsk04Analysis(new Rsk04CalculationRequest(pilot.getId(), endDate, pastCutoff, "CANDIDATE_V1")));

        assertThrows(IllegalArgumentException.class, () ->
            analysisService.executeRsk05Analysis(new Rsk05CalculationRequest(pilot.getId(), endDate, pastCutoff, "CANDIDATE_V1")));
    }
}
