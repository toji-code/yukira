package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "scheme_data_coverage")
public class SchemeDataCoverage implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", nullable = false, unique = true)
    private Scheme scheme;

    @Column(name = "nav_coverage_status", nullable = false, length = 32)
    private String navCoverageStatus = "SUPPORTED"; // SUPPORTED, INCOMPLETE, UNSUPPORTED

    @Column(name = "benchmark_coverage_status", nullable = false, length = 32)
    private String benchmarkCoverageStatus = "SUPPORTED";

    @Column(name = "holdings_coverage_status", nullable = false, length = 32)
    private String holdingsCoverageStatus = "INCOMPLETE";

    @Column(name = "risk_free_coverage_status", nullable = false, length = 32)
    private String riskFreeCoverageStatus = "SUPPORTED";

    @Column(name = "peer_universe_status", nullable = false, length = 32)
    private String peerUniverseStatus = "SUPPORTED";

    @Column(name = "earliest_nav_date")
    private LocalDate earliestNavDate;

    @Column(name = "latest_nav_date")
    private LocalDate latestNavDate;

    @Column(name = "latest_holdings_period")
    private LocalDate latestHoldingsPeriod;

    @Column(name = "latest_nav_availability")
    private ZonedDateTime latestNavAvailability;

    @Column(name = "data_limitations_summary", columnDefinition = "text")
    private String dataLimitationsSummary;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public SchemeDataCoverage() {}

    @PrePersist
    @PreUpdate
    protected void onPersist() {
        updatedAt = ZonedDateTime.now();
        if (navCoverageStatus == null) navCoverageStatus = "SUPPORTED";
        if (benchmarkCoverageStatus == null) benchmarkCoverageStatus = "SUPPORTED";
        if (holdingsCoverageStatus == null) holdingsCoverageStatus = "INCOMPLETE";
        if (riskFreeCoverageStatus == null) riskFreeCoverageStatus = "SUPPORTED";
        if (peerUniverseStatus == null) peerUniverseStatus = "SUPPORTED";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }

    public String getNavCoverageStatus() { return navCoverageStatus; }
    public void setNavCoverageStatus(String navCoverageStatus) { this.navCoverageStatus = navCoverageStatus; }

    public String getBenchmarkCoverageStatus() { return benchmarkCoverageStatus; }
    public void setBenchmarkCoverageStatus(String benchmarkCoverageStatus) { this.benchmarkCoverageStatus = benchmarkCoverageStatus; }

    public String getHoldingsCoverageStatus() { return holdingsCoverageStatus; }
    public void setHoldingsCoverageStatus(String holdingsCoverageStatus) { this.holdingsCoverageStatus = holdingsCoverageStatus; }

    public String getRiskFreeCoverageStatus() { return riskFreeCoverageStatus; }
    public void setRiskFreeCoverageStatus(String riskFreeCoverageStatus) { this.riskFreeCoverageStatus = riskFreeCoverageStatus; }

    public String getPeerUniverseStatus() { return peerUniverseStatus; }
    public void setPeerUniverseStatus(String peerUniverseStatus) { this.peerUniverseStatus = peerUniverseStatus; }

    public LocalDate getEarliestNavDate() { return earliestNavDate; }
    public void setEarliestNavDate(LocalDate earliestNavDate) { this.earliestNavDate = earliestNavDate; }

    public LocalDate getLatestNavDate() { return latestNavDate; }
    public void setLatestNavDate(LocalDate latestNavDate) { this.latestNavDate = latestNavDate; }

    public LocalDate getLatestHoldingsPeriod() { return latestHoldingsPeriod; }
    public void setLatestHoldingsPeriod(LocalDate latestHoldingsPeriod) { this.latestHoldingsPeriod = latestHoldingsPeriod; }

    public ZonedDateTime getLatestNavAvailability() { return latestNavAvailability; }
    public void setLatestNavAvailability(ZonedDateTime latestNavAvailability) { this.latestNavAvailability = latestNavAvailability; }

    public String getDataLimitationsSummary() { return dataLimitationsSummary; }
    public void setDataLimitationsSummary(String dataLimitationsSummary) { this.dataLimitationsSummary = dataLimitationsSummary; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchemeDataCoverage that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

