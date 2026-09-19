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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SchemeController schemeController = new SchemeController(schemeRepository, schemeOptionRepository);
        CalculationRunController calculationRunController = new CalculationRunController(
            calculationOrchestratorService, calculationRunRepository, metricResultRepository
        );
        mockMvc = MockMvcBuilders.standaloneSetup(healthController, schemeController, calculationRunController).build();
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
    @DisplayName("Trigger calculation run returns 400 when missing required fields")
    void testTriggerCalculationValidation() throws Exception {
        mockMvc.perform(post("/api/v1/calculation-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }
}
