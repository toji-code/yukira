package com.yukira.backend.service;

import com.yukira.backend.domain.entity.SourceCalendarExecutionLog;
import com.yukira.backend.domain.entity.SourcePublicationCalendar;
import com.yukira.backend.repository.SourceCalendarExecutionLogRepository;
import com.yukira.backend.repository.SourcePublicationCalendarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class SourceAvailabilityCalendarService {

    private final SourcePublicationCalendarRepository repository;
    private SourceCalendarExecutionLogRepository executionLogRepository;

    public SourceAvailabilityCalendarService(SourcePublicationCalendarRepository repository) {
        this.repository = repository;
    }

    @Autowired(required = false)
    public void setExecutionLogRepository(SourceCalendarExecutionLogRepository executionLogRepository) {
        this.executionLogRepository = executionLogRepository;
    }

    public record SourceCalendarStatusDto(
        Long id,
        String sourceName,
        String frequency,
        int expectedLagBusinessDays,
        LocalTime expectedCutoffTime,
        String timezone,
        Boolean isActive,
        LocalDate lastObservedDate,
        ZonedDateTime lastObservedAt,
        LocalDate nextExpectedDate,
        ZonedDateTime nextExpectedAt,
        int businessDaysLag,
        String freshnessStatus, // UP_TO_DATE, PENDING_TODAY, OVERDUE_LAG, INACTIVE
        String notes
    ) {}

    public record SourceCalendarExecutionLogDto(
        Long id,
        String sourceName,
        OffsetDateTime scheduledCutoff,
        OffsetDateTime actualIngestedAt,
        Integer delayMinutes,
        String status,
        String details,
        OffsetDateTime createdAt
    ) {}

    public List<SourceCalendarStatusDto> getAllCalendarStatuses() {
        ensureDefaultCalendars();
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(ist);
        LocalTime nowTime = LocalTime.now(ist);

        return repository.findAllByOrderBySourceNameAsc().stream()
            .map(c -> evaluateFreshness(c, today, nowTime, ist))
            .collect(Collectors.toList());
    }

    public Optional<SourceCalendarStatusDto> getCalendarStatus(String sourceName) {
        ensureDefaultCalendars();
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(ist);
        LocalTime nowTime = LocalTime.now(ist);

        return repository.findBySourceNameAndIsActiveTrue(sourceName)
            .map(c -> evaluateFreshness(c, today, nowTime, ist));
    }

    public SourceCalendarStatusDto recordObservation(String sourceName, LocalDate observedDate, ZonedDateTime observedAt) {
        SourcePublicationCalendar cal = repository.findBySourceNameAndIsActiveTrue(sourceName)
            .orElseGet(() -> {
                SourcePublicationCalendar c = new SourcePublicationCalendar();
                c.setSourceName(sourceName);
                c.setFrequency("DAILY");
                c.setExpectedLagBusinessDays(1);
                c.setExpectedCutoffTime(LocalTime.of(23, 0));
                return c;
            });

        cal.setLastObservedDate(observedDate);
        cal.setLastObservedAt(observedAt != null ? observedAt : ZonedDateTime.now());

        // Calculate next expected date based on calendar frequency
        LocalDate nextDate;
        if ("MONTHLY".equalsIgnoreCase(cal.getFrequency())) {
            nextDate = observedDate.plusMonths(1).withDayOfMonth(Math.min(10, observedDate.plusMonths(1).lengthOfMonth()));
        } else {
            nextDate = addBusinessDays(observedDate, cal.getExpectedLagBusinessDays());
        }
        cal.setNextExpectedDate(nextDate);

        ZoneId tz = ZoneId.of(cal.getTimezone() != null ? cal.getTimezone() : "Asia/Kolkata");
        cal.setNextExpectedAt(ZonedDateTime.of(nextDate, cal.getExpectedCutoffTime(), tz));

        cal = repository.save(cal);

        // Record execution log if repository available
        if (executionLogRepository != null) {
            SourceCalendarExecutionLog logEntry = new SourceCalendarExecutionLog();
            logEntry.setCalendar(cal);
            logEntry.setSourceName(sourceName);
            logEntry.setScheduledCutoff(cal.getNextExpectedAt() != null ? cal.getNextExpectedAt().toOffsetDateTime() : OffsetDateTime.now());
            logEntry.setActualIngestedAt(observedAt != null ? observedAt.toOffsetDateTime() : OffsetDateTime.now());
            logEntry.setDelayMinutes(0);
            logEntry.setStatus("COMPLETED_ON_TIME");
            logEntry.setDetails("Recorded observation for " + observedDate);
            executionLogRepository.save(logEntry);
        }

        ZoneId ist = ZoneId.of("Asia/Kolkata");
        return evaluateFreshness(cal, LocalDate.now(ist), LocalTime.now(ist), ist);
    }

    @Transactional(readOnly = true)
    public List<SourceCalendarExecutionLogDto> getRecentExecutionLogs(int limit) {
        if (executionLogRepository == null) return List.of();
        return executionLogRepository.findTop50ByOrderByCreatedAtDesc().stream()
            .limit(limit)
            .map(l -> new SourceCalendarExecutionLogDto(
                l.getId(),
                l.getSourceName(),
                l.getScheduledCutoff(),
                l.getActualIngestedAt(),
                l.getDelayMinutes(),
                l.getStatus(),
                l.getDetails(),
                l.getCreatedAt()
            ))
            .collect(Collectors.toList());
    }

    private SourceCalendarStatusDto evaluateFreshness(
            SourcePublicationCalendar cal, LocalDate today, LocalTime nowTime, ZoneId ist) {
        if (!Boolean.TRUE.equals(cal.getIsActive())) {
            return toDto(cal, 0, "INACTIVE");
        }

        if (cal.getLastObservedDate() == null) {
            return toDto(cal, 999, "OVERDUE_LAG");
        }

        int lag = countBusinessDays(cal.getLastObservedDate(), today);
        String status;

        if ("DAILY".equalsIgnoreCase(cal.getFrequency())) {
            if (lag <= cal.getExpectedLagBusinessDays()) {
                status = "UP_TO_DATE";
            } else if (lag == cal.getExpectedLagBusinessDays() + 1 && nowTime.isBefore(cal.getExpectedCutoffTime())) {
                status = "PENDING_TODAY";
            } else {
                status = "OVERDUE_LAG";
            }
        } else if ("MONTHLY".equalsIgnoreCase(cal.getFrequency())) {
            long monthsBetween = ChronoUnit.MONTHS.between(
                cal.getLastObservedDate().withDayOfMonth(1), 
                today.withDayOfMonth(1)
            );
            if (monthsBetween <= 1) {
                status = "UP_TO_DATE";
            } else if (monthsBetween == 2 && today.getDayOfMonth() <= cal.getExpectedLagBusinessDays()) {
                status = "PENDING_TODAY";
            } else {
                status = "OVERDUE_LAG";
            }
        } else {
            status = "UP_TO_DATE";
        }

        return toDto(cal, lag, status);
    }

    private SourceCalendarStatusDto toDto(SourcePublicationCalendar cal, int lag, String status) {
        ZoneId tz = ZoneId.of(cal.getTimezone() != null ? cal.getTimezone() : "Asia/Kolkata");
        return new SourceCalendarStatusDto(
            cal.getId(),
            cal.getSourceName(),
            cal.getFrequency(),
            cal.getExpectedLagBusinessDays(),
            cal.getExpectedCutoffTime(),
            cal.getTimezone(),
            cal.getIsActive(),
            cal.getLastObservedDate(),
            cal.getLastObservedAt() != null ? cal.getLastObservedAt().withZoneSameInstant(tz) : null,
            cal.getNextExpectedDate(),
            cal.getNextExpectedAt() != null ? cal.getNextExpectedAt().withZoneSameInstant(tz) : null,
            lag,
            status,
            cal.getNotes()
        );
    }

    private int countBusinessDays(LocalDate start, LocalDate end) {
        if (start == null || end == null || !start.isBefore(end)) {
            return 0;
        }
        int count = 0;
        LocalDate cur = start.plusDays(1);
        while (!cur.isAfter(end)) {
            DayOfWeek dow = cur.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
            cur = cur.plusDays(1);
        }
        return count;
    }

    private LocalDate addBusinessDays(LocalDate start, int days) {
        LocalDate cur = start;
        int added = 0;
        while (added < days) {
            cur = cur.plusDays(1);
            DayOfWeek dow = cur.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return cur;
    }

    private void ensureDefaultCalendars() {
        if (repository.count() == 0) {
            List<SourcePublicationCalendar> defaults = new ArrayList<>();

            SourcePublicationCalendar amfiNav = new SourcePublicationCalendar();
            amfiNav.setSourceName("AMFI_DAILY_NAV");
            amfiNav.setFrequency("DAILY");
            amfiNav.setExpectedLagBusinessDays(1);
            amfiNav.setExpectedCutoffTime(LocalTime.of(23, 0));
            amfiNav.setNotes("AMFI India daily mutual fund official NAV publication");
            defaults.add(amfiNav);

            SourcePublicationCalendar amcHoldings = new SourcePublicationCalendar();
            amcHoldings.setSourceName("AMC_MONTHLY_HOLDINGS");
            amcHoldings.setFrequency("MONTHLY");
            amcHoldings.setExpectedLagBusinessDays(10);
            amcHoldings.setExpectedCutoffTime(LocalTime.of(23, 59));
            amcHoldings.setNotes("Official AMC monthly portfolio disclosures due by 10th of following month");
            defaults.add(amcHoldings);

            SourcePublicationCalendar fbil = new SourcePublicationCalendar();
            fbil.setSourceName("FBIL_TREPS_TRI");
            fbil.setFrequency("DAILY");
            fbil.setExpectedLagBusinessDays(1);
            fbil.setExpectedCutoffTime(LocalTime.of(19, 0));
            fbil.setNotes("Financial Benchmarks India risk-free overnight rates");
            defaults.add(fbil);

            SourcePublicationCalendar nse = new SourcePublicationCalendar();
            nse.setSourceName("NSE_BENCHMARK_TRI");
            nse.setFrequency("DAILY");
            nse.setExpectedLagBusinessDays(1);
            nse.setExpectedCutoffTime(LocalTime.of(21, 0));
            nse.setNotes("National Stock Exchange index total return series");
            defaults.add(nse);

            repository.saveAll(defaults);
        }
    }
}
