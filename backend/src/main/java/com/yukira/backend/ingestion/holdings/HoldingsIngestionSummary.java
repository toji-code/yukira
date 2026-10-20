package com.yukira.backend.ingestion.holdings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ingestion summary for automated monthly holdings pipeline.
 */
public record HoldingsIngestionSummary(
    Long artifactId,
    String sha256Hash,
    Long schemeOptionId,
    LocalDate portfolioDate,
    int totalRowsParsed,
    int validRowsParsed,
    int holdingsIngested,
    int revisionsCreated,
    int validationIssuesCreated,
    BigDecimal sumReportedWeights,
    List<String> messages
) {}
