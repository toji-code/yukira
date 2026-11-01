package com.yukira.backend.dto.ai;

import java.util.List;

/**
 * Structured Evidence Claim for Grounded AI Interpretation Layer.
 *
 * Epistemic Contract:
 * - Every material factual claim must be traceable to one or more supplied authoritative metrics.
 * - Distinguishes between factual observations, analytical interpretations, risk factors,
 *   governance limitations, and unverified hypotheses.
 * - Enforces zero LLM-invented metrics or financial numbers.
 */
public record EvidenceClaimDto(
    String claimText,
    String claimType, // "OBSERVATION", "INTERPRETATION", "RISK", "LIMITATION", "HYPOTHESIS"
    List<String> supportingMetricCodes,
    String horizon,
    String methodologyStatus
) {
    public EvidenceClaimDto {
        if (supportingMetricCodes == null) {
            supportingMetricCodes = List.of();
        }
        if (claimType == null || claimType.isBlank()) {
            claimType = "OBSERVATION";
        }
    }
}
