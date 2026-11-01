package com.yukira.backend.dto.workspace;

import java.time.ZonedDateTime;

public record InvestorResearchTimelineEventDto(
    String eventId,
    String eventType, // WATCHLIST_ADDED, THESIS_UPDATED, COMPARISON_SAVED, SNAPSHOT_CREATED, STRESS_FINDING, MONITORING_TRIGGER, ACTION_COMPLETED
    String title,
    String summary,
    String targetType,
    String targetId,
    String referenceUrl,
    ZonedDateTime occurredAt
) {}

