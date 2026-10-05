package com.yukira.backend.controller;

import com.yukira.backend.dto.enrichment.EnrichedFundProfileDto;
import com.yukira.backend.repository.SchemeRepository;
import com.yukira.backend.service.FundEnrichmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/schemes")
public class FundEnrichmentController {

    private final FundEnrichmentService fundEnrichmentService;
    private final SchemeRepository schemeRepository;

    public FundEnrichmentController(
        FundEnrichmentService fundEnrichmentService,
        SchemeRepository schemeRepository
    ) {
        this.fundEnrichmentService = fundEnrichmentService;
        this.schemeRepository = schemeRepository;
    }

    /**
     * Retrieves factual fund enrichment information for a specific scheme option:
     * AUM, Expense Ratio (Direct vs Regular), Fund Manager, SIP/Lumpsum terms, and Holdings summary.
     */
    @GetMapping("/options/{id}/enrichment")
    public ResponseEntity<EnrichedFundProfileDto> getOptionEnrichment(
        @PathVariable Long id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff
    ) {
        EnrichedFundProfileDto dto = fundEnrichmentService.getEnrichedProfile(id, knowledgeCutoff);
        return ResponseEntity.ok(dto);
    }

    /**
     * Retrieves strictly scheme-level enrichment information (Fund Managers, statutory KIM investment terms).
     * Option-specific attributes (TER, AUM, holdings) are never populated here and must be requested
     * via /options/{id}/enrichment with an exact scheme_option_id.
     */
    @GetMapping("/{schemeId}/enrichment")
    public ResponseEntity<EnrichedFundProfileDto> getSchemeEnrichment(
        @PathVariable Long schemeId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff
    ) {
        if (!schemeRepository.existsById(schemeId)) {
            return ResponseEntity.notFound().build();
        }

        EnrichedFundProfileDto dto = fundEnrichmentService.getSchemeLevelEnrichment(schemeId, knowledgeCutoff);
        return ResponseEntity.ok(dto);
    }
}
