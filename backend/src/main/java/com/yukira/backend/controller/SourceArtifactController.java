package com.yukira.backend.controller;

import com.yukira.backend.service.SourceArtifactService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/source-artifacts")
public class SourceArtifactController {

    private final SourceArtifactService sourceArtifactService;

    public SourceArtifactController(SourceArtifactService sourceArtifactService) {
        this.sourceArtifactService = sourceArtifactService;
    }

    @GetMapping("/recent")
    public ResponseEntity<List<SourceArtifactService.SourceArtifactSummaryDto>> listRecent(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(sourceArtifactService.listRecentArtifacts(limit));
    }

    @GetMapping("/{hash}")
    public ResponseEntity<SourceArtifactService.SourceArtifactDetailDto> getByHash(@PathVariable("hash") String hash) {
        return sourceArtifactService.getArtifactByHash(hash)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-id/{id}")
    public ResponseEntity<SourceArtifactService.SourceArtifactDetailDto> getById(@PathVariable("id") Long id) {
        return sourceArtifactService.getArtifactById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{hash}/download")
    public ResponseEntity<byte[]> downloadPayload(@PathVariable("hash") String hash) {
        return sourceArtifactService.getArtifactPayload(hash)
            .map(bytes -> ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"artifact_" + hash.substring(0, 8) + ".bin\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

