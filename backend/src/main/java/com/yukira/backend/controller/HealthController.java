package com.yukira.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "yukira-backend",
            "version", "0.0.1-SNAPSHOT",
            "methodology_status", "STRICTLY EMPTY",
            "empirical_findings", "EXACTLY ZERO"
        ));
    }
}
