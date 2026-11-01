package com.yukira.backend.dto.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Drawdown episodes decomposition response DTO (Task 137).
 * Decomposes continuous historical underwater series into discrete peak-to-trough-to-recovery episodes.
 */
public record DrawdownEpisodesResponseDto(
    Long schemeOptionId,
    String schemeName,
    LocalDate asOfDate,
    OffsetDateTime knowledgeCutoffTime,
    BigDecimal maxDrawdown,                // RSK-03 (strictly negative)
    Integer maxDrawdownDurationDays,       // RSK-04
    BigDecimal currentDrawdown,
    String currentUnderwaterState,         // "RECOVERED", "UNDERWATER"
    List<DrawdownEpisodeItem> episodes,
    String governanceNotice
) {

    public record DrawdownEpisodeItem(
        int episodeIndex,
        LocalDate peakDate,
        LocalDate troughDate,
        LocalDate recoveryDate,            // null if ongoing / not yet recovered
        BigDecimal peakNav,
        BigDecimal troughNav,
        BigDecimal drawdownPercent,        // strictly negative e.g. -0.1542 (-15.42%)
        int underwaterDays,                // peak to recovery (or peak to asOfDate if ongoing)
        Integer recoveryDays,              // trough to recovery (null if ongoing)
        String episodeStatus               // "RECOVERED", "ONGOING"
    ) {}
}
