package com.yukira.backend.dto.monitoring;

public record MonitoringLifecycleUpdateRequest(
    String lifecycleStatus, // ACKNOWLEDGED, INVESTIGATING, RESOLVED, DISMISSED, NEW
    String resolutionNote,
    String investigationNote
) {}

