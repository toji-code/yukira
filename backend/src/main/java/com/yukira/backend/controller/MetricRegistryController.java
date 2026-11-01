package com.yukira.backend.controller;

import com.yukira.backend.dto.metric.AuthoritativeMetricDefinitionDto;
import com.yukira.backend.dto.metric.MetricAvailabilityEvaluationDto;
import com.yukira.backend.service.AuthoritativeMetricRegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller exposing the Authoritative Metric Registry.
 * Consumed by both internal services and frontend client applications to ensure
 * total epistemic and quantitative alignment.
 *
 * Mapped to both /api/v1/metrics/registry and /api/v1/metrics for cross-platform consistency.
 */
@RestController
@RequestMapping({"/api/v1/metrics/registry", "/api/v1/metrics", "/api/v1/registry/metrics"})
public class MetricRegistryController {

    private final AuthoritativeMetricRegistryService registryService;

    public MetricRegistryController(AuthoritativeMetricRegistryService registryService) {
        this.registryService = registryService;
    }

    @GetMapping
    public ResponseEntity<List<AuthoritativeMetricDefinitionDto>> getAllMetrics() {
        return ResponseEntity.ok(registryService.getAllDefinitions());
    }

    @GetMapping("/analytics")
    public ResponseEntity<List<AuthoritativeMetricDefinitionDto>> getAnalyticalMetrics() {
        return ResponseEntity.ok(registryService.getAnalyticalMetrics());
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<List<AuthoritativeMetricDefinitionDto>> getOperationalDiagnostics() {
        return ResponseEntity.ok(registryService.getOperationalDiagnostics());
    }

    @GetMapping("/availability/{schemeOptionId}")
    public ResponseEntity<MetricAvailabilityEvaluationDto.SchemeMetricAvailabilityReport> getMetricAvailability(
        @PathVariable Long schemeOptionId,
        @RequestParam(required = false) String asOfDate,
        @RequestParam(required = false) String knowledgeCutoffTime
    ) {
        LocalDate asOf = (asOfDate != null && !asOfDate.isBlank()) ? LocalDate.parse(asOfDate) : LocalDate.now();
        OffsetDateTime cutoff = (knowledgeCutoffTime != null && !knowledgeCutoffTime.isBlank())
            ? OffsetDateTime.parse(knowledgeCutoffTime)
            : OffsetDateTime.now();
        return ResponseEntity.ok(registryService.evaluateMetricAvailability(schemeOptionId, asOf, cutoff));
    }

    @GetMapping("/domains/{domain}")
    public ResponseEntity<List<AuthoritativeMetricDefinitionDto>> getMetricsByDomain(@PathVariable String domain) {
        return ResponseEntity.ok(registryService.getDefinitionsByDomain(domain));
    }

    @GetMapping("/{code}")
    public ResponseEntity<AuthoritativeMetricDefinitionDto> getMetricByCode(@PathVariable String code) {
        return registryService.getDefinition(code)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{code}/diagnostics")
    public ResponseEntity<List<AuthoritativeMetricDefinitionDto>> getDiagnosticsForMetric(@PathVariable String code) {
        return ResponseEntity.ok(registryService.getDiagnosticsForMetric(code));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Map<String, Object>>> getRegistrySummary() {
        Map<String, Map<String, Object>> summary = registryService.getAllDefinitions().stream()
            .collect(Collectors.toMap(
                AuthoritativeMetricDefinitionDto::metricCode,
                def -> Map.of(
                    "name", def.canonicalName(),
                    "category", def.categoryLabel() != null ? def.categoryLabel() : def.analyticalDimension(),
                    "governanceStatus", def.governanceStatus(),
                    "rankingDirection", def.rankingDirection(),
                    "horizon", def.measurementHorizon(),
                    "unit", def.unit() != null ? def.unit() : "PERCENTAGE",
                    "isDiagnostic", Boolean.TRUE.equals(def.isDiagnostic())
                )
            ));
        return ResponseEntity.ok(summary);
    }
}
