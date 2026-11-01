package com.yukira.backend.dto.portfolio.intelligence;

import com.yukira.backend.dto.portfolio.lookthrough.SectorTopSecurityDto;
import java.math.BigDecimal;
import java.util.List;

public record PortfolioSectorExposureDto(
    String sectorName,
    BigDecimal exposureValue,
    BigDecimal portfolioWeightPct,
    BigDecimal knownHoldingsWeightPct,
    int underlyingSecuritiesCount,
    int contributingFundsCount,
    List<String> contributingFunds,
    List<SectorTopSecurityDto> topSecurities
) {}
