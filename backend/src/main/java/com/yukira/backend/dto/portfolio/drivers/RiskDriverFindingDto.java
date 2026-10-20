package com.yukira.backend.dto.portfolio.drivers;

/**
 * Deterministic diagnostic finding generated from empirical portfolio concentration and overlap metrics.
 * Provides transparent observed values, explicit thresholds, explanations, and actionable allocator investigation prompts.
 */
public record RiskDriverFindingDto(
    String id,
    String category,
    String severity, // "HIGH", "MEDIUM", "LOW", "INFO", "WARNING"
    String title,
    String observedValue,
    String threshold,
    String explanation,
    String investigationPrompt,
    String evidence
) {}
