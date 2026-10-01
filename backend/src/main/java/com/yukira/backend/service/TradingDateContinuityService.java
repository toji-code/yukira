package com.yukira.backend.service;

import com.yukira.backend.domain.entity.MarketCalendar;
import com.yukira.backend.domain.entity.NavObservation;
import com.yukira.backend.repository.MarketCalendarRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Historical NAV Trading-Date Continuity Diagnostics Service:
 *
 * Evaluates date-by-date continuity across a historical analysis window and distinguishes:
 * - Expected non-trading days (weekends: Saturday and Sunday)
 * - Exchange holidays (sourced explicitly from market_calendar table capability; never hardcoded)
 * - Missing observations on expected trading days
 * - Duplicate observations (identical date and NAV)
 * - Conflicting observations (identical date with divergent NAV values)
 * - Revised observations (superseded observations with incremented revision_seq)
 * - Suspicious observations (excessive daily return jump > 20% or non-positive NAV)
 * - Genuinely unavailable data
 *
 * Epistemic Guardrail:
 * Diagnostic classifications represent epistemic data-quality states.
 * They are NEVER converted into portfolio scores or automated investment decisions.
 */
@Service
@SuppressWarnings("null")
public class TradingDateContinuityService {

    private final MarketCalendarRepository marketCalendarRepository;

    public TradingDateContinuityService(MarketCalendarRepository marketCalendarRepository) {
        this.marketCalendarRepository = marketCalendarRepository;
    }

    public enum ContinuityStatus {
        VALID_OBSERVATION,
        EXPECTED_WEEKEND,
        EXCHANGE_HOLIDAY,
        MISSING_OBSERVATION,
        DUPLICATE_OBSERVATION,
        CONFLICTING_OBSERVATION,
        REVISED_OBSERVATION,
        SUSPICIOUS_OBSERVATION
    }

    public record DailyContinuityItem(
        LocalDate date,
        DayOfWeek dayOfWeek,
        ContinuityStatus status,
        boolean isExpectedTradingDay,
        BigDecimal navValue,
        Integer revisionSeq,
        String diagnosticNote
    ) {}

    public record ContinuityDiagnosticReport(
        LocalDate startDate,
        LocalDate endDate,
        int totalDaysEvaluated,
        int expectedTradingDays,
        int expectedNonTradingDays,
        int uniqueTradingDaysPresent,
        int validObservationsCount,
        int missingDaysCount,
        int duplicateCount,
        int conflictingCount,
        int revisedCount,
        int suspiciousCount,
        int totalPhysicalObservations,
        int revisionRowsCount,
        BigDecimal coverageRatio,
        boolean hasContinuityBreach,
        List<DailyContinuityItem> dailyDetails
    ) {}

