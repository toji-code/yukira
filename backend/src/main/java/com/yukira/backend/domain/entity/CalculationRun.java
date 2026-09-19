package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "calculation_run")
public class CalculationRun implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_option_id", nullable = false)
    private SchemeOption schemeOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benchmark_id", nullable = false)
    private Benchmark benchmark;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "knowledge_cutoff_time", nullable = false)
    private OffsetDateTime knowledgeCutoffTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "methodology_version_id", nullable = false)
    private MethodologyVersion methodologyVersion;

    @Column(name = "engine_software_version", nullable = false, length = 100)
    private String engineSoftwareVersion;

    @Column(name = "input_snapshot_sha256", length = 64)
    private String inputSnapshotSha256;

    @Column(name = "execution_started_at", nullable = false)
    private OffsetDateTime executionStartedAt = OffsetDateTime.now();

    @Column(name = "execution_completed_at")
    private OffsetDateTime executionCompletedAt;

    @Column(name = "run_status", nullable = false, length = 30)
    private String runStatus = "RUNNING";

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    public CalculationRun() {}

    public CalculationRun(SchemeOption schemeOption, Benchmark benchmark, LocalDate asOfDate,
                          OffsetDateTime knowledgeCutoffTime, MethodologyVersion methodologyVersion,
                          String engineSoftwareVersion) {
        this.schemeOption = schemeOption;
        this.benchmark = benchmark;
        this.asOfDate = asOfDate;
        this.knowledgeCutoffTime = knowledgeCutoffTime;
        this.methodologyVersion = methodologyVersion;
        this.engineSoftwareVersion = engineSoftwareVersion;
        this.executionStartedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SchemeOption getSchemeOption() { return schemeOption; }
    public void setSchemeOption(SchemeOption schemeOption) { this.schemeOption = schemeOption; }

    public Benchmark getBenchmark() { return benchmark; }
    public void setBenchmark(Benchmark benchmark) { this.benchmark = benchmark; }

    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }

    public OffsetDateTime getKnowledgeCutoffTime() { return knowledgeCutoffTime; }
    public void setKnowledgeCutoffTime(OffsetDateTime knowledgeCutoffTime) { this.knowledgeCutoffTime = knowledgeCutoffTime; }

    public MethodologyVersion getMethodologyVersion() { return methodologyVersion; }
    public void setMethodologyVersion(MethodologyVersion methodologyVersion) { this.methodologyVersion = methodologyVersion; }

    public String getEngineSoftwareVersion() { return engineSoftwareVersion; }
    public void setEngineSoftwareVersion(String engineSoftwareVersion) { this.engineSoftwareVersion = engineSoftwareVersion; }

    public String getInputSnapshotSha256() { return inputSnapshotSha256; }
    public void setInputSnapshotSha256(String inputSnapshotSha256) { this.inputSnapshotSha256 = inputSnapshotSha256; }

    public OffsetDateTime getExecutionStartedAt() { return executionStartedAt; }
    public void setExecutionStartedAt(OffsetDateTime executionStartedAt) { this.executionStartedAt = executionStartedAt; }

    public OffsetDateTime getExecutionCompletedAt() { return executionCompletedAt; }
    public void setExecutionCompletedAt(OffsetDateTime executionCompletedAt) { this.executionCompletedAt = executionCompletedAt; }

    public String getRunStatus() { return runStatus; }
    public void setRunStatus(String runStatus) { this.runStatus = runStatus; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
