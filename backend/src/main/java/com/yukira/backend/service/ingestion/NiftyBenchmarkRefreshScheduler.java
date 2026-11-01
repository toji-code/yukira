package com.yukira.backend.service.ingestion;

import com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService;
import com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService.BenchmarkIngestionAuditResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled entry point for daily benchmark Total Return Index (TRI) refresh from official NSE Indices sources (Phase A2).
 *
 * Execution Controls:
 * 1. Configurable cadence via Spring property 'yukira.benchmark.refresh.cron' (default: 11:30 PM IST daily).
 * 2. On/off toggle via 'yukira.benchmark.refresh.enabled' (default: false in test/dev, enabled in prod).
 * 3. Concurrency overlap protection via AtomicBoolean.
 * 4. Safely refreshes all five reference category benchmarks with failure isolation.
 */
@Component
public class NiftyBenchmarkRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(NiftyBenchmarkRefreshScheduler.class);

    private final NiftyBenchmarkIngestionService benchmarkIngestionService;
    private final boolean refreshEnabled;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile OffsetDateTime lastRunTime;
    private volatile String lastRunStatus = "NOT_RUN";
    private volatile int lastRunBenchmarksUpdated = 0;

    public NiftyBenchmarkRefreshScheduler(
        NiftyBenchmarkIngestionService benchmarkIngestionService,
        @Value("${yukira.benchmark.refresh.enabled:false}") boolean refreshEnabled
    ) {
        this.benchmarkIngestionService = benchmarkIngestionService;
        this.refreshEnabled = refreshEnabled;
    }

    /**
     * Executes scheduled daily benchmark TRI refresh run.
     */
    @Scheduled(cron = "${yukira.benchmark.refresh.cron:0 30 23 * * ?}", zone = "Asia/Kolkata")
    public void runScheduledBenchmarkRefresh() {
        if (!refreshEnabled) {
            log.debug("Scheduled benchmark TRI refresh is disabled (yukira.benchmark.refresh.enabled=false).");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Scheduled benchmark TRI refresh skipped: another benchmark refresh job is already active.");
            return;
        }

        try {
            log.info("Triggering scheduled daily category benchmark TRI refresh job...");
            lastRunTime = OffsetDateTime.now();
            List<BenchmarkIngestionAuditResult> results = benchmarkIngestionService.refreshAllCategoryBenchmarks(LocalDate.now());
            int inserted = results.stream().mapToInt(BenchmarkIngestionAuditResult::rowsInserted).sum();
            long successCount = results.stream().filter(r -> "SUCCESS".equalsIgnoreCase(r.status()) || "UP_TO_DATE".equalsIgnoreCase(r.status())).count();

            lastRunBenchmarksUpdated = inserted;
            lastRunStatus = (successCount == results.size()) ? "SUCCESS" : "PARTIAL";

            log.info("Scheduled benchmark TRI refresh completed: status={}, benchmarksProcessed={}, rowsInserted={}",
                lastRunStatus, results.size(), inserted);
        } catch (Exception e) {
            lastRunStatus = "FAILED: " + e.getMessage();
            log.error("Scheduled benchmark TRI refresh run failed with uncaught exception: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    public OffsetDateTime getLastRunTime() {
        return lastRunTime;
    }

    public String getLastRunStatus() {
        return lastRunStatus;
    }

    public int getLastRunBenchmarksUpdated() {
        return lastRunBenchmarksUpdated;
    }
}
