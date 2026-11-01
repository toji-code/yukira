package com.yukira.backend.calculation;

import java.io.Serializable;
import java.util.List;

/**
 * Epistemic contract evaluating calculation readiness and governance status (Task 283).
 */
public record MetricAvailabilityContract(
    String metricCode,
    String metricName,
    String methodologyTier, // APPROVED, VALIDATED, CANDIDATE, RETIRED
    MetricAvailabilityState state,
    String reason,
    List<String> requiredInputs,
    List<String> missingInputs
) implements Serializable {}

