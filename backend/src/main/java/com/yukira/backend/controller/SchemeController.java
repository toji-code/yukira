package com.yukira.backend.controller;

import com.yukira.backend.domain.entity.Amc;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.ingestion.amfi.AmfiUniverseIngestionService;
import com.yukira.backend.ingestion.amfi.HistoricalNavIngestionService;
import com.yukira.backend.ingestion.amfi.UniverseCoverageReport;
import com.yukira.backend.ingestion.amfi.UniverseIngestionSummary;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.PortfolioSnapshotRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SchemeRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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
    private final NavObservationRepository navObservationRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final com.yukira.backend.service.DataQualityFreshnessService dataQualityFreshnessService;

    public SchemeController(SchemeRepository schemeRepository, SchemeOptionRepository schemeOptionRepository) {
        this(schemeRepository, schemeOptionRepository, null, null, null, null, null, null);
    }

    public SchemeController(
        SchemeRepository schemeRepository,
        SchemeOptionRepository schemeOptionRepository,
        AmfiUniverseIngestionService universeIngestionService
    ) {
        this(schemeRepository, schemeOptionRepository, universeIngestionService, null, null, null, null, null);
    }

    public SchemeController(
        SchemeRepository schemeRepository,
        SchemeOptionRepository schemeOptionRepository,
        AmfiUniverseIngestionService universeIngestionService,
        HistoricalNavIngestionService historicalNavIngestionService,
        com.yukira.backend.scoring.service.AnalyticalScoringService scoringService,
        NavObservationRepository navObservationRepository,
        PortfolioSnapshotRepository portfolioSnapshotRepository
    ) {
        this(schemeRepository, schemeOptionRepository, universeIngestionService, historicalNavIngestionService, scoringService, navObservationRepository, portfolioSnapshotRepository, null);
    }

    @Autowired
    public SchemeController(
        SchemeRepository schemeRepository,
        SchemeOptionRepository schemeOptionRepository,
        @Autowired(required = false) AmfiUniverseIngestionService universeIngestionService,
        @Autowired(required = false) HistoricalNavIngestionService historicalNavIngestionService,
        @Autowired(required = false) com.yukira.backend.scoring.service.AnalyticalScoringService scoringService,
        @Autowired(required = false) NavObservationRepository navObservationRepository,
        @Autowired(required = false) PortfolioSnapshotRepository portfolioSnapshotRepository,
        @Autowired(required = false) com.yukira.backend.service.DataQualityFreshnessService dataQualityFreshnessService
    ) {
        this.schemeRepository = schemeRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.universeIngestionService = universeIngestionService;
        this.historicalNavIngestionService = historicalNavIngestionService;
        this.scoringService = scoringService;
        this.navObservationRepository = navObservationRepository;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.dataQualityFreshnessService = dataQualityFreshnessService;
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
        Specification<Scheme> spec = (root, criteriaQuery, cb) -> {
            if (criteriaQuery.getResultType() != null && Scheme.class.isAssignableFrom(criteriaQuery.getResultType())) {
                root.fetch("amc", JoinType.LEFT);
            }
            List<Predicate> predicates = new ArrayList<>();
            Join<Scheme, Amc> amcJoin = root.join("amc", JoinType.LEFT);

            if (amcId != null) {
                predicates.add(cb.equal(amcJoin.get("id"), amcId));
            }
            if (amcCode != null && !amcCode.isBlank()) {
                predicates.add(cb.equal(cb.upper(amcJoin.get("code")), amcCode.toUpperCase()));
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
                Predicate searchPred = cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("code")), pattern),
                    cb.like(cb.lower(amcJoin.get("legalName")), pattern),
                    cb.like(cb.lower(amcJoin.get("code")), pattern)
                );
                Subquery<Long> optSub = criteriaQuery.subquery(Long.class);
                Root<SchemeOption> optRoot = optSub.from(SchemeOption.class);
                optSub.select(optRoot.get("plan").get("scheme").get("id"))
                      .where(cb.or(
                          cb.like(cb.lower(optRoot.get("amfiCode")), pattern),
                          cb.like(cb.lower(optRoot.get("isin")), pattern)
                      ));
                predicates.add(cb.or(searchPred, root.get("id").in(optSub)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Scheme> schemes = schemeRepository.findAll(spec);
        if (scoringService != null) {
            if (schemes.size() <= 100) {
                for (Scheme s : schemes) {
                    s.setYukiraScore(scoringService.getLatestScoreSummaryForScheme(s.getId()));
                }
            } else {
                Map<Long, com.yukira.backend.scoring.dto.YukiraScoreSummary> scoreMap = scoringService.getLatestScoreSummariesGroupedBySchemeId();
                for (Scheme s : schemes) {
                    s.setYukiraScore(scoreMap.get(s.getId()));
                }
            }
        }
        return ResponseEntity.ok(schemes);
    }

    @GetMapping("/{identifier}")
    public ResponseEntity<Scheme> getSchemeByIdentifier(@PathVariable String identifier) {
        java.util.Optional<Scheme> schemeOpt = java.util.Optional.empty();
        try {
            Long id = Long.parseLong(identifier);
            schemeOpt = schemeRepository.findById(id);
        } catch (NumberFormatException ignored) {}
        if (schemeOpt.isEmpty()) {
            schemeOpt = schemeRepository.findByCode(identifier);
        }
        return schemeOpt
            .map(scheme -> {
                if (scoringService != null) {
                    scheme.setYukiraScore(scoringService.getLatestScoreSummaryForScheme(scheme.getId()));
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
                    cb.like(cb.lower(schemeJoin.get("code")), pattern),
                    cb.like(cb.lower(amcJoin.get("legalName")), pattern),
                    cb.like(cb.lower(amcJoin.get("code")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<SchemeOption> options = schemeOptionRepository.findAll(spec);
        if (scoringService != null) {
            if (options.size() <= 100) {
                for (SchemeOption opt : options) {
                    opt.setYukiraScore(scoringService.getLatestScoreSummaryForOption(opt.getId()));
                }
            } else {
                Map<Long, com.yukira.backend.scoring.dto.YukiraScoreSummary> scoreMap = scoringService.getLatestScoreSummariesGroupedByOptionId();
                for (SchemeOption opt : options) {
                    opt.setYukiraScore(scoreMap.get(opt.getId()));
                }
            }
        }
        return ResponseEntity.ok(options);
    }

    @GetMapping("/{identifier}/options")
    public ResponseEntity<List<SchemeOption>> getOptionsBySchemeIdentifier(@PathVariable String identifier) {
        Long schemeId = null;
        try {
            schemeId = Long.parseLong(identifier);
        } catch (NumberFormatException ignored) {}
        if (schemeId == null) {
            java.util.Optional<Scheme> schemeOpt = schemeRepository.findByCode(identifier);
            if (schemeOpt.isPresent()) {
                schemeId = schemeOpt.get().getId();
            } else {
                return ResponseEntity.notFound().build();
            }
        }
        List<SchemeOption> options = schemeOptionRepository.findByPlanSchemeId(schemeId);
        if (scoringService != null) {
            for (SchemeOption opt : options) {
                opt.setYukiraScore(scoringService.getLatestScoreSummaryForOption(opt.getId()));
            }
        }
        for (SchemeOption opt : options) {
            if (dataQualityFreshnessService != null) {
                dataQualityFreshnessService.enrichOptionFreshness(opt, null, null);
            } else {
                if (navObservationRepository != null) {
                    navObservationRepository.findLatestActiveObservation(opt.getId()).ifPresent(nav -> {
                        if (nav.getEffectiveDate() != null) {
                            opt.setLatestNavDate(nav.getEffectiveDate().toString());
                        }
                    });
                    long count = navObservationRepository.countBySchemeOptionId(opt.getId());
                    if (count > 0) {
                        opt.setNavObservationCount(count);
                    }
                }
                if (portfolioSnapshotRepository != null) {
                    portfolioSnapshotRepository.findTopBySchemeOptionIdOrderByPortfolioDateDesc(opt.getId()).ifPresent(snap -> {
                        if (snap.getPortfolioDate() != null) {
                            opt.setLatestHoldingsDate(snap.getPortfolioDate().toString());
                        }
                        opt.setHoldingsCount(snap.getReportedHoldingsCount());
                    });
                }
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
