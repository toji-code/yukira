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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioFactorExposureServicePitTest {

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

    @InjectMocks
    private PortfolioFactorExposureService factorExposureService;

    private SchemeOption option;
    private PortfolioSnapshot snap1; // 2026-06-30
    private PortfolioSnapshot snap2; // 2026-08-31
    private Security reliance;
    private Security bosch;
    private Security zydus;

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

        snap1 = new PortfolioSnapshot();
        snap1.setId(101L);
        snap1.setSchemeOption(option);
        snap1.setPortfolioDate(LocalDate.of(2026, 6, 30));
        snap1.setAvailabilityTime(OffsetDateTime.of(2026, 7, 5, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        snap1.setRevisionSeq(1);

        snap2 = new PortfolioSnapshot();
        snap2.setId(102L);
        snap2.setSchemeOption(option);
        snap2.setPortfolioDate(LocalDate.of(2026, 8, 31));
        snap2.setAvailabilityTime(OffsetDateTime.of(2026, 9, 5, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        snap2.setRevisionSeq(1);

        reliance = new Security();
        reliance.setId(201L);
        reliance.setCanonicalName("Reliance Industries Ltd.");
        reliance.setSector("Energy");
        reliance.setAssetClass("Equity");

        bosch = new Security();
        bosch.setId(202L);
        bosch.setCanonicalName("Bosch Ltd.");
        bosch.setSector("Automobile");
        bosch.setAssetClass("Equity");

        zydus = new Security();
        zydus.setId(203L);
        zydus.setCanonicalName("Zydus Wellness Ltd.");
        zydus.setSector("Healthcare");
        zydus.setAssetClass("Equity");

        SecurityIdentifier siRel = new SecurityIdentifier();
        siRel.setSecurity(reliance);
        siRel.setIdType("ISIN");
        siRel.setIdValue("INE002A01018");

        SecurityIdentifier siBosch = new SecurityIdentifier();
        siBosch.setSecurity(bosch);
        siBosch.setIdType("ISIN");
        siBosch.setIdValue("INE323A01026");

        SecurityIdentifier siZydus = new SecurityIdentifier();
        siZydus.setSecurity(zydus);
        siZydus.setIdType("ISIN");
        siZydus.setIdValue("INE010B01027");

        when(securityIdentifierRepository.findBySecurityIdInAndIdType(any(), eq("ISIN")))
            .thenReturn(List.of(siRel, siBosch, siZydus));
    }

    @Test
    @DisplayName("Point-in-Time leakage prevention: classifications published after cutoff cannot be applied")
    void shouldPreventLookAheadLeakageWhenCutoffPriorToPublication() {
        OffsetDateTime cutoffBeforePub = OffsetDateTime.of(2026, 7, 1, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoffBeforePub))
            .thenReturn(List.of(snap1));

        // When cutoff is before publication date (e.g. published 2026-07-06), classificationRepository returns empty
        when(classificationRepository.findEligibleClassifications(eq(LocalDate.of(2026, 6, 30)), eq(cutoffBeforePub)))
            .thenReturn(Collections.emptyList());

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setPortfolioSnapshot(snap1);
        h1.setSecurity(reliance);
        h1.setReportedWeight(new BigDecimal("0.700000"));

        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1));

        PortfolioFactorExposureResponseDto response = factorExposureService.calculateFactorExposure(
            1L, LocalDate.of(2026, 6, 30), cutoffBeforePub
        );

        assertThat(response).isNotNull();
        // Since verified classifications published after cutoff are excluded, status must be APPROXIMATE_REFERENCE
        assertThat(response.currentExposure().marketCap().classificationStatus()).isEqualTo("APPROXIMATE_REFERENCE");
        assertThat(response.currentExposure().coverageLimitations())
            .anyMatch(l -> l.contains("approximate reference"));
    }

    @Test
    @DisplayName("Style drift decomposition separates active rebalancing from AMFI classification shifts")
    void shouldDecomposeStyleDriftIntoRebalancingAndReclassification() {
        OffsetDateTime cutoff = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap2, snap1));

        // Classifications for Snap 1 (Period Jan-Jun 2025, eff Feb-Jul 2026):
        // Reliance: Rank #1 (Large Cap)
        // Bosch: Rank #87 (Large Cap)
        SecurityMarketCapClassification c1Rel = new SecurityMarketCapClassification();
        c1Rel.setIsin("INE002A01018");
        c1Rel.setSecurity(reliance);
        c1Rel.setMarketCapRank(1);
        c1Rel.setMarketCapCategory("LARGE_CAP");
        c1Rel.setRankingPeriod("Jul-Dec 2025");
        c1Rel.setEffectiveFrom(LocalDate.of(2026, 2, 1));
        c1Rel.setEffectiveTo(LocalDate.of(2026, 7, 31));

        SecurityMarketCapClassification c1Bosch = new SecurityMarketCapClassification();
        c1Bosch.setIsin("INE323A01026");
        c1Bosch.setSecurity(bosch);
        c1Bosch.setMarketCapRank(87);
        c1Bosch.setMarketCapCategory("LARGE_CAP"); // In previous period, Bosch was Large Cap
        c1Bosch.setRankingPeriod("Jul-Dec 2025");
        c1Bosch.setEffectiveFrom(LocalDate.of(2026, 2, 1));
        c1Bosch.setEffectiveTo(LocalDate.of(2026, 7, 31));

        when(classificationRepository.findEligibleClassifications(eq(LocalDate.of(2026, 6, 30)), eq(cutoff)))
            .thenReturn(List.of(c1Rel, c1Bosch));

        // Classifications for Snap 2 (Period Jan-Jun 2026, eff Aug 2026 - Jan 2027):
        // Reliance: Rank #1 (Large Cap)
        // Bosch: Rank #101 (Reclassified to Mid Cap!)
        SecurityMarketCapClassification c2Rel = new SecurityMarketCapClassification();
        c2Rel.setIsin("INE002A01018");
        c2Rel.setSecurity(reliance);
        c2Rel.setMarketCapRank(1);
        c2Rel.setMarketCapCategory("LARGE_CAP");
        c2Rel.setRankingPeriod("Jan-Jun 2026");
        c2Rel.setEffectiveFrom(LocalDate.of(2026, 8, 1));
        c2Rel.setEffectiveTo(LocalDate.of(2027, 1, 31));

        SecurityMarketCapClassification c2Bosch = new SecurityMarketCapClassification();
        c2Bosch.setIsin("INE323A01026");
        c2Bosch.setSecurity(bosch);
        c2Bosch.setMarketCapRank(101);
        c2Bosch.setMarketCapCategory("MID_CAP"); // In new period, Bosch moved from Large to Mid Cap!
        c2Bosch.setRankingPeriod("Jan-Jun 2026");
        c2Bosch.setEffectiveFrom(LocalDate.of(2026, 8, 1));
        c2Bosch.setEffectiveTo(LocalDate.of(2027, 1, 31));

        when(classificationRepository.findEligibleClassifications(eq(LocalDate.of(2026, 8, 31)), eq(cutoff)))
            .thenReturn(List.of(c2Rel, c2Bosch));

        // Holdings for Snap 1: Reliance 60%, Bosch 40% (Total Large Cap = 100%)
        PortfolioHolding h1Rel = new PortfolioHolding();
        h1Rel.setPortfolioSnapshot(snap1);
        h1Rel.setSecurity(reliance);
        h1Rel.setReportedWeight(new BigDecimal("0.600000"));

        PortfolioHolding h1Bosch = new PortfolioHolding();
        h1Bosch.setPortfolioSnapshot(snap1);
        h1Bosch.setSecurity(bosch);
        h1Bosch.setReportedWeight(new BigDecimal("0.400000"));

        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1Rel, h1Bosch));

        // Holdings for Snap 2: Reliance 60%, Bosch 40% (Active weights didn't change at all!)
        PortfolioHolding h2Rel = new PortfolioHolding();
        h2Rel.setPortfolioSnapshot(snap2);
        h2Rel.setSecurity(reliance);
        h2Rel.setReportedWeight(new BigDecimal("0.600000"));

        PortfolioHolding h2Bosch = new PortfolioHolding();
        h2Bosch.setPortfolioSnapshot(snap2);
        h2Bosch.setSecurity(bosch);
        h2Bosch.setReportedWeight(new BigDecimal("0.400000"));

        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(102L))
            .thenReturn(List.of(h2Rel, h2Bosch));

        PortfolioFactorExposureResponseDto response = factorExposureService.calculateFactorExposure(
            1L, LocalDate.of(2026, 8, 31), cutoff
        );

        assertThat(response).isNotNull();
        assertThat(response.driftHistory()).hasSize(2);

        var snap2Drift = response.driftHistory().get(1).deltaFromPrevious();
        assertThat(snap2Drift).isNotNull();
        assertThat(snap2Drift.styleDriftAttribution()).isNotNull();

        var attribution = snap2Drift.styleDriftAttribution();

        // Active rebalancing deltas must be 0.00% because weights didn't change!
        assertThat(attribution.largeCapRebalanceDeltaPct()).isEqualByComparingTo("0.00");
        assertThat(attribution.midCapRebalanceDeltaPct()).isEqualByComparingTo("0.00");

        // Classification deltas must show -40.00% in Large Cap and +40.00% in Mid Cap!
        assertThat(attribution.largeCapReclassifyDeltaPct()).isEqualByComparingTo("-40.00");
        assertThat(attribution.midCapReclassifyDeltaPct()).isEqualByComparingTo("40.00");

        // Net deltas must equal total category changes
        assertThat(attribution.largeCapNetDeltaPct()).isEqualByComparingTo("-40.00");
        assertThat(attribution.midCapNetDeltaPct()).isEqualByComparingTo("40.00");

        // Reclassified holdings list must explicitly identify Bosch!
        assertThat(attribution.reclassifiedHoldings()).hasSize(1);
        var reclassHolding = attribution.reclassifiedHoldings().get(0);
        assertThat(reclassHolding.companyName()).contains("Bosch");
        assertThat(reclassHolding.previousCategory()).isEqualTo("LARGE_CAP");
        assertThat(reclassHolding.previousRank()).isEqualTo(87);
        assertThat(reclassHolding.currentCategory()).isEqualTo("MID_CAP");
        assertThat(reclassHolding.currentRank()).isEqualTo(101);
        assertThat(reclassHolding.portfolioWeightPct()).isEqualByComparingTo("40.00");
        assertThat(reclassHolding.shiftDescription()).contains("Reclassified by AMFI from LARGE_CAP (#87) to MID_CAP (#101)");
    }
}
