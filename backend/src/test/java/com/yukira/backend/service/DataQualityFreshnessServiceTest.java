package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.analysis.DataQualityFreshnessDto;
import com.yukira.backend.dto.analysis.DataQualityFreshnessDto.*;
import com.yukira.backend.repository.*;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class DataQualityFreshnessServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;
    @Mock
    private NavObservationRepository navObservationRepository;
    @Mock
    private PortfolioSnapshotRepository snapshotRepository;
    @Mock
    private PortfolioHoldingRepository holdingRepository;

    private DataQualityFreshnessService freshnessService;

    private SchemeOption testOption;
    private SchemePlan testPlan;
    private Scheme testScheme;
    private Amc testAmc;

    @BeforeEach
    void setUp() {
        freshnessService = new DataQualityFreshnessService(
            schemeOptionRepository,
            navObservationRepository,
            snapshotRepository,
            holdingRepository
        );

        testAmc = new Amc("HDFC_MF", "HDFC Mutual Fund");
        testAmc.setId(1L);

        testScheme = new Scheme(testAmc, "HDFC Flexi Cap Fund", "HDFC_FLEXI", LocalDate.of(1995, 1, 1), "Equity", "Flexi Cap Fund");
        testScheme.setId(10L);

        testPlan = new SchemePlan(testScheme, "DIRECT", "HDFC_FLEXI_DIR");
        testPlan.setId(100L);

        testOption = new SchemeOption(testPlan, "GROWTH", "118955", "INF179K01UT0");
        testOption.setId(1L);
    }

    @Test
    @DisplayName("NAV freshness: Current when observation is within 5 calendar days")
    void testNavFreshnessCurrentWhenWithinFiveDays() {
        LocalDate evalDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        NavObservation nav1 = new NavObservation(testOption, LocalDate.of(2024, 1, 12), new BigDecimal("1656.4160"), 1, cutoff);
        NavObservation nav2 = new NavObservation(testOption, LocalDate.of(2024, 1, 15), new BigDecimal("1670.6720"), 1, cutoff);

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), eq(evalDate), eq(cutoff)))
            .thenReturn(List.of(nav1, nav2));
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), eq(cutoff)))
            .thenReturn(Collections.emptyList());

        DataQualityFreshnessDto result = freshnessService.evaluateQualityAndFreshness(1L, evalDate, cutoff);

        assertNotNull(result);
        assertEquals("CURRENT", result.navEvidence().freshnessState());
        assertEquals(0L, result.navEvidence().observationAgeDays());
        assertEquals(2L, result.navEvidence().observationCount());
        assertEquals("UNAVAILABLE", result.holdingsEvidence().freshnessState());
    }

    @Test
    @DisplayName("NAV freshness: Stale when observation is older than 5 calendar days")
    void testNavFreshnessStaleWhenOlderThanFiveDays() {
        LocalDate evalDate = LocalDate.of(2024, 2, 1);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 2, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        NavObservation nav1 = new NavObservation(testOption, LocalDate.of(2024, 1, 1), new BigDecimal("1630.7330"), 1, cutoff);
        NavObservation nav2 = new NavObservation(testOption, LocalDate.of(2024, 1, 15), new BigDecimal("1670.6720"), 1, cutoff);

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), eq(evalDate), eq(cutoff)))
            .thenReturn(List.of(nav1, nav2));
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), eq(cutoff)))
            .thenReturn(Collections.emptyList());

        DataQualityFreshnessDto result = freshnessService.evaluateQualityAndFreshness(1L, evalDate, cutoff);

        assertEquals("STALE", result.navEvidence().freshnessState());
        assertEquals(17L, result.navEvidence().observationAgeDays());
        assertTrue(result.navEvidence().explanation().contains("17 days old"));
    }

    @Test
    @DisplayName("NAV freshness: Unavailable when zero observations exist in ledger")
    void testNavFreshnessUnavailableWhenZeroObservations() {
        LocalDate evalDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), eq(evalDate), eq(cutoff)))
            .thenReturn(Collections.emptyList());
        when(navObservationRepository.countBySchemeOptionId(1L)).thenReturn(0L);
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), eq(cutoff)))
            .thenReturn(Collections.emptyList());

        DataQualityFreshnessDto result = freshnessService.evaluateQualityAndFreshness(1L, evalDate, cutoff);

        assertEquals("UNAVAILABLE", result.navEvidence().freshnessState());
        assertEquals(0L, result.navEvidence().observationCount());
        assertTrue(result.navEvidence().explanation().contains("No historical NAV observations recorded"));
    }

    @Test
    @DisplayName("Holdings freshness is completely independent of NAV freshness")
    void testHoldingsFreshnessIndependentOfNavFreshness() {
        // Scenario: Daily NAV is CURRENT, but monthly holdings are STALE (e.g. 70 days old)
        LocalDate evalDate = LocalDate.of(2024, 4, 10);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 4, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        NavObservation nav = new NavObservation(testOption, LocalDate.of(2024, 4, 9), new BigDecimal("1750.00"), 1, cutoff);

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(10L);
        snap.setPortfolioDate(LocalDate.of(2024, 1, 31)); // 70 days prior
        snap.setAvailabilityTime(OffsetDateTime.of(2024, 2, 10, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        snap.setReportedHoldingsCount(10);
        snap.setSumReportedWeights(new BigDecimal("0.548200")); // 54.82%

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), eq(evalDate), eq(cutoff)))
            .thenReturn(List.of(nav));
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), eq(cutoff)))
            .thenReturn(List.of(snap));

        DataQualityFreshnessDto result = freshnessService.evaluateQualityAndFreshness(1L, evalDate, cutoff);

        assertEquals("CURRENT", result.navEvidence().freshnessState(), "NAV is 1 day old, must be CURRENT");
        assertEquals("STALE", result.holdingsEvidence().freshnessState(), "Holdings reporting date is 70 days old, must be STALE");
        assertEquals("PARTIAL", result.holdingsEvidence().coverageState(), "54.82% weight is partial top holdings");
    }

    @Test
    @DisplayName("Holdings weights are strictly unnormalized: raw sum below 100% preserves partial disclosure")
    void testHoldingsWeightsRawUnnormalizedBelow100() {
        LocalDate evalDate = LocalDate.of(2024, 2, 5);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 2, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(10L);
        snap.setPortfolioDate(LocalDate.of(2024, 1, 31));
        snap.setAvailabilityTime(OffsetDateTime.of(2024, 2, 5, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        snap.setReportedHoldingsCount(10);
        snap.setSumReportedWeights(new BigDecimal("0.548200")); // 54.82%

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(any(), any(), any()))
            .thenReturn(Collections.emptyList());
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), eq(cutoff)))
            .thenReturn(List.of(snap));

        DataQualityFreshnessDto result = freshnessService.evaluateQualityAndFreshness(1L, evalDate, cutoff);

        assertEquals(new BigDecimal("0.548200"), result.holdingsEvidence().rawSumWeights());
        assertEquals(54.82, result.holdingsEvidence().rawSumWeightsPercentage(), 0.001);
        assertEquals("PARTIAL", result.holdingsEvidence().coverageState());
        // Verify explanation mentions the raw percentage and residual
        assertTrue(result.holdingsEvidence().warnings().stream().anyMatch(w -> w.contains("54.82%") && w.contains("45.18% residual")));
    }

    @Test
    @DisplayName("Holdings weights raw sum above 100%: flags gross exposure warning without normalizing")
    void testHoldingsWeightsRawSumAbove100() {
        LocalDate evalDate = LocalDate.of(2024, 2, 5);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 2, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(20L);
        snap.setPortfolioDate(LocalDate.of(2024, 1, 31));
        snap.setReportedHoldingsCount(50);
        snap.setSumReportedWeights(new BigDecimal("1.025000")); // 102.5% gross exposure

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(any(), any(), any()))
            .thenReturn(Collections.emptyList());
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), eq(cutoff)))
            .thenReturn(List.of(snap));

        DataQualityFreshnessDto result = freshnessService.evaluateQualityAndFreshness(1L, evalDate, cutoff);

        assertEquals(102.50, result.holdingsEvidence().rawSumWeightsPercentage(), 0.001);
        assertEquals("COMPLETE", result.holdingsEvidence().coverageState());
        assertTrue(result.holdingsEvidence().warnings().stream().anyMatch(w -> w.contains("102.50%") && w.contains("gross exposure")));
    }

    @Test
    @DisplayName("Reporting date is strictly separated from availabilityTime and ingestion timestamp")
    void testReportingDateStrictlySeparatedFromAvailabilityAndIngestionTime() {
        LocalDate reportingDate = LocalDate.of(2024, 1, 31);
        OffsetDateTime availabilityTime = OffsetDateTime.of(2024, 2, 10, 18, 30, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime ingestionTimestamp = OffsetDateTime.of(2024, 2, 10, 19, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        SourceArtifact sa = new SourceArtifact();
        sa.setId(42L);
        sa.setSha256Hash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        sa.setRetrievalTimestamp(ingestionTimestamp);
        sa.setStorageUri("https://portal.amfiindia.com/disclosures/monthly-118955.csv");

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(10L);
        snap.setPortfolioDate(reportingDate);
        snap.setAvailabilityTime(availabilityTime);
        snap.setSourceArtifact(sa);
        snap.setReportedHoldingsCount(10);
        snap.setSumReportedWeights(new BigDecimal("0.548200"));

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(any(), any(), any()))
            .thenReturn(Collections.emptyList());
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(snap));

        DataQualityFreshnessDto result = freshnessService.evaluateQualityAndFreshness(1L, LocalDate.of(2024, 2, 15), OffsetDateTime.now());

        assertEquals(reportingDate, result.holdingsEvidence().latestReportingDate());
        assertEquals(availabilityTime, result.holdingsEvidence().availabilityTime());
        assertEquals(ingestionTimestamp, result.holdingsEvidence().ingestionTimestamp());
        assertNotEquals(result.holdingsEvidence().latestReportingDate().toString(), result.holdingsEvidence().availabilityTime().toString());
    }

    @Test
    @DisplayName("Metric eligibility: Drift requires >= 2 snapshots; 1 snapshot reports INSUFFICIENT_HISTORY")
    void testMetricEligibilityDriftRequiresTwoSnapshots() {
        PortfolioSnapshot snap1 = new PortfolioSnapshot();
        snap1.setId(1L);
        snap1.setPortfolioDate(LocalDate.of(2024, 1, 31));
        snap1.setReportedHoldingsCount(10);
        snap1.setSumReportedWeights(new BigDecimal("0.548200"));

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(any(), any(), any()))
            .thenReturn(Collections.emptyList());
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(snap1)); // only 1 snapshot

        DataQualityFreshnessDto result = freshnessService.evaluateQualityAndFreshness(1L, LocalDate.of(2024, 2, 15), OffsetDateTime.now());

        MetricEligibilityItemDto driftItem = result.metricEligibility().stream()
            .filter(m -> "PORTFOLIO_DRIFT".equals(m.metricCode()))
            .findFirst()
            .orElseThrow();

        assertEquals("INSUFFICIENT_HISTORY", driftItem.eligibilityState());
        assertTrue(driftItem.explanation().contains("at least 2 distinct monthly reporting periods required"));
    }

    @Test
    @DisplayName("Metric eligibility: 3Y metrics require >= 700 observations; 11 observations gives INSUFFICIENT_HISTORY")
    void testMetricEligibility3YRequires700Observations() {
        OffsetDateTime now = OffsetDateTime.now();
        NavObservation nav1 = new NavObservation(testOption, LocalDate.of(2024, 1, 1), new BigDecimal("1630.7330"), 1, now);
        NavObservation nav2 = new NavObservation(testOption, LocalDate.of(2024, 1, 15), new BigDecimal("1670.6720"), 1, now);

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(any(), any(), any()))
            .thenReturn(List.of(nav1, nav2)); // only 2 observations
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(Collections.emptyList());

        DataQualityFreshnessDto result = freshnessService.evaluateQualityAndFreshness(1L, LocalDate.of(2024, 1, 15), OffsetDateTime.now());

        MetricEligibilityItemDto ret02 = result.metricEligibility().stream()
            .filter(m -> "RET-02".equals(m.metricCode())).findFirst().orElseThrow();
        MetricEligibilityItemDto ret03 = result.metricEligibility().stream()
            .filter(m -> "RET-03".equals(m.metricCode())).findFirst().orElseThrow();
        MetricEligibilityItemDto rsk01 = result.metricEligibility().stream()
            .filter(m -> "RSK-01".equals(m.metricCode())).findFirst().orElseThrow();

        assertEquals("ELIGIBLE", ret02.eligibilityState(), "Discrete return needs only 2 observations");
        assertEquals("INSUFFICIENT_HISTORY", ret03.eligibilityState(), "RET-03 needs 700 trading days");
        assertEquals("INSUFFICIENT_HISTORY", rsk01.eligibilityState(), "RSK-01 needs 700 trading days");
    }

    @Test
    @DisplayName("Answers to 7 investor due diligence questions are grounded in verified facts")
    void testSevenQuestionsAnswersGroundedInFacts() {
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate navDate = LocalDate.of(2024, 1, 15);
        NavObservation nav1 = new NavObservation(testOption, LocalDate.of(2024, 1, 1), new BigDecimal("1630.7330"), 1, now);
        NavObservation nav2 = new NavObservation(testOption, navDate, new BigDecimal("1670.6720"), 1, now);

        LocalDate repDate = LocalDate.of(2024, 1, 31);
        OffsetDateTime availTime = OffsetDateTime.of(2024, 2, 10, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(1L);
        snap.setPortfolioDate(repDate);
        snap.setAvailabilityTime(availTime);
        snap.setReportedHoldingsCount(10);
        snap.setSumReportedWeights(new BigDecimal("0.548200"));

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(testOption));
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(any(), any(), any()))
            .thenReturn(List.of(nav1, nav2));
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(snap));

        DataQualityFreshnessDto result = freshnessService.evaluateQualityAndFreshness(1L, LocalDate.of(2024, 1, 15), OffsetDateTime.now());

        SevenQuestionsAnswerDto q = result.sevenQuestions();
        assertNotNull(q);
        assertTrue(q.q1LatestNavObservation().contains("2024-01-15"));
        assertTrue(q.q2LatestHoldingsReportingDate().contains("2024-01-31"));
        assertTrue(q.q3HoldingsSourceAvailabilityDate().contains("2024-02-10"));
        assertTrue(q.q4IsDataCurrentRelativeToCadence().contains("Daily") && q.q4IsDataCurrentRelativeToCadence().contains("Monthly"));
        assertTrue(q.q5CompletenessOfDisclosedHoldings().contains("54.82%"));
        assertTrue(q.q6MetricCalculabilityStatus().contains("RET-02"));
        assertTrue(q.q7EvidenceOrLimitationsToInvestigate().contains("Holdings drift requires >= 2 snapshots"));
    }
}
