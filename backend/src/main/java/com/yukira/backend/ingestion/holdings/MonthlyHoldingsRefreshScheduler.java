package com.yukira.backend.ingestion.holdings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled entry point for monthly mutual fund holdings disclosures refresh.
 *
 * Execution Controls:
 * 1. Configurable cadence via Spring property 'yukira.holdings.refresh.cron' (default: 2:00 AM on 1st of month).
 * 2. On/off toggle via 'yukira.holdings.refresh.enabled' (default: false in test/dev, enabled via env var in prod).
 * 3. Invokes MonthlyHoldingsRefreshService with TriggerType.SCHEDULED.
 */
@Component
public class MonthlyHoldingsRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthlyHoldingsRefreshScheduler.class);

    private final MonthlyHoldingsRefreshService refreshService;
    private final boolean refreshEnabled;
    private final String cronExpression;

    public MonthlyHoldingsRefreshScheduler(
        MonthlyHoldingsRefreshService refreshService,
        boolean refreshEnabled
    ) {
        this(refreshService, refreshEnabled, "0 0 2 12 * ?");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MonthlyHoldingsRefreshScheduler(
        MonthlyHoldingsRefreshService refreshService,
        @Value("${yukira.holdings.refresh.enabled:false}") boolean refreshEnabled,
        @Value("${yukira.holdings.refresh.cron:0 0 2 12 * ?}") String cronExpression
    ) {
        this.refreshService = refreshService;
        this.refreshEnabled = refreshEnabled;
        this.cronExpression = cronExpression;
    }

    /**
     * Executes scheduled monthly holdings refresh run.
     * Default cron: 2:00 AM on the 12th of each month (safely 48h after the SEBI 10th-of-month disclosure window).
     */
    @Scheduled(cron = "${yukira.holdings.refresh.cron:0 0 2 12 * ?}")
    public void runScheduledRefresh() {
        if (!refreshEnabled) {
            log.debug("Scheduled monthly holdings refresh is disabled (yukira.holdings.refresh.enabled=false).");
            return;
        }

        log.info("Triggering scheduled monthly holdings refresh job...");
        try {
            MonthlyHoldingsRefreshRun run = refreshService.executeMonthlyHoldingsRefresh(TriggerType.SCHEDULED);
            log.info("Scheduled monthly holdings refresh run completed: runId={}, status={}", run.runId(), run.overallStatus());
        } catch (Exception e) {
            log.error("Scheduled monthly holdings refresh run failed with uncaught exception: {}", e.getMessage(), e);
        }
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    public String getCronExpression() {
        return cronExpression;
    }
}
