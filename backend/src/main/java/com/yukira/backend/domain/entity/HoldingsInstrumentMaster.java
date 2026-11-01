package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "holdings_instrument_master")
public class HoldingsInstrumentMaster implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "isin", nullable = false, unique = true, length = 32)
    private String isin;

    @Column(name = "instrument_name", nullable = false)
    private String instrumentName;

    @Column(name = "asset_class", nullable = false, length = 32)
    private String assetClass = "EQUITY"; // EQUITY, DEBT, CASH_EQUIVALENT, DERIVATIVE, COMMODITY, REAL_ESTATE

    @Column(name = "issuer_name")
    private String issuerName;

    @Column(name = "industry", length = 128)
    private String industry;

    @Column(name = "sector_name", length = 128)
    private String sectorName;

    @Column(name = "credit_rating", length = 32)
    private String creditRating;

    @Column(name = "rating_agency", length = 64)
    private String ratingAgency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public HoldingsInstrumentMaster() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
        if (assetClass == null) assetClass = "EQUITY";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getInstrumentName() { return instrumentName; }
    public void setInstrumentName(String instrumentName) { this.instrumentName = instrumentName; }

    public String getAssetClass() { return assetClass; }
    public void setAssetClass(String assetClass) { this.assetClass = assetClass; }

    public String getIssuerName() { return issuerName; }
    public void setIssuerName(String issuerName) { this.issuerName = issuerName; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getSectorName() { return sectorName; }
    public void setSectorName(String sectorName) { this.sectorName = sectorName; }

    public String getCreditRating() { return creditRating; }
    public void setCreditRating(String creditRating) { this.creditRating = creditRating; }

    public String getRatingAgency() { return ratingAgency; }
    public void setRatingAgency(String ratingAgency) { this.ratingAgency = ratingAgency; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HoldingsInstrumentMaster that)) return false;
        return Objects.equals(isin, that.isin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isin);
    }
}

