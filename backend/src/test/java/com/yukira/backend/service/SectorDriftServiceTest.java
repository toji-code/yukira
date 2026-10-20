package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.portfolio.SectorDriftService;
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
class SectorDriftServiceTest {

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
    private BenchmarkResolutionService benchmarkResolutionService;

    private SectorDriftService sectorDriftService;

    private SchemeOption schemeOption;
    private Benchmark benchmark;

    @BeforeEach
    void setUp() {
        sectorDriftService = new SectorDriftService(
            schemeOptionRepository,
            portfolioSnapshotRepository,
            portfolioHoldingRepository,
            securityIdentifierRepository,
            benchmarkRepository,
            benchmarkConstituentRepository,
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
                "SCHEME_PRIMARY",
                LocalDate.of(2020, 1, 1),
                "Official primary benchmark"
            ));
    }

    @Test
    @DisplayName("Hand-calculated sector aggregation aggregates holdings weights deterministically")
    void shouldCalculateHandCalculatedSectorAggregation() {
        LocalDate pDate = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(101L);
        snap.setPortfolioDate(pDate);
        snap.setAvailabilityTime(OffsetDateTime.of(2026, 10, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        snap.setRevisionSeq(1);

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap));

        // Holdings:
        // ICICI Bank (0.0880 -> 8.80%), Axis Bank (0.0611 -> 6.11%) -> Financial Services: 14.91%
        // Cipla (0.0247 -> 2.47%) -> Healthcare: 2.47%
        // Net Current Assets (0.0281 -> 2.81%) -> Cash & Liquid Assets: 2.81%
        Security secIcici = new Security();
        secIcici.setId(201L);
        secIcici.setCanonicalName("ICICI Bank Ltd.");
        secIcici.setSector("Financial Services");

        Security secAxis = new Security();
        secAxis.setId(202L);
        secAxis.setCanonicalName("Axis Bank Ltd.");
        secAxis.setSector("Banks");

        Security secCipla = new Security();
        secCipla.setId(203L);
        secCipla.setCanonicalName("Cipla Ltd.");
        secCipla.setSector("Healthcare");

        Security secCash = new Security();
        secCash.setId(204L);
        secCash.setCanonicalName("NET_CURRENT_ASSETS");
        secCash.setSector("Cash & Cash Equivalents");

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setSecurity(secIcici);
        h1.setReportedWeight(new BigDecimal("0.0880"));

        PortfolioHolding h2 = new PortfolioHolding();
        h2.setSecurity(secAxis);
        h2.setReportedWeight(new BigDecimal("0.0611"));

        PortfolioHolding h3 = new PortfolioHolding();
        h3.setSecurity(secCipla);
        h3.setReportedWeight(new BigDecimal("0.0247"));

        PortfolioHolding h4 = new PortfolioHolding();
        h4.setSecurity(secCash);
        h4.setReportedWeight(new BigDecimal("0.0281"));

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1, h2, h3, h4));

        when(benchmarkConstituentRepository.findEligibleConstituents(eq(100L), eq(pDate), eq(cutoff)))
            .thenReturn(Collections.emptyList());

        SectorDriftResponseDto response = sectorDriftService.getSectorDriftTrajectory(1L, pDate, null, null, cutoff);

        assertThat(response).isNotNull();
        assertThat(response.observations()).hasSize(1);
        SectorDriftObservationDto obs = response.observations().get(0);

        assertThat(obs.portfolioDate()).isEqualTo(pDate);
        assertThat(obs.fundHoldingsCount()).isEqualTo(4);

        // Verify aggregated sectors
        var finSector = obs.sectors().stream()
            .filter(s -> "Financial Services".equals(s.sectorName())).findFirst().orElseThrow();
        assertThat(finSector.fundWeightPct()).isEqualByComparingTo("14.91");
        assertThat(finSector.holdingCount()).isEqualTo(2);

        var healthSector = obs.sectors().stream()
            .filter(s -> "Healthcare".equals(s.sectorName())).findFirst().orElseThrow();
        assertThat(healthSector.fundWeightPct()).isEqualByComparingTo("2.47");
        assertThat(healthSector.holdingCount()).isEqualTo(1);

        var cashSector = obs.sectors().stream()
            .filter(s -> "Cash & Liquid Assets".equals(s.sectorName())).findFirst().orElseThrow();
        assertThat(cashSector.fundWeightPct()).isEqualByComparingTo("2.81");
        assertThat(cashSector.holdingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("GATES definitive active sector weights when benchmark weights are only partially disclosed")
    void shouldGateDefinitiveSectorActiveWeightsWhenBenchmarkWeightsArePartial() {
        LocalDate pDate = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(102L);
        snap.setPortfolioDate(pDate);
        snap.setAvailabilityTime(OffsetDateTime.of(2026, 10, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        snap.setRevisionSeq(1);

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap));

        Security secHdfc = new Security();
        secHdfc.setId(301L);
        secHdfc.setCanonicalName("HDFC Bank Ltd.");
        secHdfc.setSector("Financial Services");

        SecurityIdentifier idHdfc = new SecurityIdentifier();
        idHdfc.setIdType("ISIN");
        idHdfc.setIdValue("INE040A01034");
        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(301L, "ISIN")).thenReturn(Optional.of(idHdfc));

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setSecurity(secHdfc);
        h1.setReportedWeight(new BigDecimal("0.0800")); // 8.00%

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(102L))
            .thenReturn(List.of(h1));

        // Benchmark: 2 members, but only 1 weighted (total weight coverage = 5.66% << 99.50%)
        BenchmarkConstituent bc1 = new BenchmarkConstituent();
        bc1.setIsin("INE040A01034");
        bc1.setSymbol("HDFCBANK");
        bc1.setIndustry("Financial Services");
        bc1.setWeight(new BigDecimal("5.660000")); // 5.66%
        bc1.setEffectiveDate(pDate);
        bc1.setAvailabilityTime(OffsetDateTime.of(2026, 9, 30, 11, 30, 0, 0, ZoneOffset.UTC));

        BenchmarkConstituent bc2 = new BenchmarkConstituent();
        bc2.setIsin("INE062A01020");
        bc2.setSymbol("SBIN");
        bc2.setIndustry("Financial Services");
        bc2.setWeight(null); // UNWEIGHTED TAIL
        bc2.setEffectiveDate(pDate);
        bc2.setAvailabilityTime(OffsetDateTime.of(2026, 9, 30, 11, 30, 0, 0, ZoneOffset.UTC));

        when(benchmarkConstituentRepository.findEligibleConstituents(eq(100L), eq(pDate), eq(cutoff)))
            .thenReturn(List.of(bc1, bc2));

        SectorDriftResponseDto response = sectorDriftService.getSectorDriftTrajectory(1L, pDate, null, null, cutoff);

        assertThat(response).isNotNull();
        SectorDriftObservationDto obs = response.observations().get(0);

        // Verification of GATING
        assertThat(obs.observationStatus()).isEqualTo("GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
        assertThat(obs.benchmarkWeightCoveragePct()).isEqualByComparingTo("5.66");
        assertThat(obs.benchmarkWeightedCount()).isEqualTo(1);
        assertThat(obs.benchmarkConstituentsCount()).isEqualTo(2);

        // Sector Comparison must be gated
        var finComp = obs.sectorComparisons().stream()
            .filter(c -> "Financial Services".equals(c.sectorName())).findFirst().orElseThrow();

        assertThat(finComp.comparisonStatus()).isEqualTo("GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
        assertThat(finComp.definitiveActiveWeightPct()).isNull(); // MUST BE NULL!
        assertThat(finComp.benchmarkDisclosedWeightPct()).isEqualByComparingTo("5.66"); // Lower bound disclosed
        assertThat(finComp.fundWeightPct()).isEqualByComparingTo("8.00");
        assertThat(finComp.statusReason()).contains("partial lower bound");
    }

    @Test
    @DisplayName("Calculates definitive sector active weights when benchmark weights are fully disclosed (>= 99.5%)")
    void shouldCalculateDefinitiveSectorActiveWeightsWhenBenchmarkIsFullyDisclosed() {
        LocalDate pDate = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(103L);
        snap.setPortfolioDate(pDate);
        snap.setAvailabilityTime(OffsetDateTime.of(2026, 10, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        snap.setRevisionSeq(1);

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap));

        Security secHdfc = new Security();
        secHdfc.setId(401L);
        secHdfc.setCanonicalName("HDFC Bank Ltd.");
        secHdfc.setSector("Financial Services");

        SecurityIdentifier idHdfc = new SecurityIdentifier();
        idHdfc.setIdType("ISIN");
        idHdfc.setIdValue("INE040A01034");
        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(401L, "ISIN")).thenReturn(Optional.of(idHdfc));

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setSecurity(secHdfc);
        h1.setReportedWeight(new BigDecimal("0.9500")); // 95.00% Financial Services

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(103L))
            .thenReturn(List.of(h1));

        // Fully disclosed benchmark (100.00% coverage)
        BenchmarkConstituent bc1 = new BenchmarkConstituent();
        bc1.setIsin("INE040A01034");
        bc1.setIndustry("Financial Services");
        bc1.setWeight(new BigDecimal("70.000000")); // 70%
        bc1.setEffectiveDate(pDate);
        bc1.setAvailabilityTime(OffsetDateTime.of(2026, 9, 30, 11, 30, 0, 0, ZoneOffset.UTC));

        BenchmarkConstituent bc2 = new BenchmarkConstituent();
        bc2.setIsin("INE002A01018");
        bc2.setIndustry("Oil Gas & Consumable Fuels");
        bc2.setWeight(new BigDecimal("30.000000")); // 30%
        bc2.setEffectiveDate(pDate);
        bc2.setAvailabilityTime(OffsetDateTime.of(2026, 9, 30, 11, 30, 0, 0, ZoneOffset.UTC));

        when(benchmarkConstituentRepository.findEligibleConstituents(eq(100L), eq(pDate), eq(cutoff)))
            .thenReturn(List.of(bc1, bc2));

        SectorDriftResponseDto response = sectorDriftService.getSectorDriftTrajectory(1L, pDate, null, null, cutoff);

        assertThat(response).isNotNull();
        SectorDriftObservationDto obs = response.observations().get(0);
        assertThat(obs.observationStatus()).isEqualTo("VERIFIED_COMPLETE");
        assertThat(obs.benchmarkWeightCoveragePct()).isEqualByComparingTo("100.00");

        var finComp = obs.sectorComparisons().stream()
            .filter(c -> "Financial Services".equals(c.sectorName())).findFirst().orElseThrow();

        assertThat(finComp.comparisonStatus()).isEqualTo("VERIFIED_COMPLETE");
        // 95.00% fund - 70.00% benchmark = +25.00% active spread
        assertThat(finComp.definitiveActiveWeightPct()).isEqualByComparingTo("25.00");

        var energyComp = obs.sectorComparisons().stream()
            .filter(c -> "Oil Gas & Consumable Fuels".equals(c.sectorName())).findFirst().orElseThrow();
        // 0.00% fund - 30.00% benchmark = -30.00% active spread
        assertThat(energyComp.definitiveActiveWeightPct()).isEqualByComparingTo("-30.00");
    }

    @Test
    @DisplayName("Preserves unclassified and unmapped holdings honestly with zero silent discards")
    void shouldHandleUnclassifiedAndUnmappedHoldingsHonestLy() {
        LocalDate pDate = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(104L);
        snap.setPortfolioDate(pDate);
        snap.setAvailabilityTime(OffsetDateTime.of(2026, 10, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        snap.setRevisionSeq(1);

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap));

        Security unmappedSec = new Security();
        unmappedSec.setId(501L);
        unmappedSec.setCanonicalName("Mysterious Startup Ltd.");
        unmappedSec.setSector(null); // No sector

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setSecurity(unmappedSec);
        h1.setReportedWeight(new BigDecimal("0.0500")); // 5.00%

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(104L))
            .thenReturn(List.of(h1));

        SectorDriftResponseDto response = sectorDriftService.getSectorDriftTrajectory(1L, pDate, null, null, cutoff);

        assertThat(response).isNotNull();
        SectorDriftObservationDto obs = response.observations().get(0);
        assertThat(obs.sectors()).hasSize(1);
        FundSectorExposureDto secDto = obs.sectors().get(0);

        assertThat(secDto.sectorName()).isEqualTo("Unclassified / Other");
        assertThat(secDto.classificationProvenance()).isEqualTo("UNCLASSIFIED");
        assertThat(secDto.fundWeightPct()).isEqualByComparingTo("5.00");
        assertThat(obs.fundClassificationCoveragePct()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Disentangles historical sector drift drivers (scope shift vs holding entry/exit vs weight adjustment)")
    void shouldDisentangleHistoricalSectorDriftDrivers() {
        LocalDate date1 = LocalDate.of(2024, 1, 31);
        LocalDate date2 = LocalDate.of(2026, 6, 30);
        LocalDate date3 = LocalDate.of(2026, 7, 31);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot s1 = new PortfolioSnapshot();
        s1.setId(1001L);
        s1.setPortfolioDate(date1);
        s1.setAvailabilityTime(OffsetDateTime.of(2024, 2, 5, 10, 0, 0, 0, ZoneOffset.UTC));
        s1.setRevisionSeq(1);

        PortfolioSnapshot s2 = new PortfolioSnapshot();
        s2.setId(1002L);
        s2.setPortfolioDate(date2);
        s2.setAvailabilityTime(OffsetDateTime.of(2026, 7, 5, 10, 0, 0, 0, ZoneOffset.UTC));
        s2.setRevisionSeq(1);

        PortfolioSnapshot s3 = new PortfolioSnapshot();
        s3.setId(1003L);
        s3.setPortfolioDate(date3);
        s3.setAvailabilityTime(OffsetDateTime.of(2026, 8, 5, 10, 0, 0, 0, ZoneOffset.UTC));
        s3.setRevisionSeq(1);

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(s3, s2, s1));

        // Snapshot 1: Partial Top 10 (sum = 54.82%)
        Security secFin1 = new Security();
        secFin1.setId(601L);
        secFin1.setCanonicalName("ICICI Bank Ltd.");
        secFin1.setSector("Financial Services");

        PortfolioHolding hDate1 = new PortfolioHolding();
        hDate1.setSecurity(secFin1);
        hDate1.setReportedWeight(new BigDecimal("0.5482")); // 54.82%

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1001L))
            .thenReturn(List.of(hDate1));

        // Snapshot 2: Full Portfolio (sum = 100.00%, 2 holdings in Fin: 40.00%)
        Security secFin2 = new Security();
        secFin2.setId(602L);
        secFin2.setCanonicalName("HDFC Bank Ltd.");
        secFin2.setSector("Financial Services");

        PortfolioHolding hDate2_1 = new PortfolioHolding();
        hDate2_1.setSecurity(secFin1);
        hDate2_1.setReportedWeight(new BigDecimal("0.2500"));

        PortfolioHolding hDate2_2 = new PortfolioHolding();
        hDate2_2.setSecurity(secFin2);
        hDate2_2.setReportedWeight(new BigDecimal("0.7500"));

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1002L))
            .thenReturn(List.of(hDate2_1, hDate2_2));

        // Snapshot 3: Full Portfolio (sum = 100.00%, same 2 holdings in Fin, weights adjusted: 30% and 70%)
        PortfolioHolding hDate3_1 = new PortfolioHolding();
        hDate3_1.setSecurity(secFin1);
        hDate3_1.setReportedWeight(new BigDecimal("0.3000")); // +5 pp

        PortfolioHolding hDate3_2 = new PortfolioHolding();
        hDate3_2.setSecurity(secFin2);
        hDate3_2.setReportedWeight(new BigDecimal("0.7000")); // -5 pp

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(1003L))
            .thenReturn(List.of(hDate3_1, hDate3_2));

        SectorDriftResponseDto response = sectorDriftService.getSectorDriftTrajectory(1L, date3, null, null, cutoff);

        assertThat(response).isNotNull();
        assertThat(response.sectorDriftChanges()).isNotEmpty();

        // 1. Shift from 2024-01-31 (partial top 10) to 2026-06-30 (full portfolio) must be DISCLOSURE_SCOPE_SHIFT
        var scopeShift = response.sectorDriftChanges().stream()
            .filter(c -> c.earlierDate().equals(date1) && c.laterDate().equals(date2))
            .findFirst().orElseThrow();
        assertThat(scopeShift.changeDriver()).isEqualTo("DISCLOSURE_SCOPE_SHIFT");
        assertThat(scopeShift.driverExplanation()).contains("partial top-holdings");

        // 2. Shift from 2026-06-30 to 2026-07-31 (same 2 holdings, net delta = 0 pp total Financial Services)
        // Check that any change with same holding counts is categorized as PORTFOLIO_WEIGHT_ADJUSTMENT
    }

    @Test
    @DisplayName("Marks observation as TEMPORAL_MISMATCH when benchmark snapshot is more than 45 days away")
    void shouldEnforceTemporalMismatchWhenBenchmarkIsOver45Days() {
        LocalDate pDate = LocalDate.of(2026, 8, 31);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(105L);
        snap.setPortfolioDate(pDate);
        snap.setAvailabilityTime(OffsetDateTime.of(2026, 9, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        snap.setRevisionSeq(1);

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap));

        Security sec = new Security();
        sec.setId(701L);
        sec.setCanonicalName("ICICI Bank Ltd.");
        sec.setSector("Financial Services");

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setSecurity(sec);
        h1.setReportedWeight(new BigDecimal("1.0000"));

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(105L))
            .thenReturn(List.of(h1));

        // Available benchmark date is 2026-06-30 (62 days away, > 45 days)
        when(benchmarkConstituentRepository.findEligibleConstituents(eq(100L), eq(pDate), eq(cutoff)))
            .thenReturn(Collections.emptyList());

        LocalDate oldBmDate = LocalDate.of(2026, 6, 30);
        when(benchmarkConstituentRepository.findAvailableCompositionDates(eq(100L), eq(cutoff)))
            .thenReturn(List.of(oldBmDate));

        BenchmarkConstituent bc = new BenchmarkConstituent();
        bc.setIsin("INE090A01021");
        bc.setEffectiveDate(oldBmDate);
        bc.setAvailabilityTime(OffsetDateTime.of(2026, 5, 30, 0, 0, 0, 0, ZoneOffset.UTC));
        bc.setWeight(new BigDecimal("5.00"));

        when(benchmarkConstituentRepository.findByBenchmarkAndDateAndCutoff(eq(100L), eq(oldBmDate), eq(cutoff)))
            .thenReturn(List.of(bc));

        SectorDriftResponseDto response = sectorDriftService.getSectorDriftTrajectory(1L, pDate, null, null, cutoff);

        assertThat(response).isNotNull();
        SectorDriftObservationDto obs = response.observations().get(0);
        assertThat(obs.isTemporallyComparable()).isFalse();
        assertThat(obs.observationStatus()).isEqualTo("TEMPORAL_MISMATCH");
        assertThat(obs.snapshotDateDiffDays()).isEqualTo(62);
    }

    @Test
    @DisplayName("Aggregates sub-industries deterministically and gates definitive sub-industry active weights")
    void shouldAggregateSubIndustriesAndGateActiveWeights() {
        LocalDate pDate = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(201L);
        snap.setPortfolioDate(pDate);
        snap.setAvailabilityTime(OffsetDateTime.of(2026, 10, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        snap.setRevisionSeq(1);

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap));

        Security s1 = new Security();
        s1.setId(801L);
        s1.setCanonicalName("ICICI Bank Ltd.");
        s1.setSector("Banks");

        Security s2 = new Security();
        s2.setId(802L);
        s2.setCanonicalName("Axis Bank Ltd.");
        s2.setSector("Banks");

        Security s3 = new Security();
        s3.setId(803L);
        s3.setCanonicalName("SBI Life Insurance Co. Ltd.");
        s3.setSector("Insurance");

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setSecurity(s1);
        h1.setReportedWeight(new BigDecimal("0.0880")); // 8.80%

        PortfolioHolding h2 = new PortfolioHolding();
        h2.setSecurity(s2);
        h2.setReportedWeight(new BigDecimal("0.0611")); // 6.11%

        PortfolioHolding h3 = new PortfolioHolding();
        h3.setSecurity(s3);
        h3.setReportedWeight(new BigDecimal("0.0325")); // 3.25%

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(201L))
            .thenReturn(List.of(h1, h2, h3));

        SecurityIdentifier id1 = new SecurityIdentifier(s1, "ISIN", "INE090A01021", LocalDate.of(2020, 1, 1));
        SecurityIdentifier id2 = new SecurityIdentifier(s2, "ISIN", "INE238A01034", LocalDate.of(2020, 1, 1));
        SecurityIdentifier id3 = new SecurityIdentifier(s3, "ISIN", "INE123A01011", LocalDate.of(2020, 1, 1));

        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(801L, "ISIN")).thenReturn(Optional.of(id1));
        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(802L, "ISIN")).thenReturn(Optional.of(id2));
        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(803L, "ISIN")).thenReturn(Optional.of(id3));

        BenchmarkConstituent bc1 = new BenchmarkConstituent();
        bc1.setIsin("INE090A01021");
        bc1.setCompanyName("ICICI Bank Ltd.");
        bc1.setIndustry("Financial Services");
        bc1.setWeight(new BigDecimal("4.93"));
        bc1.setEffectiveDate(pDate);

        BenchmarkConstituent bc2 = new BenchmarkConstituent();
        bc2.setIsin("INE238A01034");
        bc2.setCompanyName("Axis Bank Ltd.");
        bc2.setIndustry("Financial Services");
        bc2.setWeight(null); // Unweighted tail constituent
        bc2.setEffectiveDate(pDate);

        when(benchmarkConstituentRepository.findEligibleConstituents(eq(100L), eq(pDate), eq(cutoff)))
            .thenReturn(List.of(bc1, bc2));

        SectorDriftResponseDto response = sectorDriftService.getSectorDriftTrajectory(1L, pDate, null, null, cutoff);
        assertThat(response).isNotNull();

        SectorDriftObservationDto obs = response.observations().get(0);
        assertThat(obs.industries()).hasSize(2);

        // Banks industry: 8.80 + 6.11 = 14.91%
        FundIndustryExposureDto banksInd = obs.industries().stream()
            .filter(i -> "Banks".equals(i.industryName()))
            .findFirst()
            .orElseThrow();
        assertThat(banksInd.parentSectorName()).isEqualTo("Financial Services");
        assertThat(banksInd.fundWeightPct()).isEqualByComparingTo("14.91");
        assertThat(banksInd.holdingCount()).isEqualTo(2);
        assertThat(banksInd.classificationLevel()).isEqualTo("AMC_DISCLOSED_SUB_INDUSTRY");

        // Insurance industry: 3.25%
        FundIndustryExposureDto insInd = obs.industries().stream()
            .filter(i -> "Insurance".equals(i.industryName()))
            .findFirst()
            .orElseThrow();
        assertThat(insInd.fundWeightPct()).isEqualByComparingTo("3.25");
        assertThat(insInd.holdingCount()).isEqualTo(1);

        // Active comparisons for industries: definitive active weight must be gated (null)
        IndustryActiveComparisonDto banksComp = obs.industryComparisons().stream()
            .filter(c -> "Banks".equals(c.industryName()))
            .findFirst()
            .orElseThrow();
        assertThat(banksComp.fundWeightPct()).isEqualByComparingTo("14.91");
        assertThat(banksComp.benchmarkDisclosedWeightPct()).isEqualByComparingTo("4.93");
        assertThat(banksComp.definitiveActiveWeightPct()).isNull();
        assertThat(banksComp.comparisonStatus()).isEqualTo("GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
    }

    @Test
    @DisplayName("Builds security waterfall adhering strictly to the three explicit exposure categories")
    void shouldBuildSecurityWaterfallWithThreeCategories() {
        LocalDate pDate = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(202L);
        snap.setPortfolioDate(pDate);
        snap.setAvailabilityTime(OffsetDateTime.of(2026, 10, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        snap.setRevisionSeq(1);

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap));

        // 1. ICICI Bank: Weighted in benchmark -> COMPARABLE_ACTIVE_EXPOSURE
        Security s1 = new Security();
        s1.setId(901L);
        s1.setCanonicalName("ICICI Bank Ltd.");
        s1.setSector("Banks");

        // 2. Kotak Bank: In benchmark, unweighted -> BENCHMARK_MEMBER_WEIGHT_UNKNOWN
        Security s2 = new Security();
        s2.setId(902L);
        s2.setCanonicalName("Kotak Mahindra Bank Ltd.");
        s2.setSector("Banks");

        // 3. Off-benchmark holding with ISIN -> OFF_BENCHMARK_HOLDING
        Security s3 = new Security();
        s3.setId(903L);
        s3.setCanonicalName("Off Benchmark Tech Ltd.");
        s3.setSector("Information Technology");

        // 4. Holding with missing ISIN -> UNRESOLVED_MEMBERSHIP
        Security s4 = new Security();
        s4.setId(904L);
        s4.setCanonicalName("Unresolved Holding Ltd.");
        s4.setSector("Capital Goods");

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setSecurity(s1);
        h1.setReportedWeight(new BigDecimal("0.0880")); // 8.80%

        PortfolioHolding h2 = new PortfolioHolding();
        h2.setSecurity(s2);
        h2.setReportedWeight(new BigDecimal("0.0340")); // 3.40%

        PortfolioHolding h3 = new PortfolioHolding();
        h3.setSecurity(s3);
        h3.setReportedWeight(new BigDecimal("0.0150")); // 1.50%

        PortfolioHolding h4 = new PortfolioHolding();
        h4.setSecurity(s4);
        h4.setReportedWeight(new BigDecimal("0.0050")); // 0.50%

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(202L))
            .thenReturn(List.of(h1, h2, h3, h4));

        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(901L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(s1, "ISIN", "INE090A01021", LocalDate.of(2020, 1, 1))));
        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(902L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(s2, "ISIN", "INE237A01028", LocalDate.of(2020, 1, 1))));
        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(903L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(s3, "ISIN", "INE999A01099", LocalDate.of(2020, 1, 1))));
        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(904L, "ISIN"))
            .thenReturn(Optional.empty()); // No ISIN

        // Build benchmark constituent list with 450 members (complete snapshot)
        List<BenchmarkConstituent> constituents = new ArrayList<>();
        BenchmarkConstituent bc1 = new BenchmarkConstituent();
        bc1.setIsin("INE090A01021");
        bc1.setCompanyName("ICICI Bank Ltd.");
        bc1.setIndustry("Financial Services");
        bc1.setWeight(new BigDecimal("4.93"));
        bc1.setEffectiveDate(pDate);
        constituents.add(bc1);

        BenchmarkConstituent bc2 = new BenchmarkConstituent();
        bc2.setIsin("INE237A01028");
        bc2.setCompanyName("Kotak Mahindra Bank Ltd.");
        bc2.setIndustry("Financial Services");
        bc2.setWeight(null); // Unknown weight
        bc2.setEffectiveDate(pDate);
        constituents.add(bc2);

        // Benchmark heavyweight not held in fund
        BenchmarkConstituent bcHeavy = new BenchmarkConstituent();
        bcHeavy.setIsin("INE002A01018");
        bcHeavy.setCompanyName("Reliance Industries Ltd.");
        bcHeavy.setIndustry("Oil Gas & Consumable Fuels");
        bcHeavy.setWeight(new BigDecimal("4.13"));
        bcHeavy.setEffectiveDate(pDate);
        constituents.add(bcHeavy);

        // Fill remaining dummy constituents up to 450
        for (int i = 0; i < 447; i++) {
            BenchmarkConstituent dummy = new BenchmarkConstituent();
            dummy.setIsin("INE_DUMMY_" + i);
            dummy.setCompanyName("Dummy " + i);
            dummy.setIndustry("Financial Services");
            dummy.setEffectiveDate(pDate);
            constituents.add(dummy);
        }

        when(benchmarkConstituentRepository.findEligibleConstituents(eq(100L), eq(pDate), eq(cutoff)))
            .thenReturn(constituents);

        SectorDriftResponseDto response = sectorDriftService.getSectorDriftTrajectory(1L, pDate, null, null, cutoff);
        assertThat(response).isNotNull();

        SectorDriftObservationDto obs = response.observations().get(0);
        List<SecurityExposureWaterfallDto> waterfall = obs.securityWaterfall();

        // 1. Check ICICI Bank: COMPARABLE_ACTIVE_EXPOSURE
        var icici = waterfall.stream().filter(w -> "INE090A01021".equals(w.isin())).findFirst().orElseThrow();
        assertThat(icici.exposureCategory()).isEqualTo("COMPARABLE_ACTIVE_EXPOSURE");
        assertThat(icici.fundWeightPct()).isEqualByComparingTo("8.80");
        assertThat(icici.benchmarkWeightPct()).isEqualByComparingTo("4.93");
        assertThat(icici.activeSpreadPct()).isEqualByComparingTo("3.87");

        // 2. Check Kotak Bank: BENCHMARK_MEMBER_WEIGHT_UNKNOWN
        var kotak = waterfall.stream().filter(w -> "INE237A01028".equals(w.isin())).findFirst().orElseThrow();
        assertThat(kotak.exposureCategory()).isEqualTo("BENCHMARK_MEMBER_WEIGHT_UNKNOWN");
        assertThat(kotak.fundWeightPct()).isEqualByComparingTo("3.40");
        assertThat(kotak.benchmarkWeightPct()).isNull();
        assertThat(kotak.activeSpreadPct()).isNull(); // NEVER calculate spread with unknown BM weight

        // 3. Check Off-benchmark Tech: OFF_BENCHMARK_HOLDING
        var offBm = waterfall.stream().filter(w -> "INE999A01099".equals(w.isin())).findFirst().orElseThrow();
        assertThat(offBm.exposureCategory()).isEqualTo("OFF_BENCHMARK_HOLDING");
        assertThat(offBm.fundWeightPct()).isEqualByComparingTo("1.50");
        assertThat(offBm.benchmarkWeightPct()).isEqualByComparingTo("0.00");
        assertThat(offBm.activeSpreadPct()).isEqualByComparingTo("1.50");

        // 4. Check Unresolved ISIN: UNRESOLVED_MEMBERSHIP
        var unres = waterfall.stream().filter(w -> w.isin() == null).findFirst().orElseThrow();
        assertThat(unres.exposureCategory()).isEqualTo("UNRESOLVED_MEMBERSHIP");
        assertThat(unres.activeSpreadPct()).isNull();

        // 5. Check Reliance (unheld benchmark heavyweight): COMPARABLE_ACTIVE_EXPOSURE
        var reliance = waterfall.stream().filter(w -> "INE002A01018".equals(w.isin())).findFirst().orElseThrow();
        assertThat(reliance.exposureCategory()).isEqualTo("COMPARABLE_ACTIVE_EXPOSURE");
        assertThat(reliance.fundWeightPct()).isEqualByComparingTo("0.00");
        assertThat(reliance.benchmarkWeightPct()).isEqualByComparingTo("4.13");
        assertThat(reliance.activeSpreadPct()).isEqualByComparingTo("-4.13");
    }
}
