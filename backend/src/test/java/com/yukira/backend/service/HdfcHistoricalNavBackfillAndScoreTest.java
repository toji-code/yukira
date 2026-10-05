package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.AnalyticalScore;
import com.yukira.backend.domain.entity.BenchmarkObservation;
import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.scoring.CurrentScoreResponseDto;
import com.yukira.backend.ingestion.amfi.HistoricalNavIngestionService;
import com.yukira.backend.ingestion.amfi.IngestionSummary;
import com.yukira.backend.repository.AnalyticalScoreRepository;
import com.yukira.backend.repository.BenchmarkObservationRepository;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.ScoreCalculationRequest;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
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
public class HdfcHistoricalNavBackfillAndScoreTest {

    private static final Logger log = LoggerFactory.getLogger(HdfcHistoricalNavBackfillAndScoreTest.class);

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Autowired
    private AnalyticalScoringService analyticalScoringService;

    @Autowired
    private AnalyticalRefreshService analyticalRefreshService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Autowired
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    @Autowired
    private AnalyticalScoreRepository analyticalScoreRepository;

    @BeforeEach
    void setUp() {
        pilotBootstrapService.bootstrapPilot();
    }

    @Test
    @DisplayName("EXECUTE: Pilot Fund HDFC Historical NAV Backfill and First Genuine Current Score V1 Calculation")
    void executeHdfcHistoricalBackfillAndCurrentScore() {
        log.info("=== STEP 1: VERIFY INITIAL DB STATE FOR PILOT SCHEME OPTION 1 ===");
        SchemeOption hdfcOption = schemeOptionRepository.findById(1L).orElseThrow();
        assertEquals("118955", hdfcOption.getAmfiCode());
        assertEquals("INF179K01UT0", hdfcOption.getIsin());

        List<NavObservation> navsBefore = navObservationRepository.findBySchemeOptionId(1L);
        log.info("Initial NAV observations count: {}", navsBefore.size());

        List<AnalyticalScore> scoresBefore = analyticalScoreRepository.findBySchemeOptionIdOrderByCreatedAtDesc(1L);
        log.info("Initial AnalyticalScores count: {}", scoresBefore.size());

        log.info("=== STEP 2: HISTORICAL NAV BACKFILL (2026-04-16 to 2026-09-30) ===");
        IngestionSummary backfillSummary = historicalNavIngestionService.ingestHistoricalNavInChunks(
            "118955", null, LocalDate.of(2026, 4, 16), LocalDate.of(2026, 9, 30)
        );
        log.info("Backfill Summary: parsed={}, ingested={}, skipped={}, revisions={}",
            backfillSummary.totalRowsParsed(), backfillSummary.observationsIngested(),
            backfillSummary.duplicateRowsSkipped(), backfillSummary.revisionsCreated());

        assertTrue(backfillSummary.totalRowsParsed() > 0, "Historical NAV backfill must parse source records");

        log.info("=== STEP 3: IDEMPOTENCY TEST ===");
        IngestionSummary repeatSummary = historicalNavIngestionService.ingestHistoricalNavInChunks(
            "118955", null, LocalDate.of(2026, 4, 16), LocalDate.of(2026, 9, 30)
        );
        log.info("Repeat Backfill Summary: parsed={}, ingested={}, skipped={}",
            repeatSummary.totalRowsParsed(), repeatSummary.observationsIngested(), repeatSummary.duplicateRowsSkipped());
        assertEquals(0, repeatSummary.observationsIngested(), "Re-running backfill must ingest 0 new observations (idempotency requirement)");

        log.info("=== STEP 4: DATA COMPLETENESS GATE ===");
        List<NavObservation> navsAfter = navObservationRepository.findBySchemeOptionId(1L);
        log.info("Post-backfill NAV observations count: {}", navsAfter.size());

        LocalDate minDate = navsAfter.stream().map(NavObservation::getEffectiveDate).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate maxDate = navsAfter.stream().map(NavObservation::getEffectiveDate).max(Comparator.naturalOrder()).orElseThrow();
        log.info("NAV Coverage Range: minDate={}, maxDate={}", minDate, maxDate);

        // Check 3Y analytical window: 2023-10-01 to 2026-10-01
        List<NavObservation> windowObs = navsAfter.stream()
            .filter(o -> !o.getEffectiveDate().isBefore(LocalDate.of(2023, 10, 1)) && !o.getEffectiveDate().isAfter(LocalDate.of(2026, 10, 1)))
            .toList();
        log.info("3Y Analytical Window Observations (2023-10-01 to 2026-10-01): count={}", windowObs.size());
        assertTrue(windowObs.size() >= 700, "Required 3Y analytical window must have at least 700 observations");

        log.info("=== STEP 5: CURRENT SCORE CALCULATION ===");
        LocalDate asOfDate = LocalDate.of(2026, 10, 1);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2026, 10, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        ScoreCalculationRequest calcReq = new ScoreCalculationRequest(
            1L, null, asOfDate, knowledgeCutoff, null
        );
        AnalyticalScoreResponse scoreResp = analyticalScoringService.calculateScore(calcReq);

        log.info("Current Score Response: scoreId={}, score={}, confidence={}, status={}, asOfDate={}, calculationRunId={}",
            scoreResp.scoreId(), scoreResp.score(), scoreResp.confidence(), scoreResp.status(), scoreResp.asOfDate(), scoreResp.calculationRunId());

        assertNotNull(scoreResp.scoreId());
        assertNotNull(scoreResp.score());
        assertNotNull(scoreResp.calculationRunId());
        assertTrue("AVAILABLE".equalsIgnoreCase(scoreResp.status()) || "PARTIAL".equalsIgnoreCase(scoreResp.status()),
            "Score status must be AVAILABLE or PARTIAL when 3Y window is complete");

        log.info("=== STEP 6: VERIFY PERSISTENCE & PROVENANCE CHAIN ===");
        CalculationRun run = calculationRunRepository.findById(scoreResp.calculationRunId()).orElseThrow();
        assertEquals(asOfDate, run.getAsOfDate());
        assertEquals("COMPLETED", run.getRunStatus());

        List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
        log.info("MetricResults generated for CalculationRun #{}: count={}", run.getId(), results.size());
        assertTrue(results.size() >= 10, "CalculationRun must contain at least 10 canonical metric results");

        AnalyticalScore persistedScore = analyticalScoreRepository.findById(scoreResp.scoreId()).orElseThrow();
        assertEquals(asOfDate, persistedScore.getAsOfDate());
        assertNotNull(persistedScore.getCalculationRun());

        log.info("=== STEP 7: VERIFY HISTORICAL IMMUTABILITY ===");
        for (AnalyticalScore oldScore : scoresBefore) {
            AnalyticalScore found = analyticalScoreRepository.findById(oldScore.getId()).orElseThrow();
            assertEquals(oldScore.getScore(), found.getScore(), "Historical score value must be immutable");
            assertEquals(oldScore.getAsOfDate(), found.getAsOfDate(), "Historical asOfDate must be immutable");
            assertEquals(oldScore.getStatus(), found.getStatus(), "Historical status must be immutable");
        }

        log.info("=== STEP 8: VERIFY CURRENT READ ENDPOINT ===");
        Optional<CurrentScoreResponseDto> currentScoreOpt = analyticalRefreshService.getCurrentScore(1L);
        assertTrue(currentScoreOpt.isPresent());
        CurrentScoreResponseDto currentDto = currentScoreOpt.get();
        assertEquals(asOfDate, currentDto.asOfDate());
        assertEquals(scoreResp.score(), currentDto.score());
        log.info("CurrentDto asOfDate={}, isCurrent={}, maxSystemDate={}", currentDto.asOfDate(), currentDto.isCurrent(), navObservationRepository.findMaxEffectiveDate().orElse(null));
        assertTrue(currentDto.isCurrent(), "CurrentDto must be marked as current when asOfDate matches latest system date");

        log.info("=== STEP 9: VERIFY SCORE HISTORY ENDPOINT ===");
        List<AnalyticalScoreResponse> history = analyticalScoringService.getScoreHistory(1L);
        log.info("Score History count: {}", history.size());
        assertTrue(history.stream().anyMatch(s -> s.scoreId().equals(scoreResp.scoreId())), "History must include the calculated current score snapshot");

        log.info("PASSED: HDFC Historical NAV Backfill and Current Score V1 verification complete!");
    }
}
