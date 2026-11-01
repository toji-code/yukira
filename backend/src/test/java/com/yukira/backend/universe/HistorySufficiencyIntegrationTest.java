package com.yukira.backend.universe;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.controller.AmfiUniverseAdminController;
import com.yukira.backend.controller.SchemeController;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.ingestion.amfi.AmfiUniverseIngestionService;
import com.yukira.backend.ingestion.amfi.HistoricalNavIngestionService;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SchemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HistorySufficiencyIntegrationTest {

    private MockMvc schemeMockMvc;
    private MockMvc adminMockMvc;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private AmfiUniverseIngestionService universeIngestionService;

    @Autowired
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Autowired
    private ReferenceUniverseService referenceUniverseService;

    @Autowired
    private HistorySufficiencyService historySufficiencyService;

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @BeforeEach
    void setUp() {
        pilotBootstrapService.ensureCanonicalPilotMaster();

        SchemeController schemeController = new SchemeController(
            schemeRepository,
            schemeOptionRepository,
            universeIngestionService,
            historicalNavIngestionService,
            null,
            null,
            null,
            null,
            referenceUniverseService,
            historySufficiencyService
        );
        schemeMockMvc = MockMvcBuilders.standaloneSetup(schemeController).build();

        AmfiUniverseAdminController adminController = new AmfiUniverseAdminController(
            universeIngestionService,
            historicalNavIngestionService,
            referenceUniverseService
        );
        adminMockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
    }

    @Test
    @DisplayName("Integration: Evaluates history sufficiency across reference universe and individual scheme options")
    void testHistorySufficiencyIntegration() throws Exception {
        // Ingest a small multi-category universe feed with Flexi Cap and Large Cap
        String feed = """
            Open Ended Schemes ( Equity Scheme - Flexi Cap Fund )
            HDFC Mutual Fund
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Date
            118955;HDFC Flexi Cap Fund - Direct Plan - Growth Option;INF179K01UT0;;1670.6720;15-Jan-2024
            100123;HDFC Flexi Cap Fund - Regular Plan - Growth Option;INF179K01BE2;;1550.2340;15-Jan-2024
            
            Open Ended Schemes ( Equity Scheme - Large Cap Fund )
            State Bank of India Mutual Fund
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Date
            120501;SBI Bluechip Fund - Direct Plan - Growth;INF200K01UT1;;85.4200;15-Jan-2024
            """;

        universeIngestionService.ingestUniversePayload(feed.getBytes(StandardCharsets.UTF_8), "test://history-sufficiency-feed");

        // 1. Test GET /api/v1/schemes/history-eligibility (Universe-wide report)
        schemeMockMvc.perform(get("/api/v1/schemes/history-eligibility")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ruleVersion", is("YUKIRA_V1_M1_700_DAY_THRESHOLD")))
            .andExpect(jsonPath("$.countingUnit", is("DISTINCT_TRADING_DAY")))
            .andExpect(jsonPath("$.requiredThreshold", is(700)))
            .andExpect(jsonPath("$.totalReferenceOptionsEvaluated", greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.countByStatus.ELIGIBLE").isNumber())
            .andExpect(jsonPath("$.countByStatus.INSUFFICIENT_HISTORY").isNumber())
            .andExpect(jsonPath("$.countByStatus.NO_HISTORY").isNumber())
            .andExpect(jsonPath("$.countByStatus.DATA_QUALITY_LIMITED").isNumber())
            .andExpect(jsonPath("$.metricLimitations", hasSize(greaterThanOrEqualTo(3))))
            .andExpect(jsonPath("$.governanceNotes", hasSize(greaterThanOrEqualTo(3))));

        // 2. Test GET /api/v1/schemes/options/{identifier}/history-eligibility for canonical pilot
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        schemeMockMvc.perform(get("/api/v1/schemes/options/118955/history-eligibility")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemeOptionId", is(pilot.getId().intValue())))
            .andExpect(jsonPath("$.amfiCode", is("118955")))
            .andExpect(jsonPath("$.isin", is("INF179K01UT0")))
            .andExpect(jsonPath("$.inReferenceUniverse", is(true)))
            .andExpect(jsonPath("$.category", is("FLEXI_CAP")))
            .andExpect(jsonPath("$.requiredThreshold", is(700)))
            .andExpect(jsonPath("$.ruleVersion", is("YUKIRA_V1_M1_700_DAY_THRESHOLD")))
            .andExpect(jsonPath("$.status", anyOf(is("ELIGIBLE"), is("INSUFFICIENT_HISTORY"), is("NO_HISTORY"))))
            .andExpect(jsonPath("$.metricLimitations", hasSize(greaterThanOrEqualTo(3))));

        // 3. Test Options Discovery with historyStatus filter: GET /api/v1/schemes/options?historyStatus=NO_HISTORY
        schemeMockMvc.perform(get("/api/v1/schemes/options")
                .param("historyStatus", "NO_HISTORY")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].historySufficiencyStatus", everyItem(is("NO_HISTORY"))));

        // 4. Test Operator Admin Backfill Endpoint in DryRun mode: POST /api/v1/admin/ingestion/universe/backfill?dryRun=true
        adminMockMvc.perform(post("/api/v1/admin/ingestion/universe/backfill")
                .param("batchSize", "5")
                .param("category", "FLEXI_CAP")
                .param("dryRun", "true")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.triggerType", is("DRY_RUN_REFERENCE_UNIVERSE")))
            .andExpect(jsonPath("$.requestedCohortSize", is(5)))
            .andExpect(jsonPath("$.actualCohortSize", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.totalObservationsInserted", is(0)))
            .andExpect(jsonPath("$.sourceRequestsMade", is(0)))
            .andExpect(jsonPath("$.fundResults", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.fundResults[0].status", is("DRY_RUN_PREVIEW")));
    }
}
