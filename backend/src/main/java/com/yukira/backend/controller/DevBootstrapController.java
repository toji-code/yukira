package com.yukira.backend.controller;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.service.PilotMarketCapDataService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yukira.backend.dto.ingestion.BenchmarkConstituentIngestionResultDto;
import com.yukira.backend.service.PilotBenchmarkConstituentDataService;
import java.util.List;

/**
 * Dedicated, intentional development & testing bootstrap endpoint.
 * Allows reproducible seeding of canonical pilot data without silent mutation during unit tests.
 */
@RestController
@RequestMapping("/api/v1/dev")
@ConditionalOnProperty(name = "yukira.dev-endpoints.enabled", havingValue = "true", matchIfMissing = false)
public class DevBootstrapController {

    private final PilotBootstrapService pilotBootstrapService;
    private final PilotMarketCapDataService pilotMarketCapDataService;
    private final PilotBenchmarkConstituentDataService pilotBenchmarkConstituentDataService;

    public DevBootstrapController(
        PilotBootstrapService pilotBootstrapService,
        PilotMarketCapDataService pilotMarketCapDataService,
        PilotBenchmarkConstituentDataService pilotBenchmarkConstituentDataService
    ) {
        this.pilotBootstrapService = pilotBootstrapService;
        this.pilotMarketCapDataService = pilotMarketCapDataService;
        this.pilotBenchmarkConstituentDataService = pilotBenchmarkConstituentDataService;
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

    @PostMapping("/bootstrap-market-cap")
    public ResponseEntity<PilotMarketCapDataService.SeedMarketCapSummary> bootstrapMarketCap() {
        var summary = pilotMarketCapDataService.seedAuthoritativeMarketCapClassifications();
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/bootstrap-benchmark-constituents")
    public ResponseEntity<List<BenchmarkConstituentIngestionResultDto>> bootstrapBenchmarkConstituents() {
        var results = pilotBenchmarkConstituentDataService.seedCanonicalBenchmarkConstituents();
        return ResponseEntity.ok(results);
    }
}
