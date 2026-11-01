package com.yukira.backend.controller;

import com.yukira.backend.dto.telemetry.OperationalJobExecutionDto;
import com.yukira.backend.dto.telemetry.V1PlatformReadinessDto;
import com.yukira.backend.service.OperationalTelemetryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for background job telemetry, dead-letter recovery, and V1 platform readiness (Tasks 206–209).
 */
@RestController
@RequestMapping("/api/v1/admin/telemetry")
public class OperationalTelemetryController {

    private final OperationalTelemetryService telemetryService;

    public OperationalTelemetryController(OperationalTelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    /**
     * Retrieves recent operational job execution telemetry (Task 207).
     */
    @GetMapping("/jobs")
    public ResponseEntity<List<OperationalJobExecutionDto>> getJobExecutions(
        @RequestParam(required = false) String jobName,
        @RequestParam(required = false, defaultValue = "50") int limit
    ) {
        List<OperationalJobExecutionDto> jobs = telemetryService.getRecentJobExecutions(jobName, limit);
        return ResponseEntity.ok(jobs);
    }

    /**
     * Retrieves formal V1 Platform Release Readiness and Governance Verification (Task 209).
     */
    @GetMapping("/readiness")
    public ResponseEntity<V1PlatformReadinessDto> getPlatformReadiness() {
        V1PlatformReadinessDto readiness = telemetryService.evaluatePlatformReadiness();
        return ResponseEntity.ok(readiness);
    }
}

