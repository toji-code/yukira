package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.ActiveReturnAttributionResponseDto;
import com.yukira.backend.dto.portfolio.ActiveReturnAttributionResponseDto.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.BenchmarkResolutionService;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActiveReturnAttributionServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private NavObservationRepository navObservationRepository;

    @Mock
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Mock
    private RiskFreeObservationRepository riskFreeObservationRepository;

    @Mock
    private PortfolioSnapshotRepository portfolioSnapshotRepository;

    @Mock
    private PortfolioHoldingRepository portfolioHoldingRepository;

    @Mock
    private SecurityMarketCapClassificationRepository classificationRepository;

    @Mock
    private SecurityIdentifierRepository securityIdentifierRepository;

    @Mock
    private BenchmarkResolutionService benchmarkResolutionService;

    @Mock
    private BenchmarkConstituentRepository benchmarkConstituentRepository;

    @Mock
    private BenchmarkRepository benchmarkRepository;

    private ActiveReturnAttributionService attributionService;

    private SchemeOption canonicalOption;
    private Benchmark canonicalBenchmark;

    @BeforeEach
    void setUp() {
        attributionService = new ActiveReturnAttributionService(
            schemeOptionRepository,
            navObservationRepository,
            benchmarkObservationRepository,
            riskFreeObservationRepository,
            portfolioSnapshotRepository,
            portfolioHoldingRepository,
            classificationRepository,
            securityIdentifierRepository,
            benchmarkResolutionService,
            benchmarkConstituentRepository,
            benchmarkRepository
        );

        Scheme scheme = new Scheme();
        scheme.setId(10L);
        scheme.setName("HDFC Flexi Cap Fund");
        scheme.setCode("HDFC_FLEXI");

        SchemePlan plan = new SchemePlan();
        plan.setScheme(scheme);
        plan.setPlanType("DIRECT");

        canonicalOption = new SchemeOption();
        canonicalOption.setId(1L);
        canonicalOption.setPlan(plan);
        canonicalOption.setAmfiCode("118955");
        canonicalOption.setOptionType("GROWTH");

        canonicalBenchmark = new Benchmark();
        canonicalBenchmark.setId(100L);
        canonicalBenchmark.setCode("NIFTY_500_TRI");
        canonicalBenchmark.setName("NIFTY 500 TRI");

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(canonicalOption));
        when(benchmarkResolutionService.resolvePrimaryBenchmark(eq(10L), any()))
            .thenReturn(new BenchmarkResolutionService.BenchmarkResolutionResult(
                canonicalBenchmark, true, "EXPLICIT", LocalDate.of(2020, 1, 1), "Primary benchmark resolved"
            ));
    }

    private NavObservation createNav(LocalDate date, String navStr, OffsetDateTime availTime) {
        NavObservation nav = new NavObservation();
        nav.setEffectiveDate(date);
        nav.setNavValue(new BigDecimal(navStr));
        nav.setAvailabilityTime(availTime);
        return nav;
    }

    private BenchmarkObservation createBench(LocalDate date, String valStr, OffsetDateTime availTime) {
        BenchmarkObservation bench = new BenchmarkObservation();
        bench.setEffectiveDate(date);
        bench.setIndexLevel(new BigDecimal(valStr));
        bench.setAvailabilityTime(availTime);
        return bench;
    }

    @Test
    @DisplayName("Active-return macro decomposition reconciles with exact mathematical precision")
    void testActiveReturnDecompositionReconciliation() {
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 9, 30, 23, 59, 59, 0, ZoneOffset.UTC);
        LocalDate asOfDate = LocalDate.of(2026, 9, 30);

        List<NavObservation> navs = List.of(
            createNav(LocalDate.of(2026, 6, 30), "100.0000", cutoff.minusMonths(3)),
            createNav(LocalDate.of(2026, 7, 31), "102.0000", cutoff.minusMonths(2)),
            createNav(LocalDate.of(2026, 8, 31), "104.0000", cutoff.minusMonths(1)),
            createNav(LocalDate.of(2026, 9, 30), "106.0000", cutoff)
        );
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), any(), any())).thenReturn(navs);

        List<BenchmarkObservation> benches = List.of(
            createBench(LocalDate.of(2026, 6, 30), "1000.0000", cutoff.minusMonths(3)),
            createBench(LocalDate.of(2026, 7, 31), "1015.0000", cutoff.minusMonths(2)),
            createBench(LocalDate.of(2026, 8, 31), "1025.0000", cutoff.minusMonths(1)),
            createBench(LocalDate.of(2026, 9, 30), "1040.0000", cutoff)
        );
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(100L), any(), any())).thenReturn(benches);

        // Portfolio snapshot for cash weight resolution
        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(10L);
        snap.setPortfolioDate(LocalDate.of(2026, 6, 30));
        snap.setAvailabilityTime(cutoff.minusMonths(2));
        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(snap));

        PortfolioHolding equityHolding = new PortfolioHolding();
        Security equitySec = new Security();
        equitySec.setId(101L);
        equitySec.setCanonicalName("RELIANCE INDUSTRIES LTD");
        equitySec.setAssetClass("EQUITY");
        equitySec.setSector("Oil Gas & Consumable Fuels");
        equityHolding.setSecurity(equitySec);
        equityHolding.setReportedWeight(new BigDecimal("95.0000"));

        PortfolioHolding cashHolding = new PortfolioHolding();
        Security cashSec = new Security();
        cashSec.setId(102L);
        cashSec.setCanonicalName("TREPS / REVERSE REPO");
        cashSec.setAssetClass("CASH_AND_EQUIVALENTS");
        cashHolding.setSecurity(cashSec);
        cashHolding.setReportedWeight(new BigDecimal("5.0000"));

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(10L))
            .thenReturn(List.of(equityHolding, cashHolding));

        ActiveReturnAttributionResponseDto response = attributionService.calculateAttribution(1L, asOfDate, cutoff);

        assertThat(response).isNotNull();
        assertThat(response.modelStatus()).isEqualTo("PARTIAL_EVIDENCE_SUPPORTED");
        assertThat(response.fundReturnPct()).isNotNull();
        assertThat(response.benchmarkReturnPct()).isNotNull();
        assertThat(response.activeReturnPct()).isNotNull();

        // Exact reconciliation check
        assertThat(response.macroAttribution()).isNotNull();
        assertThat(response.macroAttribution().isReconciled()).isTrue();
        assertThat(response.macroAttribution().reconciliationToleranceDeltaPct().abs()).isLessThan(new BigDecimal("0.000001"));

        // Verify Macro Components sum up to Active Return
        BigDecimal sumComponents = response.macroAttribution().marketSensitivityContributionPct()
            .add(response.macroAttribution().cashDragContributionPct())
            .add(response.macroAttribution().selectionAndSectorResidualPct());
        assertThat(sumComponents).isEqualByComparingTo(response.macroAttribution().reconciledActiveReturnPct());

        // Verify Brinson Gate transparency
        assertThat(response.brinsonSegmentation()).isNotNull();
        assertThat(response.brinsonSegmentation().executionStatus()).contains("BLOCKED");

        // Verify Data-Readiness Gate has all 8 dependencies
        assertThat(response.dataReadinessGate()).isNotNull();
        assertThat(response.dataReadinessGate().dependencies()).hasSize(8);
        assertThat(response.dataReadinessGate().missingDependencies()).isEqualTo(3);

        // Verify Exposure Timeline has observation
        assertThat(response.exposureTimeline()).isNotEmpty();
        assertThat(response.exposureTimeline().get(0).comparabilityStatus()).isEqualTo("BASELINE_OBSERVATION");

        // Verify investigation prompts
        assertThat(response.investigationPrompts()).isNotEmpty();
        assertThat(response.epistemicLimitations()).isNotEmpty();
    }

    @Test
    @DisplayName("Point-in-Time contract strictly excludes future NAVs and snapshots published after knowledge cutoff")
    void testPointInTimeExclusion() {
        OffsetDateTime pastCutoff = OffsetDateTime.of(2026, 7, 31, 23, 59, 59, 0, ZoneOffset.UTC);
        LocalDate asOfDate = LocalDate.of(2026, 7, 31);

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), any(), eq(pastCutoff)))
            .thenReturn(List.of(
                createNav(LocalDate.of(2026, 6, 30), "100.0000", pastCutoff.minusMonths(1)),
                createNav(LocalDate.of(2026, 7, 31), "102.0000", pastCutoff)
            ));

        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(100L), any(), eq(pastCutoff)))
            .thenReturn(List.of(
                createBench(LocalDate.of(2026, 6, 30), "1000.0000", pastCutoff.minusMonths(1)),
                createBench(LocalDate.of(2026, 7, 31), "1015.0000", pastCutoff)
            ));

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), eq(pastCutoff)))
            .thenReturn(Collections.emptyList());

        ActiveReturnAttributionResponseDto response = attributionService.calculateAttribution(1L, asOfDate, pastCutoff);

        assertThat(response).isNotNull();
        assertThat(response.knowledgeCutoff()).isEqualTo(pastCutoff.toString());
        assertThat(response.modelStatus()).isEqualTo("PARTIAL_EVIDENCE_SUPPORTED");
        assertThat(response.attributionPeriodStart()).isEqualTo("2026-06-30");
        assertThat(response.attributionPeriodEnd()).isEqualTo("2026-07-31");
    }

    @Test
    @DisplayName("Returns INSUFFICIENT_DATA when fewer than two synchronous observations exist")
    void testInsufficientObservations() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), any(), any()))
            .thenReturn(List.of(createNav(LocalDate.of(2026, 9, 30), "100.0000", cutoff)));
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(100L), any(), any()))
            .thenReturn(Collections.emptyList());

        ActiveReturnAttributionResponseDto response = attributionService.calculateAttribution(1L, LocalDate.of(2026, 9, 30), cutoff);

        assertThat(response.modelStatus()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(response.macroAttribution().reconciliationFormula()).isEqualTo("N/A");
    }

    @Test
    @DisplayName("Brinson-Fachler attribution is strictly gated when benchmark weights are incomplete (< 99.50%)")
    void testBrinsonFachlerGatingWithIncompleteBenchmarkWeights() {
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 2, 7, 23, 59, 59, 0, ZoneOffset.UTC);
        LocalDate startDate = LocalDate.of(2024, 1, 31);
        LocalDate endDate = LocalDate.of(2024, 2, 29);

        // Benchmark constituent with only top 10 weights (~40.95% total weight)
        BenchmarkConstituent c1 = new BenchmarkConstituent();
        c1.setIsin("INE040A01034");
        c1.setCompanyName("HDFC Bank Ltd");
        c1.setWeight(new BigDecimal("8.750000"));
        c1.setIsMember(true);

        BenchmarkConstituent c2 = new BenchmarkConstituent();
        c2.setIsin("INE002A01018");
        c2.setCompanyName("Reliance Industries Ltd");
        c2.setWeight(new BigDecimal("7.200000"));
        c2.setIsMember(true);

        // 5 unweighted tail constituents
        BenchmarkConstituent tail1 = new BenchmarkConstituent();
        tail1.setIsin("INE123A01011");
        tail1.setCompanyName("Tail Stock 1");
        tail1.setWeight(null);
        tail1.setIsMember(true);

        when(benchmarkConstituentRepository.findEligibleConstituents(eq(100L), eq(startDate), any()))
            .thenReturn(List.of(c1, c2, tail1));

        BrinsonAttributionSummaryDto result = attributionService.evaluateBrinsonFachler(
            100L, startDate, endDate, cutoff,
            new BigDecimal("0.0250"), new BigDecimal("0.0150"), new BigDecimal("0.0100")
        );

        assertThat(result.executionStatus()).isEqualTo("BLOCKED_BENCHMARK_WEIGHTS_INCOMPLETE");
        assertThat(result.statusExplanation()).contains("partially disclosed");
        assertThat(result.statusExplanation()).contains("15.95%");
        assertThat(result.reasonIfNotCalculated()).contains("threshold");
        assertThat(result.isReconciled()).isFalse();
        assertThat(result.missingPrerequisites()).isNotEmpty();
    }

    @Test
    @DisplayName("Validated Brinson-Fachler calculation reconciles exact allocation, selection, interaction to active return")
    void testValidatedBrinsonFachlerCalculation() {
        // Hand-worked textbook example: 2 sectors (Financials & Technology)
        // Fund: Financials = 60% with return +10% (0.10); Tech = 40% with return +5% (0.05) -> Rp = 0.60*0.10 + 0.40*0.05 = 8.00%
        // Bench: Financials = 40% with return +8% (0.08); Tech = 60% with return +4% (0.04) -> Rb = 0.40*0.08 + 0.60*0.04 = 5.60%
        // Active Return = 8.00% - 5.60% = +2.40%
        //
        // Brinson-Fachler Formulas:
        // A_i = (w_p,i - w_b,i) * (R_b,i - R_b)
        // Financials A = (0.60 - 0.40) * (0.08 - 0.056) = 0.20 * 0.024 = +0.0048 (+0.48%)
        // Tech A = (0.40 - 0.60) * (0.04 - 0.056) = -0.20 * -0.016 = +0.0032 (+0.32%)
        // Total Allocation = +0.0080 (+0.80%)
        //
        // Selection: S_i = w_b,i * (R_p,i - R_b,i)
        // Financials S = 0.40 * (0.10 - 0.08) = 0.40 * 0.02 = +0.0080 (+0.80%)
        // Tech S = 0.60 * (0.05 - 0.04) = 0.60 * 0.01 = +0.0060 (+0.60%)
        // Total Selection = +0.0140 (+1.40%)
        //
        // Interaction: I_i = (w_p,i - w_b,i) * (R_p,i - R_b,i)
        // Financials I = 0.20 * 0.02 = +0.0040 (+0.40%)
        // Tech I = -0.20 * 0.01 = -0.0020 (-0.20%)
        // Total Interaction = +0.0020 (+0.20%)
        //
        // Reconciled Total = Total Allocation (+0.80%) + Total Selection (+1.40%) + Total Interaction (+0.20%) = +2.40%
        List<String> names = List.of("Financial Services", "Information Technology");
        List<BigDecimal> wp = List.of(new BigDecimal("60.00"), new BigDecimal("40.00"));
        List<BigDecimal> wb = List.of(new BigDecimal("40.00"), new BigDecimal("60.00"));
        List<BigDecimal> rp = List.of(new BigDecimal("0.10"), new BigDecimal("0.05"));
        List<BigDecimal> rb = List.of(new BigDecimal("0.08"), new BigDecimal("0.04"));

        BrinsonAttributionSummaryDto result = attributionService.calculateValidatedBrinsonFachler(
            names, wp, wb, rp, rb, new BigDecimal("0.08"), new BigDecimal("0.056")
        );

        assertThat(result.executionStatus()).isEqualTo("CALCULATED_VALIDATED_INPUTS");
        assertThat(result.isReconciled()).isTrue();
        assertThat(result.totalAllocationEffectPct()).isEqualByComparingTo(new BigDecimal("0.8000"));
        assertThat(result.totalSelectionEffectPct()).isEqualByComparingTo(new BigDecimal("1.4000"));
        assertThat(result.totalInteractionEffectPct()).isEqualByComparingTo(new BigDecimal("0.2000"));
        assertThat(result.totalActiveReturnReconciledPct()).isEqualByComparingTo(new BigDecimal("2.4000"));
        assertThat(result.segments()).hasSize(2);

        // Verify individual segment effects
        BrinsonSegmentDto finSeg = result.segments().get(0);
        assertThat(finSeg.allocationEffectPct()).isEqualByComparingTo(new BigDecimal("0.4800"));
        assertThat(finSeg.selectionEffectPct()).isEqualByComparingTo(new BigDecimal("0.8000"));
        assertThat(finSeg.interactionEffectPct()).isEqualByComparingTo(new BigDecimal("0.4000"));
    }

    @Test
    @DisplayName("Exposure timeline correctly flags temporal gap and scope shifts across multiple snapshots")
    void testExposureTimelineGeneration() {
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 9, 30, 23, 59, 59, 0, ZoneOffset.UTC);

        // Snapshot 1: 2021-08-31 (Full disclosure)
        PortfolioSnapshot s1 = new PortfolioSnapshot();
        s1.setId(101L);
        s1.setPortfolioDate(LocalDate.of(2021, 8, 31));
        s1.setAvailabilityTime(cutoff.minusYears(3));

        // Snapshot 2: 2024-01-31 (Partial disclosure, 29 months gap)
        PortfolioSnapshot s2 = new PortfolioSnapshot();
        s2.setId(102L);
        s2.setPortfolioDate(LocalDate.of(2024, 1, 31));
        s2.setAvailabilityTime(cutoff.minusMonths(8));

        // Snapshot 3: 2024-02-29 (Full disclosure, scope shift from partial to full)
        PortfolioSnapshot s3 = new PortfolioSnapshot();
        s3.setId(103L);
        s3.setPortfolioDate(LocalDate.of(2024, 2, 29));
        s3.setAvailabilityTime(cutoff.minusMonths(7));

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(s3, s2, s1));

        // Mock holdings for s1 (Full: 99.4%)
        PortfolioHolding h1 = new PortfolioHolding();
        Security sec1 = new Security();
        sec1.setId(1L);
        sec1.setCanonicalName("ICICI Bank Ltd");
        sec1.setSector("Financial Services");
        h1.setSecurity(sec1);
        h1.setReportedWeight(new BigDecimal("99.4000"));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1));

        // Mock holdings for s2 (Partial top-10: 58.7%)
        PortfolioHolding h2 = new PortfolioHolding();
        h2.setSecurity(sec1);
        h2.setReportedWeight(new BigDecimal("58.7000"));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(102L))
            .thenReturn(List.of(h2));

        // Mock holdings for s3 (Full: 98.8%)
        PortfolioHolding h3 = new PortfolioHolding();
        h3.setSecurity(sec1);
        h3.setReportedWeight(new BigDecimal("98.8000"));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(103L))
            .thenReturn(List.of(h3));

        List<ExposureTimelineObservationDto> timeline = attributionService.buildExposureTimeline(
            1L, LocalDate.of(2026, 9, 30), cutoff
        );

        assertThat(timeline).hasSize(3);

        // s1: Initial baseline
        assertThat(timeline.get(0).comparabilityStatus()).isEqualTo("BASELINE_OBSERVATION");
        assertThat(timeline.get(0).disclosureScope()).isEqualTo("FULL_PORTFOLIO_DISCLOSURE");

        // s2: >45 days gap (29 months)
        assertThat(timeline.get(1).comparabilityStatus()).isEqualTo("NON_COMPARABLE_TEMPORAL_GAP");
        assertThat(timeline.get(1).disclosureScope()).isEqualTo("PARTIAL_TOP_HOLDINGS");
        assertThat(timeline.get(1).daysSincePreviousSnapshot()).isGreaterThan(800L);

        // s3: Scope shift from partial to full
        assertThat(timeline.get(2).comparabilityStatus()).isEqualTo("NON_COMPARABLE_DISCLOSURE_SCOPE_SHIFT");
        assertThat(timeline.get(2).disclosureScope()).isEqualTo("FULL_PORTFOLIO_DISCLOSURE");
    }
}
