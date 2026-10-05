package com.yukira.backend.scoring.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only flattened projection over the latest persisted AnalyticalScore per SchemeOption.
 *
 * PURPOSE:
 * Fund discovery surfaces require the latest score of every share class in the expanded
 * universe. Resolving that through the {@code AnalyticalScore} entity graph forces a lazy
 * dereference of {@code schemeOption -> plan -> scheme} for every score row, which issues
 * O(scores) additional SQL statements. This projection flattens those associations into the
 * single selecting statement so no lazy proxy is ever touched.
 *
 * The projection is strictly read-only: it exposes persisted values only and performs no
 * derivation, interpolation or approximation of any financial or analytical figure.
 */
public interface LatestAnalyticalScoreProjection {

    Long getScoreId();

    Long getSchemeOptionId();

    Long getSchemeId();

    String getPlanType();

    String getOptionType();

    BigDecimal getScore();

    BigDecimal getConfidence();

    String getStatus();

    String getScoreVersion();

    String getMethodologyStatus();

    LocalDate getAsOfDate();

    String getSummary();
}
