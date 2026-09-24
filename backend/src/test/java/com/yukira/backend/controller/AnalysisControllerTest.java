package com.yukira.backend.controller;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.Ret02CalculationRequest;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.AnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AnalysisControllerTest {

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private SchemePlanRepository schemePlanRepository;

    @Autowired
    private AmcRepository amcRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Autowired
    private DataSourceRepository dataSourceRepository;

    @Autowired
    private SourceArtifactRepository sourceArtifactRepository;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    private MockMvc mockMvc;

    private SchemeOption schemeOption;
    private SourceArtifact sourceArtifact;

    @BeforeEach
    void setUp() {
        AnalysisController controller = new AnalysisController(analysisService, schemeOptionRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        Amc amc = amcRepository.findByCode("HDFC_MF")
            .orElseGet(() -> amcRepository.save(new Amc("HDFC Mutual Fund", "HDFC_MF")));
        Scheme scheme = schemeRepository.findByCode("HDFC_FLEXI")
            .orElseGet(() -> schemeRepository.save(new Scheme(amc, "HDFC Flexi Cap Fund", "HDFC_FLEXI", LocalDate.of(1995, 1, 1))));
        SchemePlan plan = schemePlanRepository.findByCode("HDFC_FLEXI_DIR")
            .orElseGet(() -> schemePlanRepository.save(new SchemePlan(scheme, "DIRECT", "HDFC_FLEXI_DIR")));
        schemeOption = schemeOptionRepository.findByAmfiCode("TEST_CTRL_01")
            .orElseGet(() -> schemeOptionRepository.save(new SchemeOption(plan, "GROWTH", "TEST_CTRL_01", "INF_TEST_CTRL_01")));

        DataSource ds = dataSourceRepository.findByCode("AMFI_NAV_ALL")
            .orElseGet(() -> dataSourceRepository.save(new DataSource("AMFI_NAV_ALL", "AMFI NAV", "https://www.amfiindia.com/net-asset-value/nav-history")));
        sourceArtifact = sourceArtifactRepository.save(new SourceArtifact(
            ds, OffsetDateTime.now(), "TEXT_NAV_BATCH", "a".repeat(64), 1024L
        ));
    }

    @Test
    @DisplayName("RET-02 API: Successful execution exposes complete auditable vertical slice")
    void testSuccessfulRet02Execution() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 15);

        NavObservation startObs = new NavObservation(schemeOption, start, new BigDecimal("100.00000000"), 1, cutoff);
        startObs.setSourceArtifact(sourceArtifact);
        navObservationRepository.save(startObs);

        NavObservation endObs = new NavObservation(schemeOption, end, new BigDecimal("110.00000000"), 1, cutoff);
        endObs.setSourceArtifact(sourceArtifact);
        navObservationRepository.save(endObs);

        String jsonPayload = String.format("""
            {
                "schemeOptionId": %d,
                "startDate": "2024-01-01",
                "endDate": "2024-01-15",
                "knowledgeCutoffTime": "%s",
                "methodologyTag": "CANDIDATE_V1"
            }
            """, schemeOption.getId(), cutoff.toString());

        mockMvc.perform(post("/api/v1/analysis/ret02")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isOk())
            // 1. Identity
            .andExpect(jsonPath("$.identity.schemeOptionId", is(schemeOption.getId().intValue())))
            .andExpect(jsonPath("$.identity.amfiCode", is("TEST_CTRL_01")))
            .andExpect(jsonPath("$.identity.optionType", is("GROWTH")))
            // 2. Result
            .andExpect(jsonPath("$.result.metricCode", is("RET-02")))
            .andExpect(jsonPath("$.result.calculationStatus", is("CALCULATED")))
            .andExpect(jsonPath("$.result.formattedValue", is("+10.0000%")))
            .andExpect(jsonPath("$.result.units", is("PERCENTAGE")))
            // 3. Period
            .andExpect(jsonPath("$.period.requestedStartDate", is("2024-01-01")))
            .andExpect(jsonPath("$.period.requestedEndDate", is("2024-01-15")))
            .andExpect(jsonPath("$.period.selectedStartDate", is("2024-01-01")))
            .andExpect(jsonPath("$.period.selectedEndDate", is("2024-01-15")))
            // 4. PIT
            .andExpect(jsonPath("$.pit.pitFilteringApplied", is(true)))
            .andExpect(jsonPath("$.pit.cutoffConventionApplied", is("CONVENTION_EOD_HISTORICAL_CUTOFF")))
            // 5. Methodology
            .andExpect(jsonPath("$.methodology.methodologyCode", is("RET_02_SIMPLE_RETURN")))
            .andExpect(jsonPath("$.methodology.approvalStatus", is("CANDIDATE")))
            .andExpect(jsonPath("$.methodology.isCandidate", is(true)))
            // 6. Benchmark
            .andExpect(jsonPath("$.benchmark.benchmarkRequired", is(false)))
            .andExpect(jsonPath("$.benchmark.benchmarkId").doesNotExist())
            // 7. Limitations & Temporal Semantics
            .andExpect(jsonPath("$.limitations.factualAvailabilityTimestampUnavailable", is(true)))
            .andExpect(jsonPath("$.limitations.analyticalCutoffConvention", is("CONVENTION_EOD_HISTORICAL_CUTOFF")))
            .andExpect(jsonPath("$.limitations.sourceAvailabilitySemantic", is("HISTORICAL_BACKFILL")))
            .andExpect(jsonPath("$.pit.sourceAvailabilitySemantic", is("HISTORICAL_BACKFILL")))
            // 8. Quality (6 Approved Dimensions)
            .andExpect(jsonPath("$.quality.dimensions", hasSize(6)))
            .andExpect(jsonPath("$.quality.dimensions[0].dimension", is("Quality")))
            .andExpect(jsonPath("$.quality.dimensions[0].state", is("VALID")))
            .andExpect(jsonPath("$.quality.dimensions[1].dimension", is("Verification")))
            .andExpect(jsonPath("$.quality.dimensions[1].state", is("VERIFIED")))
            .andExpect(jsonPath("$.quality.dimensions[2].dimension", is("Revision")))
            .andExpect(jsonPath("$.quality.dimensions[2].state", is("ORIGINAL")))
            .andExpect(jsonPath("$.quality.dimensions[3].dimension", is("Freshness")))
            .andExpect(jsonPath("$.quality.dimensions[3].state", is("CURRENT")))
            .andExpect(jsonPath("$.quality.dimensions[4].dimension", is("Presence")))
            .andExpect(jsonPath("$.quality.dimensions[4].state", is("AVAILABLE")))
            .andExpect(jsonPath("$.quality.dimensions[5].dimension", is("Integrity")))
            .andExpect(jsonPath("$.quality.dimensions[5].state", is("NONE")))
            // 9. Provenance & Lineage
            .andExpect(jsonPath("$.provenance.runStatus", is("COMPLETED")))
            .andExpect(jsonPath("$.provenance.inputObservations", hasSize(2)))
            .andExpect(jsonPath("$.provenance.inputObservations[0].sourceArtifactSha256", is("a".repeat(64))))
            .andExpect(jsonPath("$.provenance.inputObservations[0].temporalStatus", is("CURRENT")))
            .andExpect(jsonPath("$.provenance.inputObservations[0].sourceAvailabilitySemantic", is("HISTORICAL_BACKFILL")));
    }

    @Test
    @DisplayName("RET-02 API: Unknown scheme option returns 404 NOT FOUND")
    void testUnknownSchemeOption() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        String jsonPayload = String.format("""
            {
                "schemeOptionId": 999999,
                "startDate": "2024-01-01",
                "endDate": "2024-01-15",
                "knowledgeCutoffTime": "%s",
                "methodologyTag": "CANDIDATE_V1"
            }
            """, cutoff.toString());

        mockMvc.perform(post("/api/v1/analysis/ret02")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error", is("SCHEME_OPTION_NOT_FOUND")));
    }

    @Test
    @DisplayName("RET-02 API: Invalid period sequence (start >= end) returns 400 BAD REQUEST")
    void testInvalidPeriodSequence() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        String jsonPayload = String.format("""
            {
                "schemeOptionId": %d,
                "startDate": "2024-01-15",
                "endDate": "2024-01-01",
                "knowledgeCutoffTime": "%s",
                "methodologyTag": "CANDIDATE_V1"
            }
            """, schemeOption.getId(), cutoff.toString());

        mockMvc.perform(post("/api/v1/analysis/ret02")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("INVALID_PERIOD_SEQUENCE")));
    }

    @Test
    @DisplayName("RET-02 API: Insufficient NAV evidence returns 422 UNPROCESSABLE ENTITY with audit details")
    void testInsufficientEvidenceReturns422() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        String jsonPayload = String.format("""
            {
                "schemeOptionId": %d,
                "startDate": "2023-01-01",
                "endDate": "2023-01-15",
                "knowledgeCutoffTime": "%s",
                "methodologyTag": "CANDIDATE_V1"
            }
            """, schemeOption.getId(), cutoff.toString());

        mockMvc.perform(post("/api/v1/analysis/ret02")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.result.calculationStatus", is("INSUFFICIENT_DATA")))
            .andExpect(jsonPath("$.result.numericValue").doesNotExist())
            .andExpect(jsonPath("$.limitations.insufficientEvidence", is(true)))
            .andExpect(jsonPath("$.methodology.isCandidate", is(true)))
            .andExpect(jsonPath("$.benchmark.benchmarkRequired", is(false)));
    }

    @Test
    @DisplayName("RET-02 API: GET /api/v1/analysis/ret02/{runId} retrieves complete audit record")
    void testGetAnalysisByRunId() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 15);

        NavObservation startObs = new NavObservation(schemeOption, start, new BigDecimal("100.00000000"), 1, cutoff);
        navObservationRepository.save(startObs);
        NavObservation endObs = new NavObservation(schemeOption, end, new BigDecimal("105.00000000"), 1, cutoff);
        navObservationRepository.save(endObs);

        Ret02CalculationRequest req = new Ret02CalculationRequest(
            schemeOption.getId(), start, end, cutoff, "CANDIDATE_V1"
        );

        var executedResponse = analysisService.executeRet02Analysis(req);
        Long runId = executedResponse.provenance().calculationRunId();

        mockMvc.perform(get("/api/v1/analysis/ret02/" + runId)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provenance.calculationRunId", is(runId.intValue())))
            .andExpect(jsonPath("$.result.metricCode", is("RET-02")))
            .andExpect(jsonPath("$.result.calculationStatus", is("CALCULATED")))
            .andExpect(jsonPath("$.benchmark.benchmarkRequired", is(false)))
            .andExpect(jsonPath("$.methodology.approvalStatus", is("CANDIDATE")));
    }

    @Test
    @DisplayName("RET-02 API: Observation strictly after knowledge cutoff is excluded (PIT invariant)")
    void testObservationAfterCutoffIsExcluded() throws Exception {
        OffsetDateTime earlyCutoff = OffsetDateTime.of(2024, 1, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 15);

        NavObservation startObs = new NavObservation(schemeOption, start, new BigDecimal("100.00000000"), 1, earlyCutoff);
        startObs.setSourceArtifact(sourceArtifact);
        navObservationRepository.save(startObs);

        // End obs is available only AFTER cutoff (on Jan 20)
        OffsetDateTime lateAvailability = OffsetDateTime.of(2024, 1, 20, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        NavObservation endObs = new NavObservation(schemeOption, end, new BigDecimal("110.00000000"), 1, lateAvailability);
        endObs.setSourceArtifact(sourceArtifact);
        navObservationRepository.save(endObs);

        String jsonPayload = String.format("""
            {
                "schemeOptionId": %d,
                "startDate": "2024-01-01",
                "endDate": "2024-01-15",
                "knowledgeCutoffTime": "%s",
                "methodologyTag": "CANDIDATE_V1"
            }
            """, schemeOption.getId(), earlyCutoff.toString());

        mockMvc.perform(post("/api/v1/analysis/ret02")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.result.calculationStatus", is("INSUFFICIENT_DATA")))
            .andExpect(jsonPath("$.pit.pitFilteringApplied", is(true)));
    }

    @Test
    @DisplayName("RET-03 API: Successful 3Y CAGR execution dispatches to quant-engine and returns complete audit response")
    void testSuccessfulRet03Execution() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate start = LocalDate.of(2021, 1, 15);
        LocalDate end = LocalDate.of(2024, 1, 15);

        NavObservation startObs = new NavObservation(schemeOption, start, new BigDecimal("811.21700000"), 1, cutoff);
        startObs.setSourceArtifact(sourceArtifact);
        navObservationRepository.save(startObs);

        NavObservation endObs = new NavObservation(schemeOption, end, new BigDecimal("1670.67200000"), 1, cutoff);
        endObs.setSourceArtifact(sourceArtifact);
        navObservationRepository.save(endObs);

        String jsonPayload = String.format("""
            {
                "schemeOptionId": %d,
                "endDate": "2024-01-15",
                "knowledgeCutoffTime": "%s",
                "methodologyTag": "CANDIDATE_V1"
            }
            """, schemeOption.getId(), cutoff.toString());

        var result = mockMvc.perform(post("/api/v1/analysis/ret03")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.identity.schemeOptionId", is(schemeOption.getId().intValue())))
            .andExpect(jsonPath("$.result.metricCode", is("RET-03")))
            .andExpect(jsonPath("$.result.calculationStatus", is("CALCULATED")))
            .andExpect(jsonPath("$.result.formattedValue", is("+27.2496%")))
            .andExpect(jsonPath("$.result.units", is("PERCENTAGE")))
            .andExpect(jsonPath("$.period.requestedStartDate", is("2021-01-15")))
            .andExpect(jsonPath("$.period.requestedEndDate", is("2024-01-15")))
            .andExpect(jsonPath("$.methodology.methodologyCode", is("RET_03_3Y_CAGR")))
            .andExpect(jsonPath("$.methodology.approvalStatus", is("CANDIDATE")))
            .andExpect(jsonPath("$.methodology.isCandidate", is(true)))
            .andExpect(jsonPath("$.benchmark.benchmarkRequired", is(false)))
            .andExpect(jsonPath("$.provenance.runStatus", is("COMPLETED")))
            .andExpect(jsonPath("$.provenance.inputObservations", hasSize(2)))
            .andReturn();

        // Extract runId and verify generic GET /api/v1/analysis/{runId} router
        String content = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);
        double numVal = root.path("result").path("numericValue").asDouble();
        org.junit.jupiter.api.Assertions.assertEquals(0.272495778659, numVal, 0.0001);
        long runId = root.path("provenance").path("calculationRunId").asLong();

        mockMvc.perform(get("/api/v1/analysis/" + runId)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.metricCode", is("RET-03")))
            .andExpect(jsonPath("$.provenance.calculationRunId", is((int) runId)));

        mockMvc.perform(get("/api/v1/analysis/ret03/" + runId)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.metricCode", is("RET-03")))
            .andExpect(jsonPath("$.provenance.calculationRunId", is((int) runId)));
    }

    @Test
    @DisplayName("RET-03 API: Insufficient history (< 36 months) returns 422 with INSUFFICIENT_DATA")
    void testRet03InsufficientHistory() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate end = LocalDate.of(2024, 1, 15);

        // Only end date observation exists; no start observation within lookback window
        NavObservation endObs = new NavObservation(schemeOption, end, new BigDecimal("1670.67200000"), 1, cutoff);
        endObs.setSourceArtifact(sourceArtifact);
        navObservationRepository.save(endObs);

        String jsonPayload = String.format("""
            {
                "schemeOptionId": %d,
                "endDate": "2024-01-15",
                "knowledgeCutoffTime": "%s",
                "methodologyTag": "CANDIDATE_V1"
            }
            """, schemeOption.getId(), cutoff.toString());

        mockMvc.perform(post("/api/v1/analysis/ret03")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.result.calculationStatus", is("INSUFFICIENT_DATA")))
            .andExpect(jsonPath("$.limitations.insufficientEvidence", is(true)));
    }

    @Test
    @DisplayName("RET-03 API: Missing knowledgeCutoffTime is rejected with 400 BAD REQUEST")
    void testRet03MissingKnowledgeCutoffIsRejected() throws Exception {
        String jsonPayload = String.format("""
            {
                "schemeOptionId": %d,
                "endDate": "2024-01-15"
            }
            """, schemeOption.getId());

        mockMvc.perform(post("/api/v1/analysis/ret03")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("MISSING_PIT_CUTOFF")))
            .andExpect(jsonPath("$.message", containsString("knowledgeCutoffTime is required")));
    }

    @Test
    @DisplayName("RET-03 API: Supplied knowledgeCutoffTime is persisted exactly")
    void testRet03SuppliedKnowledgeCutoffPersistedExactly() throws Exception {
        OffsetDateTime exactCutoff = OffsetDateTime.of(2024, 1, 31, 20, 15, 30, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate start = LocalDate.of(2021, 1, 15);
        LocalDate end = LocalDate.of(2024, 1, 15);

        NavObservation startObs = new NavObservation(schemeOption, start, new BigDecimal("811.21700000"), 1, exactCutoff);
        startObs.setSourceArtifact(sourceArtifact);
        navObservationRepository.save(startObs);

        NavObservation endObs = new NavObservation(schemeOption, end, new BigDecimal("1670.67200000"), 1, exactCutoff);
        endObs.setSourceArtifact(sourceArtifact);
        navObservationRepository.save(endObs);

        String jsonPayload = String.format("""
            {
                "schemeOptionId": %d,
                "endDate": "2024-01-15",
                "knowledgeCutoffTime": "%s",
                "methodologyTag": "CANDIDATE_V1"
            }
            """, schemeOption.getId(), exactCutoff.toString());

        var result = mockMvc.perform(post("/api/v1/analysis/ret03")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pit.knowledgeCutoffTime", is(exactCutoff.toInstant().toString())))
            .andReturn();

        String content = result.getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);
        long runId = root.path("provenance").path("calculationRunId").asLong();

        CalculationRun persistedRun = calculationRunRepository.findById(runId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(
            exactCutoff.toInstant(),
            persistedRun.getKnowledgeCutoffTime().toInstant(),
            "Persisted calculation_run must match supplied knowledge cutoff exactly"
        );
    }

    @Test
    @DisplayName("RET-03 API: Observations unavailable at knowledge cutoff cannot enter calculation")
    void testRet03KnowledgeCutoffExcludesUnavailableObservations() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        // Observation published after cutoff time
        OffsetDateTime latePublication = OffsetDateTime.of(2024, 1, 16, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        LocalDate start = LocalDate.of(2021, 1, 15);
        LocalDate end = LocalDate.of(2024, 1, 15);

        NavObservation startObs = new NavObservation(schemeOption, start, new BigDecimal("811.21700000"), 1, cutoff);
        startObs.setSourceArtifact(sourceArtifact);
        navObservationRepository.save(startObs);

        // End observation was only published/available at latePublication (after knowledge cutoff)
        NavObservation endObs = new NavObservation(schemeOption, end, new BigDecimal("1670.67200000"), 1, latePublication);
        endObs.setSourceArtifact(sourceArtifact);
        navObservationRepository.save(endObs);

        String jsonPayload = String.format("""
            {
                "schemeOptionId": %d,
                "endDate": "2024-01-15",
                "knowledgeCutoffTime": "%s",
                "methodologyTag": "CANDIDATE_V1"
            }
            """, schemeOption.getId(), cutoff.toString());

        mockMvc.perform(post("/api/v1/analysis/ret03")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.result.calculationStatus", is("INSUFFICIENT_DATA")))
            .andExpect(jsonPath("$.limitations.insufficientEvidence", is(true)));
    }

    @Test
    @DisplayName("RSK-01 API: Successful execution exposes complete auditable risk vertical slice")
    void testSuccessfulRsk01Execution() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate start = LocalDate.of(2021, 1, 15);
        LocalDate end = LocalDate.of(2024, 1, 15);

        // Seed 720 daily observations so minObservations >= 700 is satisfied
        LocalDate current = start;
        BigDecimal nav = new BigDecimal("100.00000000");
        int count = 0;
        while (!current.isAfter(end)) {
            NavObservation obs = new NavObservation(schemeOption, current, nav, 1, cutoff);
            obs.setSourceArtifact(sourceArtifact);
            navObservationRepository.save(obs);
            nav = nav.add(new BigDecimal("0.10000000"));
            current = current.plusDays(1);
            count++;
        }

        String jsonPayload = String.format("""
            {
                "schemeOptionId": %d,
                "endDate": "2024-01-15",
                "knowledgeCutoffTime": "%s",
                "methodologyTag": "CANDIDATE_V1"
            }
            """, schemeOption.getId(), cutoff.toString());

        mockMvc.perform(post("/api/v1/analysis/rsk01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.metricCode", is("RSK-01")))
            .andExpect(jsonPath("$.result.metricName", is("3-Year Annualized Volatility")))
            .andExpect(jsonPath("$.result.calculationStatus", is("CALCULATED")))
            .andExpect(jsonPath("$.result.units", is("PERCENTAGE")))
            .andExpect(jsonPath("$.window.observationCount", greaterThanOrEqualTo(700)))
            .andExpect(jsonPath("$.window.minObservationsRequired", is(700)))
            .andExpect(jsonPath("$.methodology.methodologyCode", is("RSK_01_3Y_VOLATILITY")))
            .andExpect(jsonPath("$.methodology.annualizationConvention", is("SQRT_252_CANDIDATE")))
            .andExpect(jsonPath("$.methodology.denominatorConvention", is("N_MINUS_ONE_CANDIDATE")))
            .andExpect(jsonPath("$.benchmark.benchmarkRequired", is(false)))
            .andExpect(jsonPath("$.limitations.candidateAnnualizationApplied", is(true)))
            .andExpect(jsonPath("$.provenance.runStatus", is("COMPLETED")));
    }

    @Test
    @DisplayName("RSK-01 API: Insufficient observations (< 700) returns HTTP 422 with INSUFFICIENT_DATA status")
    void testRsk01InsufficientObservationsReturns422() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Only 5 observations seeded
        for (int i = 0; i < 5; i++) {
            NavObservation obs = new NavObservation(schemeOption, LocalDate.of(2024, 1, 10 + i), new BigDecimal("100.00"), 1, cutoff);
            obs.setSourceArtifact(sourceArtifact);
            navObservationRepository.save(obs);
        }

        String jsonPayload = String.format("""
            {
                "schemeOptionId": %d,
                "endDate": "2024-01-15",
                "knowledgeCutoffTime": "%s",
                "methodologyTag": "CANDIDATE_V1"
            }
            """, schemeOption.getId(), cutoff.toString());

        mockMvc.perform(post("/api/v1/analysis/rsk01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.result.metricCode", is("RSK-01")))
            .andExpect(jsonPath("$.result.calculationStatus", is("INSUFFICIENT_DATA")))
            .andExpect(jsonPath("$.limitations.insufficientEvidence", is(true)));
    }

    @Test
    @DisplayName("RSK-01 API: Missing knowledgeCutoffTime returns HTTP 400 Bad Request")
    void testRsk01MissingKnowledgeCutoffReturns400() throws Exception {
        String jsonPayload = String.format("""
            {
                "schemeOptionId": %d,
                "endDate": "2024-01-15"
            }
            """, schemeOption.getId());

        mockMvc.perform(post("/api/v1/analysis/rsk01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("MISSING_PIT_CUTOFF")));
    }

    @Test
    @DisplayName("RSK-01 API: Non-existent scheme option returns HTTP 404")
    void testRsk01NonExistentSchemeOptionReturns404() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        String jsonPayload = String.format("""
            {
                "schemeOptionId": 999999,
                "endDate": "2024-01-15",
                "knowledgeCutoffTime": "%s"
            }
            """, cutoff.toString());

        mockMvc.perform(post("/api/v1/analysis/rsk01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error", is("SCHEME_OPTION_NOT_FOUND")));
    }

    @Test
    @DisplayName("RSK-02 to RSK-05 API: Endpoints accept valid payloads and execute via quant-engine")
    void testRsk02To05ControllerEndpoints() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate start = LocalDate.of(2021, 1, 15);
        LocalDate end = LocalDate.of(2024, 1, 15);

        // Seed 720 daily observations
        LocalDate current = start;
        BigDecimal nav = new BigDecimal("100.00000000");
        while (!current.isAfter(end)) {
            NavObservation obs = new NavObservation(schemeOption, current, nav, 1, cutoff);
            obs.setSourceArtifact(sourceArtifact);
            navObservationRepository.save(obs);
            nav = nav.add(new BigDecimal("0.10000000"));
            current = current.plusDays(1);
        }

        String jsonPayload = String.format("""
            {
                "schemeOptionId": %d,
                "endDate": "2024-01-15",
                "knowledgeCutoffTime": "%s",
                "methodologyTag": "CANDIDATE_V1"
            }
            """, schemeOption.getId(), cutoff.toString());

        // Test RSK-02
        var res02 = mockMvc.perform(post("/api/v1/analysis/rsk02")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.metricCode", is("RSK-02")))
            .andExpect(jsonPath("$.result.calculationStatus", is("CALCULATED")))
            .andExpect(jsonPath("$.result.units", is("PERCENTAGE")))
            .andReturn();

        long runId02 = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(res02.getResponse().getContentAsString())
            .path("provenance").path("calculationRunId").asLong();

        mockMvc.perform(get("/api/v1/analysis/rsk02/" + runId02))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.metricCode", is("RSK-02")));

        // Test RSK-03
        var res03 = mockMvc.perform(post("/api/v1/analysis/rsk03")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.metricCode", is("RSK-03")))
            .andExpect(jsonPath("$.result.calculationStatus", is("CALCULATED")))
            .andExpect(jsonPath("$.result.units", is("PERCENTAGE")))
            .andReturn();

        long runId03 = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(res03.getResponse().getContentAsString())
            .path("provenance").path("calculationRunId").asLong();

        mockMvc.perform(get("/api/v1/analysis/rsk03/" + runId03))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.metricCode", is("RSK-03")));

        // Test RSK-04
        var res04 = mockMvc.perform(post("/api/v1/analysis/rsk04")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.metricCode", is("RSK-04")))
            .andExpect(jsonPath("$.result.calculationStatus", is("CALCULATED")))
            .andExpect(jsonPath("$.result.units", is("DAYS")))
            .andReturn();

        long runId04 = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(res04.getResponse().getContentAsString())
            .path("provenance").path("calculationRunId").asLong();

        mockMvc.perform(get("/api/v1/analysis/rsk04/" + runId04))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.metricCode", is("RSK-04")));

        // Test RSK-05
        var res05 = mockMvc.perform(post("/api/v1/analysis/rsk05")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.metricCode", is("RSK-05")))
            .andExpect(jsonPath("$.result.calculationStatus", is("CALCULATED")))
            .andExpect(jsonPath("$.result.units", is("POINTS")))
            .andReturn();

        long runId05 = new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(res05.getResponse().getContentAsString())
            .path("provenance").path("calculationRunId").asLong();

        mockMvc.perform(get("/api/v1/analysis/rsk05/" + runId05))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.metricCode", is("RSK-05")));
    }
}
