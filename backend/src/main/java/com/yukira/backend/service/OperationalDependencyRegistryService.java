package com.yukira.backend.service;

import com.yukira.backend.domain.entity.BackgroundJobExecutionRecord;
import com.yukira.backend.domain.entity.OperationalJobDependency;
import com.yukira.backend.repository.BackgroundJobExecutionRecordRepository;
import com.yukira.backend.repository.OperationalJobDependencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing background job dependencies, schedules, execution records,
 * distributed locks with fencing tokens, retry/backoff, and external provider readiness (Tasks 357, 358, 360-367, 431-438).
 * Enforces operational reality: jobs without successful executions are never marked operational.
 */
@Service
@Transactional
public class OperationalDependencyRegistryService {

    private static final Logger log = LoggerFactory.getLogger(OperationalDependencyRegistryService.class);

    private final OperationalJobDependencyRepository jobDependencyRepository;
    private BackgroundJobExecutionRecordRepository executionRecordRepository;

    @Value("${yukira.ingestion.amfi.base-url:https://portal.amfiindia.com}")
    private String amfiBaseUrl;

    @Value("${spring.mail.host:localhost}")
    private String mailHost;

    @Value("${yukira.monitoring.dispatch-batch-size:50}")
    private int dispatchBatchSize;

    public enum OperationalCapabilityState {
        IMPLEMENTED,
        CONFIGURED,
        SCHEDULED,
        EXECUTION_ENABLED,
        EXECUTING,
        LAST_EXECUTION_SUCCEEDED,
        DEGRADED,
        BLOCKED,
        CONFIGURATION_PENDING,
        EXTERNAL_DEPENDENCY_PENDING,
        AWAITING_FIRST_EXECUTION
    }

    public enum FailureClassification {
        TRANSIENT_PROVIDER_NETWORK(true),
        PROVIDER_RATE_LIMIT(true),
        AUTHENTICATION_FAILURE(false),
        INVALID_SOURCE_ARTIFACT(false),
        PERMANENT_VALIDATION_FAILURE(false),
        UNSUPPORTED_COVERAGE(false),
        INTERNAL_PROCESSING_FAILURE(false);

        private final boolean isRetryable;

        FailureClassification(boolean isRetryable) {
            this.isRetryable = isRetryable;
        }

        public boolean isRetryable() {
            return isRetryable;
        }
    }

    public record OperationalJobSummaryDto(
        Long id,
        String jobIdentifier,
        String jobName,
        String executionTrigger, // SCHEDULED_CRON, ON_DEMAND, EVENT_DRIVEN
        String scheduleCron,
        String requiredConfigurationKeys,
        String externalProviderRef,
        String providerConfigStatus, // CONFIGURED, UNCONFIGURED, LOCAL_STUB
        boolean isOperational,
        String lastKnownHealthStatus,
        String capabilityState, // Canonical capability enum (Tasks 360, 437)
        String schedulerRegistrationStatus, // REGISTERED, UNREGISTERED_PENDING_CREDENTIALS, ON_DEMAND_ONLY
        boolean executionEnabled,
        boolean isLocked,
        String nextOperatorAction,
        Long fencingToken,
        OffsetDateTime lastHeartbeatAt,
        int retryCount,
        OffsetDateTime nextRetryAt,
        OffsetDateTime lastSuccessfulExecution,
        OffsetDateTime lastAttemptedExecution,
        String currentBlocker
    ) {}

    public record ExternalProviderReadinessDto(
        String providerIdentifier,
        String providerName,
        String readinessStatus, // OPERATIONAL, CONFIGURATION_PENDING, EXTERNAL_FEED_PENDING
        String primaryEndpointOrHost,
        List<String> requiredEnvKeys,
        String epistemicNote
    ) {}

    public record ExecutionRecordSummaryDto(
        String executionId,
        String jobIdentifier,
        OffsetDateTime startTime,
        OffsetDateTime completionTime,
        String executionStatus,
        String triggerType,
        int attemptCount,
        String idempotencyKey,
        String failureClassification,
        String failureDetails
    ) {}

