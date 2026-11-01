package com.yukira.backend.ingestion.nse;

import com.yukira.backend.benchmark.BenchmarkDateAlignmentResultDto;
import com.yukira.backend.benchmark.BenchmarkDateAlignmentService;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import com.yukira.backend.universe.*;
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
@Transactional
@Commit
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("TSK-M1-07 Live Benchmark Historical Ingestion & Cohort Pilot Runner")
public class LiveBenchmarkIngestionRunner {

    private static final Logger log = LoggerFactory.getLogger(LiveBenchmarkIngestionRunner.class);

    @Autowired
    private NiftyBenchmarkIngestionService ingestionService;

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private BenchmarkObservationRepository benchmarkObservationRepository;

    @Autowired
    private SourceArtifactRepository sourceArtifactRepository;

    @Autowired
    private BenchmarkDateAlignmentService benchmarkDateAlignmentService;

    @Autowired
    private UniverseBatchOrchestratorService orchestratorService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private MetricResultRepository metricResultRepository;

    private static final LocalDate START_DATE = LocalDate.of(2023, 1, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 10, 1);
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 10, 1);
    private static final OffsetDateTime KNOWLEDGE_CUTOFF =
        LocalDate.of(2026, 10, 1).atTime(23, 59, 59).atOffset(ZoneOffset.ofHoursMinutes(5, 30));

    private static final String[] BENCHMARKS_TO_INGEST = new String[] {
        "NIFTY_100_TRI",
        "NIFTY_LARGEMIDCAP_250_TRI",
        "NIFTY_MIDCAP_150_TRI",
        "NIFTY_SMALLCAP_250_TRI"
    };

    @Test
    @Order(1)
    @DisplayName("Step 1: Live Ingestion of 4 Missing Category Benchmarks with SHA-256 Provenance & Audit")
    void step1_ingestFourCategoryBenchmarks() {
        System.out.println("\n=======================================================");
        System.out.println("TSK-M1-07: STEP 1 - LIVE BENCHMARK INGESTION START");
        System.out.println("=======================================================");

        for (String bmCode : BENCHMARKS_TO_INGEST) {
            System.out.printf("\n>>> Ingesting authentic series for %s [%s to %s]...\n", bmCode, START_DATE, END_DATE);

            var audit = ingestionService.ingestCategoryBenchmarkWithAudit(bmCode, START_DATE, END_DATE);

            System.out.printf("    Benchmark: %s (%s)\n", audit.benchmarkCode(), audit.benchmarkName());
            System.out.printf("    Provider: %s | Return Variant: %s\n", audit.provider(), audit.returnVariant());
            System.out.printf("    Status: %s\n", audit.status());
            System.out.printf("    Rows Received: %d | Accepted: %d | Rejected: %d\n",
                audit.rowsReceived(), audit.rowsAccepted(), audit.rowsRejected());
            System.out.printf("    Rows Inserted: %d | Revised: %d | Skipped (duplicates): %d\n",
                audit.rowsInserted(), audit.rowsRevised(), audit.duplicatesSkipped());
            System.out.printf("    Actual Returned Date Range: %s to %s\n", audit.actualMinDate(), audit.actualMaxDate());
            System.out.printf("    Source Artifact ID: %d | SHA-256: %s\n", audit.sourceArtifactId(), audit.payloadSha256());

            assertEquals("TRI", audit.returnVariant(), "Must strictly be Total Return Index (TRI)");
            assertTrue("SUCCESS".equalsIgnoreCase(audit.status()) || "PARTIAL".equalsIgnoreCase(audit.status()),
                "Ingestion must succeed for " + bmCode);
            assertTrue(audit.rowsAccepted() >= 700, "Must have parsed at least 700 observations for " + bmCode);
            assertTrue(audit.rowsInserted() >= 700 || audit.duplicatesSkipped() >= 700,
                "Must have inserted or skipped valid observations");
            assertNotNull(audit.sourceArtifactId());
            assertNotNull(audit.payloadSha256());

            // Verify in repository
            Benchmark bm = benchmarkRepository.findByCode(bmCode).orElseThrow();
            List<BenchmarkObservation> obsList = benchmarkObservationRepository.findByBenchmarkId(bm.getId());
            assertTrue(obsList.size() >= 700, "Repository must contain >= 700 observations for " + bmCode);
            System.out.printf("    [VERIFIED IN DB] Total Persisted Observations for %s: %d\n", bmCode, obsList.size());
        }

        // Idempotency verification
        System.out.println("\n>>> Verifying Idempotent Re-ingestion for NIFTY_100_TRI...");
        var idempAudit = ingestionService.ingestCategoryBenchmarkWithAudit("NIFTY_100_TRI", START_DATE, END_DATE);
        System.out.printf("    Re-ingestion Inserted: %d | Skipped: %d\n",
            idempAudit.rowsInserted(), idempAudit.duplicatesSkipped());
        assertEquals(0, idempAudit.rowsInserted(), "Re-ingestion must insert 0 rows (idempotency)");
        assertTrue(idempAudit.duplicatesSkipped() >= 700, "Re-ingestion must skip all existing duplicate observations");
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Verify Multi-Asset Date Alignment & Relative Metric Readiness Across 4 New Categories")
    void step2_verifyAlignmentAndReadiness() {
        System.out.println("\n=======================================================");
        System.out.println("TSK-M1-07: STEP 2 - CALENDAR DATE ALIGNMENT & READINESS");
        System.out.println("=======================================================");

        // Schemes from each category:
        // Large Cap: 119598 (SBI Large Cap Fund Direct Growth)
        // Large & Mid Cap: 118419 (Bandhan Large & Mid Cap Fund Direct Growth)
        // Mid Cap: 119716 (SBI MIDCAP FUND Direct Growth)
        // Small Cap: 147946 (BANDHAN Small Cap Fund Direct Growth)
        String[] amfiCodes = new String[] { "119598", "118419", "119716", "147946" };

        for (String amfiCode : amfiCodes) {
            SchemeOption option = schemeOptionRepository.findByAmfiCode(amfiCode).orElseThrow(
                () -> new IllegalStateException("Option not found for AMFI code " + amfiCode)
            );

            BenchmarkDateAlignmentResultDto alignment = benchmarkDateAlignmentService.evaluateAlignment(
                option, START_DATE, AS_OF_DATE, AS_OF_DATE, KNOWLEDGE_CUTOFF
            );

            System.out.printf("\n>>> Fund: %s (Option ID: %d, AMFI: %s)\n",
                alignment.schemeName(), alignment.schemeOptionId(), alignment.amfiCode());
            System.out.printf("    Category: %s | Resolved Benchmark: %s (%s)\n",
                alignment.equityUniverseCategory(), alignment.benchmarkCode(), alignment.benchmarkName());
            System.out.printf("    Return Variant: %s | TRI Verified: %s | Governance: %s\n",
                alignment.returnVariant(), alignment.isTri(), alignment.methodologyStatus());
            System.out.printf("    Fund Dates: %d | Benchmark Dates: %d | Aligned Trading Days: %d\n",
                alignment.fundDistinctValidDates(), alignment.benchmarkDistinctValidDates(), alignment.alignedTradingDays());
            System.out.printf("    Alignment Status: %s | Overlap Ratio: %s\n",
                alignment.alignmentStatus(), alignment.overlapRatio());

            // Check metric readiness
            var mkt01 = alignment.metricReadiness().get("MKT-01");
            var rsk02 = alignment.metricReadiness().get("RSK-02");
            var rel01 = alignment.metricReadiness().get("REL-01");

            System.out.printf("    MKT-01 (Beta 3Y): %s (isReady: %s)\n",
                mkt01 != null ? mkt01.readinessState() : "N/A", mkt01 != null && mkt01.isReady());
            System.out.printf("    RSK-02 (Tracking Error 3Y): %s (isReady: %s)\n",
                rsk02 != null ? rsk02.readinessState() : "N/A", rsk02 != null && rsk02.isReady());
            System.out.printf("    REL-01 (Excess Return 3Y): %s (isReady: %s)\n",
                rel01 != null ? rel01.readinessState() : "N/A", rel01 != null && rel01.isReady());

            assertTrue(alignment.alignedTradingDays() >= 700,
                "Aligned trading days must be >= 700 for " + alignment.schemeName());
            assertEquals("ALIGNED_3Y_SUFFICIENT", alignment.alignmentStatus());
            assertEquals("READY", mkt01.readinessState(), "MKT-01 must be READY");
            assertEquals("READY", rsk02.readinessState(), "RSK-02 must be READY");
            assertEquals("READY", rel01.readinessState(), "REL-01 must be READY");
        }
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: End-to-End Batch Calculation Pilot for Newly Covered Categories")
    void step3_executeCohortPilotCalculations() {
        System.out.println("\n=======================================================");
        System.out.println("TSK-M1-07: STEP 3 - END-TO-END COHORT PILOT CALCULATION");
        System.out.println("=======================================================");

        EquityUniverseCategory[] targetCategories = new EquityUniverseCategory[] {
            EquityUniverseCategory.LARGE_CAP,
            EquityUniverseCategory.LARGE_AND_MID_CAP,
            EquityUniverseCategory.MID_CAP,
            EquityUniverseCategory.SMALL_CAP
        };

        for (EquityUniverseCategory cat : targetCategories) {
            System.out.printf("\n>>> Running bounded batch calculation pilot for Category: %s...\n", cat);

            UniverseBatchRunRequest req = new UniverseBatchRunRequest(
                1, cat, START_DATE, AS_OF_DATE, AS_OF_DATE,
                KNOWLEDGE_CUTOFF, false, false, false, true, "tsk-m1-07-pilot"
            );

            UniverseBatchRunReportDto report = orchestratorService.executeBatchRun(req);

            assertNotNull(report);
            assertEquals(1, report.totalSelected(), "Should select 1 scheme for " + cat);
            assertEquals(1, report.totalCalculated(), "Should successfully calculate 1 scheme for " + cat);
            assertEquals(0, report.totalFailed(), "Should have 0 failures for " + cat);

            SchemeBatchExecutionDetailDto detail = report.schemeOutcomes().get(0);
            System.out.printf("    Scheme: %s (Option ID: %d, AMFI: %s)\n",
                detail.schemeName(), detail.schemeOptionId(), detail.amfiCode());
            System.out.printf("    Benchmark: %s | Governance: %s\n",
                detail.benchmarkCode(), detail.benchmarkGovernanceStatus());
            System.out.printf("    Calculation Outcome: %s | Calculation Run ID: %d\n",
                detail.calculationOutcome(), detail.calculationRunId());
            System.out.printf("    Metrics Calculated: %s\n", detail.metricsCalculated());

            assertNotNull(detail.calculationRunId());
            assertEquals("CALCULATED", detail.calculationOutcome());

            // Verify calculation_run in DB
            CalculationRun run = calculationRunRepository.findById(detail.calculationRunId()).orElseThrow();
            assertEquals("SUCCESS", run.getRunStatus());

            // Verify persisted MetricResults
            List<MetricResult> results = metricResultRepository.findByCalculationRunId(run.getId());
            assertFalse(results.isEmpty());
            System.out.printf("    Persisted Metric Results Count: %d\n", results.size());
            for (MetricResult mr : results) {
                System.out.printf("      - Metric %s: %s %s (Status: %s)\n",
                    mr.getMetricCode(), mr.getNumericValue(), mr.getUnits(), mr.getCalculationStatus());
            }
        }

        System.out.println("\n=======================================================");
        System.out.println("TSK-M1-07: COHORT PILOT CALCULATION COMPLETE - ALL SUCCESS");
        System.out.println("=======================================================\n");
    }
}
