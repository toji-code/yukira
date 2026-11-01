package com.yukira.backend.controller;

import com.yukira.backend.dto.experience.EvidenceExportBundleDto;
import com.yukira.backend.dto.experience.GlobalSearchResultDto;
import com.yukira.backend.dto.experience.InvestorDisplayPreferenceDto;
import com.yukira.backend.service.InvestorExperienceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for investor display preferences, global command search,
 * and self-contained evidence export bundles (Tasks 201–205).
 */
@RestController
@RequestMapping("/api/v1/investors")
public class InvestorExperienceController {

    private final InvestorExperienceService experienceService;

    public InvestorExperienceController(InvestorExperienceService experienceService) {
        this.experienceService = experienceService;
    }

    /**
     * Retrieves investor display preferences (Task 203).
     */
    @GetMapping("/preferences")
    public ResponseEntity<InvestorDisplayPreferenceDto> getPreferences(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        InvestorDisplayPreferenceDto pref = experienceService.getOrCreatePreferences(subject);
        return ResponseEntity.ok(pref);
    }

    /**
     * Updates investor display preferences (Task 203).
     */
    @PutMapping("/preferences")
    public ResponseEntity<InvestorDisplayPreferenceDto> updatePreferences(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody Map<String, Object> payload
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        String period = (String) payload.get("defaultAnalysisPeriod");
        String tab = (String) payload.get("defaultWorkspaceTab");
        String theme = (String) payload.get("preferredTheme");
        Boolean showWarnings = payload.get("showCandidateMetricWarnings") != null
            ? (Boolean) payload.get("showCandidateMetricWarnings") : null;
        String dateFormat = (String) payload.get("dateDisplayFormat");

        InvestorDisplayPreferenceDto updated = experienceService.updatePreferences(
            subject, period, tab, theme, showWarnings, dateFormat
        );
        return ResponseEntity.ok(updated);
    }

    /**
     * Global investor-centric search for schemes, saved research, and actions (Task 204).
     */
    @GetMapping("/search")
    public ResponseEntity<List<GlobalSearchResultDto>> search(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam String q
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        List<GlobalSearchResultDto> results = experienceService.globalSearch(subject, q);
        return ResponseEntity.ok(results);
    }

    /**
     * Generates a self-contained, auditable point-in-time evidence bundle (Task 205).
     */
    @GetMapping("/export/bundle")
    public ResponseEntity<EvidenceExportBundleDto> exportBundle(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        EvidenceExportBundleDto bundle = experienceService.exportEvidenceBundle(subject);
        return ResponseEntity.ok(bundle);
    }
}

