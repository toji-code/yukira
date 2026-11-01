package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Staged transaction record extracted from an uploaded CAS statement.
 */
@Entity
@Table(name = "portfolio_import_transaction")
public class PortfolioImportTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_import_id", nullable = false)
    private PortfolioImport portfolioImport;

    @Column(name = "raw_scheme_name", nullable = false)
    private String rawSchemeName;

    @Column(name = "folio_number", length = 64)
    private String folioNumber;

    @Column(name = "isin", length = 32)
    private String isin;

    @Column(name = "amfi_code", length = 32)
    private String amfiCode;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "raw_type_description", nullable = false)
    private String rawTypeDescription;

    @Column(name = "normalized_type", nullable = false, length = 32)
    private String normalizedType;

    @Column(name = "units", precision = 19, scale = 4)
    private BigDecimal units;

    @Column(name = "nav", precision = 19, scale = 4)
    private BigDecimal nav;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_scheme_option_id")
    private SchemeOption resolvedSchemeOption;

    @Column(name = "resolution_status", nullable = false, length = 32)
    private String resolutionStatus = "UNRESOLVED"; // RESOLVED, UNRESOLVED, AMBIGUOUS, DUPLICATE, REQUIRES_REVIEW

    @Column(name = "resolution_method", nullable = false, length = 32)
    private String resolutionMethod = "UNRESOLVED";

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @Column(name = "dedup_hash", length = 64)
    private String dedupHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = ZonedDateTime.now();
        if (resolutionStatus == null) resolutionStatus = "UNRESOLVED";
        if (resolutionMethod == null) resolutionMethod = "UNRESOLVED";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PortfolioImport getPortfolioImport() { return portfolioImport; }
    public void setPortfolioImport(PortfolioImport portfolioImport) { this.portfolioImport = portfolioImport; }

    public String getRawSchemeName() { return rawSchemeName; }
    public void setRawSchemeName(String rawSchemeName) { this.rawSchemeName = rawSchemeName; }

    public String getFolioNumber() { return folioNumber; }
    public void setFolioNumber(String folioNumber) { this.folioNumber = folioNumber; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getAmfiCode() { return amfiCode; }
    public void setAmfiCode(String amfiCode) { this.amfiCode = amfiCode; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public String getRawTypeDescription() { return rawTypeDescription; }
    public void setRawTypeDescription(String rawTypeDescription) { this.rawTypeDescription = rawTypeDescription; }

    public String getNormalizedType() { return normalizedType; }
    public void setNormalizedType(String normalizedType) { this.normalizedType = normalizedType; }

    public BigDecimal getUnits() { return units; }
    public void setUnits(BigDecimal units) { this.units = units; }

    public BigDecimal getNav() { return nav; }
    public void setNav(BigDecimal nav) { this.nav = nav; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public SchemeOption getResolvedSchemeOption() { return resolvedSchemeOption; }
    public void setResolvedSchemeOption(SchemeOption resolvedSchemeOption) { this.resolvedSchemeOption = resolvedSchemeOption; }

    public String getResolutionStatus() { return resolutionStatus; }
    public void setResolutionStatus(String resolutionStatus) { this.resolutionStatus = resolutionStatus; }

    public String getResolutionMethod() { return resolutionMethod; }
    public void setResolutionMethod(String resolutionMethod) { this.resolutionMethod = resolutionMethod; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    public String getDedupHash() { return dedupHash; }
    public void setDedupHash(String dedupHash) { this.dedupHash = dedupHash; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PortfolioImportTransaction that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

