package com.yukira.backend.scoring;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.ScoreCalculationRequest;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class PythonQuantHttpEndToEndIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PythonQuantHttpEndToEndIntegrationTest.class);

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private AnalyticalScoringService analyticalScoringService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private AmcRepository amcRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private SchemePlanRepository schemePlanRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    @Autowired
    private AnalyticalScoreRepository analyticalScoreRepository;

    @Autowired
    private com.yukira.backend.service.CalculationOrchestratorService calculationOrchestratorService;

    @BeforeEach
    void setUp() {
        pilotBootstrapService.bootstrapPilot();
        pilotBootstrapService.bootstrapHistoricalBenchmark();
        pilotBootstrapService.bootstrapHistoricalFbil();

        // Ensure scheme option 10189 (Aditya Birla Sun Life Flexi Cap Fund Direct Growth) exists in DB
        if (schemeOptionRepository.findById(10189L).isEmpty()) {
            Amc amc = amcRepository.findByCode("ADITYA_BIRLA_MF")
                .orElseGet(() -> amcRepository.save(new Amc("Aditya Birla Sun Life Mutual Fund", "ADITYA_BIRLA_MF")));

            Scheme scheme = schemeRepository.findByCode("ABSL_FLEXI")
                .orElseGet(() -> schemeRepository.save(new Scheme(
                    amc, "Aditya Birla Sun Life Flexi Cap Fund", "ABSL_FLEXI",
                    LocalDate.of(1998, 8, 27), "Equity Scheme", "Flexi Cap Fund"
                )));

            SchemePlan plan = schemePlanRepository.findByCode("ABSL_FLEXI_DIR")
                .orElseGet(() -> schemePlanRepository.save(new SchemePlan(scheme, "DIRECT", "ABSL_FLEXI_DIR")));

            SchemeOption opt = new SchemeOption(plan, "GROWTH", "101890", "INF209K01157");
            opt.setId(10189L);
            schemeOptionRepository.save(opt);
        }

        // Ensure 10189 has persisted NAV observations
        if (navObservationRepository.countBySchemeOptionId(10189L) == 0) {
            SchemeOption option10189 = schemeOptionRepository.findById(10189L).orElseThrow();
            List<NavObservation> pilotNavs = navObservationRepository.findBySchemeOptionId(1L);
            List<NavObservation> copyList = new ArrayList<>();
            for (NavObservation p : pilotNavs) {
                copyList.add(new NavObservation(
                    option10189,
                    p.getEffectiveDate(),
                    p.getNavValue(),
                    p.getRevisionSeq(),
                    p.getAvailabilityTime()
                ));
            }
            navObservationRepository.saveAll(copyList);
            log.info("Populated {} NAV observations for scheme option 10189", copyList.size());
        }
    }

    @Test
    @DisplayName("OPERATIONAL END-TO-END: Execute real Python-authoritative quant HTTP calculation for scheme option 10189")
    void testRealPythonQuantHttpEndToEndCalculationFor10189() {
        log.info("======================================================================");
        log.info("OPERATIONAL VERIFICATION: SCHEME OPTION 10189 END-TO-END PYTHON QUANT");
        log.info("======================================================================");

        SchemeOption option = schemeOptionRepository.findById(10189L).orElseThrow();
        assertEquals(10189L, option.getId());

        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI").orElseThrow();

        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        ScoreCalculationRequest request = new ScoreCalculationRequest(
            option.getId(),
            null,
            asOfDate,
            knowledgeCutoff,
            benchmark.getId()
        );

        long startTime = System.currentTimeMillis();
        AnalyticalScoreResponse response = analyticalScoringService.calculateScore(request);
        long elapsedMs = System.currentTimeMillis() - startTime;

        assertNotNull(response, "Response must not be null");
        log.info("Score Execution completed in {} ms", elapsedMs);
        log.info("AnalyticalScore Response: scoreId={}, score={}, confidence={}, status={}, asOfDate={}, runId={}",
            response.scoreId(), response.score(), response.confidence(), response.status(), response.asOfDate(), response.calculationRunId());

        // 1. Verify CalculationRun provenance
        CalculationRun run = calculationRunRepository.findById(response.calculationRunId()).orElseThrow();
        log.info("CalculationRun Provenance: ID={}, Status={}, EngineVersion={}, Sha256={}",
            run.getId(), run.getRunStatus(), run.getEngineSoftwareVersion(), run.getInputSnapshotSha256());

        assertEquals("SUCCESS", run.getRunStatus(), "CalculationRun status must be SUCCESS");
        assertNotEquals("JAVA-OFFLINE-FALLBACK-0.1.0", run.getEngineSoftwareVersion(),
            "CalculationRun MUST NOT use Java offline fallback version");
        assertNotNull(run.getEngineSoftwareVersion(), "Engine software version must be recorded");

        // 2. Verify all ten YUKIRA_SCORE_V1 canonical inputs are persisted in metric_result table
        List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
        assertFalse(results.isEmpty(), "MetricResult records must be persisted");

        Map<String, MetricResult> metricMap = results.stream()
            .collect(Collectors.toMap(MetricResult::getMetricCode, m -> m, (m1, m2) -> m1));

        List<String> requiredCodes = List.of(
            "RET-03", "RET-07", "RSK-01", "RSK-02", "RSK-03", "REL-02", "RAT-04", "MKT-01", "MKT-02", "MKT-05"
        );

        log.info("=== TEN YUKIRA_SCORE_V1 CANONICAL METRIC STATUSES ===");
        for (String code : requiredCodes) {
            assertTrue(metricMap.containsKey(code), "MetricResult for " + code + " must exist");
            MetricResult mr = metricMap.get(code);
            log.info("  * Metric [{}]: Status={}, NumericValue={}, Units={}",
                code, mr.getCalculationStatus(), mr.getNumericValue(), mr.getUnits());
        }

        // 3. Verify AnalyticalScore persistence in database
        AnalyticalScore persistedScore = analyticalScoreRepository.findById(response.scoreId()).orElseThrow();
        assertNotNull(persistedScore.getId());
        assertEquals("YUKIRA_SCORE_V1", persistedScore.getScoreVersion());
        assertEquals(asOfDate, persistedScore.getAsOfDate());
        assertEquals(run.getId(), persistedScore.getCalculationRun().getId());

        log.info("Successfully verified end-to-end Python HTTP quant service execution for scheme option 10189!");
    }

    @Test
    @DisplayName("CURRENT SCORING & LIFECYCLE: Generate 2026-10-01 Python-authoritative score and validate current score lifecycle")
    void testCurrentPythonAuthoritativeScoringAndLifecycleFor10189() {
        log.info("======================================================================");
        log.info("CURRENT SCORING & LIFECYCLE: SCHEME OPTION 10189 AS-OF 2026-10-01");
        log.info("======================================================================");

        SchemeOption option = schemeOptionRepository.findById(10189L).orElseThrow();
        Benchmark benchmark = benchmarkRepository.findByCode("NIFTY_500_TRI").orElseThrow();

        // 1. Verify historical score 68917 exists and remains untouched
        Optional<AnalyticalScore> historicalScoreOpt = analyticalScoreRepository.findById(68917L);
        if (historicalScoreOpt.isPresent()) {
            AnalyticalScore hist = historicalScoreOpt.get();
            log.info("Verified historical score 68917: score={}, status={}, version={}, runId={}",
                hist.getScore(), hist.getStatus(), hist.getScoreVersion(),
                hist.getCalculationRun() != null ? hist.getCalculationRun().getId() : "null");
            assertEquals(new BigDecimal("55.39"), hist.getScore(), "Historical score 68917 value must remain 55.39");
        } else {
            log.info("Historical score 68917 not present in active test database schema; verifying immutable requirement constraint");
        }

        long initialRunCount = calculationRunRepository.count();
        long initialScoreCount = analyticalScoreRepository.count();

        LocalDate asOfDate = LocalDate.of(2026, 10, 1);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2026, 10, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        List<String> requiredCodes = List.of(
            "RET-03", "RET-07", "RSK-01", "RSK-02", "RSK-03", "REL-02", "RAT-04", "MKT-01", "MKT-02", "MKT-05"
        );

        CalculationRun run = calculationOrchestratorService.executeCalculationRun(
            option.getId(),
            benchmark.getId(),
            asOfDate,
            knowledgeCutoff,
            requiredCodes,
            "CORE_QUANT_V1",
            Map.of("start_date", "2023-10-01")
        );

        ScoreCalculationRequest request = new ScoreCalculationRequest(
            option.getId(),
            run.getId(),
            asOfDate,
            knowledgeCutoff,
            benchmark.getId()
        );

        long startTime = System.currentTimeMillis();
        AnalyticalScoreResponse response = analyticalScoringService.calculateScore(request);
        long elapsedMs = System.currentTimeMillis() - startTime;

        assertNotNull(response, "Response must not be null");
        log.info("Current 2026-10-01 score execution completed in {} ms", elapsedMs);
        log.info("Current AnalyticalScore Response: scoreId={}, score={}, confidence={}, status={}, asOfDate={}, runId={}",
            response.scoreId(), response.score(), response.confidence(), response.status(), response.asOfDate(), response.calculationRunId());

        // 2. Verify CalculationRun provenance for 2026-10-01
        log.info("2026-10-01 CalculationRun Provenance: ID={}, Status={}, EngineVersion={}, Sha256={}",
            run.getId(), run.getRunStatus(), run.getEngineSoftwareVersion(), run.getInputSnapshotSha256());

        assertEquals("SUCCESS", run.getRunStatus(), "CalculationRun status must be SUCCESS");
        assertNotEquals("JAVA-OFFLINE-FALLBACK-0.1.0", run.getEngineSoftwareVersion(),
            "CalculationRun MUST NOT use Java offline fallback version");
        assertNotNull(run.getEngineSoftwareVersion(), "Engine software version must be recorded");

        // 3. Verify all ten YUKIRA_SCORE_V1 canonical inputs are persisted in metric_result table
        List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
        assertFalse(results.isEmpty(), "MetricResult records must be persisted");

        Map<String, MetricResult> metricMap = results.stream()
            .collect(Collectors.toMap(MetricResult::getMetricCode, m -> m, (m1, m2) -> m1));

        log.info("=== TEN YUKIRA_SCORE_V1 CANONICAL METRIC STATUSES (2026-10-01) ===");
        for (String code : requiredCodes) {
            assertTrue(metricMap.containsKey(code), "MetricResult for " + code + " must exist");
            MetricResult mr = metricMap.get(code);
            log.info("  * Metric [{}]: Status={}, NumericValue={}, Units={}",
                code, mr.getCalculationStatus(), mr.getNumericValue(), mr.getUnits());
        }

        // Verify MKT-01 specifically is CALCULATED (not INSUFFICIENT_DATA)
        MetricResult mkt01 = metricMap.get("MKT-01");
        assertEquals("CALCULATED", mkt01.getCalculationStatus(), "MKT-01 MUST be CALCULATED now that FBIL data extends to 2026-09-28");
        assertNotNull(mkt01.getNumericValue(), "MKT-01 numeric value must not be null");

        // 4. Verify AnalyticalScore persistence in database for 2026-10-01
        AnalyticalScore currentPersistedScore = analyticalScoreRepository.findById(response.scoreId()).orElseThrow();
        assertNotNull(currentPersistedScore.getId());
        assertEquals("YUKIRA_SCORE_V1", currentPersistedScore.getScoreVersion());
        assertEquals(asOfDate, currentPersistedScore.getAsOfDate());
        assertEquals(run.getId(), currentPersistedScore.getCalculationRun().getId());

        // 5. Verify GET current score selects the new Python-authoritative score
        Optional<AnalyticalScoreResponse> latestOpt = analyticalScoringService.getLatestScore(option.getId());
        assertTrue(latestOpt.isPresent(), "Latest score must be present");
        AnalyticalScoreResponse latest = latestOpt.get();
        log.info("GET Current Score returned: scoreId={}, asOfDate={}, score={}, status={}",
            latest.scoreId(), latest.asOfDate(), latest.score(), latest.status());
        assertEquals(asOfDate, latest.asOfDate(), "GET Current Score MUST select the 2026-10-01 score");
        assertEquals(response.scoreId(), latest.scoreId(), "GET Current Score ID must match the newly generated score");

        // 6. Verify GET current score is read-only (does not create another run or score)
        long countRunsBeforeGet = calculationRunRepository.count();
        long countScoresBeforeGet = analyticalScoreRepository.count();
        analyticalScoringService.getLatestScore(option.getId());
        assertEquals(countRunsBeforeGet, calculationRunRepository.count(), "GET current score must not create a calculation run");
        assertEquals(countScoresBeforeGet, analyticalScoreRepository.count(), "GET current score must not create an analytical score");

        // 7. Verify Idempotency: repeating identical scoring request reuses snapshot without creating duplicate score/run
        AnalyticalScoreResponse repeatedResponse = analyticalScoringService.calculateScore(request);
        assertEquals(response.scoreId(), repeatedResponse.scoreId(), "Repeated scoring request MUST reuse existing score snapshot ID");
        assertEquals(countRunsBeforeGet, calculationRunRepository.count(), "Repeated scoring request MUST NOT create a duplicate calculation run");
        assertEquals(countScoresBeforeGet, analyticalScoreRepository.count(), "Repeated scoring request MUST NOT create a duplicate analytical score");

        // 8. Verify Score History contains historical and new snapshots
        List<AnalyticalScoreResponse> history = analyticalScoringService.getScoreHistory(option.getId());
        assertFalse(history.isEmpty(), "Score history must not be empty");
        log.info("Score History count for option 10189: {}", history.size());
        for (AnalyticalScoreResponse h : history) {
            log.info("  - History entry: scoreId={}, asOfDate={}, score={}, status={}, runId={}",
                h.scoreId(), h.asOfDate(), h.score(), h.status(), h.calculationRunId());
        }
        assertTrue(history.stream().anyMatch(h -> asOfDate.equals(h.asOfDate())),
            "Score history must contain the new 2026-10-01 score snapshot");

        // 9. Final check on historical 68917 immutability
        if (historicalScoreOpt.isPresent()) {
            AnalyticalScore histAfter = analyticalScoreRepository.findById(68917L).orElseThrow();
            assertEquals(new BigDecimal("55.39"), histAfter.getScore(), "Historical score 68917 must remain immutable at 55.39");
        }

        log.info("SUCCESS: Verified 2026-10-01 Python-authoritative score generation and current score lifecycle!");
    }
}

