package com.yukira.backend.riskfree;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Discrete point-in-time missing date interval derived from persisted FBIL risk-free observations (Phase A5).
 *
 * Epistemic Rules:
 * - Deterministically derived from persisted observation boundaries.
 * - Missing intervals are NEVER filled with synthetic, interpolated, or forward-filled values.
 * - Used strictly for targeted source artifact lookup or bounded FBIL T-Bill retrieval.
 */
public record RiskFreeGapInterval(
    @JsonProperty("startDate") LocalDate startDate,
    @JsonProperty("endDate") LocalDate endDate,
    @JsonProperty("missingCalendarDays") long missingCalendarDays,
    @JsonProperty("missingEstimatedWeekdays") int missingEstimatedWeekdays,
    @JsonProperty("gapClassification") String gapClassification
) {
    public static RiskFreeGapInterval of(LocalDate start, LocalDate end, String classification) {
        long calDays = ChronoUnit.DAYS.between(start, end) + 1;
        int weekdays = countWeekdays(start, end);
        return new RiskFreeGapInterval(start, end, calDays, weekdays, classification);
    }

    private static int countWeekdays(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            DayOfWeek day = cursor.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }
}
