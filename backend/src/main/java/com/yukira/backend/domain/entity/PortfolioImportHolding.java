package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "portfolio_import_holding")
public class PortfolioImportHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_import_id", nullable = false)
    private PortfolioImport portfolioImport;

    @Column(name = "raw_scheme_name", nullable = false)
    private String rawSchemeName;

    @Column(name = "folio_number")
    private String folioNumber;

    @Column(name = "isin")
    private String isin;

    @Column(name = "amfi_code")
    private String amfiCode;

    @Column(name = "units", nullable = false, precision = 19, scale = 4)
    private BigDecimal units;

    @Column(name = "cost_basis_amount", precision = 19, scale = 4)
    private BigDecimal costBasisAmount;

    @Column(name = "market_value", precision = 19, scale = 4)
    private BigDecimal marketValue;

    @Column(name = "nav", precision = 19, scale = 4)
    private BigDecimal nav;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_scheme_option_id")
    private SchemeOption resolvedSchemeOption;

    @Column(name = "resolution_status", nullable = false)
    private String resolutionStatus = "UNRESOLVED"; // RESOLVED, AMBIGUOUS, UNRESOLVED, INVALID

    @Column(name = "resolution_method", nullable = false)
    private String resolutionMethod = "UNRESOLVED"; // ISIN_EXACT, AMFI_EXACT, NAME_EXACT, UNRESOLVED

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
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

    public BigDecimal getUnits() { return units; }
    public void setUnits(BigDecimal units) { this.units = units; }

    public BigDecimal getCostBasisAmount() { return costBasisAmount; }
    public void setCostBasisAmount(BigDecimal costBasisAmount) { this.costBasisAmount = costBasisAmount; }

    public BigDecimal getMarketValue() { return marketValue; }
    public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }

    public BigDecimal getNav() { return nav; }
    public void setNav(BigDecimal nav) { this.nav = nav; }

    public SchemeOption getResolvedSchemeOption() { return resolvedSchemeOption; }
    public void setResolvedSchemeOption(SchemeOption resolvedSchemeOption) { this.resolvedSchemeOption = resolvedSchemeOption; }

    public String getResolutionStatus() { return resolutionStatus; }
    public void setResolutionStatus(String resolutionStatus) { this.resolutionStatus = resolutionStatus; }

    public String getResolutionMethod() { return resolutionMethod; }
    public void setResolutionMethod(String resolutionMethod) { this.resolutionMethod = resolutionMethod; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
}
