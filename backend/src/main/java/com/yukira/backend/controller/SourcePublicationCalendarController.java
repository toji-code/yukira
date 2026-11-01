package com.yukira.backend.controller;

import com.yukira.backend.service.SourceAvailabilityCalendarService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/source-calendar")
public class SourcePublicationCalendarController {

    private final SourceAvailabilityCalendarService calendarService;

    public SourcePublicationCalendarController(SourceAvailabilityCalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping
    public ResponseEntity<List<SourceAvailabilityCalendarService.SourceCalendarStatusDto>> getAllCalendars() {
        return ResponseEntity.ok(calendarService.getAllCalendarStatuses());
    }

    @GetMapping("/{sourceName}")
    public ResponseEntity<SourceAvailabilityCalendarService.SourceCalendarStatusDto> getCalendar(
            @PathVariable("sourceName") String sourceName) {
        return calendarService.getCalendarStatus(sourceName)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

