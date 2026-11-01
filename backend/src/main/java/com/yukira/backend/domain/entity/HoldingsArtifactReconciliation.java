package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "holdings_artifact_reconciliation")
public class HoldingsArtifactReconciliation implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_artifact_id")
    private SourceArtifact sourceArtifact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amc_id")
    private Amc amc;

    @Column(name = "reporting_period", nullable = false)
    private LocalDate reportingPeriod;

    @Column(name = "parser_version", nullable = false, length = 32)
    private String parserVersion;

    @Column(name = "parsing_outcome", nullable = false, length = 32)
    private String parsingOutcome; // SUCCESSFULLY_PARSED, PARTIALLY_PARSED, UNSUPPORTED_FORMAT, INVALID_DISCLOSURE, DUPLICATE_ARTIFACT, SUPERSEDED

    @Column(name = "total_rows_read", nullable = false)
    private Integer totalRowsRead = 0;

    @Column(name = "equity_rows_parsed", nullable = false)
    private Integer equityRowsParsed = 0;

    @Column(name = "debt_rows_parsed", nullable = false)
    private Integer debtRowsParsed = 0;

    @Column(name = "cash_rows_parsed", nullable = false)
    private Integer cashRowsParsed = 0;

    @Column(name = "derivative_rows_parsed", nullable = false)
    private Integer derivativeRowsParsed = 0;

    @Column(name = "unclassified_rows", nullable = false)
    private Integer unclassifiedRows = 0;

    @Column(name = "total_market_value", precision = 19, scale = 4)
    private BigDecimal totalMarketValue;

    @Column(name = "derivative_notional_value", precision = 19, scale = 4)
    private BigDecimal derivativeNotionalValue;

    @Column(name = "cash_market_value", precision = 19, scale = 4)
    private BigDecimal cashMarketValue;

    @Column(name = "error_diagnostics", columnDefinition = "text")
    private String errorDiagnostics;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public HoldingsArtifactReconciliation() {}

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        if (totalRowsRead == null) totalRowsRead = 0;
        if (equityRowsParsed == null) equityRowsParsed = 0;
        if (debtRowsParsed == null) debtRowsParsed = 0;
        if (cashRowsParsed == null) cashRowsParsed = 0;
        if (derivativeRowsParsed == null) derivativeRowsParsed = 0;
        if (unclassifiedRows == null) unclassifiedRows = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SourceArtifact getSourceArtifact() { return sourceArtifact; }
    public void setSourceArtifact(SourceArtifact sourceArtifact) { this.sourceArtifact = sourceArtifact; }

    public Amc getAmc() { return amc; }
    public void setAmc(Amc amc) { this.amc = amc; }

    public LocalDate getReportingPeriod() { return reportingPeriod; }
    public void setReportingPeriod(LocalDate reportingPeriod) { this.reportingPeriod = reportingPeriod; }

    public String getParserVersion() { return parserVersion; }
    public void setParserVersion(String parserVersion) { this.parserVersion = parserVersion; }

    public String getParsingOutcome() { return parsingOutcome; }
    public void setParsingOutcome(String parsingOutcome) { this.parsingOutcome = parsingOutcome; }

    public Integer getTotalRowsRead() { return totalRowsRead; }
    public void setTotalRowsRead(Integer totalRowsRead) { this.totalRowsRead = totalRowsRead; }

    public Integer getEquityRowsParsed() { return equityRowsParsed; }
    public void setEquityRowsParsed(Integer equityRowsParsed) { this.equityRowsParsed = equityRowsParsed; }

    public Integer getDebtRowsParsed() { return debtRowsParsed; }
    public void setDebtRowsParsed(Integer debtRowsParsed) { this.debtRowsParsed = debtRowsParsed; }

    public Integer getCashRowsParsed() { return cashRowsParsed; }
    public void setCashRowsParsed(Integer cashRowsParsed) { this.cashRowsParsed = cashRowsParsed; }

    public Integer getDerivativeRowsParsed() { return derivativeRowsParsed; }
    public void setDerivativeRowsParsed(Integer derivativeRowsParsed) { this.derivativeRowsParsed = derivativeRowsParsed; }

    public Integer getUnclassifiedRows() { return unclassifiedRows; }
    public void setUnclassifiedRows(Integer unclassifiedRows) { this.unclassifiedRows = unclassifiedRows; }

    public BigDecimal getTotalMarketValue() { return totalMarketValue; }
    public void setTotalMarketValue(BigDecimal totalMarketValue) { this.totalMarketValue = totalMarketValue; }

    public BigDecimal getDerivativeNotionalValue() { return derivativeNotionalValue; }
    public void setDerivativeNotionalValue(BigDecimal derivativeNotionalValue) { this.derivativeNotionalValue = derivativeNotionalValue; }

    public BigDecimal getCashMarketValue() { return cashMarketValue; }
    public void setCashMarketValue(BigDecimal cashMarketValue) { this.cashMarketValue = cashMarketValue; }

    public String getErrorDiagnostics() { return errorDiagnostics; }
    public void setErrorDiagnostics(String errorDiagnostics) { this.errorDiagnostics = errorDiagnostics; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HoldingsArtifactReconciliation that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

