package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.Amc;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.ValidationIssue;
import com.yukira.backend.repository.*;
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
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AmfiUniverseIngestionIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private AmfiUniverseIngestionService universeIngestionService;

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private AmcRepository amcRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private SchemePlanRepository schemePlanRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private ValidationIssueRepository validationIssueRepository;

    @BeforeEach
    void setUp() {
        // Ensure canonical pilot exists before test
        pilotBootstrapService.ensureCanonicalPilotMaster();
        com.yukira.backend.controller.SchemeController controller =
            new com.yukira.backend.controller.SchemeController(schemeRepository, schemeOptionRepository, universeIngestionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Idempotent Universe Ingestion: Preserves pilot, registers sample universe, handles duplicates and diagnostics")
    void testUniverseIngestionAndDiscovery() throws Exception {
        // Representative multi-AMC sample feed containing:
        // 1. Category headers (Equity Flexi Cap, Equity Large Cap, Debt Liquid)
        // 2. AMC banners (HDFC, SBI, ICICI Prudential, Nippon India)
        // 3. Existing canonical pilot (118955 - HDFC Flexi Cap Direct Growth)
        // 4. New schemes under existing AMC and new AMCs
        // 5. Direct and Regular plans
        // 6. Growth and IDCW options
        // 7. Duplicate AMFI code and duplicate ISIN to test diagnostics
        // 8. Malformed rows (short row, invalid code)
        String sampleFeed = """
            Open Ended Schemes ( Equity Scheme - Flexi Cap Fund )
            HDFC Mutual Fund
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Repurchase Price;Sale Price;Date
            118955;HDFC Flexi Cap Fund - Direct Plan - Growth Option;INF179K01UT0;;1670.6720;;;15-Jan-2024
            100123;HDFC Flexi Cap Fund - Regular Plan - Growth Option;INF179K01BE2;;1550.2340;;;15-Jan-2024
            100124;HDFC Flexi Cap Fund - Direct Plan - IDCW Option;INF179K01UU8;;65.4320;;;15-Jan-2024
            
            Open Ended Schemes ( Equity Scheme - Large Cap Fund )
            State Bank of India Mutual Fund
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Repurchase Price;Sale Price;Date
            120501;SBI Bluechip Fund - Direct Plan - Growth;INF200K01UT1;;85.4200;;;15-Jan-2024
            120502;SBI Bluechip Fund - Regular Plan - Growth;INF200K01UT2;;78.3100;;;15-Jan-2024
            
            ICICI Prudential Mutual Fund
            120601;ICICI Prudential Bluechip Fund - Direct Plan - Growth Option;INF109K01UT3;;92.1500;;;15-Jan-2024
            120602;ICICI Prudential Bluechip Fund - Regular Plan - IDCW Option;INF109K01UT4;;32.4500;;;15-Jan-2024
            
            Open Ended Schemes ( Equity Scheme - Small Cap Fund )
            Nippon India Mutual Fund
            120701;Nippon India Small Cap Fund - Direct Plan - Growth Plan;INF204K01UT5;;145.8900;;;15-Jan-2024
            
            # Diagnostic Edge Cases:
            # 1. Duplicate AMFI code with different name
            120501;SBI Bluechip Conflicting Name - Direct Plan - Growth;INF200K01XX9;;85.4200;;;15-Jan-2024
            # 2. Duplicate ISIN across different scheme code
            120899;Fake Duplicate ISIN Fund - Direct Plan - Growth;INF200K01UT1;;50.0000;;;15-Jan-2024
            # 3. Malformed line with insufficient tokens
            999999;MalformedRow
            # 4. Non-numeric scheme code
            INVALID_CODE;Some Fund - Direct - Growth;INF999K01UT9;;10.00;;;15-Jan-2024
            """;

        byte[] payloadBytes = sampleFeed.getBytes(StandardCharsets.UTF_8);

        // 1. Initial Ingestion
        UniverseIngestionSummary summary = universeIngestionService.ingestUniversePayload(payloadBytes, "test://sample-universe");

        assertNotNull(summary);
        assertTrue(schemeRepository.count() >= 4, "Should have at least 4 distinct schemes in database");
        assertTrue(schemeOptionRepository.count() >= 6, "Should have at least 6 scheme options in database");
        assertTrue(summary.duplicatesSkipped() >= 1, "Should detect and skip duplicate AMFI code");
        assertTrue(summary.validationIssuesCreated() >= 3, "Should record validation issues for malformed/duplicate rows");

        // 2. Verify Canonical Pilot Preservation:
        // Pilot AMFI 118955 must match existing HDFC_FLEXI scheme and HDFC_FLEXI_DIR plan
        SchemeOption pilotOption = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        SchemePlan pilotPlan = schemePlanRepository.findById(pilotOption.getPlan().getId()).orElseThrow();
        Scheme pilotScheme = schemeRepository.findById(pilotPlan.getScheme().getId()).orElseThrow();
        assertEquals("HDFC_FLEXI", pilotScheme.getCode());
        assertEquals("HDFC_FLEXI_DIR", pilotPlan.getCode());
        assertEquals("INF179K01UT0", pilotOption.getIsin());

        // 3. Verify Nullable Inception Date (Zero fabricated dates!):
        Scheme sbiScheme = schemeRepository.findByCode("SBI_BLUECHIP_FUND").orElseThrow();
        assertNull(sbiScheme.getInceptionDate(), "Inception date must be null when absent from feed; zero fabricated dates");
        assertEquals("Equity Scheme", sbiScheme.getCategory());
        assertEquals("Large Cap Fund", sbiScheme.getSubcategory());

        // 4. Verify Idempotent Second Run (0 new schemes/options added)
        UniverseIngestionSummary secondRun = universeIngestionService.ingestUniversePayload(payloadBytes, "test://sample-universe");
        assertEquals(0, secondRun.schemesRegistered(), "Second ingestion run must register 0 new schemes (idempotency)");
        assertEquals(0, secondRun.optionsRegistered(), "Second ingestion run must register 0 new options (idempotency)");

        // 5. Test Discovery API: GET /api/v1/schemes
        mockMvc.perform(get("/api/v1/schemes")
                .param("category", "Equity Scheme")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))))
            .andExpect(jsonPath("$[*].category", everyItem(containsString("Equity Scheme"))));

        // Filter schemes by search
        mockMvc.perform(get("/api/v1/schemes")
                .param("search", "Bluechip")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2)))) // SBI Bluechip and ICICI Bluechip
            .andExpect(jsonPath("$[*].name", hasItems("SBI Bluechip Fund", "ICICI Prudential Bluechip Fund")));

        // 6. Test Discovery API: GET /api/v1/schemes/options
        // Filter options by Direct plan and Growth option
        mockMvc.perform(get("/api/v1/schemes/options")
                .param("planType", "DIRECT")
                .param("optionType", "GROWTH")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))))
            .andExpect(jsonPath("$[*].optionType", everyItem(is("GROWTH"))));

        // Filter options by exact ISIN
        mockMvc.perform(get("/api/v1/schemes/options")
                .param("isin", "INF179K01UT0")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].amfiCode", is("118955")));

        // 7. Test Ingest Universe API endpoint: POST /api/v1/schemes/ingest-universe
        String miniPayload = """
            Open Ended Schemes ( Debt Scheme - Liquid Fund )
            Kotak Mahindra Mutual Fund
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Date
            120801;Kotak Liquid Fund - Direct Plan - Growth;INF174K01UT9;;4500.25;15-Jan-2024
            """;

        mockMvc.perform(post("/api/v1/schemes/ingest-universe")
                .contentType(MediaType.TEXT_PLAIN)
                .content(miniPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemesRegistered", greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.optionsRegistered", greaterThanOrEqualTo(0)))
            .andExpect(jsonPath("$.totalRowsParsed", is(4)));
    }
}
