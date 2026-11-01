package com.yukira.backend.service.ingestion;

import com.yukira.backend.ingestion.fbil.FbilTBillIngestionService;
import com.yukira.backend.ingestion.fbil.FbilTBillIngestionService.FbilIngestionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Scheduled entry point for daily FBIL risk-free rate refresh from official FBIL sources (Phase A2).
 *
 * Execution Controls:
 * 1. Configurable cadence via Spring property 'yukira.fbil.refresh.cron' (default: 11:45 PM daily).
 * 2. On/off toggle via 'yukira.fbil.refresh.enabled' (default: false in test/dev, enabled in prod).
 * 3. Incrementally refreshes FBIL 91-day Treasury Bill rate observations safely.
 */
@Component
public class FbilRiskFreeRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(FbilRiskFreeRefreshScheduler.class);

    private final FbilTBillIngestionService fbilTBillIngestionService;
    private final boolean refreshEnabled;

    private volatile OffsetDateTime lastRunTime;
    private volatile String lastRunStatus = "NOT_RUN";
    private volatile int lastRunRowsInserted = 0;

    public FbilRiskFreeRefreshScheduler(
        FbilTBillIngestionService fbilTBillIngestionService,
        @Value("${yukira.fbil.refresh.enabled:false}") boolean refreshEnabled
    ) {
        this.fbilTBillIngestionService = fbilTBillIngestionService;
        this.refreshEnabled = refreshEnabled;
    }

    /**
     * Executes scheduled daily FBIL risk-free rate refresh run.
     */
    @Scheduled(cron = "${yukira.fbil.refresh.cron:0 45 23 * * ?}")
    public void runScheduledFbilRefresh() {
        if (!refreshEnabled) {
            log.debug("Scheduled daily FBIL rate refresh is disabled (yukira.fbil.refresh.enabled=false).");
            return;
        }

        log.info("Triggering scheduled daily FBIL risk-free rate refresh job...");
        lastRunTime = OffsetDateTime.now();
        try {
            FbilIngestionSummary summary = fbilTBillIngestionService.refreshLatestRates(LocalDate.now());
            lastRunRowsInserted = summary.insertedCount();
            lastRunStatus = summary.status() != null ? summary.status() : "SUCCESS";

            log.info("Scheduled FBIL rate refresh completed: status={}, inserted={}, skipped={}, revised={}",
                lastRunStatus, summary.insertedCount(), summary.skippedCount(), summary.revisedCount());
        } catch (Exception e) {
            lastRunStatus = "FAILED: " + e.getMessage();
            log.error("Scheduled FBIL rate refresh run failed with uncaught exception: {}", e.getMessage(), e);
        }
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

    public int getLastRunRowsInserted() {
        return lastRunRowsInserted;
    }
}
