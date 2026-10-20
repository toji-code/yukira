package com.yukira.backend.dto.ingestion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record HistoricalBackfillPreviewDto(
    int totalSelected,
    int amcCount,
    int directCount,
    int regularCount,
    int growthCount,
    int idcwCount,
    Map<String, Integer> tierBreakdown,
    Map<String, Integer> amcBreakdown,
    int estimatedChunkHttpRequests,
    boolean isDryRun,
    List<SchemeOptionPreviewItem> selectedOptions,
    long totalActiveOptions,
    long candidateCount3Y,
    long already1YCount,
    long lessThan1YCount,
    long zeroNavCount,
    long already3YCount,
    long capable3YCount,
    long incapableInceptionCount,
    Map<String, Integer> topUnderCoveredAmcs,
    long estimatedDbInsertions,
    // 1Y Readiness Funnel Breakdown
    long ready5YCount,
    long ready3YCount,
    long ready1YCount,
    long days180To364Count,
    long days90To179Count,
    long days1To89Count,
    long shortInception1YCount,
    long capable1YCount,
    // Zero-NAV Root Cause Breakdown
    long zeroNavNewFundCount,
    long zeroNavSourceNotIngestedCount,
    long zeroNavSourceNoHistoryCount,
    long zeroNavIdentityMappingCount,
    long zeroNavCatalogQualityCount,
    long zeroNavOtherCount
) {
    public HistoricalBackfillPreviewDto(
        int totalSelected,
        int amcCount,
        int directCount,
        int regularCount,
        int growthCount,
        int idcwCount,
        Map<String, Integer> tierBreakdown,
        Map<String, Integer> amcBreakdown,
        int estimatedChunkHttpRequests,
        boolean isDryRun,
        List<SchemeOptionPreviewItem> selectedOptions
    ) {
        this(
            totalSelected, amcCount, directCount, regularCount, growthCount, idcwCount,
            tierBreakdown, amcBreakdown, estimatedChunkHttpRequests, isDryRun, selectedOptions,
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, Map.of(), 0L,
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L, 0L
        );
    }

    public HistoricalBackfillPreviewDto(
        int totalSelected,
        int amcCount,
        int directCount,
        int regularCount,
        int growthCount,
        int idcwCount,
        Map<String, Integer> tierBreakdown,
        Map<String, Integer> amcBreakdown,
        int estimatedChunkHttpRequests,
        boolean isDryRun,
        List<SchemeOptionPreviewItem> selectedOptions,
        long totalActiveOptions,
        long candidateCount3Y,
        long already1YCount,
        long lessThan1YCount,
        long zeroNavCount,
        long already3YCount,
        long capable3YCount,
        long incapableInceptionCount,
        Map<String, Integer> topUnderCoveredAmcs,
        long estimatedDbInsertions
    ) {
        this(
            totalSelected, amcCount, directCount, regularCount, growthCount, idcwCount,
            tierBreakdown, amcBreakdown, estimatedChunkHttpRequests, isDryRun, selectedOptions,
            totalActiveOptions, candidateCount3Y, already1YCount, lessThan1YCount, zeroNavCount,
            already3YCount, capable3YCount, incapableInceptionCount, topUnderCoveredAmcs, estimatedDbInsertions,
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L, 0L
        );
    }

    public record SchemeOptionPreviewItem(
        Long schemeOptionId,
        String amfiCode,
        String schemeName,
        String amcName,
        String planType,
        String optionType,
        String tier,
        long currentObservationsCount,
        LocalDate minDate,
        LocalDate maxDate,
        double currentHorizonYears,
        double targetHorizonYears,
        long shortfallDays,
        boolean is3YCapable,
        String inceptionStatus
    ) {
        public SchemeOptionPreviewItem(
            Long schemeOptionId,
            String amfiCode,
            String schemeName,
            String amcName,
            String planType,
            String optionType,
            String tier,
            long currentObservationsCount,
            LocalDate minDate,
            LocalDate maxDate,
            double currentHorizonYears,
            double targetHorizonYears,
            long shortfallDays
        ) {
            this(
                schemeOptionId, amfiCode, schemeName, amcName, planType, optionType, tier,
                currentObservationsCount, minDate, maxDate, currentHorizonYears, targetHorizonYears, shortfallDays,
                true, "CAPABLE"
            );
        }
    }
}

