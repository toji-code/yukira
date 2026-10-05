package com.yukira.backend.scoring;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.controller.ScoringController;
import com.yukira.backend.domain.entity.Benchmark;
import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.scoring.CurrentScoreResponseDto;
import com.yukira.backend.repository.BenchmarkRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.ScoreCalculationRequest;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
import com.yukira.backend.service.CalculationOrchestratorService;
import com.yukira.backend.service.scoring.AnalyticalRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ScoreProvenanceAndInvestorEvidenceIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ScoreProvenanceAndInvestorEvidenceIntegrationTest.class);

    @Autowired
    private ScoringController scoringController;

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private AnalyticalScoringService analyticalScoringService;

    @Autowired
    private CalculationOrchestratorService calculationOrchestratorService;

    @Autowired
    private AnalyticalRefreshService analyticalRefreshService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @BeforeEach
    void setUp() {
        pilotBootstrapService.bootstrapPilot();
        pilotBootstrapService.bootstrapHistoricalBenchmark();
        pilotBootstrapService.bootstrapHistoricalFbil();

        // Ensure 10189 / 2026-10-01 run and score exist
        SchemeOption option = schemeOptionRepository.findById(10189L).orElseThrow();
        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI").orElseThrow();
        LocalDate asOfDate = LocalDate.of(2026, 10, 1);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2026, 10, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        CalculationRun run = calculationOrchestratorService.executeCalculationRun(
            option.getId(),
            benchmark.getId(),
            asOfDate,
            knowledgeCutoff,
            List.of("RET-03", "RET-07", "RSK-01", "RSK-02", "RSK-03", "REL-02", "RAT-04", "MKT-01", "MKT-02", "MKT-05"),
            "CORE_QUANT_V1",
            Map.of("start_date", "2023-10-01")
        );

        analyticalScoringService.calculateScore(new ScoreCalculationRequest(
            option.getId(), run.getId(), asOfDate, knowledgeCutoff, benchmark.getId()
        ));
    }

    @Test
    @DisplayName("EVIDENCE AUDIT 1: GET /api/v1/scores/10189/current returns complete, auditable provenance for #68919")
    void testCurrentScoreEndpointProvenanceAndEvidenceCompleteness() {
        log.info("Testing GET /api/v1/scores/10189/current provenance and evidence completeness...");

        ResponseEntity<?> response = scoringController.getCurrentScore(10189L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        CurrentScoreResponseDto dto = (CurrentScoreResponseDto) response.getBody();
        assertNotNull(dto, "CurrentScoreResponseDto must not be null");

        log.info("Current Score Response: scoreId={}, score={}, status={}, asOfDate={}, runId={}",
            dto.scoreId(), dto.score(), dto.status(), dto.asOfDate(), dto.calculationRunId());

        // 1. Overall Score Provenance Checks
        assertNotNull(dto.scoreId());
        assertEquals(10189L, dto.schemeOptionId());
        assertEquals(new BigDecimal("55.05"), dto.score());
        assertEquals(new BigDecimal("100.00"), dto.confidence());
        assertEquals("PARTIAL", dto.status());
        assertEquals("YUKIRA_SCORE_V1", dto.scoreVersion());
        assertEquals("CANDIDATE", dto.methodologyStatus());
        assertEquals(LocalDate.of(2026, 10, 1), dto.asOfDate());
        assertNotNull(dto.calculationRunId());
        assertEquals("INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1", dto.referencePopulation());
        assertTrue(dto.isCurrent(), "isCurrent must be true for current endpoint");
        assertNotNull(dto.summary());
        assertNotNull(dto.disclaimer());

        // 2. Evidence Confidence Provenance Checks
        assertNotNull(dto.evidenceConfidence());
        assertTrue(dto.evidenceConfidence().meetsObservationThreshold());
        assertTrue(dto.evidenceConfidence().sourceArtifactVerified());
        assertTrue(dto.evidenceConfidence().pitIntegrityMaintained());
        assertEquals("HIGH_CONFIDENCE", dto.evidenceConfidence().assessment());

        // 3. Dimensions Provenance Checks (4 scoring dimensions)
        List<AnalyticalScoreResponse.DimensionScoreDto> dimensions = dto.dimensions();
        assertNotNull(dimensions);
        assertEquals(4, dimensions.size(), "Must have exactly 4 scoring dimensions");

        Map<String, String> expectedMetricNames = Map.of(
            "RET-03", "3-Year Compound Annual Growth Rate (CAGR)",
            "RET-07", "3-Year Annualized Active Return",
            "RSK-01", "3-Year Annualized Volatility",
            "RSK-02", "3-Year Downside Semideviation",
            "RSK-03", "3-Year Maximum Drawdown",
            "REL-02", "Jensen's Alpha 3Y",
            "RAT-04", "Information Ratio 3Y",
            "MKT-01", "Beta 3Y",
            "MKT-02", "Downside Beta 3Y",
            "MKT-05", "Capture Spread 3Y"
        );

        int totalMetricsVerified = 0;

        for (AnalyticalScoreResponse.DimensionScoreDto dim : dimensions) {
            log.info("  Dimension [{}] Score: {}, Weight: {}, EffWeight: {}, Status: {}",
                dim.dimension(), dim.score(), dim.weight(), dim.effectiveWeight(), dim.status());
            assertNotNull(dim.dimension());
            assertNotNull(dim.dimensionName());
            assertNotNull(dim.score());
            assertNotNull(dim.weight());
            assertNotNull(dim.effectiveWeight());
            assertNotNull(dim.metricContributions());

            for (AnalyticalScoreResponse.MetricContributionDto mc : dim.metricContributions()) {
                totalMetricsVerified++;
                log.info("    * Metric [{}] Name='{}', RawVal={}, NormVal={}, Eligibility={}, EffWeight={}",
                    mc.metricCode(), mc.metricName(), mc.formattedRawValue(), mc.normalizedValue(), mc.eligibility(), mc.effectiveWeight());

                assertNotNull(mc.metricCode());
                assertEquals(expectedMetricNames.get(mc.metricCode()), mc.metricName(), "Metric name contract mismatch for " + mc.metricCode());
                assertNotNull(mc.eligibility());
                assertNotNull(mc.weight());
                assertNotNull(mc.effectiveWeight());
                assertNotNull(mc.contribution());

                if ("MKT-05".equals(mc.metricCode())) {
                    assertEquals("UNCALIBRATED", mc.eligibility());
                    assertEquals(0, BigDecimal.ZERO.compareTo(mc.effectiveWeight()), "MKT-05 effective weight must be 0.0000");
                    assertEquals(0, BigDecimal.ZERO.compareTo(mc.contribution()), "MKT-05 contribution must be 0.0000");
                } else {
                    assertEquals("ELIGIBLE", mc.eligibility());
                    assertNotNull(mc.rawValue());
                    assertNotNull(mc.formattedRawValue());
                    assertNotNull(mc.normalizedValue());
                    assertNotNull(mc.metricResultId());
                }
            }
        }

        assertEquals(10, totalMetricsVerified, "Must verify all 10 canonical metric contributions");
    }

    @Test
    @DisplayName("EVIDENCE AUDIT 2: Mathematical Reconcilability of Score API Output")
    void testScoreApiMathematicalReconcilability() {
        log.info("Testing mathematical reconcilability of GET /api/v1/scores/10189/current API output...");

        ResponseEntity<?> response = scoringController.getCurrentScore(10189L);
        CurrentScoreResponseDto dto = (CurrentScoreResponseDto) response.getBody();
        assertNotNull(dto);

        BigDecimal sumDimWeightedScore = BigDecimal.ZERO;
        BigDecimal sumDimWeight = BigDecimal.ZERO;

        for (AnalyticalScoreResponse.DimensionScoreDto dim : dto.dimensions()) {
            BigDecimal sumMetricContrib = BigDecimal.ZERO;
            for (AnalyticalScoreResponse.MetricContributionDto mc : dim.metricContributions()) {
                if ("ELIGIBLE".equals(mc.eligibility())) {
                    sumMetricContrib = sumMetricContrib.add(mc.contribution());
                }
            }

            BigDecimal expectedDimScore = sumMetricContrib.setScale(2, RoundingMode.HALF_UP);
            assertEquals(0, expectedDimScore.compareTo(dim.score()), "Dimension " + dim.dimension() + " API score reconciliation failed");

            sumDimWeightedScore = sumDimWeightedScore.add(dim.score().multiply(dim.effectiveWeight()));
            sumDimWeight = sumDimWeight.add(dim.effectiveWeight());
        }

        BigDecimal expectedOverallScore = sumDimWeightedScore.divide(sumDimWeight, 2, RoundingMode.HALF_UP);
        assertEquals(0, expectedOverallScore.compareTo(dto.score()), "Overall API score reconciliation failed");
        log.info("Successfully reconciled API overall score {} from dimension contributions!", dto.score());
    }

    @Test
    @DisplayName("EVIDENCE AUDIT 3: Verify GET /api/v1/scores/methodology governance and transparency")
    void testMethodologyEndpointGovernance() {
        log.info("Testing GET /api/v1/scores/methodology governance transparency...");

        ResponseEntity<?> response = scoringController.getMethodologyConfig();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);

        assertEquals("YUKIRA_SCORE_V1", body.get("scoreVersion"));
        assertEquals("CANDIDATE", body.get("methodologyStatus"));
        assertEquals("INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1", body.get("referencePopulation"));
        assertNotNull(body.get("disclaimer"));
        assertNotNull(body.get("authorizedScoreInputMetrics"));
        assertNotNull(body.get("unauthorizedMetrics"));

        log.info("Verified /methodology endpoint governance details!");
    }

    @Test
    @DisplayName("EVIDENCE AUDIT 4: Verify GET /api/v1/scores/run/{runId} and history endpoints")
    void testRunAndHistoryEndpoints() {
        log.info("Testing GET /api/v1/scores/run/{runId} and /history endpoints...");

        Optional<AnalyticalScoreResponse> latestOpt = analyticalScoringService.getLatestScore(10189L);
        assertTrue(latestOpt.isPresent());
        AnalyticalScoreResponse latest = latestOpt.get();

        // 1. Test /run/{calculationRunId}
        ResponseEntity<?> runResp = scoringController.getScoreByCalculationRunId(latest.calculationRunId());
        assertEquals(HttpStatus.OK, runResp.getStatusCode());
        AnalyticalScoreResponse runScore = (AnalyticalScoreResponse) runResp.getBody();
        assertNotNull(runScore);
        assertEquals(latest.scoreId(), runScore.scoreId());
        assertEquals(latest.calculationRunId(), runScore.calculationRunId());

        // 2. Test /{schemeOptionId}/history
        ResponseEntity<?> historyResp = scoringController.getScoreHistory(10189L);
        assertEquals(HttpStatus.OK, historyResp.getStatusCode());
        List<?> history = (List<?>) historyResp.getBody();
        assertNotNull(history);
        assertFalse(history.isEmpty());
        log.info("Score history entry count for scheme option 10189: {}", history.size());
    }
}
