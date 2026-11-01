package com.yukira.backend.controller;

import com.yukira.backend.dto.portfolio.CasImportConfirmResponseDto;
import com.yukira.backend.dto.portfolio.CasImportResultDto;
import com.yukira.backend.service.portfolio.cas.CasImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String auth0Subject = resolveSubject(jwt, authentication);
        try (InputStream is = file.getInputStream()) {
            CasImportResultDto result = importService.stageImport(auth0Subject, file.getOriginalFilename(), is, password);
            return ResponseEntity.ok(result);
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CasImportResultDto> stageCasJson(
            @RequestBody String rawJson,
            @RequestParam(required = false, defaultValue = "cams_cas_import.json") String filename,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        String auth0Subject = resolveSubject(jwt, authentication);
        ByteArrayInputStream is = new ByteArrayInputStream(rawJson.getBytes(StandardCharsets.UTF_8));
        CasImportResultDto result = importService.stageImport(auth0Subject, filename, is);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CasImportResultDto> getImportDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        String auth0Subject = resolveSubject(jwt, authentication);
        return ResponseEntity.ok(importService.getImportDetails(auth0Subject, id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<CasImportConfirmResponseDto> confirmImport(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "MERGE") String mode,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        String auth0Subject = resolveSubject(jwt, authentication);
        return ResponseEntity.ok(importService.confirmImport(auth0Subject, id, mode));
    }

    @GetMapping
    public ResponseEntity<List<CasImportResultDto>> listImports(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {
        String auth0Subject = resolveSubject(jwt, authentication);
        return ResponseEntity.ok(importService.listInvestorImports(auth0Subject));
    }

    private String resolveSubject(Jwt jwt, Authentication authentication) {
        if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            return jwt.getSubject();
        }
        if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null
                && !authentication.getName().isBlank() && !"anonymousUser".equalsIgnoreCase(authentication.getName())) {
            return authentication.getName();
        }
        throw new AccessDeniedException("Authenticated investor principal required.");
    }
}
