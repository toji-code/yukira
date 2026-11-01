package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "fifo_lot_ledger")
public class FifoLotLedger implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_option_id", nullable = false)
    private SchemeOption schemeOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_transaction_id")
    private InvestorTransaction sourceTransaction;

    @Column(name = "acquisition_date", nullable = false)
    private LocalDate acquisitionDate;

    @Column(name = "units_acquired", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitsAcquired;

    @Column(name = "units_remaining", precision = 19, scale = 4, nullable = false)
    private BigDecimal unitsRemaining;

    @Column(name = "acquisition_nav", precision = 19, scale = 4, nullable = false)
    private BigDecimal acquisitionNav;

    @Column(name = "total_cost", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalCost;

    @Column(name = "is_depleted", nullable = false)
    private Boolean isDepleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public FifoLotLedger() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
        if (isDepleted == null) isDepleted = false;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public SchemeOption getSchemeOption() { return schemeOption; }
    public void setSchemeOption(SchemeOption schemeOption) { this.schemeOption = schemeOption; }

    public InvestorTransaction getSourceTransaction() { return sourceTransaction; }
    public void setSourceTransaction(InvestorTransaction sourceTransaction) { this.sourceTransaction = sourceTransaction; }

    public InvestorTransaction getAcquisitionTransaction() { return sourceTransaction; }
    public void setAcquisitionTransaction(InvestorTransaction sourceTransaction) { this.sourceTransaction = sourceTransaction; }

    public LocalDate getAcquisitionDate() { return acquisitionDate; }
    public void setAcquisitionDate(LocalDate acquisitionDate) { this.acquisitionDate = acquisitionDate; }

    public BigDecimal getUnitsAcquired() { return unitsAcquired; }
    public void setUnitsAcquired(BigDecimal unitsAcquired) { this.unitsAcquired = unitsAcquired; }

    public BigDecimal getUnitsRemaining() { return unitsRemaining; }
    public void setUnitsRemaining(BigDecimal unitsRemaining) { this.unitsRemaining = unitsRemaining; }

    public BigDecimal getAcquisitionNav() { return acquisitionNav; }
    public void setAcquisitionNav(BigDecimal acquisitionNav) { this.acquisitionNav = acquisitionNav; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

    public Boolean getIsDepleted() { return isDepleted; }
    public void setIsDepleted(Boolean isDepleted) { this.isDepleted = isDepleted; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FifoLotLedger that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

