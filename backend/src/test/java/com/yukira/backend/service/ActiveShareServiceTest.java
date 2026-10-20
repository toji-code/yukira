package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.ActiveShareObservationDto;
import com.yukira.backend.dto.portfolio.ActiveShareTrajectoryResponseDto;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.portfolio.ActiveShareService;
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
class ActiveShareServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private PortfolioSnapshotRepository portfolioSnapshotRepository;

    @Mock
    private PortfolioHoldingRepository portfolioHoldingRepository;

    @Mock
    private SecurityIdentifierRepository securityIdentifierRepository;

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private BenchmarkConstituentRepository benchmarkConstituentRepository;

    @Mock
    private SecurityMarketCapClassificationRepository classificationRepository;

    @Mock
    private NavObservationRepository navObservationRepository;

    @Mock
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Mock
    private BenchmarkResolutionService benchmarkResolutionService;

    private ActiveShareService activeShareService;

    private SchemeOption schemeOption;
    private Benchmark benchmark;

    @BeforeEach
    void setUp() {
        activeShareService = new ActiveShareService(
            schemeOptionRepository,
            portfolioSnapshotRepository,
            portfolioHoldingRepository,
            securityIdentifierRepository,
            benchmarkRepository,
            benchmarkConstituentRepository,
            classificationRepository,
            navObservationRepository,
            benchmarkObservationRepository,
            benchmarkResolutionService
        );

        Scheme scheme = new Scheme();
        scheme.setId(10L);
        scheme.setName("HDFC Flexi Cap Fund");

        SchemePlan plan = new SchemePlan();
        plan.setId(20L);
        plan.setScheme(scheme);
        plan.setPlanType("DIRECT");

        schemeOption = new SchemeOption();
        schemeOption.setId(1L);
        schemeOption.setPlan(plan);
        schemeOption.setOptionType("GROWTH");

        benchmark = new Benchmark("NIFTY_500_TRI", "NIFTY 500 Total Returns Index", "NSE_INDICES", "TRI");
        benchmark.setId(100L);

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(schemeOption));
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(benchmark));
        when(benchmarkResolutionService.resolvePrimaryBenchmark(eq(10L), any(LocalDate.class)))
            .thenReturn(new BenchmarkResolutionService.BenchmarkResolutionResult(
                benchmark,
                true,
                "SCHEME_MASTER",
                LocalDate.of(2026, 1, 1),
                "Canonical Primary Benchmark"
            ));
    }

    @Test
    @DisplayName("Mathematical kernel: Identical portfolios produce 0.00% Active Share")
    void testPureActiveShare_IdenticalPortfolios() {
        Map<String, BigDecimal> p = Map.of(
            "INE001A01036", new BigDecimal("60.00"),
            "INE002A01018", new BigDecimal("40.00")
        );
        Map<String, BigDecimal> b = Map.of(
            "INE001A01036", new BigDecimal("60.00"),
            "INE002A01018", new BigDecimal("40.00")
        );

        BigDecimal as = ActiveShareService.calculatePureActiveShare(p, b);
        assertThat(as).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Mathematical kernel: Completely disjoint portfolios produce 100.00% Active Share")
    void testPureActiveShare_DisjointPortfolios() {
        Map<String, BigDecimal> p = Map.of(
            "INE001A01036", new BigDecimal("100.00")
        );
        Map<String, BigDecimal> b = Map.of(
            "INE002A01018", new BigDecimal("100.00")
        );

        BigDecimal as = ActiveShareService.calculatePureActiveShare(p, b);
        assertThat(as).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Mathematical kernel: Partial overlap calculates hand-verified Active Share (40.00%)")
    void testPureActiveShare_PartialOverlap() {
        // Portfolio: A: 50%, B: 30%, C: 20%
        // Benchmark: A: 30%, B: 50%, D: 20%
        // Diff: |50-30| + |30-50| + |20-0| + |0-20| = 20 + 20 + 20 + 20 = 80
        // Active Share = 0.5 * 80 = 40.00%
        Map<String, BigDecimal> p = Map.of(
            "A", new BigDecimal("50.00"),
            "B", new BigDecimal("30.00"),
            "C", new BigDecimal("20.00")
        );
        Map<String, BigDecimal> b = Map.of(
            "A", new BigDecimal("30.00"),
            "B", new BigDecimal("50.00"),
            "D", new BigDecimal("20.00")
        );

        BigDecimal as = ActiveShareService.calculatePureActiveShare(p, b);
        assertThat(as).isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    @DisplayName("Mathematical kernel: Verified lower bound computation with incomplete benchmark weights")
    void testActiveShareLowerBound_IncompleteWeights() {
        Map<String, BigDecimal> p = Map.of(
            "IN_BM_DISCLOSED", new BigDecimal("25.00"),
            "IN_BM_UNWEIGHTED", new BigDecimal("35.00"),
            "OFF_BM", new BigDecimal("40.00")
        );
        Map<String, BigDecimal> bDisclosed = Map.of(
            "IN_BM_DISCLOSED", new BigDecimal("10.00")
        );
        Set<String> bmIsins = Set.of("IN_BM_DISCLOSED", "IN_BM_UNWEIGHTED");

        // Off-BM contributes: 40.00
        // In-BM disclosed contributes: |25.00 - 10.00| = 15.00
        // Lower bound = 0.5 * (40.00 + 15.00) = 27.50%
        BigDecimal lb = ActiveShareService.calculateActiveShareLowerBound(p, bDisclosed, bmIsins);
        assertThat(lb).isEqualByComparingTo(new BigDecimal("27.50"));
    }

    @Test
    @DisplayName("Mandatory Completeness: Partially disclosed benchmark weights gate Active Share")
    void testIncompleteBenchmarkWeights_GatedActiveShare() {
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate date = LocalDate.of(2026, 9, 30);

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(101L);
        snap.setPortfolioDate(date);
        snap.setAvailabilityTime(cutoff.minusDays(1));
        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap));

        Security s1 = new Security(); s1.setId(1L); s1.setCanonicalName("HDFC Bank");
        Security s2 = new Security(); s2.setId(2L); s2.setCanonicalName("Reliance");

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setSecurity(s1);
        h1.setReportedWeight(new BigDecimal("0.5000"));

        PortfolioHolding h2 = new PortfolioHolding();
        h2.setSecurity(s2);
        h2.setReportedWeight(new BigDecimal("0.5000"));

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1, h2));

        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(1L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(s1, "ISIN", "INE040A01034", LocalDate.of(2020, 1, 1))));
        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(2L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(s2, "ISIN", "INE002A01018", LocalDate.of(2020, 1, 1))));

        // Benchmark constituent list has members, but only top-10 have weights (summing to ~32.22%)
        BenchmarkConstituent bc1 = new BenchmarkConstituent();
        bc1.setBenchmark(benchmark);
        bc1.setIsin("INE040A01034");
        bc1.setCompanyName("HDFC Bank");
        bc1.setEffectiveDate(date);
        bc1.setWeight(new BigDecimal("12.50"));
        bc1.setAvailabilityTime(cutoff.minusDays(2));

        BenchmarkConstituent bc2 = new BenchmarkConstituent();
        bc2.setBenchmark(benchmark);
        bc2.setIsin("INE002A01018");
        bc2.setCompanyName("Reliance");
        bc2.setEffectiveDate(date);
        bc2.setWeight(null); // Unweighted constituent!
        bc2.setAvailabilityTime(cutoff.minusDays(2));

        when(benchmarkConstituentRepository.findEligibleConstituents(100L, date, cutoff))
            .thenReturn(List.of(bc1, bc2));

        ActiveShareTrajectoryResponseDto response = activeShareService.getActiveShareTrajectory(
            1L, date, null, null, cutoff
        );

        assertThat(response.overallStatus()).isEqualTo("GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
        assertThat(response.observations()).hasSize(1);

        ActiveShareObservationDto obs = response.observations().get(0);
        assertThat(obs.status()).isEqualTo("GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
        assertThat(obs.activeSharePct()).isNull(); // Strictly null! No fake calculation
        assertThat(obs.activeShareLowerBoundPct()).isNotNull();
        assertThat(obs.benchmarkWeightCoveragePct()).isLessThan(new BigDecimal("99.50"));
        assertThat(response.missingDataRequirements()).anyMatch(r -> r.contains("weights"));
    }

    @Test
    @DisplayName("PIT enforcement: Future snapshots after knowledge cutoff are excluded")
    void testPitEnforcement_FutureSnapshotExcluded() {
        OffsetDateTime historicalCutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate historicalDate = LocalDate.of(2024, 1, 15);

        // When repository is queried with historical cutoff, only pre-cutoff snapshots returned
        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, historicalCutoff))
            .thenReturn(Collections.emptyList());

        ActiveShareTrajectoryResponseDto response = activeShareService.getActiveShareTrajectory(
            1L, historicalDate, null, null, historicalCutoff
        );

        assertThat(response.overallStatus()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(response.eligibleObservationsCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Temporal compatibility: Benchmark snapshot > 45 days gap flags UNVERIFIED_SOURCE")
    void testTemporalMismatch_FlagsUnverifiedSource() {
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate fundDate = LocalDate.of(2026, 9, 30);
        LocalDate oldBmDate = LocalDate.of(2026, 6, 15); // > 100 days gap

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(101L);
        snap.setPortfolioDate(fundDate);
        snap.setAvailabilityTime(cutoff.minusDays(1));
        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap));

        Security s1 = new Security(); s1.setId(1L); s1.setCanonicalName("HDFC Bank");
        PortfolioHolding h1 = new PortfolioHolding();
        h1.setSecurity(s1);
        h1.setReportedWeight(new BigDecimal("1.0000"));

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1));
        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(1L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(s1, "ISIN", "INE040A01034", LocalDate.of(2020, 1, 1))));

        BenchmarkConstituent bc = new BenchmarkConstituent();
        bc.setBenchmark(benchmark);
        bc.setIsin("INE040A01034");
        bc.setCompanyName("HDFC Bank");
        bc.setEffectiveDate(oldBmDate);
        bc.setWeight(new BigDecimal("100.00"));
        bc.setAvailabilityTime(cutoff.minusDays(50));

        when(benchmarkConstituentRepository.findEligibleConstituents(100L, fundDate, cutoff))
            .thenReturn(List.of(bc));

        ActiveShareTrajectoryResponseDto response = activeShareService.getActiveShareTrajectory(
            1L, fundDate, null, null, cutoff
        );

        assertThat(response.observations()).hasSize(1);
        ActiveShareObservationDto obs = response.observations().get(0);
        assertThat(obs.isTemporallyComparable()).isFalse();
        assertThat(obs.status()).isEqualTo("UNVERIFIED_SOURCE");
        assertThat(obs.statusReason()).contains("Temporal mismatch");
    }

    @Test
    @DisplayName("Apparent changes: Categorizes weight changes, index reconstitution, and classification shifts")
    void testApparentChanges_FiveCategoriesSeparation() {
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate d1 = LocalDate.of(2026, 8, 31);
        LocalDate d2 = LocalDate.of(2026, 9, 30);

        PortfolioSnapshot snap1 = new PortfolioSnapshot();
        snap1.setId(101L);
        snap1.setPortfolioDate(d1);
        snap1.setAvailabilityTime(cutoff.minusDays(30));

        PortfolioSnapshot snap2 = new PortfolioSnapshot();
        snap2.setId(102L);
        snap2.setPortfolioDate(d2);
        snap2.setAvailabilityTime(cutoff.minusDays(1));

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap1, snap2));

        Security s1 = new Security(); s1.setId(1L); s1.setCanonicalName("Stock Alpha");
        Security s2 = new Security(); s2.setId(2L); s2.setCanonicalName("Stock Beta");

        // Period 1 holdings: Stock Alpha = 5%, Stock Beta = 0%
        PortfolioHolding h1_d1 = new PortfolioHolding();
        h1_d1.setSecurity(s1);
        h1_d1.setReportedWeight(new BigDecimal("0.0500"));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1_d1));

        // Period 2 holdings: Stock Alpha = 8% (+3%), Stock Beta = 2% (new)
        PortfolioHolding h1_d2 = new PortfolioHolding();
        h1_d2.setSecurity(s1);
        h1_d2.setReportedWeight(new BigDecimal("0.0800"));
        PortfolioHolding h2_d2 = new PortfolioHolding();
        h2_d2.setSecurity(s2);
        h2_d2.setReportedWeight(new BigDecimal("0.0200"));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(102L))
            .thenReturn(List.of(h1_d2, h2_d2));

        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(1L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(s1, "ISIN", "INE001A01001", LocalDate.of(2020, 1, 1))));
        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(2L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(s2, "ISIN", "INE002A01002", LocalDate.of(2020, 1, 1))));

        // Benchmark: Alpha is in BM on d1 and d2. Beta is absent on d1, enters on d2.
        BenchmarkConstituent bc1_d1 = new BenchmarkConstituent();
        bc1_d1.setBenchmark(benchmark);
        bc1_d1.setIsin("INE001A01001");
        bc1_d1.setCompanyName("Stock Alpha");
        bc1_d1.setEffectiveDate(d1);
        bc1_d1.setWeight(new BigDecimal("100.00"));
        when(benchmarkConstituentRepository.findEligibleConstituents(100L, d1, cutoff)).thenReturn(List.of(bc1_d1));

        BenchmarkConstituent bc1_d2 = new BenchmarkConstituent();
        bc1_d2.setBenchmark(benchmark);
        bc1_d2.setIsin("INE001A01001");
        bc1_d2.setCompanyName("Stock Alpha");
        bc1_d2.setEffectiveDate(d2);
        bc1_d2.setWeight(new BigDecimal("98.00"));

        BenchmarkConstituent bc2_d2 = new BenchmarkConstituent();
        bc2_d2.setBenchmark(benchmark);
        bc2_d2.setIsin("INE002A01002");
        bc2_d2.setCompanyName("Stock Beta");
        bc2_d2.setEffectiveDate(d2);
        bc2_d2.setWeight(new BigDecimal("2.00"));
        when(benchmarkConstituentRepository.findEligibleConstituents(100L, d2, cutoff)).thenReturn(List.of(bc1_d2, bc2_d2));

        // Classification change: Stock Alpha shifts from MID_CAP to LARGE_CAP
        SecurityMarketCapClassification c1 = new SecurityMarketCapClassification();
        c1.setMarketCapCategory("MID_CAP");
        when(classificationRepository.findByIsinAndPit("INE001A01001", d1, cutoff)).thenReturn(List.of(c1));

        SecurityMarketCapClassification c2 = new SecurityMarketCapClassification();
        c2.setMarketCapCategory("LARGE_CAP");
        when(classificationRepository.findByIsinAndPit("INE001A01001", d2, cutoff)).thenReturn(List.of(c2));

        ActiveShareTrajectoryResponseDto response = activeShareService.getActiveShareTrajectory(
            1L, d2, null, null, cutoff
        );

        assertThat(response.apparentChanges()).isNotEmpty();

        boolean hasPortfolioWeightChange = response.apparentChanges().stream()
            .anyMatch(c -> "PORTFOLIO_WEIGHT_CHANGE".equals(c.changeCategory()));
        boolean hasBenchmarkMembershipChange = response.apparentChanges().stream()
            .anyMatch(c -> "BENCHMARK_MEMBERSHIP_CHANGE".equals(c.changeCategory()));
        boolean hasClassificationChange = response.apparentChanges().stream()
            .anyMatch(c -> "CLASSIFICATION_CHANGE".equals(c.changeCategory()));

        assertThat(hasPortfolioWeightChange).isTrue();
        assertThat(hasBenchmarkMembershipChange).isTrue();
        assertThat(hasClassificationChange).isTrue();

        // Check non-causal warnings exist on changes
        response.apparentChanges().forEach(c -> assertThat(c.epistemicCaution()).isNotEmpty());
    }

    @Test
    @DisplayName("Descriptive return association: Emits non-causal label and caveats")
    void testReturnAssociation_NonCausalLabel() {
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        LocalDate d1 = LocalDate.of(2026, 8, 31);
        LocalDate d2 = LocalDate.of(2026, 9, 30);

        PortfolioSnapshot snap1 = new PortfolioSnapshot(); snap1.setId(101L); snap1.setPortfolioDate(d1); snap1.setAvailabilityTime(cutoff.minusDays(30));
        PortfolioSnapshot snap2 = new PortfolioSnapshot(); snap2.setId(102L); snap2.setPortfolioDate(d2); snap2.setAvailabilityTime(cutoff.minusDays(1));

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap1, snap2));

        // Mock NAVs: d1 = 100.0, d2 = 105.0 (+5%)
        NavObservation n1 = new NavObservation(); n1.setEffectiveDate(d1); n1.setNavValue(new BigDecimal("100.00"));
        NavObservation n2 = new NavObservation(); n2.setEffectiveDate(d2); n2.setNavValue(new BigDecimal("105.00"));
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(1L, d2, cutoff))
            .thenReturn(List.of(n1, n2));

        // Mock Benchmark Index Levels: d1 = 20000, d2 = 20400 (+2%)
        BenchmarkObservation b1 = new BenchmarkObservation(); b1.setEffectiveDate(d1); b1.setIndexLevel(new BigDecimal("20000.00"));
        BenchmarkObservation b2 = new BenchmarkObservation(); b2.setEffectiveDate(d2); b2.setIndexLevel(new BigDecimal("20400.00"));
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(100L, d2, cutoff))
            .thenReturn(List.of(b1, b2));

        ActiveShareTrajectoryResponseDto response = activeShareService.getActiveShareTrajectory(
            1L, d2, null, null, cutoff
        );

        assertThat(response.returnAssociations()).hasSize(1);
        var assoc = response.returnAssociations().get(0);
        assertThat(assoc.fundReturnPct()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(assoc.benchmarkReturnPct()).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(assoc.activeReturnPct()).isEqualByComparingTo(new BigDecimal("3.00"));
        assertThat(assoc.associationLabel()).isEqualTo("DESCRIPTIVE_ASSOCIATION_ONLY");
        assertThat(assoc.methodologicalCaveat()).contains("Non-causal");
    }
}
