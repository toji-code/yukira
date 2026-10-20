package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.longevity.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.portfolio.HoldingLongevityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HoldingLongevityServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private PortfolioSnapshotRepository portfolioSnapshotRepository;

    @Mock
    private PortfolioHoldingRepository portfolioHoldingRepository;

    @Mock
    private SecurityIdentifierRepository securityIdentifierRepository;

    private HoldingLongevityService holdingLongevityService;

    private SchemeOption schemeOption;

    @BeforeEach
    void setUp() {
        holdingLongevityService = new HoldingLongevityService(
            schemeOptionRepository,
            portfolioSnapshotRepository,
            portfolioHoldingRepository,
            securityIdentifierRepository
        );

        Amc amc = new Amc();
        amc.setCode("HDFC_MF");
        amc.setLegalName("HDFC Mutual Fund");

        Scheme scheme = new Scheme();
        scheme.setId(10L);
        scheme.setCode("HDFC_FLEXI");
        scheme.setName("HDFC Flexi Cap Fund");
        scheme.setAmc(amc);

        SchemePlan plan = new SchemePlan();
        plan.setScheme(scheme);
        plan.setPlanType("DIRECT");

        schemeOption = new SchemeOption();
        schemeOption.setId(1L);
        schemeOption.setPlan(plan);
        schemeOption.setOptionType("GROWTH");
        schemeOption.setAmfiCode("118955");
        schemeOption.setIsin("INF179K01UT0");

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(schemeOption));
    }

    private PortfolioSnapshot createSnapshot(Long id, LocalDate date, OffsetDateTime avail, BigDecimal sumWeight) {
        PortfolioSnapshot s = new PortfolioSnapshot();
        s.setId(id);
        s.setPortfolioDate(date);
        s.setAvailabilityTime(avail);
        s.setRevisionSeq(1);
        s.setSumReportedWeights(sumWeight);
        s.setReportedHoldingsCount(10);
        return s;
    }

    private PortfolioHolding createHolding(Long id, PortfolioSnapshot snap, Security sec, String isin, BigDecimal weight, int rank) {
        PortfolioHolding h = new PortfolioHolding();
        h.setId(id);
        h.setPortfolioSnapshot(snap);
        h.setSecurity(sec);
        h.setReportedWeight(weight);
        h.setHoldingRank(rank);
        return h;
    }

    private Security createSecurity(Long id, String name) {
        Security s = new Security();
        s.setId(id);
        s.setCanonicalName(name);
        s.setAssetClass("Equity");
        return s;
    }

    @Test
    @DisplayName("Longevity: Tracks holding presence, continuous vs intermittent tenure across chronological snapshots")
    void shouldTrackLongevityAndPresenceAcrossSnapshots() {
        LocalDate d1 = LocalDate.of(2026, 7, 31);
        LocalDate d2 = LocalDate.of(2026, 8, 31);
        LocalDate d3 = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 10, 0, 0, 0, 0, ZoneOffset.UTC);

        PortfolioSnapshot s1 = createSnapshot(101L, d1, OffsetDateTime.of(2026, 8, 5, 0, 0, 0, 0, ZoneOffset.UTC), new BigDecimal("0.9500"));
        PortfolioSnapshot s2 = createSnapshot(102L, d2, OffsetDateTime.of(2026, 9, 5, 0, 0, 0, 0, ZoneOffset.UTC), new BigDecimal("0.9600"));
        PortfolioSnapshot s3 = createSnapshot(103L, d3, OffsetDateTime.of(2026, 10, 5, 0, 0, 0, 0, ZoneOffset.UTC), new BigDecimal("0.9700"));

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(s3, s2, s1));

        Security sIcici = createSecurity(501L, "ICICI Bank Ltd.");
        Security sInfy = createSecurity(502L, "Infosys Ltd.");
        Security sNew = createSecurity(503L, "Zomato Ltd.");
        Security sExit = createSecurity(504L, "Wipro Ltd.");

        // Snapshot 1: ICICI (9%), Infy (4%), Exit (3%)
        List<PortfolioHolding> h1 = List.of(
            createHolding(1L, s1, sIcici, "INE090A01021", new BigDecimal("0.090000"), 1),
            createHolding(2L, s1, sInfy, "INE009A01021", new BigDecimal("0.040000"), 2),
            createHolding(3L, s1, sExit, "INE075A01022", new BigDecimal("0.030000"), 3)
        );
        // Snapshot 2: ICICI (9.5%), Exit (2.5%) (Infy absent)
        List<PortfolioHolding> h2 = List.of(
            createHolding(4L, s2, sIcici, "INE090A01021", new BigDecimal("0.095000"), 1),
            createHolding(5L, s2, sExit, "INE075A01022", new BigDecimal("0.025000"), 2)
        );
        // Snapshot 3: ICICI (10%), Infy (4.5%), New (2%) (Exit absent)
        List<PortfolioHolding> h3 = List.of(
            createHolding(6L, s3, sIcici, "INE090A01021", new BigDecimal("0.100000"), 1),
            createHolding(7L, s3, sInfy, "INE009A01021", new BigDecimal("0.045000"), 2),
            createHolding(8L, s3, sNew, "INE758T01015", new BigDecimal("0.020000"), 3)
        );

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L)).thenReturn(h1);
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(102L)).thenReturn(h2);
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(103L)).thenReturn(h3);

        HoldingLongevityResponseDto response = holdingLongevityService.getHoldingLongevityTrajectory(1L, d3, null, null, cutoff);

        assertThat(response).isNotNull();
        assertThat(response.analysisStatus()).isEqualTo("SUCCESS");
        assertThat(response.eligibleSnapshotsCount()).isEqualTo(3);

        // Find ICICI Bank: Continuous & Persistent
        PositionLongevityItemDto icici = response.allHoldings().stream()
            .filter(item -> "ICICI Bank Ltd.".equals(item.canonicalName()))
            .findFirst().orElseThrow();
        assertThat(icici.appearanceCount()).isEqualTo(3);
        assertThat(icici.spanObservationsCount()).isEqualTo(3);
        assertThat(icici.presenceStatus()).isEqualTo("CONTINUOUS");
        assertThat(icici.persistenceCategory()).isEqualTo("PERSISTENT");
        assertThat(icici.firstObservedDate()).isEqualTo(d1);
        assertThat(icici.lastObservedDate()).isEqualTo(d3);
        assertThat(icici.consecutiveObservationsCount()).isEqualTo(3);
        assertThat(icici.latestReportedWeightPct()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(icici.averageObservedWeightPct()).isEqualByComparingTo(new BigDecimal("9.50"));

        // Find Infosys: Intermittent (present M1, M3; absent M2)
        PositionLongevityItemDto infy = response.allHoldings().stream()
            .filter(item -> "Infosys Ltd.".equals(item.canonicalName()))
            .findFirst().orElseThrow();
        assertThat(infy.appearanceCount()).isEqualTo(2);
        assertThat(infy.spanObservationsCount()).isEqualTo(3);
        assertThat(infy.presenceStatus()).isEqualTo("INTERMITTENT");
        assertThat(infy.persistenceCategory()).isEqualTo("INTERMITTENT");
        assertThat(infy.consecutiveObservationsCount()).isEqualTo(1);

        // Find Zomato: Newly Observed (present only in M3)
        PositionLongevityItemDto zomato = response.allHoldings().stream()
            .filter(item -> "Zomato Ltd.".equals(item.canonicalName()))
            .findFirst().orElseThrow();
        assertThat(zomato.appearanceCount()).isEqualTo(1);
        assertThat(zomato.presenceStatus()).isEqualTo("NEWLY_OBSERVED");
        assertThat(zomato.persistenceCategory()).isEqualTo("NEWLY_OBSERVED");

        // Find Wipro: Potential Exit (present M1, M2; absent M3)
        PositionLongevityItemDto wipro = response.allHoldings().stream()
            .filter(item -> "Wipro Ltd.".equals(item.canonicalName()))
            .findFirst().orElseThrow();
        assertThat(wipro.appearanceCount()).isEqualTo(2);
        assertThat(wipro.presenceStatus()).isEqualTo("POTENTIAL_EXIT");
        assertThat(wipro.persistenceCategory()).isEqualTo("POTENTIAL_EXIT");
        assertThat(wipro.lastObservedDate()).isEqualTo(d2);
    }

    @Test
    @DisplayName("Scope Shift: Disappearance from partial disclosure is classified as NOT_OBSERVED_IN_PARTIAL_DISCLOSURE, never as an exit")
    void shouldDistinguishPartialDisclosureDisappearanceFromGenuineExit() {
        LocalDate d1 = LocalDate.of(2024, 1, 15);
        LocalDate d2 = LocalDate.of(2024, 1, 31);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 2, 10, 0, 0, 0, 0, ZoneOffset.UTC);

        // Snapshot 1 is full disclosure (95%), Snapshot 2 is partial top-holdings (50%)
        PortfolioSnapshot s1 = createSnapshot(101L, d1, OffsetDateTime.of(2024, 1, 16, 0, 0, 0, 0, ZoneOffset.UTC), new BigDecimal("0.9500"));
        PortfolioSnapshot s2 = createSnapshot(102L, d2, OffsetDateTime.of(2024, 2, 5, 0, 0, 0, 0, ZoneOffset.UTC), new BigDecimal("0.5082"));

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(s2, s1));

        Security sMidcap = createSecurity(601L, "Persistent Systems Ltd.");
        Security sLarge = createSecurity(602L, "ICICI Bank Ltd.");

        // In S1 (Full), Persistent Systems is holding rank 25 (0.80%)
        List<PortfolioHolding> h1 = List.of(
            createHolding(1L, s1, sLarge, "INE090A01021", new BigDecimal("0.090000"), 1),
            createHolding(2L, s1, sMidcap, "INE262H01013", new BigDecimal("0.008000"), 25)
        );
        // In S2 (Partial top 10), Persistent Systems is omitted because only top 10 are reported
        List<PortfolioHolding> h2 = List.of(
            createHolding(3L, s2, sLarge, "INE090A01021", new BigDecimal("0.092000"), 1)
        );

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L)).thenReturn(h1);
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(102L)).thenReturn(h2);

        HoldingLongevityResponseDto response = holdingLongevityService.getHoldingLongevityTrajectory(1L, d2, null, null, cutoff);

        PositionLongevityItemDto item = response.allHoldings().stream()
            .filter(h -> "Persistent Systems Ltd.".equals(h.canonicalName()))
            .findFirst().orElseThrow();

        // Must NOT be classified as POTENTIAL_EXIT! Must be NOT_OBSERVED_IN_PARTIAL_DISCLOSURE
        assertThat(item.presenceStatus()).isEqualTo("NOT_OBSERVED_IN_PARTIAL_DISCLOSURE");
        assertThat(item.persistenceCategory()).isEqualTo("NOT_OBSERVED_IN_PARTIAL_DISCLOSURE");
    }

    @Test
    @DisplayName("Multi-Lot Merging: Aggregates multiple lots of identical security into single position")
    void shouldMergeMultiLotHoldingsForSameSecurity() {
        LocalDate d1 = LocalDate.of(2026, 8, 31);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 9, 10, 0, 0, 0, 0, ZoneOffset.UTC);

        PortfolioSnapshot s1 = createSnapshot(101L, d1, OffsetDateTime.of(2026, 9, 5, 0, 0, 0, 0, ZoneOffset.UTC), new BigDecimal("0.9800"));
        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(s1));

        Security sCash = createSecurity(701L, "TREPS / Reverse Repo");

        // Two lots of TREPS (2.10% and 1.40%)
        List<PortfolioHolding> h1 = List.of(
            createHolding(1L, s1, sCash, null, new BigDecimal("0.021000"), 50),
            createHolding(2L, s1, sCash, null, new BigDecimal("0.014000"), 51)
        );

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L)).thenReturn(h1);

        HoldingLongevityResponseDto response = holdingLongevityService.getHoldingLongevityTrajectory(1L, d1, null, null, cutoff);

        assertThat(response.allHoldings()).hasSize(1);
        PositionLongevityItemDto cash = response.allHoldings().get(0);
        assertThat(cash.canonicalName()).isEqualTo("TREPS / Reverse Repo");
        // Merged weight must be 2.10% + 1.40% = 3.50%
        assertThat(cash.latestReportedWeightPct()).isEqualByComparingTo(new BigDecimal("3.50"));
    }

    @Test
    @DisplayName("Composition Change: Calculates 1/2 * sum(|w_t - w_{t-1}|) accurately against hand-worked example")
    void shouldCalculateCompositionChangeHalfSumAndReconcile() {
        LocalDate d1 = LocalDate.of(2026, 7, 31);
        LocalDate d2 = LocalDate.of(2026, 8, 31);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 9, 10, 0, 0, 0, 0, ZoneOffset.UTC);

        PortfolioSnapshot s1 = createSnapshot(101L, d1, OffsetDateTime.of(2026, 8, 5, 0, 0, 0, 0, ZoneOffset.UTC), new BigDecimal("1.0000"));
        PortfolioSnapshot s2 = createSnapshot(102L, d2, OffsetDateTime.of(2026, 9, 5, 0, 0, 0, 0, ZoneOffset.UTC), new BigDecimal("1.0000"));

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(s2, s1));

        Security sA = createSecurity(1L, "Holding A");
        Security sB = createSecurity(2L, "Holding B");
        Security sC = createSecurity(3L, "Holding C");

        // S1: A=60%, B=40%
        List<PortfolioHolding> h1 = List.of(
            createHolding(1L, s1, sA, "INE001A01011", new BigDecimal("0.600000"), 1),
            createHolding(2L, s1, sB, "INE002A01012", new BigDecimal("0.400000"), 2)
        );

        // S2: A=50% (-10%), B=30% (-10%), C=20% (+20% new entry)
        List<PortfolioHolding> h2 = List.of(
            createHolding(3L, s2, sA, "INE001A01011", new BigDecimal("0.500000"), 1),
            createHolding(4L, s2, sB, "INE002A01012", new BigDecimal("0.300000"), 2),
            createHolding(5L, s2, sC, "INE003A01013", new BigDecimal("0.200000"), 3)
        );

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L)).thenReturn(h1);
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(102L)).thenReturn(h2);

        HoldingLongevityResponseDto response = holdingLongevityService.getHoldingLongevityTrajectory(1L, d2, null, null, cutoff);

        assertThat(response.transitions()).hasSize(1);
        CompositionChangeTransitionDto t = response.transitions().get(0);

        assertThat(t.isComparable()).isTrue();
        assertThat(t.comparabilityStatus()).isEqualTo("COMPARABLE_FULL_DISCLOSURE");
        assertThat(t.newEntriesCount()).isEqualTo(1);
        assertThat(t.newEntriesWeightPct()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(t.completeExitsCount()).isEqualTo(0);
        assertThat(t.continuingPositionsCount()).isEqualTo(2);

        // Hand calculation:
        // |50 - 60| = 10%
        // |30 - 40| = 10%
        // |20 - 0| = 20%
        // Sum absolute delta = 40.00%
        // Half sum = 20.00%
        assertThat(t.sumAbsWeightChangesPct()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(t.halfSumCompositionChangePct()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(t.unresolvedDiscrepancyPct()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Turnover Gating: Gated strictly with TURNOVER_UNAVAILABLE_TRANSACTION_DATA_MISSING when transaction data absent")
    void shouldStrictlyGateTurnoverWhenTransactionDataMissing() {
        LocalDate d1 = LocalDate.of(2026, 8, 31);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 9, 10, 0, 0, 0, 0, ZoneOffset.UTC);

        PortfolioSnapshot s1 = createSnapshot(101L, d1, OffsetDateTime.of(2026, 9, 5, 0, 0, 0, 0, ZoneOffset.UTC), new BigDecimal("0.9800"));
        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(s1));

        Security sA = createSecurity(1L, "Holding A");
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(createHolding(1L, s1, sA, "INE001A01011", new BigDecimal("0.100000"), 1)));

        HoldingLongevityResponseDto response = holdingLongevityService.getHoldingLongevityTrajectory(1L, d1, null, null, cutoff);

        TurnoverEvaluationDto turnover = response.turnoverEvaluation();
        assertThat(turnover.turnoverStatus()).isEqualTo("TURNOVER_UNAVAILABLE_TRANSACTION_DATA_MISSING");
        assertThat(turnover.isTurnoverCalculated()).isFalse();
        assertThat(turnover.reportedRegulatoryTurnoverRatioPct()).isNull();
        assertThat(turnover.descriptiveChangeFormula()).contains("1/2 * SUM(|w_{i,t} - w_{i,t-1}|)");
    }

    @Test
    @DisplayName("Dual PIT: Excludes snapshots published after requested knowledge cutoff")
    void shouldStrictlyEnforcePointInTimeKnowledgeCutoff() {
        LocalDate d1 = LocalDate.of(2026, 7, 31);
        LocalDate d2 = LocalDate.of(2026, 8, 31);
        OffsetDateTime earlyCutoff = OffsetDateTime.of(2026, 8, 10, 0, 0, 0, 0, ZoneOffset.UTC);

        // s2 was published on Sept 5, 2026 -> must be excluded under earlyCutoff (Aug 10, 2026)
        PortfolioSnapshot s1 = createSnapshot(101L, d1, OffsetDateTime.of(2026, 8, 5, 0, 0, 0, 0, ZoneOffset.UTC), new BigDecimal("0.9800"));

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), eq(earlyCutoff)))
            .thenReturn(List.of(s1));

        Security sA = createSecurity(1L, "Holding A");
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(createHolding(1L, s1, sA, "INE001A01011", new BigDecimal("0.100000"), 1)));

        HoldingLongevityResponseDto response = holdingLongevityService.getHoldingLongevityTrajectory(1L, d2, null, null, earlyCutoff);

        assertThat(response.eligibleSnapshotsCount()).isEqualTo(1);
        assertThat(response.snapshots().get(0).portfolioDate()).isEqualTo(d1);
    }
}
