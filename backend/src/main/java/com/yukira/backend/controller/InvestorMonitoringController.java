package com.yukira.backend.controller;

import com.yukira.backend.dto.monitoring.*;
import com.yukira.backend.service.InvestorMonitoringService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Primary REST Controller for Continuous Fund & Portfolio Monitoring (YUKIRA Task 12 & Task 13).
 *
 * Epistemic Contract:
 * - Ownership is derived strictly from authenticated Jwt principal (or local dev context).
 * - All event detections and triggers are computed deterministically in backend.
 * - ZERO investment recommendations, ZERO composite scoring.
 * - Distinguishes between genuine investment drift and data quality/disclosure timing artifacts.
 * - Exposes execution telemetry, metric registry configs, and condition lifecycle.
 */
@RestController
@RequestMapping("/api/v1/monitoring")
public class InvestorMonitoringController {

    private final InvestorMonitoringService monitoringService;

    public InvestorMonitoringController(InvestorMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    /**
     * Retrieves overall monitoring overview, telemetry, and counts for the authenticated investor.
     */
    @GetMapping("/overview")
    public ResponseEntity<MonitoringOverviewDto> getMonitoringOverview(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        MonitoringOverviewDto overview = monitoringService.getMonitoringOverview(subject);
        return ResponseEntity.ok(overview);
    }

    /**
     * Retrieves the latest monitoring execution record and operational telemetry.
     */
    @GetMapping("/execution/latest")
    public ResponseEntity<MonitoringExecutionDto> getLatestExecution(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return monitoringService.getLatestExecution(subject)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Retrieves paginated execution history for the authenticated investor.
     */
    @GetMapping("/execution/history")
    public ResponseEntity<Page<MonitoringExecutionDto>> getExecutionHistory(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "10") int size
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        Page<MonitoringExecutionDto> history = monitoringService.getExecutionHistory(subject, page, size);
        return ResponseEntity.ok(history);
    }

    /**
     * Retrieves all centralized metric configurations, unit definitions, and supported operators.
     */
    @GetMapping("/metrics")
    public ResponseEntity<List<MonitoringMetricConfigDto>> getSupportedMetrics() {
        List<MonitoringMetricConfigDto> metrics = monitoringService.getSupportedMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * Retrieves filtered and paginated monitoring events.
     */
    @GetMapping("/events")
    public ResponseEntity<Page<MonitoringEventDto>> getEvents(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) Long schemeId,
        @RequestParam(required = false) Long schemeOptionId,
        @RequestParam(required = false) String classification,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String scope,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        Page<MonitoringEventDto> events = monitoringService.getFilteredEvents(
            subject, schemeId, schemeOptionId, classification, severity, status, scope, page, size
        );
        return ResponseEntity.ok(events);
    }

    /**
     * Retrieves detail for a specific monitoring event.
     */
    @GetMapping("/events/{id}")
    public ResponseEntity<?> getEventDetail(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return monitoringService.getEventDetail(id, subject)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(null));
    }

    /**
     * Updates the lifecycle status of a monitoring event (e.g. ACKNOWLEDGED, INVESTIGATING, RESOLVED, DISMISSED).
     */
    @PostMapping("/events/{id}/lifecycle")
    public ResponseEntity<MonitoringEventDto> updateEventLifecycle(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @RequestBody MonitoringLifecycleUpdateRequest request
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        MonitoringEventDto updated = monitoringService.updateLifecycle(id, request, subject);
        return ResponseEntity.ok(updated);
    }

    /**
     * Appends an investor investigation note to a monitoring event.
     */
    @PostMapping("/events/{id}/notes")
    public ResponseEntity<MonitoringEventNoteDto> addEventNote(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @RequestBody Map<String, String> payload
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        String noteText = payload.get("noteText");
        MonitoringEventNoteDto note = monitoringService.addEventNote(id, noteText, subject);
        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }

