package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.attribution.*;
import com.yukira.backend.dto.portfolio.lookthrough.ResearchPortfolioFundItemDto;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
public class PortfolioRiskAttributionServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private MultiFundLookThroughService lookThroughService;

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Mock
    private NavObservationRepository navObservationRepository;

    private PortfolioRiskAttributionService service;

    private SchemeOption fundA;
    private SchemeOption fundB;
    private Benchmark benchmark;
    private LocalDate asOfDate;
    private OffsetDateTime cutoff;

    @BeforeEach
    void setUp() {
        service = new PortfolioRiskAttributionService(
            schemeOptionRepository,
            lookThroughService,
            benchmarkRepository,
            benchmarkObservationRepository,
            navObservationRepository
        );

        asOfDate = LocalDate.of(2026, 9, 30);
        cutoff = OffsetDateTime.of(2026, 9, 30, 23, 59, 59, 0, ZoneOffset.UTC);

        Scheme schemeA = new Scheme();
        schemeA.setName("HDFC Flexi Cap Fund");
        SchemePlan planA = new SchemePlan();
        planA.setScheme(schemeA);
        planA.setPlanType("DIRECT");
        fundA = new SchemeOption();
        fundA.setId(1L);
        fundA.setAmfiCode("118955");
        fundA.setPlan(planA);

        Scheme schemeB = new Scheme();
        schemeB.setName("Parag Parikh Flexi Cap Fund");
        SchemePlan planB = new SchemePlan();
        planB.setScheme(schemeB);
        planB.setPlanType("DIRECT");
        fundB = new SchemeOption();
        fundB.setId(10151L);
        fundB.setAmfiCode("122639");
        fundB.setPlan(planB);

        benchmark = new Benchmark();
        benchmark.setId(100L);
        benchmark.setCode("NIFTY_500_TRI");
        benchmark.setName("Nifty 500 Total Returns Index");

        lenient().when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));
        lenient().when(schemeOptionRepository.findById(10151L)).thenReturn(Optional.of(fundB));
        lenient().when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(benchmark));

        lenient().when(lookThroughService.calculateMultiFundLookThrough(any())).thenReturn(
            new com.yukira.backend.dto.portfolio.lookthrough.MultiFundLookThroughResponseDto(
                asOfDate, cutoff, BigDecimal.valueOf(100), true, "VALID",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(),
                new com.yukira.backend.dto.portfolio.lookthrough.PortfolioConcentrationMetricsDto(
                    BigDecimal.valueOf(25), BigDecimal.valueOf(40), BigDecimal.valueOf(1200), "Unconcentrated",
                    50, 5, BigDecimal.valueOf(10), BigDecimal.valueOf(95), BigDecimal.valueOf(5), BigDecimal.ZERO
                ),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyMap()
            )
        );
    }

    @Test
    @DisplayName("Exact Euler Risk Reconciliation: Sum of component risk contributions equals portfolio volatility")
    void testEulerRiskReconciliation() {
        // Setup 30 trading days of synthetic returns
        LocalDate start = LocalDate.of(2024, 1, 1);
        List<NavObservation> navsA = new ArrayList<>();
        List<NavObservation> navsB = new ArrayList<>();
        List<BenchmarkObservation> bms = new ArrayList<>();

        double navValA = 100.0;
        double navValB = 50.0;
        double bmVal = 10000.0;

        for (int i = 0; i < 30; i++) {
            LocalDate d = start.plusDays(i);
            // Fund A: volatile
            navValA *= (1.0 + (i % 2 == 0 ? 0.015 : -0.012));
            // Fund B: moderate
            navValB *= (1.0 + (i % 3 == 0 ? 0.008 : -0.005));
            // Benchmark
            bmVal *= (1.0 + (i % 2 == 0 ? 0.009 : -0.007));

            NavObservation noA = new NavObservation();
            noA.setEffectiveDate(d);
            noA.setNavValue(BigDecimal.valueOf(navValA));
            navsA.add(noA);

            NavObservation noB = new NavObservation();
            noB.setEffectiveDate(d);
            noB.setNavValue(BigDecimal.valueOf(navValB));
            navsB.add(noB);

            BenchmarkObservation bo = new BenchmarkObservation();
            bo.setEffectiveDate(d);
            bo.setIndexLevel(BigDecimal.valueOf(bmVal));
            bms.add(bo);
        }

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), any(), any())).thenReturn(navsA);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(10151L), any(), any())).thenReturn(navsB);
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(100L), any(), any())).thenReturn(bms);

        PortfolioRiskAttributionRequestDto request = new PortfolioRiskAttributionRequestDto(
            List.of(
                new ResearchPortfolioFundItemDto(1L, new BigDecimal("60.00")),
                new ResearchPortfolioFundItemDto(10151L, new BigDecimal("40.00"))
            ),
            asOfDate,
            cutoff,
            "NIFTY_500_TRI"
        );

        PortfolioRiskAttributionResponseDto response = service.calculateRiskAttribution(request);

        assertThat(response).isNotNull();
        assertThat(response.evaluationStatus()).isEqualTo("AVAILABLE");
        assertThat(response.alignedObservationCount()).isGreaterThanOrEqualTo(20);
        assertThat(response.fundContributions()).hasSize(2);

        // Check Euler reconciliation
        RiskReconciliationDto recon = response.reconciliation();
        assertThat(recon).isNotNull();
        assertThat(recon.isReconciled()).isTrue();
        assertThat(recon.sumRiskContributionsPct()).isEqualByComparingTo(recon.portfolioVolatilityPct());
        assertThat(recon.sumPercentageContributions()).isBetween(new BigDecimal("99.90"), new BigDecimal("100.10"));

        // Overview metrics
        assertThat(response.overviewMetrics()).isNotEmpty();
        assertThat(response.overviewMetrics().stream().anyMatch(m -> "ANNUALIZED_VOLATILITY".equals(m.metricCode()))).isTrue();
        assertThat(response.overviewMetrics().stream().anyMatch(m -> "MAX_DRAWDOWN".equals(m.metricCode()))).isTrue();
        assertThat(response.overviewMetrics().stream().anyMatch(m -> "DOWNSIDE_SEMIDEVIATION".equals(m.metricCode()))).isTrue();
        assertThat(response.overviewMetrics().stream().anyMatch(m -> "PORTFOLIO_BETA".equals(m.metricCode()))).isTrue();

        // Interpretation panel
        assertThat(response.interpretation()).isNotNull();
        assertThat(response.interpretation().primaryRiskDrivers()).isNotEmpty();
        assertThat(response.interpretation().investigationPrompts()).isNotEmpty();
    }

    @Test
    @DisplayName("Preserve valid negative risk contributions from negatively correlated assets")
    void testNegativeRiskContributionPreserved() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        List<NavObservation> navsA = new ArrayList<>();
        List<NavObservation> navsB = new ArrayList<>();
        List<BenchmarkObservation> bms = new ArrayList<>();

        double navValA = 100.0;
        double navValB = 100.0;
        double bmVal = 10000.0;

        for (int i = 0; i < 30; i++) {
            LocalDate d = start.plusDays(i);
            // Perfectly negatively correlated moves between A and B
            double move = (i % 2 == 0 ? 0.02 : -0.018);
            navValA *= (1.0 + move);
            navValB *= (1.0 - move * 1.5); // B moves in opposite direction with higher magnitude
            bmVal *= (1.0 + move * 0.5);

            NavObservation noA = new NavObservation();
            noA.setEffectiveDate(d);
            noA.setNavValue(BigDecimal.valueOf(navValA));
            navsA.add(noA);

            NavObservation noB = new NavObservation();
            noB.setEffectiveDate(d);
            noB.setNavValue(BigDecimal.valueOf(navValB));
            navsB.add(noB);

            BenchmarkObservation bo = new BenchmarkObservation();
            bo.setEffectiveDate(d);
            bo.setIndexLevel(BigDecimal.valueOf(bmVal));
            bms.add(bo);
        }

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), any(), any())).thenReturn(navsA);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(10151L), any(), any())).thenReturn(navsB);
        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(100L), any(), any())).thenReturn(bms);

        // Portfolio with 85% A and 15% B: B's strong negative covariance reduces portfolio variance so RC_B < 0
        PortfolioRiskAttributionRequestDto request = new PortfolioRiskAttributionRequestDto(
            List.of(
                new ResearchPortfolioFundItemDto(1L, new BigDecimal("85.00")),
                new ResearchPortfolioFundItemDto(10151L, new BigDecimal("15.00"))
            ),
            asOfDate,
            cutoff,
            "NIFTY_500_TRI"
        );

        PortfolioRiskAttributionResponseDto response = service.calculateRiskAttribution(request);

        assertThat(response).isNotNull();
        assertThat(response.evaluationStatus()).isEqualTo("AVAILABLE");
        
        // Find if fund B has negative risk contribution
        FundRiskContributionDto contribB = response.fundContributions().stream()
            .filter(f -> f.schemeOptionId().equals(10151L))
            .findFirst()
            .orElseThrow();

        assertThat(contribB.isNegativeContribution()).isTrue();
        assertThat(contribB.volatilityContributionPct()).isLessThan(BigDecimal.ZERO);

        // Even with negative contribution, sum of RC still equals portfolio volatility!
        RiskReconciliationDto recon = response.reconciliation();
        assertThat(recon.isReconciled()).isTrue();
        assertThat(recon.sumRiskContributionsPct()).isEqualByComparingTo(recon.portfolioVolatilityPct());
    }

    @Test
    @DisplayName("Gate calculation with INSUFFICIENT_RETURN_OBSERVATIONS when N < 20")
    void testInsufficientObservationsGated() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        List<NavObservation> navsA = new ArrayList<>();
        List<NavObservation> navsB = new ArrayList<>();

        for (int i = 0; i < 10; i++) { // Only 10 days
            LocalDate d = start.plusDays(i);
            NavObservation noA = new NavObservation();
            noA.setEffectiveDate(d);
            noA.setNavValue(BigDecimal.valueOf(100.0 + i));
            navsA.add(noA);

            NavObservation noB = new NavObservation();
            noB.setEffectiveDate(d);
            noB.setNavValue(BigDecimal.valueOf(50.0 + i));
            navsB.add(noB);
        }

        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(1L), any(), any())).thenReturn(navsA);
        when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(eq(10151L), any(), any())).thenReturn(navsB);

        PortfolioRiskAttributionRequestDto request = new PortfolioRiskAttributionRequestDto(
            List.of(
                new ResearchPortfolioFundItemDto(1L, new BigDecimal("60.00")),
                new ResearchPortfolioFundItemDto(10151L, new BigDecimal("40.00"))
            ),
            asOfDate,
            cutoff,
            "NIFTY_500_TRI"
        );

        PortfolioRiskAttributionResponseDto response = service.calculateRiskAttribution(request);

        assertThat(response).isNotNull();
        assertThat(response.evaluationStatus()).isEqualTo("INSUFFICIENT_RETURN_OBSERVATIONS");
        assertThat(response.fundContributions()).isEmpty();
        assertThat(response.dataQualityWarnings()).isNotEmpty();
    }
}
