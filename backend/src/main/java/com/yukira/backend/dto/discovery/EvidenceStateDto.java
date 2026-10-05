package com.yukira.backend.dto.discovery;

import java.util.List;

/**
 * Epistemic evidence state and data recency indicators for a discovered fund.
 */
public record EvidenceStateDto(
    String navAsOfDate,
    String enrichmentAsOfDate,
    String dataQualitySummary,
    List<String> qualityFlags
) {}
