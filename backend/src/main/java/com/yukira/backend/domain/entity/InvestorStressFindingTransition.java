package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Durable audit ledger for stress investigation finding lifecycle transitions (Task 101, 102).
 * Captures explicit transition provenance, distinguishing investor resolution/dismissal from
 * system-driven supersession across calculation runs without erasing historical context.
 */
@Entity
@Table(name = "investor_stress_finding_transition")
public class InvestorStressFindingTransition implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finding_record_id", nullable = false)
    private InvestorStressFinding finding;

    @Column(name = "finding_id", nullable = false, length = 100)
    private String findingId;

    @Column(name = "previous_status", nullable = false, length = 50)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 50)
    private String newStatus;

    @Column(name = "transition_by", nullable = false, length = 50)
    private String transitionBy; // 'SYSTEM', 'INVESTOR'

    @Column(name = "transition_reason", columnDefinition = "text")
    private String transitionReason;

    @Column(name = "investor_notes", columnDefinition = "text")
    private String investorNotes;

    @Column(name = "calculation_run_code", length = 100)
    private String calculationRunCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public InvestorStressFindingTransition() {}

    public InvestorStressFindingTransition(
        InvestorStressFinding finding,
        String findingId,
        String previousStatus,
        String newStatus,
        String transitionBy,
        String transitionReason,
        String investorNotes,
        String calculationRunCode
    ) {
        this.finding = finding;
        this.findingId = findingId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.transitionBy = transitionBy;
        this.transitionReason = transitionReason;
        this.investorNotes = investorNotes;
        this.calculationRunCode = calculationRunCode;
        this.createdAt = OffsetDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public InvestorStressFinding getFinding() { return finding; }
    public void setFinding(InvestorStressFinding finding) { this.finding = finding; }

    public String getFindingId() { return findingId; }
    public void setFindingId(String findingId) { this.findingId = findingId; }

    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getTransitionBy() { return transitionBy; }
    public void setTransitionBy(String transitionBy) { this.transitionBy = transitionBy; }

    public String getTransitionReason() { return transitionReason; }
    public void setTransitionReason(String transitionReason) { this.transitionReason = transitionReason; }

    public String getInvestorNotes() { return investorNotes; }
    public void setInvestorNotes(String investorNotes) { this.investorNotes = investorNotes; }

    public String getCalculationRunCode() { return calculationRunCode; }
    public void setCalculationRunCode(String calculationRunCode) { this.calculationRunCode = calculationRunCode; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorStressFindingTransition that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

