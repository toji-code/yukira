package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "scheme_benchmark_hist")
public class SchemeBenchmarkHist implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benchmark_id", nullable = false)
    private Benchmark benchmark;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = true;

    public SchemeBenchmarkHist() {}

    public SchemeBenchmarkHist(Scheme scheme, Benchmark benchmark, LocalDate effectiveFrom, LocalDate effectiveTo, Boolean isPrimary) {
        this.scheme = scheme;
        this.benchmark = benchmark;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.isPrimary = isPrimary != null ? isPrimary : true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }

    public Benchmark getBenchmark() { return benchmark; }
    public void setBenchmark(Benchmark benchmark) { this.benchmark = benchmark; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }
}
