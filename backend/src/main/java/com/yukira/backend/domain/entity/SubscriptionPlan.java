package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "subscription_plan")
public class SubscriptionPlan {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_inr", precision = 10, scale = 2, nullable = false)
    private BigDecimal priceInr;

    @Column(name = "billing_interval", length = 16, nullable = false)
    private String billingInterval;

    @Column(name = "max_portfolios", nullable = false)
    private int maxPortfolios = 1;

    @Column(name = "max_holdings_per_portfolio", nullable = false)
    private int maxHoldingsPerPortfolio = 20;

    @Column(name = "max_saved_funds", nullable = false)
    private int maxSavedFunds = 25;

    @Column(name = "can_import_cas", nullable = false)
    private boolean canImportCas = false;

    @Column(name = "can_access_api", nullable = false)
    private boolean canAccessApi = false;

    @Column(name = "api_rate_limit_per_min", nullable = false)
    private int apiRateLimitPerMin = 0;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

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

    public SubscriptionPlan() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPriceInr() { return priceInr; }
    public void setPriceInr(BigDecimal priceInr) { this.priceInr = priceInr; }
    public String getBillingInterval() { return billingInterval; }
    public void setBillingInterval(String billingInterval) { this.billingInterval = billingInterval; }
    public int getMaxPortfolios() { return maxPortfolios; }
    public void setMaxPortfolios(int maxPortfolios) { this.maxPortfolios = maxPortfolios; }
    public int getMaxHoldingsPerPortfolio() { return maxHoldingsPerPortfolio; }
    public void setMaxHoldingsPerPortfolio(int maxHoldingsPerPortfolio) { this.maxHoldingsPerPortfolio = maxHoldingsPerPortfolio; }
    public int getMaxSavedFunds() { return maxSavedFunds; }
    public void setMaxSavedFunds(int maxSavedFunds) { this.maxSavedFunds = maxSavedFunds; }
    public boolean isCanImportCas() { return canImportCas; }
    public void setCanImportCas(boolean canImportCas) { this.canImportCas = canImportCas; }
    public boolean isCanAccessApi() { return canAccessApi; }
    public void setCanAccessApi(boolean canAccessApi) { this.canAccessApi = canAccessApi; }
    public int getApiRateLimitPerMin() { return apiRateLimitPerMin; }
    public void setApiRateLimitPerMin(int apiRateLimitPerMin) { this.apiRateLimitPerMin = apiRateLimitPerMin; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubscriptionPlan that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
