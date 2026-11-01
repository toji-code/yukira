package com.yukira.backend.dto.telemetry;

import java.time.ZonedDateTime;
import java.util.Map;

public record V1PlatformReadinessDto(
    String platformVersion,
    String releaseStatus, // READY, DEGRADED, BLOCKED
    String methodologyGovernanceStatus, // FROZEN_PHASE_2H
    String baselineOperationalMetric, // RET-02
    int totalCandidateMetricsCount,
    boolean nonAdvisorySafetyEnforced,
    boolean bitemporalPitEnforced,
    boolean deterministicBackendAuthoritative,
    Map<String, String> subsystemHealth,
    ZonedDateTime evaluatedAt
) {}

