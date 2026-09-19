package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "benchmark_observation", uniqueConstraints = {
    @UniqueConstraint(name = "uq_bm_observation", columnNames = {"benchmark_id", "effective_date", "revision_seq"})
})
public class BenchmarkObservation implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benchmark_id", nullable = false)
    private Benchmark benchmark;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "index_level", nullable = false, precision = 20, scale = 8)
    private BigDecimal indexLevel;

    @Column(name = "revision_seq", nullable = false)
    private Integer revisionSeq = 1;

    @Column(name = "is_latest_revision", nullable = false)
    private Boolean isLatestRevision = true;

    @Column(name = "availability_time", nullable = false)
    private OffsetDateTime availabilityTime;

    @Column(name = "ingestion_time", nullable = false)
    private OffsetDateTime ingestionTime = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_artifact_id")
    private SourceArtifact sourceArtifact;

    public BenchmarkObservation() {}

    public BenchmarkObservation(Benchmark benchmark, LocalDate effectiveDate, BigDecimal indexLevel,
                                Integer revisionSeq, OffsetDateTime availabilityTime) {
        this.benchmark = benchmark;
        this.effectiveDate = effectiveDate;
        this.indexLevel = indexLevel;
        this.revisionSeq = revisionSeq;
        this.availabilityTime = availabilityTime;
        this.ingestionTime = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Benchmark getBenchmark() { return benchmark; }
    public void setBenchmark(Benchmark benchmark) { this.benchmark = benchmark; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public BigDecimal getIndexLevel() { return indexLevel; }
    public void setIndexLevel(BigDecimal indexLevel) { this.indexLevel = indexLevel; }

    public Integer getRevisionSeq() { return revisionSeq; }
    public void setRevisionSeq(Integer revisionSeq) { this.revisionSeq = revisionSeq; }

    public Boolean getLatestRevision() { return isLatestRevision; }
    public void setLatestRevision(Boolean latestRevision) { isLatestRevision = latestRevision; }

    public OffsetDateTime getAvailabilityTime() { return availabilityTime; }
    public void setAvailabilityTime(OffsetDateTime availabilityTime) { this.availabilityTime = availabilityTime; }

    public OffsetDateTime getIngestionTime() { return ingestionTime; }
    public void setIngestionTime(OffsetDateTime ingestionTime) { this.ingestionTime = ingestionTime; }

    public SourceArtifact getSourceArtifact() { return sourceArtifact; }
    public void setSourceArtifact(SourceArtifact sourceArtifact) { this.sourceArtifact = sourceArtifact; }
}
