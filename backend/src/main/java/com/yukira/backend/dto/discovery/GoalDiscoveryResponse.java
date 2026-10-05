package com.yukira.backend.dto.discovery;

import java.util.List;

/**
 * Complete evaluation response for goal-based mutual fund discovery.
 */
public record GoalDiscoveryResponse(
    GoalDiscoveryRequest normalizedRequirements,
    int totalEvaluated,
    List<GoalDiscoveryResultDto> results
) {}
