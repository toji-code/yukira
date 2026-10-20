package com.yukira.backend.scoring;

import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.analysis.UniverseReadinessReportDto;
import com.yukira.backend.dto.analysis.UniverseReadinessReportDto.*;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.scoring.dto.AnalyticalScoreResponse;
import com.yukira.backend.scoring.dto.ScoreCalculationRequest;
import com.yukira.backend.scoring.service.AnalyticalScoringService;
import com.yukira.backend.service.BenchmarkMasterService;
import com.yukira.backend.service.BenchmarkResolutionService;
import com.yukira.backend.service.BenchmarkResolutionService.BenchmarkResolutionResult;
import com.yukira.backend.service.PilotBenchmarkMappingDataService;
import com.yukira.backend.service.UniverseAnalyticalReadinessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class UniverseAnalyticalReadinessTest {

    private static final Logger log = LoggerFactory.getLogger(UniverseAnalyticalReadinessTest.class);

    @Autowired
    private UniverseAnalyticalReadinessService readinessService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private AnalyticalScoringService scoringService;

    @Autowired
    private BenchmarkMasterService benchmarkMasterService;

    @Autowired
    private BenchmarkResolutionService benchmarkResolutionService;

    @Autowired
    private PilotBenchmarkMappingDataService pilotMappingDataService;

    @Test
    @DisplayName("Canonical Active-Universe Readiness Audit & Funnel Verification")
    void testUniverseAnalyticalReadinessAudit() {
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);

        // First seed benchmark master & pilot mappings
        benchmarkMasterService.ensureCanonicalBenchmarkMaster();
        var mappingSummary = pilotMappingDataService.seedAuthoritativePilotMappings();
        log.info("Pilot mapping ingestion summary: total={}, inserted={}, skipped={}, conflicts={}",
            mappingSummary.totalProcessed(), mappingSummary.insertedCount(), mappingSummary.skippedCount(), mappingSummary.conflictCount());

        UniverseReadinessReportDto report = readinessService.generateReadinessReport(asOfDate);

        assertNotNull(report);
        assertTrue(report.totalCatalog() > 0, "Total catalog must be > 0");
        assertTrue(report.activeUniverse() > 0, "Active universe must be > 0");
        assertEquals(report.activeUniverse(), report.activeWithNav() + report.activeZeroNav(),
            "activeWithNav + activeZeroNav must equal activeUniverse");
        assertTrue(report.explicitBenchmarkMappedCount() > 0, "Explicit benchmark mapped count should be > 0 after seeding");

        System.out.println("\n==========================================================================");
        System.out.println("YUKIRA CANONICAL UNIVERSE ANALYTICAL-READINESS AUDIT REPORT");
        System.out.println("==========================================================================");
        System.out.println(String.format("As Of Date: %s", report.asOfDate()));
        System.out.println(String.format("Total Catalog Options: %,d", report.totalCatalog()));
        System.out.println(String.format("Active Universe Options: %,d", report.activeUniverse()));
        System.out.println(String.format("Active Options with NAV History: %,d (%.2f%%)",
            report.activeWithNav(), (double) report.activeWithNav() / report.activeUniverse() * 100.0));
        System.out.println(String.format("Active Zero-NAV Options: %,d (%.2f%%)",
            report.activeZeroNav(), (double) report.activeZeroNav() / report.activeUniverse() * 100.0));
        System.out.println(String.format("Active 1Y Ready (>=250 NAV days): %,d (%.2f%%)",
            report.active1yReady(), (double) report.active1yReady() / report.activeUniverse() * 100.0));
        System.out.println(String.format("Active 3Y Ready (>=700 NAV days): %,d (%.2f%%)",
            report.active3yReady(), (double) report.active3yReady() / report.activeUniverse() * 100.0));
        System.out.println(String.format("Active 5Y Ready (>=1200 NAV days): %,d (%.2f%%)",
            report.active5yReady(), (double) report.active5yReady() / report.activeUniverse() * 100.0));

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("BENCHMARK MAPPING COVERAGE BREAKDOWN");
        System.out.println("--------------------------------------------------------------------------");
        System.out.println(String.format("Explicitly Mapped Options: %,d (%.2f%%)",
            report.explicitBenchmarkMappedCount(), (double) report.explicitBenchmarkMappedCount() / report.activeUniverse() * 100.0));
        System.out.println(String.format("Default/Fallback Mapped Options: %,d (%.2f%%)",
            report.fallbackBenchmarkCount(), (double) report.fallbackBenchmarkCount() / report.activeUniverse() * 100.0));
        System.out.println(String.format("Unmapped Options: %,d (%.2f%%)",
            report.unmappedBenchmarkCount(), (double) report.unmappedBenchmarkCount() / report.activeUniverse() * 100.0));

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("QUANT READINESS FUNNEL");
        System.out.println("--------------------------------------------------------------------------");
        for (FunnelStepItem step : report.quantReadinessFunnel()) {
            System.out.println(String.format("%-28s | Count: %-5d | %6.2f%% | Excluded: %-5d | Reason: %s",
                step.stepName(), step.count(), step.percentageOfActiveUniverse(), step.excludedCount(), step.primaryExclusionReason()));
        }

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("MUTUALLY EXCLUSIVE BLOCKER CLASSIFICATION");
        System.out.println("--------------------------------------------------------------------------");
        long sumBlockers = 0;
        for (Map.Entry<String, BlockerStat> entry : report.blockerClassification().entrySet()) {
            BlockerStat stat = entry.getValue();
            sumBlockers += stat.count();
            System.out.println(String.format("%-32s | Count: %-5d | %6.2f%% Active | %6.2f%% 3Y | Examples: %s",
                stat.category(), stat.count(), stat.percentageOfActiveUniverse(), stat.percentageOfTargetCohort(), stat.representativeExamples()));
        }
        assertEquals(report.activeUniverse(), sumBlockers, "Sum of mutually exclusive blockers must equal activeUniverse");

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("BENCHMARK AUDIT");
        System.out.println("--------------------------------------------------------------------------");
        for (BenchmarkAuditItem bm : report.benchmarkAudits()) {
            System.out.println(String.format("Code: %-28s | Name: %-35s | Mapped: %-5d | 3Y Ready: %b",
                bm.code(), bm.name(), bm.mappedActiveSchemeCount(), bm.is3yReady()));
        }

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("RISK-FREE AUDIT");
        System.out.println("--------------------------------------------------------------------------");
        RiskFreeAuditItem rf = report.riskFreeAudit();
        System.out.println(String.format("Code: %-16s | Obs: %-6d | Min: %s | Max: %s | 3Y Ready: %b",
            rf.code(), rf.observationCount(), rf.minDate(), rf.maxDate(), rf.is3yReady()));

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("PERFORMANCE METRICS");
        System.out.println("--------------------------------------------------------------------------");
        AuditPerformanceMetrics perf = report.performanceMetrics();
        System.out.println(String.format("Options Scanned: %,d | DB Query Count: %d | Duration: %d ms | Memory: %.2f MB",
            perf.optionsScanned(), perf.queryCount(), perf.durationMs(), (double) perf.peakMemoryBytes() / (1024 * 1024)));
        System.out.println("==========================================================================\n");
    }

    @Test
    @DisplayName("Official Benchmark Mapping Ingestion and PIT Resolution Verification")
    void testOfficialBenchmarkMappingIngestionAndResolution() {
        benchmarkMasterService.ensureCanonicalBenchmarkMaster();

        // 1. Ingest pilot benchmark mappings
        var summary = pilotMappingDataService.seedAuthoritativePilotMappings();
        assertNotNull(summary);
        assertTrue(summary.totalProcessed() > 0);

        // 2. Verify HDFC Flexi Cap (118955) resolves NIFTY_500_TRI explicitly
        SchemeOption hdfcFlexi = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        BenchmarkResolutionResult hdfcRes = benchmarkResolutionService.resolvePrimaryBenchmark(
            hdfcFlexi.getPlan().getScheme().getId(), LocalDate.of(2024, 1, 15)
        );
        assertTrue(hdfcRes.isExplicitMapping(), "HDFC Flexi Cap must resolve to explicit benchmark mapping");
        assertEquals("NIFTY_500_TRI", hdfcRes.benchmark().getCode());

        // 3. Idempotent rerun check
        var summaryRerun = pilotMappingDataService.seedAuthoritativePilotMappings();
        assertEquals(0, summaryRerun.insertedCount(), "Idempotent rerun must insert 0 new mappings");
        assertTrue(summaryRerun.skippedCount() > 0, "Idempotent rerun must skip existing mappings");

        log.info("Official Benchmark Mapping Ingestion & Resolution Verification PASS: Initial Ingested={}, Rerun Skipped={}",
            summary.insertedCount(), summaryRerun.skippedCount());
    }

    @Test
    @DisplayName("Controlled Pilot Cohort Validation Across Active Direct Growth Options")
    void testControlledPilotCohortScoring() {
        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.parse("2024-01-31T23:59:59+05:30");

        List<SchemeOption> allOptions = schemeOptionRepository.findAll();
        List<SchemeOption> eligibleCohort = allOptions.stream()
            .filter(o -> "ACTIVE".equalsIgnoreCase(o.getStatus()) || o.getStatus() == null)
            .filter(o -> "GROWTH".equalsIgnoreCase(o.getOptionType()))
            .filter(o -> "DIRECT".equalsIgnoreCase(o.getPlan() != null ? o.getPlan().getPlanType() : ""))
            .limit(30)
            .toList();

        assertFalse(eligibleCohort.isEmpty(), "Eligible cohort must not be empty");
        log.info("Executing controlled pilot score calculation for {} active direct growth options...", eligibleCohort.size());

        int scoredCount = 0;
        int unavailableCount = 0;

        for (SchemeOption opt : eligibleCohort) {
            ScoreCalculationRequest req = new ScoreCalculationRequest(
                opt.getId(),
                null,
                asOfDate,
                cutoff,
                null
            );

            AnalyticalScoreResponse resp = scoringService.calculateScore(req);
            assertNotNull(resp);
            assertEquals(opt.getId(), resp.schemeOptionId());

            if (resp.score() != null) {
                scoredCount++;
                assertTrue(resp.score().doubleValue() >= 0.0 && resp.score().doubleValue() <= 100.0);
            } else {
                unavailableCount++;
            }
        }

        log.info("Controlled Pilot Execution Complete: Total={}, Scored={}, Unavailable={}",
            eligibleCohort.size(), scoredCount, unavailableCount);

        assertEquals(eligibleCohort.size(), scoredCount + unavailableCount,
            "All cohort items must complete scoring evaluation without throwing uncaught exceptions");
    }
}
