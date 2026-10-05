package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.AnalyticalScore;
import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.domain.entity.ValidationIssue;
import com.yukira.backend.dto.ingestion.AmfiDataFreshnessDto;
import com.yukira.backend.dto.ingestion.AmfiNavRefreshResultDto;
import com.yukira.backend.ingestion.amfi.AmfiSourceClient;
import com.yukira.backend.repository.AnalyticalScoreRepository;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import com.yukira.backend.repository.ValidationIssueRepository;
import com.yukira.backend.service.ingestion.AmfiDataFreshnessService;
import com.yukira.backend.service.ingestion.AmfiNavRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AmfiNavRefreshIntegrationTest {

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private AmfiNavRefreshService navRefreshService;

    @Autowired
    private AmfiDataFreshnessService dataFreshnessService;

    @Autowired
    private AmfiSourceClient amfiSourceClient;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Autowired
    private SourceArtifactRepository sourceArtifactRepository;

    @Autowired
    private ValidationIssueRepository validationIssueRepository;

    @Autowired
    private AnalyticalScoreRepository analyticalScoreRepository;

    @BeforeEach
    void setUp() {
        pilotBootstrapService.bootstrapPilot();
    }

    @Test
    @DisplayName("TC-REFRESH-01: Controlled source payload refresh succeeds with valid observations")
    void refreshFromControlledPayloadSucceeds() {
        String payload = buildSampleAmfiPayload("118955", "INF179K01UT0", "2024-01-20", "1680.5000");
        AmfiNavRefreshResultDto result = navRefreshService.refreshFromPayload(payload.getBytes(StandardCharsets.ISO_8859_1), "test://controlled-payload");

        assertEquals("SUCCESS", result.status());
        assertNotNull(result.sourceArtifactId());
        assertNotNull(result.sourceHash());

        SchemeOption hdfc = schemeOptionRepository.findById(1L).orElseThrow();
        List<NavObservation> obs = navObservationRepository.findBySchemeOptionIdAndEffectiveDate(hdfc.getId(), LocalDate.of(2024, 1, 20));
        assertFalse(obs.isEmpty());
        assertEquals(0, new BigDecimal("1680.5000").compareTo(obs.get(0).getNavValue()));
    }

    @Test
    @DisplayName("TC-REFRESH-02: Re-running identical source artifact payload is idempotent")
    void rerunningIdenticalPayloadIsIdempotent() {
        String payload = buildSampleAmfiPayload("118955", "INF179K01UT0", "2024-01-21", "1685.0000");
        byte[] bytes = payload.getBytes(StandardCharsets.ISO_8859_1);

        AmfiNavRefreshResultDto run1 = navRefreshService.refreshFromPayload(bytes, "test://idempotent");
        long obsCountAfterRun1 = navObservationRepository.count();

        AmfiNavRefreshResultDto run2 = navRefreshService.refreshFromPayload(bytes, "test://idempotent");
        long obsCountAfterRun2 = navObservationRepository.count();

        assertEquals(run1.sourceHash(), run2.sourceHash());
        assertEquals(obsCountAfterRun1, obsCountAfterRun2);
    }

    @Test
    @DisplayName("TC-REFRESH-03: Duplicate identical NAV observations skip without creating new records")
    void duplicateIdenticalObservationsAreSkipped() {
        SchemeOption option = schemeOptionRepository.findById(1L).orElseThrow();
        LocalDate date = LocalDate.of(2024, 1, 15);
        long initialCount = navObservationRepository.findBySchemeOptionIdAndEffectiveDate(option.getId(), date).size();

        String payload = buildSampleAmfiPayload("118955", "INF179K01UT0", "2024-01-15", "1670.6720"); // Matches pilot baseline
        AmfiNavRefreshResultDto result = navRefreshService.refreshFromPayload(payload.getBytes(StandardCharsets.ISO_8859_1), "test://duplicate");

        long finalCount = navObservationRepository.findBySchemeOptionIdAndEffectiveDate(option.getId(), date).size();
        assertEquals(initialCount, finalCount);
    }

    @Test
    @DisplayName("TC-REFRESH-04 & 05: Revisions preserve historical observations and increment revision_seq bitemporally")
    void revisionsPreserveImmutabilityAndIncrementSeq() {
        SchemeOption option = schemeOptionRepository.findById(1L).orElseThrow();
        LocalDate revDate = LocalDate.of(2024, 1, 10);

        List<NavObservation> initialObs = navObservationRepository.findBySchemeOptionIdAndEffectiveDate(option.getId(), revDate);
        assertFalse(initialObs.isEmpty());
        NavObservation orig = initialObs.get(0);
        int origSeq = orig.getRevisionSeq();

        // Submit revised NAV for same date
        String revisedPayload = buildSampleAmfiPayload("118955", "INF179K01UT0", "2024-01-10", "1699.9900");
        navRefreshService.refreshFromPayload(revisedPayload.getBytes(StandardCharsets.ISO_8859_1), "test://revision");

        List<NavObservation> allRevisions = navObservationRepository.findBySchemeOptionIdAndEffectiveDate(option.getId(), revDate);
        assertEquals(initialObs.size() + 1, allRevisions.size());

        NavObservation revised = allRevisions.stream()
            .max((a, b) -> Integer.compare(a.getRevisionSeq(), b.getRevisionSeq()))
            .orElseThrow();

        assertEquals(origSeq + 1, revised.getRevisionSeq());
        assertEquals("REVISED", revised.getRevisionStatus());
        assertEquals(0, new BigDecimal("1699.9900").compareTo(revised.getNavValue()));
    }

    @Test
    @DisplayName("TC-REFRESH-06: PIT metadata effective_date, availability_time, and revision_seq are preserved")
    void pitMetadataIsPreserved() {
        SchemeOption option = schemeOptionRepository.findById(1L).orElseThrow();
        String payload = buildSampleAmfiPayload("118955", "INF179K01UT0", "2024-01-22", "1690.0000");
        navRefreshService.refreshFromPayload(payload.getBytes(StandardCharsets.ISO_8859_1), "test://pit-metadata");

        NavObservation obs = navObservationRepository.findBySchemeOptionIdAndEffectiveDate(option.getId(), LocalDate.of(2024, 1, 22)).get(0);
        assertNotNull(obs.getEffectiveDate());
        assertNotNull(obs.getAvailabilityTime());
        assertTrue(obs.getRevisionSeq() >= 1);
        assertNotNull(obs.getSourceArtifact());
    }

    @Test
    @DisplayName("TC-REFRESH-07: Invalid non-positive NAV rows are rejected and flagged as ValidationIssues")
    void invalidNavRowsAreFlagged() {
        String invalidPayload = buildSampleAmfiPayload("118955", "INF179K01UT0", "2024-01-23", "-10.0000");
        long initialIssues = validationIssueRepository.count();

        navRefreshService.refreshFromPayload(invalidPayload.getBytes(StandardCharsets.ISO_8859_1), "test://invalid-nav");

        long finalIssues = validationIssueRepository.count();
        assertTrue(finalIssues > initialIssues);
    }

    @Test
    @DisplayName("TC-REFRESH-08: Unresolved scheme identity is quarantined as ValidationIssue and not silently assigned")
    void unresolvedSchemeIdentityIsQuarantined() {
        String unmappedPayload = buildSampleAmfiPayload("999999", "INF999K99XX9", "2024-01-24", "100.0000");
        byte[] bytes = unmappedPayload.getBytes(StandardCharsets.ISO_8859_1);
        SourceArtifact artifact = amfiSourceClient.persistRawPayload(bytes, "test://unmapped", OffsetDateTime.now());

        long initialObs = navObservationRepository.count();
        long initialIssues = validationIssueRepository.count();

        navRefreshService.refreshFromHistoricalArtifact(artifact);

        assertEquals(initialObs, navObservationRepository.count());
        assertTrue(validationIssueRepository.count() > initialIssues);
    }

    @Test
    @DisplayName("TC-REFRESH-09: Source artifact SHA-256 hash is persisted correctly")
    void sourceArtifactHashIsPersisted() {
        String payload = buildSampleAmfiPayload("118955", "INF179K01UT0", "2024-01-25", "1700.0000");
        byte[] bytes = payload.getBytes(StandardCharsets.ISO_8859_1);
        String expectedHash = AmfiSourceClient.computeSha256(bytes);

        AmfiNavRefreshResultDto result = navRefreshService.refreshFromPayload(bytes, "test://hash-check");
        assertEquals(expectedHash, result.sourceHash());

        SourceArtifact artifact = sourceArtifactRepository.findBySha256Hash(expectedHash).orElseThrow();
        assertEquals((long) bytes.length, artifact.getByteSize());
    }

    @Test
    @DisplayName("TC-REFRESH-10 & 11: Freshness read model deterministically reports latest date and state")
    void freshnessReadModelReportsState() {
        AmfiDataFreshnessDto freshness = dataFreshnessService.getFreshnessStatus(LocalDate.of(2024, 1, 15));
        assertNotNull(freshness.latestNavDate());
        assertEquals(LocalDate.of(2024, 1, 15), freshness.latestNavDate());
        assertEquals("FRESH", freshness.freshnessState());
        assertEquals(3, freshness.freshnessThresholdDays());
        assertNotNull(freshness.governanceDisclaimer());
    }

    @Test
    @DisplayName("TC-REFRESH-13: HDFC pilot scheme_option_id 1 remains correctly resolved")
    void hdfcPilotIdentityRemainsResolved() {
        SchemeOption hdfc = schemeOptionRepository.findById(1L).orElseThrow();
        assertEquals("118955", hdfc.getAmfiCode());
        assertEquals("INF179K01UT0", hdfc.getIsin());
    }

    @Test
    @DisplayName("TC-REFRESH-15 & 16: Ingestion does NOT invoke quant calculation engine or alter historical score snapshots")
    void ingestionDoesNotAlterHistoricalScoresOrInvokeQuant() {
        long initialScoresCount = analyticalScoreRepository.count();
        List<AnalyticalScore> scoresBefore = analyticalScoreRepository.findAll();

        String payload = buildSampleAmfiPayload("118955", "INF179K01UT0", "2024-01-26", "1710.0000");
        navRefreshService.refreshFromPayload(payload.getBytes(StandardCharsets.ISO_8859_1), "test://quant-check");

        long finalScoresCount = analyticalScoreRepository.count();
        assertEquals(initialScoresCount, finalScoresCount);

        List<AnalyticalScore> scoresAfter = analyticalScoreRepository.findAll();
        for (int i = 0; i < scoresBefore.size(); i++) {
            assertEquals(scoresBefore.get(i).getScore(), scoresAfter.get(i).getScore());
            assertEquals(scoresBefore.get(i).getAsOfDate(), scoresAfter.get(i).getAsOfDate());
        }
    }

    private String buildSampleAmfiPayload(String schemeCode, String isin, String dateStr, String navStr) {
        // AMFI NAVAll.txt format line: Scheme Code;ISIN Growth;ISIN Reinvest;Scheme Name;NAV;Date
        return String.format(
            "Open Ended Schemes ( Equity Scheme - Flexi Cap Fund )\r\n" +
            "HDFC Mutual Fund\r\n" +
            "Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Net Asset Value;Date\r\n" +
            "%s;%s;;HDFC Flexi Cap Fund - DIRECT Plan - GROWTH Option;%s;%s\r\n",
            schemeCode, isin, navStr, formatDateForAmfi(dateStr)
        );
    }

    private String formatDateForAmfi(String isoDateStr) {
        LocalDate date = LocalDate.parse(isoDateStr);
        // AMFI format e.g. 20-Jan-2024
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        return String.format("%02d-%s-%d", date.getDayOfMonth(), months[date.getMonthValue() - 1], date.getYear());
    }
}
