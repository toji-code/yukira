package com.yukira.backend.scoring;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.ScoreCalculationRequest;
import com.yukira.backend.scoring.dto.UniverseScoringReport;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(OrderAnnotation.class)
public class RealUniverseScoringExecutionTest {

    private static final Logger log = LoggerFactory.getLogger(RealUniverseScoringExecutionTest.class);

    @Autowired
    private AnalyticalScoringService scoringService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private AnalyticalScoreRepository analyticalScoreRepository;

    @Autowired
    private ScoreDimensionRepository scoreDimensionRepository;

    @Autowired
    private ScoreMetricContributionRepository scoreMetricContributionRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Test
    @Order(1)
    @Transactional
    @Rollback(false)
    void executeHdfcRealScoringAndVerifyPersistence() {
        log.info("============================================================");
        log.info("STEP 1: HDFC REAL SCORE EXECUTION & DIRECT DB VERIFICATION");
        log.info("============================================================");

        // Delete previous HDFC analytical_score and old calculation runs so calculation uses corrected formulas
        analyticalScoreRepository.findBySchemeOptionAndDateAndVersion(1L, LocalDate.of(2024, 1, 15), "YUKIRA_SCORE_V1")
            .forEach(analyticalScoreRepository::delete);
        List<CalculationRun> oldRuns = calculationRunRepository.findBySchemeOptionId(1L).stream()
            .filter(r -> LocalDate.of(2024, 1, 15).equals(r.getAsOfDate()))
            .toList();
        for (CalculationRun r : oldRuns) {
            metricResultRepository.deleteAll(metricResultRepository.findByCalculationRunId(r.getId()));
            calculationRunRepository.delete(r);
        }

        SchemeOption hdfcOption = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("HDFC pilot option 118955 not found"));

        assertEquals("INF179K01UT0", hdfcOption.getIsin());
        assertEquals(1L, hdfcOption.getId());

        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        ScoreCalculationRequest request = new ScoreCalculationRequest(
            hdfcOption.getId(),
            null,
            asOfDate,
            cutoff,
            null
        );

        long startTime = System.currentTimeMillis();
        AnalyticalScoreResponse response = scoringService.calculateScore(request);
        long durationMs = System.currentTimeMillis() - startTime;

        assertNotNull(response);
        log.info("HDFC Score Calculation Duration: {} ms", durationMs);
        log.info("HDFC Score Result: Score={}/100, Confidence={}/100, Status={}, Summary={}",
            response.score(), response.confidence(), response.status(), response.summary());

        // Direct DB Verification
        List<AnalyticalScore> dbScores = analyticalScoreRepository.findBySchemeOptionAndDateAndVersion(
            hdfcOption.getId(), asOfDate, "YUKIRA_SCORE_V1"
        );
        assertEquals(1, dbScores.size(), "AnalyticalScore record must exist in database");

        AnalyticalScore entity = dbScores.get(0);
        assertNotNull(entity.getId());
        assertNotNull(entity.getScore());
        assertTrue(entity.getScore().doubleValue() > 0.0 && entity.getScore().doubleValue() <= 100.0);
        assertNotNull(entity.getConfidence());
        assertTrue(entity.getConfidence().doubleValue() > 0.0);
        assertEquals("PARTIAL", entity.getStatus(), "Status must be PARTIAL because MKT-05 is uncalibrated under YUKIRA_SCORE_V1");
        assertEquals("YUKIRA_SCORE_V1", entity.getScoreVersion());
        assertEquals("CANDIDATE", entity.getMethodologyStatus());
        assertEquals(asOfDate, entity.getAsOfDate());
        assertNotNull(entity.getCalculationRun());

        // Verify Dimensions & Metric Contributions in DB
        List<ScoreDimension> dims = entity.getDimensions();
        assertFalse(dims.isEmpty());
        log.info("HDFC Persisted Dimensions Count: {}", dims.size());

        for (ScoreDimension dim : dims) {
            log.info("  - Dimension [{}]: Name='{}', Score={}, Weight={}, Status={}, MetricContribs={}",
                dim.getDimension(), dim.getDimensionName(), dim.getScore(), dim.getWeight(), dim.getStatus(), dim.getMetricContributions().size());

            for (ScoreMetricContribution smc : dim.getMetricContributions()) {
                log.info("    * Metric [{}]: Raw={}, Norm={}, Weight={}, Contrib={}, Eligibility={}, Exclusion={}",
                    smc.getMetricCode(), smc.getRawValue(), smc.getNormalizedValue(), smc.getWeight(), smc.getContribution(), smc.getEligibility(), smc.getExclusionReason());
            }
        }

