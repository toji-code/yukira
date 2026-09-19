package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AmfiNavIngestionServiceTest {

    @Autowired
    private AmfiNavIngestionService ingestionService;

    @Autowired
    private AmfiSourceClient amfiSourceClient;

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

    @Autowired
    private ValidationIssueRepository validationIssueRepository;

    private SchemeOption schemeOption;
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    @BeforeEach
    void setUp() {
        Amc amc = amcRepository.findByCode("HDFC_MF")
            .orElseGet(() -> amcRepository.save(new Amc("HDFC Mutual Fund", "HDFC_MF")));
        Scheme scheme = schemeRepository.findByCode("HDFC_FLEXI")
            .orElseGet(() -> schemeRepository.save(new Scheme(amc, "HDFC Flexi Cap Fund", "HDFC_FLEXI", LocalDate.of(1995, 1, 1))));
        SchemePlan plan = schemePlanRepository.findByCode("HDFC_FLEXI_DIR")
            .orElseGet(() -> schemePlanRepository.save(new SchemePlan(scheme, "DIRECT", "HDFC_FLEXI_DIR")));
        schemeOption = schemeOptionRepository.findByAmfiCode("999001")
            .orElseGet(() -> schemeOptionRepository.save(new SchemeOption(plan, "GROWTH", "999001", "INF999001BE2")));
    }

    @Test
    @DisplayName("Test 1: Ingestion of valid AMFI rows creates valid observations with backfill temporal status")
    void testIngestValidRows() {
        String payload = """
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Repurchase Price;Sale Price;Date
            999001;HDFC Flexi Cap Fund - Direct Plan - Growth Option;INF999001BE2;;100.5000;;;10-Jan-2024
            999001;HDFC Flexi Cap Fund - Direct Plan - Growth Option;INF999001BE2;;101.2000;;;11-Jan-2024
            """;
        SourceArtifact artifact = amfiSourceClient.persistRawPayload(
            payload.getBytes(WINDOWS_1252), "mock://amfi/test1", OffsetDateTime.now()
        );

        IngestionSummary summary = ingestionService.ingestArtifact(artifact);

        assertEquals(2, summary.validRowsCount());
        assertEquals(2, summary.observationsIngested());
        assertEquals(0, summary.revisionsCreated());

        List<NavObservation> obs = navObservationRepository
            .findBySchemeOptionIdAndEffectiveDate(schemeOption.getId(), LocalDate.of(2024, 1, 10));
        assertEquals(1, obs.size());
        assertEquals(1, obs.get(0).getRevisionSeq());
        assertEquals("ORIGINAL", obs.get(0).getRevisionStatus());
        assertEquals("CURRENT", obs.get(0).getTemporalStatus());
        assertEquals("VALID", obs.get(0).getQualityAssessment());
        assertEquals(0, new BigDecimal("100.5000").compareTo(obs.get(0).getNavValue()));
    }

    @Test
    @DisplayName("Test 2: Duplicate identical rows in new artifact are skipped without creating redundant revisions")
    void testDeduplicationOfIdenticalRows() {
        String payload = """
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Repurchase Price;Sale Price;Date
            999001;HDFC Flexi Cap Fund - Direct Plan - Growth Option;INF999001BE2;;100.5000;;;10-Jan-2024
            """;
        SourceArtifact a1 = amfiSourceClient.persistRawPayload(payload.getBytes(WINDOWS_1252), "mock://amfi/a1", OffsetDateTime.now());
        ingestionService.ingestArtifact(a1);

        // Second ingestion with identical NAV
        IngestionSummary s2 = ingestionService.ingestArtifact(a1);
        assertEquals(1, s2.duplicateRowsSkipped());
        assertEquals(0, s2.observationsIngested());
        assertEquals(0, s2.revisionsCreated());

        List<NavObservation> obs = navObservationRepository
            .findBySchemeOptionIdAndEffectiveDate(schemeOption.getId(), LocalDate.of(2024, 1, 10));
        assertEquals(1, obs.size(), "Should remain exactly 1 observation (no duplicate row inserted)");
    }

    @Test
    @DisplayName("Test 3: Revised NAV creates new revision_seq while preserving original record immutably")
    void testRevisionPreservesHistoricalImmutability() {
        String originalPayload = "999001;HDFC;INF999001BE2;;100.0000;;;15-Jan-2024\n";
        SourceArtifact a1 = amfiSourceClient.persistRawPayload(originalPayload.getBytes(WINDOWS_1252), "mock://amfi/rev1", OffsetDateTime.now());
        ingestionService.ingestArtifact(a1);

        // Later artifact with restated NAV
        String revisedPayload = "999001;HDFC;INF999001BE2;;102.5000;;;15-Jan-2024\n";
        SourceArtifact a2 = amfiSourceClient.persistRawPayload(revisedPayload.getBytes(WINDOWS_1252), "mock://amfi/rev2", OffsetDateTime.now());
        IngestionSummary s2 = ingestionService.ingestArtifact(a2);

        assertEquals(1, s2.revisionsCreated());

        List<NavObservation> allObs = navObservationRepository
            .findBySchemeOptionIdAndEffectiveDate(schemeOption.getId(), LocalDate.of(2024, 1, 15));
        assertEquals(2, allObs.size(), "Both original and revised observations must exist");

        NavObservation rev1 = allObs.stream().filter(o -> o.getRevisionSeq() == 1).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("100.0000").compareTo(rev1.getNavValue()));
        assertFalse(rev1.getLatestRevision(), "Rev 1 is no longer latest revision");
        assertEquals("SUPERSEDED", rev1.getRevisionStatus());

        NavObservation rev2 = allObs.stream().filter(o -> o.getRevisionSeq() == 2).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("102.5000").compareTo(rev2.getNavValue()));
        assertTrue(rev2.getLatestRevision(), "Rev 2 is current latest revision");
        assertEquals("REVISED", rev2.getRevisionStatus());
    }

    @Test
    @DisplayName("Test 4: Candidate suspicious jump (>20%) is persisted with SUSPICIOUS status and logs validation issue")
    void testCandidateSuspiciousJumpFlagged() {
        String payload = """
            999001;HDFC;INF999001BE2;;100.0000;;;10-Jan-2024
            999001;HDFC;INF999001BE2;;130.0000;;;11-Jan-2024
            """;
        SourceArtifact a = amfiSourceClient.persistRawPayload(payload.getBytes(WINDOWS_1252), "mock://amfi/jump", OffsetDateTime.now());
        IngestionSummary s = ingestionService.ingestArtifact(a);

        assertEquals(2, s.observationsIngested());
        assertTrue(s.validationIssuesCreated() >= 1);

        NavObservation obsJump = navObservationRepository
            .findBySchemeOptionIdAndEffectiveDate(schemeOption.getId(), LocalDate.of(2024, 1, 11))
            .get(0);
        assertEquals("SUSPICIOUS", obsJump.getQualityAssessment());

        List<ValidationIssue> issues = validationIssueRepository
            .findByTargetEntityTypeAndTargetEntityId("SCHEME_OPTION", schemeOption.getId());
        boolean hasJumpIssue = issues.stream().anyMatch(i -> "CANDIDATE_SUSPICIOUS_NAV_JUMP".equals(i.getCheckCode()));
        assertTrue(hasJumpIssue, "Must log CANDIDATE_SUSPICIOUS_NAV_JUMP validation issue");
    }

    @Test
    @DisplayName("Test 5: Non-positive NAV boundary violation rejects row from ledger without deleting raw evidence")
    void testNonPositiveNavRejected() {
        String payload = "999001;HDFC;INF999001BE2;;0.0000;;;10-Jan-2024\n";
        SourceArtifact a = amfiSourceClient.persistRawPayload(payload.getBytes(WINDOWS_1252), "mock://amfi/zero", OffsetDateTime.now());
        IngestionSummary s = ingestionService.ingestArtifact(a);

        assertEquals(0, s.observationsIngested());
        assertEquals(1, s.validationIssuesCreated());

        List<NavObservation> obs = navObservationRepository
            .findBySchemeOptionIdAndEffectiveDate(schemeOption.getId(), LocalDate.of(2024, 1, 10));
        assertTrue(obs.isEmpty(), "Zero NAV must NOT be ingested into nav_observation");
        assertNotNull(a.getPayloadBlob(), "Raw artifact evidence is preserved intact");
    }

    @Test
    @DisplayName("Test 6: Unmapped scheme code emits diagnostic and does not insert fake scheme option")
    void testUnmappedSchemeCodeEmitsDiagnostic() {
        String payload = "999999;UnknownScheme;INFX9999999;;100.0000;;;10-Jan-2024\n";
        SourceArtifact a = amfiSourceClient.persistRawPayload(payload.getBytes(WINDOWS_1252), "mock://amfi/unmapped", OffsetDateTime.now());
        IngestionSummary s = ingestionService.ingestArtifact(a);

        assertEquals(0, s.observationsIngested());
        assertEquals(1, s.validationIssuesCreated());

        List<ValidationIssue> issues = validationIssueRepository
            .findByTargetEntityTypeAndTargetEntityId("SOURCE_ARTIFACT", a.getId());
        boolean hasUnmapped = issues.stream().anyMatch(i -> "UNMAPPED_SOURCE_SCHEME".equals(i.getCheckCode()));
        assertTrue(hasUnmapped, "Must log UNMAPPED_SOURCE_SCHEME validation issue");
    }

    @Test
    @DisplayName("Test 7: SHA-256 hash determinism and deduplication across identical payloads")
    void testSha256DeterminismAndArtifactDeduplication() {
        byte[] bytes = "Deterministic AMFI test payload".getBytes(WINDOWS_1252);
        String hash1 = AmfiSourceClient.computeSha256(bytes);
        String hash2 = AmfiSourceClient.computeSha256(bytes);
        assertEquals(hash1, hash2);

        SourceArtifact a1 = amfiSourceClient.persistRawPayload(bytes, "mock://amfi/hash1", OffsetDateTime.now());
        SourceArtifact a2 = amfiSourceClient.persistRawPayload(bytes, "mock://amfi/hash1", OffsetDateTime.now());

        assertEquals(a1.getId(), a2.getId(), "Must deduplicate and return identical SourceArtifact entity");
    }
}
