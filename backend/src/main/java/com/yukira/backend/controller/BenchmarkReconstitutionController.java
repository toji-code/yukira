package com.yukira.backend.controller;

import com.yukira.backend.dto.portfolio.BenchmarkReconstitutionResponseDto;
import com.yukira.backend.service.BenchmarkReconstitutionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Controller exposing REST API for Benchmark Reconstitution Tracking.
 * GET /api/v1/benchmarks/{code}/reconstitution?fromDate=...&toDate=...
 */
@RestController
@RequestMapping("/api/v1/benchmarks")
public class BenchmarkReconstitutionController {

    private final BenchmarkReconstitutionService reconstitutionService;
    private final com.yukira.backend.service.BenchmarkFactsheetArchiveService factsheetArchiveService;

    public BenchmarkReconstitutionController(
            BenchmarkReconstitutionService reconstitutionService,
            com.yukira.backend.service.BenchmarkFactsheetArchiveService factsheetArchiveService) {
        this.reconstitutionService = reconstitutionService;
        this.factsheetArchiveService = factsheetArchiveService;
    }

    @GetMapping("/{code}/reconstitution")
    public ResponseEntity<BenchmarkReconstitutionResponseDto> getReconstitution(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(reconstitutionService.calculateReconstitution(code, fromDate, toDate, knowledgeCutoff));
    }

    @GetMapping("/{code}/factsheet-archive")
    public ResponseEntity<com.yukira.backend.dto.portfolio.BenchmarkFactsheetArchiveResponseDto> getFactsheetArchive(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime knowledgeCutoff) {
        return ResponseEntity.ok(factsheetArchiveService.getFactsheetArchive(code, knowledgeCutoff));
    }
}
