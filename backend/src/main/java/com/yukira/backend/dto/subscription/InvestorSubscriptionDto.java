package com.yukira.backend.dto.subscription;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record InvestorSubscriptionDto(
        Long id,
        Long investorId,
        String planId,
        String planName,
        String billingInterval,
        BigDecimal priceInr,
        String provider,
        String providerOrderId,
        String providerSubscriptionId,
        String status, // NONE, CHECKOUT_INITIATED, ACTIVE, PAST_DUE, CANCELLED, EXPIRED, PAYMENT_FAILED
        String paymentStatus, // UNPAID, PENDING, PAID, FAILED, REFUNDED
        String entitlementStatus, // STANDARD, ELEVATED_PREVIEW, ACTIVE_PRO, EXPIRED
        boolean cancelAtPeriodEnd,
        ZonedDateTime currentPeriodStart,
        ZonedDateTime currentPeriodEnd,
        boolean providerConfigured,
        String message,
        boolean canReactivate,
        boolean attentionRequired,
        String nextAction,
        String periodEndBehavior
) {
    public InvestorSubscriptionDto(
            Long id,
            Long investorId,
            String planId,
            String planName,
            String billingInterval,
            BigDecimal priceInr,
            String provider,
            String providerOrderId,
            String providerSubscriptionId,
            String status,
            String paymentStatus,
            String entitlementStatus,
            boolean cancelAtPeriodEnd,
            ZonedDateTime currentPeriodStart,
            ZonedDateTime currentPeriodEnd,
            boolean providerConfigured,
            String message
    ) {
        this(
                id,
                investorId,
                planId,
                planName,
                billingInterval,
                priceInr,
                provider,
                providerOrderId,
                providerSubscriptionId,
                status,
                paymentStatus,
                entitlementStatus,
                cancelAtPeriodEnd,
                currentPeriodStart,
                currentPeriodEnd,
                providerConfigured,
                message,
                cancelAtPeriodEnd && currentPeriodEnd != null && currentPeriodEnd.isAfter(ZonedDateTime.now()),
                "FAILED".equalsIgnoreCase(paymentStatus) || "PAST_DUE".equalsIgnoreCase(status),
                cancelAtPeriodEnd ? "REACTIVATE" : "NONE",
                "Reverts to Community Explorer upon expiration."
        );
    }
}
