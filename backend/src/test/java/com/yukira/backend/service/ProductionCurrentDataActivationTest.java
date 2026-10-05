package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.AnalyticalScore;
import com.yukira.backend.domain.entity.BenchmarkObservation;
import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.SchemeOption;

import com.yukira.backend.dto.ingestion.AmfiNavRefreshResultDto;
import com.yukira.backend.dto.scoring.AnalyticalRefreshResultDto;
import com.yukira.backend.dto.scoring.CurrentScoreResponseDto;
import com.yukira.backend.ingestion.amfi.HistoricalNavIngestionService;
import com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService;
import com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService.NiftyIngestionSummary;
import com.yukira.backend.repository.AnalyticalScoreRepository;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;

import com.yukira.backend.scoring.dto.ScoreCalculationRequest;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
import com.yukira.backend.service.ingestion.AmfiNavRefreshService;
import com.yukira.backend.service.scoring.AnalyticalRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ProductionCurrentDataActivationTest {

    private static final Logger log = LoggerFactory.getLogger(ProductionCurrentDataActivationTest.class);

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private AmfiNavRefreshService navRefreshService;

    @Autowired
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Autowired
    private NiftyBenchmarkIngestionService niftyBenchmarkIngestionService;

    @Autowired
    private AnalyticalRefreshService analyticalRefreshService;

    @Autowired
    private AnalyticalScoringService analyticalScoringService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Autowired
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private AnalyticalScoreRepository analyticalScoreRepository;

    @BeforeEach
    void setUp() {
        pilotBootstrapService.bootstrapPilot();
    }

    @Test
    @DisplayName("ACTIVATE: Attempt live production data activation and evaluate current score generation")
    void executeProductionDataActivationAndScoring() {
        log.info("=== STEP 1: INITIAL DATABASE STATE BEFORE ACTIVATION ===");
        SchemeOption hdfcOption = schemeOptionRepository.findById(1L).orElseThrow();
        assertEquals("118955", hdfcOption.getAmfiCode());
        assertEquals("INF179K01UT0", hdfcOption.getIsin());

        List<NavObservation> hdfcNavsBefore = navObservationRepository.findBySchemeOptionId(1L);
        LocalDate maxNavDateBefore = hdfcNavsBefore.stream()
            .map(NavObservation::getEffectiveDate)
            .max(Comparator.naturalOrder())
            .orElse(null);
        log.info("HDFC NAV Observations before activation step: count={}, maxDate={}", hdfcNavsBefore.size(), maxNavDateBefore);
        assertNotNull(maxNavDateBefore);

        var benchmarkOpt = benchmarkRepository.findByCode("NIFTY_500_TRI");
        assertTrue(benchmarkOpt.isPresent());
        var benchmark = benchmarkOpt.get();

        List<BenchmarkObservation> benchmarkObsBefore = benchmarkObservationRepository.findByBenchmarkId(benchmark.getId());
        LocalDate maxBenchmarkDateBefore = benchmarkObsBefore.stream()
            .map(BenchmarkObservation::getEffectiveDate)
            .max(Comparator.naturalOrder())
            .orElse(null);
        log.info("NIFTY_500_TRI Benchmark Observations before activation: count={}, maxDate={}", benchmarkObsBefore.size(), maxBenchmarkDateBefore);
        assertNotNull(maxBenchmarkDateBefore);

        List<AnalyticalScore> hdfcScoresBefore = analyticalScoreRepository.findBySchemeOptionIdOrderByCreatedAtDesc(1L);
        log.info("HDFC Historical Scores before activation: count={}", hdfcScoresBefore.size());

        log.info("=== STEP 2: AMFI NAV LIVE ACTIVATION REFRESH & HISTORICAL BACKFILL ===");
        AmfiNavRefreshResultDto amfiResult = navRefreshService.refreshLatestNav();
        log.info("AMFI NAV Refresh Result: status={}, latestNavDate={}, observationsIngested={}, messages={}",
            amfiResult.status(), amfiResult.freshness() != null ? amfiResult.freshness().latestNavDate() : null,
            amfiResult.observationsIngested(), amfiResult.messages());

        // Ingest HDFC historical daily NAV backfill for 2024-01-16 to 2026-09-30 in yearly chunks
        var backfillSummary = historicalNavIngestionService.ingestHistoricalNavInChunks(
            "118955", "9", LocalDate.of(2024, 1, 16), LocalDate.of(2026, 9, 30)
        );
        log.info("HDFC Historical Backfill Summary: parsed={}, ingested={}, skipped={}, revisions={}",
            backfillSummary.totalRowsParsed(), backfillSummary.observationsIngested(),
            backfillSummary.duplicateRowsSkipped(), backfillSummary.revisionsCreated());

        List<NavObservation> hdfcNavsAfter = navObservationRepository.findBySchemeOptionId(1L);
        LocalDate maxNavDateAfter = hdfcNavsAfter.stream()
            .map(NavObservation::getEffectiveDate)
            .max(Comparator.naturalOrder())
            .orElse(null);
        log.info("HDFC NAV Observations after refresh: count={}, maxDate={}", hdfcNavsAfter.size(), maxNavDateAfter);

        boolean amfiHasNewerData = maxNavDateAfter != null && maxNavDateAfter.isAfter(maxNavDateBefore);

        log.info("=== STEP 3: NIFTY BENCHMARK LIVE ACTIVATION REFRESH ===");
        NiftyIngestionSummary niftySummary = niftyBenchmarkIngestionService.refreshBenchmarkData(
            LocalDate.of(2024, 1, 16),
            LocalDate.now()
        );
        log.info("NIFTY Benchmark Refresh Summary: parsed={}, inserted={}, skipped={}, revised={}",
            niftySummary.totalParsed(), niftySummary.insertedCount(), niftySummary.skippedCount(), niftySummary.revisedCount());

        List<BenchmarkObservation> benchmarkObsAfter = benchmarkObservationRepository.findByBenchmarkId(benchmark.getId());
        LocalDate maxBenchmarkDateAfter = benchmarkObsAfter.stream()
            .map(BenchmarkObservation::getEffectiveDate)
            .max(Comparator.naturalOrder())
            .orElse(null);
        log.info("NIFTY_500_TRI Benchmark Observations after refresh: count={}, maxDate={}", benchmarkObsAfter.size(), maxBenchmarkDateAfter);

        boolean niftyHasNewerData = maxBenchmarkDateAfter != null && maxBenchmarkDateAfter.isAfter(maxBenchmarkDateBefore);

        log.info("=== STEP 4: CURRENT SCORE ACTIVATION & IMMUTABILITY VERIFICATION ===");
        if (amfiHasNewerData && niftyHasNewerData) {
            log.info("Both AMFI and Benchmark sources delivered live data newer than 2024-01-15!");
            LocalDate alignedAsOfDate = maxNavDateAfter.isBefore(maxBenchmarkDateAfter) ? maxNavDateAfter : maxBenchmarkDateAfter;
            OffsetDateTime knowledgeCutoff = OffsetDateTime.of(
                alignedAsOfDate.getYear(), alignedAsOfDate.getMonthValue(), alignedAsOfDate.getDayOfMonth(),
                23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30)
            );

            // Execute ONE real current HDFC score calculation
            ScoreCalculationRequest calcReq = new ScoreCalculationRequest(
                1L, null, alignedAsOfDate, knowledgeCutoff, null
            );
            AnalyticalScoreResponse scoreResp = analyticalScoringService.calculateScore(calcReq);
            log.info("HDFC Score Response: score={}, confidence={}, status={}, asOfDate={}, calculationRunId={}",
                scoreResp.score(), scoreResp.confidence(), scoreResp.status(), scoreResp.asOfDate(), scoreResp.calculationRunId());

            Optional<CurrentScoreResponseDto> currentScoreOpt = analyticalRefreshService.getCurrentScore(1L);
            assertTrue(currentScoreOpt.isPresent(), "Current score must be generated and accessible when both sources are live");
            CurrentScoreResponseDto currentScore = currentScoreOpt.get();

            log.info("Persisted Current Score DTO: score={}, confidence={}, status={}, asOfDate={}, isCurrent={}",
                currentScore.score(), currentScore.confidence(), currentScore.status(), currentScore.asOfDate(), currentScore.isCurrent());

            assertEquals(alignedAsOfDate, currentScore.asOfDate());
            if ("AVAILABLE".equalsIgnoreCase(currentScore.status()) || "PARTIAL".equalsIgnoreCase(currentScore.status())) {
                assertNotNull(currentScore.score());
            }
            assertNotNull(currentScore.calculationRunId());

            // Historical immutability check
            List<AnalyticalScore> hdfcScoresAfter = analyticalScoreRepository.findBySchemeOptionIdOrderByCreatedAtDesc(1L);
            assertTrue(hdfcScoresAfter.size() > hdfcScoresBefore.size(), "New score snapshot should be appended");
            for (AnalyticalScore oldScore : hdfcScoresBefore) {
                AnalyticalScore found = analyticalScoreRepository.findById(oldScore.getId()).orElseThrow();
                assertEquals(oldScore.getScore(), found.getScore(), "Historical score values must be immutable");
                assertEquals(oldScore.getAsOfDate(), found.getAsOfDate(), "Historical asOfDate must be immutable");
            }
        } else {
            log.info("Live data activation check: AMFI newer data={}, NIFTY benchmark newer data={}",
                amfiHasNewerData, niftyHasNewerData);

            // Verify historical scores remain unchanged
            List<AnalyticalScore> hdfcScoresAfter = analyticalScoreRepository.findBySchemeOptionIdOrderByCreatedAtDesc(1L);
            assertEquals(hdfcScoresBefore.size(), hdfcScoresAfter.size(), "No fake score snapshot should be created when data is unaligned");
        }
    }
}

