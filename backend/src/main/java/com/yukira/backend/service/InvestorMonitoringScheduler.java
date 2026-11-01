package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.repository.InvestorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled executor for Continuous Fund & Portfolio Monitoring in Project YUKIRA.
 *
 * Operational Governance:
 * 1. Configurable execution cadence (default: 4:00 AM IST daily via 'yukira.monitoring.evaluation.cron').
 * 2. On/off configuration via 'yukira.monitoring.evaluation.enabled'.
 * 3. Overlap protection via AtomicBoolean concurrency guard.
 * 4. Strict investor isolation: Each investor's monitoring universe is evaluated independently.
 * 5. Resilient error isolation: An evaluation error for one investor does not abort remaining evaluations.
 * 6. Records explicit execution telemetry in the investor_monitoring_execution ledger.
 */
@Component
public class InvestorMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(InvestorMonitoringScheduler.class);

    private final InvestorRepository investorRepository;
    private final InvestorMonitoringService monitoringService;
    private final boolean isEnabled;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile ZonedDateTime lastScheduledRunTime;
    private volatile String lastScheduledRunStatus = "NOT_RUN";

    public InvestorMonitoringScheduler(
        InvestorRepository investorRepository,
        InvestorMonitoringService monitoringService,
        @Value("${yukira.monitoring.evaluation.enabled:true}") boolean isEnabled
    ) {
        this.investorRepository = investorRepository;
        this.monitoringService = monitoringService;
        this.isEnabled = isEnabled;
    }

    /**
     * Executes scheduled daily monitoring sweep across all registered investors.
     * Evaluates watched funds, portfolio positions, data quality, and research thesis conditions.
     */
    @Scheduled(cron = "${yukira.monitoring.evaluation.cron:0 0 4 * * ?}", zone = "${yukira.monitoring.evaluation.zone:Asia/Kolkata}")
    public void runScheduledMonitoringEvaluations() {
        if (!isEnabled) {
            log.debug("Continuous monitoring scheduled evaluation is disabled (yukira.monitoring.evaluation.enabled=false).");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Scheduled continuous monitoring sweep skipped: another monitoring job is currently active.");
            return;
        }

        try {
            lastScheduledRunTime = ZonedDateTime.now();
            log.info("Starting scheduled continuous monitoring sweep for all active investors at {}...", lastScheduledRunTime);

            List<Investor> investors = investorRepository.findAll();
            int totalInvestors = investors.size();
            int succeeded = 0;
            int failed = 0;

            for (Investor investor : investors) {
                try {
                    monitoringService.executeMonitoringSweep(investor, "SCHEDULED", null);
                    succeeded++;
                } catch (Exception e) {
                    failed++;
                    log.error("Failed scheduled monitoring evaluation for investor ID {}: {}", investor.getId(), e.getMessage());
                }
            }

            lastScheduledRunStatus = (failed == 0) ? "COMPLETED" : (succeeded > 0 ? "PARTIALLY_COMPLETED" : "FAILED");
            log.info("Completed scheduled continuous monitoring sweep: {} total investors, {} succeeded, {} failed. Status: {}",
                totalInvestors, succeeded, failed, lastScheduledRunStatus);

        } finally {
            isRunning.set(false);
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public ZonedDateTime getLastScheduledRunTime() {
        return lastScheduledRunTime;
    }

    public String getLastScheduledRunStatus() {
        return lastScheduledRunStatus;
    }
}