    public record ConsolidatedOperationalReportDto(
        int totalJobsCount,
        int operationalJobsCount,
        int degradedOrBlockedCount,
        List<OperationalJobSummaryDto> jobs,
        List<ExternalProviderReadinessDto> providers,
        List<ExecutionRecordSummaryDto> recentExecutions,
        String reportingTimestamp,
        String systemHealthOverview
    ) {}

    /**
     * Alias record for REST controller compatibility.
     */
    public record OperationalStatusReportDto(
        int totalJobsCount,
        int operationalJobsCount,
        int degradedOrBlockedCount,
        List<OperationalJobSummaryDto> jobs,
        List<ExternalProviderReadinessDto> providers,
        List<ExecutionRecordSummaryDto> recentExecutions,
        String reportingTimestamp,
        String systemHealthOverview
    ) {}

    public record InvestorSafeDisclosureDto(
        String operationalIntegrityStatus, // OPERATIONAL_BASELINE, PARTIALLY_DEGRADED, MAINTENANCE
        boolean dailyNavSyncActive,
        boolean continuousMonitoringActive,
        boolean notificationsConfigured,
        boolean broadNseBenchmarkIngestionLive,
        String primaryBenchmarkStatus,
        String disclaimer
    ) {}

    public record LockLeaseResultDto(
        boolean acquired,
        Long fencingToken,
        String lockHolder,
        OffsetDateTime acquiredAt,
        String message
    ) {}

    public OperationalDependencyRegistryService(OperationalJobDependencyRepository jobDependencyRepository) {
        this.jobDependencyRepository = jobDependencyRepository;
    }

