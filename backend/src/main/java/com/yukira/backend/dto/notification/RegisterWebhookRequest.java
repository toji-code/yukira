package com.yukira.backend.dto.notification;

public record RegisterWebhookRequest(
    String endpointUrl,
    String secretKey,
    String subscriptionEventsCsv
) {}

