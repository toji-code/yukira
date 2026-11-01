package com.yukira.backend.controller;

import com.yukira.backend.ingestion.amfi.AmfiUniverseIngestionService;
import com.yukira.backend.ingestion.amfi.HistoricalNavIngestionService;
import com.yukira.backend.universe.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class AmfiUniverseAdminControllerTest {

    @Mock
    private AmfiUniverseIngestionService universeIngestionService;

    @Mock
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Mock
    private ReferenceUniverseService referenceUniverseService;

    @Mock
    private UniverseBatchOrchestratorService universeBatchOrchestratorService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AmfiUniverseAdminController controller = new AmfiUniverseAdminController(
            universeIngestionService,
            historicalNavIngestionService,
            referenceUniverseService,
            universeBatchOrchestratorService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private UniverseBatchRunReportDto createSampleReportDto(boolean dryRun) {
        return new UniverseBatchRunReportDto(
            "run_sample_123",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            1200L,
            dryRun,
            "admin-operator",
            "COMPLETED",
            Map.of("batchSize", 5),
            5,
            3,
            2,
            0,
            0,
            Collections.emptyList(),
            List.of(
                new SchemeBatchExecutionDetailDto(
                    1L, "118955", "INF179K01UT0", "HDFC Flexi Cap Fund", EquityUniverseCategory.FLEXI_CAP,
                    "NIFTY_500_TRI", "CANDIDATE_METHODOLOGY_MAPPING", 750, 750, "ELIGIBLE",
                    "SKIPPED_ALREADY_PRESENT", 0, "CALCULATED", 501L,
                    List.of("RET-02", "RET-03", "RSK-01", "RSK-03", "MKT-01", "MKT-02", "RAT-04", "REL-01"),
                    null
                )
            ),
            List.of("Governance note 1")
        );
    }

    @Test
    @DisplayName("GET /api/v1/admin/ingestion/universe/batch-run/preview returns 200 with dryRun=true")
    void testPreviewBatchRun() throws Exception {
        when(universeBatchOrchestratorService.previewBatchRun(any(UniverseBatchRunRequest.class)))
            .thenReturn(createSampleReportDto(true));

        mockMvc.perform(get("/api/v1/admin/ingestion/universe/batch-run/preview")
                .param("batchSize", "5")
                .param("category", "FLEXI_CAP"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value("run_sample_123"))
            .andExpect(jsonPath("$.dryRun").value(true))
            .andExpect(jsonPath("$.totalSelected").value(5))
            .andExpect(jsonPath("$.schemeOutcomes[0].schemeOptionId").value(1))
            .andExpect(jsonPath("$.schemeOutcomes[0].calculationOutcome").value("CALCULATED"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/ingestion/universe/batch-run executes batch run and returns 200")
    void testExecuteBatchRun() throws Exception {
        when(universeBatchOrchestratorService.executeBatchRun(any(UniverseBatchRunRequest.class)))
            .thenReturn(createSampleReportDto(false));

        mockMvc.perform(post("/api/v1/admin/ingestion/universe/batch-run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "batchSize": 5,
                        "category": "FLEXI_CAP",
                        "dryRun": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value("run_sample_123"))
            .andExpect(jsonPath("$.dryRun").value(false))
            .andExpect(jsonPath("$.totalCalculated").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/admin/ingestion/universe/coverage-status returns 200 with breakdown")
    void testGetCoverageStatus() throws Exception {
        UniverseCoverageStatusDto coverage = new UniverseCoverageStatusDto(
            "YUKIRA_V1_M1_UNIVERSE_BATCH_ORCHESTRATOR_V1",
            14412,
            193,
            126,
            67,
            0,
            Map.of("FLEXI_CAP", new CategoryCoverageDto(
                EquityUniverseCategory.FLEXI_CAP, "Flexi Cap", "NIFTY_500_TRI", "CANDIDATE_METHODOLOGY_MAPPING",
                47, 25, 22, 0, 1416, "SUFFICIENT_3Y_COVERAGE"
            )),
            Collections.emptyList(),
            OffsetDateTime.now(),
            List.of("Governance note")
        );

        when(universeBatchOrchestratorService.getUniverseCoverageStatus()).thenReturn(coverage);

        mockMvc.perform(get("/api/v1/admin/ingestion/universe/coverage-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalReferenceUniverseOptions").value(193))
            .andExpect(jsonPath("$.totalSufficientOptions").value(126))
            .andExpect(jsonPath("$.categoryBreakdown.FLEXI_CAP.sufficientOptions").value(25))
            .andExpect(jsonPath("$.categoryBreakdown.FLEXI_CAP.candidateBenchmarkCode").value("NIFTY_500_TRI"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/ingestion/universe/calculate-eligible returns 200 with calculated outcomes")
    void testCalculateEligible() throws Exception {
        when(universeBatchOrchestratorService.calculateEligibleCohort(any(), any(Integer.class), any(), any()))
            .thenReturn(createSampleReportDto(false));

        mockMvc.perform(post("/api/v1/admin/ingestion/universe/calculate-eligible")
                .param("category", "FLEXI_CAP")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value("run_sample_123"))
            .andExpect(jsonPath("$.totalCalculated").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/admin/ingestion/universe/batch-run/async returns 202 ACCEPTED with job status")
    void testTriggerAsyncBatchRun() throws Exception {
        UniverseBatchJobStatusDto jobStatus = new UniverseBatchJobStatusDto(
            "JOB-12345", "PENDING", OffsetDateTime.now(), null, "admin-async",
            EquityUniverseCategory.FLEXI_CAP, java.time.LocalDate.of(2026, 10, 1),
            5, 25, 5, 0, 0, 0, 0, 5, null, null, 0.0, null, null
        );

        when(universeBatchOrchestratorService.triggerAsyncBatchRun(any(UniverseBatchRunRequest.class)))
            .thenReturn(jobStatus);

        mockMvc.perform(post("/api/v1/admin/ingestion/universe/batch-run/async")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "batchSize": 5,
                        "category": "FLEXI_CAP",
                        "asOfDate": "2026-10-01"
                    }
                """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.jobId").value("JOB-12345"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.requestedBatchSize").value(5))
            .andExpect(jsonPath("$.totalEligibleCohort").value(25));
    }

    @Test
    @DisplayName("POST /api/v1/admin/ingestion/universe/batch-run/async returns 409 CONFLICT when job is already running")
    void testTriggerAsyncBatchRunConflict() throws Exception {
        UniverseBatchJobStatusDto runningJob = new UniverseBatchJobStatusDto(
            "JOB-ACTIVE-1", "RUNNING", OffsetDateTime.now(), null, "admin",
            null, java.time.LocalDate.of(2026, 10, 1),
            5, 126, 5, 2, 2, 0, 0, 3, 10497L, "Bandhan Large Cap", 40.0, null, null
        );

        when(universeBatchOrchestratorService.triggerAsyncBatchRun(any(UniverseBatchRunRequest.class)))
            .thenThrow(new IllegalStateException("A batch calculation job is already actively running: JOB-ACTIVE-1"));
        when(universeBatchOrchestratorService.getJobStatus()).thenReturn(runningJob);

        mockMvc.perform(post("/api/v1/admin/ingestion/universe/batch-run/async")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchSize\": 5}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("CONCURRENT_JOB_RUNNING"))
            .andExpect(jsonPath("$.activeJob.jobId").value("JOB-ACTIVE-1"))
            .andExpect(jsonPath("$.activeJob.status").value("RUNNING"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/ingestion/universe/batch-run/status returns 200 with job status")
    void testGetBatchRunStatus() throws Exception {
        UniverseBatchJobStatusDto jobStatus = new UniverseBatchJobStatusDto(
            "JOB-9999", "COMPLETED", OffsetDateTime.now().minusMinutes(2), OffsetDateTime.now(), "admin",
            null, java.time.LocalDate.of(2026, 10, 1),
            5, 126, 5, 5, 4, 1, 0, 0, null, null, 100.0, null, null
        );

        when(universeBatchOrchestratorService.getJobStatus()).thenReturn(jobStatus);

        mockMvc.perform(get("/api/v1/admin/ingestion/universe/batch-run/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobId").value("JOB-9999"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.successfulCount").value(4))
            .andExpect(jsonPath("$.skippedCount").value(1));
    }
}
