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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.yukira.backend.BackendApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Task 2: Universe-Wide Batch Calculation and ECDF Screener Verification")
public class UniverseBatchCalculationRunnerTest {

    private static final Logger log = LoggerFactory.getLogger(UniverseBatchCalculationRunnerTest.class);

    @Autowired
    private UniverseBatchOrchestratorService orchestratorService;

    @Autowired
    private UniverseScreenerService screenerService;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    private static final LocalDate START_DATE = LocalDate.of(2023, 10, 1);
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 10, 1);
    private static final OffsetDateTime KNOWLEDGE_CUTOFF =
        LocalDate.of(2026, 10, 1).atTime(23, 59, 59, 999000000).atOffset(ZoneOffset.ofHoursMinutes(5, 30));

    @Test
    @Order(1)
    @DisplayName("Step 1: Execute Universe-Wide Batch Calculation across 193 Reference Schemes")
    void step1_executeUniverseWideBatch() {
        System.out.println("\n=======================================================");
        System.out.println("TASK 2: UNIVERSE-WIDE BATCH CALCULATION START");
        System.out.println("=======================================================");

        UniverseBatchRunRequest request = new UniverseBatchRunRequest(
            250,
            null,
            START_DATE,
            AS_OF_DATE,
            AS_OF_DATE,
            KNOWLEDGE_CUTOFF,
            false,
            false,
            false,
            true,
            "task-2-batch-runner",
            false
        );

        UniverseBatchRunReportDto report = orchestratorService.executeBatchRun(request);

        assertNotNull(report);
        System.out.printf("Run ID: %s | Overall Status: %s | Duration: %d ms\n",
            report.runId(), report.overallStatus(), report.durationMs());
        System.out.printf("Total Selected: %d | Calculated: %d | Skipped: %d | Failed: %d\n",
            report.totalSelected(), report.totalCalculated(), report.totalSkipped(), report.totalFailed());

        assertEquals(193, report.totalSelected(), "All 193 reference schemes must be selected");
        assertEquals(0, report.totalFailed(), "Zero calculation orchestration failures");

        System.out.println("=======================================================");
        System.out.println("TASK 2: UNIVERSE-WIDE BATCH CALCULATION COMPLETE");
        System.out.println("=======================================================\n");
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Verify ECDF Percentiles and Core Metrics Across Screener")
    void step2_verifyScreenerAndEcdfPercentiles() {
        System.out.println("\n=======================================================");
        System.out.println("TASK 2: ECDF SCREENER VERIFICATION START");
        System.out.println("=======================================================");

        UniverseScreenerDto.ScreenerResponse screener = screenerService.getScreenerData(null, false, AS_OF_DATE);

        assertNotNull(screener);
        System.out.printf("As of Date: %s | Total Universe: %d | Total Calculated: %d\n",
            screener.asOfDate(), screener.totalReferenceOptions(), screener.calculatedOptionsCount());

        assertEquals(193, screener.totalReferenceOptions(), "Screener must contain all 193 reference options");
        assertTrue(screener.calculatedOptionsCount() >= 108, "At least 108 sufficient options must be calculated; found: " + screener.calculatedOptionsCount());

        // Check category summary breakdown
        for (var entry : screener.categorySummaries().entrySet()) {
            var cat = entry.getValue();
            System.out.printf("Category %s: Total=%d, Eligible=%d, Partial=%d, Calculated=%d, Benchmark=%s\n",
                cat.category(), cat.totalOptions(), cat.eligibleCount(), cat.partialCount(), cat.calculatedCount(), cat.candidateBenchmarkCode());
        }

        // Verify percentile population on calculated items
        long calculatedWithPercentiles = 0;
        long totalItemsWithRet03 = 0;

        for (var item : screener.items()) {
            var ret03 = item.metrics().get("RET-03");
            if (ret03 != null && "CALCULATED".equalsIgnoreCase(ret03.calculationStatus()) && ret03.numericValue() != null) {
                totalItemsWithRet03++;
                if (ret03.percentileRank() != null && ret03.formattedPercentile() != null) {
                    calculatedWithPercentiles++;
                }
            }
        }

        System.out.printf("Total items with valid RET-03: %d | With populated ECDF percentile: %d\n",
            totalItemsWithRet03, calculatedWithPercentiles);
        assertTrue(totalItemsWithRet03 >= 108, "Expected at least 108 items with valid RET-03");
        assertEquals(totalItemsWithRet03, calculatedWithPercentiles, "Every valid calculated metric must have an ECDF percentile rank");

        System.out.println("=======================================================");
        System.out.println("TASK 2: ECDF SCREENER VERIFICATION COMPLETE");
        System.out.println("=======================================================\n");
    }
}
