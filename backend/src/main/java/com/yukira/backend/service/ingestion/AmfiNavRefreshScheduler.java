package com.yukira.backend.service.ingestion;

import com.yukira.backend.dto.ingestion.AmfiNavRefreshResultDto;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled entry point for daily AMFI NAV refresh from official portal sources.
 *
 * Execution Controls:
 * 1. Configurable cadence via Spring property 'yukira.amfi.nav.refresh.cron' (default: 11:00 PM IST daily).
 * 2. On/off toggle via 'yukira.amfi.nav.refresh.enabled' (default: false in test/dev, enabled via env in prod).
 * 3. Concurrency overlap prevention via AtomicBoolean.
 * 4. Bounded retries with exponential backoff and jitter.
 * 5. Invokes AmfiNavRefreshService.refreshLatestNav() safely.
 */
@Component
public class AmfiNavRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(AmfiNavRefreshScheduler.class);

    private final AmfiNavRefreshService navRefreshService;
    private final boolean refreshEnabled;
    private final int maxRetries;
    private final long retryDelayMs;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile java.time.OffsetDateTime lastRunTime;
    private volatile String lastRunStatus = "NOT_RUN";
    private volatile int lastObservationsIngested = 0;
    private volatile int lastDuplicatesSkipped = 0;

    public AmfiNavRefreshScheduler(
        AmfiNavRefreshService navRefreshService,
        @Value("${yukira.amfi.nav.refresh.enabled:false}") boolean refreshEnabled,
        @Value("${yukira.amfi.nav.refresh.max-retries:3}") int maxRetries,
        @Value("${yukira.amfi.nav.refresh.retry-delay-ms:2000}") long retryDelayMs
    ) {
        this.navRefreshService = navRefreshService;
        this.refreshEnabled = refreshEnabled;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
    }

    /**
     * Executes scheduled daily AMFI NAV refresh run with configurable retries,
     * exponential backoff, and concurrency overlap protection.
     */
    @Scheduled(cron = "${yukira.amfi.nav.refresh.cron:0 0 23 * * ?}", zone = "Asia/Kolkata")
    public void runScheduledNavRefresh() {
        if (!refreshEnabled) {
            log.debug("Scheduled daily AMFI NAV refresh is disabled (yukira.amfi.nav.refresh.enabled=false).");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Scheduled daily AMFI NAV refresh skipped: another refresh job is already active.");
            return;
        }

        try {
            log.info("Triggering scheduled daily AMFI NAV refresh job (maxRetries={})...", maxRetries);
            lastRunTime = java.time.OffsetDateTime.now();

            Exception lastException = null;
            for (int attempt = 1; attempt <= Math.max(1, maxRetries); attempt++) {
                try {
                    AmfiNavRefreshResultDto result = navRefreshService.refreshLatestNav();
                    lastObservationsIngested = result.observationsIngested();
                    lastDuplicatesSkipped = result.duplicatesSkipped();
                    lastRunStatus = result.status();

                    log.info("Scheduled daily AMFI NAV refresh completed on attempt {}/{}: status={}, observationsIngested={}, duplicatesSkipped={}",
                        attempt, maxRetries, result.status(), result.observationsIngested(), result.duplicatesSkipped());
                    return;
                } catch (Exception e) {
                    lastException = e;
                    log.warn("Scheduled AMFI NAV refresh attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                    if (attempt < maxRetries) {
                        try {
                            long backoffMs = Math.min(30000L, (long) (retryDelayMs * Math.pow(2, attempt - 1)));
                            long jitter = (long) (Math.random() * 500);
                            Thread.sleep(backoffMs + jitter);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            lastRunStatus = "FAILED: " + (lastException != null ? lastException.getMessage() : "Unknown");
            log.error("Scheduled daily AMFI NAV refresh failed after {} attempts: {}", maxRetries, lastRunStatus);
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

    public java.time.OffsetDateTime getLastRunTime() {
        return lastRunTime;
    }

    public String getLastRunStatus() {
        return lastRunStatus;
    }

    public int getLastObservationsIngested() {
        return lastObservationsIngested;
    }

    public int getLastDuplicatesSkipped() {
        return lastDuplicatesSkipped;
    }
}
