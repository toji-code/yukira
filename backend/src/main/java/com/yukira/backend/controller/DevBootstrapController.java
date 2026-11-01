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
    private final com.yukira.backend.universe.ReferenceUniverseNavRecoveryService referenceUniverseNavRecoveryService;
    private final com.yukira.backend.service.PilotHoldingsDataService pilotHoldingsDataService;

    @org.springframework.beans.factory.annotation.Autowired
    public DevBootstrapController(
        PilotBootstrapService pilotBootstrapService,
        PilotMarketCapDataService pilotMarketCapDataService,
        PilotBenchmarkConstituentDataService pilotBenchmarkConstituentDataService,
        @org.springframework.beans.factory.annotation.Autowired(required = false) com.yukira.backend.universe.ReferenceUniverseNavRecoveryService referenceUniverseNavRecoveryService,
        @org.springframework.beans.factory.annotation.Autowired(required = false) com.yukira.backend.service.PilotHoldingsDataService pilotHoldingsDataService
    ) {
        this.pilotBootstrapService = pilotBootstrapService;
        this.pilotMarketCapDataService = pilotMarketCapDataService;
        this.pilotBenchmarkConstituentDataService = pilotBenchmarkConstituentDataService;
        this.referenceUniverseNavRecoveryService = referenceUniverseNavRecoveryService;
        this.pilotHoldingsDataService = pilotHoldingsDataService;
    }

    public DevBootstrapController(
        PilotBootstrapService pilotBootstrapService,
        PilotMarketCapDataService pilotMarketCapDataService,
        PilotBenchmarkConstituentDataService pilotBenchmarkConstituentDataService
    ) {
        this(pilotBootstrapService, pilotMarketCapDataService, pilotBenchmarkConstituentDataService, null, null);
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

    @PostMapping("/bootstrap-reference-universe-recovery")
    public ResponseEntity<com.yukira.backend.universe.NavRecoveryBatchExecutionResultDto> bootstrapReferenceUniverseRecovery(
        @org.springframework.web.bind.annotation.RequestParam(required = false) Integer batchSize,
        @org.springframework.web.bind.annotation.RequestParam(required = false) com.yukira.backend.universe.EquityUniverseCategory category,
        @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "true") Boolean recalculateProfiles
    ) {
        if (referenceUniverseNavRecoveryService == null) {
            return ResponseEntity.status(503).build();
        }
        int effectiveBatch = (batchSize != null && batchSize > 0) ? batchSize : 100;
        com.yukira.backend.universe.StoredArtifactRecoveryRequest req = new com.yukira.backend.universe.StoredArtifactRecoveryRequest(
            effectiveBatch, category, null, recalculateProfiles, java.time.LocalDate.of(2026, 10, 1), "dev-bootstrap"
        );
        return ResponseEntity.ok(referenceUniverseNavRecoveryService.executeStoredArtifactRecovery(req));
    }

    @PostMapping("/bootstrap-reference-universe-intervals")
    public ResponseEntity<com.yukira.backend.universe.NavRecoveryBatchExecutionResultDto> bootstrapReferenceUniverseIntervals(
        @org.springframework.web.bind.annotation.RequestParam(required = false) Integer batchSize,
        @org.springframework.web.bind.annotation.RequestParam(required = false) com.yukira.backend.universe.EquityUniverseCategory category,
        @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "true") Boolean recalculateProfiles,
        @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "10") Integer maxIntervalsPerFund
    ) {
        if (referenceUniverseNavRecoveryService == null) {
            return ResponseEntity.status(503).build();
        }
        int effectiveBatch = (batchSize != null && batchSize > 0) ? batchSize : 50;
        com.yukira.backend.universe.TargetedIntervalRecoveryRequest req = new com.yukira.backend.universe.TargetedIntervalRecoveryRequest(
            effectiveBatch, category, null, recalculateProfiles, java.time.LocalDate.of(2026, 10, 1), maxIntervalsPerFund, "dev-bootstrap"
        );
        return ResponseEntity.ok(referenceUniverseNavRecoveryService.executeTargetedIntervalRecovery(req));
    }

    @PostMapping("/bootstrap-holdings-cohort")
    public ResponseEntity<com.yukira.backend.service.PilotHoldingsDataService.SeedHoldingsCohortSummary> bootstrapHoldingsCohort() {
        if (pilotHoldingsDataService == null) {
            return ResponseEntity.status(503).build();
        }
        var summary = pilotHoldingsDataService.seedControlledPilotHoldingsCohort();
        return ResponseEntity.ok(summary);
    }
}
