package com.yukira.backend.controller;

import com.yukira.backend.domain.entity.Amc;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.ingestion.amfi.AmfiUniverseIngestionService;
import com.yukira.backend.ingestion.amfi.HistoricalNavIngestionService;
import com.yukira.backend.ingestion.amfi.UniverseCoverageReport;
import com.yukira.backend.ingestion.amfi.UniverseIngestionSummary;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SchemeRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller exposing REST endpoints for mutual fund universe discovery,
 * entity retrieval, coverage reporting, and AMFI universe ingestion.
 */
@RestController
@RequestMapping("/api/v1/schemes")
public class SchemeController {

    private final SchemeRepository schemeRepository;
    private final SchemeOptionRepository schemeOptionRepository;
    private final AmfiUniverseIngestionService universeIngestionService;
    private final HistoricalNavIngestionService historicalNavIngestionService;
    private final com.yukira.backend.scoring.service.AnalyticalScoringService scoringService;

    public SchemeController(SchemeRepository schemeRepository, SchemeOptionRepository schemeOptionRepository) {
        this(schemeRepository, schemeOptionRepository, null, null, null);
    }

    public SchemeController(
        SchemeRepository schemeRepository,
        SchemeOptionRepository schemeOptionRepository,
        AmfiUniverseIngestionService universeIngestionService
    ) {
        this(schemeRepository, schemeOptionRepository, universeIngestionService, null, null);
    }

    @Autowired
    public SchemeController(
        SchemeRepository schemeRepository,
        SchemeOptionRepository schemeOptionRepository,
        @Autowired(required = false) AmfiUniverseIngestionService universeIngestionService,
        @Autowired(required = false) HistoricalNavIngestionService historicalNavIngestionService,
        @Autowired(required = false) com.yukira.backend.scoring.service.AnalyticalScoringService scoringService
    ) {
        this.schemeRepository = schemeRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.universeIngestionService = universeIngestionService;
        this.historicalNavIngestionService = historicalNavIngestionService;
        this.scoringService = scoringService;
    }

