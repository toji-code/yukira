package com.yukira.backend.dto.monitoring;

import java.time.ZonedDateTime;

public record MonitoringEventNoteDto(
    Long id,
    Long eventId,
    String noteText,
    String author,
    ZonedDateTime createdAt
) {}

