package com.yukira.backend.dto.portfolio.intelligence;

public record PortfolioResearchFindingDto(
    String findingId,
    String category,
    String title,
    String observedFact,
    String calculatedExposure,
    String interpretation,
    String riskOrLimitation,
    String unknownInformation,
    String followUpCategory,
    String defaultQuestion
) {}
