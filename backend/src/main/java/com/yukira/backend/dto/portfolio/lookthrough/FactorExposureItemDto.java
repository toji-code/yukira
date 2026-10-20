package com.yukira.backend.dto.portfolio.lookthrough;

/**
 * Economic factor exposure item with explicit epistemic status.
 * Distinguishes holdings-based proxies from empirical factor regressions and gated models.
 */
public record FactorExposureItemDto(
    String factorCode,
    String factorName,
    String category,
    String exposureValue,
    String status,
    String economicDefinition,
    String requiredInputs,
    String calculationMethod,
    String evidenceNotes
) {}
