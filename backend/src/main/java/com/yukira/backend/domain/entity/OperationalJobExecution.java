package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "operational_job_execution")
public class OperationalJobExecution implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 64)
    private String jobName; // AMFI_NAV_INGESTION, BENCHMARK_TRI_INGESTION, FBIL_RISK_FREE_INGESTION, AMC_HOLDINGS_INGESTION, CONTINUOUS_MONITORING_SWEEP, NOTIFICATION_OUTBOX_DELIVERY

    @Column(name = "execution_trigger", nullable = false, length = 32)
    private String executionTrigger = "SCHEDULED"; // SCHEDULED, MANUAL, EVENT_DRIVEN

    @Column(name = "execution_status", nullable = false, length = 32)
    private String executionStatus = "RUNNING"; // RUNNING, SUCCESS, PARTIAL_SUCCESS, FAILED

    @Column(name = "started_at", nullable = false)
    private ZonedDateTime startedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(name = "scope_description")
    private String scopeDescription;

    @Column(name = "records_processed", nullable = false)
    private Integer recordsProcessed = 0;

    @Column(name = "records_failed", nullable = false)
    private Integer recordsFailed = 0;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "telemetry_json", columnDefinition = "text")
    private String telemetryJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public OperationalJobExecution() {}

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        if (startedAt == null) startedAt = ZonedDateTime.now();
        if (executionTrigger == null) executionTrigger = "SCHEDULED";
        if (executionStatus == null) executionStatus = "RUNNING";
        if (recordsProcessed == null) recordsProcessed = 0;
        if (recordsFailed == null) recordsFailed = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    public String getExecutionTrigger() { return executionTrigger; }
    public void setExecutionTrigger(String executionTrigger) { this.executionTrigger = executionTrigger; }

    public String getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(String executionStatus) { this.executionStatus = executionStatus; }

    public ZonedDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(ZonedDateTime startedAt) { this.startedAt = startedAt; }

    public ZonedDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(ZonedDateTime completedAt) { this.completedAt = completedAt; }

    public String getScopeDescription() { return scopeDescription; }
    public void setScopeDescription(String scopeDescription) { this.scopeDescription = scopeDescription; }

    public Integer getRecordsProcessed() { return recordsProcessed; }
    public void setRecordsProcessed(Integer recordsProcessed) { this.recordsProcessed = recordsProcessed; }

    public Integer getRecordsFailed() { return recordsFailed; }
    public void setRecordsFailed(Integer recordsFailed) { this.recordsFailed = recordsFailed; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getTelemetryJson() { return telemetryJson; }
    public void setTelemetryJson(String telemetryJson) { this.telemetryJson = telemetryJson; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OperationalJobExecution that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

