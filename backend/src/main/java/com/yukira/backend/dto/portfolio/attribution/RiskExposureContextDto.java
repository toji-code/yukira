package com.yukira.backend.dto.portfolio.attribution;

import java.math.BigDecimal;
import java.util.List;

public record RiskExposureContextDto(
    List<AttributionSectorExposureDto> sectors,
    List<AttributionMarketCapExposureDto> marketCaps,
    List<AttributionSecurityExposureDto> topSecurities,
    BigDecimal disclosedEquityCoveragePct,
    BigDecimal unclassifiedWeightPct,
    BigDecimal cashAndDebtWeightPct,
    List<String> exposureDisclaimers
) {}
