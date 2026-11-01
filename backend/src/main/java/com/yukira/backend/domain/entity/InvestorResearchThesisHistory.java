package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Durable version history for investor research theses and investigation questions (Task 120, 121, 128).
 * Ensures that updating a research note or thesis never silently overwrites historical investor statements
 * or disassociates them from original empirical evidence.
 */
@Entity
@Table(name = "investor_research_thesis_history")
public class InvestorResearchThesisHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watchlist_id", nullable = false)
    private InvestorWatchlist watchlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_option_id")
    private SchemeOption schemeOption;

    @Column(name = "thesis_version", nullable = false)
    private Integer thesisVersion = 1;

    @Column(name = "thesis_status", nullable = false, length = 50)
    private String thesisStatus = "ACTIVE_HYPOTHESIS";

    @Column(name = "reason_saved", columnDefinition = "text")
    private String reasonSaved;

    @Column(name = "investigation_question", columnDefinition = "text")
    private String investigationQuestion;

    @Column(name = "next_step", columnDefinition = "text")
    private String nextStep;

    @Column(name = "associated_finding_id", length = 100)
    private String associatedFindingId;

    @Column(name = "evidence_context", columnDefinition = "text")
    private String evidenceContext;

    @Column(name = "reconsideration_conditions", columnDefinition = "text")
    private String reconsiderationConditions;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public InvestorResearchThesisHistory() {}

    public InvestorResearchThesisHistory(
        InvestorWatchlist watchlist,
        Investor investor,
        Scheme scheme,
        SchemeOption schemeOption,
        Integer thesisVersion,
        String thesisStatus,
        String reasonSaved,
        String investigationQuestion,
        String nextStep,
        String associatedFindingId,
        String evidenceContext,
        String reconsiderationConditions
    ) {
        this.watchlist = watchlist;
        this.investor = investor;
        this.scheme = scheme;
        this.schemeOption = schemeOption;
        this.thesisVersion = thesisVersion;
        this.thesisStatus = thesisStatus;
        this.reasonSaved = reasonSaved;
        this.investigationQuestion = investigationQuestion;
        this.nextStep = nextStep;
        this.associatedFindingId = associatedFindingId;
        this.evidenceContext = evidenceContext;
        this.reconsiderationConditions = reconsiderationConditions;
        this.createdAt = OffsetDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public InvestorWatchlist getWatchlist() { return watchlist; }
    public void setWatchlist(InvestorWatchlist watchlist) { this.watchlist = watchlist; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }

    public SchemeOption getSchemeOption() { return schemeOption; }
    public void setSchemeOption(SchemeOption schemeOption) { this.schemeOption = schemeOption; }

    public Integer getThesisVersion() { return thesisVersion; }
    public void setThesisVersion(Integer thesisVersion) { this.thesisVersion = thesisVersion; }

    public String getThesisStatus() { return thesisStatus; }
    public void setThesisStatus(String thesisStatus) { this.thesisStatus = thesisStatus; }

    public String getReasonSaved() { return reasonSaved; }
    public void setReasonSaved(String reasonSaved) { this.reasonSaved = reasonSaved; }

    public String getInvestigationQuestion() { return investigationQuestion; }
    public void setInvestigationQuestion(String investigationQuestion) { this.investigationQuestion = investigationQuestion; }

    public String getNextStep() { return nextStep; }
    public void setNextStep(String nextStep) { this.nextStep = nextStep; }

    public String getAssociatedFindingId() { return associatedFindingId; }
    public void setAssociatedFindingId(String associatedFindingId) { this.associatedFindingId = associatedFindingId; }

    public String getEvidenceContext() { return evidenceContext; }
    public void setEvidenceContext(String evidenceContext) { this.evidenceContext = evidenceContext; }

    public String getReconsiderationConditions() { return reconsiderationConditions; }
    public void setReconsiderationConditions(String reconsiderationConditions) { this.reconsiderationConditions = reconsiderationConditions; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorResearchThesisHistory that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

