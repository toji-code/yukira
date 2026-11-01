package com.yukira.backend.universe;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistorySufficiencyServiceTest {

    @Mock
    private NavObservationRepository navObservationRepository;

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private ReferenceUniverseService referenceUniverseService;

    private HistorySufficiencyService service;

    @BeforeEach
    void setUp() {
        service = new HistorySufficiencyService(navObservationRepository, schemeOptionRepository, referenceUniverseService);
    }

    private SchemeOption createCandidate(Long id, String amfiCode, String isin) {
        Amc amc = new Amc("HDFC Mutual Fund", "HDFC_MF");
        amc.setId(10L);
        Scheme scheme = new Scheme(amc, "HDFC Flexi Cap Fund", "HDFC_FLEXI", null, "Equity Scheme", "Flexi Cap Fund");
        scheme.setId(20L);
        SchemePlan plan = new SchemePlan(scheme, "DIRECT", "HDFC_FLEXI_DIR");
        plan.setId(30L);
        SchemeOption opt = new SchemeOption(plan, "GROWTH", amfiCode, isin);
        opt.setId(id);
        opt.setStatus("ACTIVE");
        return opt;
    }

    @Test
    @DisplayName("Boundary Test: Exactly 700 distinct trading days is ELIGIBLE")
    void testBoundaryExactly700IsEligible() {
        SchemeOption opt = createCandidate(1L, "118955", "INF179K01UT0");
        when(referenceUniverseService.evaluateOption(opt)).thenReturn(
            UniverseEvaluationResult.ofIncluded(1L, "118955", "INF179K01UT0", "HDFC Flexi Cap Fund", "HDFC Mutual Fund", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP)
        );

        LocalDate earliest = LocalDate.of(2021, 1, 1);
        LocalDate latest = LocalDate.of(2024, 1, 15);
        // Summary row: [earliest, latest, distinctCount, invalidCount, missingArtifactCount]
        Object[] summary = new Object[]{earliest, latest, 700L, 0L, 0L};
        when(navObservationRepository.findDistinctValidNavSummaryBySchemeOptionId(1L))
            .thenReturn(Collections.singletonList(summary));

        HistorySufficiencyResult result = service.evaluateOption(opt);

        assertEquals(HistorySufficiencyStatus.ELIGIBLE, result.status());
        assertEquals(700L, result.distinctObservationDates());
        assertEquals(700, result.requiredThreshold());
        assertEquals(earliest, result.earliestObservationDate());
        assertEquals(latest, result.latestObservationDate());
        assertTrue(result.explanation().contains("Satisfies operational minimum history threshold"));
        assertNotNull(result.metricLimitations());
        assertFalse(result.metricLimitations().isEmpty());
    }

    @Test
    @DisplayName("Boundary Test: Exactly 699 distinct trading days is INSUFFICIENT_HISTORY")
    void testBoundaryExactly699IsInsufficientHistory() {
        SchemeOption opt = createCandidate(1L, "118955", "INF179K01UT0");
        when(referenceUniverseService.evaluateOption(opt)).thenReturn(
            UniverseEvaluationResult.ofIncluded(1L, "118955", "INF179K01UT0", "HDFC Flexi Cap Fund", "HDFC Mutual Fund", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP)
        );

        LocalDate earliest = LocalDate.of(2021, 1, 2);
        LocalDate latest = LocalDate.of(2024, 1, 15);
        Object[] summary = new Object[]{earliest, latest, 699L, 0L, 0L};
        when(navObservationRepository.findDistinctValidNavSummaryBySchemeOptionId(1L))
            .thenReturn(Collections.singletonList(summary));

        HistorySufficiencyResult result = service.evaluateOption(opt);

        assertEquals(HistorySufficiencyStatus.INSUFFICIENT_HISTORY, result.status());
        assertEquals(699L, result.distinctObservationDates());
        assertEquals(700, result.requiredThreshold());
        assertTrue(result.explanation().contains("Insufficient history"));
    }

    @Test
    @DisplayName("Zero Observations: Results in NO_HISTORY")
    void testZeroObservationsIsNoHistory() {
        SchemeOption opt = createCandidate(2L, "120000", "INF200K01UT0");
        when(referenceUniverseService.evaluateOption(opt)).thenReturn(
            UniverseEvaluationResult.ofIncluded(2L, "120000", "INF200K01UT0", "Test Fund", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.LARGE_CAP)
        );

        when(navObservationRepository.findDistinctValidNavSummaryBySchemeOptionId(2L))
            .thenReturn(Collections.emptyList());

        HistorySufficiencyResult result = service.evaluateOption(opt);

        assertEquals(HistorySufficiencyStatus.NO_HISTORY, result.status());
        assertEquals(0L, result.distinctObservationDates());
        assertNull(result.earliestObservationDate());
        assertNull(result.latestObservationDate());
        assertTrue(result.explanation().contains("Zero NAV observations"));
    }

    @Test
    @DisplayName("Data Quality Guardrail: Invalid observation causes DATA_QUALITY_LIMITED even if count >= 700")
    void testInvalidObservationCausesDataQualityLimited() {
        SchemeOption opt = createCandidate(3L, "120001", "INF200K01UT1");
        when(referenceUniverseService.evaluateOption(opt)).thenReturn(
            UniverseEvaluationResult.ofIncluded(3L, "120001", "INF200K01UT1", "Test Mid Fund", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.MID_CAP)
        );

        LocalDate earliest = LocalDate.of(2020, 1, 1);
        LocalDate latest = LocalDate.of(2024, 1, 15);
        // 750 distinct dates, but 1 invalid observation
        Object[] summary = new Object[]{earliest, latest, 750L, 1L, 0L};
        when(navObservationRepository.findDistinctValidNavSummaryBySchemeOptionId(3L))
            .thenReturn(Collections.singletonList(summary));

        HistorySufficiencyResult result = service.evaluateOption(opt);

        assertEquals(HistorySufficiencyStatus.DATA_QUALITY_LIMITED, result.status());
        assertEquals(750L, result.distinctObservationDates());
        assertTrue(result.explanation().contains("quality/provenance compromised"));
        assertTrue(result.explanation().contains("1 invalid observations"));
    }

    @Test
    @DisplayName("Provenance Guardrail: Missing source artifact causes DATA_QUALITY_LIMITED")
    void testMissingSourceArtifactCausesDataQualityLimited() {
        SchemeOption opt = createCandidate(4L, "120002", "INF200K01UT2");
        when(referenceUniverseService.evaluateOption(opt)).thenReturn(
            UniverseEvaluationResult.ofIncluded(4L, "120002", "INF200K01UT2", "Test Small Fund", "Test AMC", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.SMALL_CAP)
        );

        LocalDate earliest = LocalDate.of(2020, 1, 1);
        LocalDate latest = LocalDate.of(2024, 1, 15);
        // 800 distinct dates, 0 invalid, but 2 missing source artifacts
        Object[] summary = new Object[]{earliest, latest, 800L, 0L, 2L};
        when(navObservationRepository.findDistinctValidNavSummaryBySchemeOptionId(4L))
            .thenReturn(Collections.singletonList(summary));

        HistorySufficiencyResult result = service.evaluateOption(opt);

        assertEquals(HistorySufficiencyStatus.DATA_QUALITY_LIMITED, result.status());
        assertEquals(800L, result.distinctObservationDates());
        assertTrue(result.explanation().contains("2 missing source artifacts"));
    }

    @Test
    @DisplayName("Point-in-Time PIT Cutoff: Excludes future observations and revisions published after cutoff")
    void testPointInTimeCutoffExcludesFutureData() {
        SchemeOption opt = createCandidate(1L, "118955", "INF179K01UT0");
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(opt));
        when(referenceUniverseService.evaluateOption(opt)).thenReturn(
            UniverseEvaluationResult.ofIncluded(1L, "118955", "INF179K01UT0", "HDFC Flexi Cap Fund", "HDFC Mutual Fund", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP)
        );

        LocalDate cutoffDate = LocalDate.of(2024, 1, 10);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        SourceArtifact artifact = new SourceArtifact();
        artifact.setId(100L);
        artifact.setSha256Hash("test-artifact-hash");

        // Simulate 3 observations known as of 2024-01-10
        NavObservation o1 = new NavObservation(opt, LocalDate.of(2024, 1, 8), new BigDecimal("1633.2610"), 1, OffsetDateTime.of(2024, 1, 8, 21, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        o1.setSourceArtifact(artifact);
        o1.setQualityAssessment("VALID");

        NavObservation o2 = new NavObservation(opt, LocalDate.of(2024, 1, 9), new BigDecimal("1639.4210"), 1, OffsetDateTime.of(2024, 1, 9, 21, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        o2.setSourceArtifact(artifact);
        o2.setQualityAssessment("VALID");

        // Two revisions on 2024-01-10 (same date - should deduplicate to 1 distinct date)
        NavObservation o3 = new NavObservation(opt, LocalDate.of(2024, 1, 10), new BigDecimal("1645.8200"), 1, OffsetDateTime.of(2024, 1, 10, 21, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        o3.setSourceArtifact(artifact);
        o3.setQualityAssessment("VALID");

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(1L, cutoffDate, knowledgeCutoff))
            .thenReturn(List.of(o1, o2, o3));

        HistorySufficiencyResult result = service.evaluateOptionAsOf(1L, cutoffDate, knowledgeCutoff);

        // Distinct dates should be 3
        assertEquals(3L, result.distinctObservationDates());
        assertEquals(LocalDate.of(2024, 1, 8), result.earliestObservationDate());
        assertEquals(LocalDate.of(2024, 1, 10), result.latestObservationDate());
        assertEquals(HistorySufficiencyStatus.INSUFFICIENT_HISTORY, result.status());
    }

    @Test
    @DisplayName("Deduplication Invariant: Multiple revisions on same date do not inflate distinct trading day count")
    void testRevisionDeduplicationDoesNotInflateCount() {
        SchemeOption opt = createCandidate(1L, "118955", "INF179K01UT0");
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(opt));
        when(referenceUniverseService.evaluateOption(opt)).thenReturn(
            UniverseEvaluationResult.ofIncluded(1L, "118955", "INF179K01UT0", "HDFC Flexi Cap Fund", "HDFC Mutual Fund", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP)
        );

        LocalDate cutoffDate = LocalDate.of(2024, 1, 5);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 5, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        SourceArtifact artifact = new SourceArtifact();
        artifact.setId(100L);

        // Observations on 2024-01-02, 2024-01-03, and two revisions for 2024-01-04
        NavObservation o1 = new NavObservation(opt, LocalDate.of(2024, 1, 2), new BigDecimal("1625.1540"), 1, OffsetDateTime.now());
        o1.setSourceArtifact(artifact);
        o1.setQualityAssessment("VALID");

        NavObservation o2 = new NavObservation(opt, LocalDate.of(2024, 1, 3), new BigDecimal("1626.4560"), 1, OffsetDateTime.now());
        o2.setSourceArtifact(artifact);
        o2.setQualityAssessment("VALID");

        NavObservation o3_rev1 = new NavObservation(opt, LocalDate.of(2024, 1, 4), new BigDecimal("1639.8500"), 1, OffsetDateTime.now());
        o3_rev1.setSourceArtifact(artifact);
        o3_rev1.setQualityAssessment("VALID");

        NavObservation o3_rev2 = new NavObservation(opt, LocalDate.of(2024, 1, 4), new BigDecimal("1639.8560"), 2, OffsetDateTime.now());
        o3_rev2.setSourceArtifact(artifact);
        o3_rev2.setQualityAssessment("VALID");

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(1L, cutoffDate, knowledgeCutoff))
            .thenReturn(List.of(o1, o2, o3_rev1, o3_rev2));

        HistorySufficiencyResult result = service.evaluateOptionAsOf(1L, cutoffDate, knowledgeCutoff);

        // Despite 4 observation rows, distinct dates must be 3
        assertEquals(3L, result.distinctObservationDates(), "Distinct dates must deduplicate multiple revisions on the same date");
    }

    @Test
    @DisplayName("Transient Field Enrichment: Sets status, observation count, and dates on entity")
    void testEnrichOption() {
        SchemeOption opt = createCandidate(1L, "118955", "INF179K01UT0");
        when(referenceUniverseService.evaluateOption(opt)).thenReturn(
            UniverseEvaluationResult.ofIncluded(1L, "118955", "INF179K01UT0", "HDFC Flexi Cap Fund", "HDFC Mutual Fund", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP)
        );

        LocalDate earliest = LocalDate.of(2021, 1, 1);
        LocalDate latest = LocalDate.of(2024, 1, 15);
        Object[] summary = new Object[]{earliest, latest, 720L, 0L, 0L};
        when(navObservationRepository.findDistinctValidNavSummaryBySchemeOptionId(1L))
            .thenReturn(Collections.singletonList(summary));

        service.enrichOption(opt);

        assertEquals("ELIGIBLE", opt.getHistorySufficiencyStatus());
        assertEquals(720L, opt.getDistinctObservationCount());
        assertEquals("2021-01-01", opt.getEarliestNavDate());
        assertEquals("2024-01-15", opt.getLatestNavDate());
    }

    @Test
    @DisplayName("Universe Report Aggregation: Categorizes reference universe options accurately without double counting")
    void testGenerateUniverseSufficiencyReport() {
        SchemeOption opt1 = createCandidate(1L, "118955", "INF179K01UT0"); // ELIGIBLE (Flexi Cap)
        SchemeOption opt2 = createCandidate(2L, "120501", "INF200K01UT1"); // INSUFFICIENT_HISTORY (Large Cap)
        SchemeOption opt3 = createCandidate(3L, "120601", "INF201K01UT2"); // NO_HISTORY (Mid Cap)
        SchemeOption opt4 = createCandidate(4L, "120701", "INF202K01UT3"); // DATA_QUALITY_LIMITED (Small Cap)

        when(referenceUniverseService.getEligibleReferenceUniverseOptions()).thenReturn(List.of(opt1, opt2, opt3, opt4));
        when(referenceUniverseService.evaluateOption(opt1)).thenReturn(
            UniverseEvaluationResult.ofIncluded(1L, "118955", "INF179K01UT0", "HDFC Flexi Cap Fund", "HDFC Mutual Fund", "DIRECT", "GROWTH", "ACTIVE", EquityUniverseCategory.FLEXI_CAP)
        );

        // Mock summaries batch:
        // [schemeOptionId, earliest, latest, distinctCount, invalidCount, missingArtifactCount]
        List<Object[]> summaries = List.of(
            new Object[]{1L, LocalDate.of(2021, 1, 1), LocalDate.of(2024, 1, 15), 750L, 0L, 0L},
            new Object[]{2L, LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 15), 250L, 0L, 0L},
            // opt3 is not present in summaryMap -> NO_HISTORY
            new Object[]{4L, LocalDate.of(2020, 1, 1), LocalDate.of(2024, 1, 15), 800L, 2L, 0L} // invalid observations -> DATA_QUALITY_LIMITED
        );
        when(navObservationRepository.findDistinctValidNavSummaries()).thenReturn(summaries);

        UniverseHistorySufficiencyReportDto report = service.generateUniverseSufficiencyReport();

        assertNotNull(report);
        assertEquals(4L, report.totalReferenceOptionsEvaluated());
        assertEquals(1L, report.eligibleCount());
        assertEquals(1L, report.insufficientHistoryCount());
        assertEquals(1L, report.noHistoryCount());
        assertEquals(1L, report.dataQualityLimitedCount());

        assertEquals(1L, report.countByStatus().get("ELIGIBLE"));
        assertEquals(1L, report.countByStatus().get("INSUFFICIENT_HISTORY"));
        assertEquals(1L, report.countByStatus().get("NO_HISTORY"));
        assertEquals(1L, report.countByStatus().get("DATA_QUALITY_LIMITED"));

        assertEquals(1L, report.eligibleByCategory().get("FLEXI_CAP"));
        assertEquals(0L, report.eligibleByCategory().get("LARGE_CAP"));
        assertFalse(report.metricLimitations().isEmpty());
        assertFalse(report.governanceNotes().isEmpty());
    }
}
