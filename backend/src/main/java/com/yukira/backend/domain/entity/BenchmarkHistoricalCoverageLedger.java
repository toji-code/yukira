package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entity modeling the historical coverage and availability ledger for market benchmarks (Tasks 376, 377).
 * Tracks observation start/end dates, total count, gap intervals, artifact coverage, and ingestion state.
 */
@Entity
@Table(name = "benchmark_historical_coverage_ledger")
public class BenchmarkHistoricalCoverageLedger implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benchmark_id", nullable = false)
    private Benchmark benchmark;

    @Column(name = "benchmark_code", nullable = false, unique = true, length = 100)
    private String benchmarkCode;

    @Column(name = "benchmark_name", nullable = false)
    private String benchmarkName;

    @Column(name = "index_type", nullable = false, length = 50)
    private String indexType = "TOTAL_RETURN_INDEX";

    @Column(name = "assigned_scheme_universe_count", nullable = false)
    private Integer assignedSchemeUniverseCount = 0;

    @Column(name = "available_start_date")
    private LocalDate availableStartDate;

    @Column(name = "available_end_date")
    private LocalDate availableEndDate;

    @Column(name = "total_observations", nullable = false)
    private Integer totalObservations = 0;

    @Column(name = "missing_intervals_count", nullable = false)
    private Integer missingIntervalsCount = 0;

    @Column(name = "source_artifact_coverage_ratio", precision = 6, scale = 4)
    private BigDecimal sourceArtifactCoverageRatio = new BigDecimal("1.0000");

    @Column(name = "ingestion_state", nullable = false, length = 50)
    private String ingestionState = "EXTERNAL_FEED_PENDING"; // OPERATIONAL, EXTERNAL_FEED_PENDING, CONFIGURATION_PENDING, DEGRADED

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();


    @Column(name = "declared_start_date")
    private LocalDate declaredStartDate;

    @Column(name = "declared_end_date")
    private LocalDate declaredEndDate;

    @Column(name = "declared_observation_count")
    private Integer declaredObservationCount = 0;

    @Column(name = "actual_observation_count")
    private Integer actualObservationCount = 0;

    @Column(name = "actual_start_date")
    private LocalDate actualStartDate;

    @Column(name = "actual_end_date")
    private LocalDate actualEndDate;

    @Column(name = "coverage_reconciliation_status", length = 50)
    private String coverageReconciliationStatus = "DECLARED_VS_ACTUAL_RECONCILED";

    @Column(name = "source_artifact_lineage_status", length = 50)
    private String sourceArtifactLineageStatus = "SOURCE_LINEAGE_PENDING";

    public BenchmarkHistoricalCoverageLedger() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Benchmark getBenchmark() { return benchmark; }
    public void setBenchmark(Benchmark benchmark) { this.benchmark = benchmark; }

    public String getBenchmarkCode() { return benchmarkCode; }
    public void setBenchmarkCode(String benchmarkCode) { this.benchmarkCode = benchmarkCode; }

    public String getBenchmarkName() { return benchmarkName; }
    public void setBenchmarkName(String benchmarkName) { this.benchmarkName = benchmarkName; }

    public String getIndexType() { return indexType; }
    public void setIndexType(String indexType) { this.indexType = indexType; }

    public Integer getAssignedSchemeUniverseCount() { return assignedSchemeUniverseCount; }
    public void setAssignedSchemeUniverseCount(Integer assignedSchemeUniverseCount) { this.assignedSchemeUniverseCount = assignedSchemeUniverseCount; }

    public LocalDate getAvailableStartDate() { return availableStartDate; }
    public void setAvailableStartDate(LocalDate availableStartDate) { this.availableStartDate = availableStartDate; }

    public LocalDate getAvailableEndDate() { return availableEndDate; }
    public void setAvailableEndDate(LocalDate availableEndDate) { this.availableEndDate = availableEndDate; }

    public Integer getTotalObservations() { return totalObservations; }
    public void setTotalObservations(Integer totalObservations) { this.totalObservations = totalObservations; }

    public Integer getMissingIntervalsCount() { return missingIntervalsCount; }
    public void setMissingIntervalsCount(Integer missingIntervalsCount) { this.missingIntervalsCount = missingIntervalsCount; }

    public BigDecimal getSourceArtifactCoverageRatio() { return sourceArtifactCoverageRatio; }
    public void setSourceArtifactCoverageRatio(BigDecimal sourceArtifactCoverageRatio) { this.sourceArtifactCoverageRatio = sourceArtifactCoverageRatio; }

    public String getIngestionState() { return ingestionState; }
    public void setIngestionState(String ingestionState) { this.ingestionState = ingestionState; }

    public OffsetDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(OffsetDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDate getDeclaredStartDate() { return declaredStartDate; }
    public void setDeclaredStartDate(LocalDate declaredStartDate) { this.declaredStartDate = declaredStartDate; }

    public LocalDate getDeclaredEndDate() { return declaredEndDate; }
    public void setDeclaredEndDate(LocalDate declaredEndDate) { this.declaredEndDate = declaredEndDate; }

    public Integer getDeclaredObservationCount() { return declaredObservationCount; }
    public void setDeclaredObservationCount(Integer declaredObservationCount) { this.declaredObservationCount = declaredObservationCount; }

    public Integer getActualObservationCount() { return actualObservationCount; }
    public void setActualObservationCount(Integer actualObservationCount) { this.actualObservationCount = actualObservationCount; }

    public LocalDate getActualStartDate() { return actualStartDate; }
    public void setActualStartDate(LocalDate actualStartDate) { this.actualStartDate = actualStartDate; }

    public LocalDate getActualEndDate() { return actualEndDate; }
    public void setActualEndDate(LocalDate actualEndDate) { this.actualEndDate = actualEndDate; }

    public String getCoverageReconciliationStatus() { return coverageReconciliationStatus; }
    public void setCoverageReconciliationStatus(String coverageReconciliationStatus) { this.coverageReconciliationStatus = coverageReconciliationStatus; }

    public String getSourceArtifactLineageStatus() { return sourceArtifactLineageStatus; }
    public void setSourceArtifactLineageStatus(String sourceArtifactLineageStatus) { this.sourceArtifactLineageStatus = sourceArtifactLineageStatus; }

}
