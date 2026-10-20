package com.yukira.backend.service.ingestion;

import com.yukira.backend.dto.ingestion.AmfiNavRefreshResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled entry point for daily AMFI NAV refresh from official portal sources.
 *
 * Execution Controls:
 * 1. Configurable cadence via Spring property 'yukira.amfi.nav.refresh.cron' (default: 11:00 PM daily).
 * 2. On/off toggle via 'yukira.amfi.nav.refresh.enabled' (default: false in test/dev, enabled via env in prod).
 * 3. Invokes AmfiNavRefreshService.refreshLatestNav() safely.
 */
@Component
public class AmfiNavRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(AmfiNavRefreshScheduler.class);

    private final AmfiNavRefreshService navRefreshService;
    private final boolean refreshEnabled;

    public AmfiNavRefreshScheduler(
        AmfiNavRefreshService navRefreshService,
        @Value("${yukira.amfi.nav.refresh.enabled:false}") boolean refreshEnabled
    ) {
        this.navRefreshService = navRefreshService;
        this.refreshEnabled = refreshEnabled;
    }

    /**
     * Executes scheduled daily AMFI NAV refresh run.
     */
    @Scheduled(cron = "${yukira.amfi.nav.refresh.cron:0 0 23 * * ?}")
    public void runScheduledNavRefresh() {
        if (!refreshEnabled) {
            log.debug("Scheduled daily AMFI NAV refresh is disabled (yukira.amfi.nav.refresh.enabled=false).");
            return;
        }

        log.info("Triggering scheduled daily AMFI NAV refresh job...");
        try {
            AmfiNavRefreshResultDto result = navRefreshService.refreshLatestNav();
            log.info("Scheduled daily AMFI NAV refresh completed: status={}, observationsIngested={}, duplicatesSkipped={}",
                result.status(), result.observationsIngested(), result.duplicatesSkipped());
        } catch (Exception e) {
            log.error("Scheduled daily AMFI NAV refresh run failed with uncaught exception: {}", e.getMessage(), e);
        }
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }
}
