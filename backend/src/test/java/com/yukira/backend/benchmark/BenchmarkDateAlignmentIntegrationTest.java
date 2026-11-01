package com.yukira.backend.benchmark;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.controller.BenchmarkIngestionController;
import com.yukira.backend.controller.SchemeController;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SchemeRepository;
import com.yukira.backend.service.ingestion.BenchmarkDataFreshnessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("TSK-M1-04: Benchmark Date Alignment and Multi-Asset Synchronization Integration Test")
class BenchmarkDateAlignmentIntegrationTest {

    private MockMvc schemeMockMvc;
    private MockMvc benchmarkMockMvc;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Autowired
    private BenchmarkDateAlignmentService benchmarkDateAlignmentService;

    @Autowired
    private NiftyBenchmarkIngestionService niftyBenchmarkIngestionService;

    @Autowired
    private BenchmarkDataFreshnessService freshnessService;

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @BeforeEach
    void setUp() {
        pilotBootstrapService.ensureCanonicalPilotMaster();

        SchemeController schemeController = new SchemeController(
            schemeRepository,
            schemeOptionRepository,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            benchmarkDateAlignmentService
        );
        schemeMockMvc = MockMvcBuilders.standaloneSetup(schemeController).build();

        BenchmarkIngestionController benchmarkController = new BenchmarkIngestionController(
            niftyBenchmarkIngestionService,
            freshnessService,
            benchmarkRepository,
            benchmarkObservationRepository
        );
        benchmarkMockMvc = MockMvcBuilders.standaloneSetup(benchmarkController).build();
    }

    @Test
    @DisplayName("Integration: Evaluates benchmark categories endpoint listing all 5 canonical TRI benchmarks")
    void testBenchmarkCategoriesEndpoint() throws Exception {
        benchmarkMockMvc.perform(get("/api/v1/ingestion/benchmark/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ruleVersion", is("YUKIRA_V1_M1_CATEGORY_BENCHMARK_MAPPING_V1")))
            .andExpect(jsonPath("$.categories", hasSize(5)))
            .andExpect(jsonPath("$.categories[?(@.category == 'FLEXI_CAP')].benchmarkCode", hasItem("NIFTY_500_TRI")))
            .andExpect(jsonPath("$.categories[?(@.category == 'LARGE_CAP')].benchmarkCode", hasItem("NIFTY_100_TRI")))
            .andExpect(jsonPath("$.categories[?(@.category == 'LARGE_AND_MID_CAP')].benchmarkCode", hasItem("NIFTY_LARGEMIDCAP_250_TRI")))
            .andExpect(jsonPath("$.categories[?(@.category == 'MID_CAP')].benchmarkCode", hasItem("NIFTY_MIDCAP_150_TRI")))
            .andExpect(jsonPath("$.categories[?(@.category == 'SMALL_CAP')].benchmarkCode", hasItem("NIFTY_SMALLCAP_250_TRI")))
            .andExpect(jsonPath("$.categories[*].returnVariant", everyItem(is("TRI"))))
            .andExpect(jsonPath("$.categories[*].provider", everyItem(is("NSE Indices Limited"))));
    }

    @Test
    @DisplayName("Integration: Evaluates canonical pilot fund 118955 benchmark date alignment and metric readiness")
    void testPilotBenchmarkAlignmentIntegration() throws Exception {
        // 1. Ingest official 3-year NIFTY 500 TRI fixture
        InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/nifty500_tri_3y.json");
        assertNotNull(stream, "Fixture nifty500_tri_3y.json must be present in classpath");
        byte[] payload = stream.readAllBytes();

        String sourceUri = "https://www.niftyindices.com/BackPage/getTotalReturnIndexString?name=Nifty%20500&startDate=15-Jan-2021&endDate=15-Jan-2024";
        var summary = niftyBenchmarkIngestionService.ingestPayload("NIFTY_500_TRI", sourceUri, payload);
        assertTrue(summary.insertedCount() > 0 || summary.skippedCount() > 0, "Benchmark observations must be ingested or already present");

        // 2. Query benchmark alignment endpoint for canonical pilot option 118955
        schemeMockMvc.perform(get("/api/v1/schemes/options/118955/benchmark-alignment")
                .param("asOfDate", "2024-01-15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amfiCode", is("118955")))
            .andExpect(jsonPath("$.isin", is("INF179K01UT0")))
            .andExpect(jsonPath("$.benchmarkCode", is("NIFTY_500_TRI")))
            .andExpect(jsonPath("$.returnVariant", is("TRI")))
            .andExpect(jsonPath("$.isTri", is(true)))
            .andExpect(jsonPath("$.mappingStatus", is("CATEGORY_DEFAULT_MAPPING")))
            .andExpect(jsonPath("$.methodologyStatus", is("CANDIDATE_METHODOLOGY_MAPPING")))
            .andExpect(jsonPath("$.equityUniverseCategory", is("FLEXI_CAP")))
            .andExpect(jsonPath("$.metricReadiness['RET-02'].isReady", is(true)))
            .andExpect(jsonPath("$.metricReadiness['RET-02'].readinessState", is("READY")))
            .andExpect(jsonPath("$.metricReadiness['RET-02'].methodologyStatus", is("VALIDATED_OPERATIONAL_SLICE")))
            .andExpect(jsonPath("$.governanceDisclaimer", containsString("Phase 2H governance rule")));
    }
}
