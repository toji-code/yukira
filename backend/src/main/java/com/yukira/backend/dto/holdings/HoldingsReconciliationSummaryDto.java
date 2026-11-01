package com.yukira.backend.dto.holdings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

public record HoldingsReconciliationSummaryDto(
    Long id,
    Long sourceArtifactId,
    Long amcId,
    String amcName,
    LocalDate reportingPeriod,
    String parserVersion,
    String parsingOutcome,
    Integer totalRowsRead,
    Integer equityRowsParsed,
    Integer debtRowsParsed,
    Integer cashRowsParsed,
    Integer derivativeRowsParsed,
    Integer unclassifiedRows,
    BigDecimal totalMarketValue,
    BigDecimal derivativeNotionalValue,
    BigDecimal cashMarketValue,
    String errorDiagnostics,
    ZonedDateTime createdAt
) {}

