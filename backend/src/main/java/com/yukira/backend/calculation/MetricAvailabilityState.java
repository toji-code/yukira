package com.yukira.backend.calculation;

/**
 * Authoritative quantitative withholding taxonomy (Task 283).
 * Explicitly decouples data unavailability or statutory non-computation
 * from synthetic approximation or numerical zeroes.
 */
public enum MetricAvailabilityState {
    AVAILABLE,
    INSUFFICIENT_DATA,
    MISSING_BENCHMARK,
    MISSING_SOURCE,
    STALE_DATA,
    INCOMPATIBLE_SERIES,
    INVALID_INPUT,
    NOT_IMPLEMENTED,
    CANDIDATE_METHOD
}

