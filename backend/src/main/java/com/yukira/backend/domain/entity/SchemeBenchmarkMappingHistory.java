package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entity tracking point-in-time scheme-to-benchmark historical mapping validity intervals (Task 378).
 * Prevents retroactively applying current benchmark assignments to past periods.
 */
@Entity
@Table(name = "scheme_benchmark_mapping_history")
public class SchemeBenchmarkMappingHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benchmark_id", nullable = false)
    private Benchmark benchmark;

    @Column(name = "effective_start_date", nullable = false)
    private LocalDate effectiveStartDate;

    @Column(name = "effective_end_date")
    private LocalDate effectiveEndDate;

    @Column(name = "mapping_authority", nullable = false, length = 100)
    private String mappingAuthority = "AMFI_TIER1_MANDATE"; // AMFI_TIER1_MANDATE, AMC_DISCLOSURE, REGULATORY_ORDER

    @Column(name = "mapping_status", nullable = false, length = 50)
    private String mappingStatus = "ACTIVE"; // ACTIVE, HISTORICAL_SUPERSEDED, PENDING_CONFIRMATION

    @Column(name = "source_ref", length = 255)
    private String sourceRef;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public SchemeBenchmarkMappingHistory() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }

    public Benchmark getBenchmark() { return benchmark; }
    public void setBenchmark(Benchmark benchmark) { this.benchmark = benchmark; }

    public LocalDate getEffectiveStartDate() { return effectiveStartDate; }
    public void setEffectiveStartDate(LocalDate effectiveStartDate) { this.effectiveStartDate = effectiveStartDate; }

    public LocalDate getEffectiveEndDate() { return effectiveEndDate; }
    public void setEffectiveEndDate(LocalDate effectiveEndDate) { this.effectiveEndDate = effectiveEndDate; }

    public String getMappingAuthority() { return mappingAuthority; }
    public void setMappingAuthority(String mappingAuthority) { this.mappingAuthority = mappingAuthority; }

    public String getMappingStatus() { return mappingStatus; }
    public void setMappingStatus(String mappingStatus) { this.mappingStatus = mappingStatus; }

    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

