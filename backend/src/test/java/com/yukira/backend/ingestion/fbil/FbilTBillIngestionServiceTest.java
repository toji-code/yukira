package com.yukira.backend.ingestion.fbil;

import com.yukira.backend.domain.entity.RiskFreeObservation;
import com.yukira.backend.domain.entity.ValidationIssue;
import com.yukira.backend.repository.DataSourceRepository;
import com.yukira.backend.repository.RiskFreeObservationRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import com.yukira.backend.repository.ValidationIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@DisplayName("FBIL 91-Day T-Bill Ingestion Service Tests")
class FbilTBillIngestionServiceTest {

    @Autowired
    private FbilTBillIngestionService ingestionService;

    @Autowired
    private RiskFreeObservationRepository riskFreeObservationRepository;

    @Autowired
    private SourceArtifactRepository sourceArtifactRepository;

    @Autowired
    private ValidationIssueRepository validationIssueRepository;

    private static final String SAMPLE_CSV = """
        Date,Tenor,Yield(%)
        01-Jan-2024,91D,6.9500
        02-Jan-2024,91D,6.9650
        03-Jan-2024,91D,6.9400
        """;

    @Test
    @DisplayName("Successfully ingests FBIL payload, records SourceArtifact SHA-256 and bitemporal observations")
    void testSuccessfulIngestionAndProvenanceLinking() {
        byte[] payload = SAMPLE_CSV.getBytes(StandardCharsets.UTF_8);
        FbilTBillIngestionService.FbilIngestionSummary summary = ingestionService.ingestPayload(
            "FBIL_TEST_INGEST", "https://www.fbil.org.in/test-tbill-feed", payload
        );

        assertEquals(3, summary.totalParsed());
        assertEquals(3, summary.insertedCount());
        assertEquals(0, summary.skippedCount());
        assertNotNull(summary.sourceArtifactId());
        assertNotNull(summary.payloadSha256());

        List<RiskFreeObservation> obs = riskFreeObservationRepository.findByBenchmarkCode("FBIL_TEST_INGEST");
        assertTrue(obs.stream().anyMatch(o -> o.getEffectiveDate().equals(LocalDate.of(2024, 1, 1))));
        assertTrue(obs.stream().anyMatch(o -> o.getEffectiveDate().equals(LocalDate.of(2024, 1, 2))));
        assertTrue(obs.stream().anyMatch(o -> o.getEffectiveDate().equals(LocalDate.of(2024, 1, 3))));

        RiskFreeObservation o1 = obs.stream()
            .filter(o -> o.getEffectiveDate().equals(LocalDate.of(2024, 1, 1)))
            .findFirst()
            .orElseThrow();
        assertEquals(new BigDecimal("0.06950000"), o1.getQuotedYield());
        assertEquals(1, o1.getRevisionSeq());
        assertTrue(o1.getLatestRevision());
        assertEquals("VALID", o1.getQualityAssessment());
        assertEquals("VERIFIED", o1.getVerificationStatus());
        assertEquals("ORIGINAL", o1.getRevisionStatus());
        assertEquals(summary.sourceArtifactId(), o1.getSourceArtifact().getId());
    }

    @Test
    @DisplayName("Idempotency: Re-ingesting identical payload produces zero duplicate observations")
    void testIngestionIdempotency() {
        byte[] payload = SAMPLE_CSV.getBytes(StandardCharsets.UTF_8);
        var first = ingestionService.ingestPayload("FBIL_TEST_IDEMPOTENT", "https://www.fbil.org.in/test-tbill-feed", payload);
        assertEquals(3, first.insertedCount());

        var second = ingestionService.ingestPayload("FBIL_TEST_IDEMPOTENT", "https://www.fbil.org.in/test-tbill-feed", payload);
        assertEquals(0, second.insertedCount());
        assertEquals(3, second.skippedCount());
        assertEquals(first.sourceArtifactId(), second.sourceArtifactId());
    }

    @Test
    @DisplayName("Ingests real FBIL JSON fixture with 11 benchmark observations, verifiable provenance and idempotency")
    void testIngestRealFbilJsonFixture() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/fbil_tbill_jan2024.json");
        assertNotNull(stream);
        byte[] payload = stream.readAllBytes();

        String uri = "https://www.fbil.org.in/wasdm/tbill/fetchfiltered?fromDate=2024-01-01&toDate=2024-01-15&authenticated=false";
        
        // 1. First Ingestion
        var firstSummary = ingestionService.ingestPayload("FBIL_TEST_FIXTURE", uri, payload);
        assertEquals(11, firstSummary.totalParsed());
        assertEquals(11, firstSummary.insertedCount());
        assertEquals(0, firstSummary.skippedCount());
        assertEquals("4185b1406cacf54e3910835b5e9004017576ba7aef3f4b05c5c029d72ee50754", firstSummary.payloadSha256());