    /**
     * Discovers schemes with optional filtering by AMC, category, subcategory, status, or search query.
     */
    @GetMapping
    public ResponseEntity<List<Scheme>> getAllSchemes(
        @RequestParam(required = false) Long amcId,
        @RequestParam(required = false) String amcCode,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String subcategory,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String search
    ) {
        Specification<Scheme> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (amcId != null) {
                predicates.add(cb.equal(root.get("amc").get("id"), amcId));
            }
            if (amcCode != null && !amcCode.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("amc").get("code")), amcCode.toUpperCase()));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("category")), "%" + category.toLowerCase() + "%"));
            }
            if (subcategory != null && !subcategory.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("subcategory")), "%" + subcategory.toLowerCase() + "%"));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status.toUpperCase()));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("code")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Scheme> schemes = schemeRepository.findAll(spec);
        if (scoringService != null) {
            Map<Long, com.yukira.backend.scoring.dto.YukiraScoreSummary> scoreMap = scoringService.getLatestScoreSummariesGroupedBySchemeId();
            for (Scheme s : schemes) {
                s.setYukiraScore(scoreMap.get(s.getId()));
            }
        }
        return ResponseEntity.ok(schemes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Scheme> getSchemeById(@PathVariable Long id) {
        return schemeRepository.findById(id)
            .map(scheme -> {
                if (scoringService != null) {
                    Map<Long, com.yukira.backend.scoring.dto.YukiraScoreSummary> scoreMap = scoringService.getLatestScoreSummariesGroupedBySchemeId();
                    scheme.setYukiraScore(scoreMap.get(scheme.getId()));
                }
                return ResponseEntity.ok(scheme);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Discovers scheme options across the expanded universe with rich multi-attribute filtering.
     * Supports: amc, category, subcategory, planType (Direct/Regular), optionType (Growth/IDCW),
     * amfiCode, isin, status (active/inactive), and free text search.
     */
    @GetMapping("/options")
    public ResponseEntity<List<SchemeOption>> getAllOptions(
        @RequestParam(required = false) Long schemeId,
        @RequestParam(required = false) Long amcId,
        @RequestParam(required = false) String amcCode,
        @RequestParam(required = false) String planType,
        @RequestParam(required = false) String optionType,
        @RequestParam(required = false) String amfiCode,
        @RequestParam(required = false) String isin,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String subcategory,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String search
    ) {
        Specification<SchemeOption> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // The serialized option payload embeds plan.scheme, so resolving it lazily would
            // issue one SELECT per row across the expanded universe (14k+ options). Fetch-join
            // that serialization path up front. plan and scheme are to-one associations, so
            // these additional join paths cannot multiply result rows; the typed joins below
            // remain solely for filtering. amc is left lazy because Scheme.amc is @JsonIgnore.
            root.fetch("plan", JoinType.LEFT).fetch("scheme", JoinType.LEFT);

            Join<SchemeOption, SchemePlan> planJoin = root.join("plan", JoinType.LEFT);
            Join<SchemePlan, Scheme> schemeJoin = planJoin.join("scheme", JoinType.LEFT);
            Join<Scheme, Amc> amcJoin = schemeJoin.join("amc", JoinType.LEFT);

            if (schemeId != null) {
                predicates.add(cb.equal(schemeJoin.get("id"), schemeId));
            }
            if (amcId != null) {
                predicates.add(cb.equal(amcJoin.get("id"), amcId));
            }
            if (amcCode != null && !amcCode.isBlank()) {
                predicates.add(cb.equal(cb.upper(amcJoin.get("code")), amcCode.toUpperCase()));
            }
            if (planType != null && !planType.isBlank()) {
                predicates.add(cb.equal(cb.upper(planJoin.get("planType")), planType.toUpperCase()));
            }
            if (optionType != null && !optionType.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("optionType")), optionType.toUpperCase()));
            }
            if (amfiCode != null && !amfiCode.isBlank()) {
                predicates.add(cb.equal(root.get("amfiCode"), amfiCode.trim()));
            }
            if (isin != null && !isin.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("isin")), isin.trim().toUpperCase()));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.like(cb.lower(schemeJoin.get("category")), "%" + category.toLowerCase() + "%"));
            }
            if (subcategory != null && !subcategory.isBlank()) {
                predicates.add(cb.like(cb.lower(schemeJoin.get("subcategory")), "%" + subcategory.toLowerCase() + "%"));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), status.toUpperCase()));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("amfiCode")), pattern),
                    cb.like(cb.lower(root.get("isin")), pattern),
                    cb.like(cb.lower(schemeJoin.get("name")), pattern),
                    cb.like(cb.lower(schemeJoin.get("code")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<SchemeOption> options = schemeOptionRepository.findAll(spec);
        if (scoringService != null) {
            Map<Long, com.yukira.backend.scoring.dto.YukiraScoreSummary> scoreMap = scoringService.getLatestScoreSummariesGroupedByOptionId();
            for (SchemeOption opt : options) {
                opt.setYukiraScore(scoreMap.get(opt.getId()));
            }
        }
        return ResponseEntity.ok(options);
    }

    @GetMapping("/{id}/options")
    public ResponseEntity<List<SchemeOption>> getOptionsBySchemeId(@PathVariable Long id) {
        List<SchemeOption> options = schemeOptionRepository.findByPlanSchemeId(id);
        if (scoringService != null) {
            Map<Long, com.yukira.backend.scoring.dto.YukiraScoreSummary> scoreMap = scoringService.getLatestScoreSummariesGroupedByOptionId();
            for (SchemeOption opt : options) {
                opt.setYukiraScore(scoreMap.get(opt.getId()));
            }
        }
        return ResponseEntity.ok(options);
    }

    /**
     * Ingests a raw AMFI text feed to expand the mutual fund universe catalog.
     */
    @PostMapping("/ingest-universe")
    public ResponseEntity<UniverseIngestionSummary> ingestUniverse(@RequestBody String payload) {
        if (universeIngestionService == null) {
            return ResponseEntity.status(503).build();
        }
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        UniverseIngestionSummary summary = universeIngestionService.ingestUniversePayload(bytes, "api://ingest-universe");
        return ResponseEntity.ok(summary);
    }

    /**
     * Fetches and ingests the live AMFI daily universe directly from portal.amfiindia.com.
     */
    @PostMapping("/ingest-live-universe")
    public ResponseEntity<UniverseIngestionSummary> ingestLiveUniverse() {
        if (universeIngestionService == null) {
            return ResponseEntity.status(503).build();
        }
        UniverseIngestionSummary summary = universeIngestionService.fetchAndIngestLiveUniverse();
        return ResponseEntity.ok(summary);
    }

    /**
     * Retrieves Part G machine-readable catalog coverage, data-quality findings, and historical NAV status.
     */
    @GetMapping("/universe-summary")
    public ResponseEntity<UniverseCoverageReport> getUniverseSummary() {
        if (universeIngestionService == null) {
            return ResponseEntity.status(503).build();
        }
        Map<String, Object> controlledCoverage = historicalNavIngestionService != null
            ? historicalNavIngestionService.getControlledSampleCoverage()
            : Map.of();
        return ResponseEntity.ok(universeIngestionService.generateUniverseCoverageReport(controlledCoverage));
    }

    /**
     * Ingests the Part D controlled sample of historical NAV observations across 5 representative funds.
     */
    @PostMapping("/ingest-controlled-nav-sample")
    public ResponseEntity<Map<String, Object>> ingestControlledNavSample() {
        if (historicalNavIngestionService == null) {
            return ResponseEntity.status(503).build();
        }
        Map<String, Object> result = historicalNavIngestionService.ingestControlledSample();
        return ResponseEntity.ok(result);
    }
}
