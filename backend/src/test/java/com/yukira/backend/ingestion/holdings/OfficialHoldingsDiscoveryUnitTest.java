package com.yukira.backend.ingestion.holdings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OfficialHoldingsDiscoveryUnitTest {

    private AuthoritativeHoldingsSourceFetcher mockFetcher;
    private PpfasHoldingsSourceDiscoverer ppfasDiscoverer;
    private HdfcHoldingsSourceDiscoverer hdfcDiscoverer;

    private MonthlyHoldingsSourceRegistry.RegisteredFundConfig hdfcFlexiConfig;
    private MonthlyHoldingsSourceRegistry.RegisteredFundConfig ppfasFlexiConfig;
    private MonthlyHoldingsSourceRegistry.RegisteredFundConfig ppfasTaxConfig;

    @BeforeEach
    void setUp() {
        mockFetcher = Mockito.mock(AuthoritativeHoldingsSourceFetcher.class);
        ppfasDiscoverer = new PpfasHoldingsSourceDiscoverer(mockFetcher, PpfasHoldingsSourceDiscoverer.DEFAULT_INDEX_URL);
        hdfcDiscoverer = new HdfcHoldingsSourceDiscoverer(mockFetcher, HdfcHoldingsSourceDiscoverer.DEFAULT_INDEX_URL);

        hdfcFlexiConfig = new MonthlyHoldingsSourceRegistry.RegisteredFundConfig(
            "HDFC_MF", "HDFC Mutual Fund", "HDFC_FLEXI", "HDFC Flexi Cap Fund", "118955", "INF179K01UT0",
            MonthlyHoldingsSourceRegistry.FundSourceState.VERIFIED_OFFICIAL, null
        );

        ppfasFlexiConfig = new MonthlyHoldingsSourceRegistry.RegisteredFundConfig(
            "PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_FLEXI", "Parag Parikh Flexi Cap Fund", "122639", "INF879O01027",
            MonthlyHoldingsSourceRegistry.FundSourceState.VERIFIED_OFFICIAL, null
        );

        ppfasTaxConfig = new MonthlyHoldingsSourceRegistry.RegisteredFundConfig(
            "PPFAS_MF", "PPFAS Mutual Fund", "PPFAS_TAX", "Parag Parikh ELSS Tax Saver Fund", "147481", "INF879O01100",
            MonthlyHoldingsSourceRegistry.FundSourceState.VERIFIED_OFFICIAL, null
        );
    }

    private String loadFixture(String fixtureName) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/" + fixtureName)) {
            assertNotNull(is, "Fixture file not found: " + fixtureName);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("TC-DISC-01: Correct discovery of HDFC monthly reports from authentic HTML fixture")
    void testHdfcDiscoveryFromHtmlFixture() throws Exception {
        String html = loadFixture("hdfc_portfolio_disclosure_page.html");
        List<DiscoveredHoldingsCandidate> candidates = hdfcDiscoverer.parseDisclosuresFromHtml(
            html, HdfcHoldingsSourceDiscoverer.HDFC_BASE_URL, hdfcFlexiConfig,
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30)
        );

        assertNotNull(candidates);
        assertEquals(2, candidates.size(), "Should discover exactly 2 HDFC Flexi Cap disclosures (Sep 2026 & Aug 2026)");

        DiscoveredHoldingsCandidate sepCandidate = candidates.stream()
            .filter(c -> LocalDate.of(2026, 9, 30).equals(c.reportingDate()))
            .findFirst()
            .orElseThrow();
        assertEquals(DiscoveryStatus.DISCOVERED, sepCandidate.status());
        assertTrue(sepCandidate.sourceUrl().contains("30%20September%202026.xlsx"));
        assertTrue(sepCandidate.sourceUrl().startsWith("https://files.hdfcfund.com"));
        assertNotNull(sepCandidate.descriptor());
        assertEquals("118955", sepCandidate.descriptor().amfiCode());

        DiscoveredHoldingsCandidate augCandidate = candidates.stream()
            .filter(c -> LocalDate.of(2026, 8, 31).equals(c.reportingDate()))
            .findFirst()
            .orElseThrow();
        assertEquals(DiscoveryStatus.DISCOVERED, augCandidate.status());
        assertTrue(augCandidate.sourceUrl().contains("VersionId=uiLQ6_fSvY8B0150fLIx39g50v9.1guo"), "VersionId query param must be preserved");
    }

    @Test
    @DisplayName("TC-DISC-02: Correct discovery of PPFAS Flexi Cap and ELSS reports independently")
    void testPpfasIndependentSchemeDiscovery() throws Exception {
        String html = loadFixture("ppfas_portfolio_disclosure_page.html");

        // 1. Flexi Cap Discovery
        List<DiscoveredHoldingsCandidate> flexiCandidates = ppfasDiscoverer.parseDisclosuresFromHtml(
            html, PpfasHoldingsSourceDiscoverer.PPFAS_BASE_URL, ppfasFlexiConfig,
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)
        );
        assertEquals(3, flexiCandidates.size(), "Should discover 3 PPFAS Flexi Cap disclosures");
        for (DiscoveredHoldingsCandidate c : flexiCandidates) {
            assertEquals(DiscoveryStatus.DISCOVERED, c.status());
            assertTrue(c.sourceUrl().contains("PPFCF_"), "Must be PPFCF report");
            assertFalse(c.sourceUrl().contains("PPTSF_"), "Must not contain PPTSF");
            assertEquals("122639", c.descriptor().amfiCode());
            assertNotNull(c.publicationTime(), "Must have parsed publication date from query param");
        }

        // 2. ELSS Tax Saver Discovery
        List<DiscoveredHoldingsCandidate> taxCandidates = ppfasDiscoverer.parseDisclosuresFromHtml(
            html, PpfasHoldingsSourceDiscoverer.PPFAS_BASE_URL, ppfasTaxConfig,
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)
        );
        assertEquals(3, taxCandidates.size(), "Should discover 3 PPFAS Tax Saver disclosures");
        for (DiscoveredHoldingsCandidate c : taxCandidates) {
            assertEquals(DiscoveryStatus.DISCOVERED, c.status());
            assertTrue(c.sourceUrl().contains("PPTSF_"), "Must be PPTSF report");
            assertFalse(c.sourceUrl().contains("PPFCF_"), "Must not contain PPFCF");
            assertEquals("147481", c.descriptor().amfiCode());
        }
    }

    @Test
    @DisplayName("TC-DISC-03: Relative URLs resolve against official AMC base domain")
    void testRelativeUrlResolution() throws Exception {
        String html = loadFixture("ppfas_portfolio_disclosure_page.html");
        List<DiscoveredHoldingsCandidate> candidates = ppfasDiscoverer.parseDisclosuresFromHtml(
            html, "https://amc.ppfas.com", ppfasFlexiConfig,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        );
        assertEquals(1, candidates.size());
        assertTrue(candidates.get(0).sourceUrl().startsWith("https://amc.ppfas.com/downloads/portfolio-disclosure/"));
    }

    @Test
    @DisplayName("TC-DISC-04: Query timestamp is parsed as authentic publication availability time")
    void testQueryTimestampExtraction() throws Exception {
        String html = loadFixture("ppfas_portfolio_disclosure_page.html");
        List<DiscoveredHoldingsCandidate> candidates = ppfasDiscoverer.parseDisclosuresFromHtml(
            html, "https://amc.ppfas.com", ppfasFlexiConfig,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        );
        assertEquals(1, candidates.size());
        DiscoveredHoldingsCandidate sep = candidates.get(0);
        // ?07102026 -> 2026-10-07
        assertEquals(LocalDate.of(2026, 10, 7), sep.publicationTime().toLocalDate());
    }

    @Test
    @DisplayName("TC-DISC-05: Empty/No reports published yet returns NOT_YET_PUBLISHED")
    void testNoReportPublishedYet() throws Exception {
        String html = loadFixture("empty_disclosure_page.html");
        List<DiscoveredHoldingsCandidate> candidates = hdfcDiscoverer.parseDisclosuresFromHtml(
            html, HdfcHoldingsSourceDiscoverer.HDFC_BASE_URL, hdfcFlexiConfig,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        );
        assertEquals(1, candidates.size());
        assertEquals(DiscoveryStatus.NOT_YET_PUBLISHED, candidates.get(0).status());
    }

    @Test
    @DisplayName("TC-DISC-06: Ambiguous conflicting candidates fail closed with AMBIGUOUS_SOURCE")
    void testAmbiguousCandidatesFailClosed() throws Exception {
        String html = loadFixture("hdfc_ambiguous_disclosure_page.html");
        List<DiscoveredHoldingsCandidate> candidates = hdfcDiscoverer.parseDisclosuresFromHtml(
            html, HdfcHoldingsSourceDiscoverer.HDFC_BASE_URL, hdfcFlexiConfig,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        );
        assertEquals(1, candidates.size());
        assertEquals(DiscoveryStatus.AMBIGUOUS_SOURCE, candidates.get(0).status());
        assertTrue(candidates.get(0).statusMessage().contains("Ambiguous candidates"));
    }

    @Test
    @DisplayName("TC-DISC-07: HTTP 403 Forbidden returns SOURCE_UNAVAILABLE without retry")
    void testHttp403ForbiddenFailsFast() {
        when(mockFetcher.fetchHtml(anyString()))
            .thenReturn(AuthoritativeHoldingsSourceFetcher.HtmlFetchResult.failure(403, "HTTP 403 Forbidden"));

        List<DiscoveredHoldingsCandidate> result = hdfcDiscoverer.discoverDisclosures(
            hdfcFlexiConfig, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        );

        assertEquals(1, result.size());
        assertEquals(DiscoveryStatus.SOURCE_UNAVAILABLE, result.get(0).status());
        assertTrue(result.get(0).statusMessage().contains("HTTP 403"));
        verify(mockFetcher, times(1)).fetchHtml(anyString());
    }

    @Test
    @DisplayName("TC-DISC-08: HTTP 404 Not Found returns SOURCE_UNAVAILABLE without retry")
    void testHttp404NotFoundFailsFast() {
        when(mockFetcher.fetchHtml(anyString()))
            .thenReturn(AuthoritativeHoldingsSourceFetcher.HtmlFetchResult.failure(404, "HTTP 404 Not Found"));

        List<DiscoveredHoldingsCandidate> result = ppfasDiscoverer.discoverDisclosures(
            ppfasFlexiConfig, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        );

        assertEquals(1, result.size());
        assertEquals(DiscoveryStatus.SOURCE_UNAVAILABLE, result.get(0).status());
        assertTrue(result.get(0).statusMessage().contains("HTTP 404"));
        verify(mockFetcher, times(1)).fetchHtml(anyString());
    }

    @Test
    @DisplayName("TC-DISC-09: Malformed or unparseable HTML handles gracefully without exceptions")
    void testMalformedHtmlSafeFailure() {
        String brokenHtml = "<<<invalid <<html unclosed <<body";
        List<DiscoveredHoldingsCandidate> candidates = hdfcDiscoverer.parseDisclosuresFromHtml(
            brokenHtml, HdfcHoldingsSourceDiscoverer.HDFC_BASE_URL, hdfcFlexiConfig,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        );
        assertEquals(1, candidates.size());
        assertEquals(DiscoveryStatus.NOT_YET_PUBLISHED, candidates.get(0).status());
    }

    @Test
    @DisplayName("TC-DISC-10: AMC layout change causing missing links fails safely as NOT_YET_PUBLISHED")
    void testLayoutChangeSafeFailure() {
        String changedLayoutHtml = "<div class='modern-grid'><span>Holdings Disclosures moved to external app</span></div>";
        List<DiscoveredHoldingsCandidate> candidates = ppfasDiscoverer.parseDisclosuresFromHtml(
            changedLayoutHtml, PpfasHoldingsSourceDiscoverer.PPFAS_BASE_URL, ppfasFlexiConfig,
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        );
        assertEquals(1, candidates.size());
        assertEquals(DiscoveryStatus.NOT_YET_PUBLISHED, candidates.get(0).status());
    }
}
