package com.yukira.backend.ingestion.holdings;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Machine-readable summary for a monthly holdings refresh run across registered mutual funds.
 */
public record MonthlyHoldingsRefreshRun(
    String runId,
    TriggerType triggerType,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    int totalRegisteredFunds,
    int attemptedCount,
    int successCount,
    int duplicateCount,
    int failedCount,
    int skippedCount,
    String overallStatus,
    List<FundRefreshResult> fundResults,
    List<String> auditLogs
) {
    public boolean isSuccessful() {
        return "SUCCESS".equalsIgnoreCase(overallStatus) || "PARTIAL_SUCCESS".equalsIgnoreCase(overallStatus);
    }
}
