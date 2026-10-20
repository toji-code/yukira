package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.BenchmarkCompositionComparisonResponseDto;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BenchmarkCompositionComparisonServiceTest {

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

    private BenchmarkCompositionComparisonService comparisonService;

    private SchemeOption schemeOption;
    private Benchmark benchmark;
    private PortfolioSnapshot snapshot;

    @BeforeEach
    void setUp() {
        comparisonService = new BenchmarkCompositionComparisonService(
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

        snapshot = new PortfolioSnapshot();
        snapshot.setId(500L);
        snapshot.setSchemeOption(schemeOption);
        snapshot.setPortfolioDate(LocalDate.of(2024, 1, 15));
        snapshot.setAvailabilityTime(OffsetDateTime.of(2024, 1, 16, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        snapshot.setRevisionSeq(1);

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(schemeOption));
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(benchmark));
        when(benchmarkResolutionService.resolvePrimaryBenchmark(eq(10L), any(LocalDate.class)))
            .thenReturn(new BenchmarkResolutionService.BenchmarkResolutionResult(
                benchmark,
                true,
                "SCHEME_MASTER",
                LocalDate.of(2024, 1, 15),
                "Canonical Primary Benchmark"
            ));
    }

    @Test
    @DisplayName("Calculates holdings overlap, active security positions, and sector spreads deterministically")
    void shouldCalculateHoldingsComparisonSuccessfully() {
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 20, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snapshot));

        // Create 2 holdings in Fund: HDFC Bank (8.80%), ICICI Bank (6.20%)
        Security hdfcSec = new Security();
        hdfcSec.setId(101L);
        hdfcSec.setCanonicalName("HDFC Bank Ltd.");
        hdfcSec.setSector("Financial Services");

        Security iciciSec = new Security();
        iciciSec.setId(102L);
        iciciSec.setCanonicalName("ICICI Bank Ltd.");
        iciciSec.setSector("Financial Services");

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setId(1001L);
        h1.setPortfolioSnapshot(snapshot);
        h1.setSecurity(hdfcSec);
        h1.setReportedWeight(new BigDecimal("0.088000")); // 8.80%

        PortfolioHolding h2 = new PortfolioHolding();
        h2.setId(1002L);
        h2.setPortfolioSnapshot(snapshot);
        h2.setSecurity(iciciSec);
        h2.setReportedWeight(new BigDecimal("0.062000")); // 6.20%

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(500L))
            .thenReturn(List.of(h1, h2));

        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(101L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(hdfcSec, "ISIN", "INE040A01034", LocalDate.of(2020, 1, 1))));
        when(securityIdentifierRepository.findTopBySecurityIdAndIdType(102L, "ISIN"))
            .thenReturn(Optional.of(new SecurityIdentifier(iciciSec, "ISIN", "INE090A01021", LocalDate.of(2020, 1, 1))));

        // Create Benchmark constituents: HDFC Bank (8.75%), ICICI Bank (5.90%), Reliance (7.20% not held in fund)
        BenchmarkConstituent bc1 = new BenchmarkConstituent();
        bc1.setId(2001L);
        bc1.setBenchmark(benchmark);
        bc1.setIsin("INE040A01034");
        bc1.setSymbol("HDFCBANK");
        bc1.setCompanyName("HDFC Bank Ltd.");
        bc1.setIndustry("Financial Services");
        bc1.setWeight(new BigDecimal("8.750000"));
        bc1.setMappingStatus("MAPPED");
        bc1.setEffectiveDate(asOfDate);

        BenchmarkConstituent bc2 = new BenchmarkConstituent();
        bc2.setId(2002L);
        bc2.setBenchmark(benchmark);
        bc2.setIsin("INE090A01021");
        bc2.setSymbol("ICICIBANK");
        bc2.setCompanyName("ICICI Bank Ltd.");
        bc2.setIndustry("Financial Services");
        bc2.setWeight(new BigDecimal("5.900000"));
        bc2.setMappingStatus("MAPPED");
        bc2.setEffectiveDate(asOfDate);

        BenchmarkConstituent bc3 = new BenchmarkConstituent();
        bc3.setId(2003L);
        bc3.setBenchmark(benchmark);
        bc3.setIsin("INE002A01018");
        bc3.setSymbol("RELIANCE");
        bc3.setCompanyName("Reliance Industries Ltd.");
        bc3.setIndustry("Oil Gas & Consumable Fuels");
        bc3.setWeight(new BigDecimal("7.200000"));
        bc3.setMappingStatus("UNMAPPED");
        bc3.setEffectiveDate(asOfDate);

        when(benchmarkConstituentRepository.findEligibleConstituents(eq(100L), eq(asOfDate), eq(cutoff)))
            .thenReturn(List.of(bc1, bc2, bc3));

        // Market cap classifications
        SecurityMarketCapClassification mcapHdfc = new SecurityMarketCapClassification();
        mcapHdfc.setMarketCapCategory("LARGE_CAP");
        mcapHdfc.setMarketCapRank(1);
        when(classificationRepository.findByIsinAndPit(eq("INE040A01034"), eq(asOfDate), eq(cutoff)))
            .thenReturn(List.of(mcapHdfc));

        SecurityMarketCapClassification mcapIcici = new SecurityMarketCapClassification();
        mcapIcici.setMarketCapCategory("LARGE_CAP");
        mcapIcici.setMarketCapRank(2);
        when(classificationRepository.findByIsinAndPit(eq("INE090A01021"), eq(asOfDate), eq(cutoff)))
            .thenReturn(List.of(mcapIcici));

        BenchmarkCompositionComparisonResponseDto response = comparisonService.compareComposition(1L, asOfDate, cutoff);

        assertThat(response).isNotNull();
        assertThat(response.schemeOptionId()).isEqualTo(1L);
        assertThat(response.fundName()).isEqualTo("HDFC Flexi Cap Fund");
        assertThat(response.benchmarkCode()).isEqualTo("NIFTY_500_TRI");
        assertThat(response.benchmarkConstituentCount()).isEqualTo(3);
        assertThat(response.fundHoldingsCount()).isEqualTo(2);

        // Overlap: min(8.80, 8.75) + min(6.20, 5.90) = 8.75 + 5.90 = 14.65%
        assertThat(response.holdingsOverlapPct()).isEqualByComparingTo("14.65");

        // Active positions: HDFC (+0.05%), ICICI (+0.30%), Reliance (-7.20%)
        assertThat(response.activePositions()).hasSize(3);
        var iciciPos = response.activePositions().stream()
            .filter(p -> p.isin().equals("INE090A01021")).findFirst().orElseThrow();
        assertThat(iciciPos.activeWeightPct()).isEqualByComparingTo("0.30");
        assertThat(iciciPos.positionStatus()).isEqualTo("OVERWEIGHT");

        var reliancePos = response.activePositions().stream()
            .filter(p -> p.isin().equals("INE002A01018")).findFirst().orElseThrow();
        assertThat(reliancePos.activeWeightPct()).isEqualByComparingTo("-7.20");
        assertThat(reliancePos.positionStatus()).isEqualTo("BENCHMARK_ONLY");

        // Sector spread: Financial Services: fund = 15.00%, bm = 14.65%, spread = +0.35%
        assertThat(response.sectorExposures()).isNotEmpty();
        var finSector = response.sectorExposures().stream()
            .filter(s -> s.sectorName().equals("Financial Services")).findFirst().orElseThrow();
        assertThat(finSector.activeSectorExposurePct()).isEqualByComparingTo("0.35");

        // Brinson readiness gate
        assertThat(response.brinsonAttributionStatus()).isEqualTo("BLOCKED_PENDING_STOCK_RETURNS");
        assertThat(response.brinsonBlockerReason()).contains("Constituent-level Brinson-Fachler attribution");
    }

    @Test
    @DisplayName("Returns empty diagnostic response when no portfolio snapshot is available under PIT cutoff")
    void shouldReturnEmptyWhenNoSnapshotUnderCutoff() {
        LocalDate asOfDate = LocalDate.of(2023, 1, 1);
        OffsetDateTime cutoff = OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(Collections.emptyList());

        BenchmarkCompositionComparisonResponseDto response = comparisonService.compareComposition(1L, asOfDate, cutoff);

        assertThat(response).isNotNull();
        assertThat(response.fundHoldingsCount()).isEqualTo(0);
        assertThat(response.holdingsOverlapPct()).isEqualByComparingTo("0.00");
        assertThat(response.activePositions()).isEmpty();
        assertThat(response.brinsonAttributionStatus()).isEqualTo("INSUFFICIENT_DATA");
    }
}
