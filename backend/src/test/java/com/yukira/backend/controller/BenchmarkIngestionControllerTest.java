package com.yukira.backend.controller;

import com.yukira.backend.benchmark.CategoryBenchmarkMapping;
import com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService;
import com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService.BenchmarkIngestionAuditResult;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.service.ingestion.BenchmarkDataFreshnessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BenchmarkIngestionController REST API Unit Tests")
class BenchmarkIngestionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NiftyBenchmarkIngestionService benchmarkIngestionService;

    @Mock
    private BenchmarkDataFreshnessService freshnessService;

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @BeforeEach
    void setUp() {
        BenchmarkIngestionController controller = new BenchmarkIngestionController(
            benchmarkIngestionService,
            freshnessService,
            benchmarkRepository,
            benchmarkObservationRepository
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/ingestion/benchmark/categories returns all 5 canonical category benchmarks with candidate status")
    void testGetCategoryBenchmarksStatus() throws Exception {
        mockMvc.perform(get("/api/v1/ingestion/benchmark/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ruleVersion").value(CategoryBenchmarkMapping.RULE_VERSION))
            .andExpect(jsonPath("$.categories.length()").value(5))
            .andExpect(jsonPath("$.categories[0].isTri").value(true))
            .andExpect(jsonPath("$.categories[0].methodologyStatus").value("CANDIDATE_METHODOLOGY_MAPPING"));
    }

    @Test
    @DisplayName("POST /api/v1/ingestion/benchmark/categories/NIFTY_100_TRI/backfill returns detailed audit report")
    void testBackfillCategoryBenchmarkSuccess() throws Exception {
        BenchmarkIngestionAuditResult mockAudit = new BenchmarkIngestionAuditResult(
            "NIFTY_100_TRI",
            "NIFTY 100 Total Returns Index",
            "NSE Indices Limited",
            "TRI",
            LocalDate.of(2021, 1, 1),
            LocalDate.of(2024, 1, 15),
            LocalDate.of(2021, 1, 1),
            LocalDate.of(2024, 1, 15),
            743, 743, 0,
            743, 0, 0,
            101L,
            "abc123sha256hash",
            "SUCCESS",
            null
        );

        when(benchmarkIngestionService.ingestCategoryBenchmarkWithAudit(eq("NIFTY_100_TRI"), any(), any()))
            .thenReturn(mockAudit);

        mockMvc.perform(post("/api/v1/ingestion/benchmark/categories/NIFTY_100_TRI/backfill")
                .param("startDate", "2021-01-01")
                .param("endDate", "2024-01-15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.benchmarkCode").value("NIFTY_100_TRI"))
            .andExpect(jsonPath("$.returnVariant").value("TRI"))
            .andExpect(jsonPath("$.epistemicStatus").value("BENCHMARK_BACKFILL_SUCCESSFUL"))
            .andExpect(jsonPath("$.auditMetrics.rowsReceived").value(743))
            .andExpect(jsonPath("$.auditMetrics.rowsAccepted").value(743))
            .andExpect(jsonPath("$.auditMetrics.rowsInserted").value(743))
            .andExpect(jsonPath("$.sourceArtifact.sourceArtifactId").value(101))
            .andExpect(jsonPath("$.sourceArtifact.payloadSha256").value("abc123sha256hash"));
    }

    @Test
    @DisplayName("POST /api/v1/ingestion/benchmark/categories/backfill-all ingests all category benchmarks with failure isolation")
    void testBackfillAllCategoryBenchmarks() throws Exception {
        BenchmarkIngestionAuditResult res1 = new BenchmarkIngestionAuditResult(
            "NIFTY_100_TRI", "Nifty 100 TRI", "NSE", "TRI",
            LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 15),
            LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 15),
            743, 743, 0, 743, 0, 0, 101L, "hash1", "SUCCESS", null
        );
        BenchmarkIngestionAuditResult res2 = new BenchmarkIngestionAuditResult(
            "NIFTY_500_TRI", "Nifty 500 TRI", "NSE", "TRI",
            LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 15),
            LocalDate.of(2021, 1, 15), LocalDate.of(2024, 1, 15),
            743, 743, 0, 0, 0, 743, 102L, "hash2", "SKIPPED_ALREADY_SUFFICIENT", "Already sufficient"
        );

        when(benchmarkIngestionService.ingestAllMissingCategoryBenchmarks(any(), any(), anyInt()))
            .thenReturn(List.of(res1, res2));

        mockMvc.perform(post("/api/v1/ingestion/benchmark/categories/backfill-all")
                .param("startDate", "2021-01-01")
                .param("endDate", "2024-01-15")
                .param("minThreshold", "700"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.operation").value("BACKFILL_ALL_CATEGORY_BENCHMARKS"))
            .andExpect(jsonPath("$.benchmarksEvaluated").value(2))
            .andExpect(jsonPath("$.benchmarksSufficientOrSuccess").value(2))
            .andExpect(jsonPath("$.aggregateAuditMetrics.totalReceived").value(1486))
            .andExpect(jsonPath("$.aggregateAuditMetrics.totalAccepted").value(1486))
            .andExpect(jsonPath("$.aggregateAuditMetrics.totalInserted").value(743))
            .andExpect(jsonPath("$.aggregateAuditMetrics.totalSkipped").value(743));
    }
}
