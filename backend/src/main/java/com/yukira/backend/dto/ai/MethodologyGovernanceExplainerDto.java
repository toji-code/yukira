package com.yukira.backend.dto.ai;

import java.util.List;

public record MethodologyGovernanceExplainerDto(
    String metricCode,
    String metricName,
    String methodologyStatus, // CANDIDATE, VALIDATED, APPROVED, RETIRED
    String description,
    String mathematicalFormula,
    String requiredInputsSummary,
    String minimumHorizonRequired,
    String governanceNote,
    List<String> regulatoryReferences
) {}

