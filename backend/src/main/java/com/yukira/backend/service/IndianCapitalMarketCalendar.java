package com.yukira.backend.service;

import com.yukira.backend.repository.CapitalMarketCalendarVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Authoritative Indian Capital Market Calendar (NSE/BSE/AMFI) (Tasks 270, 331, 332).
 * Governs trading days, weekends, gazetted market holidays, and explicit bounds protection (2023-2026).
 * Out-of-bounds queries produce UNSUPPORTED_CALENDAR_DATE to prevent lookahead or uncorroborated estimations.
 */
@Component
public class IndianCapitalMarketCalendar {

    public static final String CALENDAR_VERSION = "2024.1_STATUTORY";
    public static final LocalDate MIN_SUPPORTED_DATE = LocalDate.of(2023, 1, 1);
    public static final LocalDate MAX_SUPPORTED_DATE = LocalDate.of(2026, 12, 31);

    public enum DayClassification {
        TRADING_DAY,
        WEEKEND,
        OFFICIAL_HOLIDAY,
        OUT_OF_BOUNDS_UNSUPPORTED
    }

    private CapitalMarketCalendarVersionRepository calendarVersionRepository;

    @Autowired(required = false)
    public void setCalendarVersionRepository(CapitalMarketCalendarVersionRepository calendarVersionRepository) {
        this.calendarVersionRepository = calendarVersionRepository;
    }

    private static final Map<LocalDate, String> OFFICIAL_HOLIDAYS;

