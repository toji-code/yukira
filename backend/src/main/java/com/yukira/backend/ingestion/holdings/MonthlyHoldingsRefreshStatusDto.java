package com.yukira.backend.ingestion.holdings;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Operational status DTO exposing the health, execution history, and pilot freshness of the monthly holdings refresh workflow.
 */
public record MonthlyHoldingsRefreshStatusDto(
    boolean schedulerEnabled,
    String configuredCron,
    OffsetDateTime serverTime,
    boolean isCurrentlyRunning,
    MonthlyHoldingsRefreshRun lastAttemptedRun,
    MonthlyHoldingsRefreshRun lastSuccessfulRun,
    List<PilotFundHoldingsStatusDto> pilotFundsStatus
) {}