    @Autowired(required = false)
    public void setExecutionRecordRepository(BackgroundJobExecutionRecordRepository executionRecordRepository) {
        this.executionRecordRepository = executionRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<OperationalJobSummaryDto> listAllOperationalJobs() {
        return jobDependencyRepository.findAll().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<OperationalJobSummaryDto> getJobByIdentifier(String jobIdentifier) {
        return jobDependencyRepository.findByJobIdentifier(jobIdentifier)
            .map(this::toDto);
    }

    /**
     * Resolves the canonical capability state derived from actual execution records and configuration (Tasks 360, 437).
     */
    public OperationalCapabilityState evaluateCapabilityState(OperationalJobDependency job) {
        if (Boolean.TRUE.equals(job.getIsLocked())) {
            return OperationalCapabilityState.EXECUTING;
        }
        if ("EXTERNAL_FEED_PENDING".equals(job.getProviderConfigStatus()) || "UNSUPPORTED".equals(job.getProviderConfigStatus())) {
            return OperationalCapabilityState.EXTERNAL_DEPENDENCY_PENDING;
        }
        if ("MISSING_CREDENTIALS".equals(job.getProviderConfigStatus()) || "MISSING_ENDPOINT".equals(job.getProviderConfigStatus())) {
            return OperationalCapabilityState.CONFIGURATION_PENDING;
        }
        if ("FAILED".equals(job.getLastStatus())) {
            return OperationalCapabilityState.DEGRADED;
        }
        if (Boolean.FALSE.equals(job.getExecutionEnabled())) {
            return OperationalCapabilityState.BLOCKED;
        }

        // Check actual successful execution in database (Task 437)
        long successCount = 0;
        if (executionRecordRepository != null) {
            successCount = executionRecordRepository.countByJobIdentifierAndExecutionStatus(job.getJobIdentifier(), "SUCCESS");
        }
        if (successCount == 0 && !"SUCCESS".equals(job.getLastStatus())) {
            return OperationalCapabilityState.AWAITING_FIRST_EXECUTION;
        }

        if ("SUCCESS".equals(job.getLastStatus())) {
            return OperationalCapabilityState.LAST_EXECUTION_SUCCEEDED;
        }
        if (job.getScheduleCron() != null && !job.getScheduleCron().isBlank() && "REGISTERED".equals(job.getSchedulerRegistrationStatus())) {
            return OperationalCapabilityState.SCHEDULED;
        }
        if ("CONFIGURED".equals(job.getProviderConfigStatus())) {
            return OperationalCapabilityState.CONFIGURED;
        }
        return OperationalCapabilityState.IMPLEMENTED;
    }

    /**
     * Attempts to acquire an execution lock with fencing token and safe orphaned lock recovery (Tasks 433, 434).
     */
    public LockLeaseResultDto acquireExecutionLock(String jobIdentifier, String operatorOrNode, String idempotencyKey) {
        Optional<OperationalJobDependency> opt = jobDependencyRepository.findByJobIdentifier(jobIdentifier);
        if (opt.isEmpty()) {
            return new LockLeaseResultDto(false, null, null, null, "Job identifier not registered: " + jobIdentifier);
        }
        OperationalJobDependency job = opt.get();
        OffsetDateTime now = OffsetDateTime.now();

        // Safe Orphaned Lock Recovery (Task 434)
        if (Boolean.TRUE.equals(job.getIsLocked())) {
            boolean isHeartbeatStale = job.getLastHeartbeatAt() != null &&
                job.getLastHeartbeatAt().isBefore(now.minusSeconds(300)); // 5 min lease expiry
            boolean isLockExpired = job.getLockAcquiredAt() != null &&
                job.getLockAcquiredAt().isBefore(now.minusSeconds(1800)); // 30 min max execution

            if (isHeartbeatStale && isLockExpired) {
                log.warn("Reclaiming orphaned lock on job {} previously held by {} since {}",
                    jobIdentifier, job.getLockedBy(), job.getLockAcquiredAt());
                job.setIsLocked(false);
            } else {
                log.warn("Job {} is actively locked by {} (last heartbeat: {}), acquisition rejected",
                    jobIdentifier, job.getLockedBy(), job.getLastHeartbeatAt());
                return new LockLeaseResultDto(false, job.getFencingToken(), job.getLockedBy(), job.getLockAcquiredAt(), "Lock currently held by " + job.getLockedBy());
            }
        }

        // Monotonic fencing token increment (Task 433)
        long nextFencingToken = (job.getFencingToken() == null ? 0L : job.getFencingToken()) + 1L;
        job.setIsLocked(true);
        job.setLockedBy(operatorOrNode != null ? operatorOrNode : "LOCAL_NODE");
        job.setLockAcquiredAt(now);
        job.setLastHeartbeatAt(now);
        job.setFencingToken(nextFencingToken);
        job.setIdempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString());
        jobDependencyRepository.save(job);

        log.info("Acquired execution lock on job {} with fencingToken {} and idempotencyKey {}",
            jobIdentifier, nextFencingToken, job.getIdempotencyKey());

        return new LockLeaseResultDto(true, nextFencingToken, job.getLockedBy(), now, "Lock successfully acquired");
    }

    /**
     * Renews the execution lease via heartbeat (Task 434).
     */
    public boolean renewExecutionLease(String jobIdentifier, String operatorOrNode, Long fencingToken) {
        Optional<OperationalJobDependency> opt = jobDependencyRepository.findByJobIdentifier(jobIdentifier);
        if (opt.isEmpty()) return false;
        OperationalJobDependency job = opt.get();

        if (!Boolean.TRUE.equals(job.getIsLocked())) {
            log.warn("Cannot renew lease on unlocked job {}", jobIdentifier);
            return false;
        }
        if (!job.getFencingToken().equals(fencingToken) || !job.getLockedBy().equals(operatorOrNode)) {
            log.error("Fencing token mismatch or stolen lock on job {}: expected token {} / holder {}, got token {} / holder {}",
                jobIdentifier, job.getFencingToken(), job.getLockedBy(), fencingToken, operatorOrNode);
            return false;
        }

        job.setLastHeartbeatAt(OffsetDateTime.now());
        jobDependencyRepository.save(job);
        return true;
    }

    /**
     * Releases an execution lock (Task 433).
     */
    public void releaseExecutionLock(String jobIdentifier, Long fencingToken) {
        jobDependencyRepository.findByJobIdentifier(jobIdentifier).ifPresent(job -> {
            if (fencingToken != null && !fencingToken.equals(job.getFencingToken())) {
                log.warn("Lock release attempted with stale fencing token {} on job {} (current: {})",
                    fencingToken, jobIdentifier, job.getFencingToken());
                return;
            }
            job.setIsLocked(false);
            job.setLockedBy(null);
            job.setLockAcquiredAt(null);
            jobDependencyRepository.save(job);
            log.info("Released execution lock on job {}", jobIdentifier);
        });
    }

    /**
     * Records execution failure with classification and bounded exponential backoff (Task 435).
     */
    public String recordExecutionFailureWithRetry(
        String jobIdentifier,
        FailureClassification classification,
        String failureDetails,
        int currentAttemptCount
    ) {
        String execId = "EXEC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OffsetDateTime now = OffsetDateTime.now();

        boolean retryable = classification != null && classification.isRetryable() && currentAttemptCount < 3;
        OffsetDateTime nextRetry = null;
        int nextAttempt = currentAttemptCount + 1;

        if (retryable) {
            long backoffSeconds = (long) Math.pow(2, currentAttemptCount) * 60L; // 60s, 120s, 240s
            nextRetry = now.plusSeconds(backoffSeconds);
            log.warn("Scheduling retry {} for job {} at {} due to {}", nextAttempt, jobIdentifier, nextRetry, classification);
        }

        if (executionRecordRepository != null) {
            BackgroundJobExecutionRecord record = new BackgroundJobExecutionRecord();
            record.setExecutionId(execId);
            record.setJobIdentifier(jobIdentifier);
            record.setStartTime(now);
            record.setCompletionTime(now);
            record.setExecutionStatus(retryable ? "RETRY_PENDING" : "FAILED");
            record.setTriggerType("SCHEDULED_CRON");
            record.setAttemptCount(currentAttemptCount);
            record.setFailureClassification(classification != null ? classification.name() : "UNCLASSIFIED");
            record.setFailureDetails(failureDetails);
            record.setCreatedAt(now);
            executionRecordRepository.save(record);
        }

        OffsetDateTime finalNextRetry = nextRetry;
        jobDependencyRepository.findByJobIdentifier(jobIdentifier).ifPresent(job -> {
            job.setLastExecutedAt(now);
            job.setLastStatus(retryable ? "RETRY_PENDING" : "FAILED");
            job.setLastFailedInteraction(now);
            job.setLastFailureReason(failureDetails);
            job.setRetryCount(currentAttemptCount);
            job.setNextRetryAt(finalNextRetry);
            job.setIsLocked(false);
            jobDependencyRepository.save(job);
        });

        return execId;
    }

    /**
     * Records an execution outcome into the immutable ledger (Tasks 362, 363, 432).
     */
    public String recordExecutionOutcome(
        String jobIdentifier,
        String executionStatus,
        String triggerType,
        String failureClassification,
        String failureDetails,
        String outputSummary
    ) {
        String execId = "EXEC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OffsetDateTime now = OffsetDateTime.now();

        if (executionRecordRepository != null) {
            BackgroundJobExecutionRecord record = new BackgroundJobExecutionRecord();
            record.setExecutionId(execId);
            record.setJobIdentifier(jobIdentifier);
            record.setStartTime(now);
            record.setCompletionTime(now);
            record.setExecutionStatus(executionStatus);
            record.setTriggerType(triggerType != null ? triggerType : "SCHEDULED_CRON");
            record.setFailureClassification(failureClassification != null ? failureClassification : "NONE");
            record.setFailureDetails(failureDetails);
            record.setOutputSummaryRef(outputSummary);
            record.setCreatedAt(now);

            executionRecordRepository.save(record);
        }

        jobDependencyRepository.findByJobIdentifier(jobIdentifier).ifPresent(job -> {
            job.setLastExecutedAt(now);
            job.setLastStatus(executionStatus);
            if ("SUCCESS".equalsIgnoreCase(executionStatus)) {
                job.setLastSuccessfulInteraction(now);
                job.setLastFailureReason(null);
                job.setRetryCount(0);
                job.setNextRetryAt(null);
                job.setIsOperational(true);
            } else {
                job.setLastFailedInteraction(now);
                job.setLastFailureReason(failureDetails);
            }
            job.setIsLocked(false);
            jobDependencyRepository.save(job);
        });

        return execId;
    }

    @Transactional(readOnly = true)
    public List<ExternalProviderReadinessDto> evaluateProviderReadiness() {
        List<ExternalProviderReadinessDto> providers = new ArrayList<>();

        // AMFI Provider
        boolean amfiReady = amfiBaseUrl != null && !amfiBaseUrl.isBlank();
        providers.add(new ExternalProviderReadinessDto(
            "PROVIDER-AMFI",
            "Association of Mutual Funds in India (AMFI)",
            amfiReady ? "OPERATIONAL" : "CONFIGURATION_PENDING",
            amfiBaseUrl,
            List.of("yukira.ingestion.amfi.base-url"),
            "Authoritative Tier-1 public mutual fund NAV provider. Nightly publication cutoff: 23:00 IST."
        ));

        // NSE Index Feed
        providers.add(new ExternalProviderReadinessDto(
            "PROVIDER-NSE-INDICES",
            "National Stock Exchange (NSE Indices)",
            "EXTERNAL_FEED_PENDING",
            "https://www.niftyindices.com",
            List.of("yukira.benchmark.nse.api-key", "yukira.benchmark.nse.endpoint"),
            "Benchmark Total Return Index (TRI) series. Broad historical ingestion pending automated provider licensing connector."
        ));

        // Email Dispatch Provider (SMTP/SES)
        boolean emailConfigured = mailHost != null && !mailHost.equals("localhost");
        providers.add(new ExternalProviderReadinessDto(
            "PROVIDER-EMAIL",
            "SMTP / Amazon SES Delivery Gateway",
            emailConfigured ? "OPERATIONAL" : "CONFIGURATION_PENDING",
            mailHost,
            List.of("spring.mail.host", "spring.mail.username", "spring.mail.password"),
            "Investor notification outbox dispatch. Falls back to in-app notification ledger when mail server is unconfigured."
        ));

        // Webhook Provider
        providers.add(new ExternalProviderReadinessDto(
            "PROVIDER-WEBHOOK",
            "Outbound Webhook HTTP Dispatcher",
            "OPERATIONAL",
            "OUTBOUND_HTTP_CLIENT",
            List.of("yukira.notifications.webhook.timeout-ms"),
            "Direct HTTPS payload delivery to investor-registered endpoints with HMAC-SHA256 signatures."
        ));

        return providers;
    }

    /**
     * Produces consolidated operational status report (Tasks 366, 437).
     */
    @Transactional(readOnly = true)
    public OperationalStatusReportDto getOperationalStatusReport() {
        List<OperationalJobDependency> allJobs = jobDependencyRepository.findAll();
        List<OperationalJobSummaryDto> jobDtos = allJobs.stream().map(this::toDto).toList();
        List<ExternalProviderReadinessDto> providers = evaluateProviderReadiness();

        int total = allJobs.size();
        int operational = (int) jobDtos.stream().filter(OperationalJobSummaryDto::isOperational).count();
        int degradedOrBlocked = (int) jobDtos.stream().filter(j ->
            !j.isOperational() || "FAILED".equals(j.lastKnownHealthStatus())
        ).count();

        List<ExecutionRecordSummaryDto> recentExecutions = new ArrayList<>();
        if (executionRecordRepository != null) {
            recentExecutions = executionRecordRepository.findAll().stream()
                .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                .limit(20)
                .map(r -> new ExecutionRecordSummaryDto(
                    r.getExecutionId(),
                    r.getJobIdentifier(),
                    r.getStartTime(),
                    r.getCompletionTime(),
                    r.getExecutionStatus(),
                    r.getTriggerType(),
                    r.getAttemptCount() != null ? r.getAttemptCount() : 1,
                    r.getIdempotencyKey(),
                    r.getFailureClassification(),
                    r.getFailureDetails()
                ))
                .toList();
        }

        String overview = (degradedOrBlocked == 0)
            ? "ALL_SYSTEMS_OPERATIONAL"
            : (operational > 0 ? "PARTIALLY_DEGRADED_OPERATION" : "CONFIGURATION_REQUIRED");

        return new OperationalStatusReportDto(
            total,
            operational,
            degradedOrBlocked,
            jobDtos,
            providers,
            recentExecutions,
            OffsetDateTime.now().toString(),
            overview
        );
    }

    /**
     * Produces investor-safe operational disclosure (Task 367).
     */
    @Transactional(readOnly = true)
    public InvestorSafeDisclosureDto getInvestorSafeDisclosure() {
        boolean navSync = jobDependencyRepository.findByJobIdentifier("JOB-AMFI-NAV-SYNC")
            .map(j -> Boolean.TRUE.equals(j.getIsOperational())).orElse(false);
        boolean monitoring = jobDependencyRepository.findByJobIdentifier("JOB-MONITORING-EVAL")
            .map(j -> Boolean.TRUE.equals(j.getIsOperational())).orElse(false);
        boolean emailConfigured = mailHost != null && !mailHost.equals("localhost");

        return new InvestorSafeDisclosureDto(
            navSync && monitoring ? "OPERATIONAL_BASELINE" : "PARTIALLY_DEGRADED",
            navSync,
            monitoring,
            emailConfigured,
            false, // broad NSE benchmark feed is officially pending
            "NIFTY_500_TRI available for canonical pilot; broad universe benchmarking pending external feed activation.",
            "YUKIRA displays actual system operational readiness. No simulated data or synthetic observations are generated."
        );
    }

    private OperationalJobSummaryDto toDto(OperationalJobDependency job) {
        OperationalCapabilityState state = evaluateCapabilityState(job);

        // Task 437: A job is operational ONLY if it has executed successfully and is not blocked
        long successCount = 0;
        if (executionRecordRepository != null) {
            successCount = executionRecordRepository.countByJobIdentifierAndExecutionStatus(job.getJobIdentifier(), "SUCCESS");
        }
        boolean isReallyOperational = Boolean.TRUE.equals(job.getExecutionEnabled()) &&
            (successCount > 0 || "SUCCESS".equals(job.getLastStatus())) &&
            !"FAILED".equals(job.getLastStatus()) &&
            !"EXTERNAL_FEED_PENDING".equals(job.getProviderConfigStatus());

        String blocker = null;
        if (!Boolean.TRUE.equals(job.getExecutionEnabled())) {
            blocker = "Execution disabled via configuration.";
        } else if ("EXTERNAL_FEED_PENDING".equals(job.getProviderConfigStatus())) {
            blocker = "External provider connector pending licensing credentials.";
        } else if (state == OperationalCapabilityState.AWAITING_FIRST_EXECUTION) {
            blocker = "Awaiting first execution trigger.";
        } else if ("FAILED".equals(job.getLastStatus())) {
            blocker = job.getLastFailureReason();
        }

        return new OperationalJobSummaryDto(
            job.getId(),
            job.getJobIdentifier(),
            job.getJobName(),
            job.getExecutionTrigger(),
            job.getScheduleCron(),
            job.getRequiredConfigurationKeys(),
            job.getExternalProviderRef(),
            job.getProviderConfigStatus(),
            isReallyOperational,
            isReallyOperational ? "HEALTHY" : ("FAILED".equals(job.getLastStatus()) ? "FAILED" : "PENDING_CONFIGURATION"),
            state.name(),
            job.getSchedulerRegistrationStatus() != null ? job.getSchedulerRegistrationStatus() : "REGISTERED",
            Boolean.TRUE.equals(job.getExecutionEnabled()),
            Boolean.TRUE.equals(job.getIsLocked()),
            job.getNextOperatorAction() != null ? job.getNextOperatorAction() : "NONE",
            job.getFencingToken(),
            job.getLastHeartbeatAt(),
            job.getRetryCount() != null ? job.getRetryCount() : 0,
            job.getNextRetryAt(),
            job.getLastSuccessfulInteraction(),
            job.getLastExecutedAt(),
            blocker
        );
    }
}