    static {
        Map<LocalDate, String> holidays = new HashMap<>();

        // 2023 Market Holidays
        holidays.put(LocalDate.of(2023, Month.JANUARY, 26), "Republic Day");
        holidays.put(LocalDate.of(2023, Month.MARCH, 7), "Holi");
        holidays.put(LocalDate.of(2023, Month.MARCH, 30), "Ram Navami");
        holidays.put(LocalDate.of(2023, Month.APRIL, 4), "Mahavir Jayanti");
        holidays.put(LocalDate.of(2023, Month.APRIL, 7), "Good Friday");
        holidays.put(LocalDate.of(2023, Month.APRIL, 14), "Dr. Baba Saheb Ambedkar Jayanti");
        holidays.put(LocalDate.of(2023, Month.MAY, 1), "Maharashtra Day");
        holidays.put(LocalDate.of(2023, Month.JUNE, 29), "Bakri Id");
        holidays.put(LocalDate.of(2023, Month.AUGUST, 15), "Independence Day");
        holidays.put(LocalDate.of(2023, Month.SEPTEMBER, 19), "Ganesh Chaturthi");
        holidays.put(LocalDate.of(2023, Month.OCTOBER, 2), "Mahatma Gandhi Jayanti");
        holidays.put(LocalDate.of(2023, Month.OCTOBER, 24), "Dussehra");
        holidays.put(LocalDate.of(2023, Month.NOVEMBER, 14), "Diwali Balipratipada");
        holidays.put(LocalDate.of(2023, Month.NOVEMBER, 27), "Guru Nanak Jayanti");
        holidays.put(LocalDate.of(2023, Month.DECEMBER, 25), "Christmas");

        // 2024 Market Holidays
        holidays.put(LocalDate.of(2024, Month.JANUARY, 22), "Special Market Holiday (Ayodhya)");
        holidays.put(LocalDate.of(2024, Month.JANUARY, 26), "Republic Day");
        holidays.put(LocalDate.of(2024, Month.MARCH, 8), "Mahashivratri");
        holidays.put(LocalDate.of(2024, Month.MARCH, 25), "Holi");
        holidays.put(LocalDate.of(2024, Month.MARCH, 29), "Good Friday");
        holidays.put(LocalDate.of(2024, Month.APRIL, 11), "Id-Ul-Fitr");
        holidays.put(LocalDate.of(2024, Month.APRIL, 17), "Ram Navami");
        holidays.put(LocalDate.of(2024, Month.MAY, 1), "Maharashtra Day");
        holidays.put(LocalDate.of(2024, Month.MAY, 20), "General Parliamentary Elections");
        holidays.put(LocalDate.of(2024, Month.JUNE, 17), "Bakri Id");
        holidays.put(LocalDate.of(2024, Month.JULY, 17), "Muharram");
        holidays.put(LocalDate.of(2024, Month.AUGUST, 15), "Independence Day");
        holidays.put(LocalDate.of(2024, Month.OCTOBER, 2), "Mahatma Gandhi Jayanti");
        holidays.put(LocalDate.of(2024, Month.NOVEMBER, 1), "Diwali Laxmi Pujan");
        holidays.put(LocalDate.of(2024, Month.NOVEMBER, 15), "Guru Nanak Jayanti");
        holidays.put(LocalDate.of(2024, Month.NOVEMBER, 20), "Maharashtra Assembly Elections");
        holidays.put(LocalDate.of(2024, Month.DECEMBER, 25), "Christmas");

        // 2025 Market Holidays
        holidays.put(LocalDate.of(2025, Month.FEBRUARY, 26), "Mahashivratri");
        holidays.put(LocalDate.of(2025, Month.MARCH, 14), "Holi");
        holidays.put(LocalDate.of(2025, Month.MARCH, 31), "Id-Ul-Fitr");
        holidays.put(LocalDate.of(2025, Month.APRIL, 10), "Mahavir Jayanti");
        holidays.put(LocalDate.of(2025, Month.APRIL, 14), "Dr. Baba Saheb Ambedkar Jayanti");
        holidays.put(LocalDate.of(2025, Month.APRIL, 18), "Good Friday");
        holidays.put(LocalDate.of(2025, Month.MAY, 1), "Maharashtra Day");
        holidays.put(LocalDate.of(2025, Month.JUNE, 7), "Bakri Id");
        holidays.put(LocalDate.of(2025, Month.AUGUST, 15), "Independence Day");
        holidays.put(LocalDate.of(2025, Month.AUGUST, 27), "Ganesh Chaturthi");
        holidays.put(LocalDate.of(2025, Month.OCTOBER, 2), "Mahatma Gandhi Jayanti / Dussehra");
        holidays.put(LocalDate.of(2025, Month.OCTOBER, 21), "Diwali Balipratipada");
        holidays.put(LocalDate.of(2025, Month.NOVEMBER, 5), "Guru Nanak Jayanti");
        holidays.put(LocalDate.of(2025, Month.DECEMBER, 25), "Christmas");

        // 2026 Market Holidays
        holidays.put(LocalDate.of(2026, Month.JANUARY, 26), "Republic Day");
        holidays.put(LocalDate.of(2026, Month.FEBRUARY, 16), "Mahashivratri");
        holidays.put(LocalDate.of(2026, Month.MARCH, 3), "Holi");
        holidays.put(LocalDate.of(2026, Month.MARCH, 20), "Id-Ul-Fitr");
        holidays.put(LocalDate.of(2026, Month.APRIL, 3), "Good Friday");
        holidays.put(LocalDate.of(2026, Month.APRIL, 14), "Dr. Baba Saheb Ambedkar Jayanti");
        holidays.put(LocalDate.of(2026, Month.MAY, 1), "Maharashtra Day");
        holidays.put(LocalDate.of(2026, Month.AUGUST, 15), "Independence Day");
        holidays.put(LocalDate.of(2026, Month.OCTOBER, 2), "Mahatma Gandhi Jayanti");
        holidays.put(LocalDate.of(2026, Month.OCTOBER, 20), "Dussehra");
        holidays.put(LocalDate.of(2026, Month.NOVEMBER, 9), "Diwali");
        holidays.put(LocalDate.of(2026, Month.NOVEMBER, 24), "Guru Nanak Jayanti");
        holidays.put(LocalDate.of(2026, Month.DECEMBER, 25), "Christmas");

        OFFICIAL_HOLIDAYS = Collections.unmodifiableMap(holidays);
    }

    /**
     * Checks if a given date is within the verified calendar boundaries (2023-01-01 to 2026-12-31).
     */
    public boolean isWithinSupportedBounds(LocalDate date) {
        if (date == null) return false;
        return !date.isBefore(MIN_SUPPORTED_DATE) && !date.isAfter(MAX_SUPPORTED_DATE);
    }

