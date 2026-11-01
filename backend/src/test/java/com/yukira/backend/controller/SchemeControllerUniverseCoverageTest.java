package com.yukira.backend.controller;

import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.PortfolioSnapshotRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SchemeRepository;
import com.yukira.backend.universe.CategoryCoverageDto;
import com.yukira.backend.universe.EquityUniverseCategory;
import com.yukira.backend.universe.UniverseBatchOrchestratorService;
import com.yukira.backend.universe.UniverseCoverageStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class SchemeControllerUniverseCoverageTest {

    @Mock
    private SchemeRepository schemeRepository;

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private NavObservationRepository navObservationRepository;

    @Mock
    private PortfolioSnapshotRepository portfolioSnapshotRepository;

    @Mock
    private UniverseBatchOrchestratorService universeBatchOrchestratorService;

    @Mock
    private com.yukira.backend.universe.UniverseScreenerService universeScreenerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SchemeController controller = new SchemeController(
            schemeRepository,
            schemeOptionRepository,
            null,
            null,
            null,
            navObservationRepository,
            portfolioSnapshotRepository,
            null,
            null,
            null,
            null,
            universeBatchOrchestratorService,
            universeScreenerService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/schemes/universe-coverage-status returns public 200 with coverage details")
    void testGetUniverseCoverageStatusPublicEndpoint() throws Exception {
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
            List.of("Public due diligence note")
        );

        when(universeBatchOrchestratorService.getUniverseCoverageStatus()).thenReturn(coverage);

        mockMvc.perform(get("/api/v1/schemes/universe-coverage-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalReferenceUniverseOptions").value(193))
            .andExpect(jsonPath("$.totalSufficientOptions").value(126))
            .andExpect(jsonPath("$.categoryBreakdown.FLEXI_CAP.sufficientOptions").value(25))
            .andExpect(jsonPath("$.categoryBreakdown.FLEXI_CAP.candidateBenchmarkCode").value("NIFTY_500_TRI"));
    }

    @Test
    @DisplayName("GET /api/v1/schemes/screener returns public 200 with 193-option comparative screener dataset")
    void testScreenerEndpointReturnsPublic200WithComparativeData() throws Exception {
        com.yukira.backend.universe.UniverseScreenerDto.ScreenerResponse response =
            new com.yukira.backend.universe.UniverseScreenerDto.ScreenerResponse(
                java.time.LocalDate.of(2026, 10, 1),
                193, 126, 67, 0, 5,
                Map.of("FLEXI_CAP", new com.yukira.backend.universe.UniverseScreenerDto.CategorySummary(
                    EquityUniverseCategory.FLEXI_CAP, "Flexi Cap", "NIFTY_500_TRI",
                    47, 25, 22, 1
                )),
                List.of(new com.yukira.backend.universe.UniverseScreenerDto.MetricDefinitionDto(
                    "RET-02", "Simple Period Return", "RETURN", "PERCENTAGE",
                    "3 Years (>= 700 aligned daily observations)", false,
                    "R = (NAV_end - NAV_start) / NAV_start", "APPROVED_M2N"
                )),
                List.of(new com.yukira.backend.universe.UniverseScreenerDto.ScreenerItem(
                    1L, 101L, "HDFC_FLEXI", "HDFC Flexi Cap Fund",
                    10L, "HDFC Mutual Fund", "118955", "INF179K01UT0",
                    EquityUniverseCategory.FLEXI_CAP, "Flexi Cap", 1920L, "ELIGIBLE", true, null,
                    "NIFTY_500_TRI", "Nifty 500 Total Returns Index", "CANDIDATE_METHODOLOGY_MAPPING",
                    1416, "SUFFICIENT_3Y_COVERAGE", 9781L, java.time.LocalDate.of(2026, 10, 1), "SUCCESS",
                    Map.of("RET-02", new com.yukira.backend.universe.UniverseScreenerDto.MetricValueDto(
                        "RET-02", java.math.BigDecimal.valueOf(0.1842), "+18.42%", "3Y", "PERCENTAGE", "CALCULATED"
                    ))
                )),
                List.of("Category benchmark assignments represent candidate methodology conventions under Phase 2H."),
                OffsetDateTime.now()
            );

        when(universeScreenerService.getScreenerData(null, false, null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/schemes/screener"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalReferenceOptions").value(193))
            .andExpect(jsonPath("$.eligibleOptionsCount").value(126))
            .andExpect(jsonPath("$.partialOptionsCount").value(67))
            .andExpect(jsonPath("$.items[0].schemeName").value("HDFC Flexi Cap Fund"))
            .andExpect(jsonPath("$.items[0].metrics.RET-02.formattedValue").value("+18.42%"))
            .andExpect(jsonPath("$.metricDefinitions[0].metricCode").value("RET-02"))
            .andExpect(jsonPath("$.governanceDisclaimers[0]").value(org.hamcrest.Matchers.containsString("Phase 2H")));
    }
}
