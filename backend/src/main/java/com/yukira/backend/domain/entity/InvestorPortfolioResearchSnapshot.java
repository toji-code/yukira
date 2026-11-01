package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_portfolio_research_snapshot")
public class InvestorPortfolioResearchSnapshot implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @Column(name = "snapshot_title", nullable = false)
    private String snapshotTitle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_snapshot_id")
    private InvestorPortfolioAllocationSnapshot allocationSnapshot;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "knowledge_cutoff", nullable = false)
    private ZonedDateTime knowledgeCutoff;

    @Column(name = "fingerprint", nullable = false, length = 128)
    private String fingerprint;

    @Column(name = "xirr_value", precision = 19, scale = 6)
    private BigDecimal xirrValue;

    @Column(name = "total_value", precision = 19, scale = 4)
    private BigDecimal totalValue;

    @Column(name = "cash_flow_summary_json", columnDefinition = "text")
    private String cashFlowSummaryJson;

    @Column(name = "research_thesis", columnDefinition = "text")
    private String researchThesis;

    @Column(name = "investigation_notes", columnDefinition = "text")
    private String investigationNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public InvestorPortfolioResearchSnapshot() {}

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public String getSnapshotTitle() { return snapshotTitle; }
    public void setSnapshotTitle(String snapshotTitle) { this.snapshotTitle = snapshotTitle; }

    public InvestorPortfolioAllocationSnapshot getAllocationSnapshot() { return allocationSnapshot; }
    public void setAllocationSnapshot(InvestorPortfolioAllocationSnapshot allocationSnapshot) { this.allocationSnapshot = allocationSnapshot; }

    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }

    public ZonedDateTime getKnowledgeCutoff() { return knowledgeCutoff; }
    public void setKnowledgeCutoff(ZonedDateTime knowledgeCutoff) { this.knowledgeCutoff = knowledgeCutoff; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public BigDecimal getXirrValue() { return xirrValue; }
    public void setXirrValue(BigDecimal xirrValue) { this.xirrValue = xirrValue; }

    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }

    public String getCashFlowSummaryJson() { return cashFlowSummaryJson; }
    public void setCashFlowSummaryJson(String cashFlowSummaryJson) { this.cashFlowSummaryJson = cashFlowSummaryJson; }

    public String getResearchThesis() { return researchThesis; }
    public void setResearchThesis(String researchThesis) { this.researchThesis = researchThesis; }

    public String getInvestigationNotes() { return investigationNotes; }
    public void setInvestigationNotes(String investigationNotes) { this.investigationNotes = investigationNotes; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorPortfolioResearchSnapshot that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

