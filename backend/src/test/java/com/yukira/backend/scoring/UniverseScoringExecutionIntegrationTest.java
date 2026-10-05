package com.yukira.backend.scoring;

import com.yukira.backend.domain.entity.AnalyticalScore;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.AnalyticalScoreRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.ScoreCalculationRequest;
import com.yukira.backend.scoring.dto.UniverseScoringReport;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class UniverseScoringExecutionIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(UniverseScoringExecutionIntegrationTest.class);

    @Autowired
    private AnalyticalScoringService scoringService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private AnalyticalScoreRepository analyticalScoreRepository;

    @Autowired
    private com.yukira.backend.repository.NavObservationRepository navObservationRepository;

    @Test
    @DisplayName("Pilot Validation: HDFC Flexi Cap Direct Growth Canonical Pilot Scoring")
    void testHdfcCanonicalPilotScoring() {
        // AMFI 118955, ISIN INF179K01UT0, SchemeOption #1
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955")
            .orElseThrow(() -> new IllegalStateException("Canonical pilot option 118955 not found"));

        assertEquals("INF179K01UT0", pilot.getIsin());

        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        long totalObs = navObservationRepository.countBySchemeOptionId(pilot.getId());
        long rangeObs = navObservationRepository.countBySchemeOptionIdAndDateRange(pilot.getId(), asOfDate.minusYears(3), asOfDate);
        log.info("Pilot Option #{}: AMFI={}, Category={}, Plan={}, Option={}, totalObs={}, rangeObs={}",
            pilot.getId(), pilot.getAmfiCode(),
            pilot.getPlan() != null && pilot.getPlan().getScheme() != null ? pilot.getPlan().getScheme().getCategory() : "NULL",
            pilot.getPlan() != null ? pilot.getPlan().getPlanType() : "NULL",
            pilot.getOptionType(),
            totalObs, rangeObs);

        ScoreCalculationRequest req = new ScoreCalculationRequest(
            pilot.getId(),
            null,
            asOfDate,
            cutoff,
            null
        );

        AnalyticalScoreResponse resp = scoringService.calculateScore(req);

        assertNotNull(resp);
        assertEquals(pilot.getId(), resp.schemeOptionId());
        assertEquals("YUKIRA_SCORE_V1", resp.scoreVersion());
        assertEquals("CANDIDATE", resp.methodologyStatus());
        assertEquals(asOfDate, resp.asOfDate());
        assertNotNull(resp.score(), "Pilot score value should be calculated from quantitative engine");
        assertTrue(resp.score().doubleValue() > 0.0 && resp.score().doubleValue() <= 100.0);
        assertNotNull(resp.confidence(), "Confidence score should be separate");
        assertTrue(resp.confidence().doubleValue() > 0.0);
        assertNotNull(resp.dimensions());
        assertFalse(resp.dimensions().isEmpty());

        // Verify retrieval from repository
        Optional<AnalyticalScoreResponse> latest = scoringService.getLatestScore(pilot.getId());
        assertTrue(latest.isPresent());
        assertEquals(resp.score(), latest.get().score());

        log.info("HDFC Canonical Pilot Score: {}/100, Confidence: {}/100, Status: {}",
            resp.score(), resp.confidence(), resp.status());
    }

    @Test
    @DisplayName("Incomplete Data Validation: Verify explicit score unavailable state without data fabrication")
    void testIncompleteDataHandling() {
        // Find an active scheme option with 0 NAV observations or non-equity category
        List<SchemeOption> allOptions = schemeOptionRepository.findAll();
        SchemeOption incompleteOption = allOptions.stream()
            .filter(o -> "REGULAR".equalsIgnoreCase(o.getPlan() != null ? o.getPlan().getPlanType() : ""))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No regular plan option found for validation"));

        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        ScoreCalculationRequest req = new ScoreCalculationRequest(
            incompleteOption.getId(),
            null,
            asOfDate,
            cutoff,
            null
        );

        AnalyticalScoreResponse resp = scoringService.calculateScore(req);

        assertNotNull(resp);
        assertNull(resp.score(), "Score value MUST be null for unsupported/incomplete data fund (no fabrication)");
        assertNotEquals("AVAILABLE", resp.status());
        assertTrue(resp.summary().contains("Score Unavailable") || resp.summary().contains("not yet supported") || resp.summary().contains("outside YUKIRA_SCORE_V1"));

        log.info("Incomplete Data Fund Option #{}: Status={}, Reason={}",
            incompleteOption.getId(), resp.status(), resp.summary());
    }

    @Test
    @DisplayName("Full Active Universe Scoring Batch & Idempotency Audit")
    void testActiveUniverseScoringBatchAndIdempotency() {
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        // Run 1: Batch score active universe
        UniverseScoringReport report1 = scoringService.scoreActiveUniverse(asOfDate, cutoff);

        assertNotNull(report1);
        assertTrue(report1.totalSchemeOptions() > 0);
        assertTrue(report1.activeSchemeOptions() > 0);
        assertEquals(report1.successfullyScored() + report1.scoreUnavailable(), report1.activeSchemeOptions());

        long initialScoreCount = analyticalScoreRepository.count();
        assertTrue(initialScoreCount >= report1.activeSchemeOptions());

        // Run 2: Idempotent re-execution
        UniverseScoringReport report2 = scoringService.scoreActiveUniverse(asOfDate, cutoff);

        assertEquals(report1.successfullyScored(), report2.successfullyScored());
        assertEquals(report1.scoreUnavailable(), report2.scoreUnavailable());
        assertEquals(initialScoreCount, analyticalScoreRepository.count(), "Idempotent run must not duplicate analytical_score DB records");

        log.info("Universe Scoring Report 1: Total={}, Active={}, Scored={}, Unavailable={}",
            report1.totalSchemeOptions(), report1.activeSchemeOptions(), report1.successfullyScored(), report1.scoreUnavailable());
    }

    @Test
    @DisplayName("Point-in-Time & Historical Score Preservation")
    void testHistoricalScorePreservation() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();

        LocalDate cutoff1 = LocalDate.of(2023, 12, 31);
        LocalDate cutoff2 = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        // Calculate score for historical date 2023-12-31
        AnalyticalScoreResponse resp1 = scoringService.calculateScore(new ScoreCalculationRequest(
            pilot.getId(), null, cutoff1, knowledgeCutoff, null
        ));

        // Calculate score for date 2024-01-15
        AnalyticalScoreResponse resp2 = scoringService.calculateScore(new ScoreCalculationRequest(
            pilot.getId(), null, cutoff2, knowledgeCutoff, null
        ));

        List<AnalyticalScore> historicalRuns = analyticalScoreRepository.findBySchemeOptionIdOrderByCreatedAtDesc(pilot.getId());
        assertTrue(historicalRuns.size() >= 2, "Historical score runs for distinct cutoff dates must both be preserved");

        assertEquals(cutoff1, resp1.asOfDate());
        assertEquals(cutoff2, resp2.asOfDate());
    }
}
