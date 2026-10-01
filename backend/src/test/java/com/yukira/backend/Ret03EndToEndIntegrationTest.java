package com.yukira.backend;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.Ret03AnalysisResponse;
import com.yukira.backend.dto.analysis.Ret03CalculationRequest;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.AnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@SuppressWarnings("null")
public class Ret03EndToEndIntegrationTest {

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
    @DisplayName("RET-03 Step 5 & 6: Real end-to-end execution against canonical pilot with persistence and provenance verification")
    void testCanonicalPilotRet03EndToEnd() {
        // 1. Identify canonical pilot: HDFC Flexi Cap Fund Direct Growth (AMFI 118955, ISIN INF179K01UT0)
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        assertEquals("INF179K01UT0", pilot.getIsin());
        assertEquals("GROWTH", pilot.getOptionType());

        // 2. Parameters per Step 5:
        // End date: 2024-01-15
        // Knowledge cutoff: 2024-01-31T23:59:59+05:30
        LocalDate endDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        long runCountBefore = calculationRunRepository.count();

        // 3. Execute RET-03 via AnalysisService
        Ret03CalculationRequest request = new Ret03CalculationRequest(
            pilot.getId(),
            endDate,
            knowledgeCutoff,
            "CANDIDATE_V1"
        );

        Ret03AnalysisResponse response = analysisService.executeRet03Analysis(request);

        // Verify no duplicate calculation runs created
        long runCountAfter = calculationRunRepository.count();
        assertEquals(runCountBefore + 1, runCountAfter, "Exactly one calculation run must be created");

        // 4. Verify API Response Contract
        assertNotNull(response);
        assertEquals("RET-03", response.result().metricCode());
        assertEquals("CALCULATED", response.result().calculationStatus());
        assertEquals("PERCENTAGE", response.result().units());
        assertEquals("+27.2496%", response.result().formattedValue());

        // Mathematical verification approx 0.272495778659...
        assertNotNull(response.result().numericValue());
        assertEquals(0.272495778659, response.result().numericValue().doubleValue(), 0.0001);

        // Period verification
        assertEquals(LocalDate.of(2021, 1, 15), response.period().selectedStartDate());
        assertEquals(LocalDate.of(2024, 1, 15), response.period().selectedEndDate());
        assertFalse(response.period().startSubstituted());
        assertFalse(response.period().endSubstituted());
        assertEquals(0, response.period().startLookbackDaysUsed());
        assertEquals(0, response.period().endLookbackDaysUsed());

        // Methodology verification
        assertEquals("RET_03_3Y_CAGR", response.methodology().methodologyCode());
        assertEquals("CANDIDATE_V1", response.methodology().methodologyVersion());
        assertEquals("CANDIDATE", response.methodology().approvalStatus());
        assertTrue(response.methodology().isCandidate());

        // Benchmark verification
        assertFalse(response.benchmark().benchmarkRequired());
        assertNull(response.benchmark().benchmarkId());

        // Provenance & Lineage verification
        assertEquals("COMPLETED", response.provenance().runStatus());
        assertEquals(2, response.provenance().inputObservations().size());

        // Boundary observations
        var startRef = response.provenance().inputObservations().get(0);
        var endRef = response.provenance().inputObservations().get(1);
        assertEquals(LocalDate.of(2021, 1, 15), startRef.effectiveDate());
        assertEquals(0, new BigDecimal("811.21700000").compareTo(startRef.navValue()));
        assertEquals(LocalDate.of(2024, 1, 15), endRef.effectiveDate());
        assertEquals(0, new BigDecimal("1670.67200000").compareTo(endRef.navValue()));

        // 5. Verify Persistence & Provenance in Database
        Long runId = response.provenance().calculationRunId();
        assertNotNull(runId);

        CalculationRun persistedRun = calculationRunRepository.findById(runId)
            .orElseThrow(() -> new IllegalStateException("Persisted calculation run not found: " + runId));

        assertEquals("COMPLETED", persistedRun.getRunStatus());
        assertEquals(pilot.getId(), persistedRun.getSchemeOption().getId());
        assertEquals(endDate, persistedRun.getAsOfDate());
        // Knowledge cutoff persisted exactly
        assertNotNull(persistedRun.getKnowledgeCutoffTime());
        assertEquals(knowledgeCutoff.toInstant(), persistedRun.getKnowledgeCutoffTime().toInstant(),
            "Persisted knowledge cutoff must exactly match supplied knowledgeCutoffTime");
        assertNotNull(persistedRun.getInputSnapshotSha256());
        assertNull(persistedRun.getBenchmark(), "Benchmark must be null (no synthetic benchmark)");

        // Methodology version correctly referenced
        assertNotNull(persistedRun.getMethodologyVersion());
        assertEquals("RET_03_3Y_CAGR", persistedRun.getMethodologyVersion().getMethodologyCode());
        assertEquals("CANDIDATE_V1", persistedRun.getMethodologyVersion().getVersionTag());

        // MetricResult verification
        List<MetricResult> results = metricResultRepository.findByCalculationRunId(runId);
        assertEquals(1, results.size());
        MetricResult res = results.get(0);
        assertEquals("RET-03", res.getMetricCode());
        assertEquals("3Y", res.getPeriodType());
        assertEquals("PERCENTAGE", res.getUnits());
        assertEquals("CALCULATED", res.getCalculationStatus());
        // Verify persisted result equals API result
        assertEquals(response.result().numericValue(), res.getNumericValue());

        // CalculationRunInputObservation verification
        List<CalculationRunInputObservation> inputObs = inputObservationRepository.findByCalculationRunId(runId);
        assertEquals(2, inputObs.size());
        assertEquals(LocalDate.of(2021, 1, 15), inputObs.get(0).getEffectiveDate());
        assertEquals(LocalDate.of(2024, 1, 15), inputObs.get(1).getEffectiveDate());
        assertEquals(startRef.observationId(), inputObs.get(0).getNavObservation().getId());
        assertEquals(endRef.observationId(), inputObs.get(1).getNavObservation().getId());

        // 6. Generic and specific API retrieval verification
        Optional<Ret03AnalysisResponse> bySpecific = analysisService.getRet03AnalysisByRunId(runId);
        assertTrue(bySpecific.isPresent());
        assertEquals(response.result().numericValue(), bySpecific.get().result().numericValue());

        Optional<Object> byGeneric = analysisService.getAnalysisByRunId(runId);
        assertTrue(byGeneric.isPresent());
        assertTrue(byGeneric.get() instanceof Ret03AnalysisResponse);
        Ret03AnalysisResponse genericCast = (Ret03AnalysisResponse) byGeneric.get();
        assertEquals("RET-03", genericCast.result().metricCode());
        assertEquals(response.result().numericValue(), genericCast.result().numericValue());
    }

