package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.mcap.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.portfolio.MarketCapDriftService;
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
class MarketCapDriftServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private PortfolioSnapshotRepository portfolioSnapshotRepository;

    @Mock
    private PortfolioHoldingRepository portfolioHoldingRepository;

    @Mock
    private SecurityIdentifierRepository securityIdentifierRepository;

    @Mock
    private SecurityMarketCapClassificationRepository classificationRepository;

    @Mock
    private BenchmarkRepository benchmarkRepository;

    @Mock
    private BenchmarkConstituentRepository benchmarkConstituentRepository;

    @Mock
    private BenchmarkResolutionService benchmarkResolutionService;

    private MarketCapDriftService marketCapDriftService;

    private SchemeOption schemeOption;
    private Benchmark benchmark;

    @BeforeEach
    void setUp() {
        marketCapDriftService = new MarketCapDriftService(
            schemeOptionRepository,
            portfolioSnapshotRepository,
            portfolioHoldingRepository,
            securityIdentifierRepository,
            classificationRepository,
            benchmarkRepository,
            benchmarkConstituentRepository,
            benchmarkResolutionService
        );

        Scheme scheme = new Scheme();
        scheme.setId(10L);
        scheme.setName("HDFC Flexi Cap Fund");
        scheme.setCode("HDFC_FLEXI");

        SchemePlan plan = new SchemePlan();
        plan.setId(20L);
        plan.setScheme(scheme);
        plan.setPlanType("DIRECT");

        schemeOption = new SchemeOption();
        schemeOption.setId(1L);
        schemeOption.setPlan(plan);
        schemeOption.setOptionType("GROWTH");
        schemeOption.setAmfiCode("118955");

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
    @DisplayName("Aggregates holdings into Large, Mid, Small, Cash, Debt, and Unclassified buckets deterministically")
    void shouldAggregateHoldingsIntoDistinctBuckets() {
        LocalDate pDate = LocalDate.of(2024, 3, 31);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 4, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(101L);
        snap.setPortfolioDate(pDate);
        snap.setAvailabilityTime(OffsetDateTime.of(2024, 4, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        snap.setRevisionSeq(1);

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap));

        // Securities:
        // 1. ICICI Bank: Equity (0.0950 -> 9.50%) - Large Cap (rank 2)
        // 2. Bharat Forge: Equity (0.0420 -> 4.20%) - Mid Cap (rank 115)
        // 3. Neuland Labs: Equity (0.0180 -> 1.80%) - Small Cap (rank 310)
        // 4. TREPS / Reverse Repo: Cash (0.0350 -> 3.50%)
        // 5. GOI 7.18% 2033: Debt (0.0200 -> 2.00%)
        // 6. Unknown Tech: Equity without classification (0.0100 -> 1.00%) -> Unclassified
        Security s1 = createSecurity(201L, "ICICI Bank Ltd.", "EQUITY", "Financial Services");
        Security s2 = createSecurity(202L, "Bharat Forge Ltd.", "EQUITY", "Automobile");
        Security s3 = createSecurity(203L, "Neuland Laboratories Ltd.", "EQUITY", "Healthcare");
        Security s4 = createSecurity(204L, "TREPS - Clearing Corporation of India", "CASH", "Cash & Cash Equivalents");
        Security s5 = createSecurity(205L, "Government of India 7.18% 2033", "DEBT", "Sovereign Debt");
        Security s6 = createSecurity(206L, "Mystery Unlisted Corp", "OTHER", "Unclassified");

        PortfolioHolding h1 = createHolding(snap, s1, new BigDecimal("0.0950"));
        PortfolioHolding h2 = createHolding(snap, s2, new BigDecimal("0.0420"));
        PortfolioHolding h3 = createHolding(snap, s3, new BigDecimal("0.0180"));
        PortfolioHolding h4 = createHolding(snap, s4, new BigDecimal("0.0350"));
        PortfolioHolding h5 = createHolding(snap, s5, new BigDecimal("0.0200"));
        PortfolioHolding h6 = createHolding(snap, s6, new BigDecimal("0.0100"));

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1, h2, h3, h4, h5, h6));

        SecurityIdentifier id1 = createIdentifier(s1, "ISIN", "INE090A01021");
        SecurityIdentifier id2 = createIdentifier(s2, "ISIN", "INE465A01025");
        SecurityIdentifier id3 = createIdentifier(s3, "ISIN", "INE799A01013");
        when(securityIdentifierRepository.findBySecurityIdInAndIdType(any(), eq("ISIN")))
            .thenReturn(List.of(id1, id2, id3));

        // PIT classifications
        SecurityMarketCapClassification c1 = createClassification(s1, "INE090A01021", 2, "LARGE_CAP", "Jul-Dec 2023",
            LocalDate.of(2024, 2, 1), LocalDate.of(2024, 7, 31), OffsetDateTime.of(2024, 1, 5, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        SecurityMarketCapClassification c2 = createClassification(s2, "INE465A01025", 115, "MID_CAP", "Jul-Dec 2023",
            LocalDate.of(2024, 2, 1), LocalDate.of(2024, 7, 31), OffsetDateTime.of(2024, 1, 5, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        SecurityMarketCapClassification c3 = createClassification(s3, "INE799A01013", 310, "SMALL_CAP", "Jul-Dec 2023",
            LocalDate.of(2024, 2, 1), LocalDate.of(2024, 7, 31), OffsetDateTime.of(2024, 1, 5, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));

        when(classificationRepository.findEligibleClassifications(pDate, cutoff))
            .thenReturn(List.of(c1, c2, c3));

        MarketCapDriftResponseDto response = marketCapDriftService.getMarketCapDriftTrajectory(
            1L, pDate, null, null, cutoff
        );

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.observations()).hasSize(1);
        MarketCapObservationDto obs = response.observations().get(0);

        assertThat(obs.sumReportedWeightsPct()).isEqualByComparingTo(new BigDecimal("22.00"));
        assertThat(obs.weightReconciliationDeltaPct()).isEqualByComparingTo(new BigDecimal("78.00")); // 100 - 22 = 78
        assertThat(obs.classificationStatus()).isEqualTo("VERIFIED_HISTORICAL_PIT");
        assertThat(obs.sourceRankingPeriod()).isEqualTo("Jul-Dec 2023");

        // Verify buckets: Large (9.50%), Mid (4.20%), Small (1.80%), Cash (3.50%), Debt (2.00%), Unclassified (1.00%)
        Map<String, BigDecimal> bucketMap = new HashMap<>();
        for (MarketCapBucketExposureDto b : obs.bucketExposures()) {
            bucketMap.put(b.category(), b.reportedWeightPct());
        }

        assertThat(bucketMap.get("LARGE_CAP")).isEqualByComparingTo(new BigDecimal("9.50"));
        assertThat(bucketMap.get("MID_CAP")).isEqualByComparingTo(new BigDecimal("4.20"));
        assertThat(bucketMap.get("SMALL_CAP")).isEqualByComparingTo(new BigDecimal("1.80"));
        assertThat(bucketMap.get("CASH")).isEqualByComparingTo(new BigDecimal("3.50"));
        assertThat(bucketMap.get("DEBT_OTHER")).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(bucketMap.get("UNCLASSIFIED")).isEqualByComparingTo(new BigDecimal("1.00"));
    }

    @Test
    @DisplayName("Decomposes style drift between consecutive snapshots into weight adjustment vs AMFI reclassification")
    void shouldDecomposeDriftIntoWeightAdjustmentAndReclassification() {
        LocalDate d1 = LocalDate.of(2024, 6, 30);
        LocalDate d2 = LocalDate.of(2024, 8, 31);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 9, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot s1 = new PortfolioSnapshot();
        s1.setId(101L);
        s1.setPortfolioDate(d1);
        s1.setAvailabilityTime(OffsetDateTime.of(2024, 7, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        s1.setRevisionSeq(1);

        PortfolioSnapshot s2 = new PortfolioSnapshot();
        s2.setId(102L);
        s2.setPortfolioDate(d2);
        s2.setAvailabilityTime(OffsetDateTime.of(2024, 9, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        s2.setRevisionSeq(1);

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(s2, s1));

        // Security that reclassifies:
        // Trent Ltd: In Period 1 (Jul-Dec 2023), ranked #105 (Mid-Cap). Weight: 0.0500 (5.00%)
        // In Period 2 (Jan-Jun 2024), ranked #92 (Large-Cap). Weight: 0.0600 (6.00%)
        // Continuing large-cap: ICICI Bank: 0.1000 (10.00%) -> 0.1100 (11.00%)
        // Continuing mid-cap: Blue Star: 0.0400 (4.00%) -> 0.0350 (3.50%)
        // Cash: 0.0500 (5.00%) -> 0.0500 (5.00%)
        Security secTrent = createSecurity(301L, "Trent Ltd.", "EQUITY", "Consumer Services");
        Security secIcici = createSecurity(302L, "ICICI Bank Ltd.", "EQUITY", "Financial Services");
        Security secBlueStar = createSecurity(303L, "Blue Star Ltd.", "EQUITY", "Consumer Durables");
        Security secCash = createSecurity(304L, "TREPS", "CASH", "Cash & Cash Equivalents");

        // Period 1 Holdings
        PortfolioHolding h1Trent = createHolding(s1, secTrent, new BigDecimal("0.0500"));
        PortfolioHolding h1Icici = createHolding(s1, secIcici, new BigDecimal("0.1000"));
        PortfolioHolding h1BlueStar = createHolding(s1, secBlueStar, new BigDecimal("0.0400"));
        PortfolioHolding h1Cash = createHolding(s1, secCash, new BigDecimal("0.0500"));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1Icici, h1Trent, h1BlueStar, h1Cash));

        // Period 2 Holdings
        PortfolioHolding h2Trent = createHolding(s2, secTrent, new BigDecimal("0.0600"));
        PortfolioHolding h2Icici = createHolding(s2, secIcici, new BigDecimal("0.1100"));
        PortfolioHolding h2BlueStar = createHolding(s2, secBlueStar, new BigDecimal("0.0350"));
        PortfolioHolding h2Cash = createHolding(s2, secCash, new BigDecimal("0.0500"));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(102L))
            .thenReturn(List.of(h2Icici, h2Trent, h2BlueStar, h2Cash));

        SecurityIdentifier idTrent = createIdentifier(secTrent, "ISIN", "INE849A01020");
        SecurityIdentifier idIcici = createIdentifier(secIcici, "ISIN", "INE090A01021");
        SecurityIdentifier idBlueStar = createIdentifier(secBlueStar, "ISIN", "INE472A01039");
        when(securityIdentifierRepository.findBySecurityIdInAndIdType(any(), eq("ISIN")))
            .thenReturn(List.of(idTrent, idIcici, idBlueStar));

        // Period 1 Classifications (Effective up to 2024-07-31)
        SecurityMarketCapClassification c1Trent = createClassification(secTrent, "INE849A01020", 105, "MID_CAP", "Jul-Dec 2023",
            LocalDate.of(2024, 2, 1), LocalDate.of(2024, 7, 31), OffsetDateTime.of(2024, 1, 5, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        SecurityMarketCapClassification c1Icici = createClassification(secIcici, "INE090A01021", 2, "LARGE_CAP", "Jul-Dec 2023",
            LocalDate.of(2024, 2, 1), LocalDate.of(2024, 7, 31), OffsetDateTime.of(2024, 1, 5, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        SecurityMarketCapClassification c1BlueStar = createClassification(secBlueStar, "INE472A01039", 180, "MID_CAP", "Jul-Dec 2023",
            LocalDate.of(2024, 2, 1), LocalDate.of(2024, 7, 31), OffsetDateTime.of(2024, 1, 5, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        when(classificationRepository.findEligibleClassifications(d1, cutoff))
            .thenReturn(List.of(c1Trent, c1Icici, c1BlueStar));

        // Period 2 Classifications (Effective 2024-08-01 onwards): Trent reclassified from Mid-Cap to Large-Cap!
        SecurityMarketCapClassification c2Trent = createClassification(secTrent, "INE849A01020", 92, "LARGE_CAP", "Jan-Jun 2024",
            LocalDate.of(2024, 8, 1), LocalDate.of(2025, 1, 31), OffsetDateTime.of(2024, 7, 5, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        SecurityMarketCapClassification c2Icici = createClassification(secIcici, "INE090A01021", 2, "LARGE_CAP", "Jan-Jun 2024",
            LocalDate.of(2024, 8, 1), LocalDate.of(2025, 1, 31), OffsetDateTime.of(2024, 7, 5, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        SecurityMarketCapClassification c2BlueStar = createClassification(secBlueStar, "INE472A01039", 175, "MID_CAP", "Jan-Jun 2024",
            LocalDate.of(2024, 8, 1), LocalDate.of(2025, 1, 31), OffsetDateTime.of(2024, 7, 5, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)));
        when(classificationRepository.findEligibleClassifications(d2, cutoff))
            .thenReturn(List.of(c2Trent, c2Icici, c2BlueStar));

        MarketCapDriftResponseDto response = marketCapDriftService.getMarketCapDriftTrajectory(
            1L, d2, null, null, cutoff
        );

        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.observations()).hasSize(2);

        // Period 1 allocations:
        // Large-Cap: ICICI (10.00%)
        // Mid-Cap: Trent (5.00%) + Blue Star (4.00%) = 9.00%
        // Cash: 5.00%
        MarketCapObservationDto obs1 = response.observations().get(0);
        assertThat(getBucketWeight(obs1, "LARGE_CAP")).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(getBucketWeight(obs1, "MID_CAP")).isEqualByComparingTo(new BigDecimal("9.00"));

        // Period 2 allocations:
        // Large-Cap: ICICI (11.00%) + Trent (6.00%) = 17.00%
        // Mid-Cap: Blue Star (3.50%)
        // Cash: 5.00%
        MarketCapObservationDto obs2 = response.observations().get(1);
        assertThat(getBucketWeight(obs2, "LARGE_CAP")).isEqualByComparingTo(new BigDecimal("17.00"));
        assertThat(getBucketWeight(obs2, "MID_CAP")).isEqualByComparingTo(new BigDecimal("3.50"));

        // Check transition drift decomposition for Large-Cap:
        // Observed delta: 17.00 - 10.00 = +7.00 pp
        // Trent reclassification base: +5.00 pp (reclassification component)
        // Weight adjustments: ICICI (+1.00 pp) + Trent growth (+1.00 pp) = +2.00 pp (weight change component)
        // Sum components = 5.00 + 2.00 = 7.00 pp. Exactly reconciled!
        MarketCapDriftChangeDto largeCapChange = response.transitions().stream()
            .filter(t -> "LARGE_CAP".equals(t.category()))
            .findFirst()
            .orElseThrow();

        assertThat(largeCapChange.deltaWeightPct()).isEqualByComparingTo(new BigDecimal("7.00"));
        assertThat(largeCapChange.reclassificationComponent()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(largeCapChange.weightChangeComponent()).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(largeCapChange.isReconciled()).isTrue();
        assertThat(largeCapChange.reclassifiedHoldings()).hasSize(1);
        assertThat(largeCapChange.reclassifiedHoldings().get(0).securityName()).isEqualTo("Trent Ltd.");
        assertThat(largeCapChange.reclassifiedHoldings().get(0).earlierCategory()).isEqualTo("MID_CAP");
        assertThat(largeCapChange.reclassifiedHoldings().get(0).laterCategory()).isEqualTo("LARGE_CAP");

        // Check Mid-Cap transition:
        // Observed delta: 3.50 - 9.00 = -5.50 pp
        // Trent reclassification out: -5.00 pp
        // Blue Star weight reduction: -0.50 pp
        // Sum components = -5.00 + -0.50 = -5.50 pp. Exactly reconciled!
        MarketCapDriftChangeDto midCapChange = response.transitions().stream()
            .filter(t -> "MID_CAP".equals(t.category()))
            .findFirst()
            .orElseThrow();

        assertThat(midCapChange.deltaWeightPct()).isEqualByComparingTo(new BigDecimal("-5.50"));
        assertThat(midCapChange.reclassificationComponent()).isEqualByComparingTo(new BigDecimal("-5.00"));
        assertThat(midCapChange.weightChangeComponent()).isEqualByComparingTo(new BigDecimal("-0.50"));
        assertThat(midCapChange.isReconciled()).isTrue();
    }

    @Test
    @DisplayName("Strictly gates benchmark active market-cap comparisons when benchmark constituent weights are partially disclosed")
    void shouldGateBenchmarkComparisonWhenWeightsAreIncomplete() {
        LocalDate pDate = LocalDate.of(2024, 3, 31);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 4, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(101L);
        snap.setPortfolioDate(pDate);
        snap.setAvailabilityTime(OffsetDateTime.of(2024, 4, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        snap.setRevisionSeq(1);

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap));

        Security s1 = createSecurity(201L, "HDFC Bank Ltd.", "EQUITY", "Financial Services");
        PortfolioHolding h1 = createHolding(snap, s1, new BigDecimal("0.1000"));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1));

        // Benchmark constituents: only top 2 disclosed (total 15.5% coverage out of 500 constituents)
        BenchmarkConstituent bc1 = createConstituent(benchmark, pDate, "HDFC Bank Ltd.", "INE040A01034", new BigDecimal("9.50"), "Financial Services");
        BenchmarkConstituent bc2 = createConstituent(benchmark, pDate, "Reliance Industries Ltd.", "INE002A01018", new BigDecimal("6.00"), "Energy");
        BenchmarkConstituent bc3 = createConstituent(benchmark, pDate, "Unweighted Tail Corp 1", "INE000000001", null, "Metals");

        when(benchmarkConstituentRepository.findEligibleConstituents(eq(100L), eq(pDate), eq(cutoff)))
            .thenReturn(List.of(bc1, bc2, bc3));

        MarketCapDriftResponseDto response = marketCapDriftService.getMarketCapDriftTrajectory(
            1L, pDate, null, null, cutoff
        );

        MarketCapBenchmarkComparisonDto bmComp = response.observations().get(0).benchmarkComparison();
        assertThat(bmComp.gatingStatus()).isEqualTo("GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
        assertThat(bmComp.isWeightCoverageComplete()).isFalse();
        assertThat(bmComp.benchmarkWeightCoveragePct()).isEqualByComparingTo(new BigDecimal("15.50"));
        assertThat(bmComp.gatingReason()).contains("partially disclosed");
        // Disclosed weights are preserved as explicit lower bounds, not treated as 0
        assertThat(bmComp.benchmarkLargeCapDisclosedWeightPct()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Knowledge cutoff strictly excludes later-published AMFI ranking ledgers")
    void shouldStrictlyEnforceKnowledgeCutoffExclusion() {
        LocalDate pDate = LocalDate.of(2024, 1, 15);
        // Cutoff set before the Jan 2024 ranking publication on 2024-01-05
        OffsetDateTime pastCutoff = OffsetDateTime.of(2024, 1, 4, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(101L);
        snap.setPortfolioDate(pDate);
        snap.setAvailabilityTime(OffsetDateTime.of(2024, 1, 3, 12, 0, 0, 0, ZoneOffset.UTC));
        snap.setRevisionSeq(1);

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, pastCutoff))
            .thenReturn(List.of(snap));

        Security s1 = createSecurity(201L, "ICICI Bank Ltd.", "EQUITY", "Financial Services");
        PortfolioHolding h1 = createHolding(snap, s1, new BigDecimal("0.1000"));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1));

        // When cutoff is 2024-01-04, no ranking ledger published on 2024-01-05 is eligible
        when(classificationRepository.findEligibleClassifications(pDate, pastCutoff))
            .thenReturn(Collections.emptyList());

        MarketCapDriftResponseDto response = marketCapDriftService.getMarketCapDriftTrajectory(
            1L, pDate, null, null, pastCutoff
        );

        assertThat(response.status()).isEqualTo("SUCCESS");
        MarketCapObservationDto obs = response.observations().get(0);
        // Because no authentic PIT was available under pastCutoff, it falls back to APPROXIMATE_REFERENCE
        assertThat(obs.classificationStatus()).isEqualTo("APPROXIMATE_REFERENCE");
        assertThat(obs.sourceRankingPeriod()).isNull();
    }

    @Test
    @DisplayName("Detects disclosure scope expansion from partial top holdings to full portfolio disclosure")
    void shouldDetectDisclosureScopeExpansion() {
        LocalDate d1 = LocalDate.of(2024, 3, 31);
        LocalDate d2 = LocalDate.of(2024, 4, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 5, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Snapshot 1: Top-10 partial disclosure (sum reported weights = 45.00%)
        PortfolioSnapshot s1 = new PortfolioSnapshot();
        s1.setId(101L);
        s1.setPortfolioDate(d1);
        s1.setAvailabilityTime(OffsetDateTime.of(2024, 4, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        s1.setRevisionSeq(1);

        // Snapshot 2: Full portfolio disclosure (sum reported weights = 98.50%)
        PortfolioSnapshot s2 = new PortfolioSnapshot();
        s2.setId(102L);
        s2.setPortfolioDate(d2);
        s2.setAvailabilityTime(OffsetDateTime.of(2024, 5, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        s2.setRevisionSeq(1);

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(s2, s1));

        Security s1Sec = createSecurity(201L, "HDFC Bank Ltd.", "EQUITY", "Financial Services");
        PortfolioHolding h1 = createHolding(s1, s1Sec, new BigDecimal("0.4500"));
        PortfolioHolding h2 = createHolding(s2, s1Sec, new BigDecimal("0.9850"));

        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(102L))
            .thenReturn(List.of(h2));

        MarketCapDriftResponseDto response = marketCapDriftService.getMarketCapDriftTrajectory(
            1L, d2, null, null, cutoff
        );

        assertThat(response.observations().get(0).portfolioDisclosureScope()).isEqualTo("PARTIAL_TOP_HOLDINGS");
        assertThat(response.observations().get(1).portfolioDisclosureScope()).isEqualTo("FULL_PORTFOLIO_DISCLOSURE");

        // Transition should flag DISCLOSURE_SCOPE_SHIFT and mark non-comparable
        MarketCapDriftChangeDto transition = response.transitions().get(0);
        assertThat(transition.primaryDriver()).isEqualTo("DISCLOSURE_SCOPE_SHIFT");
        assertThat(transition.isComparable()).isFalse();
        assertThat(transition.nonComparableReason()).contains("partial top-holdings");
    }

    @Test
    @DisplayName("Identical queries produce deterministic, repeatable results with zero drift")
    void shouldProduceRepeatableResults() {
        LocalDate pDate = LocalDate.of(2024, 6, 30);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 7, 10, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setId(101L);
        snap.setPortfolioDate(pDate);
        snap.setAvailabilityTime(OffsetDateTime.of(2024, 7, 5, 12, 0, 0, 0, ZoneOffset.UTC));
        snap.setRevisionSeq(1);

        when(portfolioSnapshotRepository.findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(1L, cutoff))
            .thenReturn(List.of(snap));

        Security s1 = createSecurity(201L, "ICICI Bank Ltd.", "EQUITY", "Financial Services");
        PortfolioHolding h1 = createHolding(snap, s1, new BigDecimal("0.1000"));
        when(portfolioHoldingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(101L))
            .thenReturn(List.of(h1));

        MarketCapDriftResponseDto r1 = marketCapDriftService.getMarketCapDriftTrajectory(1L, pDate, null, null, cutoff);
        MarketCapDriftResponseDto r2 = marketCapDriftService.getMarketCapDriftTrajectory(1L, pDate, null, null, cutoff);

        assertThat(r1.observations().get(0).sumReportedWeightsPct())
            .isEqualTo(r2.observations().get(0).sumReportedWeightsPct());
        assertThat(r1.observations().get(0).bucketExposures().get(0).reportedWeightPct())
            .isEqualTo(r2.observations().get(0).bucketExposures().get(0).reportedWeightPct());
    }

    private BigDecimal getBucketWeight(MarketCapObservationDto obs, String category) {
        return obs.bucketExposures().stream()
            .filter(b -> category.equals(b.category()))
            .map(MarketCapBucketExposureDto::reportedWeightPct)
            .findFirst()
            .orElse(BigDecimal.ZERO);
    }

    private Security createSecurity(Long id, String name, String assetClass, String sector) {
        Security sec = new Security();
        sec.setId(id);
        sec.setCanonicalName(name);
        sec.setAssetClass(assetClass);
        sec.setSector(sector);
        return sec;
    }

    private PortfolioHolding createHolding(PortfolioSnapshot snap, Security sec, BigDecimal weight) {
        PortfolioHolding h = new PortfolioHolding();
        h.setPortfolioSnapshot(snap);
        h.setSecurity(sec);
        h.setReportedWeight(weight);
        return h;
    }

    private SecurityIdentifier createIdentifier(Security sec, String type, String value) {
        SecurityIdentifier si = new SecurityIdentifier();
        si.setSecurity(sec);
        si.setIdType(type);
        si.setIdValue(value);
        return si;
    }

    private SecurityMarketCapClassification createClassification(
        Security sec, String isin, Integer rank, String category, String period,
        LocalDate effFrom, LocalDate effTo, OffsetDateTime avail
    ) {
        SecurityMarketCapClassification c = new SecurityMarketCapClassification();
        c.setSecurity(sec);
        c.setIsin(isin);
        c.setCompanyName(sec != null ? sec.getCanonicalName() : "Co");
        c.setMarketCapRank(rank);
        c.setMarketCapCategory(category);
        c.setRankingPeriod(period);
        c.setRankingPeriodEndDate(effFrom.minusMonths(1));
        c.setSourcePublicationDate(effFrom.minusDays(25));
        c.setEffectiveFrom(effFrom);
        c.setEffectiveTo(effTo);
        c.setAvailabilityTime(avail);
        c.setRevisionSeq(1);
        c.setLatestRevision(true);
        return c;
    }

    private BenchmarkConstituent createConstituent(
        Benchmark benchmark, LocalDate date, String name, String isin, BigDecimal weight, String industry
    ) {
        BenchmarkConstituent bc = new BenchmarkConstituent();
        bc.setBenchmark(benchmark);
        bc.setEffectiveDate(date);
        bc.setCompanyName(name);
        bc.setIsin(isin);
        bc.setWeight(weight);
        bc.setIndustry(industry);
        bc.setAvailabilityTime(OffsetDateTime.of(date.atTime(12, 0), ZoneOffset.UTC));
        bc.setIsMember(true);
        bc.setRevisionSeq(1);
        bc.setIsLatestRevision(true);
        return bc;
    }
}
