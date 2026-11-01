package com.yukira.backend.benchmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Discrete point-in-time missing date interval derived from persisted benchmark observations (Phase A4).
 *
 * Epistemic Rules:
 * - Deterministically derived from persisted observation boundaries.
 * - Missing intervals are NEVER filled with synthetic, interpolated, or forward-filled values.
 * - Used strictly for targeted source artifact lookup or bounded Nifty TRI retrieval.
 */
public record BenchmarkGapInterval(
    @JsonProperty("startDate") LocalDate startDate,
    @JsonProperty("endDate") LocalDate endDate,
    @JsonProperty("missingCalendarDays") long missingCalendarDays,
    @JsonProperty("missingEstimatedWeekdays") int missingEstimatedWeekdays,
    @JsonProperty("gapClassification") String gapClassification
) {
    public static BenchmarkGapInterval of(LocalDate start, LocalDate end, String classification) {
        long calDays = ChronoUnit.DAYS.between(start, end) + 1;
        int weekdays = countWeekdays(start, end);
        return new BenchmarkGapInterval(start, end, calDays, weekdays, classification);
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
