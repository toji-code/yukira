package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.BenchmarkConstituent;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.dto.ingestion.BenchmarkConstituentIngestionResultDto;
import com.yukira.backend.ingestion.benchmark.BenchmarkConstituentIngestionService;
import com.yukira.backend.repository.BenchmarkConstituentRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import com.yukira.backend.service.portfolio.ActiveShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BenchmarkPitEnforcementIntegrationTest {

    @Autowired
    private BenchmarkConstituentIngestionService ingestionService;

    @Autowired
    private BenchmarkConstituentRepository constituentRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private SourceArtifactRepository sourceArtifactRepository;

    private Benchmark testBenchmark;

    @BeforeEach
    void setUp() {
        testBenchmark = benchmarkRepository.findByCode("NIFTY_500_TRI")
            .orElseGet(() -> {
                Benchmark bm = new Benchmark("NIFTY_500_TRI", "NIFTY 500 Total Returns Index", "NSE_INDICES", "TRI");
                return benchmarkRepository.save(bm);
            });
    }

    private static final String SAMPLE_CSV =
        "Company Name,Industry,Symbol,Series,ISIN Code\n" +
        "HDFC Bank Ltd.,Financial Services,HDFCBANK,EQ,INE040A01034\n" +
        "Reliance Industries Ltd.,Oil Gas & Consumable Fuels,RELIANCE,EQ,INE002A01018\n" +
        "ICICI Bank Ltd.,Financial Services,ICICIBANK,EQ,INE090A01021\n" +
        "Infosys Ltd.,Information Technology,INFY,EQ,INE009A01021\n" +
        "Tata Consultancy Services Ltd.,Information Technology,TCS,EQ,INE467B01029\n";

    @Test
    @DisplayName("PIT Enforcement 1: Historical snapshot published after knowledge cutoff is excluded")
    void testHistoricalSnapshotAfterCutoff_Excluded() {
        LocalDate effDate = LocalDate.of(2023, 11, 30);
        OffsetDateTime publishTime = OffsetDateTime.of(2023, 12, 7, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        ingestionService.ingestFromCsv(
            testBenchmark.getCode(),
            SAMPLE_CSV.getBytes(StandardCharsets.UTF_8),
            effDate,
            publishTime,
            "https://test.niftyindices.com/test1.csv",
            Map.of("INE040A01034", new BigDecimal("8.750000")),
            false
        );

        // Scenario A: Knowledge cutoff is BEFORE publication (e.g., 2023-12-05) -> Must return 0 constituents for this date
        OffsetDateTime beforePublish = OffsetDateTime.of(2023, 12, 5, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        List<BenchmarkConstituent> beforeResults = constituentRepository.findByBenchmarkAndDateAndCutoff(
            testBenchmark.getId(), effDate, beforePublish
        );
        assertThat(beforeResults).isEmpty();

        // Scenario B: Knowledge cutoff is AFTER publication (e.g., 2023-12-08) -> Must return the constituents
        OffsetDateTime afterPublish = OffsetDateTime.of(2023, 12, 8, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        List<BenchmarkConstituent> afterResults = constituentRepository.findByBenchmarkAndDateAndCutoff(
            testBenchmark.getId(), effDate, afterPublish
        );
        assertThat(afterResults).hasSize(5);
    }

    @Test
    @DisplayName("PIT Enforcement 2: Later revision published after cutoff does NOT leak into earlier query")
    void testLaterRevisionAfterCutoff_Isolation() {
        LocalDate effDate = LocalDate.of(2024, 3, 31);
        OffsetDateTime t1 = OffsetDateTime.of(2024, 4, 1, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime t2 = OffsetDateTime.of(2024, 4, 15, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Ingestion Rev 1 (Original published at T1) with weight 8.00%
        ingestionService.ingestFromCsv(
            testBenchmark.getCode(),
            SAMPLE_CSV.getBytes(StandardCharsets.UTF_8),
            effDate,
            t1,
            "https://test.niftyindices.com/rev1.csv",
            Map.of("INE040A01034", new BigDecimal("8.000000")),
            false
        );

        // Simulate revised artifact at T2 (different bytes so it's a genuine new revision)
        String revisedCsv = SAMPLE_CSV + "Kotak Mahindra Bank Ltd.,Financial Services,KOTAKBANK,EQ,INE237A01028\n";
        ingestionService.ingestFromCsv(
            testBenchmark.getCode(),
            revisedCsv.getBytes(StandardCharsets.UTF_8),
            effDate,
            t2,
            "https://test.niftyindices.com/rev2_correction.csv",
            Map.of("INE040A01034", new BigDecimal("8.500000")),
            false
        );

        // Query with knowledge cutoff between T1 and T2 (e.g. 2024-04-10): Must see Rev 1 (5 members, 8.00% weight)
        OffsetDateTime betweenT1AndT2 = OffsetDateTime.of(2024, 4, 10, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        List<BenchmarkConstituent> pitRev1 = constituentRepository.findByBenchmarkAndDateAndCutoff(
            testBenchmark.getId(), effDate, betweenT1AndT2
        );
        assertThat(pitRev1).hasSize(5);
        BenchmarkConstituent hdfcRev1 = pitRev1.stream()
            .filter(c -> "INE040A01034".equals(c.getIsin())).findFirst().orElseThrow();
        assertThat(hdfcRev1.getWeight()).isEqualByComparingTo(new BigDecimal("8.000000"));
        assertThat(hdfcRev1.getRevisionSeq()).isEqualTo(1);

        // Query with knowledge cutoff after T2 (e.g. 2024-04-20): Must see Rev 2 (6 members, 8.50% weight)
        OffsetDateTime afterT2 = OffsetDateTime.of(2024, 4, 20, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        List<BenchmarkConstituent> pitRev2 = constituentRepository.findByBenchmarkAndDateAndCutoff(
            testBenchmark.getId(), effDate, afterT2
        );
        assertThat(pitRev2).hasSize(6);
        BenchmarkConstituent hdfcRev2 = pitRev2.stream()
            .filter(c -> "INE040A01034".equals(c.getIsin())).findFirst().orElseThrow();
        assertThat(hdfcRev2.getWeight()).isEqualByComparingTo(new BigDecimal("8.500000"));
        assertThat(hdfcRev2.getRevisionSeq()).isEqualTo(2);
    }

    @Test
    @DisplayName("PIT Enforcement 3: Multiple snapshots around disclosure date pick closest prior snapshot")
    void testMultipleSnapshots_PicksClosestPrior() {
        LocalDate d1 = LocalDate.of(2024, 1, 15);
        LocalDate d2 = LocalDate.of(2024, 1, 31);
        LocalDate d3 = LocalDate.of(2024, 2, 28);
        OffsetDateTime avail = OffsetDateTime.of(2024, 3, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        ingestionService.ingestFromCsv(testBenchmark.getCode(), SAMPLE_CSV.getBytes(StandardCharsets.UTF_8), d1, avail, "url1", Map.of(), false);
        ingestionService.ingestFromCsv(testBenchmark.getCode(), SAMPLE_CSV.getBytes(StandardCharsets.UTF_8), d2, avail, "url2", Map.of(), false);
        ingestionService.ingestFromCsv(testBenchmark.getCode(), SAMPLE_CSV.getBytes(StandardCharsets.UTF_8), d3, avail, "url3", Map.of(), false);

        // Query as of 2024-02-15: Must pick 2024-01-31 (not 2024-01-15 and never 2024-02-28!)
        LocalDate queryDate = LocalDate.of(2024, 2, 15);
        List<BenchmarkConstituent> selected = constituentRepository.findEligibleConstituents(
            testBenchmark.getId(), queryDate, avail
        );

        assertThat(selected).isNotEmpty();
        assertThat(selected.get(0).getEffectiveDate()).isEqualTo(d2);
    }

    @Test
    @DisplayName("Idempotency: Duplicate ingestion of the same artifact produces ALREADY_INGESTED without duplicate DB rows")
    void testDuplicateIngestion_Idempotent() {
        LocalDate effDate = LocalDate.of(2025, 6, 30);
        OffsetDateTime avail = OffsetDateTime.of(2025, 7, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        byte[] bytes = SAMPLE_CSV.getBytes(StandardCharsets.UTF_8);

        // Run 1: First ingestion
        BenchmarkConstituentIngestionResultDto res1 = ingestionService.ingestFromCsv(
            testBenchmark.getCode(), bytes, effDate, avail, "https://test.niftyindices.com/dup.csv", Map.of(), false
        );
        assertThat(res1.status()).isEqualTo("SUCCESS");
        assertThat(res1.acceptedCount()).isEqualTo(5);

        // Run 2: Re-ingest identical artifact
        BenchmarkConstituentIngestionResultDto res2 = ingestionService.ingestFromCsv(
            testBenchmark.getCode(), bytes, effDate, avail, "https://test.niftyindices.com/dup.csv", Map.of(), false
        );
        assertThat(res2.status()).isEqualTo("ALREADY_INGESTED");
        assertThat(res2.duplicateCount()).isEqualTo(5);

        // Verify database table has exactly 5 constituents, not 10
        List<BenchmarkConstituent> dbRows = constituentRepository.findByBenchmarkAndDateAndCutoff(
            testBenchmark.getId(), effDate, avail
        );
        assertThat(dbRows).hasSize(5);
    }

    @Test
    @DisplayName("Dry run: Validates without writing records to database")
    void testDryRunValidation_NoPersistence() {
        LocalDate effDate = LocalDate.of(2025, 12, 31);
        OffsetDateTime avail = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        byte[] bytes = SAMPLE_CSV.getBytes(StandardCharsets.UTF_8);

        BenchmarkConstituentIngestionResultDto dryResult = ingestionService.ingestFromCsv(
            testBenchmark.getCode(), bytes, effDate, avail, "https://test.niftyindices.com/dry.csv",
            Map.of("INE040A01034", new BigDecimal("10.00")),
            true // dryRun = true
        );

        assertThat(dryResult.status()).isEqualTo("DRY_RUN_SUCCESS");
        assertThat(dryResult.acceptedCount()).isEqualTo(5);
        assertThat(dryResult.disclosedWeightsCount()).isEqualTo(1);
        assertThat(dryResult.unknownWeightsCount()).isEqualTo(4);
        assertThat(dryResult.disclosedWeightSum()).isEqualByComparingTo(new BigDecimal("10.00"));

        // Verify ZERO records persisted in database
        List<BenchmarkConstituent> dbRows = constituentRepository.findByBenchmarkAndDateAndCutoff(
            testBenchmark.getId(), effDate, avail
        );
        assertThat(dbRows).isEmpty();
    }

    @Test
    @DisplayName("Mathematical lower bound: Lower bound is always <= true Active Share (triangle inequality)")
    void testActiveShareLowerBound_MathematicalConservatism() {
        // True portfolio: 40% in A, 30% in B, 30% in C (Off-BM)
        Map<String, BigDecimal> p = Map.of(
            "A", new BigDecimal("40.00"),
            "B", new BigDecimal("30.00"),
            "C", new BigDecimal("30.00")
        );

        // True benchmark: A: 50%, B: 40%, D: 10%
        // True Active Share: 0.5 * (|40-50| + |30-40| + |30-0| + |0-10|) = 0.5 * (10 + 10 + 30 + 10) = 30.00%
        Map<String, BigDecimal> bTrue = Map.of(
            "A", new BigDecimal("50.00"),
            "B", new BigDecimal("40.00"),
            "D", new BigDecimal("10.00")
        );
        BigDecimal trueActiveShare = ActiveShareService.calculatePureActiveShare(p, bTrue);
        assertThat(trueActiveShare).isEqualByComparingTo(new BigDecimal("30.00"));

        // Disclosed benchmark: only A is disclosed (50%), B is known member but undisclosed weight, D is unknown member
        Map<String, BigDecimal> bDisclosed = Map.of(
            "A", new BigDecimal("50.00")
        );
        Set<String> knownBmMembers = Set.of("A", "B");

        // Lower bound = 0.5 * (w_offBM + |w_pA - w_bA|) = 0.5 * (30.00 + |40.00 - 50.00|) = 0.5 * 40.00 = 20.00%
        BigDecimal lowerBound = ActiveShareService.calculateActiveShareLowerBound(p, bDisclosed, knownBmMembers);
        assertThat(lowerBound).isEqualByComparingTo(new BigDecimal("20.00"));

        // Mathematical property: LowerBound <= TrueActiveShare
        assertThat(lowerBound).isLessThanOrEqualTo(trueActiveShare);
    }
}
