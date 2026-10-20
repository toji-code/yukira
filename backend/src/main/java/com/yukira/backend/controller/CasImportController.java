package com.yukira.backend.controller;

import com.yukira.backend.dto.portfolio.CasImportConfirmResponseDto;
import com.yukira.backend.dto.portfolio.CasImportResultDto;
import com.yukira.backend.service.portfolio.cas.CasImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/investor/portfolio/imports")
public class CasImportController {

    private final CasImportService importService;

    public CasImportController(CasImportService importService) {
        this.importService = importService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CasImportResultDto> uploadCasFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password,
            @RequestHeader(value = "X-Dev-Subject", required = false, defaultValue = "dev-investor-local") String auth0Subject) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try (InputStream is = file.getInputStream()) {
            CasImportResultDto result = importService.stageImport(auth0Subject, file.getOriginalFilename(), is, password);
            return ResponseEntity.ok(result);
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CasImportResultDto> stageCasJson(
            @RequestBody String rawJson,
            @RequestParam(required = false, defaultValue = "cams_cas_import.json") String filename,
            @RequestHeader(value = "X-Dev-Subject", required = false, defaultValue = "dev-investor-local") String auth0Subject) {
        ByteArrayInputStream is = new ByteArrayInputStream(rawJson.getBytes(StandardCharsets.UTF_8));
        CasImportResultDto result = importService.stageImport(auth0Subject, filename, is);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CasImportResultDto> getImportDetails(
            @PathVariable Long id,
            @RequestHeader(value = "X-Dev-Subject", required = false, defaultValue = "dev-investor-local") String auth0Subject) {
        return ResponseEntity.ok(importService.getImportDetails(auth0Subject, id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<CasImportConfirmResponseDto> confirmImport(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "MERGE") String mode,
            @RequestHeader(value = "X-Dev-Subject", required = false, defaultValue = "dev-investor-local") String auth0Subject) {
        return ResponseEntity.ok(importService.confirmImport(auth0Subject, id, mode));
    }

    @GetMapping
    public ResponseEntity<List<CasImportResultDto>> listImports(
            @RequestHeader(value = "X-Dev-Subject", required = false, defaultValue = "dev-investor-local") String auth0Subject) {
        return ResponseEntity.ok(importService.listInvestorImports(auth0Subject));
    }
}
