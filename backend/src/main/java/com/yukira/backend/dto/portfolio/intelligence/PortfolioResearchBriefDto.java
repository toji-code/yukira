package com.yukira.backend.dto.portfolio.intelligence;

import java.util.List;
import java.util.Map;

public record PortfolioResearchBriefDto(
    String summary,
    List<PortfolioResearchFindingDto> findings,
    List<String> investigationPrompts,
    List<String> dataLimitations,
    Map<String, String> epistemicDisclosure
) {}
