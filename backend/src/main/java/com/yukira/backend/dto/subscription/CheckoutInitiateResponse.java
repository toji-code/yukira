package com.yukira.backend.dto.subscription;

public record CheckoutInitiateResponse(
        String status, // SUCCESS, PROVIDER_UNCONFIGURED, ERROR
        String orderId,
        Long amountPaise,
        String currency,
        String keyId,
        String planId,
        String planName,
        boolean providerConfigured,
        String message
) {
    public static CheckoutInitiateResponse unconfigured(String planId, String planName, String message) {
        return new CheckoutInitiateResponse(
                "PROVIDER_UNCONFIGURED",
                null,
                null,
                "INR",
                null,
                planId,
                planName,
                false,
                message
        );
    }

    public static CheckoutInitiateResponse success(String orderId, Long amountPaise, String currency, String keyId, String planId, String planName) {
        return new CheckoutInitiateResponse(
                "SUCCESS",
                orderId,
                amountPaise,
                currency,
                keyId,
                planId,
                planName,
                true,
                "Checkout session successfully created."
        );
    }
}