    /**
     * Triggers an on-demand continuous monitoring evaluation scan across the investor's universe.
     */
    @PostMapping("/evaluate")
    public ResponseEntity<MonitoringEvaluationResponseDto> runMonitoringEvaluation(
        @AuthenticationPrincipal Jwt jwt
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        MonitoringEvaluationResponseDto response = monitoringService.evaluateMonitoring(subject);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all investor-defined monitoring conditions.
     */
    @GetMapping("/conditions")
    public ResponseEntity<List<MonitoringConditionDto>> getConditions(
        @AuthenticationPrincipal Jwt jwt
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        List<MonitoringConditionDto> conditions = monitoringService.getConditions(subject);
        return ResponseEntity.ok(conditions);
    }

    /**
     * Creates a new investor-defined monitoring condition.
     */
    @PostMapping("/conditions")
    public ResponseEntity<MonitoringConditionDto> createCondition(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody MonitoringConditionRequest request
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        MonitoringConditionDto created = monitoringService.createCondition(request, subject);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Direct Finding-to-Monitoring Handoff (Task 89).
     * Creates a monitoring condition directly from an active stress investigation finding.
     */
    @PostMapping("/conditions/from-finding")
    public ResponseEntity<MonitoringConditionDto> createConditionFromFinding(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody CreateConditionFromFindingRequest request
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        MonitoringConditionDto created = monitoringService.createConditionFromFinding(request, subject);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates an existing investor-defined monitoring condition.
     */
    @PutMapping("/conditions/{id}")
    public ResponseEntity<MonitoringConditionDto> updateCondition(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @RequestBody MonitoringConditionRequest request
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        MonitoringConditionDto updated = monitoringService.updateCondition(id, request, subject);
        return ResponseEntity.ok(updated);
    }

    /**
     * Deletes an investor-defined monitoring condition.
     */
    @DeleteMapping("/conditions/{id}")
    public ResponseEntity<Void> deleteCondition(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        monitoringService.deleteCondition(id, subject);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves execution history for a specific monitoring condition (Task 163).
     */
    @GetMapping("/conditions/{id}/history")
    public ResponseEntity<List<MonitoringConditionHistoryDto>> getConditionHistory(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        List<MonitoringConditionHistoryDto> history = monitoringService.getConditionHistory(id, subject);
        return ResponseEntity.ok(history);
    }

    /**
     * Updates condition lifecycle status (ACTIVE, PAUSED, RESOLVED, DISMISSED) (Task 151).
     */
    @PostMapping("/conditions/{id}/status")
    public ResponseEntity<MonitoringConditionDto> updateConditionStatus(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @RequestBody Map<String, String> payload
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        String status = payload.get("status");
        String notes = payload.get("notes");
        MonitoringConditionDto updated = monitoringService.updateConditionStatus(id, status, notes, subject);
        return ResponseEntity.ok(updated);
    }

    /**
     * Toggles an investor-defined condition enabled/disabled.
     */
    @PostMapping("/conditions/{id}/toggle")
    public ResponseEntity<MonitoringConditionDto> toggleCondition(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        MonitoringConditionDto updated = monitoringService.toggleCondition(id, subject);
        return ResponseEntity.ok(updated);
    }

    /**
     * Retrieves monitoring events specific to a fund scheme.
     */
    @GetMapping("/funds/{schemeId}/events")
    public ResponseEntity<List<MonitoringEventDto>> getFundEvents(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long schemeId
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        List<MonitoringEventDto> events = monitoringService.getFundEvents(schemeId, subject);
        return ResponseEntity.ok(events);
    }

    /**
     * Retrieves portfolio-level monitoring events.
     */
    @GetMapping("/portfolio/events")
    public ResponseEntity<List<MonitoringEventDto>> getPortfolioEvents(
        @AuthenticationPrincipal Jwt jwt
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        List<MonitoringEventDto> events = monitoringService.getPortfolioEvents(subject);
        return ResponseEntity.ok(events);
    }

    /**
     * Generates a grounded AI or deterministic fallback explanation for an event with 3 due-diligence questions.
     */
    @PostMapping("/events/{id}/explain")
    public ResponseEntity<MonitoringAiExplanationDto> getEventExplanation(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        MonitoringAiExplanationDto explanation = monitoringService.getEventAiExplanation(id, subject);
        return ResponseEntity.ok(explanation);
    }
}
