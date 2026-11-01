package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "scheme_benchmark_mapping")
public class SchemeBenchmarkMapping implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benchmark_id", nullable = false)
    private Benchmark benchmark;

    @Column(name = "mapping_role", nullable = false, length = 32)
    private String mappingRole = "PRIMARY"; // PRIMARY, SECONDARY, FALLBACK

    @Column(name = "mapping_source", nullable = false, length = 64)
    private String mappingSource; // AMC_DISCLOSURE, REGULATORY_MANDATE, CATEGORY_DEFAULT

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_tri", nullable = false)
    private Boolean isTri = true;

    @Column(name = "provenance_reference")
    private String provenanceReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public SchemeBenchmarkMapping() {}

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        if (mappingRole == null) mappingRole = "PRIMARY";
        if (isTri == null) isTri = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }

    public Benchmark getBenchmark() { return benchmark; }
    public void setBenchmark(Benchmark benchmark) { this.benchmark = benchmark; }

    public String getMappingRole() { return mappingRole; }
    public void setMappingRole(String mappingRole) { this.mappingRole = mappingRole; }

    public String getMappingSource() { return mappingSource; }
    public void setMappingSource(String mappingSource) { this.mappingSource = mappingSource; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public Boolean getIsTri() { return isTri; }
    public void setIsTri(Boolean isTri) { this.isTri = isTri; }

    public String getProvenanceReference() { return provenanceReference; }
    public void setProvenanceReference(String provenanceReference) { this.provenanceReference = provenanceReference; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchemeBenchmarkMapping that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

