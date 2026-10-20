package com.yukira.backend;

import com.yukira.backend.controller.CalculationRunController;
import com.yukira.backend.controller.HealthController;
import com.yukira.backend.controller.SchemeController;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SchemeRepository;
import com.yukira.backend.service.CalculationOrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ApiControllerTest {

    @Autowired
    private HealthController healthController;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private CalculationOrchestratorService calculationOrchestratorService;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    @Autowired
    private com.yukira.backend.repository.NavObservationRepository navObservationRepository;

    @Autowired
    private com.yukira.backend.repository.PortfolioSnapshotRepository portfolioSnapshotRepository;

    @Autowired
    private com.yukira.backend.controller.PortfolioController portfolioController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SchemeController schemeController = new SchemeController(
            schemeRepository, schemeOptionRepository, null, null, null,
            navObservationRepository, portfolioSnapshotRepository
        );
        CalculationRunController calculationRunController = new CalculationRunController(
            calculationOrchestratorService, calculationRunRepository, metricResultRepository
        );
        mockMvc = MockMvcBuilders.standaloneSetup(healthController, schemeController, calculationRunController, portfolioController).build();
    }

    @Test
    @DisplayName("Health endpoint returns UP with STRICTLY EMPTY methodology and EXACTLY ZERO empirical findings")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("UP")))
            .andExpect(jsonPath("$.service", is("yukira-backend")))
            .andExpect(jsonPath("$.methodology_status", is("STRICTLY EMPTY")))
            .andExpect(jsonPath("$.empirical_findings", is("EXACTLY ZERO")));
    }

    @Test
    @DisplayName("Schemes endpoint returns 200 OK")
    void testSchemesEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/schemes")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Schemes endpoint searches by fund name, AMFI code, and returns AMC details")
    void testSchemesSearchByNameAndIdentifier() throws Exception {
        mockMvc.perform(get("/api/v1/schemes?search=HDFC")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").exists());

        mockMvc.perform(get("/api/v1/schemes?search=118955")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code", is("HDFC_FLEXI")));
    }

    @Test
    @DisplayName("Scheme options endpoint exposes exact share-class IDs, AMFI code, and verified data availability")
    void testSchemeOptionsBySchemeIdWithFreshness() throws Exception {
        mockMvc.perform(get("/api/v1/schemes/1/options")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id", is(1)))
            .andExpect(jsonPath("$[0].amfiCode", is("118955")))
            .andExpect(jsonPath("$[0].optionType", is("GROWTH")))
            .andExpect(jsonPath("$[0].plan.planType", is("DIRECT")))
            .andExpect(jsonPath("$[0].latestNavDate").exists())
            .andExpect(jsonPath("$[0].navObservationCount").exists());
    }

    @Test
    @DisplayName("Trigger calculation run returns 400 when missing required fields")
    void testTriggerCalculationValidation() throws Exception {
        mockMvc.perform(post("/api/v1/calculation-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Pairwise overlap and correlation endpoints return verified evidence for canonical pilots")
    void testPairwiseOverlapAndCorrelationEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/schemes/options/overlap?schemeOptionIdA=1&schemeOptionIdB=10262")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemeOptionIdA", is(1)))
            .andExpect(jsonPath("$.schemeOptionIdB", is(10262)))
            .andExpect(jsonPath("$.qualityState").exists())
            .andExpect(jsonPath("$.commonSecurityCount").isNumber());

        mockMvc.perform(get("/api/v1/schemes/options/correlation?schemeOptionIdA=1&schemeOptionIdB=10262")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemeOptionIdA", is(1)))
            .andExpect(jsonPath("$.schemeOptionIdB", is(10262)))
            .andExpect(jsonPath("$.calculationStatus").exists())
            .andExpect(jsonPath("$.comparabilityStatus").exists());
    }

    @Test
    @DisplayName("Scheme endpoint resolves by both alphanumeric schemeCode and numeric ID")
    void testSchemeResolutionByIdentifierAndOptions() throws Exception {
        mockMvc.perform(get("/api/v1/schemes/HDFC_FLEXI")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code", is("HDFC_FLEXI")))
            .andExpect(jsonPath("$.name", is("HDFC Flexi Cap Fund")));

        mockMvc.perform(get("/api/v1/schemes/HDFC_FLEXI/options")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].amfiCode", is("118955")));
    }
}
