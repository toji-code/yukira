package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_notification_preference")
public class InvestorNotificationPreference implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false, unique = true)
    private Investor investor;

    @Column(name = "email_enabled", nullable = false)
    private Boolean emailEnabled = true;

    @Column(name = "in_app_enabled", nullable = false)
    private Boolean inAppEnabled = true;

    @Column(name = "webhook_enabled", nullable = false)
    private Boolean webhookEnabled = false;

    @Column(name = "email_address")
    private String emailAddress;

    @Column(name = "monitoring_triggers_enabled", nullable = false)
    private Boolean monitoringTriggersEnabled = true;

    @Column(name = "research_updates_enabled", nullable = false)
    private Boolean researchUpdatesEnabled = true;

    @Column(name = "data_quality_alerts_enabled", nullable = false)
    private Boolean dataQualityAlertsEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public InvestorNotificationPreference() {}

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
        if (emailEnabled == null) emailEnabled = true;
        if (inAppEnabled == null) inAppEnabled = true;
        if (webhookEnabled == null) webhookEnabled = false;
        if (monitoringTriggersEnabled == null) monitoringTriggersEnabled = true;
        if (researchUpdatesEnabled == null) researchUpdatesEnabled = true;
        if (dataQualityAlertsEnabled == null) dataQualityAlertsEnabled = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public Boolean getEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(Boolean emailEnabled) { this.emailEnabled = emailEnabled; }

    public Boolean getInAppEnabled() { return inAppEnabled; }
    public void setInAppEnabled(Boolean inAppEnabled) { this.inAppEnabled = inAppEnabled; }

    public Boolean getWebhookEnabled() { return webhookEnabled; }
    public void setWebhookEnabled(Boolean webhookEnabled) { this.webhookEnabled = webhookEnabled; }

    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }

    public Boolean getMonitoringTriggersEnabled() { return monitoringTriggersEnabled; }
    public void setMonitoringTriggersEnabled(Boolean monitoringTriggersEnabled) { this.monitoringTriggersEnabled = monitoringTriggersEnabled; }

    public Boolean getResearchUpdatesEnabled() { return researchUpdatesEnabled; }
    public void setResearchUpdatesEnabled(Boolean researchUpdatesEnabled) { this.researchUpdatesEnabled = researchUpdatesEnabled; }

    public Boolean getDataQualityAlertsEnabled() { return dataQualityAlertsEnabled; }
    public void setDataQualityAlertsEnabled(Boolean dataQualityAlertsEnabled) { this.dataQualityAlertsEnabled = dataQualityAlertsEnabled; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorNotificationPreference that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

