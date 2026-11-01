package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "monitoring_evaluation_run")
public class MonitoringEvaluationRun implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evaluation_timestamp", nullable = false)
    private OffsetDateTime evaluationTimestamp = OffsetDateTime.now();

    @Column(name = "data_cutoff", nullable = false)
    private LocalDate dataCutoff;

    @Column(name = "knowledge_cutoff", nullable = false)
    private OffsetDateTime knowledgeCutoff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_run_id")
    private CalculationRun calculationRun;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "triggers_evaluated", nullable = false)
    private Integer triggersEvaluated = 0;

    @Column(name = "alerts_generated", nullable = false)
    private Integer alertsGenerated = 0;

    @Column(name = "status", nullable = false, length = 64)
    private String status = "COMPLETED";

    @Column(name = "execution_duration_ms", nullable = false)
    private Long executionDurationMs = 0L;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public MonitoringEvaluationRun() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public OffsetDateTime getEvaluationTimestamp() { return evaluationTimestamp; }
    public void setEvaluationTimestamp(OffsetDateTime evaluationTimestamp) { this.evaluationTimestamp = evaluationTimestamp; }

    public LocalDate getDataCutoff() { return dataCutoff; }
    public void setDataCutoff(LocalDate dataCutoff) { this.dataCutoff = dataCutoff; }

    public OffsetDateTime getKnowledgeCutoff() { return knowledgeCutoff; }
    public void setKnowledgeCutoff(OffsetDateTime knowledgeCutoff) { this.knowledgeCutoff = knowledgeCutoff; }

    public CalculationRun getCalculationRun() { return calculationRun; }
    public void setCalculationRun(CalculationRun calculationRun) { this.calculationRun = calculationRun; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Integer getTriggersEvaluated() { return triggersEvaluated; }
    public void setTriggersEvaluated(Integer triggersEvaluated) { this.triggersEvaluated = triggersEvaluated; }

    public Integer getAlertsGenerated() { return alertsGenerated; }
    public void setAlertsGenerated(Integer alertsGenerated) { this.alertsGenerated = alertsGenerated; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(Long executionDurationMs) { this.executionDurationMs = executionDurationMs; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

