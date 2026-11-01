package com.yukira.backend.dto.subscription;

public record SubscriptionActionResponse(
        String status, // SUCCESS, SCHEDULED, INFO, ERROR
        String message,
        InvestorSubscriptionDto subscription
) {}
