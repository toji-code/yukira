package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.BenchmarkDivergenceResponseDto;
import com.yukira.backend.repository.*;
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
class BenchmarkDivergenceServiceTest {

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
    private BenchmarkResolutionService benchmarkResolutionService;

    private BenchmarkDivergenceService divergenceService;

    private SchemeOption schemeOption;
    private Benchmark benchmark;

    @BeforeEach
    void setUp() {
        divergenceService = new BenchmarkDivergenceService(
            schemeOptionRepository,
            portfolioSnapshotRepository,
            portfolioHoldingRepository,
            securityIdentifierRepository,
            benchmarkRepository,
            benchmarkConstituentRepository,
            classificationRepository,
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
    @DisplayName("Calculates multi-period divergence and separates Three Sources of Apparent Change")
    void shouldCalculateMultiPeriodDivergenceAndSeparateThreeSourcesOfChange() {
        LocalDate date1 = LocalDate.of(2026, 8, 31);
        LocalDate date2 = LocalDate.of(2026, 9, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 5, 0, 0, 0, 0, ZoneOffset.UTC);

        PortfolioSnapshot snap1 = new PortfolioSnapshot();
        snap1.setId(101L);
        snap1.setSchemeOption(schemeOption);
        snap1.setPortfolioDate(date1);
        snap1.setAvailabilityTime(date1.atStartOfDay().atOffset(ZoneOffset.UTC));

        PortfolioSnapshot snap2 = new PortfolioSnapshot();
        snap2.setId(102L);
        snap2.setSchemeOption(schemeOption);
        snap2.setPortfolioDate(date2);
        snap2.setAvailabilityTime(date2.atStartOfDay().atOffset(ZoneOffset.UTC));

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap2, snap1));

        // Securities
        Security secHdfc = new Security();
        secHdfc.setId(1L);
        secHdfc.setCanonicalName("HDFC Bank Ltd.");

        Security secMidToLarge = new Security();
        secMidToLarge.setId(2L);
        secMidToLarge.setCanonicalName("Voltas Ltd.");

        Security secExitBm = new Security();
        secExitBm.setId(3L);
        secExitBm.setCanonicalName("Off Benchmark Tech Ltd.");

        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(1L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(secHdfc, "ISIN", "INE040A01034", LocalDate.of(2020, 1, 1))));
        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(2L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(secMidToLarge, "ISIN", "INE226A01021", LocalDate.of(2020, 1, 1))));
        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(3L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(secExitBm, "ISIN", "INE999X01099", LocalDate.of(2020, 1, 1))));

        // Snap1 Holdings: HDFC Bank 8.00%, Voltas 3.00%, Off Benchmark Tech 2.00%
        PortfolioHolding h1_1 = new PortfolioHolding();
        h1_1.setSecurity(secHdfc);
        h1_1.setReportedWeight(new BigDecimal("0.080000")); // 8.00%

        PortfolioHolding h1_2 = new PortfolioHolding();
        h1_2.setSecurity(secMidToLarge);
        h1_2.setReportedWeight(new BigDecimal("0.030000")); // 3.00%

        PortfolioHolding h1_3 = new PortfolioHolding();
        h1_3.setSecurity(secExitBm);
        h1_3.setReportedWeight(new BigDecimal("0.020000")); // 2.00%

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1_1, h1_2, h1_3));

        // Snap2 Holdings: HDFC Bank 9.50% (Fund change: +1.50%), Voltas 3.00% (same weight), Off Benchmark Tech 2.00%
        PortfolioHolding h2_1 = new PortfolioHolding();
        h2_1.setSecurity(secHdfc);
        h2_1.setReportedWeight(new BigDecimal("0.095000")); // 9.50%

        PortfolioHolding h2_2 = new PortfolioHolding();
        h2_2.setSecurity(secMidToLarge);
        h2_2.setReportedWeight(new BigDecimal("0.030000")); // 3.00%

        PortfolioHolding h2_3 = new PortfolioHolding();
        h2_3.setSecurity(secExitBm);
        h2_3.setReportedWeight(new BigDecimal("0.020000")); // 2.00%

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(102L))
            .thenReturn(List.of(h2_1, h2_2, h2_3));

        // Benchmark Constituents:
        // Period 1: HDFC Bank (5.66%), Voltas (in BM, no weight disclosed = null), Off Benchmark Tech (in BM)
        BenchmarkConstituent bc1_1 = new BenchmarkConstituent();
        bc1_1.setIsin("INE040A01034");
        bc1_1.setWeight(new BigDecimal("5.660000"));
        bc1_1.setEffectiveDate(date1);

        BenchmarkConstituent bc1_2 = new BenchmarkConstituent();
        bc1_2.setIsin("INE226A01021");
        bc1_2.setWeight(null); // Undisclosed tail
        bc1_2.setEffectiveDate(date1);

        BenchmarkConstituent bc1_3 = new BenchmarkConstituent();
        bc1_3.setIsin("INE999X01099");
        bc1_3.setWeight(null);
        bc1_3.setEffectiveDate(date1);

        // Period 2: HDFC Bank (5.66%), Voltas (null), but Off Benchmark Tech EXITED benchmark!
        BenchmarkConstituent bc2_1 = new BenchmarkConstituent();
        bc2_1.setIsin("INE040A01034");
        bc2_1.setWeight(new BigDecimal("5.660000"));
        bc2_1.setEffectiveDate(date2);

        BenchmarkConstituent bc2_2 = new BenchmarkConstituent();
        bc2_2.setIsin("INE226A01021");
        bc2_2.setWeight(null);
        bc2_2.setEffectiveDate(date2);

        when(benchmarkConstituentRepository.findEligibleConstituents(eq(100L), eq(date1), eq(cutoff)))
            .thenReturn(List.of(bc1_1, bc1_2, bc1_3));
        when(benchmarkConstituentRepository.findEligibleConstituents(eq(100L), eq(date2), eq(cutoff)))
            .thenReturn(List.of(bc2_1, bc2_2));

        // AMFI Classifications:
        // Voltas was MID_CAP at date1, but reclassified to LARGE_CAP at date2!
        SecurityMarketCapClassification mcapVoltas1 = new SecurityMarketCapClassification();
        mcapVoltas1.setMarketCapCategory("MID_CAP");
        when(classificationRepository.findByIsinAndPit(eq("INE226A01021"), eq(date1), eq(cutoff)))
            .thenReturn(List.of(mcapVoltas1));

        SecurityMarketCapClassification mcapVoltas2 = new SecurityMarketCapClassification();
        mcapVoltas2.setMarketCapCategory("LARGE_CAP");
        when(classificationRepository.findByIsinAndPit(eq("INE226A01021"), eq(date2), eq(cutoff)))
            .thenReturn(List.of(mcapVoltas2));

        SecurityMarketCapClassification mcapHdfc = new SecurityMarketCapClassification();
        mcapHdfc.setMarketCapCategory("LARGE_CAP");
        when(classificationRepository.findByIsinAndPit(eq("INE040A01034"), any(LocalDate.class), eq(cutoff)))
            .thenReturn(List.of(mcapHdfc));

        BenchmarkDivergenceResponseDto response = divergenceService.calculateMultiPeriodDivergence(
            1L, date1, date2, cutoff
        );

        assertThat(response).isNotNull();
        assertThat(response.periods()).hasSize(2);

        // Period 1 checks
        var p1 = response.periods().get(0);
        assertThat(p1.portfolioDate()).isEqualTo(date1);
        assertThat(p1.fundHoldingsCount()).isEqualTo(3);
        assertThat(p1.commonCount()).isEqualTo(3);
        assertThat(p1.fundOnlyCount()).isEqualTo(0);
        assertThat(p1.offBenchmarkCapitalAllocationPct()).isEqualByComparingTo("0.00");
        assertThat(p1.periodStatus()).isEqualTo("VERIFIED_PARTIAL");

        // Period 2 checks: Off Benchmark Tech exited benchmark!
        var p2 = response.periods().get(1);
        assertThat(p2.portfolioDate()).isEqualTo(date2);
        assertThat(p2.commonCount()).isEqualTo(2);
        assertThat(p2.fundOnlyCount()).isEqualTo(1); // Off Benchmark Tech is now fund-only!
        assertThat(p2.offBenchmarkCapitalAllocationPct()).isEqualByComparingTo("2.00"); // 2.00% off-benchmark capital!

        // Separation of Three Sources of Apparent Change
        assertThat(response.apparentChanges()).isNotEmpty();

        // 1. Source 1: Fund Portfolio Change (HDFC Bank weight shifted from 8.00% to 9.50%)
        var fundChanges = response.apparentChanges().stream()
            .filter(c -> "FUND_PORTFOLIO_CHANGE".equals(c.changeCategory())).toList();
        assertThat(fundChanges).isNotEmpty();
        var hdfcChange = fundChanges.stream().filter(c -> c.securityName().contains("HDFC Bank")).findFirst().orElseThrow();
        assertThat(hdfcChange.description()).contains("shifted from 8.00% to 9.50% (+1.50%)");

        // 2. Source 2: Benchmark Membership Change (Off Benchmark Tech exited benchmark)
        var bmChanges = response.apparentChanges().stream()
            .filter(c -> "BENCHMARK_MEMBERSHIP_CHANGE".equals(c.changeCategory())).toList();
        assertThat(bmChanges).isNotEmpty();
        var bmExit = bmChanges.stream().filter(c -> c.securityName().contains("Off Benchmark Tech")).findFirst().orElseThrow();
        assertThat(bmExit.description()).contains("Stock exited benchmark index");

        // 3. Source 3: Classification / Coverage Change (Voltas reclassified from MID_CAP to LARGE_CAP)
        var classChanges = response.apparentChanges().stream()
            .filter(c -> "CLASSIFICATION_COVERAGE_CHANGE".equals(c.changeCategory())).toList();
        assertThat(classChanges).isNotEmpty();
        var mcapReclass = classChanges.stream().filter(c -> c.securityName().contains("Voltas")).findFirst().orElseThrow();
        assertThat(mcapReclass.description()).contains("AMFI semi-annual market-cap reclassification: MID_CAP -> LARGE_CAP");
    }

    @Test
    @DisplayName("Flags UNVERIFIED_SOURCE when benchmark observation date difference exceeds 45 days")
    void shouldFlagUnverifiedSourceOnDateMismatch() {
        LocalDate portfolioDate = LocalDate.of(2024, 1, 31);
        LocalDate staleBmDate = LocalDate.of(2019, 3, 29); // ~1769 days old
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(201L);
        snap.setSchemeOption(schemeOption);
        snap.setPortfolioDate(portfolioDate);
        snap.setAvailabilityTime(portfolioDate.atStartOfDay().atOffset(ZoneOffset.UTC));

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(201L))
            .thenReturn(Collections.emptyList());

        BenchmarkConstituent staleBc = new BenchmarkConstituent();
        staleBc.setIsin("INE040A01034");
        staleBc.setEffectiveDate(staleBmDate);
        staleBc.setWeight(new BigDecimal("8.750000"));

        when(benchmarkConstituentRepository.findEligibleConstituents(eq(100L), eq(portfolioDate), eq(cutoff)))
            .thenReturn(List.of(staleBc));

        BenchmarkDivergenceResponseDto response = divergenceService.calculateMultiPeriodDivergence(
            1L, portfolioDate, portfolioDate, cutoff
        );

        assertThat(response.periods()).hasSize(1);
        var p = response.periods().get(0);
        assertThat(p.snapshotDateCompatible()).isFalse();
        assertThat(p.snapshotDateDifferenceDays()).isGreaterThan(1700L);
        assertThat(p.periodStatus()).isEqualTo("UNVERIFIED_SOURCE");
        assertThat(p.warningMessage()).contains("Snapshot date mismatch");
        assertThat(response.overallStatus()).contains("STALENESS_WARNINGS");
    }
}
