package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.Amc;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AmfiUniverseIngestionServiceTest {

    @Mock
    private AmcRepository amcRepository;

    @Mock
    private SchemeRepository schemeRepository;

    @Mock
    private SchemePlanRepository schemePlanRepository;

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private ValidationIssueRepository validationIssueRepository;

    @Mock
    private SourceArtifactRepository sourceArtifactRepository;

    @Mock
    private DataSourceRepository dataSourceRepository;

    @Mock
    private AmfiSourceClient amfiSourceClient;

    private AmfiUniverseIngestionService service;

    @BeforeEach
    void setUp() {
        service = new AmfiUniverseIngestionService(
            amcRepository,
            schemeRepository,
            schemePlanRepository,
            schemeOptionRepository,
            validationIssueRepository,
            sourceArtifactRepository,
            dataSourceRepository,
            amfiSourceClient
        );
    }

    @Test
    @DisplayName("Decompose scheme names: Direct vs Regular, Growth vs IDCW vs Bonus")
    void testDecomposeScheme() {
        // Direct Growth
        var d1 = service.decomposeScheme("HDFC Flexi Cap Fund - Direct Plan - Growth Option");
        assertEquals("HDFC Flexi Cap Fund", d1.baseSchemeName());
        assertEquals("DIRECT", d1.planType());
        assertEquals("GROWTH", d1.optionType());

        // Regular IDCW
        var d2 = service.decomposeScheme("Kotak Liquid Fund - Regular Plan - IDCW Option");
        assertEquals("Kotak Liquid Fund", d2.baseSchemeName());
        assertEquals("REGULAR", d2.planType());
        assertEquals("IDCW", d2.optionType());

        // Direct Bonus
        var d3 = service.decomposeScheme("Axis Bluechip Fund - Direct Plan - Bonus");
        assertEquals("Axis Bluechip Fund", d3.baseSchemeName());
        assertEquals("DIRECT", d3.planType());
        assertEquals("BONUS", d3.optionType());

        // IDCW Reinvestment
        var d4 = service.decomposeScheme("SBI Small Cap Fund - Direct Plan - IDCW Reinvestment");
        assertEquals("SBI Small Cap Fund", d4.baseSchemeName());
        assertEquals("DIRECT", d4.planType());
        assertEquals("IDCW_REINVESTMENT", d4.optionType());

        // Fallback on blank
        var d5 = service.decomposeScheme("   ");
        assertEquals("Unclassified Scheme", d5.baseSchemeName());
        assertEquals("REGULAR", d5.planType());
        assertEquals("GROWTH", d5.optionType());
    }

    @Test
    @DisplayName("Reject empty or insufficient payload bytes (< 50 bytes)")
    void testRejectEmptyPayload() {
        assertThrows(IllegalArgumentException.class, () ->
            service.ingestUniversePayload(null, "test://empty")
        );

        assertThrows(IllegalArgumentException.class, () ->
            service.ingestUniversePayload(new byte[0], "test://empty")
        );

        assertThrows(IllegalArgumentException.class, () ->
            service.ingestUniversePayload("Too short".getBytes(StandardCharsets.UTF_8), "test://short")
        );
    }

    @Test
    @DisplayName("Reject HTML error pages without creating source artifacts or partial records")
    void testRejectHtmlErrorPayload() {
        String htmlError = "<!DOCTYPE html><html><head><title>Error</title></head><body>No Data Found</body></html>";
        byte[] bytes = htmlError.getBytes(StandardCharsets.UTF_8);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.ingestUniversePayload(bytes, "test://html-error")
        );
        assertTrue(ex.getMessage().contains("HTML error page"));
        verifyNoInteractions(sourceArtifactRepository);
    }

    @Test
    @DisplayName("SHA-256 calculation matches exact raw byte digest")
    void testSha256MatchesExactRawBytes() throws Exception {
        byte[] sample = "Scheme Code;Scheme Name;ISIN;Date\n118955;HDFC Flexi;INF179K01UT0;15-Jan-2024".getBytes(StandardCharsets.UTF_8);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(sample);
        StringBuilder expectedHex = new StringBuilder();
        for (byte b : digest) {
            expectedHex.append(String.format("%02x", b));
        }

        // When invoking with sample payload, repository searches by exact hash
        when(sourceArtifactRepository.findBySha256Hash(expectedHex.toString()))
            .thenReturn(Optional.empty());
        when(dataSourceRepository.findByCode("AMFI_PORTAL"))
            .thenReturn(Optional.of(new com.yukira.backend.domain.entity.DataSource("AMFI_PORTAL", "AMFI", "AMFI")));

        SourceArtifact mockArtifact = new SourceArtifact();
        mockArtifact.setId(100L);
        mockArtifact.setSha256Hash(expectedHex.toString());
        mockArtifact.setPayloadBlob(sample);
        when(sourceArtifactRepository.save(any(SourceArtifact.class))).thenReturn(mockArtifact);

        when(amcRepository.findAll()).thenReturn(Collections.emptyList());
        when(schemeRepository.findAll()).thenReturn(Collections.emptyList());
        when(schemePlanRepository.findAll()).thenReturn(Collections.emptyList());
        when(schemeOptionRepository.findAll()).thenReturn(Collections.emptyList());

        UniverseIngestionSummary summary = service.ingestUniversePayload(sample, "test://sha256-check");
        assertEquals(expectedHex.toString(), summary.sha256Hash());
    }

    @Test
    @DisplayName("Cross-AMC scheme collision prevention: Identical scheme names under distinct AMCs disambiguated")
    void testCrossAmcCollisionPrevention() {
        // Feed with two AMCs having the exact same base scheme name "Liquid Fund"
        String payload = """
            Open Ended Schemes ( Debt Scheme - Liquid Fund )
            Alpha Mutual Fund
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Date
            100001;Alpha Liquid Fund - Direct Plan - Growth;INF101K01UT1;;100.0;15-Jan-2024
            
            Beta Mutual Fund
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Date
            200001;Beta Liquid Fund - Direct Plan - Growth;INF202K01UT2;;200.0;15-Jan-2024
            """;
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);

        SourceArtifact mockArtifact = new SourceArtifact();
        mockArtifact.setId(1L);
        mockArtifact.setSha256Hash("dummy-hash");
        mockArtifact.setPayloadBlob(bytes);

        when(sourceArtifactRepository.findBySha256Hash(anyString())).thenReturn(Optional.of(mockArtifact));
        when(amcRepository.findAll()).thenReturn(Collections.emptyList());
        when(schemeRepository.findAll()).thenReturn(Collections.emptyList());
        when(schemePlanRepository.findAll()).thenReturn(Collections.emptyList());
        when(schemeOptionRepository.findAll()).thenReturn(Collections.emptyList());

        Amc alphaAmc = new Amc("Alpha Mutual Fund", "ALPHA_MF");
        alphaAmc.setId(1L);
        Amc betaAmc = new Amc("Beta Mutual Fund", "BETA_MF");
        betaAmc.setId(2L);

        when(amcRepository.findByCode("ALPHA_MF")).thenReturn(Optional.of(alphaAmc));
        when(amcRepository.findByCode("BETA_MF")).thenReturn(Optional.of(betaAmc));

        when(schemeRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(schemeRepository.save(any(Scheme.class))).thenAnswer(inv -> inv.getArgument(0));
        when(schemePlanRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(schemePlanRepository.save(any(com.yukira.backend.domain.entity.SchemePlan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(schemeOptionRepository.findByAmfiCode(anyString())).thenReturn(Optional.empty());
        when(schemeOptionRepository.save(any(com.yukira.backend.domain.entity.SchemeOption.class))).thenAnswer(inv -> inv.getArgument(0));

        UniverseIngestionSummary summary = service.ingestUniversePayload(bytes, "test://cross-amc");

        assertEquals(2, summary.schemesRegistered(), "Both distinct schemes should be registered");
        assertEquals(2, summary.optionsRegistered(), "Both options should be registered");
    }
}
