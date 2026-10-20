package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.PortfolioFactorExposureResponseDto;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.BenchmarkResolutionService;
import com.yukira.backend.service.PortfolioFactorExposureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
class PortfolioFactorExposureServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private PortfolioHoldingRepository holdingRepository;

    @Mock
    private BenchmarkResolutionService benchmarkResolutionService;

    @Mock
    private SecurityMarketCapClassificationRepository classificationRepository;

    @Mock
    private SecurityIdentifierRepository securityIdentifierRepository;

    @Mock
    private com.yukira.backend.service.portfolio.ActiveReturnAttributionService activeReturnAttributionService;

    @InjectMocks
    private PortfolioFactorExposureService factorExposureService;

    private SchemeOption option;
    private PortfolioSnapshot snap1;
    private PortfolioSnapshot snap2;

    @BeforeEach
    void setUp() {
        Scheme scheme = new Scheme();
        scheme.setId(10L);
        scheme.setName("HDFC Flexi Cap Fund");
        scheme.setCode("HDFC_FLEXI");

        SchemePlan plan = new SchemePlan();
        plan.setScheme(scheme);
        plan.setPlanType("DIRECT");

        option = new SchemeOption();
        option.setId(1L);
        option.setPlan(plan);
        option.setAmfiCode("118955");
        option.setOptionType("GROWTH");

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(option));

        Benchmark bm = new Benchmark();
        bm.setCode("NIFTY_500_TRI");
        bm.setName("Nifty 500 Total Returns Index");
        when(benchmarkResolutionService.resolvePrimaryBenchmark(eq(10L), any(LocalDate.class)))
            .thenReturn(new BenchmarkResolutionService.BenchmarkResolutionResult(bm, true, "EXPLICIT", LocalDate.of(2020, 1, 1), "OK"));

        // Snapshot 1: 2024-01-31 (Partial Top 10 disclosure: 50% weight)
        snap1 = new PortfolioSnapshot();
        snap1.setId(101L);
        snap1.setSchemeOption(option);
        snap1.setPortfolioDate(LocalDate.of(2024, 1, 31));
        snap1.setAvailabilityTime(OffsetDateTime.of(2024, 2, 5, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        snap1.setRevisionSeq(1);
        snap1.setReportedHoldingsCount(2);
        snap1.setSumReportedWeights(new BigDecimal("0.500000"));

        // Snapshot 2: 2024-02-29 (Full disclosure: 100% weight)
        snap2 = new PortfolioSnapshot();
        snap2.setId(102L);
        snap2.setSchemeOption(option);
        snap2.setPortfolioDate(LocalDate.of(2024, 2, 29));
        snap2.setAvailabilityTime(OffsetDateTime.of(2024, 3, 5, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        snap2.setRevisionSeq(1);
        snap2.setReportedHoldingsCount(3);
        snap2.setSumReportedWeights(new BigDecimal("1.000000"));
    }

    @Test
    @DisplayName("Calculates deterministic factor exposure and detects non-comparable coverage change")
    void shouldCalculateFactorExposureAndDetectCoverageShift() {
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 3, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap2, snap1));

        // Holdings for Snap 1 (50% Large Cap)
        Security hdfcBank = new Security();
        hdfcBank.setId(1L);
        hdfcBank.setCanonicalName("HDFC Bank Ltd.");
        hdfcBank.setSector("Financial Services");
        hdfcBank.setAssetClass("Equity");

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setId(1001L);
        h1.setPortfolioSnapshot(snap1);
        h1.setSecurity(hdfcBank);
        h1.setReportedWeight(new BigDecimal("0.500000"));

        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1));

        // Holdings for Snap 2 (60% Large Cap, 30% Mid Cap, 10% Cash)
        Security zydus = new Security();
        zydus.setId(2L);
        zydus.setCanonicalName("Zydus Lifesciences Limited");
        zydus.setSector("Healthcare");
        zydus.setAssetClass("Equity");

        Security cashSec = new Security();
        cashSec.setId(3L);
        cashSec.setCanonicalName("Cash & Cash Equivalents");
        cashSec.setSector("Cash");
        cashSec.setAssetClass("Cash");

        PortfolioHolding h2a = new PortfolioHolding();
        h2a.setId(1002L);
        h2a.setPortfolioSnapshot(snap2);
        h2a.setSecurity(hdfcBank);
        h2a.setReportedWeight(new BigDecimal("0.600000"));

        PortfolioHolding h2b = new PortfolioHolding();
        h2b.setId(1003L);
        h2b.setPortfolioSnapshot(snap2);
        h2b.setSecurity(zydus);
        h2b.setReportedWeight(new BigDecimal("0.300000"));

        PortfolioHolding h2c = new PortfolioHolding();
        h2c.setId(1004L);
        h2c.setPortfolioSnapshot(snap2);
        h2c.setSecurity(cashSec);
        h2c.setReportedWeight(new BigDecimal("0.100000"));

        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(102L))
            .thenReturn(List.of(h2a, h2b, h2c));

        PortfolioFactorExposureResponseDto response = factorExposureService.calculateFactorExposure(
            1L, LocalDate.of(2024, 2, 29), cutoff
        );

        assertThat(response).isNotNull();
        assertThat(response.analysisStatus()).isEqualTo("SUCCESS");
        assertThat(response.fundName()).isEqualTo("HDFC Flexi Cap Fund");
        assertThat(response.benchmarkCode()).isEqualTo("NIFTY_500_TRI");

        // Latest snapshot check (Snap 2)
        assertThat(response.currentExposure()).isNotNull();
        assertThat(response.currentExposure().portfolioDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(response.currentExposure().sumReportedWeightsPct()).isEqualByComparingTo("100.00");
        assertThat(response.currentExposure().marketCap().largeCapWeightPct()).isEqualByComparingTo("60.00");
        assertThat(response.currentExposure().marketCap().midCapWeightPct()).isEqualByComparingTo("30.00");
        assertThat(response.currentExposure().marketCap().cashWeightPct()).isEqualByComparingTo("10.00");
        assertThat(response.currentExposure().completenessState()).isEqualTo("FULL_DISCLOSURE");

        // Multi-period drift history check
        assertThat(response.driftHistory()).hasSize(2);
        var secondSnapshotDrift = response.driftHistory().get(1).deltaFromPrevious();
        assertThat(secondSnapshotDrift).isNotNull();
        assertThat(secondSnapshotDrift.largeCapDeltaPct()).isEqualByComparingTo("10.00");
        assertThat(secondSnapshotDrift.midCapDeltaPct()).isEqualByComparingTo("30.00");
        assertThat(secondSnapshotDrift.cashDeltaPct()).isEqualByComparingTo("10.00");
        assertThat(secondSnapshotDrift.coverageDeltaPct()).isEqualByComparingTo("50.00");

        // Invariant: Because coverage shifted by 50%, it must be marked non-comparable!
        assertThat(secondSnapshotDrift.isComparable()).isFalse();
        assertThat(secondSnapshotDrift.comparabilityNote()).contains("coverage");

        // Attribution check: transparently reports missing factor series
        assertThat(response.returnAttribution().modelStatus()).isEqualTo("METHODOLOGY_PREREQUISITES_MISSING");
        assertThat(response.returnAttribution().missingPrerequisites()).isNotEmpty();
    }

    @Test
    @DisplayName("Enforces Point-in-Time cutoff: future snapshots are completely excluded")
    void shouldExcludeFutureSnapshotsUnderPitCutoff() {
        // Cutoff as of 2024-02-10: Snap 2 (available 2024-03-05) must NOT leak into query!
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 2, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap1)); // only snap 1 is known

        Security hdfcBank = new Security();
        hdfcBank.setId(1L);
        hdfcBank.setCanonicalName("HDFC Bank Ltd.");
        hdfcBank.setSector("Financial Services");
        hdfcBank.setAssetClass("Equity");

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setId(1001L);
        h1.setPortfolioSnapshot(snap1);
        h1.setSecurity(hdfcBank);
        h1.setReportedWeight(new BigDecimal("0.500000"));

        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1));

        PortfolioFactorExposureResponseDto response = factorExposureService.calculateFactorExposure(
            1L, LocalDate.of(2024, 2, 10), cutoff
        );

        assertThat(response.driftHistory()).hasSize(1);
        assertThat(response.driftHistory().get(0).portfolioDate()).isEqualTo(LocalDate.of(2024, 1, 31));
        assertThat(response.currentExposure().portfolioDate()).isEqualTo(LocalDate.of(2024, 1, 31));
        assertThat(response.currentExposure().completenessState()).isEqualTo("PARTIAL_DISCLOSURE");
    }

    @Test
    @DisplayName("Returns NO_HOLDINGS_SNAPSHOTS when no snapshots exist under cutoff")
    void shouldHandleZeroSnapshotsGracefully() {
        OffsetDateTime cutoff = OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(Collections.emptyList());

        PortfolioFactorExposureResponseDto response = factorExposureService.calculateFactorExposure(
            1L, LocalDate.of(2023, 1, 1), cutoff
        );

        assertThat(response.analysisStatus()).isEqualTo("NO_HOLDINGS_SNAPSHOTS");
        assertThat(response.currentExposure()).isNull();
        assertThat(response.driftHistory()).isEmpty();
        assertThat(response.returnAttribution().modelStatus()).isEqualTo("METHODOLOGY_PREREQUISITES_MISSING");
    }
}
