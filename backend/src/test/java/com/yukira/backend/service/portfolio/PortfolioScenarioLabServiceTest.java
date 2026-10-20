package com.yukira.backend.service.portfolio;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.lookthrough.ResearchPortfolioFundItemDto;
import com.yukira.backend.dto.portfolio.scenario.*;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
public class PortfolioScenarioLabServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private PortfolioHoldingRepository holdingRepository;

    @Mock
    private SecurityMarketCapClassificationRepository classificationRepository;

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Mock
    private NavObservationRepository navObservationRepository;

    private PortfolioScenarioLabService service;

    private SchemeOption fundA;
    private SchemeOption fundB;
    private Benchmark benchmarkNifty500;
    private LocalDate asOfDate;
    private OffsetDateTime cutoff;

    @BeforeEach
    void setUp() {
        service = new PortfolioScenarioLabService(
            schemeOptionRepository,
            snapshotRepository,
            holdingRepository,
            classificationRepository,
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

    private void mockHoldingsSnapshot() {
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));
        when(schemeOptionRepository.findById(2L)).thenReturn(Optional.of(fundB));

        PortfolioSnapshot snapA = new PortfolioSnapshot();
        snapA.setId(101L);
        snapA.setSchemeOption(fundA);
        snapA.setPortfolioDate(LocalDate.of(2026, 9, 30));
        snapA.setRevisionSeq(1);

        PortfolioSnapshot snapB = new PortfolioSnapshot();
        snapB.setId(102L);
        snapB.setSchemeOption(fundB);
        snapB.setPortfolioDate(LocalDate.of(2026, 9, 30));
        snapB.setRevisionSeq(1);

        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(snapA));
        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(2L), any()))
            .thenReturn(List.of(snapB));

        // Fund A: 70% Large Cap (HDFC Bank), 20% Mid Cap (Persistent Systems), 10% Cash
        Security secHdfcBank = new Security();
        secHdfcBank.setId(1001L);
        secHdfcBank.setCanonicalName("HDFC Bank Ltd");
        secHdfcBank.setSector("FINANCIAL SERVICES");
        secHdfcBank.setAssetClass("Equity");

        Security secPersistent = new Security();
        secPersistent.setId(1002L);
        secPersistent.setCanonicalName("Persistent Systems Ltd");
        secPersistent.setSector("INFORMATION TECHNOLOGY");
        secPersistent.setAssetClass("Equity");

        Security secCash = new Security();
        secCash.setId(1003L);
        secCash.setCanonicalName("TREPS / Cash Reserve");
        secCash.setSector("Cash");
        secCash.setAssetClass("Cash and Cash Equivalents");

        PortfolioHolding hA1 = new PortfolioHolding();
        hA1.setPortfolioSnapshot(snapA);
        hA1.setSecurity(secHdfcBank);
        hA1.setReportedWeight(new BigDecimal("0.7000")); // 70%

        PortfolioHolding hA2 = new PortfolioHolding();
        hA2.setPortfolioSnapshot(snapA);
        hA2.setSecurity(secPersistent);
        hA2.setReportedWeight(new BigDecimal("0.2000")); // 20%

        PortfolioHolding hA3 = new PortfolioHolding();
        hA3.setPortfolioSnapshot(snapA);
        hA3.setSecurity(secCash);
        hA3.setReportedWeight(new BigDecimal("0.1000")); // 10%

        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(hA1, hA2, hA3));

        // Fund B: 50% Large Cap (Reliance), 30% Small Cap (Unlisted/Small), 20% Cash
        Security secReliance = new Security();
        secReliance.setId(2001L);
        secReliance.setCanonicalName("Reliance Industries Ltd");
        secReliance.setSector("OIL GAS & CONSUMABLE FUELS");
        secReliance.setAssetClass("Equity");

        Security secSmall = new Security();
        secSmall.setId(2002L);
        secSmall.setCanonicalName("Alpha Micro Tech Ltd");
        secSmall.setSector("CAPITAL GOODS");
        secSmall.setAssetClass("Equity");

        PortfolioHolding hB1 = new PortfolioHolding();
        hB1.setPortfolioSnapshot(snapB);
        hB1.setSecurity(secReliance);
        hB1.setReportedWeight(new BigDecimal("0.5000")); // 50%

        PortfolioHolding hB2 = new PortfolioHolding();
        hB2.setPortfolioSnapshot(snapB);
        hB2.setSecurity(secSmall);
        hB2.setReportedWeight(new BigDecimal("0.3000")); // 30%

        PortfolioHolding hB3 = new PortfolioHolding();
        hB3.setPortfolioSnapshot(snapB);
        hB3.setSecurity(secCash);
        hB3.setReportedWeight(new BigDecimal("0.2000")); // 20%

        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(102L))
            .thenReturn(List.of(hB1, hB2, hB3));
    }

    private void mockBenchmarkAndNavObservations(double betaA, double betaB) {
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(benchmarkNifty500));

        List<BenchmarkObservation> bmObs = new ArrayList<>();
        List<NavObservation> navsA = new ArrayList<>();
        List<NavObservation> navsB = new ArrayList<>();

        double bmVal = 20000.0;
        double navAVal = 1000.0;
        double navBVal = 50.0;

        for (int i = 0; i <= 20; i++) {
            LocalDate d = asOfDate.minusDays(20 - i);
            double dailyBmReturn = (i % 2 == 0 ? 0.01 : -0.01);
            if (i > 0) {
                bmVal *= (1.0 + dailyBmReturn);
                navAVal *= (1.0 + betaA * dailyBmReturn);
                navBVal *= (1.0 + betaB * dailyBmReturn);
            }

            BenchmarkObservation bo = new BenchmarkObservation(benchmarkNifty500, d, BigDecimal.valueOf(bmVal), 1, cutoff);
            bmObs.add(bo);

            NavObservation noA = new NavObservation();
            noA.setSchemeOption(fundA);
            noA.setEffectiveDate(d);
            noA.setNavValue(BigDecimal.valueOf(navAVal));
            navsA.add(noA);

            NavObservation noB = new NavObservation();
            noB.setSchemeOption(fundB);
            noB.setEffectiveDate(d);
            noB.setNavValue(BigDecimal.valueOf(navBVal));
            navsB.add(noB);
        }

        lenient().when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(
            eq(123L), eq(asOfDate), any()
        )).thenReturn(bmObs);

        lenient().when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(
            eq(1L), eq(asOfDate), any()
        )).thenReturn(navsA);

        lenient().when(navObservationRepository.findAuthoritativeObservationsAsOfCutoff(
            eq(2L), eq(asOfDate), any()
        )).thenReturn(navsB);
    }

    @Test
    @DisplayName("Broad Market Sensitivity: Computes linear beta shock with perfect constituent contribution reconciliation")
    void testBroadMarketSensitivityCalculation() {
        mockHoldingsSnapshot();
        mockBenchmarkAndNavObservations(1.10, 0.90);

        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(1L, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(2L, new BigDecimal("40.00"))
        );

        ScenarioLabRequestDto request = new ScenarioLabRequestDto(
            funds,
            "BROAD_MARKET",
            "Broad Market -10% Shock",
            "NIFTY_500_TRI",
            asOfDate,
            cutoff,
            new BigDecimal("-10.00"),
            null, null, null, null,
            null, null, null
        );

        ScenarioLabResponseDto response = service.runScenario(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("SENSITIVITY_ESTIMATE_ONLY");
        assertThat(response.scenarioType()).isEqualTo("BROAD_MARKET");
        assertThat(response.portfolioBeta()).isNotNull();

        // Portfolio beta: 0.60 * 1.10 + 0.40 * 0.90 = 1.02
        assertThat(response.portfolioBeta().doubleValue()).isBetween(1.00, 1.04);

        // Portfolio impact: ~ -10.20%
        assertThat(response.estimatedPortfolioImpactPct().doubleValue()).isBetween(-10.40, -10.00);

        // Mathematical reconciliation: sum of constituent contributions == portfolio impact
        BigDecimal sumContrib = response.constituentImpacts().stream()
            .map(ScenarioConstituentImpactDto::portfolioContributionPct)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sumContrib).isEqualByComparingTo(response.estimatedPortfolioImpactPct());

        // Check limitations & disclaimers
        assertThat(response.limitations()).anyMatch(l -> l.contains("Beta linearity omission"));
        assertThat(response.epistemicClassifications()).containsEntry("EpistemicStatus", "SENSITIVITY_ESTIMATE_ONLY");
    }

    @Test
    @DisplayName("Broad Market Beta Gating: When aligned observations < 10, beta is gated as DATA_UNAVAILABLE")
    void testBroadMarketBetaGatingWhenInsufficientObservations() {
        mockHoldingsSnapshot();
        when(benchmarkRepository.findByCode("NIFTY_500_TRI")).thenReturn(Optional.of(benchmarkNifty500));

        // Only 5 observations (below K >= 10 threshold)
        List<BenchmarkObservation> sparseBm = List.of(
            new BenchmarkObservation(benchmarkNifty500, asOfDate.minusDays(4), BigDecimal.valueOf(20000), 1, cutoff),
            new BenchmarkObservation(benchmarkNifty500, asOfDate.minusDays(3), BigDecimal.valueOf(20100), 1, cutoff),
            new BenchmarkObservation(benchmarkNifty500, asOfDate.minusDays(2), BigDecimal.valueOf(19900), 1, cutoff),
            new BenchmarkObservation(benchmarkNifty500, asOfDate.minusDays(1), BigDecimal.valueOf(20050), 1, cutoff),
            new BenchmarkObservation(benchmarkNifty500, asOfDate, BigDecimal.valueOf(20000), 1, cutoff)
        );

        when(benchmarkObservationRepository.findAuthoritativeObservationsAsOfCutoff(
            eq(123L), eq(asOfDate), any()
        )).thenReturn(sparseBm);

        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(1L, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(2L, new BigDecimal("40.00"))
        );

        ScenarioLabRequestDto request = new ScenarioLabRequestDto(
            funds, "BROAD_MARKET", "Broad Market Shock", "NIFTY_500_TRI", asOfDate, cutoff,
            new BigDecimal("-10.00"), null, null, null, null, null, null, null
        );

        ScenarioLabResponseDto response = service.runScenario(request);

        assertThat(response.status()).isEqualTo("DATA_UNAVAILABLE");
        assertThat(response.limitations()).anyMatch(l -> l.contains("gated: Insufficient"));
        assertThat(response.portfolioBeta()).isNull();
    }

    @Test
    @DisplayName("Market-Cap Shocks: Exact linear reconciliation across mutually exclusive partitions")
    void testMarketCapBucketShocksReconciliation() {
        mockHoldingsSnapshot();
        // NAVs for beta estimation
        mockBenchmarkAndNavObservations(1.0, 1.0);

        // Portfolio weights: Fund A (60%), Fund B (40%)
        // Fund A: Large 70%, Mid 20%, Small 0%, Cash 10%
        // Fund B: Large 50%, Mid 0%, Small 30%, Cash 20%
        // Expected Portfolio Weights:
        // Large: 0.60 * 70% + 0.40 * 50% = 42% + 20% = 62.00%
        // Mid: 0.60 * 20% + 0.40 * 0% = 12.00%
        // Small: 0.60 * 0% + 0.40 * 30% = 12.00%
        // Cash: 0.60 * 10% + 0.40 * 20% = 6% + 8% = 14.00%
        // Total Covered: 62 + 12 + 12 + 14 = 100.00%

        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(1L, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(2L, new BigDecimal("40.00"))
        );

        ScenarioLabRequestDto request = new ScenarioLabRequestDto(
            funds,
            "MARKET_CAP",
            "Cap Dispersion Stress",
            "NIFTY_500_TRI",
            asOfDate,
            cutoff,
            null,
            new BigDecimal("-5.00"),  // Large Cap -5%
            new BigDecimal("-10.00"), // Mid Cap -10%
            new BigDecimal("-20.00"), // Small Cap -20%
            BigDecimal.ZERO,          // Cash 0%
            null, null, null
        );

        ScenarioLabResponseDto response = service.runScenario(request);

        assertThat(response.status()).isEqualTo("SENSITIVITY_ESTIMATE_ONLY");
        assertThat(response.coveredWeightPct()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.unmodeledWeightPct()).isEqualByComparingTo(BigDecimal.ZERO);

        // Expected Impact:
        // Large: 62% * (-5%) = -3.10%
        // Mid: 12% * (-10%) = -1.20%
        // Small: 12% * (-20%) = -2.40%
        // Cash: 14% * 0% = 0.00%
        // Total Expected Portfolio Impact = -3.10 + -1.20 + -2.40 = -6.70%
        assertThat(response.estimatedPortfolioImpactPct()).isEqualByComparingTo(new BigDecimal("-6.70"));

        // Sum of exposure impacts == portfolio impact
        BigDecimal sumExpImpact = response.exposureImpacts().stream()
            .map(ScenarioExposureImpactDto::estimatedContributionPct)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumExpImpact).isEqualByComparingTo(new BigDecimal("-6.70"));

        // Sum of constituent contributions == portfolio impact
        BigDecimal sumFundImpact = response.constituentImpacts().stream()
            .map(ScenarioConstituentImpactDto::portfolioContributionPct)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumFundImpact).isEqualByComparingTo(new BigDecimal("-6.70"));
    }

    @Test
    @DisplayName("Partial Exposure Coverage: Unclassified holdings are NEVER renormalized to 100%")
    void testPartialExposureCoverageAndZeroRenormalization() {
        when(schemeOptionRepository.findById(1L)).thenReturn(Optional.of(fundA));

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(101L);
        snap.setSchemeOption(fundA);
        snap.setPortfolioDate(asOfDate);
        snap.setRevisionSeq(1);

        when(snapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(eq(1L), any()))
            .thenReturn(List.of(snap));

        // Fund A has only 75% disclosed/classified holdings, 25% unclassified
        Security secLarge = new Security();
        secLarge.setId(1001L);
        secLarge.setCanonicalName("HDFC Bank Ltd");
        secLarge.setSector("FINANCIAL SERVICES");
        secLarge.setAssetClass("Equity");

        Security secUnclass = new Security();
        secUnclass.setId(1099L);
        secUnclass.setCanonicalName("Off-Market Unclassified");
        secUnclass.setSector(null);
        secUnclass.setAssetClass("Other");

        PortfolioHolding h1 = new PortfolioHolding();
        h1.setPortfolioSnapshot(snap);
        h1.setSecurity(secLarge);
        h1.setReportedWeight(new BigDecimal("0.7500")); // 75%

        PortfolioHolding h2 = new PortfolioHolding();
        h2.setPortfolioSnapshot(snap);
        h2.setSecurity(secUnclass);
        h2.setReportedWeight(new BigDecimal("0.2500")); // 25%

        when(holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1, h2));

        mockBenchmarkAndNavObservations(1.0, 1.0);

        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(1L, new BigDecimal("100.00"))
        );

        ScenarioLabRequestDto request = new ScenarioLabRequestDto(
            funds, "MARKET_CAP", "Partial Coverage Test", "NIFTY_500_TRI", asOfDate, cutoff,
            null, new BigDecimal("-10.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null
        );

        ScenarioLabResponseDto response = service.runScenario(request);

        // Covered is 75%, Unmodeled is 25%. It must NEVER be scaled to 100%!
        assertThat(response.coveredWeightPct()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(response.unmodeledWeightPct()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(response.isFullyCovered()).isFalse();

        // 75% * (-10%) = -7.50% (NOT -10.00% if it had been renormalized!)
        assertThat(response.estimatedPortfolioImpactPct()).isEqualByComparingTo(new BigDecimal("-7.50"));

        // Limitation must be prominently reported
        assertThat(response.limitations()).anyMatch(l -> l.contains("Incomplete holdings exposure coverage: 25.00%"));
    }

    @Test
    @DisplayName("Scenario Comparison: Compares two scenarios under identical conditions and computes exact deltas")
    void testScenarioComparisonConsistency() {
        mockHoldingsSnapshot();
        mockBenchmarkAndNavObservations(1.10, 0.90);

        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(1L, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(2L, new BigDecimal("40.00"))
        );

        ScenarioLabRequestDto scA = new ScenarioLabRequestDto(
            funds, "BROAD_MARKET", "Baseline -10% Shock", "NIFTY_500_TRI", asOfDate, cutoff,
            new BigDecimal("-10.00"), null, null, null, null, null, null, null
        );

        ScenarioLabRequestDto scB = new ScenarioLabRequestDto(
            funds, "BROAD_MARKET", "Severe -15% Shock", "NIFTY_500_TRI", asOfDate, cutoff,
            new BigDecimal("-15.00"), null, null, null, null, null, null, null
        );

        ScenarioComparisonRequestDto compRequest = new ScenarioComparisonRequestDto(
            funds, asOfDate, cutoff, "NIFTY_500_TRI", scA, scB
        );

        ScenarioComparisonResponseDto comparison = service.compareScenarios(compRequest);

        assertThat(comparison).isNotNull();
        assertThat(comparison.isComparable()).isTrue();
        assertThat(comparison.comparabilityNotice()).contains("Methodologically synchronized");

        // With beta = 1.02:
        // Impact A = -10.20%, Impact B = -15.30%
        // Delta = Impact B - Impact A = -5.10%
        assertThat(comparison.portfolioImpactDeltaPct().doubleValue()).isBetween(-5.30, -4.90);
        assertThat(comparison.constituentDeltas()).hasSize(2);
        assertThat(comparison.divergenceSummary()).contains("Scenario B");
    }

    @Test
    @DisplayName("Scenario Comparison: Refuses or flags non-comparable scenarios with mismatched evaluation dates")
    void testScenarioComparisonNonComparableDetection() {
        mockHoldingsSnapshot();
        mockBenchmarkAndNavObservations(1.0, 1.0);

        List<ResearchPortfolioFundItemDto> funds = List.of(
            new ResearchPortfolioFundItemDto(1L, new BigDecimal("60.00")),
            new ResearchPortfolioFundItemDto(2L, new BigDecimal("40.00"))
        );

        ScenarioLabRequestDto scA = new ScenarioLabRequestDto(
            funds, "BROAD_MARKET", "Scenario A", "NIFTY_500_TRI", asOfDate, cutoff,
            new BigDecimal("-10.00"), null, null, null, null, null, null, null
        );

        LocalDate differentDate = asOfDate.minusMonths(3);
        ScenarioLabRequestDto scB = new ScenarioLabRequestDto(
            funds, "BROAD_MARKET", "Scenario B", "NIFTY_500_TRI", differentDate, cutoff,
            new BigDecimal("-10.00"), null, null, null, null, null, null, null
        );

        ScenarioComparisonRequestDto compRequest = new ScenarioComparisonRequestDto(
            funds, asOfDate, cutoff, "NIFTY_500_TRI", scA, scB
        );

        ScenarioComparisonResponseDto comparison = service.compareScenarios(compRequest);

        assertThat(comparison.isComparable()).isFalse();
        assertThat(comparison.comparabilityNotice()).contains("Different evaluation dates");
    }

    @Test
    @DisplayName("Input Validation: Rejects null/empty funds or negative allocations")
    void testInvalidRequestValidation() {
        assertThatThrownBy(() -> service.runScenario(null))
            .isInstanceOf(IllegalArgumentException.class);

        ScenarioLabRequestDto emptyFunds = new ScenarioLabRequestDto(
            Collections.emptyList(), "BROAD_MARKET", "Empty", "NIFTY_500_TRI", asOfDate, cutoff,
            new BigDecimal("-10.00"), null, null, null, null, null, null, null
        );
        assertThatThrownBy(() -> service.runScenario(emptyFunds))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must contain at least one constituent fund");

        ScenarioLabRequestDto negAlloc = new ScenarioLabRequestDto(
            List.of(new ResearchPortfolioFundItemDto(1L, new BigDecimal("-10.00"))),
            "BROAD_MARKET", "Negative", "NIFTY_500_TRI", asOfDate, cutoff,
            new BigDecimal("-10.00"), null, null, null, null, null, null, null
        );
        assertThatThrownBy(() -> service.runScenario(negAlloc))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be negative");
    }
}
