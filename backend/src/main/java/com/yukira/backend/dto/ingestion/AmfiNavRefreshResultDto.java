package com.yukira.backend.dto.ingestion;

import java.util.List;

public record AmfiNavRefreshResultDto(
    String status,
    Long sourceArtifactId,
    String sourceHash,
    int totalParsedRows,
    int validRows,
    int observationsIngested,
    int revisionsDetected,
    int duplicatesSkipped,
    int issuesCreated,
    AmfiDataFreshnessDto freshness,
    List<String> messages
) {}