        // 2. Second Ingestion (Idempotency)
        var secondSummary = ingestionService.ingestPayload("FBIL_TEST_FIXTURE", uri, payload);
        assertEquals(11, secondSummary.totalParsed());
        assertEquals(0, secondSummary.insertedCount());
        assertEquals(11, secondSummary.skippedCount());
        assertEquals(firstSummary.sourceArtifactId(), secondSummary.sourceArtifactId());

        // 3. Verify exact observations in repository
        List<RiskFreeObservation> obs = riskFreeObservationRepository.findByBenchmarkCode("FBIL_TEST_FIXTURE");
        assertEquals(11, obs.size());

        // Check Jan 1 observation
        RiskFreeObservation jan1 = obs.stream()
            .filter(o -> o.getEffectiveDate().equals(LocalDate.of(2024, 1, 1)))
            .findFirst()
            .orElseThrow();
        assertEquals(new BigDecimal("0.06860000"), jan1.getQuotedYield());
        assertEquals("ACT_365", jan1.getDaycountConvention());
        assertEquals("VALID", jan1.getQualityAssessment());
        assertEquals("VERIFIED", jan1.getVerificationStatus());
        assertEquals(firstSummary.sourceArtifactId(), jan1.getSourceArtifact().getId());

        // Check Jan 15 observation
        RiskFreeObservation jan15 = obs.stream()
            .filter(o -> o.getEffectiveDate().equals(LocalDate.of(2024, 1, 15)))
            .findFirst()
            .orElseThrow();
        assertEquals(new BigDecimal("0.06950000"), jan15.getQuotedYield());
        assertEquals(OffsetDateTime.of(2024, 1, 15, 18, 45, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)), jan15.getAvailabilityTime());
    }

    @Test
    @DisplayName("Retroactive revision: later publication increments revision_seq and updates latest pointer")
    void testRetroactiveRevisionTracking() {
        String originalCsv = "Date,Tenor,Yield(%)\n01-Jan-2024,91D,6.9500\n";
        ingestionService.ingestPayload("FBIL_TEST_REV", "https://www.fbil.org.in/tbill-initial", originalCsv.getBytes(StandardCharsets.UTF_8));

        // Restated yield published next day
        String revisedCsv = "Date,Tenor,Yield(%),Time\n01-Jan-2024,91D,6.9550,2024-01-02T10:00:00+05:30\n";
        var summary = ingestionService.ingestPayload("FBIL_TEST_REV", "https://www.fbil.org.in/tbill-revised", revisedCsv.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, summary.revisedCount());

        List<RiskFreeObservation> revisions = riskFreeObservationRepository
            .findByBenchmarkCodeAndEffectiveDate("FBIL_TEST_REV", LocalDate.of(2024, 1, 1));
        assertEquals(2, revisions.size());

        RiskFreeObservation rev1 = revisions.stream().filter(r -> r.getRevisionSeq() == 1).findFirst().orElseThrow();
        RiskFreeObservation rev2 = revisions.stream().filter(r -> r.getRevisionSeq() == 2).findFirst().orElseThrow();

        assertFalse(rev1.getLatestRevision());
        assertEquals(new BigDecimal("0.06950000"), rev1.getQuotedYield());

        assertTrue(rev2.getLatestRevision());
        assertEquals(new BigDecimal("0.06955000"), rev2.getQuotedYield());
        assertEquals("REVISED", rev2.getRevisionStatus());
    }

    @Test
    @DisplayName("Conflicting observations at the same availability instant generate a ValidationIssue and flag SUSPICIOUS")
    void testConflictingObservationDetection() {
        String csv1 = "Date,Tenor,Yield(%),Time\n01-Jan-2024,91D,6.9500,2024-01-01T17:30:00+05:30\n";
        ingestionService.ingestPayload("FBIL_TEST_CONFLICT", "https://www.fbil.org.in/feed-a", csv1.getBytes(StandardCharsets.UTF_8));

        // Same effective date and availability instant, but conflicting yield
        String csv2 = "Date,Tenor,Yield(%),Time\n01-Jan-2024,91D,6.9800,2024-01-01T17:30:00+05:30\n";
        ingestionService.ingestPayload("FBIL_TEST_CONFLICT", "https://www.fbil.org.in/feed-b", csv2.getBytes(StandardCharsets.UTF_8));

        List<RiskFreeObservation> revisions = riskFreeObservationRepository
            .findByBenchmarkCodeAndEffectiveDate("FBIL_TEST_CONFLICT", LocalDate.of(2024, 1, 1));
        assertEquals(2, revisions.size());

        RiskFreeObservation conflicting = revisions.stream().filter(r -> r.getRevisionSeq() == 2).findFirst().orElseThrow();
        assertEquals("SUSPICIOUS", conflicting.getQualityAssessment());
        assertEquals("CONFLICTING", conflicting.getIntegrityCondition());

        List<ValidationIssue> issues = validationIssueRepository.findAll();
        assertTrue(issues.stream().anyMatch(i -> "INGESTION_CONFLICT".equals(i.getCheckCode())));
    }
}
