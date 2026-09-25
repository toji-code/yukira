package com.yukira.backend.controller;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dedicated, intentional development & testing bootstrap endpoint.
 * Allows reproducible seeding of canonical pilot data without silent mutation during unit tests.
 */
@RestController
@RequestMapping("/api/v1/dev")
@ConditionalOnProperty(name = "yukira.dev-endpoints.enabled", havingValue = "true", matchIfMissing = false)
public class DevBootstrapController {

    private final PilotBootstrapService pilotBootstrapService;

    public DevBootstrapController(PilotBootstrapService pilotBootstrapService) {
        this.pilotBootstrapService = pilotBootstrapService;
    }

    @PostMapping("/bootstrap-pilot")
    public ResponseEntity<PilotBootstrapService.BootstrapReport> bootstrapPilot() {
        PilotBootstrapService.BootstrapReport report = pilotBootstrapService.bootstrapPilot();
        return ResponseEntity.ok(report);
    }

    @PostMapping("/bootstrap-historical")
    public ResponseEntity<PilotBootstrapService.HistoricalBootstrapReport> bootstrapHistorical() {
        PilotBootstrapService.HistoricalBootstrapReport report = pilotBootstrapService.bootstrapHistoricalHorizon(5);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/bootstrap-historical-fbil")
    public ResponseEntity<com.yukira.backend.ingestion.fbil.FbilTBillIngestionService.FbilIngestionSummary> bootstrapHistoricalFbil() {
        var summary = pilotBootstrapService.bootstrapHistoricalFbil();
        return ResponseEntity.ok(summary);
    }
}