    /**
     * Analyzes continuity across the requested date window [startDate, endDate]
     * for a given collection of historical NAV observations.
     *
     * Invariant: Trading date presence and coverage ratio are computed using
     * unique effective trading dates, NOT raw physical observation rows.
     * Revision rows for the same effective date do not inflate trading dates.
     */
    public ContinuityDiagnosticReport analyzeContinuity(
        LocalDate startDate,
        LocalDate endDate,
        List<NavObservation> observations
    ) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate must not be null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }

        // Query explicit exchange holidays if registered in market_calendar
        Map<LocalDate, MarketCalendar> calendarMap = marketCalendarRepository
            .findByCalendarDateBetweenOrderByCalendarDateAsc(startDate, endDate).stream()
            .collect(Collectors.toMap(MarketCalendar::getCalendarDate, c -> c, (a, b) -> a));

        // Group observations by effective date within [startDate, endDate]
        Map<LocalDate, List<NavObservation>> obsByDate = new HashMap<>();
        int totalPhysicalObservations = 0;
        if (observations != null) {
            for (NavObservation obs : observations) {
                if (!obs.getEffectiveDate().isBefore(startDate) && !obs.getEffectiveDate().isAfter(endDate)) {
                    totalPhysicalObservations++;
                    obsByDate.computeIfAbsent(obs.getEffectiveDate(), k -> new ArrayList<>()).add(obs);
                }
            }
        }

        List<DailyContinuityItem> details = new ArrayList<>();
        int expectedTradingDays = 0;
        int expectedNonTradingDays = 0;
        int uniqueTradingDaysPresent = 0;
        int validCount = 0;
        int missingCount = 0;
        int duplicateCount = 0;
        int conflictingCount = 0;
        int revisedCount = 0;
        int suspiciousCount = 0;
        int revisionRowsCount = 0;

        BigDecimal prevNav = null;
        LocalDate cur = startDate;

        while (!cur.isAfter(endDate)) {
            DayOfWeek dow = cur.getDayOfWeek();
            boolean isWeekend = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);

            MarketCalendar calEntry = calendarMap.get(cur);
            boolean isCalendarHoliday = (calEntry != null && !calEntry.isTradingDay());
            boolean isExpectedTrading = !isWeekend && !isCalendarHoliday;

            List<NavObservation> dateObs = obsByDate.getOrDefault(cur, Collections.emptyList());

            if (isExpectedTrading) {
                expectedTradingDays++;

                if (dateObs.isEmpty()) {
                    missingCount++;
                    details.add(new DailyContinuityItem(
                        cur, dow, ContinuityStatus.MISSING_OBSERVATION, true,
                        null, null, "Expected trading day has no recorded NAV observation."
                    ));
                } else {
                    // Unique trading day present — exactly 1 day counted regardless of revision row count
                    uniqueTradingDaysPresent++;
                    if (dateObs.size() > 1) {
                        revisionRowsCount += (dateObs.size() - 1);
                    }

                    // Check for conflicts / duplicates / revisions
                    boolean hasConflict = false;
                    boolean hasDuplicate = false;
                    boolean hasRevision = false;
                    boolean isSuspicious = false;

                    NavObservation authoritative = dateObs.get(0);
                    BigDecimal primaryNav = authoritative.getNavValue();

                    Map<Integer, BigDecimal> navBySeq = new HashMap<>();
                    for (NavObservation o : dateObs) {
                        if (o.getRevisionSeq() > 1 || "REVISED".equalsIgnoreCase(o.getRevisionStatus())) {
                            hasRevision = true;
                        }
                        BigDecimal existingNav = navBySeq.putIfAbsent(o.getRevisionSeq(), o.getNavValue());
                        if (existingNav != null) {
                            if (existingNav.compareTo(o.getNavValue()) != 0) {
                                hasConflict = true; // Divergent NAVs for identical revisionSeq
                            } else {
                                hasDuplicate = true; // Identical NAV duplicate for identical revisionSeq
                            }
                        }
                        if ("SUSPICIOUS".equalsIgnoreCase(o.getQualityAssessment()) || o.getNavValue().compareTo(BigDecimal.ZERO) <= 0) {
                            isSuspicious = true;
                        }
                    }

                    if (dateObs.size() > 1 && !hasConflict && !hasDuplicate) {
                        hasRevision = true; // Different revision sequences for same date
                    }

                    // Check daily return jump vs previous NAV
                    if (prevNav != null && prevNav.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal jump = primaryNav.subtract(prevNav).divide(prevNav, 8, RoundingMode.HALF_UP).abs();
                        if (jump.compareTo(new BigDecimal("0.20")) > 0) {
                            isSuspicious = true;
                        }
                    }

                    ContinuityStatus status;
                    String note;

                    if (hasConflict) {
                        conflictingCount++;
                        status = ContinuityStatus.CONFLICTING_OBSERVATION;
                        note = String.format("Conflicting observations on %s (%d records with divergent values).", cur, dateObs.size());
                    } else if (hasRevision) {
                        revisedCount++;
                        status = ContinuityStatus.REVISED_OBSERVATION;
                        note = String.format("Observation on %s has retroactive revision history (seq %d).", cur, authoritative.getRevisionSeq());
                    } else if (isSuspicious) {
                        suspiciousCount++;
                        status = ContinuityStatus.SUSPICIOUS_OBSERVATION;
                        note = String.format("Observation on %s flagged suspicious (NAV: %s).", cur, primaryNav);
                    } else if (hasDuplicate) {
                        duplicateCount++;
                        status = ContinuityStatus.DUPLICATE_OBSERVATION;
                        note = String.format("Duplicate identical observations on %s (%d records).", cur, dateObs.size());
                    } else {
                        validCount++;
                        status = ContinuityStatus.VALID_OBSERVATION;
                        note = "Normal valid observation.";
                    }

                    prevNav = primaryNav;
                    details.add(new DailyContinuityItem(
                        cur, dow, status, true, primaryNav, authoritative.getRevisionSeq(), note
                    ));
                }
            } else {
                expectedNonTradingDays++;
                if (dateObs.size() > 1) {
                    revisionRowsCount += (dateObs.size() - 1);
                }
                if (isCalendarHoliday) {
                    String holName = (calEntry != null && calEntry.getHolidayName() != null) ? calEntry.getHolidayName() : "Exchange Holiday";
                    details.add(new DailyContinuityItem(
                        cur, dow, ContinuityStatus.EXCHANGE_HOLIDAY, false, null, null, holName
                    ));
                } else {
                    details.add(new DailyContinuityItem(
                        cur, dow, ContinuityStatus.EXPECTED_WEEKEND, false, null, null, "Weekend"
                    ));
                }
            }

            cur = cur.plusDays(1);
        }

        int totalDays = (int) (endDate.toEpochDay() - startDate.toEpochDay() + 1);
        boolean hasBreach = (missingCount > 0 || conflictingCount > 0 || suspiciousCount > 0);

        BigDecimal coverageRatio = expectedTradingDays > 0
            ? BigDecimal.valueOf(uniqueTradingDaysPresent)
                .divide(BigDecimal.valueOf(expectedTradingDays), 6, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new ContinuityDiagnosticReport(
            startDate,
            endDate,
            totalDays,
            expectedTradingDays,
            expectedNonTradingDays,
            uniqueTradingDaysPresent,
            validCount,
            missingCount,
            duplicateCount,
            conflictingCount,
            revisedCount,
            suspiciousCount,
            totalPhysicalObservations,
            revisionRowsCount,
            coverageRatio,
            hasBreach,
            details
        );
    }
}
