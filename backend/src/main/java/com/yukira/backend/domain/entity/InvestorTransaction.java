package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Authoritative ledger record of an investor-scoped transaction.
 *
 * Epistemic & Audit Invariants:
 * 1. Investor identity strictly scopes every transaction.
 * 2. Original transaction values and source descriptions are preserved.
 * 3. Reversals create a compensating record referencing reversal_reference_id and set is_reversed = true.
 * 4. Deduplication hash ensures deterministic idempotency on repeated imports.
 */
@Entity
@Table(name = "investor_transaction")
public class InvestorTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_option_id")
    private SchemeOption schemeOption;

    @Column(name = "transaction_type", nullable = false, length = 32)
    private String transactionType; // PURCHASE, REDEMPTION, SWITCH_IN, SWITCH_OUT, SIP_PURCHASE, etc.

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "units", nullable = false, precision = 19, scale = 4)
    private BigDecimal units;

    @Column(name = "nav", precision = 19, scale = 4)
    private BigDecimal nav;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "charges_or_taxes", nullable = false, precision = 19, scale = 4)
    private BigDecimal chargesOrTaxes = BigDecimal.ZERO;

    @Column(name = "dividend_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal dividendAmount = BigDecimal.ZERO;

    @Column(name = "source_attribution", nullable = false, length = 32)
    private String sourceAttribution = "MANUAL_ENTRY"; // MANUAL_ENTRY, CAS_IMPORT

    @Column(name = "source_document_ref")
    private String sourceDocumentRef;

    @Column(name = "original_description")
    private String originalDescription;

    @Column(name = "import_batch_id", length = 64)
    private String importBatchId;

    @Column(name = "reconciliation_status", nullable = false, length = 32)
    private String reconciliationStatus = "UNRECONCILED"; // RECONCILED, PARTIALLY_RECONCILED, UNRECONCILED, etc.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_reference_id")
    private InvestorTransaction reversalReference;

    @Column(name = "is_reversed", nullable = false)
    private Boolean isReversed = false;

    @Column(name = "dedup_hash", length = 64)
    private String dedupHash;

    @Column(name = "classification", length = 32)
    private String classification = "EXTERNAL_INVESTMENT"; // EXTERNAL_INVESTMENT, EXTERNAL_REDEMPTION, INTERNAL_SWITCH_IN, INTERNAL_SWITCH_OUT, DIVIDEND_PAYOUT, DIVIDEND_REINVESTMENT, REVERSAL, FEE, CORPORATE_ACTION_ADJUSTMENT

    @Column(name = "corporate_action_type", length = 32)
    private String corporateActionType; // BONUS, CONSOLIDATION, SPLIT, MERGER_CONVERSION

    @Column(name = "corporate_action_ratio", length = 32)
    private String corporateActionRatio;

    @Column(name = "source_record_reference")
    private String sourceRecordReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = ZonedDateTime.now();
        if (updatedAt == null) updatedAt = ZonedDateTime.now();
        if (chargesOrTaxes == null) chargesOrTaxes = BigDecimal.ZERO;
        if (dividendAmount == null) dividendAmount = BigDecimal.ZERO;
        if (isReversed == null) isReversed = false;
        if (reconciliationStatus == null) reconciliationStatus = "UNRECONCILED";
        if (sourceAttribution == null) sourceAttribution = "MANUAL_ENTRY";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public SchemeOption getSchemeOption() { return schemeOption; }
    public void setSchemeOption(SchemeOption schemeOption) { this.schemeOption = schemeOption; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public LocalDate getSettlementDate() { return settlementDate; }
    public void setSettlementDate(LocalDate settlementDate) { this.settlementDate = settlementDate; }

    public BigDecimal getUnits() { return units; }
    public void setUnits(BigDecimal units) { this.units = units; }

    public BigDecimal getNav() { return nav; }
    public void setNav(BigDecimal nav) { this.nav = nav; }

    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }

    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }

    public BigDecimal getChargesOrTaxes() { return chargesOrTaxes; }
    public void setChargesOrTaxes(BigDecimal chargesOrTaxes) { this.chargesOrTaxes = chargesOrTaxes; }

    public BigDecimal getDividendAmount() { return dividendAmount; }
    public void setDividendAmount(BigDecimal dividendAmount) { this.dividendAmount = dividendAmount; }

    public String getSourceAttribution() { return sourceAttribution; }
    public void setSourceAttribution(String sourceAttribution) { this.sourceAttribution = sourceAttribution; }

    public String getSourceDocumentRef() { return sourceDocumentRef; }
    public void setSourceDocumentRef(String sourceDocumentRef) { this.sourceDocumentRef = sourceDocumentRef; }

    public String getOriginalDescription() { return originalDescription; }
    public void setOriginalDescription(String originalDescription) { this.originalDescription = originalDescription; }

    public String getImportBatchId() { return importBatchId; }
    public void setImportBatchId(String importBatchId) { this.importBatchId = importBatchId; }

    public String getReconciliationStatus() { return reconciliationStatus; }
    public void setReconciliationStatus(String reconciliationStatus) { this.reconciliationStatus = reconciliationStatus; }

    public InvestorTransaction getReversalReference() { return reversalReference; }
    public void setReversalReference(InvestorTransaction reversalReference) { this.reversalReference = reversalReference; }

    public Boolean getIsReversed() { return isReversed; }
    public void setIsReversed(Boolean reversed) { isReversed = reversed; }

    public String getDedupHash() { return dedupHash; }
    public void setDedupHash(String dedupHash) { this.dedupHash = dedupHash; }

    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }

    public String getCorporateActionType() { return corporateActionType; }
    public void setCorporateActionType(String corporateActionType) { this.corporateActionType = corporateActionType; }

    public String getCorporateActionRatio() { return corporateActionRatio; }
    public void setCorporateActionRatio(String corporateActionRatio) { this.corporateActionRatio = corporateActionRatio; }

    public String getSourceRecordReference() { return sourceRecordReference; }
    public void setSourceRecordReference(String sourceRecordReference) { this.sourceRecordReference = sourceRecordReference; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Transient
    private String reconciliationNotes;
    public String getReconciliationNotes() { return reconciliationNotes; }
    public void setReconciliationNotes(String reconciliationNotes) { this.reconciliationNotes = reconciliationNotes; }

    public BigDecimal getAmount() { return netAmount != null ? netAmount : grossAmount; }
    public String getTransactionReference() { return sourceRecordReference != null ? sourceRecordReference : (sourceDocumentRef != null ? sourceDocumentRef : (id != null ? String.valueOf(id) : "N/A")); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorTransaction that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

