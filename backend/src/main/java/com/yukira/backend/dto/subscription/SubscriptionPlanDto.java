package com.yukira.backend.dto.subscription;

import java.math.BigDecimal;

public record SubscriptionPlanDto(
        String id,
        String name,
        String description,
        BigDecimal priceInr,
        String billingInterval,
        int maxPortfolios,
        int maxHoldingsPerPortfolio,
        int maxSavedFunds,
        boolean canImportCas,
        boolean canAccessApi,
        int apiRateLimitPerMin,
        boolean isActive
) {}
