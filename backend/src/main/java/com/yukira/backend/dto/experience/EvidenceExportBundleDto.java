package com.yukira.backend.dto.experience;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public record EvidenceExportBundleDto(
    String exportId,
    Long investorId,
    String exportFingerprint,
    ZonedDateTime generatedAt,
    String governanceNote,
    Map<String, Object> portfolioSummary,
    List<Map<String, Object>> activeWatchlist,
    List<Map<String, Object>> savedComparisons,
    List<Map<String, Object>> monitoringConditions,
    List<Map<String, Object>> investigationActions
) {}