    /**
     * Classifies a date according to statutory capital market rules (Task 332).
     */
    public DayClassification classifyDate(LocalDate date) {
        if (!isWithinSupportedBounds(date)) {
            return DayClassification.OUT_OF_BOUNDS_UNSUPPORTED;
        }

        if (OFFICIAL_HOLIDAYS.containsKey(date)) {
            return DayClassification.OFFICIAL_HOLIDAY;
        }

        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return DayClassification.WEEKEND;
        }

        return DayClassification.TRADING_DAY;
    }

    /**
     * Checks if a given date is a confirmed Indian capital market trading holiday.
     */
    public boolean isTradingHoliday(LocalDate date) {
        if (date == null) return false;
        return OFFICIAL_HOLIDAYS.containsKey(date);
    }

    /**
     * Returns true if the market is open (not weekend and not an official holiday).
     */
    public boolean isExpectedPublicationDay(LocalDate date) {
        if (date == null) return false;
        DayClassification classification = classifyDate(date);
        return classification == DayClassification.TRADING_DAY;
    }

    /**
     * Returns the name of the official trading holiday, if applicable.
     */
    public Optional<String> getHolidayName(LocalDate date) {
        if (date == null) return Optional.empty();
        return Optional.ofNullable(OFFICIAL_HOLIDAYS.get(date));
    }

    public String getCalendarVersion() {
        return CALENDAR_VERSION;
    }

    /**
     * Resolves the statutory calendar version identifier for a given year (Task 380).
     */
    public String resolveStatutoryCalendarVersionForYear(int year) {
        if (year >= 2023 && year <= 2026) {
            return "NSE-HOL-" + year + "-V1";
        }
        return "OUT_OF_BOUNDS_UNSUPPORTED";
    }

    public record NavPublicationWindowDto(
        LocalDate date,
        String assetCategory,
        boolean isPublicationExpected,
        String expectedPublishStartTime, // "21:00:00+05:30"
        String statutoryCutoffTime, // "23:00:00+05:30"
        String settlementCycle, // T+1 for Equity post-Jan-2023, T+0 for Liquid
        String calendarRuleExplanation
    ) {}

    /**
     * Mutual fund NAV publication delay and expected cutoff rules (Task 381).
     * Enforces SEBI/AMFI regulatory publication conventions:
     * - Equity/Hybrid: Published on official trading days by 23:00 IST. Settlement: T+1.
     * - Liquid/Overnight: Published 365 days/year (including Sundays and holidays). Settlement: T+0/T+1.
     */
    public NavPublicationWindowDto getNavPublicationWindow(String assetCategory, LocalDate date) {
        if (!isWithinSupportedBounds(date)) {
            return new NavPublicationWindowDto(
                date, assetCategory, false, null, null, null,
                "OUT_OF_BOUNDS: Date is outside verified capital market calendar bounds."
            );
        }

        boolean isLiquidOrOvernight = assetCategory != null && (
            assetCategory.toLowerCase().contains("liquid") ||
            assetCategory.toLowerCase().contains("overnight")
        );

        if (isLiquidOrOvernight) {
            return new NavPublicationWindowDto(
                date,
                assetCategory,
                true,
                "21:00:00+05:30",
                "23:00:00+05:30",
                "T+0",
                "Liquid/Overnight mutual fund schemes calculate and declare daily NAVs across all calendar days for interest accrual."
            );
        }

        boolean isTrading = isExpectedPublicationDay(date);
        return new NavPublicationWindowDto(
            date,
            assetCategory != null ? assetCategory : "EQUITY",
            isTrading,
            isTrading ? "21:00:00+05:30" : null,
            isTrading ? "23:00:00+05:30" : null,
            "T+1",
            isTrading
                ? "Equity/Hybrid mutual fund NAV published on official trading days by 23:00 IST pursuant to SEBI Master Circular."
                : "Non-trading day (weekend or exchange gazetted holiday); official daily NAV publication not mandated."
        );
    }
}
