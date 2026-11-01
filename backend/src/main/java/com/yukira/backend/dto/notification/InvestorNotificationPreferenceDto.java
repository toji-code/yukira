package com.yukira.backend.dto.notification;

public record InvestorNotificationPreferenceDto(
    Boolean emailEnabled,
    Boolean inAppEnabled,
    Boolean webhookEnabled,
    String emailAddress,
    Boolean monitoringTriggersEnabled,
    Boolean researchUpdatesEnabled,
    Boolean dataQualityAlertsEnabled
) {}

