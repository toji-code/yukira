package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_webhook_config")
public class InvestorWebhookConfig implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @Column(name = "endpoint_url", nullable = false, length = 1024)
    private String endpointUrl;

    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @Column(name = "subscription_events_csv", nullable = false)
    private String subscriptionEventsCsv = "MONITORING_TRIGGER,CONDITION_STATUS_CHANGE";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "failure_count", nullable = false)
    private Integer failureCount = 0;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE"; // ACTIVE, SUSPENDED, INACTIVE

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public InvestorWebhookConfig() {}

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
        if (isActive == null) isActive = true;
        if (failureCount == null) failureCount = 0;
        if (status == null) status = "ACTIVE";
        if (subscriptionEventsCsv == null) subscriptionEventsCsv = "MONITORING_TRIGGER,CONDITION_STATUS_CHANGE";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public String getSecretHash() { return secretHash; }
    public void setSecretHash(String secretHash) { this.secretHash = secretHash; }

    public String getSubscriptionEventsCsv() { return subscriptionEventsCsv; }
    public void setSubscriptionEventsCsv(String subscriptionEventsCsv) { this.subscriptionEventsCsv = subscriptionEventsCsv; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Integer getFailureCount() { return failureCount; }
    public void setFailureCount(Integer failureCount) { this.failureCount = failureCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorWebhookConfig that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

