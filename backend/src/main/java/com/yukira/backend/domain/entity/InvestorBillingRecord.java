package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_billing_record")
public class InvestorBillingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private InvestorSubscription subscription;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "provider", length = 32, nullable = false)
    private String provider = "RAZORPAY";

    @Column(name = "provider_order_id", length = 64)
    private String providerOrderId;

    @Column(name = "provider_payment_id", length = 64)
    private String providerPaymentId;

    @Column(name = "provider_invoice_id", length = 64)
    private String providerInvoiceId;

    @Column(name = "provider_invoice_url", columnDefinition = "TEXT")
    private String providerInvoiceUrl;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Column(name = "amount_inr", precision = 10, scale = 2, nullable = false)
    private BigDecimal amountInr;

    @Column(name = "currency", length = 8, nullable = false)
    private String currency = "INR";

    @Column(name = "payment_status", length = 32, nullable = false)
    private String paymentStatus; // PAID, PENDING, FAILED, REFUNDED

    @Column(name = "billing_reason", length = 64, nullable = false)
    private String billingReason = "INITIAL_CHECKOUT"; // INITIAL_CHECKOUT, RENEWAL, UPGRADE, PLAN_SWITCH

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "period_start")
    private ZonedDateTime periodStart;

    @Column(name = "period_end")
    private ZonedDateTime periodEnd;

    @Column(name = "provider_event_timestamp")
    private ZonedDateTime providerEventTimestamp;

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

    public InvestorBillingRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }
    public InvestorSubscription getSubscription() { return subscription; }
    public void setSubscription(InvestorSubscription subscription) { this.subscription = subscription; }
    public SubscriptionPlan getPlan() { return plan; }
    public void setPlan(SubscriptionPlan plan) { this.plan = plan; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderOrderId() { return providerOrderId; }
    public void setProviderOrderId(String providerOrderId) { this.providerOrderId = providerOrderId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }
    public String getProviderInvoiceId() { return providerInvoiceId; }
    public void setProviderInvoiceId(String providerInvoiceId) { this.providerInvoiceId = providerInvoiceId; }
    public String getProviderInvoiceUrl() { return providerInvoiceUrl; }
    public void setProviderInvoiceUrl(String providerInvoiceUrl) { this.providerInvoiceUrl = providerInvoiceUrl; }
    public Long getAmountPaise() { return amountPaise; }
    public void setAmountPaise(Long amountPaise) { this.amountPaise = amountPaise; }
    public BigDecimal getAmountInr() { return amountInr; }
    public void setAmountInr(BigDecimal amountInr) { this.amountInr = amountInr; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getBillingReason() { return billingReason; }
    public void setBillingReason(String billingReason) { this.billingReason = billingReason; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public ZonedDateTime getPeriodStart() { return periodStart; }
    public void setPeriodStart(ZonedDateTime periodStart) { this.periodStart = periodStart; }
    public ZonedDateTime getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(ZonedDateTime periodEnd) { this.periodEnd = periodEnd; }
    public ZonedDateTime getProviderEventTimestamp() { return providerEventTimestamp; }
    public void setProviderEventTimestamp(ZonedDateTime providerEventTimestamp) { this.providerEventTimestamp = providerEventTimestamp; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorBillingRecord that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
