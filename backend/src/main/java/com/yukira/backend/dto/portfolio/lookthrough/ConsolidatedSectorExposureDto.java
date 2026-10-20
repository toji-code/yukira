package com.yukira.backend.dto.portfolio.lookthrough;

import java.math.BigDecimal;
import java.util.List;

public record ConsolidatedSectorExposureDto(
    String macroSector,
    BigDecimal consolidatedWeightPct,
    int classifiedSecuritiesCount,
    List<FundSectorContributionDto> fundContributions,
    String classificationProvenance,
    List<SectorTopSecurityDto> topSecurities
) {
    public ConsolidatedSectorExposureDto(
        String macroSector,
        BigDecimal consolidatedWeightPct,
        int classifiedSecuritiesCount,
        List<FundSectorContributionDto> fundContributions,
        String classificationProvenance
    ) {
        this(macroSector, consolidatedWeightPct, classifiedSecuritiesCount, fundContributions, classificationProvenance, List.of());
    }
}
