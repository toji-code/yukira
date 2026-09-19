package com.yukira.backend.ingestion.amfi;

import java.util.List;

public record IngestionSummary(
    Long sourceArtifactId,
    String sha256Hash,
    int totalRowsParsed,
    int validRowsCount,
    int observationsIngested,
    int revisionsCreated,
    int duplicateRowsSkipped,
    int validationIssuesCreated,
    List<String> messages
) {}
