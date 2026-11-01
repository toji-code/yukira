package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_subscription")
public class InvestorSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false, unique = true)
    private Investor investor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "provider", length = 32, nullable = false)
    private String provider = "RAZORPAY";

    @Column(name = "provider_customer_id", length = 64)
    private String providerCustomerId;

    @Column(name = "provider_subscription_id", length = 64)
    private String providerSubscriptionId;

    @Column(name = "provider_order_id", length = 64)
    private String providerOrderId;

    @Column(name = "provider_payment_id", length = 64)
    private String providerPaymentId;

    @Column(name = "status", length = 32, nullable = false)
    private String status = "NONE"; // NONE, CHECKOUT_INITIATED, ACTIVE, PAST_DUE, CANCELLED, EXPIRED, PAYMENT_FAILED

    @Column(name = "payment_status", length = 32, nullable = false)
    private String paymentStatus = "UNPAID"; // UNPAID, PENDING, PAID, FAILED, REFUNDED

    @Column(name = "entitlement_status", length = 32, nullable = false)
    private String entitlementStatus = "STANDARD"; // STANDARD, ELEVATED_PREVIEW, ACTIVE_PRO, EXPIRED

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd = false;

    @Column(name = "current_period_start")
    private ZonedDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private ZonedDateTime currentPeriodEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    public InvestorSubscription() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }
    public SubscriptionPlan getPlan() { return plan; }
    public void setPlan(SubscriptionPlan plan) { this.plan = plan; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderCustomerId() { return providerCustomerId; }
    public void setProviderCustomerId(String providerCustomerId) { this.providerCustomerId = providerCustomerId; }
    public String getProviderSubscriptionId() { return providerSubscriptionId; }
    public void setProviderSubscriptionId(String providerSubscriptionId) { this.providerSubscriptionId = providerSubscriptionId; }
    public String getProviderOrderId() { return providerOrderId; }
    public void setProviderOrderId(String providerOrderId) { this.providerOrderId = providerOrderId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getEntitlementStatus() { return entitlementStatus; }
    public void setEntitlementStatus(String entitlementStatus) { this.entitlementStatus = entitlementStatus; }
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public void setCancelAtPeriodEnd(boolean cancelAtPeriodEnd) { this.cancelAtPeriodEnd = cancelAtPeriodEnd; }
    public ZonedDateTime getCurrentPeriodStart() { return currentPeriodStart; }
    public void setCurrentPeriodStart(ZonedDateTime currentPeriodStart) { this.currentPeriodStart = currentPeriodStart; }
    public ZonedDateTime getCurrentPeriodEnd() { return currentPeriodEnd; }
    public void setCurrentPeriodEnd(ZonedDateTime currentPeriodEnd) { this.currentPeriodEnd = currentPeriodEnd; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorSubscription that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
