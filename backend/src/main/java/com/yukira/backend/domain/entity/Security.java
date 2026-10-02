package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "security")
public class Security implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_name", nullable = false)
    private String canonicalName;

    @Column(name = "asset_class", nullable = false, length = 50)
    private String assetClass;

    @Column(name = "instrument_type", nullable = false, length = 50)
    private String instrumentType;

    
    @Column(name = "issuer_name")
    private String issuerName;

    @Column(name = "sector", length = 100)
    private String sector;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE";

    public Security() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCanonicalName() { return canonicalName; }
    public void setCanonicalName(String canonicalName) { this.canonicalName = canonicalName; }
    public String getAssetClass() { return assetClass; }
    public void setAssetClass(String assetClass) { this.assetClass = assetClass; }
    public String getInstrumentType() { return instrumentType; }
    public void setInstrumentType(String instrumentType) { this.instrumentType = instrumentType; }
    public String getIssuerName() { return issuerName; }
    public void setIssuerName(String issuerName) { this.issuerName = issuerName; }
    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
