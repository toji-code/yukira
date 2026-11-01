package com.yukira.backend.dto.portfolio.intelligence;

import java.time.LocalDate;
import java.util.List;

public record PortfolioExposureDriftDto(
    String status,
    String explanation,
    LocalDate earlierSnapshotDate,
    LocalDate latestSnapshotDate,
    List<HoldingDriftItemDto> topHoldingDrifts,
    List<SectorDriftItemDto> sectorDrifts,
    String distinctionNotice
) {}
