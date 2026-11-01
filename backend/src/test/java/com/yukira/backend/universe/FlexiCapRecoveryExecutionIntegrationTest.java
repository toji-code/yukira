package com.yukira.backend.universe;

import com.yukira.backend.domain.entity.CalculationRun;
import com.yukira.backend.domain.entity.MetricResult;
import com.yukira.backend.repository.CalculationRunRepository;
import com.yukira.backend.repository.MetricResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FlexiCapRecoveryExecutionIntegrationTest {

    @Autowired
    private UniverseBatchOrchestratorService batchOrchestratorService;

    @Autowired
    private UniverseScreenerService universeScreenerService;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 10, 1);
    private static final OffsetDateTime KNOWLEDGE_CUTOFF = OffsetDateTime.of(2026, 10, 1, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

    @Test
    @Order(1)
    @DisplayName("Pre-flight dry run preview for Flexi Cap cohort produces zero mutations")
    void testDryRunPreviewZeroMutations() {
        long runCountBefore = calculationRunRepository.count();
        long metricCountBefore = metricResultRepository.count();

        UniverseBatchRunRequest dryRunReq = new UniverseBatchRunRequest(
            15,
            EquityUniverseCategory.FLEXI_CAP,
            LocalDate.of(2021, 1, 1),
            AS_OF_DATE,
            AS_OF_DATE,
            KNOWLEDGE_CUTOFF,
            true, // dryRun = true
            false, // backfillNavIfMissing = false
            false, // ingestBenchmarkIfMissing = false
            true,  // calculateMetrics = true
            "dry-run-operator",
            false
        );

        UniverseBatchRunReportDto report = batchOrchestratorService.executeBatchRun(dryRunReq);
        assertNotNull(report);
        assertTrue(report.dryRun());
        assertEquals(15, report.totalSelected());
        assertEquals(0, report.totalCalculated());

        long runCountAfter = calculationRunRepository.count();
        long metricCountAfter = metricResultRepository.count();

        assertEquals(runCountBefore, runCountAfter, "Dry run must not create calculation runs");
        assertEquals(metricCountBefore, metricCountAfter, "Dry run must not create metric results");
    }

    @Test
    @Order(2)
    @DisplayName("Execute bounded batch calculation for 14 deficient Flexi Cap options with recovered risk-free observations")
    void testExecuteFlexiCapBatchCalculation() {
        UniverseBatchRunRequest batchReq = new UniverseBatchRunRequest(
            15,
            EquityUniverseCategory.FLEXI_CAP,
            LocalDate.of(2021, 1, 1),
            AS_OF_DATE,
            AS_OF_DATE,
            KNOWLEDGE_CUTOFF,
            false, // real execution
            false, // backfillNavIfMissing = false
            false, // ingestBenchmarkIfMissing = false
            true,  // calculateMetrics = true
            "antigravity-flexicap-recovery",
            false // forceRecalculate = false (Option 1 skipped, 14 deficient options calculated)
        );

        UniverseBatchRunReportDto report = batchOrchestratorService.executeBatchRun(batchReq);
        assertNotNull(report);
        System.out.println("RECOVERY BATCH REPORT: totalSelected=" + report.totalSelected()
            + ", totalCalculated=" + report.totalCalculated()
            + ", totalSkipped=" + report.totalSkipped()
            + ", totalFailed=" + report.totalFailed());

        for (SchemeBatchExecutionDetailDto d : report.schemeOutcomes()) {
            System.out.println("  Option " + d.schemeOptionId() + ": " + d.schemeName()
                + " -> " + d.calculationOutcome() + ", runId=" + d.calculationRunId()
                + ", metrics=" + d.metricsCalculated() + ", err=" + d.failureOrSkipReason());
        }

        assertEquals(15, report.totalSelected());
        assertEquals(0, report.totalFailed(), "All Flexi Cap calculations must succeed with zero failures");
        assertEquals(15, report.totalSkipped() + report.totalCalculated(), "All 15 options must be accounted for");
        assertTrue(report.totalSkipped() >= 2, "At least Option 1 and Option 10189 must be skipped");

        // Verify that each newly calculated run has all 8 canonical metrics with non-null values
        for (SchemeBatchExecutionDetailDto d : report.schemeOutcomes()) {
            if ("CALCULATED".equals(d.calculationOutcome())) {
                assertNotNull(d.calculationRunId());
                List<MetricResult> results = metricResultRepository.findByCalculationRunId(d.calculationRunId());
                assertNotNull(results);
                assertEquals(8, results.size(), "Each run must persist all 8 canonical metrics");
                for (MetricResult mr : results) {
                    assertEquals("CALCULATED", mr.getCalculationStatus(), "Metric " + mr.getMetricCode() + " must be CALCULATED");
                    assertNotNull(mr.getNumericValue(), "Metric " + mr.getMetricCode() + " must have non-null numeric value");
                    assertNull(mr.getErrorMessage(), "Metric " + mr.getMetricCode() + " must have null error message");
                }
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("Idempotent second run skips all 15 Flexi Cap options without re-calculation")
    void testIdempotentFlexiCapRun() {
        UniverseBatchRunRequest batchReq = new UniverseBatchRunRequest(
            15,
            EquityUniverseCategory.FLEXI_CAP,
            LocalDate.of(2021, 1, 1),
            AS_OF_DATE,
            AS_OF_DATE,
            KNOWLEDGE_CUTOFF,
            false,
            false,
            false,
            true,
            "idempotency-check",
            false
        );

        UniverseBatchRunReportDto report = batchOrchestratorService.executeBatchRun(batchReq);
        assertNotNull(report);
        assertEquals(15, report.totalSelected());
        assertEquals(0, report.totalCalculated(), "Second execution must calculate 0 options");
        assertEquals(15, report.totalSkipped(), "All 15 options must be skipped idempotently");
        assertEquals(0, report.totalFailed());
    }

    @Test
    @Order(4)
    @DisplayName("Verify Screener Reconciliation: all 5 categories have N = 15 and PRODUCTION_CALIBRATED across all 8 metrics")
    void testScreenerFullReconciliation() {
        UniverseScreenerDto.ScreenerResponse response = universeScreenerService.getScreenerData(null, null, AS_OF_DATE);
        assertNotNull(response);

        System.out.println("=== FULL SCREENER RECONCILIATION SUMMARY ===");
        System.out.println("Total Reference Options: " + response.totalReferenceOptions());
        System.out.println("Total Calculated Options: " + response.calculatedOptionsCount());

        List<String> canonical = List.of("RET-02", "RET-03", "RSK-01", "RSK-03", "MKT-01", "MKT-02", "RAT-04", "REL-01");

        for (EquityUniverseCategory cat : EquityUniverseCategory.values()) {
            List<UniverseScreenerDto.ScreenerItem> catItems = response.items().stream()
                .filter(i -> i.category() == cat && i.latestCalculationRunId() != null)
                .toList();

            System.out.println("\nCategory: " + cat.name() + " (Calculated Peers = " + catItems.size() + ")");
            assertEquals(15, catItems.size(), "Category " + cat.name() + " must have exactly 15 calculated options");

            for (String metricCode : canonical) {
                long validCount = catItems.stream()
                    .map(i -> i.metrics().get(metricCode))
                    .filter(m -> m != null && m.numericValue() != null)
                    .count();

                System.out.println("  " + metricCode + ": N = " + validCount);
                assertEquals(15, validCount, "Category " + cat.name() + " must have N = 15 for " + metricCode);

                // Verify every option has confidence status PRODUCTION_CALIBRATED and valid category percentile
                for (UniverseScreenerDto.ScreenerItem item : catItems) {
                    UniverseScreenerDto.MetricValueDto mv = item.metrics().get(metricCode);
                    assertNotNull(mv, "Metric " + metricCode + " must be present on option " + item.schemeOptionId());
                    assertNotNull(mv.numericValue(), "Metric " + metricCode + " numericValue must be non-null");
                    assertEquals(15, mv.peerDenominator(), "Peer denominator must be exactly 15");
                    assertEquals("PRODUCTION_CALIBRATED", mv.peerCohortStatus(), "Peer cohort status must be PRODUCTION_CALIBRATED");
                    assertNotNull(mv.percentileRank(), "Category percentile rank must be present");
                    assertTrue(mv.percentileRank() >= 0.0 && mv.percentileRank() <= 100.0,
                        "Category percentile rank must be between 0 and 100");
                }
            }
        }
    }
}
