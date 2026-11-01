package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "portfolio_import")
public class PortfolioImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @Column(name = "source_filename", nullable = false)
    private String sourceFilename;

    @Column(name = "file_type", nullable = false)
    private String fileType = "CAS_STATEMENT";

    @Column(name = "status", nullable = false)
    private String status = "PENDING_REVIEW"; // PENDING_REVIEW, CONFIRMED, FAILED, CANCELLED

    @Column(name = "total_records_detected", nullable = false)
    private int totalRecordsDetected;

    @Column(name = "resolved_records_count", nullable = false)
    private int resolvedRecordsCount;

    @Column(name = "unresolved_records_count", nullable = false)
    private int unresolvedRecordsCount;

    @Column(name = "ambiguous_records_count", nullable = false)
    private int ambiguousRecordsCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_artifact_id")
    private SourceArtifact sourceArtifact;

    @Column(name = "statement_date")
    private LocalDate statementDate;

    @Column(name = "parser_version", nullable = false)
    private String parserVersion = "CAS_PARSER_V1";

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "confirmed_at")
    private ZonedDateTime confirmedAt;

    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "statement_period_start")
    private LocalDate statementPeriodStart;

    @Column(name = "statement_period_end")
    private LocalDate statementPeriodEnd;

    @Column(name = "investor_pan_masked", length = 32)
    private String investorPanMasked;

    @Column(name = "total_transactions_count")
    private int totalTransactionsCount;

    @Column(name = "reconciliation_diagnostics", columnDefinition = "text")
    private String reconciliationDiagnostics;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @OneToMany(mappedBy = "portfolioImport", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PortfolioImportHolding> holdings = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public String getSourceFilename() { return sourceFilename; }
    public void setSourceFilename(String sourceFilename) { this.sourceFilename = sourceFilename; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalRecordsDetected() { return totalRecordsDetected; }
    public void setTotalRecordsDetected(int totalRecordsDetected) { this.totalRecordsDetected = totalRecordsDetected; }

    public int getResolvedRecordsCount() { return resolvedRecordsCount; }
    public void setResolvedRecordsCount(int resolvedRecordsCount) { this.resolvedRecordsCount = resolvedRecordsCount; }

    public int getUnresolvedRecordsCount() { return unresolvedRecordsCount; }
    public void setUnresolvedRecordsCount(int unresolvedRecordsCount) { this.unresolvedRecordsCount = unresolvedRecordsCount; }

    public int getAmbiguousRecordsCount() { return ambiguousRecordsCount; }
    public void setAmbiguousRecordsCount(int ambiguousRecordsCount) { this.ambiguousRecordsCount = ambiguousRecordsCount; }

    public SourceArtifact getSourceArtifact() { return sourceArtifact; }
    public void setSourceArtifact(SourceArtifact sourceArtifact) { this.sourceArtifact = sourceArtifact; }

    public LocalDate getStatementDate() { return statementDate; }
    public void setStatementDate(LocalDate statementDate) { this.statementDate = statementDate; }

    public String getParserVersion() { return parserVersion; }
    public void setParserVersion(String parserVersion) { this.parserVersion = parserVersion; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(ZonedDateTime confirmedAt) { this.confirmedAt = confirmedAt; }

    public List<PortfolioImportHolding> getHoldings() { return holdings; }
    public void setHoldings(List<PortfolioImportHolding> holdings) { this.holdings = holdings; }

    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }

    public LocalDate getStatementPeriodStart() { return statementPeriodStart; }
    public void setStatementPeriodStart(LocalDate statementPeriodStart) { this.statementPeriodStart = statementPeriodStart; }

    public LocalDate getStatementPeriodEnd() { return statementPeriodEnd; }
    public void setStatementPeriodEnd(LocalDate statementPeriodEnd) { this.statementPeriodEnd = statementPeriodEnd; }

    public String getInvestorPanMasked() { return investorPanMasked; }
    public void setInvestorPanMasked(String investorPanMasked) { this.investorPanMasked = investorPanMasked; }

    public int getTotalTransactionsCount() { return totalTransactionsCount; }
    public void setTotalTransactionsCount(int totalTransactionsCount) { this.totalTransactionsCount = totalTransactionsCount; }

    public String getReconciliationDiagnostics() { return reconciliationDiagnostics; }
    public void setReconciliationDiagnostics(String reconciliationDiagnostics) { this.reconciliationDiagnostics = reconciliationDiagnostics; }

    public ZonedDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(ZonedDateTime completedAt) { this.completedAt = completedAt; }
}