    @Test
    @DisplayName("RET-03 PIT: Observations published after knowledge cutoff are strictly excluded from calculation")
    void testKnowledgeCutoffStrictlyExcludesObservationsPublishedLater() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        LocalDate endDate = LocalDate.of(2024, 1, 15);
        // Knowledge cutoff is set prior to 2024-01-15 (e.g. 2024-01-10)
        // This simulates requesting a calculation where the required boundary observation was not yet known
        OffsetDateTime prematureCutoff = OffsetDateTime.of(2024, 1, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        Ret03CalculationRequest request = new Ret03CalculationRequest(
            pilot.getId(),
            endDate,
            prematureCutoff,
            "CANDIDATE_V1"
        );

        Ret03AnalysisResponse response = analysisService.executeRet03Analysis(request);

        assertNotNull(response);
        assertEquals("RET-03", response.result().metricCode());
        assertEquals("INSUFFICIENT_DATA", response.result().calculationStatus());
        assertNull(response.result().numericValue(), "No numeric value may be generated when PIT observations are excluded");
        assertEquals("FAILED", response.provenance().runStatus());
        assertTrue(response.limitations().insufficientEvidence());

        // Verify persisted calculation run records the exact premature knowledge cutoff
        Long runId = response.provenance().calculationRunId();
        CalculationRun persistedRun = calculationRunRepository.findById(runId).orElseThrow();
        assertEquals(prematureCutoff.toInstant(), persistedRun.getKnowledgeCutoffTime().toInstant());
        assertEquals("FAILED", persistedRun.getRunStatus());
    }
}
