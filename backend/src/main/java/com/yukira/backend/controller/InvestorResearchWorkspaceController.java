package com.yukira.backend.controller;

import com.yukira.backend.dto.workspace.*;
import com.yukira.backend.service.InvestorResearchWorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for unified investor research workspace (Tasks 191–195).
 */
@RestController
@RequestMapping("/api/v1/investors/workspace")
public class InvestorResearchWorkspaceController {

    private final InvestorResearchWorkspaceService workspaceService;

    public InvestorResearchWorkspaceController(InvestorResearchWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * Retrieves cross-entity unified research timeline feed (Task 191).
     */
    @GetMapping("/timeline")
    public ResponseEntity<List<InvestorResearchTimelineEventDto>> getUnifiedTimeline(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false, defaultValue = "50") int limit
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        List<InvestorResearchTimelineEventDto> timeline = workspaceService.getUnifiedTimeline(subject, limit);
        return ResponseEntity.ok(timeline);
    }

    /**
     * Retrieves all investor research tags (Task 192).
     */
    @GetMapping("/tags")
    public ResponseEntity<List<InvestorResearchTagDto>> getTags(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String targetType,
        @RequestParam(required = false) String targetId
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        if (targetType != null && targetId != null) {
            return ResponseEntity.ok(workspaceService.getTagsForTarget(subject, targetType, targetId));
        }
        return ResponseEntity.ok(workspaceService.getAllTags(subject));
    }

    /**
     * Attaches a research tag to an entity (Task 192).
     */
    @PostMapping("/tags")
    public ResponseEntity<InvestorResearchTagDto> addTag(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody Map<String, String> payload
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        String tagName = payload.get("tagName");
        String targetType = payload.get("targetType");
        String targetId = payload.get("targetId");
        InvestorResearchTagDto tag = workspaceService.addTag(subject, tagName, targetType, targetId);
        return ResponseEntity.status(HttpStatus.CREATED).body(tag);
    }

    /**
     * Deletes a research tag.
     */
    @DeleteMapping("/tags/{id}")
    public ResponseEntity<Void> deleteTag(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        workspaceService.deleteTag(subject, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves saved screener / discovery filters (Task 193).
     */
    @GetMapping("/filters")
    public ResponseEntity<List<InvestorSavedFilterDto>> getSavedFilters(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return ResponseEntity.ok(workspaceService.getSavedFilters(subject));
    }

    /**
     * Saves a discovery filter (Task 193).
     */
    @PostMapping("/filters")
    public ResponseEntity<InvestorSavedFilterDto> saveFilter(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody Map<String, String> payload
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        String name = payload.get("filterName");
        String criteria = payload.get("filterCriteriaJson");
        String type = payload.get("filterType");
        InvestorSavedFilterDto saved = workspaceService.saveFilter(subject, name, criteria, type);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Deletes a saved discovery filter.
     */
    @DeleteMapping("/filters/{id}")
    public ResponseEntity<Void> deleteFilter(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        workspaceService.deleteFilter(subject, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves revision history for a saved comparison (Task 194).
     */
    @GetMapping("/comparisons/{id}/versions")
    public ResponseEntity<List<InvestorComparisonVersionDto>> getComparisonVersions(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return ResponseEntity.ok(workspaceService.getComparisonVersions(subject, id));
    }

    /**
     * Retrieves investigation queue actions (Task 195).
     */
    @GetMapping("/actions")
    public ResponseEntity<List<InvestorInvestigationActionDto>> getActions(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String status
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return ResponseEntity.ok(workspaceService.getActions(subject, status));
    }

    /**
     * Enqueues a new investigation action (Task 195).
     */
    @PostMapping("/actions")
    public ResponseEntity<InvestorInvestigationActionDto> createAction(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody CreateInvestigationActionRequest request
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        InvestorInvestigationActionDto created = workspaceService.createAction(subject, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates investigation action lifecycle status or outcome notes (Task 195).
     */
    @PatchMapping("/actions/{id}/status")
    public ResponseEntity<InvestorInvestigationActionDto> updateActionStatus(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @RequestBody Map<String, String> payload
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        String status = payload.get("status");
        String notes = payload.get("outcomeNotes");
        InvestorInvestigationActionDto updated = workspaceService.updateActionStatus(subject, id, status, notes);
        return ResponseEntity.ok(updated);
    }
}

