package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Entity modeling centralized operational background job dependencies and
 * external provider configuration readiness states (Tasks 357, 358, 360-365).
 */
@Entity
@Table(name = "operational_job_dependency")
public class OperationalJobDependency implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_identifier", nullable = false, unique = true, length = 100)
    private String jobIdentifier;

    @Column(name = "job_name", nullable = false, length = 255)
    private String jobName;

    @Column(name = "job_purpose", nullable = false, columnDefinition = "TEXT")
    private String jobPurpose;

    @Column(name = "execution_trigger", nullable = false, length = 50)
    private String executionTrigger; // SCHEDULED_CRON, ON_DEMAND, EVENT_TRIGGERED

    @Column(name = "schedule_cron", length = 100)
    private String scheduleCron;

    @Column(name = "required_configuration_keys", columnDefinition = "TEXT")
    private String requiredConfigurationKeys;

    @Column(name = "external_provider_ref", length = 100)
    private String externalProviderRef;

    @Column(name = "provider_config_status", nullable = false, length = 50)
    private String providerConfigStatus; // CONFIGURED, MISSING_CREDENTIALS, MISSING_ENDPOINT, DISABLED, UNSUPPORTED

    @Column(name = "last_status", length = 50)
    private String lastStatus; // SUCCESS, FAILED, CONFIGURATION_REQUIRED, IDLE

    @Column(name = "last_executed_at")
    private OffsetDateTime lastExecutedAt;

    @Column(name = "last_failure_reason", columnDefinition = "TEXT")
    private String lastFailureReason;

    @Column(name = "is_operational", nullable = false)
    private Boolean isOperational = false;

    // Extended operational & scheduler fields (Tasks 361, 364, 365)
    @Column(name = "job_definition_status", length = 50)
    private String jobDefinitionStatus = "ACTIVE"; // ACTIVE, DEPRECATED, SUSPENDED

    @Column(name = "scheduler_registration_status", length = 50)
    private String schedulerRegistrationStatus = "REGISTERED"; // REGISTERED, UNREGISTERED_PENDING_CREDENTIALS, ON_DEMAND_ONLY

    @Column(name = "execution_enabled")
    private Boolean executionEnabled = true;

    @Column(name = "configured_timezone", length = 50)
    private String configuredTimezone = "Asia/Kolkata";

    @Column(name = "misfire_policy", length = 50)
    private String misfirePolicy = "RUN_ONCE_IMMEDIATELY"; // RUN_ONCE_IMMEDIATELY, SKIP, RESCHEDULE_NEXT

    @Column(name = "max_execution_duration_sec")
    private Integer maxExecutionDurationSec = 3600;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "is_locked")
    private Boolean isLocked = false;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "lock_acquired_at")
    private OffsetDateTime lockAcquiredAt;

    @Column(name = "last_successful_interaction")
    private OffsetDateTime lastSuccessfulInteraction;

    @Column(name = "last_failed_interaction")
    private OffsetDateTime lastFailedInteraction;

    @Column(name = "credential_presence_confirmed")
    private Boolean credentialPresenceConfirmed = false;

    @Column(name = "next_operator_action", columnDefinition = "TEXT")
    private String nextOperatorAction;


    @Column(name = "fencing_token")
    private Long fencingToken = 0L;

    @Column(name = "last_heartbeat_at")
    private OffsetDateTime lastHeartbeatAt;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    public OperationalJobDependency() {}

    public OperationalJobDependency(
        String jobIdentifier,
        String jobName,
        String jobPurpose,
        String executionTrigger,
        String scheduleCron,
        String requiredConfigurationKeys,
        String externalProviderRef,
        String providerConfigStatus,
        String lastStatus,
        Boolean isOperational
    ) {
        this.jobIdentifier = jobIdentifier;
        this.jobName = jobName;
        this.jobPurpose = jobPurpose;
        this.executionTrigger = executionTrigger;
        this.scheduleCron = scheduleCron;
        this.requiredConfigurationKeys = requiredConfigurationKeys;
        this.externalProviderRef = externalProviderRef;
        this.providerConfigStatus = providerConfigStatus;
        this.lastStatus = lastStatus;
        this.isOperational = isOperational;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getJobIdentifier() { return jobIdentifier; }
    public void setJobIdentifier(String jobIdentifier) { this.jobIdentifier = jobIdentifier; }

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    public String getJobPurpose() { return jobPurpose; }
    public void setJobPurpose(String jobPurpose) { this.jobPurpose = jobPurpose; }

    public String getExecutionTrigger() { return executionTrigger; }
    public void setExecutionTrigger(String executionTrigger) { this.executionTrigger = executionTrigger; }

    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }

    public String getRequiredConfigurationKeys() { return requiredConfigurationKeys; }
    public void setRequiredConfigurationKeys(String requiredConfigurationKeys) { this.requiredConfigurationKeys = requiredConfigurationKeys; }

    public String getExternalProviderRef() { return externalProviderRef; }
    public void setExternalProviderRef(String externalProviderRef) { this.externalProviderRef = externalProviderRef; }

    public String getProviderConfigStatus() { return providerConfigStatus; }
    public void setProviderConfigStatus(String providerConfigStatus) { this.providerConfigStatus = providerConfigStatus; }

    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }

    public OffsetDateTime getLastExecutedAt() { return lastExecutedAt; }
    public void setLastExecutedAt(OffsetDateTime lastExecutedAt) { this.lastExecutedAt = lastExecutedAt; }

    public String getLastFailureReason() { return lastFailureReason; }
    public void setLastFailureReason(String lastFailureReason) { this.lastFailureReason = lastFailureReason; }

    public Boolean getIsOperational() { return isOperational; }
    public void setIsOperational(Boolean isOperational) { this.isOperational = isOperational; }

    public String getJobDefinitionStatus() { return jobDefinitionStatus; }
    public void setJobDefinitionStatus(String jobDefinitionStatus) { this.jobDefinitionStatus = jobDefinitionStatus; }

    public String getSchedulerRegistrationStatus() { return schedulerRegistrationStatus; }
    public void setSchedulerRegistrationStatus(String schedulerRegistrationStatus) { this.schedulerRegistrationStatus = schedulerRegistrationStatus; }

    public Boolean getExecutionEnabled() { return executionEnabled; }
    public void setExecutionEnabled(Boolean executionEnabled) { this.executionEnabled = executionEnabled; }

    public String getConfiguredTimezone() { return configuredTimezone; }
    public void setConfiguredTimezone(String configuredTimezone) { this.configuredTimezone = configuredTimezone; }

    public String getMisfirePolicy() { return misfirePolicy; }
    public void setMisfirePolicy(String misfirePolicy) { this.misfirePolicy = misfirePolicy; }

    public Integer getMaxExecutionDurationSec() { return maxExecutionDurationSec; }
    public void setMaxExecutionDurationSec(Integer maxExecutionDurationSec) { this.maxExecutionDurationSec = maxExecutionDurationSec; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Boolean getIsLocked() { return isLocked; }
    public void setIsLocked(Boolean isLocked) { this.isLocked = isLocked; }

    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }

    public OffsetDateTime getLockAcquiredAt() { return lockAcquiredAt; }
    public void setLockAcquiredAt(OffsetDateTime lockAcquiredAt) { this.lockAcquiredAt = lockAcquiredAt; }

    public OffsetDateTime getLastSuccessfulInteraction() { return lastSuccessfulInteraction; }
    public void setLastSuccessfulInteraction(OffsetDateTime lastSuccessfulInteraction) { this.lastSuccessfulInteraction = lastSuccessfulInteraction; }

    public OffsetDateTime getLastFailedInteraction() { return lastFailedInteraction; }
    public void setLastFailedInteraction(OffsetDateTime lastFailedInteraction) { this.lastFailedInteraction = lastFailedInteraction; }

    public Boolean getCredentialPresenceConfirmed() { return credentialPresenceConfirmed; }
    public void setCredentialPresenceConfirmed(Boolean credentialPresenceConfirmed) { this.credentialPresenceConfirmed = credentialPresenceConfirmed; }

    public String getNextOperatorAction() { return nextOperatorAction; }
    public void setNextOperatorAction(String nextOperatorAction) { this.nextOperatorAction = nextOperatorAction; }

    public Long getFencingToken() { return fencingToken; }
    public void setFencingToken(Long fencingToken) { this.fencingToken = fencingToken; }

    public OffsetDateTime getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(OffsetDateTime lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public OffsetDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(OffsetDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }

}
