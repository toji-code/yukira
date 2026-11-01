package com.yukira.backend.dto.portfolio.intelligence;

import java.util.List;

public record PortfolioRiskContextDto(
    String status,
    String governanceNotice,
    List<ConstituentFundRiskProfileDto> fundRiskProfiles
) {}
