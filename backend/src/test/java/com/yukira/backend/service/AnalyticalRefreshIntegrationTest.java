package com.yukira.backend.service;

import com.yukira.backend.domain.entity.AnalyticalScore;
import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.ScoreDimension;
import com.yukira.backend.dto.scoring.AnalyticalRefreshResultDto;
import com.yukira.backend.dto.scoring.CurrentScoreResponseDto;
import com.yukira.backend.repository.AnalyticalScoreRepository;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.scoring.AnalyticalRefreshService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class AnalyticalRefreshIntegrationTest {

    @Autowired
    private AnalyticalRefreshService analyticalRefreshService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private AnalyticalScoreRepository analyticalScoreRepository;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    @Test
    @DisplayName("TC-AR01: Controlled production analytical refresh executes with explicit asOfDate and PIT metadata")
    void testAnalyticalRefreshExecutionWithPitMetadata() {
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        AnalyticalRefreshResultDto result = analyticalRefreshService.refreshAnalyticalScores(asOfDate, knowledgeCutoff);

        assertNotNull(result);
        assertEquals(asOfDate, result.asOfDate());
        assertEquals(knowledgeCutoff, result.knowledgeCutoffTime());
        assertTrue(result.totalFundsProcessed() > 0);
        assertTrue(result.scoresGenerated() + result.scoresCached() > 0);
        assertNotNull(result.summary());
        assertFalse(result.messages().isEmpty());
    }

    @Test
    @DisplayName("TC-AR02: Idempotent re-execution of analytical refresh skips redundant calculation and preserves DB score count")
    void testAnalyticalRefreshIdempotency() {
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        // First run
        AnalyticalRefreshResultDto run1 = analyticalRefreshService.refreshAnalyticalScores(asOfDate, knowledgeCutoff);
        long initialScoreCount = analyticalScoreRepository.count();

        // Second run with identical parameters
        AnalyticalRefreshResultDto run2 = analyticalRefreshService.refreshAnalyticalScores(asOfDate, knowledgeCutoff);

        assertEquals(0, run2.scoresGenerated(), "Second run must not generate new scores");
        assertTrue(run2.scoresCached() > 0, "Second run must retrieve cached scores from ledger");
        assertEquals(initialScoreCount, analyticalScoreRepository.count(), "Idempotent refresh must not duplicate analytical_score records");
    }

    @Test
    @DisplayName("TC-AR03: Historical score immutability — new analytical refresh creates new score snapshots without mutating historical scores")
    void testHistoricalScoreImmutability() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();

        LocalDate cutoff1 = LocalDate.of(2023, 12, 31);
        LocalDate cutoff2 = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        // Run for 2023-12-31
        analyticalRefreshService.refreshAnalyticalScores(cutoff1, knowledgeCutoff);
        List<AnalyticalScore> scoresForCutoff1 = analyticalScoreRepository.findBySchemeOptionAndDateAndVersion(
            pilot.getId(), cutoff1, "YUKIRA_SCORE_V1"
        );
        assertFalse(scoresForCutoff1.isEmpty());
        AnalyticalScore originalScore1 = scoresForCutoff1.get(0);
        Long originalScore1Id = originalScore1.getId();

        // Run for 2024-01-15
        analyticalRefreshService.refreshAnalyticalScores(cutoff2, knowledgeCutoff);
        List<AnalyticalScore> scoresForCutoff2 = analyticalScoreRepository.findBySchemeOptionAndDateAndVersion(
            pilot.getId(), cutoff2, "YUKIRA_SCORE_V1"
        );
        assertFalse(scoresForCutoff2.isEmpty());
        AnalyticalScore score2 = scoresForCutoff2.get(0);

        assertNotEquals(originalScore1Id, score2.getId(), "New cutoff refresh must create a distinct score snapshot ID");
        
        // Re-query cutoff 1 to ensure zero mutation
        AnalyticalScore requeried1 = analyticalScoreRepository.findById(originalScore1Id).orElseThrow();
        assertEquals(cutoff1, requeried1.getAsOfDate());
        assertEquals(originalScore1.getScore(), requeried1.getScore());
    }

    @Test
    @DisplayName("TC-AR04: Current score read model returns newest valid snapshot with data freshness & PIT context without triggering calculation")
    void testCurrentScoreReadModel() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        // Ensure score is refreshed
        analyticalRefreshService.refreshAnalyticalScores(asOfDate, knowledgeCutoff);

        long runCountBefore = calculationRunRepository.count();

        // Read current score
        Optional<CurrentScoreResponseDto> currentOpt = analyticalRefreshService.getCurrentScore(pilot.getId());

        assertTrue(currentOpt.isPresent());
        CurrentScoreResponseDto current = currentOpt.get();
        assertEquals(pilot.getId(), current.schemeOptionId());
        assertEquals("YUKIRA_SCORE_V1", current.scoreVersion());
        assertNotNull(current.score());
        assertTrue(current.isCurrent());
        assertNotNull(current.dataFreshnessState());

        long runCountAfter = calculationRunRepository.count();
        assertEquals(runCountBefore, runCountAfter, "getCurrentScore read operation MUST NOT trigger calculation runs");
    }

    @Test
    @DisplayName("TC-AR05: Current HDFC score selection prefers later valid same-date snapshot and reconciles persisted dimensions")
    void testCurrentHdfcScoreSelectionAndPersistedReconciliation() {
        AnalyticalScore insufficient = analyticalScoreRepository.findById(51661L).orElseThrow(
            () -> new AssertionError("Expected current audit INSUFFICIENT_DATA snapshot #51661"));
        AnalyticalScore valid = analyticalScoreRepository.findById(66494L).orElseThrow(
            () -> new AssertionError("Expected current audit valid snapshot #66494"));

        assertEquals(LocalDate.of(2026, 10, 1), insufficient.getAsOfDate());
        assertEquals("INSUFFICIENT_DATA", insufficient.getStatus());
        assertNull(insufficient.getCalculationRun());

        assertEquals(LocalDate.of(2026, 10, 1), valid.getAsOfDate());
        assertEquals("PARTIAL", valid.getStatus());
        assertEquals(8275L, valid.getCalculationRun().getId());
        assertTrue(valid.getCreatedAt().isAfter(insufficient.getCreatedAt()),
            "Later valid snapshot must deterministically outrank earlier same-date INSUFFICIENT_DATA snapshot");

        long runCountBefore = calculationRunRepository.count();
        CurrentScoreResponseDto current = analyticalRefreshService.getCurrentScore(1L).orElseThrow();
        long runCountAfter = calculationRunRepository.count();

        assertEquals(runCountBefore, runCountAfter, "Current score read must not create calculation runs");
        assertEquals(66494L, current.scoreId());
        assertEquals(8275L, current.calculationRunId());
        assertEquals(new BigDecimal("61.60"), current.score());
        assertEquals(new BigDecimal("100.00"), current.confidence());
        assertEquals("PARTIAL", current.status());
        assertEquals(LocalDate.of(2026, 10, 1), current.asOfDate());

        BigDecimal weightedDimensionSum = valid.getDimensions().stream()
            .filter(d -> d.getWeight().compareTo(BigDecimal.ZERO) > 0)
            .map(d -> d.getScore().multiply(d.getWeight()).setScale(4, RoundingMode.HALF_UP))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("61.60"), weightedDimensionSum.setScale(2, RoundingMode.HALF_UP));
        assertEquals(valid.getScore(), weightedDimensionSum.setScale(2, RoundingMode.HALF_UP));

        ScoreDimension evidence = valid.getDimensions().stream()
            .filter(d -> "EVIDENCE_CONFIDENCE".equals(d.getDimension()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Evidence & Data Confidence dimension must be persisted"));
        assertEquals(BigDecimal.ZERO.setScale(4), evidence.getWeight());

        List<Long> historyIds = analyticalScoreRepository.findLatestBySchemeOptionId(1L).stream()
            .map(AnalyticalScore::getId)
            .toList();
        assertTrue(historyIds.containsAll(List.of(48221L, 48224L, 51661L, 66494L)),
            "Historical snapshots must remain distinct and queryable");
    }
    @Test
    @DisplayName("TC-AR05: Benchmark dependency gate — missing benchmark observations past cutoff date are explicitly reported without benchmark data fabrication")
    void testBenchmarkDataDependencyGate() {
        LocalDate futureAsOfDate = LocalDate.of(2026, 10, 1);
        OffsetDateTime futureKnowledgeCutoff = OffsetDateTime.parse("2026-10-04T23:59:59+05:30");

        AnalyticalRefreshResultDto result = analyticalRefreshService.refreshAnalyticalScores(futureAsOfDate, futureKnowledgeCutoff);

        assertNotNull(result);
        assertEquals(futureAsOfDate, result.asOfDate());
        assertFalse(result.benchmarkDataAvailable(), "Benchmark observations for 2026 must be flagged as unavailable without fabrication");
        assertEquals("BENCHMARK_DEPENDENCY_REQUIRED", result.refreshStatus());
        assertTrue(result.messages().stream().anyMatch(m -> m.contains("BENCHMARK DATA DEPENDENCY")));
    }

    @Test
    @DisplayName("TC-AR06: Canonical HDFC scheme_option_id 1 pilot scoring regression")
    void testHdfcCanonicalPilotRegression() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        assertEquals("INF179K01UT0", pilot.getIsin());

        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        analyticalRefreshService.refreshAnalyticalScores(asOfDate, knowledgeCutoff);

        Optional<CurrentScoreResponseDto> scoreOpt = analyticalRefreshService.getCurrentScore(pilot.getId());
        assertTrue(scoreOpt.isPresent());
        CurrentScoreResponseDto score = scoreOpt.get();

        assertNotNull(score.score());
        assertTrue(score.score().doubleValue() > 0.0 && score.score().doubleValue() <= 100.0);
        assertEquals("CANDIDATE", score.methodologyStatus());
        assertNotNull(score.dimensions());
        assertFalse(score.dimensions().isEmpty());
    }

    @Test
    @DisplayName("TC-AR07: Complete score provenance — CalculationRun, MetricResult, and ScoreDimensions are preserved")
    void testCompleteScoreProvenancePreservation() {
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        analyticalRefreshService.refreshAnalyticalScores(asOfDate, knowledgeCutoff);

        AnalyticalScore score = analyticalScoreRepository.findBySchemeOptionAndDateAndVersion(
            pilot.getId(), asOfDate, "YUKIRA_SCORE_V1"
        ).get(0);

        assertNotNull(score.getCalculationRun());
        CalculationRun run = score.getCalculationRun();
        assertEquals(asOfDate, run.getAsOfDate());
        assertNotNull(run.getInputSnapshotSha256());

        List<MetricResult> metricResults = metricResultRepository.findByCalculationRunId(run.getId());
        assertFalse(metricResults.isEmpty());
        assertTrue(metricResults.stream().anyMatch(m -> "RET-03".equals(m.getMetricCode())));
        assertTrue(metricResults.stream().anyMatch(m -> "RSK-01".equals(m.getMetricCode())));

        assertNotNull(score.getDimensions());
        assertFalse(score.getDimensions().isEmpty());
    }
}