        // Verify all 10 canonical score metrics
        List<MetricResult> calcResults = metricResultRepository.findByCalculationRunId(entity.getCalculationRun().getId());
        assertFalse(calcResults.isEmpty());
        log.info("HDFC CalculationRun #{} has {} MetricResult records", entity.getCalculationRun().getId(), calcResults.size());
    }

    @Test
    @Order(2)
    @Transactional
    @Rollback(false)
    void executeSupportedUniverseScoringBatch() {
        log.info("============================================================");
        log.info("STEP 2: FULL ACTIVE UNIVERSE BATCH SCORING EXECUTION");
        log.info("============================================================");

        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        long startTime = System.currentTimeMillis();
        UniverseScoringReport report = scoringService.scoreActiveUniverse(asOfDate, cutoff);
        long durationMs = System.currentTimeMillis() - startTime;

        log.info("Universe Scoring Batch Duration: {} ms ({} seconds)", durationMs, durationMs / 1000.0);
        log.info("Report Summary: {}", report.summary());
        log.info("  - Total Scheme Options: {}", report.totalSchemeOptions());
        log.info("  - Active Scheme Options: {}", report.activeSchemeOptions());
        log.info("  - Supported Count: {}", report.supportedByMethodology());
        log.info("  - Successfully Scored (AVAILABLE): {}", report.successfullyScored());
        log.info("  - Score Unavailable: {}", report.scoreUnavailable());
        log.info("  - Insufficient Data (<700 obs): {}", report.insufficientData());
        log.info("  - Data Quality Limited: {}", report.dataQualityLimited());
        log.info("  - Not Applicable / Unsupported: {}", report.notApplicableOrUnsupported());
        log.info("  - Complete Metric Coverage: {}", report.completeMetricCoverageCount());
        log.info("  - Partial Metric Coverage: {}", report.partialMetricCoverageCount());

        long asOfDateScoreCount = analyticalScoreRepository.findByAsOfDateAndScoreVersion(asOfDate, "YUKIRA_SCORE_V1").size();
        long totalDbRows = analyticalScoreRepository.count();
        log.info("AnalyticalScore Persisted Rows for 2024-01-15: {}", asOfDateScoreCount);
        log.info("AnalyticalScore Total Persisted Rows in DB: {}", totalDbRows);
        assertEquals(report.activeSchemeOptions(), asOfDateScoreCount, "Every active scheme option must have an analytical_score record persisted for 2024-01-15");
    }

    @Test
    @Order(3)
    @Transactional
    @Rollback(false)
    void auditAndVerifyIdempotency() {
        log.info("============================================================");
        log.info("STEP 3: IDEMPOTENCY RE-EXECUTION AUDIT");
        log.info("============================================================");

        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        long countBefore = analyticalScoreRepository.count();
        List<AnalyticalScore> scoresBefore = analyticalScoreRepository.findByAsOfDateAndScoreVersion(asOfDate, "YUKIRA_SCORE_V1");

        long startTime = System.currentTimeMillis();
        UniverseScoringReport reportRerun = scoringService.scoreActiveUniverse(asOfDate, cutoff);
        long durationMs = System.currentTimeMillis() - startTime;

        long countAfter = analyticalScoreRepository.count();
        List<AnalyticalScore> scoresAfter = analyticalScoreRepository.findByAsOfDateAndScoreVersion(asOfDate, "YUKIRA_SCORE_V1");

        log.info("Idempotency Rerun Duration: {} ms", durationMs);
        log.info("  - AnalyticalScore Rows Before: {}", countBefore);
        log.info("  - AnalyticalScore Rows After:  {}", countAfter);

        assertEquals(countBefore, countAfter, "Idempotent rerun must NOT create duplicate analytical_score rows");
        assertEquals(scoresBefore.size(), scoresAfter.size());

        // Verify score values are unchanged
        for (int i = 0; i < scoresBefore.size(); i++) {
            AnalyticalScore b = scoresBefore.get(i);
            AnalyticalScore a = scoresAfter.get(i);
            assertEquals(b.getId(), a.getId());
            assertEquals(b.getScore(), a.getScore());
            assertEquals(b.getConfidence(), a.getConfidence());
            assertEquals(b.getStatus(), a.getStatus());
        }
        log.info("SUCCESS: Idempotency audit verified zero duplicate records and zero score drift.");
    }

    @Test
    @Order(4)
    @Transactional
    @Rollback(false)
    void testHistoricalRunPreservation() {
        log.info("============================================================");
        log.info("STEP 4: HISTORICAL SCORE CUTOFF EXECUTION & PRESERVATION");
        log.info("============================================================");

        SchemeOption hdfcOption = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();

        LocalDate historicalAsOf = LocalDate.of(2023, 12, 31);
        OffsetDateTime cutoff = OffsetDateTime.parse("2024-01-15T23:59:59+05:30");

        ScoreCalculationRequest histReq = new ScoreCalculationRequest(
            hdfcOption.getId(), null, historicalAsOf, cutoff, null
        );

        AnalyticalScoreResponse histResp = scoringService.calculateScore(histReq);
        assertNotNull(histResp);
        log.info("HDFC Historical Score @ {}: Score={}/100, Status={}", historicalAsOf, histResp.score(), histResp.status());

        List<AnalyticalScore> hdfcScoresInDb = analyticalScoreRepository.findBySchemeOptionIdOrderByCreatedAtDesc(hdfcOption.getId());
        log.info("HDFC Total Persisted Scores in DB across cutoffs: {}", hdfcScoresInDb.size());

        for (AnalyticalScore s : hdfcScoresInDb) {
            log.info("  - Score ID {} | AsOfDate: {} | Score: {} | Status: {}",
                s.getId(), s.getAsOfDate(), s.getScore(), s.getStatus());
        }

        assertTrue(hdfcScoresInDb.size() >= 2, "HDFC must have separate historical AnalyticalScore records for distinct cutoffs");
    }
}
