package com.yukira.backend.universe;

import java.time.LocalDate;

/**
 * Represents a discrete, point-in-time missing date interval derived from actual NAV observations (Phase A1).
 *
 * Epistemic Rules:
 * - Deterministically derived from persisted observation boundaries.
 * - Missing intervals are NEVER filled with synthetic, interpolated, or forward-filled values.
 * - Used strictly for targeted source artifact lookup or bounded AMFI retrieval.
 */
public record HistoricalNavGapInterval(
    LocalDate startDate,
    LocalDate endDate,
    long missingCalendarDays,
    int missingEstimatedWeekdays,
    String gapClassification
) {
    public static HistoricalNavGapInterval of(LocalDate start, LocalDate end, String classification) {
        long calDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        int weekdays = countWeekdays(start, end);
        return new HistoricalNavGapInterval(start, end, calDays, weekdays, classification);
    }

    private static int countWeekdays(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            java.time.DayOfWeek day = cursor.getDayOfWeek();
            if (day != java.time.DayOfWeek.SATURDAY && day != java.time.DayOfWeek.SUNDAY) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }
}
