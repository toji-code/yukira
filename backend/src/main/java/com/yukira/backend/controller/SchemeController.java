package com.yukira.backend.controller;

import com.yukira.backend.domain.entity.Amc;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.ingestion.amfi.AmfiUniverseIngestionService;
import com.yukira.backend.ingestion.amfi.HistoricalNavIngestionService;
import com.yukira.backend.ingestion.amfi.UniverseCoverageReport;
import com.yukira.backend.ingestion.amfi.UniverseIngestionSummary;
import com.yukira.backend.ingestion.nse.NiftyBenchmarkIngestionService;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.PortfolioSnapshotRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SchemeRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import com.yukira.backend.service.SchemeMetadataRevisionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
    private final com.yukira.backend.universe.ReferenceUniverseService referenceUniverseService;
    private final com.yukira.backend.universe.HistorySufficiencyService historySufficiencyService;
    private final com.yukira.backend.benchmark.BenchmarkDateAlignmentService benchmarkDateAlignmentService;
    private final com.yukira.backend.universe.UniverseBatchOrchestratorService universeBatchOrchestratorService;
    private final com.yukira.backend.universe.UniverseScreenerService universeScreenerService;
    @Autowired(required = false)
    private NiftyBenchmarkIngestionService benchmarkIngestionService;

    @Autowired(required = false)
    private com.yukira.backend.service.UnifiedFundProfileService unifiedFundProfileService;

    @Autowired(required = false)
    private com.yukira.backend.service.SchemeIdentityResolutionService schemeIdentityResolutionService;

    @Autowired(required = false)
    private com.yukira.backend.service.discovery.FundDiscoveryFilterService fundDiscoveryFilterService;

    public SchemeController(SchemeRepository schemeRepository, SchemeOptionRepository schemeOptionRepository) {
        this(schemeRepository, schemeOptionRepository, null, null, null, null, null, null, null, null, null, null, null);
    }

    public SchemeController(
        SchemeRepository schemeRepository,
        SchemeOptionRepository schemeOptionRepository,
        AmfiUniverseIngestionService universeIngestionService
    ) {
        this(schemeRepository, schemeOptionRepository, universeIngestionService, null, null, null, null, null, null, null, null, null, null);
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
        this(schemeRepository, schemeOptionRepository, universeIngestionService, historicalNavIngestionService, scoringService, navObservationRepository, portfolioSnapshotRepository, null, null, null, null, null, null);
    }

    public SchemeController(
        SchemeRepository schemeRepository,
        SchemeOptionRepository schemeOptionRepository,
        AmfiUniverseIngestionService universeIngestionService,
        HistoricalNavIngestionService historicalNavIngestionService,
        com.yukira.backend.scoring.service.AnalyticalScoringService scoringService,
        NavObservationRepository navObservationRepository,
        PortfolioSnapshotRepository portfolioSnapshotRepository,
        com.yukira.backend.service.DataQualityFreshnessService dataQualityFreshnessService,
        com.yukira.backend.universe.ReferenceUniverseService referenceUniverseService
    ) {
        this(schemeRepository, schemeOptionRepository, universeIngestionService, historicalNavIngestionService, scoringService, navObservationRepository, portfolioSnapshotRepository, dataQualityFreshnessService, referenceUniverseService, null, null, null, null);
    }

    public SchemeController(
        SchemeRepository schemeRepository,
        SchemeOptionRepository schemeOptionRepository,
        AmfiUniverseIngestionService universeIngestionService,
        HistoricalNavIngestionService historicalNavIngestionService,
        com.yukira.backend.scoring.service.AnalyticalScoringService scoringService,
        NavObservationRepository navObservationRepository,
        PortfolioSnapshotRepository portfolioSnapshotRepository,
        com.yukira.backend.service.DataQualityFreshnessService dataQualityFreshnessService,
        com.yukira.backend.universe.ReferenceUniverseService referenceUniverseService,
        com.yukira.backend.universe.HistorySufficiencyService historySufficiencyService
    ) {
        this(schemeRepository, schemeOptionRepository, universeIngestionService, historicalNavIngestionService, scoringService, navObservationRepository, portfolioSnapshotRepository, dataQualityFreshnessService, referenceUniverseService, historySufficiencyService, null, null, null);
    }

    public SchemeController(
        SchemeRepository schemeRepository,
        SchemeOptionRepository schemeOptionRepository,
        AmfiUniverseIngestionService universeIngestionService,
        HistoricalNavIngestionService historicalNavIngestionService,
        com.yukira.backend.scoring.service.AnalyticalScoringService scoringService,
        NavObservationRepository navObservationRepository,
        PortfolioSnapshotRepository portfolioSnapshotRepository,
        com.yukira.backend.service.DataQualityFreshnessService dataQualityFreshnessService,
        com.yukira.backend.universe.ReferenceUniverseService referenceUniverseService,
        com.yukira.backend.universe.HistorySufficiencyService historySufficiencyService,
        com.yukira.backend.benchmark.BenchmarkDateAlignmentService benchmarkDateAlignmentService
    ) {
        this(schemeRepository, schemeOptionRepository, universeIngestionService, historicalNavIngestionService, scoringService, navObservationRepository, portfolioSnapshotRepository, dataQualityFreshnessService, referenceUniverseService, historySufficiencyService, benchmarkDateAlignmentService, null, null);
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
        @Autowired(required = false) com.yukira.backend.service.DataQualityFreshnessService dataQualityFreshnessService,
        @Autowired(required = false) com.yukira.backend.universe.ReferenceUniverseService referenceUniverseService,
        @Autowired(required = false) com.yukira.backend.universe.HistorySufficiencyService historySufficiencyService,
        @Autowired(required = false) com.yukira.backend.benchmark.BenchmarkDateAlignmentService benchmarkDateAlignmentService,
        @Autowired(required = false) com.yukira.backend.universe.UniverseBatchOrchestratorService universeBatchOrchestratorService,
        @Autowired(required = false) com.yukira.backend.universe.UniverseScreenerService universeScreenerService
    ) {
        this.schemeRepository = schemeRepository;
        this.schemeOptionRepository = schemeOptionRepository;
        this.universeIngestionService = universeIngestionService;
        this.historicalNavIngestionService = historicalNavIngestionService;
        this.scoringService = scoringService;
        this.navObservationRepository = navObservationRepository;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.dataQualityFreshnessService = dataQualityFreshnessService;
        this.referenceUniverseService = referenceUniverseService;
        this.historySufficiencyService = historySufficiencyService;
        this.benchmarkDateAlignmentService = benchmarkDateAlignmentService;
        this.universeBatchOrchestratorService = universeBatchOrchestratorService;
        this.universeScreenerService = universeScreenerService;
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
        if (schemeOpt.isEmpty()) {
            try {
                Long optId = Long.parseLong(identifier);
                schemeOpt = schemeOptionRepository.findById(optId)
                    .map(so -> so.getPlan() != null ? so.getPlan().getScheme() : null);
            } catch (NumberFormatException ignored) {}
        }
        if (schemeOpt.isEmpty()) {
            schemeOpt = schemeOptionRepository.findByAmfiCode(identifier)
                .map(so -> so.getPlan() != null ? so.getPlan().getScheme() : null);
        }
        if (schemeOpt.isEmpty()) {
            schemeOpt = schemeOptionRepository.findByIsin(identifier)
                .map(so -> so.getPlan() != null ? so.getPlan().getScheme() : null);
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
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Boolean referenceUniverseOnly,
        @RequestParam(required = false) com.yukira.backend.universe.HistorySufficiencyStatus historyStatus
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
        if (referenceUniverseService != null) {
            for (SchemeOption opt : options) {
                referenceUniverseService.enrichOption(opt);
            }
            if (Boolean.TRUE.equals(referenceUniverseOnly)) {
                options = options.stream()
                    .filter(o -> Boolean.TRUE.equals(o.getInReferenceUniverse()))
                    .collect(java.util.stream.Collectors.toList());
            }
        }
        if (historySufficiencyService != null) {
            for (SchemeOption opt : options) {
                historySufficiencyService.enrichOption(opt);
            }
            if (historyStatus != null) {
                options = options.stream()
                    .filter(o -> historyStatus.equals(o.getHistorySufficiencyStatus()))
                    .collect(java.util.stream.Collectors.toList());
            }
        }
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
        java.util.Optional<Scheme> schemeOpt = java.util.Optional.empty();
        try {
            Long id = Long.parseLong(identifier);
            schemeOpt = schemeRepository.findById(id);
        } catch (NumberFormatException ignored) {}
        if (schemeOpt.isEmpty()) {
            schemeOpt = schemeRepository.findByCode(identifier);
        }
        if (schemeOpt.isEmpty()) {
            try {
                Long optId = Long.parseLong(identifier);
                schemeOpt = schemeOptionRepository.findById(optId)
                    .map(so -> so.getPlan() != null ? so.getPlan().getScheme() : null);
            } catch (NumberFormatException ignored) {}
        }
        if (schemeOpt.isEmpty()) {
            schemeOpt = schemeOptionRepository.findByAmfiCode(identifier)
                .map(so -> so.getPlan() != null ? so.getPlan().getScheme() : null);
        }
        if (schemeOpt.isEmpty()) {
            schemeOpt = schemeOptionRepository.findByIsin(identifier)
                .map(so -> so.getPlan() != null ? so.getPlan().getScheme() : null);
        }
        if (schemeOpt.isEmpty() || schemeOpt.get() == null) {
            return ResponseEntity.notFound().build();
        }
        Long schemeId = schemeOpt.get().getId();
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
        if (referenceUniverseService != null) {
            for (SchemeOption opt : options) {
                referenceUniverseService.enrichOption(opt);
            }
        }
        if (historySufficiencyService != null) {
            for (SchemeOption opt : options) {
                historySufficiencyService.enrichOption(opt);
            }
        }
        return ResponseEntity.ok(options);
    }

    /**
     * Retrieves the machine-readable reference universe denominator, 5-category breakdown,
     * and exclusion counts for the initial 5-category Direct-Growth scope (TSK-M1-02).
     */
    @GetMapping("/reference-universe")
    public ResponseEntity<com.yukira.backend.universe.ReferenceUniverseReportDto> getReferenceUniverseReport() {
        if (referenceUniverseService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(referenceUniverseService.generateUniverseReport());
    }

    /**
     * Retrieves universe-wide history-sufficiency assessment and status breakdown (TSK-M1-03).
     */
    @GetMapping("/history-eligibility")
    public ResponseEntity<com.yukira.backend.universe.UniverseHistorySufficiencyReportDto> getUniverseHistorySufficiencyReport() {
        if (historySufficiencyService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(historySufficiencyService.generateUniverseSufficiencyReport());
    }

    /**
     * Evaluates deterministic history-sufficiency for an individual scheme option (TSK-M1-03).
     * Accepts database ID, AMFI code, or ISIN as identifier.
     * Optional asOfDate parameter applies strict point-in-time knowledge cutoff.
     */
    @GetMapping("/options/{identifier}/history-eligibility")
    public ResponseEntity<com.yukira.backend.universe.HistorySufficiencyResult> getOptionHistoryEligibility(
        @PathVariable String identifier,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate
    ) {
        if (historySufficiencyService == null) {
            return ResponseEntity.status(503).build();
        }
        Long optionId = null;
        try {
            optionId = Long.parseLong(identifier);
        } catch (NumberFormatException ignored) {}
        SchemeOption option = null;
        if (optionId != null) {
            option = schemeOptionRepository.findById(optionId).orElse(null);
        }
        if (option == null) {
            option = schemeOptionRepository.findByAmfiCode(identifier).orElse(null);
        }
        if (option == null) {
            option = schemeOptionRepository.findByIsin(identifier).orElse(null);
        }
        if (option == null) {
            return ResponseEntity.notFound().build();
        }
        com.yukira.backend.universe.HistorySufficiencyResult result = asOfDate != null
            ? historySufficiencyService.evaluateOptionAsOf(option, asOfDate)
            : historySufficiencyService.evaluateOption(option);
        return ResponseEntity.ok(result);
    }

    /**
     * Evaluates deterministic benchmark date alignment and metric readiness for an option (TSK-M1-04).
     * Accepts database ID, AMFI code, or ISIN as identifier.
     * Optional startDate, endDate, asOfDate, and knowledgeCutoffTime parameters apply strict PIT cutoff.
     */
    @GetMapping("/options/{identifier}/benchmark-alignment")
    public ResponseEntity<com.yukira.backend.benchmark.BenchmarkDateAlignmentResultDto> getOptionBenchmarkAlignment(
        @PathVariable String identifier,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.OffsetDateTime knowledgeCutoffTime
    ) {
        if (benchmarkDateAlignmentService == null) {
            return ResponseEntity.status(503).build();
        }
        try {
            com.yukira.backend.benchmark.BenchmarkDateAlignmentResultDto result =
                benchmarkDateAlignmentService.evaluateAlignmentByIdentifier(identifier, startDate, endDate, asOfDate, knowledgeCutoffTime);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
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

    /**
     * Retrieves the comprehensive 5-category reference universe data and benchmark coverage status for investors and research due diligence (TSK-M1-06).
     *
     * GET /api/v1/schemes/universe-coverage-status
     */
    @GetMapping("/universe-coverage-status")
    public ResponseEntity<com.yukira.backend.universe.UniverseCoverageStatusDto> getUniverseCoverageStatus() {
        if (universeBatchOrchestratorService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(universeBatchOrchestratorService.getUniverseCoverageStatus());
    }

    /**
     * Retrieves the investor-facing comparative screener dataset across the 193 reference-universe options (TSK-M1-08).
     *
     * GET /api/v1/schemes/screener
     */
    @GetMapping("/screener")
    public ResponseEntity<com.yukira.backend.universe.UniverseScreenerDto.ScreenerResponse> getScreener(
        @RequestParam(required = false) com.yukira.backend.universe.EquityUniverseCategory category,
        @RequestParam(required = false, defaultValue = "false") Boolean eligibleOnly,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate,
        @RequestParam(required = false, defaultValue = "3Y") String horizon
    ) {
        if (universeScreenerService == null) {
            return ResponseEntity.status(503).build();
        }
        com.yukira.backend.universe.AnalyticalHorizon h = com.yukira.backend.universe.AnalyticalHorizon.fromCode(horizon);
        return ResponseEntity.ok(universeScreenerService.getScreenerData(category, eligibleOnly, asOfDate, h));
    }

    /**
     * Retrieves factual multi-horizon historical coverage (3Y, 5Y, 10Y) for an individual option (Phase A3).
     *
     * GET /api/v1/schemes/options/{identifier}/horizon-coverage
     */
    @GetMapping("/options/{identifier}/horizon-coverage")
    public ResponseEntity<com.yukira.backend.universe.OptionMultiHorizonCoverageDto> getOptionHorizonCoverage(
        @PathVariable String identifier,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate
    ) {
        if (historySufficiencyService == null) {
            return ResponseEntity.status(503).build();
        }
        Long optionId = null;
        try {
            optionId = Long.parseLong(identifier);
        } catch (NumberFormatException ignored) {}
        SchemeOption option = null;
        if (optionId != null) {
            option = schemeOptionRepository.findById(optionId).orElse(null);
        }
        if (option == null) {
            option = schemeOptionRepository.findByAmfiCode(identifier).orElse(null);
        }
        if (option == null) {
            option = schemeOptionRepository.findByIsin(identifier).orElse(null);
        }
        if (option == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(historySufficiencyService.evaluateOptionMultiHorizon(option, asOfDate));
    }

    /**
     * Retrieves universe-wide factual coverage summary for an analytical horizon (Phase A3).
     *
     * GET /api/v1/schemes/universe-horizon-coverage
     */
    @GetMapping("/universe-horizon-coverage")
    public ResponseEntity<com.yukira.backend.universe.UniverseHorizonCoverageReportDto> getUniverseHorizonCoverage(
        @RequestParam(defaultValue = "5Y") String horizon,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate
    ) {
        if (historySufficiencyService == null) {
            return ResponseEntity.status(503).build();
        }
        com.yukira.backend.universe.AnalyticalHorizon h = com.yukira.backend.universe.AnalyticalHorizon.fromCode(horizon);
        return ResponseEntity.ok(historySufficiencyService.generateUniverseHorizonReport(h, asOfDate));
    }

    /**
     * Retrieves factual multi-horizon historical TRI coverage (3Y, 5Y, 10Y) for an option's assigned
     * canonical category benchmark (Phase A4).
     *
     * GET /api/v1/schemes/options/{identifier}/benchmark-horizon-coverage
     */
    @GetMapping("/options/{identifier}/benchmark-horizon-coverage")
    public ResponseEntity<?> getOptionBenchmarkHorizonCoverage(
        @PathVariable String identifier,
        @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOfDate,
        @RequestParam(required = false) String horizon
    ) {
        Long optionId = null;
        try {
            optionId = Long.parseLong(identifier);
        } catch (NumberFormatException ignored) {}
        SchemeOption option = null;
        if (optionId != null) {
            option = schemeOptionRepository.findById(optionId).orElse(null);
        }
        if (option == null) {
            option = schemeOptionRepository.findByAmfiCode(identifier).orElse(null);
        }
        if (option == null) {
            option = schemeOptionRepository.findByIsin(identifier).orElse(null);
        }
        if (option == null) {
            return ResponseEntity.notFound().build();
        }

        SchemePlan plan = option.getPlan();
        Scheme scheme = plan != null ? plan.getScheme() : null;
        if (scheme == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "MISSING_SCHEME",
                "message", "SchemeOption does not have an associated Scheme entity."
            ));
        }

        com.yukira.backend.universe.EquityUniverseCategory cat = com.yukira.backend.universe.EquityUniverseCategory.categorize(
            scheme.getCategory(), scheme.getSubcategory(), scheme.getName()
        );
        if (cat == null) {
            return ResponseEntity.ok(Map.of(
                "schemeOptionId", option.getId(),
                "amfiCode", option.getAmfiCode(),
                "isin", option.getIsin(),
                "category", "UNMAPPED",
                "message", "Option is outside the five reference-universe equity categories.",
                "governanceNote", "Benchmark coverage is only mapped for the five canonical reference categories under Phase 2H."
            ));
        }

        var descOpt = com.yukira.backend.benchmark.CategoryBenchmarkMapping.getDescriptorForCategory(cat);
        if (descOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "schemeOptionId", option.getId(),
                "category", cat.name(),
                "message", "No canonical category benchmark mapped for category: " + cat.name()
            ));
        }

        if (benchmarkIngestionService == null) {
            return ResponseEntity.status(503).body(Map.of(
                "error", "SERVICE_UNAVAILABLE",
                "message", "NiftyBenchmarkIngestionService is not available."
            ));
        }

        LocalDate evalDate = asOfDate != null ? asOfDate : LocalDate.now();
        if (horizon != null && !horizon.isBlank() && !"ALL".equalsIgnoreCase(horizon.trim())) {
            com.yukira.backend.universe.AnalyticalHorizon h = com.yukira.backend.universe.AnalyticalHorizon.fromCode(horizon);
            com.yukira.backend.benchmark.BenchmarkHorizonCoverageDto cov = benchmarkIngestionService.evaluateBenchmarkHorizon(
                descOpt.get().benchmarkCode(), h, evalDate
            );
            return ResponseEntity.ok(cov);
        }

        com.yukira.backend.benchmark.BenchmarkMultiHorizonCoverageDto multi = benchmarkIngestionService.evaluateBenchmarkMultiHorizon(
            descOpt.get().benchmarkCode(), evalDate
        );
        return ResponseEntity.ok(multi);
    }

    /**
     * Composite comprehensive fund profile endpoint (Task 220).
     */
    @GetMapping("/{id}/profile/composite")
    public ResponseEntity<?> getCompositeProfile(@PathVariable("id") Long schemeId) {
        if (unifiedFundProfileService == null) {
            return ResponseEntity.status(503).body(Map.of("error", "SERVICE_UNAVAILABLE", "message", "UnifiedFundProfileService not available"));
        }
        return unifiedFundProfileService.getCompositeProfile(schemeId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Resolves AMFI code, ISIN, ticker, or symbol to canonical SchemeOption (Task 216).
     */
    @GetMapping("/resolve")
    public ResponseEntity<?> resolveSchemeIdentity(@RequestParam("query") String query) {
        if (schemeIdentityResolutionService == null) {
            return ResponseEntity.status(503).body(Map.of("error", "SERVICE_UNAVAILABLE", "message", "SchemeIdentityResolutionService not available"));
        }
        return ResponseEntity.ok(schemeIdentityResolutionService.resolve(query));
    }

    /**
     * Multi-dimensional fund discovery screener endpoint (Task 219).
     */
    @PostMapping("/discovery/filter")
    public ResponseEntity<?> filterFunds(
        @RequestBody com.yukira.backend.service.discovery.FundDiscoveryFilterService.DiscoveryFilterCriteria criteria,
        @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        if (fundDiscoveryFilterService == null) {
            return ResponseEntity.status(503).body(Map.of("error", "SERVICE_UNAVAILABLE", "message", "FundDiscoveryFilterService not available"));
        }
        return ResponseEntity.ok(fundDiscoveryFilterService.searchFunds(criteria, limit));
    }

    /**
     * Unified search across scheme names, AMCs, AMFI codes, and ISINs (Task 217).
     */
    @GetMapping("/search/unified")
    public ResponseEntity<?> unifiedSearch(
        @RequestParam("query") String query,
        @RequestParam(name = "limit", defaultValue = "30") int limit
    ) {
        if (fundDiscoveryFilterService == null) {
            return ResponseEntity.status(503).body(Map.of("error", "SERVICE_UNAVAILABLE", "message", "FundDiscoveryFilterService not available"));
        }
        var criteria = new com.yukira.backend.service.discovery.FundDiscoveryFilterService.DiscoveryFilterCriteria(
            null, null, null, null, null, null, query
        );
        return ResponseEntity.ok(fundDiscoveryFilterService.searchFunds(criteria, limit));
    }

    @Autowired(required = false)
    private SchemeMetadataRevisionService schemeMetadataRevisionService;

    /**
     * Immutable scheme metadata revision audit history (Task 268).
     */
    @GetMapping("/{id}/metadata-revisions")
    public ResponseEntity<?> getSchemeMetadataRevisions(@PathVariable Long id) {
        if (schemeMetadataRevisionService == null) {
            return ResponseEntity.status(503).body(Map.of("error", "SERVICE_UNAVAILABLE", "message", "SchemeMetadataRevisionService not available"));
        }
        return ResponseEntity.ok(schemeMetadataRevisionService.getRevisionsForScheme(id));
    }
}
