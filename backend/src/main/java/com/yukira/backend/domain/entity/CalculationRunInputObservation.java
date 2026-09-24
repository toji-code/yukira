package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "calculation_run_input_observation", uniqueConstraints = {
    @UniqueConstraint(name = "uq_run_input_nav", columnNames = {"calculation_run_id", "nav_observation_id"}),
    @UniqueConstraint(name = "uq_run_input_benchmark", columnNames = {"calculation_run_id", "benchmark_observation_id"}),
    @UniqueConstraint(name = "uq_run_input_risk_free", columnNames = {"calculation_run_id", "risk_free_observation_id"})
})
public class CalculationRunInputObservation implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_run_id", nullable = false)
    private CalculationRun calculationRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nav_observation_id")
    private NavObservation navObservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benchmark_observation_id")
    private BenchmarkObservation benchmarkObservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "risk_free_observation_id")
    private RiskFreeObservation riskFreeObservation;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "revision_seq", nullable = false)
    private Integer revisionSeq;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    public CalculationRunInputObservation() {}

    public CalculationRunInputObservation(CalculationRun calculationRun, NavObservation navObservation,
                                          LocalDate effectiveDate, Integer revisionSeq) {
        this.calculationRun = calculationRun;
        this.navObservation = navObservation;
        this.benchmarkObservation = null;
        this.riskFreeObservation = null;
        this.effectiveDate = effectiveDate;
        this.revisionSeq = revisionSeq;
        this.recordedAt = OffsetDateTime.now();
    }

    public CalculationRunInputObservation(CalculationRun calculationRun, BenchmarkObservation benchmarkObservation,
                                          LocalDate effectiveDate, Integer revisionSeq) {
        this.calculationRun = calculationRun;
        this.navObservation = null;
        this.benchmarkObservation = benchmarkObservation;
        this.riskFreeObservation = null;
        this.effectiveDate = effectiveDate;
        this.revisionSeq = revisionSeq;
        this.recordedAt = OffsetDateTime.now();
    }

    public CalculationRunInputObservation(CalculationRun calculationRun, RiskFreeObservation riskFreeObservation,
                                          LocalDate effectiveDate, Integer revisionSeq) {
        this.calculationRun = calculationRun;
        this.navObservation = null;
        this.benchmarkObservation = null;
        this.riskFreeObservation = riskFreeObservation;
        this.effectiveDate = effectiveDate;
        this.revisionSeq = revisionSeq;
        this.recordedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CalculationRun getCalculationRun() { return calculationRun; }
    public void setCalculationRun(CalculationRun calculationRun) { this.calculationRun = calculationRun; }

    public NavObservation getNavObservation() { return navObservation; }
    public void setNavObservation(NavObservation navObservation) { this.navObservation = navObservation; }

    public BenchmarkObservation getBenchmarkObservation() { return benchmarkObservation; }
    public void setBenchmarkObservation(BenchmarkObservation benchmarkObservation) { this.benchmarkObservation = benchmarkObservation; }

    public RiskFreeObservation getRiskFreeObservation() { return riskFreeObservation; }
    public void setRiskFreeObservation(RiskFreeObservation riskFreeObservation) { this.riskFreeObservation = riskFreeObservation; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public Integer getRevisionSeq() { return revisionSeq; }
    public void setRevisionSeq(Integer revisionSeq) { this.revisionSeq = revisionSeq; }

    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
}
