package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.lookthrough.ResearchPortfolioFundItemDto;
import com.yukira.backend.dto.portfolio.stress.*;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
public class PortfolioStressReplayServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private NavObservationRepository navObservationRepository;

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Mock
    private PortfolioSnapshotRepository portfolioSnapshotRepository;

    @Mock
    private PortfolioHoldingRepository portfolioHoldingRepository;

    @Mock
    private SecurityMarketCapClassificationRepository classificationRepository;

    private PortfolioStressReplayService service;

    private SchemeOption fundA;
    private SchemeOption fundB;
    private Benchmark benchmarkNifty500;

    @BeforeEach
    void setUp() {
        service = new PortfolioStressReplayService(
            schemeOptionRepository,
            navObservationRepository,
            benchmarkRepository,
            benchmarkObservationRepository,
            portfolioSnapshotRepository,
            portfolioHoldingRepository,
            classificationRepository
        );

        Scheme schemeA = new Scheme();
        schemeA.setName("HDFC Flexi Cap Fund");
        SchemePlan planA = new SchemePlan();
        planA.setScheme(schemeA);
        fundA = new SchemeOption();
        fundA.setId(1L);
        fundA.setPlan(planA);
        fundA.setOptionType("GROWTH");
        fundA.setAmfiCode("118955");

        Scheme schemeB = new Scheme();
        schemeB.setName("Parag Parikh Flexi Cap Fund");
        SchemePlan planB = new SchemePlan();
        planB.setScheme(schemeB);
        fundB = new SchemeOption();
        fundB.setId(2L);
        fundB.setPlan(planB);
        fundB.setOptionType("GROWTH");
        fundB.setAmfiCode("122639");

        benchmarkNifty500 = new Benchmark();
        benchmarkNifty500.setId(123L);
        benchmarkNifty500.setCode("NIFTY_500_TRI");
        benchmarkNifty500.setName("Nifty 500 Total Returns Index");
    }

    @Test
    @DisplayName("Predefined episodes catalog marks COVID-2020 as unavailable with explicit epistemic rationale")
    void testPredefinedEpisodesCatalogAvailability() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(1L, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(2L, new BigDecimal("40.00"))
        );

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));
        when(schemeOptionRepository.findById(2L)).thenReturn(Optional.of(fundB));
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(benchmarkNifty500));
        when(benchmarkObservationRepository.findMinEffectiveDateByBenchmarkId(123L))
            .thenReturn(Optional.of(LocalDate.of(2021, 1, 15)));

        // Fund observations starting 2021-01-15
        NavObservation obsA = createNav(fundA, LocalDate.of(2021, 1, 15), new BigDecimal("1000.00"));
        NavObservation obsB = createNav(fundB, LocalDate.of(2021, 1, 15), new BigDecimal("50.00"));
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), any(LocalDate.class), any(OffsetDateTime.class)))
            .thenReturn(List.of(obsA));
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(2L), any(LocalDate.class), any(OffsetDateTime.class)))
            .thenReturn(List.of(obsB));

        List<HistoricalMarketEpisodeDto> episodes = service.getAvailableEpisodes(funds, OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(episodes).isNotEmpty();

        // 1. COVID 2020 must be unavailable because benchmark data starts 2021-01-15
        Optional<HistoricalMarketEpisodeDto> covid = episodes.stream()
            .filter(e -> "EPISODE_2020_COVID_SHOCK".equals(e.episodeId()))
            .findFirst();
        assertThat(covid).isPresent();
        assertThat(covid.get().isAvailable()).isFalse();
        assertThat(covid.get().unavailabilityReason()).contains("observations start on 2021-01-15");

        // 2. Verified episodes with data after 2021-01-15 should be available
        Optional<HistoricalMarketEpisodeDto> rateHike = episodes.stream()
            .filter(e -> "EPISODE_2021_2022_RATE_HIKE_DRAWDOWN".equals(e.episodeId()))
            .findFirst();
        assertThat(rateHike).isPresent();
        assertThat(rateHike.get().isAvailable()).isTrue();
        assertThat(rateHike.get().unavailabilityReason()).isNull();
    }

    @Test
    @DisplayName("Validation fails for invalid allocations or missing funds")
    void testReplayValidationFailures() {
        // Empty funds
        assertThatThrownBy(() -> service.executeStressReplay(new PortfolioStressReplayRequestDto(
            List.of(), null, LocalDate.of(2022, 1, 1), LocalDate.of(2022, 6, 1), null, null
        ))).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cannot be empty");

        // Negative weight
        assertThatThrownBy(() -> service.executeStressReplay(new PortfolioStressReplayRequestDto(
            List.of(new ResearchPortfolioFundItemDto(1L, new BigDecimal("-10.00"))),
            null, LocalDate.of(2022, 1, 1), LocalDate.of(2022, 6, 1), null, null
        ))).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cannot be negative");

        // Start date after end date
        assertThatThrownBy(() -> service.executeStressReplay(new PortfolioStressReplayRequestDto(
            List.of(new ResearchPortfolioFundItemDto(1L, new BigDecimal("100.00"))),
            null, LocalDate.of(2022, 6, 1), LocalDate.of(2022, 1, 1), null, null
        ))).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cannot be after end date");
    }

    @Test
    @DisplayName("Deterministic arithmetic: Daily rebalanced portfolio returns, cumulative return, drawdowns, and constituent contributions")
    void testDeterministicReplayArithmetic() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(1L, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(2L, new BigDecimal("40.00"))
        );

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));
        when(schemeOptionRepository.findById(2L)).thenReturn(Optional.of(fundB));
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(benchmarkNifty500));

        LocalDate d0 = LocalDate.of(2022, 1, 3);
        LocalDate d1 = LocalDate.of(2022, 1, 4);
        LocalDate d2 = LocalDate.of(2022, 1, 5);
        LocalDate d3 = LocalDate.of(2022, 1, 6);

        // Day 0: Fund A NAV 100, Fund B NAV 100, Benchmark 1000
        // Day 1: Fund A NAV 95 (-5%), Fund B NAV 90 (-10%), Benchmark 920 (-8%)
        //   Port daily R1 = 0.60 * (-0.05) + 0.40 * (-0.10) = -0.07 (-7.00%)
        // Day 2: Fund A NAV 90 (-5.263%), Fund B NAV 85 (-5.556%), Benchmark 880 (-4.348%)
        // Day 3: Fund A NAV 95 (+5.556%), Fund B NAV 90 (+5.882%), Benchmark 910 (+3.409%)

        List<NavObservation> fundANavs = List.of(
            createNav(fundA, d0, new BigDecimal("100.00")),
            createNav(fundA, d1, new BigDecimal("95.00")),
            createNav(fundA, d2, new BigDecimal("90.00")),
            createNav(fundA, d3, new BigDecimal("95.00"))
        );

        List<NavObservation> fundBNavs = List.of(
            createNav(fundB, d0, new BigDecimal("100.00")),
            createNav(fundB, d1, new BigDecimal("90.00")),
            createNav(fundB, d2, new BigDecimal("85.00")),
            createNav(fundB, d3, new BigDecimal("90.00"))
        );

        List<BenchmarkObservation> benchNavs = List.of(
            createBenchmarkObs(benchmarkNifty500, d0, new BigDecimal("1000.00")),
            createBenchmarkObs(benchmarkNifty500, d1, new BigDecimal("920.00")),
            createBenchmarkObs(benchmarkNifty500, d2, new BigDecimal("880.00")),
            createBenchmarkObs(benchmarkNifty500, d3, new BigDecimal("910.00"))
        );

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), eq(d3), any(OffsetDateTime.class)))
            .thenReturn(fundANavs);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(2L), eq(d3), any(OffsetDateTime.class)))
            .thenReturn(fundBNavs);
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(123L), eq(d3), any(OffsetDateTime.class)))
            .thenReturn(benchNavs);

        PortfolioStressReplayRequestDto request = new PortfolioStressReplayRequestDto(
            funds, null, d0, d3, "NIFTY_500_TRI", OffsetDateTime.now(ZoneOffset.UTC)
        );

        PortfolioStressReplayResponseDto response = service.executeStressReplay(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("HYPOTHETICAL_RECONSTRUCTION");
        assertThat(response.alignedTradingDaysCount()).isEqualTo(4);

        // Benchmark cumulative return from 1000 to 910 is -9.00%
        assertThat(response.benchmarkCumulativeReturnPct()).isEqualByComparingTo(new BigDecimal("-9.00"));

        // Day 1: Port Return = -7.00%
        assertThat(response.trajectory().get(1).portfolioCumulativeReturnPct())
            .isEqualByComparingTo(new BigDecimal("-7.00"));

        // Max drawdown should be negative
        assertThat(response.portfolioMaxDrawdownPct()).isLessThan(BigDecimal.ZERO);
        assertThat(response.benchmarkMaxDrawdownPct()).isLessThan(BigDecimal.ZERO);

        // Peak date is d0
        assertThat(response.peakDate()).isEqualTo(d0);
        // Trough date is d2
        assertThat(response.troughDate()).isEqualTo(d2);

        // Constituent results: Fund A and Fund B
        assertThat(response.constituentResults()).hasSize(2);
        ConstituentReplayResultDto resA = response.constituentResults().get(0);
        ConstituentReplayResultDto resB = response.constituentResults().get(1);

        assertThat(resA.schemeOptionId()).isEqualTo(1L);
        assertThat(resA.allocationWeightPct()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(resB.schemeOptionId()).isEqualTo(2L);
        assertThat(resB.allocationWeightPct()).isEqualByComparingTo(new BigDecimal("40.00"));

        // Verify Carino logarithmic attribution exact reconciliation
        assertThat(response.attributionConvention()).isEqualTo("CARINO_LOGARITHMIC_LINKING");
        BigDecimal sumContrib = resA.contributionPct().add(resB.contributionPct());
        BigDecimal portCum = response.portfolioCumulativeReturnPct();
        assertThat(sumContrib.subtract(portCum).abs()).isLessThanOrEqualTo(new BigDecimal("0.02"));
        assertThat(response.attributionResidualPct()).isEqualByComparingTo(BigDecimal.ZERO);

        // Verify standalone linear buy-and-hold contributions and compounding residual
        assertThat(resA.standaloneContributionPct()).isNotNull();
        assertThat(resB.standaloneContributionPct()).isNotNull();
        assertThat(response.compoundingResidualPct()).isNotNull();
        assertThat(response.standaloneSumReturnPct()).isNotNull();

        // Modeled Sensitivity Scenarios must be present and labeled
        assertThat(response.modeledScenarios()).hasSize(3);
        for (ModeledSensitivityScenarioDto scenario : response.modeledScenarios()) {
            assertThat(scenario.epistemicStatus()).isEqualTo("SENSITIVITY_ESTIMATE_ONLY");
            assertThat(scenario.omittedEffects()).isNotEmpty();
        }

        // Epistemic classifications
        assertThat(response.epistemicClassifications())
            .containsEntry("PortfolioReconstruction", "HYPOTHETICAL_RECONSTRUCTION (Target-Weight Daily Rebalanced)")
            .containsEntry("ModeledStress", "SENSITIVITY_ESTIMATE_ONLY (Linear Empirical OLS Beta Approximation)");
    }

    @Test
    @DisplayName("Carino Attribution: Exact multi-period reconciliation with changing relative constituent performance")
    void testCarinoAttributionExactReconciliationMultiConstituentChangingPerformance() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(1L, new BigDecimal("50.00")),
            new ResearchPortfolioFundItemDto(2L, new BigDecimal("50.00"))
        );

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));
        when(schemeOptionRepository.findById(2L)).thenReturn(Optional.of(fundB));
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(benchmarkNifty500));

        LocalDate d0 = LocalDate.of(2023, 1, 2);
        LocalDate d1 = LocalDate.of(2023, 1, 3);
        LocalDate d2 = LocalDate.of(2023, 1, 4);

        // Day 1: Fund A +10%, Fund B -10% -> Port return = 0.0%
        // Day 2: Fund A -10%, Fund B +10% -> Port return = 0.0%
        // Over 2 days: Port cumulative return = 0.00%
        // Standalone returns: Fund A = 1.10 * 0.90 - 1 = -1.00%, Fund B = 0.90 * 1.10 - 1 = -1.00%
        // Linear buy-and-hold sum = 0.5 * (-1.00%) + 0.5 * (-1.00%) = -1.00%
        // Compounding residual = 0.00% - (-1.00%) = +1.00% (the rebalancing premium!)
        // Carino linking: C_A = 0.00%, C_B = 0.00%, Sum = 0.00% == Port return!
        List<NavObservation> navsA = List.of(
            createNav(fundA, d0, new BigDecimal("100.00")),
            createNav(fundA, d1, new BigDecimal("110.00")),
            createNav(fundA, d2, new BigDecimal("99.00"))
        );
        List<NavObservation> navsB = List.of(
            createNav(fundB, d0, new BigDecimal("100.00")),
            createNav(fundB, d1, new BigDecimal("90.00")),
            createNav(fundB, d2, new BigDecimal("99.00"))
        );
        List<BenchmarkObservation> benchNavs = List.of(
            createBenchmarkObs(benchmarkNifty500, d0, new BigDecimal("1000.00")),
            createBenchmarkObs(benchmarkNifty500, d1, new BigDecimal("1010.00")),
            createBenchmarkObs(benchmarkNifty500, d2, new BigDecimal("1005.00"))
        );

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), eq(d2), any(OffsetDateTime.class)))
            .thenReturn(navsA);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(2L), eq(d2), any(OffsetDateTime.class)))
            .thenReturn(navsB);
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(123L), eq(d2), any(OffsetDateTime.class)))
            .thenReturn(benchNavs);

        PortfolioStressReplayRequestDto request = new PortfolioStressReplayRequestDto(
            funds, null, d0, d2, "NIFTY_500_TRI", OffsetDateTime.now(ZoneOffset.UTC)
        );

        PortfolioStressReplayResponseDto response = service.executeStressReplay(request);

        assertThat(response.portfolioCumulativeReturnPct()).isEqualByComparingTo(new BigDecimal("0.00"));
        ConstituentReplayResultDto cA = response.constituentResults().get(0);
        ConstituentReplayResultDto cB = response.constituentResults().get(1);

        // Standalone returns are -1.00%
        assertThat(cA.cumulativeReturnPct()).isEqualByComparingTo(new BigDecimal("-1.00"));
        assertThat(cB.cumulativeReturnPct()).isEqualByComparingTo(new BigDecimal("-1.00"));

        // Standalone linear contributions are -0.50%
        assertThat(cA.standaloneContributionPct()).isEqualByComparingTo(new BigDecimal("-0.50"));
        assertThat(cB.standaloneContributionPct()).isEqualByComparingTo(new BigDecimal("-0.50"));
        assertThat(response.standaloneSumReturnPct()).isEqualByComparingTo(new BigDecimal("-1.00"));

        // Compounding residual is +1.00%
        assertThat(response.compoundingResidualPct()).isEqualByComparingTo(new BigDecimal("1.00"));

        // Carino contributions are 0.00% each, summing to exactly 0.00% (Port Return!)
        assertThat(cA.contributionPct()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(cB.contributionPct()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(response.attributionResidualPct()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Holdings proximity selection picks nearest historical snapshot instead of newest")
    void testHoldingsSnapshotProximitySelection() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(1L, new BigDecimal("100.00"))
        );

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(benchmarkNifty500));

        LocalDate d0 = LocalDate.of(2024, 2, 8);
        LocalDate d1 = LocalDate.of(2024, 2, 9);

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), eq(d1), any(OffsetDateTime.class)))
            .thenReturn(List.of(createNav(fundA, d0, new BigDecimal("100")), createNav(fundA, d1, new BigDecimal("98"))));
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(123L), eq(d1), any(OffsetDateTime.class)))
            .thenReturn(List.of(createBenchmarkObs(benchmarkNifty500, d0, new BigDecimal("1000")), createBenchmarkObs(benchmarkNifty500, d1, new BigDecimal("990"))));

        // Fund has two snapshots: 2026-09-30 (newest, ~950 days away) and 2024-01-31 (only 9 days away!)
        PortfolioSnapshot snap2026 = new PortfolioSnapshot();
        snap2026.setId(100L);
        snap2026.setPortfolioDate(LocalDate.of(2026, 9, 30));

        PortfolioSnapshot snap2024 = new PortfolioSnapshot();
        snap2024.setId(101L);
        snap2024.setPortfolioDate(LocalDate.of(2024, 1, 31));

        // Repository returns snaps ordered by portfolioDate desc
        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any(OffsetDateTime.class)))
            .thenReturn(List.of(snap2026, snap2024));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of());

        PortfolioStressReplayRequestDto request = new PortfolioStressReplayRequestDto(
            funds, null, d0, d1, "NIFTY_500_TRI", OffsetDateTime.now(ZoneOffset.UTC)
        );

        PortfolioStressReplayResponseDto response = service.executeStressReplay(request);

        // Must pick 2024-01-31, which is 9 days away (<= 45 days, so TEMPORALLY_ALIGNED)
        assertThat(response.exposureContext().observationDate()).isEqualTo(LocalDate.of(2024, 1, 31));
        assertThat(response.exposureContext().daysMismatchFromEpisodeEnd()).isEqualTo(9);
        assertThat(response.exposureContext().comparabilityState()).isEqualTo("TEMPORALLY_ALIGNED");
        assertThat(response.exposureContext().epistemicWarning()).contains("Observed month-end holdings snapshot near stress period");
    }

    @Test
    @DisplayName("Holdings temporal mismatch disclosure flags distant portfolio snapshot (> 45 days)")
    void testTemporalMismatchDisclosure() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(1L, new BigDecimal("100.00"))
        );

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(benchmarkNifty500));

        LocalDate d0 = LocalDate.of(2022, 6, 1);
        LocalDate d1 = LocalDate.of(2022, 6, 2);

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), eq(d1), any(OffsetDateTime.class)))
            .thenReturn(List.of(createNav(fundA, d0, new BigDecimal("100")), createNav(fundA, d1, new BigDecimal("98"))));
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(123L), eq(d1), any(OffsetDateTime.class)))
            .thenReturn(List.of(createBenchmarkObs(benchmarkNifty500, d0, new BigDecimal("1000")), createBenchmarkObs(benchmarkNifty500, d1, new BigDecimal("990"))));

        // Snapshot is on 2026-09-30, which is > 1500 days after 2022-06-02!
        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(99L);
        snap.setPortfolioDate(LocalDate.of(2026, 9, 30));
        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any(OffsetDateTime.class)))
            .thenReturn(List.of(snap));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(99L))
            .thenReturn(List.of());

        PortfolioStressReplayRequestDto request = new PortfolioStressReplayRequestDto(
            funds, null, d0, d1, "NIFTY_500_TRI", OffsetDateTime.now(ZoneOffset.UTC)
        );

        PortfolioStressReplayResponseDto response = service.executeStressReplay(request);

        assertThat(response.exposureContext().comparabilityState()).isEqualTo("SIGNIFICANT_TEMPORAL_MISMATCH");
        assertThat(response.exposureContext().epistemicWarning())
            .contains("days from stress period");
    }

    @Test
    @DisplayName("Benchmark Identity Integrity: Explicit benchmark identification prevents silent substitution")
    void testBenchmarkIdentityIntegrity() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(1L, new BigDecimal("100.00"))
        );

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(benchmarkNifty500));

        LocalDate d0 = LocalDate.of(2022, 1, 3);
        LocalDate d1 = LocalDate.of(2022, 1, 4);

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), eq(d1), any(OffsetDateTime.class)))
            .thenReturn(List.of(createNav(fundA, d0, new BigDecimal("100")), createNav(fundA, d1, new BigDecimal("102"))));
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(123L), eq(d1), any(OffsetDateTime.class)))
            .thenReturn(List.of(createBenchmarkObs(benchmarkNifty500, d0, new BigDecimal("1000")), createBenchmarkObs(benchmarkNifty500, d1, new BigDecimal("1010"))));

        PortfolioStressReplayRequestDto request = new PortfolioStressReplayRequestDto(
            funds, null, d0, d1, "NIFTY_500_TRI", OffsetDateTime.now(ZoneOffset.UTC)
        );

        PortfolioStressReplayResponseDto response = service.executeStressReplay(request);

        assertThat(response.benchmarkCode()).isEqualTo("NIFTY_500_TRI");
        assertThat(response.benchmarkName()).isEqualTo("Nifty 500 Total Returns Index");
        assertThat(response.benchmarkId()).isEqualTo(123L);
        assertThat(response.benchmarkCode()).isNotEqualTo("NIFTY_50");
        assertThat(response.benchmarkCode()).isNotEqualTo("NIFTY_50_TRI");
    }

    @Test
    @DisplayName("Carino Scaling Factor: Deterministic edge cases, numerical stability, and domain limits")
    void testCarinoScalingFactorProperties() {
        // Zero return limit
        assertThat(PortfolioStressReplayService.calculateCarinoScalingFactor(0.0)).isEqualTo(1.0);

        // Sub-epsilon near-zero values
        assertThat(PortfolioStressReplayService.calculateCarinoScalingFactor(1e-16)).isEqualTo(1.0);
        assertThat(PortfolioStressReplayService.calculateCarinoScalingFactor(-1e-16)).isEqualTo(1.0);

        // Near-zero value stable via Math.log1p: ln(1 + 1e-8) / 1e-8 approx 1 - 0.5e-8
        double kNearZero = PortfolioStressReplayService.calculateCarinoScalingFactor(1e-8);
        assertThat(kNearZero).isCloseTo(1.0 - 0.5e-8, org.assertj.core.data.Offset.offset(1e-14));

        // Normal returns: +10%
        double kPlus10 = PortfolioStressReplayService.calculateCarinoScalingFactor(0.10);
        assertThat(kPlus10).isCloseTo(Math.log(1.10) / 0.10, org.assertj.core.data.Offset.offset(1e-15));

        // Normal returns: -10%
        double kMinus10 = PortfolioStressReplayService.calculateCarinoScalingFactor(-0.10);
        assertThat(kMinus10).isCloseTo(Math.log(0.90) / -0.10, org.assertj.core.data.Offset.offset(1e-15));

        // Extreme loss domain: r = -1.0 (100% loss)
        assertThat(PortfolioStressReplayService.calculateCarinoScalingFactor(-1.0)).isEqualTo(1.0);

        // Invalid domains: r < -1.0
        assertThat(PortfolioStressReplayService.calculateCarinoScalingFactor(-1.5)).isEqualTo(1.0);

        // Non-finite values
        assertThat(PortfolioStressReplayService.calculateCarinoScalingFactor(Double.NaN)).isEqualTo(1.0);
        assertThat(PortfolioStressReplayService.calculateCarinoScalingFactor(Double.POSITIVE_INFINITY)).isEqualTo(1.0);
        assertThat(PortfolioStressReplayService.calculateCarinoScalingFactor(Double.NEGATIVE_INFINITY)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Holdings selection tie-breaking: Equidistant snapshot preceding asOfDate takes priority over future snapshot")
    void testHoldingsSnapshotSelectionTieBreakingPrecedingOverFuture() {
        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(1L, new BigDecimal("100.00"))
        );

        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(benchmarkNifty500));

        LocalDate asOfDate = LocalDate.of(2024, 2, 15);
        LocalDate d0 = LocalDate.of(2024, 2, 14);
        LocalDate d1 = asOfDate;

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), eq(d1), any(OffsetDateTime.class)))
            .thenReturn(List.of(createNav(fundA, d0, new BigDecimal("100")), createNav(fundA, d1, new BigDecimal("101"))));
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(123L), eq(d1), any(OffsetDateTime.class)))
            .thenReturn(List.of(createBenchmarkObs(benchmarkNifty500, d0, new BigDecimal("1000")), createBenchmarkObs(benchmarkNifty500, d1, new BigDecimal("1010"))));

        // Two equidistant snapshots:
        // Snapshot Preceding: 2024-02-05 (10 days before)
        // Snapshot Future:    2024-02-25 (10 days after)
        PortfolioSnapshot snapPreceding = new PortfolioSnapshot();
        snapPreceding.setId(201L);
        snapPreceding.setPortfolioDate(LocalDate.of(2024, 2, 5));
        snapPreceding.setRevisionSeq(1);

        PortfolioSnapshot snapFuture = new PortfolioSnapshot();
        snapFuture.setId(202L);
        snapFuture.setPortfolioDate(LocalDate.of(2024, 2, 25));
        snapFuture.setRevisionSeq(1);

        // Repo returns ordered by portfolioDate desc: [Future, Preceding]
        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any(OffsetDateTime.class)))
            .thenReturn(List.of(snapFuture, snapPreceding));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(201L))
            .thenReturn(List.of());

        PortfolioStressReplayRequestDto request = new PortfolioStressReplayRequestDto(
            funds, null, d0, d1, "NIFTY_500_TRI", OffsetDateTime.now(ZoneOffset.UTC)
        );

        PortfolioStressReplayResponseDto response = service.executeStressReplay(request);

        // Must select the preceding snapshot (2024-02-05) to respect historical point-in-time integrity
        assertThat(response.exposureContext().observationDate()).isEqualTo(LocalDate.of(2024, 2, 5));
        assertThat(response.exposureContext().daysMismatchFromEpisodeEnd()).isEqualTo(10);
    }

    private NavObservation createNav(SchemeOption scheme, LocalDate date, BigDecimal nav) {
        NavObservation obs = new NavObservation();
        obs.setSchemeOption(scheme);
        obs.setEffectiveDate(date);
        obs.setNavValue(nav);
        obs.setAvailabilityTime(OffsetDateTime.now(ZoneOffset.UTC));
        return obs;
    }

    private BenchmarkObservation createBenchmarkObs(Benchmark benchmark, LocalDate date, BigDecimal val) {
        BenchmarkObservation obs = new BenchmarkObservation();
        obs.setBenchmark(benchmark);
        obs.setEffectiveDate(date);
        obs.setIndexLevel(val);
        obs.setAvailabilityTime(OffsetDateTime.now(ZoneOffset.UTC));
        return obs;
    }
}
