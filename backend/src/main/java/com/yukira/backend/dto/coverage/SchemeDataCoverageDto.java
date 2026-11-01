package com.yukira.backend.dto.coverage;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public record SchemeDataCoverageDto(
    Long id,
    Long schemeId,
    String schemeName,
    String navCoverageStatus,
    String benchmarkCoverageStatus,
    String holdingsCoverageStatus,
    String riskFreeCoverageStatus,
    String peerUniverseStatus,
    LocalDate earliestNavDate,
    LocalDate latestNavDate,
    LocalDate latestHoldingsPeriod,
    ZonedDateTime latestNavAvailability,
    String dataLimitationsSummary,
    ZonedDateTime updatedAt
) {}

