package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Entity modeling immutable execution history records for background and operational jobs (Tasks 362, 363, 364).
 * Preserves execution attempts, status, duration, failure categorization, and dependency readiness snapshots.
 */
@Entity
@Table(name = "background_job_execution_record")
public class BackgroundJobExecutionRecord implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false, unique = true, length = 100)
    private String executionId;

    @Column(name = "job_identifier", nullable = false, length = 100)
    private String jobIdentifier;

    @Column(name = "scheduled_time")
    private OffsetDateTime scheduledTime;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "completion_time")
    private OffsetDateTime completionTime;

    @Column(name = "execution_status", nullable = false, length = 50)
    private String executionStatus; // RUNNING, SUCCEEDED, FAILED, TIMED_OUT, SKIPPED_LOCKED

    @Column(name = "trigger_type", nullable = false, length = 50)
    private String triggerType; // SCHEDULED_CRON, ON_DEMAND, EVENT_TRIGGERED

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 1;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "input_context_ref", length = 255)
    private String inputContextRef;

    @Column(name = "output_summary_ref", columnDefinition = "TEXT")
    private String outputSummaryRef;

    @Column(name = "failure_classification", length = 50)
    private String failureClassification = "NONE"; // TRANSIENT_NETWORK, PROVIDER_RATE_LIMIT, AUTHENTICATION_FAILURE, SCHEMA_MISMATCH, DATABASE_UNAVAILABLE, TIMEOUT, LOGICAL_DATA_ERROR, NONE

    @Column(name = "failure_details", columnDefinition = "TEXT")
    private String failureDetails;

    @Column(name = "dependency_readiness_snapshot", columnDefinition = "TEXT")
    private String dependencyReadinessSnapshot;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public BackgroundJobExecutionRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getJobIdentifier() { return jobIdentifier; }
    public void setJobIdentifier(String jobIdentifier) { this.jobIdentifier = jobIdentifier; }

    public OffsetDateTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(OffsetDateTime scheduledTime) { this.scheduledTime = scheduledTime; }

    public OffsetDateTime getStartTime() { return startTime; }
    public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }

    public OffsetDateTime getCompletionTime() { return completionTime; }
    public void setCompletionTime(OffsetDateTime completionTime) { this.completionTime = completionTime; }

    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getInputContextRef() { return inputContextRef; }
    public void setInputContextRef(String inputContextRef) { this.inputContextRef = inputContextRef; }

    public String getOutputSummaryRef() { return outputSummaryRef; }
    public void setOutputSummaryRef(String outputSummaryRef) { this.outputSummaryRef = outputSummaryRef; }

    public String getFailureClassification() { return failureClassification; }
    public void setFailureClassification(String failureClassification) { this.failureClassification = failureClassification; }

    public String getFailureDetails() { return failureDetails; }
    public void setFailureDetails(String failureDetails) { this.failureDetails = failureDetails; }

    public String getDependencyReadinessSnapshot() { return dependencyReadinessSnapshot; }
    public void setDependencyReadinessSnapshot(String dependencyReadinessSnapshot) { this.dependencyReadinessSnapshot = dependencyReadinessSnapshot; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

