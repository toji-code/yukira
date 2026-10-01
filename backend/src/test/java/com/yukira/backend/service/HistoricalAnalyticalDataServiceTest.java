package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.AnalyticalObservationSeriesDto;
import com.yukira.backend.ingestion.amfi.AmfiNavIngestionService;
import com.yukira.backend.ingestion.amfi.AmfiSourceClient;
import com.yukira.backend.ingestion.amfi.IngestionSummary;
import com.yukira.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic tests for Phase 2J:
 * Historical analytical data foundation, provenance lineage, bitemporal revision handling,
 * trading-date continuity diagnostics, and strict point-in-time knowledge-cutoff enforcement.
 */
@SpringBootTest
@Transactional
@SuppressWarnings({"null", "deprecation"})
public class HistoricalAnalyticalDataServiceTest {

    @Autowired
    private HistoricalAnalyticalDataService historicalDataService;

    @Autowired
    private TradingDateContinuityService continuityService;

    @Autowired
    private AmfiSourceClient amfiSourceClient;

    @Autowired
    private AmfiNavIngestionService ingestionService;

    @Autowired
    private AmcRepository amcRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private SchemePlanRepository schemePlanRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    private SchemeOption canonicalOption;

    @BeforeEach
    void setUp() {
        Amc amc = amcRepository.findByCode("TEST_AMC")
            .orElseGet(() -> amcRepository.save(new Amc("Test Mutual Fund", "TEST_AMC")));

        Scheme scheme = schemeRepository.findByCode("TEST_SCHEME")
            .orElseGet(() -> schemeRepository.save(new Scheme(amc, "Test Scheme", "TEST_SCHEME", LocalDate.of(1995, 1, 1))));

        SchemePlan plan = schemePlanRepository.findByCode("TEST_PLAN_DIR")
            .orElseGet(() -> schemePlanRepository.save(new SchemePlan(scheme, "DIRECT", "TEST_PLAN_DIR")));

        canonicalOption = schemeOptionRepository.findByAmfiCode("999999")
            .orElseGet(() -> schemeOptionRepository.save(new SchemeOption(plan, "GROWTH", "999999", "INF999TEST01")));
    }

    @Test
    @DisplayName("Verification 1 & 2: Historical AMFI parsing and canonical identity resolution")
    void testHistoricalAmfiParsingAndIdentityResolution() {
        String rawSample = """
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Repurchase Price;Sale Price;Date
            999999;Test Scheme - Growth Option - Direct Plan;INF999TEST01;;1250.5000;;;02-Jan-2023
            999999;Test Scheme - Growth Option - Direct Plan;INF999TEST01;;1255.7500;;;03-Jan-2023
            """;

        SourceArtifact artifact = amfiSourceClient.persistRawPayload(
            rawSample.getBytes(StandardCharsets.ISO_8859_1),
            "test://sample/2023",
            OffsetDateTime.now()
        );

        IngestionSummary summary = ingestionService.ingestArtifact(artifact);
        assertEquals(2, summary.validRowsCount());
        assertTrue(summary.observationsIngested() >= 0);

        List<NavObservation> obs = navObservationRepository.findBySchemeOptionId(canonicalOption.getId());
        assertFalse(obs.isEmpty());
        assertTrue(obs.stream().anyMatch(o -> o.getEffectiveDate().equals(LocalDate.of(2023, 1, 2))));
    }

    @Test
    @DisplayName("Verification 3: Artifact cryptographic SHA-256 and foreign-key provenance")
    void testArtifactShaAndProvenance() {
        byte[] payload = "999999;Test Scheme - Growth Option - Direct Plan;INF999TEST01;;1300.0000;;;10-Jan-2023\n".getBytes(StandardCharsets.ISO_8859_1);
        String expectedHash = AmfiSourceClient.computeSha256(payload);

        SourceArtifact artifact = amfiSourceClient.persistRawPayload(payload, "test://sha-test", OffsetDateTime.now());
        assertEquals(expectedHash, artifact.getSha256Hash());
        assertEquals((long) payload.length, artifact.getByteSize());

        ingestionService.ingestArtifact(artifact);
        List<NavObservation> observations = navObservationRepository.findBySchemeOptionIdAndEffectiveDate(
            canonicalOption.getId(), LocalDate.of(2023, 1, 10)
        );

        assertFalse(observations.isEmpty());
        NavObservation obs = observations.get(0);
        assertNotNull(obs.getSourceArtifact());
        assertEquals(artifact.getId(), obs.getSourceArtifact().getId());
        assertEquals(expectedHash, obs.getSourceArtifact().getSha256Hash());
    }

