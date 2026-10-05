package com.yukira.backend.controller;

import com.yukira.backend.ingestion.amfi.PeerHistoricalNavIngestionService;
import com.yukira.backend.scoring.population.PeerCohortIngestionReport;
import com.yukira.backend.scoring.population.PeerFundCoverageSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API for bounded peer cohort historical NAV batch ingestion.
 *
 * Part G of the bounded peer cohort production batch:
 * Triggers authoritative 3-year historical NAV ingestion for the identified
 * Flexi Cap Direct Growth peer cohort (2021-01-01 → 2024-01-15).
 *
 * Epistemic constraints:
 *  - Ingests only from the authoritative AMFI portal or session-local cache.
 *  - No interpolated, synthetic, or forward-filled observations.
 *  - Bitemporal immutability: duplicates skipped, revisions tracked with revision_seq.
 *  - Per-fund failure isolation: a single fund failure does not abort the batch.
 *  - Deterministic batch ordering by schemeOption ID.
 *  - Does NOT replace the provisional reference population INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1.
 */
@RestController
@RequestMapping("/api/v1/ingestion/peers")
@SuppressWarnings("null")
public class PeerIngestionController {

    private static final Logger log = LoggerFactory.getLogger(PeerIngestionController.class);

    // Phase 2H canonical analysis window
    private static final LocalDate CANONICAL_START = LocalDate.of(2021, 1, 1);
    private static final LocalDate CANONICAL_END   = LocalDate.of(2024, 1, 15);

    private final PeerHistoricalNavIngestionService peerIngestionService;

    public PeerIngestionController(PeerHistoricalNavIngestionService peerIngestionService) {
        this.peerIngestionService = peerIngestionService;
    }

    /**
     * Part G: Execute bounded peer cohort historical NAV ingestion for the
     * canonical Flexi Cap Direct Growth cohort (2021-01-01 → 2024-01-15).
     *
     * POST /api/v1/ingestion/peers/flexi-cap-cohort
     *
     * - Uses local session cache where available; falls back to live AMFI fetch.
     * - Annual chunked strategy to bypass AMFI 30 MB portal limits.
     * - Returns a PeerCohortIngestionReport with per-fund coverage summaries.
     *
     * WARNING: This is a long-running batch operation. Progress is logged server-side.
     *   Typical runtime: 2–15 minutes depending on AMC data volumes and network latency.
     */
    @PostMapping("/flexi-cap-cohort")
    public ResponseEntity<?> ingestFlexiCapCohort() {
        return ingestFlexiCapCohortRange(CANONICAL_START, CANONICAL_END);
    }

    /**
     * Part G (parameterized): Execute peer cohort ingestion for an explicit date range.
     *
     * POST /api/v1/ingestion/peers/flexi-cap-cohort/range?startDate=2021-01-01&endDate=2024-01-15
     */
    @PostMapping("/flexi-cap-cohort/range")
    public ResponseEntity<?> ingestFlexiCapCohortRange(
        @RequestParam(value = "startDate", defaultValue = "2021-01-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(value = "endDate", defaultValue = "2024-01-15")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_DATE_RANGE",
                "message", "startDate must not be after endDate."
            ));
        }

        try {
            PeerCohortIngestionReport report = peerIngestionService.ingestFlexiCapPeerCohort(startDate, endDate);

            // Build a structured response with key counts for quick inspection
            long sufficient = report.fundCoverages().stream()
                .filter(c -> "SUFFICIENT_HISTORY".equals(c.status())).count();
            long partial = report.fundCoverages().stream()
                .filter(c -> "PARTIAL_HISTORY".equals(c.status())).count();
            long zero = report.fundCoverages().stream()
                .filter(c -> "INSUFFICIENT_HISTORY".equals(c.status()) || "UNMAPPED_AMC_OR_NEW_FUND".equals(c.status())).count();
            long failed = report.fundCoverages().stream()
                .filter(c -> "INGESTION_FAILED".equals(c.status())).count();

            return ResponseEntity.ok(Map.of(
                "runId",         report.runId(),
                "cohortId",      report.cohortId(),
                "analysisWindow", Map.of(
                    "startDate", report.startDate().toString(),
                    "endDate",   report.endDate().toString()
                ),
                "summary", Map.of(
                    "totalCandidates",          report.requestedFundsCount(),
                    "sufficientHistory_ge700",   sufficient,
                    "partialHistory_1to699",     partial,
                    "zeroOrUnmapped",            zero,
                    "ingestionFailed",           failed,
                    "totalObservationsIngested", report.totalObservationsIngested(),
                    "totalDuplicatesSkipped",    report.totalDuplicatesSkipped()
                ),
                "perFundCoverages", report.fundCoverages().stream().map(c -> {
                    // Map.of() forbids null values, and firstObservationDate / lastObservationDate
                    // are legitimately null for unmapped or zero-observation funds. A null here
                    // must never abort the whole cohort response.
                    Map<String, Object> fund = new LinkedHashMap<>();
                    fund.put("schemeOptionId", c.schemeOptionId());
                    fund.put("amfiCode",      c.amfiCode() != null ? c.amfiCode() : "");
                    fund.put("isin",          c.isin() != null ? c.isin() : "");
                    fund.put("amcCode",       c.amcCode() != null ? c.amcCode() : "");
                    fund.put("schemeName",    c.schemeName() != null ? c.schemeName() : "");
                    fund.put("observationCount", c.observationCount());
                    fund.put("firstDate",     c.firstObservationDate() != null ? c.firstObservationDate().toString() : null);
                    fund.put("lastDate",      c.lastObservationDate() != null ? c.lastObservationDate().toString() : null);
                    fund.put("status",        c.status());
                    fund.put("meetsThreshold", c.meetsThreshold());
                    return fund;
                }).toList(),
                "messages", report.messages(),
                "epistemicStatus",
                    sufficient >= 25 ? "COHORT_SUFFICIENT_FOR_EMPIRICAL_STUDY" : "COHORT_INSUFFICIENT_FOR_EMPIRICAL_CALIBRATION",
                "governanceNote",
                    "DATA LOADED != EMPIRICALLY VALIDATED != METHODOLOGY APPROVED. " +
                    "Reference population INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1 is retained. " +
                    "Empirical distribution calibration requires >= 25 funds with SUFFICIENT_HISTORY."
            ));
        } catch (Exception e) {
            // e.getMessage() may legitimately be null. Reporting must never mask the root cause
            // with a secondary NullPointerException from Map.of().
            log.error("Peer cohort ingestion failed for window {}..{}", startDate, endDate, e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "PEER_COHORT_INGESTION_FAILED");
            error.put("exception", e.getClass().getName());
            error.put("message", e.getMessage() != null ? e.getMessage() : "NO_MESSAGE_PROVIDED");
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * GET /api/v1/ingestion/peers/flexi-cap-cohort/coverage
     *
     * Dry-run: evaluates cohort coverage from the current database state without
     * triggering any ingestion. Delegates to PeerPopulationService.
     */
    @GetMapping("/flexi-cap-cohort/coverage")
    public ResponseEntity<?> getFlexiCapCohortCoverage() {
        return ResponseEntity.ok(Map.of(
            "instruction", "Use GET /api/v1/scores/reference-population for the full empirical readiness evaluation report.",
            "cohortId",    PeerHistoricalNavIngestionService.COHORT_ID,
            "governanceNote",
                "Cohort coverage is evaluated via PeerPopulationService. " +
                "No ingestion is performed by this endpoint."
        ));
    }
}
