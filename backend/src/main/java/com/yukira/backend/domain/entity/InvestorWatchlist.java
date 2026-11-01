package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_watchlist")
public class InvestorWatchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_option_id")
    private SchemeOption schemeOption;

    @Column(name = "reason_saved")
    private String reasonSaved;

    @Column(name = "investigation_question")
    private String investigationQuestion;

    @Column(name = "next_step")
    private String nextStep;

    @Column(name = "thesis_status", length = 50)
    private String thesisStatus = "ACTIVE_HYPOTHESIS";

    @Column(name = "associated_finding_id", length = 100)
    private String associatedFindingId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }
    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }
    public SchemeOption getSchemeOption() { return schemeOption; }
    public void setSchemeOption(SchemeOption schemeOption) { this.schemeOption = schemeOption; }
    public String getReasonSaved() { return reasonSaved; }
    public void setReasonSaved(String reasonSaved) { this.reasonSaved = reasonSaved; }
    public String getInvestigationQuestion() { return investigationQuestion; }
    public void setInvestigationQuestion(String investigationQuestion) { this.investigationQuestion = investigationQuestion; }
    public String getNextStep() { return nextStep; }
    public void setNextStep(String nextStep) { this.nextStep = nextStep; }
    public String getThesisStatus() { return thesisStatus; }
    public String getStatus() { return thesisStatus; }
    public void setThesisStatus(String thesisStatus) { this.thesisStatus = thesisStatus; }
    public String getAssociatedFindingId() { return associatedFindingId; }
    public void setAssociatedFindingId(String associatedFindingId) { this.associatedFindingId = associatedFindingId; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public ZonedDateTime getAddedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorWatchlist that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