    @Test
    @DisplayName("Verification 4: Duplicate observation handling (skips redundant rows)")
    void testDuplicateObservationHandling() {
        LocalDate date = LocalDate.of(2023, 2, 1);
        OffsetDateTime avail = OffsetDateTime.of(2023, 2, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        byte[] payload = String.format("999999;Test Scheme - Direct;INF999TEST01;;1280.0000;;;01-Feb-2023\n")
            .getBytes(StandardCharsets.ISO_8859_1);

        SourceArtifact artifact1 = amfiSourceClient.persistRawPayload(payload, "test://dup-1", avail);
        IngestionSummary s1 = ingestionService.ingestArtifact(artifact1);
        assertEquals(1, s1.observationsIngested());

        // Ingest identical data from second artifact
        SourceArtifact artifact2 = amfiSourceClient.persistRawPayload(
            (new String(payload) + " ").getBytes(StandardCharsets.ISO_8859_1), "test://dup-2", avail.plusMinutes(5)
        );
        IngestionSummary s2 = ingestionService.ingestArtifact(artifact2);
        assertEquals(0, s2.observationsIngested(), "Duplicate observation must not create new row");
        assertEquals(1, s2.duplicateRowsSkipped(), "Duplicate observation must be recorded in summary");

        List<NavObservation> records = navObservationRepository.findBySchemeOptionIdAndEffectiveDate(canonicalOption.getId(), date);
        assertEquals(1, records.size(), "Ledger must retain exactly 1 row for identical duplicate");
    }

    @Test
    @DisplayName("Verification 5 & 6: Conflicting observation and append-only revision handling")
    void testConflictingObservationAndRevisionHandling() {
        LocalDate date = LocalDate.of(2023, 3, 1);
        OffsetDateTime t1 = OffsetDateTime.of(2023, 3, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime t2 = OffsetDateTime.of(2023, 3, 2, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Initial observation: 1310.0000
        byte[] p1 = "999999;Test Scheme;INF999TEST01;;1310.0000;;;01-Mar-2023\n".getBytes(StandardCharsets.ISO_8859_1);
        SourceArtifact a1 = amfiSourceClient.persistRawPayload(p1, "test://rev-1", t1);
        ingestionService.ingestArtifact(a1);

        // Restatement/Correction published next day: 1312.5000
        byte[] p2 = "999999;Test Scheme;INF999TEST01;;1312.5000;;;01-Mar-2023\n".getBytes(StandardCharsets.ISO_8859_1);
        SourceArtifact a2 = amfiSourceClient.persistRawPayload(p2, "test://rev-2", t2);
        IngestionSummary s2 = ingestionService.ingestArtifact(a2);

        assertEquals(1, s2.revisionsCreated(), "Should create exactly 1 revision");

        List<NavObservation> allRevisions = navObservationRepository
            .findBySchemeOptionIdAndEffectiveDate(canonicalOption.getId(), date);
        assertEquals(2, allRevisions.size(), "Database must store both revisions immutably");

        NavObservation original = allRevisions.stream().filter(r -> r.getRevisionSeq() == 1).findFirst().orElseThrow();
        NavObservation revised = allRevisions.stream().filter(r -> r.getRevisionSeq() == 2).findFirst().orElseThrow();

        assertEquals(0, new BigDecimal("1310.0000").compareTo(original.getNavValue()));
        assertEquals("SUPERSEDED", original.getRevisionStatus());
        assertFalse(Boolean.TRUE.equals(original.getLatestRevision()));

        assertEquals(0, new BigDecimal("1312.5000").compareTo(revised.getNavValue()));
        assertEquals("REVISED", revised.getRevisionStatus());
        assertTrue(Boolean.TRUE.equals(revised.getLatestRevision()));
    }

    @Test
    @DisplayName("Verification 7 & 9: PIT cutoff excludes future data")
    void testPitCutoffExcludesFutureData() {
        LocalDate d1 = LocalDate.of(2023, 5, 10);
        LocalDate d2 = LocalDate.of(2023, 5, 20);
        OffsetDateTime t = OffsetDateTime.of(2023, 5, 25, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        navObservationRepository.save(new NavObservation(canonicalOption, d1, new BigDecimal("1350.0000"), 1, t));
        navObservationRepository.save(new NavObservation(canonicalOption, d2, new BigDecimal("1360.0000"), 1, t));

        // Query with analysis cutoff as of d1 (May 10)
        AnalyticalObservationSeriesDto series = historicalDataService.getHistoricalObservationSeries(
            canonicalOption.getId(),
            LocalDate.of(2023, 5, 1),
            d1,
            t
        );

        assertEquals(1, series.observations().size());
        assertEquals(d1, series.observations().get(0).effectiveDate());
        assertFalse(series.observations().stream().anyMatch(o -> o.effectiveDate().isAfter(d1)),
            "Observations after analysis cutoff must be strictly excluded");
    }

    @Test
    @DisplayName("Verification 8: Knowledge cutoff excludes observations known only in the future")
    void testKnowledgeCutoffExcludesLaterAvailability() {
        LocalDate date = LocalDate.of(2023, 6, 1);
        OffsetDateTime cutoffPast = OffsetDateTime.of(2023, 6, 1, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime availLate = OffsetDateTime.of(2023, 6, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Observation became available only at EOD (23:59:59)
        navObservationRepository.save(new NavObservation(canonicalOption, date, new BigDecimal("1380.0000"), 1, availLate));

        // Query evaluated as of midday (12:00:00) before data was ingested
        AnalyticalObservationSeriesDto series = historicalDataService.getHistoricalObservationSeries(
            canonicalOption.getId(),
            LocalDate.of(2023, 6, 1),
            LocalDate.of(2023, 6, 1),
            cutoffPast
        );

        assertTrue(series.observations().isEmpty(),
            "Observation not yet available at knowledgeCutoff must be excluded");
    }

    @Test
    @DisplayName("Verification 10: Historical series reconstruction is reproducible across temporal states")
    void testHistoricalReconstructionReproducibility() {
        LocalDate date = LocalDate.of(2023, 7, 1);
        OffsetDateTime t1 = OffsetDateTime.of(2023, 7, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime t2 = OffsetDateTime.of(2023, 7, 5, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        NavObservation o1 = new NavObservation(canonicalOption, date, new BigDecimal("1400.0000"), 1, t1);
        o1.setLatestRevision(false);
        o1.setRevisionStatus("SUPERSEDED");
        navObservationRepository.save(o1);

        NavObservation o2 = new NavObservation(canonicalOption, date, new BigDecimal("1405.0000"), 2, t2);
        o2.setLatestRevision(true);
        o2.setRevisionStatus("REVISED");
        navObservationRepository.save(o2);

        // Historical query as of t1 (before revision occurred): Must see 1400.0000
        AnalyticalObservationSeriesDto pastSeries = historicalDataService.getHistoricalObservationSeries(
            canonicalOption.getId(), date, date, t1
        );
        assertEquals(1, pastSeries.observations().size());
        assertEquals(0, new BigDecimal("1400.0000").compareTo(pastSeries.observations().get(0).navValue()));

        // Modern query as of t2 (after revision occurred): Must see revised 1405.0000
        AnalyticalObservationSeriesDto modernSeries = historicalDataService.getHistoricalObservationSeries(
            canonicalOption.getId(), date, date, t2
        );
        assertEquals(1, modernSeries.observations().size());
        assertEquals(0, new BigDecimal("1405.0000").compareTo(modernSeries.observations().get(0).navValue()));
    }

    @Test
    @DisplayName("Verification 11 & 12: Trading-date continuity diagnostics identifies weekends, gaps, and revision invariants")
    void testTradingDateContinuityDiagnostics() {
        LocalDate friday = LocalDate.of(2024, 1, 12);
        LocalDate monday = LocalDate.of(2024, 1, 15);
        LocalDate tuesday = LocalDate.of(2024, 1, 16);

        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        // Clean original observations with revisionSeq = 1
        NavObservation oCleanFri = new NavObservation(canonicalOption, friday, new BigDecimal("1656.4160"), 1, cutoff);
        NavObservation oCleanMon = new NavObservation(canonicalOption, monday, new BigDecimal("1670.6720"), 1, cutoff);

        // Friday with revision seq = 2
        NavObservation oFriRev2 = new NavObservation(canonicalOption, friday, new BigDecimal("1656.4200"), 2, cutoff);

        // Test basic Friday-Monday clean window
        var report1 = continuityService.analyzeContinuity(friday, monday, List.of(oCleanFri, oCleanMon));
        assertEquals(4, report1.totalDaysEvaluated()); // Fri, Sat, Sun, Mon
        assertEquals(2, report1.expectedTradingDays()); // Fri, Mon
        assertEquals(2, report1.expectedNonTradingDays()); // Sat, Sun
        assertEquals(2, report1.uniqueTradingDaysPresent());
        assertEquals(2, report1.validObservationsCount());
        assertEquals(0, report1.missingDaysCount());
        assertEquals(2, report1.totalPhysicalObservations());
        assertEquals(0, report1.revisionRowsCount());
        assertEquals(new BigDecimal("1.000000"), report1.coverageRatio());
        assertFalse(report1.hasContinuityBreach());

        // Test Friday-Tuesday window with revisions and weekday gap:
        // Revisions for same effective date must NOT inflate unique trading dates or coverage
        var report2 = continuityService.analyzeContinuity(friday, tuesday, List.of(oCleanFri, oFriRev2, oCleanMon));
        assertEquals(5, report2.totalDaysEvaluated()); // Fri, Sat, Sun, Mon, Tue
        assertEquals(3, report2.expectedTradingDays()); // Fri, Mon, Tue
        assertEquals(2, report2.expectedNonTradingDays()); // Sat, Sun
        assertEquals(2, report2.uniqueTradingDaysPresent(), "Must count exactly 2 unique trading dates; revisions must not inflate");
        assertEquals(1, report2.validObservationsCount(), "Monday is clean valid observation");
        assertEquals(1, report2.revisedCount(), "Friday is classified as revised observation");
        assertEquals(1, report2.missingDaysCount(), "Tuesday must be detected as 1 missing trading day");
        assertEquals(3, report2.totalPhysicalObservations(), "Physical observations row count must be 3");
        assertEquals(1, report2.revisionRowsCount(), "Must detect exactly 1 revision row for Friday");
        // Coverage: 2 unique trading days / 3 expected trading days = 0.666667 (66.6667%)
        assertEquals(new BigDecimal("0.666667"), report2.coverageRatio());
        assertTrue(report2.hasContinuityBreach(), "Missing Tuesday constitutes a continuity gap");
    }

    @Test
    @DisplayName("Verification 13: Strict rejection of queries missing explicit knowledge cutoff")
    void testRejectionOfQueryMissingKnowledgeCutoff() {
        assertThrows(IllegalArgumentException.class, () ->
            historicalDataService.getHistoricalObservationSeries(
                canonicalOption.getId(),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 15),
                null
            )
        );
    }

    @Test
    @DisplayName("Verification 14: Data quality dimensions are preserved in analytical series contract")
    void testDataQualityPropagation() {
        LocalDate date = LocalDate.of(2023, 11, 15);
        OffsetDateTime cutoff = OffsetDateTime.of(2023, 11, 30, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        NavObservation o = new NavObservation(canonicalOption, date, new BigDecimal("1630.7330"), 1, cutoff);
        o.setQualityAssessment("VALID");
        o.setVerificationStatus("VERIFIED");
        o.setRevisionStatus("ORIGINAL");
        o.setTemporalStatus("CURRENT");
        o.setPresenceStatus("AVAILABLE");
        navObservationRepository.save(o);

        AnalyticalObservationSeriesDto series = historicalDataService.getHistoricalObservationSeries(
            canonicalOption.getId(), date, date, cutoff
        );

        assertEquals(1, series.observations().size());
        var item = series.observations().get(0);
        assertEquals("VALID", item.qualityAssessment());
        assertEquals("VERIFIED", item.verificationStatus());
        assertEquals("ORIGINAL", item.revisionStatus());
        assertEquals("CURRENT", item.temporalStatus());
        assertEquals("AVAILABLE", item.presenceStatus());
    }

    @Test
    @DisplayName("Verification 15: Strict rejection of analysis cutoff date exceeding knowledge cutoff date")
    void testRejectionOfAnalysisCutoffLaterThanKnowledgeCutoff() {
        LocalDate analysisCutoff = LocalDate.of(2024, 2, 1);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            historicalDataService.getHistoricalObservationSeries(
                canonicalOption.getId(),
                LocalDate.of(2024, 1, 1),
                analysisCutoff,
                knowledgeCutoff
            )
        );
        assertTrue(ex.getMessage().contains("cannot exceed knowledgeCutoff date"),
            "Error message must clearly explain PIT boundary violation");
    }
}
