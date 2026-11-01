package com.yukira.backend.dto.notification;

import java.time.ZonedDateTime;

public record InvestorWebhookConfigDto(
    Long id,
    String endpointUrl,
    String subscriptionEventsCsv,
    Boolean isActive,
    String status,
    Integer failureCount,
    ZonedDateTime createdAt
) {}

