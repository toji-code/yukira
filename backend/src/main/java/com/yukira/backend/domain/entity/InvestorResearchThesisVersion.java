package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity
@Table(name = "investor_research_thesis_version", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"investor_id", "thesis_id", "version_number"})
})
public class InvestorResearchThesisVersion implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id")
    private Scheme scheme;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_option_id")
    private SchemeOption schemeOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watchlist_id")
    private InvestorWatchlist watchlist;

    @Column(name = "thesis_id", nullable = false, length = 64)
    private String thesisId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber = 1;

    @Column(name = "author", nullable = false, length = 128)
    private String author = "Investor";

    @Column(name = "thesis_title", nullable = false, length = 255)
    private String thesisTitle;

    @Column(name = "thesis_body", nullable = false, columnDefinition = "TEXT")
    private String thesisBody;

    @Column(name = "linked_condition", length = 255)
    private String linkedCondition;

    @Column(name = "linked_evidence", columnDefinition = "TEXT")
    private String linkedEvidence;

    @Column(name = "revision_reason", columnDefinition = "TEXT")
    private String revisionReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public InvestorResearchThesisVersion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }

    public SchemeOption getSchemeOption() { return schemeOption; }
    public void setSchemeOption(SchemeOption schemeOption) { this.schemeOption = schemeOption; }

    public InvestorWatchlist getWatchlist() { return watchlist; }
    public void setWatchlist(InvestorWatchlist watchlist) { this.watchlist = watchlist; }

    public String getThesisId() { return thesisId; }
    public void setThesisId(String thesisId) { this.thesisId = thesisId; }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getThesisTitle() { return thesisTitle; }
    public void setThesisTitle(String thesisTitle) { this.thesisTitle = thesisTitle; }

    public String getThesisBody() { return thesisBody; }
    public void setThesisBody(String thesisBody) { this.thesisBody = thesisBody; }

    public String getLinkedCondition() { return linkedCondition; }
    public void setLinkedCondition(String linkedCondition) { this.linkedCondition = linkedCondition; }

    public String getLinkedEvidence() { return linkedEvidence; }
    public void setLinkedEvidence(String linkedEvidence) { this.linkedEvidence = linkedEvidence; }

    public String getRevisionReason() { return revisionReason; }
    public void setRevisionReason(String revisionReason) { this.revisionReason = revisionReason; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

