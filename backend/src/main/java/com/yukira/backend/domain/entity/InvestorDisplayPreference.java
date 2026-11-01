package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_display_preference")
public class InvestorDisplayPreference implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false, unique = true)
    private Investor investor;

    @Column(name = "default_analysis_period", nullable = false, length = 16)
    private String defaultAnalysisPeriod = "3Y"; // 1Y, 3Y, 5Y, ALL

    @Column(name = "default_workspace_tab", nullable = false, length = 32)
    private String defaultWorkspaceTab = "OVERVIEW"; // OVERVIEW, WATCHLIST, PORTFOLIO, COMPARISONS, MONITORING, ACTIONS, TIMELINE

    @Column(name = "preferred_theme", nullable = false, length = 16)
    private String preferredTheme = "DARK"; // DARK, LIGHT

    @Column(name = "show_candidate_metric_warnings", nullable = false)
    private Boolean showCandidateMetricWarnings = true;

    @Column(name = "date_display_format", nullable = false, length = 32)
    private String dateDisplayFormat = "YYYY-MM-DD";

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public InvestorDisplayPreference() {}

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
        if (defaultAnalysisPeriod == null) defaultAnalysisPeriod = "3Y";
        if (defaultWorkspaceTab == null) defaultWorkspaceTab = "OVERVIEW";
        if (preferredTheme == null) preferredTheme = "DARK";
        if (showCandidateMetricWarnings == null) showCandidateMetricWarnings = true;
        if (dateDisplayFormat == null) dateDisplayFormat = "YYYY-MM-DD";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public String getDefaultAnalysisPeriod() { return defaultAnalysisPeriod; }
    public void setDefaultAnalysisPeriod(String defaultAnalysisPeriod) { this.defaultAnalysisPeriod = defaultAnalysisPeriod; }

    public String getDefaultWorkspaceTab() { return defaultWorkspaceTab; }
    public void setDefaultWorkspaceTab(String defaultWorkspaceTab) { this.defaultWorkspaceTab = defaultWorkspaceTab; }

    public String getPreferredTheme() { return preferredTheme; }
    public void setPreferredTheme(String preferredTheme) { this.preferredTheme = preferredTheme; }

    public Boolean getShowCandidateMetricWarnings() { return showCandidateMetricWarnings; }
    public void setShowCandidateMetricWarnings(Boolean showCandidateMetricWarnings) { this.showCandidateMetricWarnings = showCandidateMetricWarnings; }

    public String getDateDisplayFormat() { return dateDisplayFormat; }
    public void setDateDisplayFormat(String dateDisplayFormat) { this.dateDisplayFormat = dateDisplayFormat; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorDisplayPreference that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

