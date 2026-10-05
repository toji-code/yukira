package com.yukira.backend.scoring.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Compact read-only snapshot of YUKIRA Analytical Quality Score for Fund Discovery and Comparison.
 * Exposes core analytical fields and optional dimension summaries without loading full metric trees.
 */
public record YukiraScoreSummary(
    Long scoreId,
    Long schemeOptionId,
    BigDecimal score,
    BigDecimal confidence,
    String status,
    String scoreVersion,
    String methodologyStatus,
    LocalDate asOfDate,
    String summary,
    List<AnalyticalScoreResponse.DimensionScoreDto> dimensions
) implements Serializable {
    public YukiraScoreSummary(
        Long scoreId,
        Long schemeOptionId,
        BigDecimal score,
        BigDecimal confidence,
        String status,
        String scoreVersion,
        String methodologyStatus,
        LocalDate asOfDate,
        String summary
    ) {
        this(scoreId, schemeOptionId, score, confidence, status, scoreVersion, methodologyStatus, asOfDate, summary, null);
    }
}

