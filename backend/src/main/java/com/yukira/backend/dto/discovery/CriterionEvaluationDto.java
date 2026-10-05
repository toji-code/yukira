package com.yukira.backend.dto.discovery;

/**
 * Result of evaluating a single explicit discovery criterion.
 */
public record CriterionEvaluationDto(
    String criterion,
    String state,
    String explanation
) {}
