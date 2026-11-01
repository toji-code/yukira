package com.yukira.backend.dto.portfolio;

/**
 * Structured portfolio investigation prompt distinguishing factual observation,
 * analytical context/interpretation, and suggested investor action.
 */
public record PortfolioInvestigationItemDto(
    String category,
    String fact,
    String interpretation,
    String suggestedAction,
    Long schemeOptionId,
    String fundName
) {}
