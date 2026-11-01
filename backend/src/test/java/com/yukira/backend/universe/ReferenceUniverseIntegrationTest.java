package com.yukira.backend.universe;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.ingestion.amfi.AmfiUniverseIngestionService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReferenceUniverseIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private ReferenceUniverseService referenceUniverseService;

    @Autowired
    private AmfiUniverseIngestionService universeIngestionService;

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @BeforeEach
    void setUp() {
        pilotBootstrapService.ensureCanonicalPilotMaster();
        com.yukira.backend.controller.SchemeController controller =
            new com.yukira.backend.controller.SchemeController(
                schemeRepository,
                schemeOptionRepository,
                universeIngestionService,
                null,
                null,
                null,
                null,
                null,
                referenceUniverseService
            );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Integration: Evaluates seeded pilot and ingested feed against Reference Universe rules")
    void testReferenceUniverseIntegration() throws Exception {
        // Multi-category, multi-plan feed:
        // 1. Flexi Cap Direct Growth (AMFI 118955 - Pilot)
        // 2. Flexi Cap Regular Growth (100123) -> Excluded PLAN_NOT_DIRECT
        // 3. Flexi Cap Direct IDCW (100124) -> Excluded OPTION_NOT_GROWTH
        // 4. Large Cap Direct Growth (120501) -> Included LARGE_CAP
        // 5. Debt Liquid Direct Growth (120801) -> Excluded CATEGORY_OUT_OF_SCOPE
        String feed = """
            Open Ended Schemes ( Equity Scheme - Flexi Cap Fund )
            HDFC Mutual Fund
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Date
            118955;HDFC Flexi Cap Fund - Direct Plan - Growth Option;INF179K01UT0;;1670.6720;15-Jan-2024
            100123;HDFC Flexi Cap Fund - Regular Plan - Growth Option;INF179K01BE2;;1550.2340;15-Jan-2024
            100124;HDFC Flexi Cap Fund - Direct Plan - IDCW Option;INF179K01UU8;;65.4320;15-Jan-2024
            
            Open Ended Schemes ( Equity Scheme - Large Cap Fund )
            State Bank of India Mutual Fund
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Date
            120501;SBI Bluechip Fund - Direct Plan - Growth;INF200K01UT1;;85.4200;15-Jan-2024
            
            Open Ended Schemes ( Debt Scheme - Liquid Fund )
            Kotak Mahindra Mutual Fund
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Date
            120801;Kotak Liquid Fund - Direct Plan - Growth;INF174K01UT9;;4500.25;15-Jan-2024
            """;

        universeIngestionService.ingestUniversePayload(feed.getBytes(StandardCharsets.UTF_8), "test://reference-feed");

        // 1. Verify Pilot Option Membership
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        UniverseEvaluationResult pilotResult = referenceUniverseService.evaluateOption(pilot);
        assertTrue(pilotResult.included(), "Canonical pilot HDFC Flexi Cap must be included in reference universe");
        assertEquals(EquityUniverseCategory.FLEXI_CAP, pilotResult.category());

        // 2. Test GET /api/v1/schemes/reference-universe endpoint
        mockMvc.perform(get("/api/v1/schemes/reference-universe")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ruleVersion", is("YUKIRA_V1_M1_5CAT_DIRECT_GROWTH")))
            .andExpect(jsonPath("$.countingUnit", is("SCHEME_OPTION")))
            .andExpect(jsonPath("$.isProvisional").isBoolean())
            .andExpect(jsonPath("$.includedUniverseCount", greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.includedCountsByCategory.FLEXI_CAP", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.includedCountsByCategory.LARGE_CAP", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.excludedCountsByReason.PLAN_NOT_DIRECT", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.excludedCountsByReason.OPTION_NOT_GROWTH", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.excludedCountsByReason.CATEGORY_OUT_OF_SCOPE", greaterThanOrEqualTo(1)));

        // 3. Test Filtered Discovery: GET /api/v1/schemes/options?referenceUniverseOnly=true
        mockMvc.perform(get("/api/v1/schemes/options")
                .param("referenceUniverseOnly", "true")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
            .andExpect(jsonPath("$[*].inReferenceUniverse", everyItem(is(true))))
            .andExpect(jsonPath("$[*].optionType", everyItem(is("GROWTH"))));

        // 4. Test Unfiltered Discovery: Out-of-universe schemes preserved with explicit exclusion reasons
        mockMvc.perform(get("/api/v1/schemes/options")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))))
            // Verify at least one item has inReferenceUniverse=false and universeExclusionReason set
            .andExpect(jsonPath("$[?(@.inReferenceUniverse == false)]", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$[?(@.universeExclusionReason != null)]", hasSize(greaterThanOrEqualTo(1))));
    }
}
