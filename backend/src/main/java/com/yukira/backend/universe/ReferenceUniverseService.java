package com.yukira.backend.universe;

import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service establishing the deterministic, evidence-based reference universe definition
 * and Direct-Growth equity filtering for Project YUKIRA V1 (TSK-M1-02).
 *
 * Core Epistemic Principles:
 * 1. The complete AMFI scheme master remains fully discoverable; universe membership is an analytical filter.
 * 2. Five initial core equity categories: Flexi Cap, Large Cap, Large & Mid Cap, Mid Cap, Small Cap.
 * 3. Strict Direct-Growth partition: Plan must be DIRECT, Option must be GROWTH, Status must be ACTIVE.
 * 4. Deterministic exclusion reasons: Every excluded candidate carries an explicit, auditable reason code.
 * 5. Universe membership does NOT imply metric validation or scoring approval.
 */
@Service
public class ReferenceUniverseService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceUniverseService.class);

    public static final String RULE_VERSION = "YUKIRA_V1_M1_5CAT_DIRECT_GROWTH";
    public static final String COUNTING_UNIT = "SCHEME_OPTION";
    public static final int PROVISIONAL_THRESHOLD = 100;

    private final SchemeOptionRepository schemeOptionRepository;
    private final SourceArtifactRepository sourceArtifactRepository;

    public ReferenceUniverseService(
        SchemeOptionRepository schemeOptionRepository,
        SourceArtifactRepository sourceArtifactRepository
    ) {
        this.schemeOptionRepository = schemeOptionRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
    }

    /**
     * Deterministically evaluates a SchemeOption against the 5-category Direct-Growth reference universe criteria.
     *
     * @param option The SchemeOption entity to evaluate.
     * @return UniverseEvaluationResult containing inclusion status, assigned category, or explicit exclusion reason.
     */
    public UniverseEvaluationResult evaluateOption(SchemeOption option) {
        if (option == null) {
            return UniverseEvaluationResult.ofExcluded(
                null, null, null, null, null, null, null, null,
                UniverseExclusionReason.CATEGORY_UNCLASSIFIED,
                "Option entity is null."
            );
        }

        Long optId = option.getId();
        String amfiCode = option.getAmfiCode();
        String isin = option.getIsin();
        String optionType = option.getOptionType();
        String optStatus = option.getStatus();

        SchemePlan plan = option.getPlan();
        String planType = plan != null ? plan.getPlanType() : null;
        Scheme scheme = plan != null ? plan.getScheme() : null;
        String schemeName = scheme != null ? scheme.getName() : null;
        String amcName = (scheme != null && scheme.getAmc() != null) ? scheme.getAmc().getName() : null;

        // 1. Active Status Check
        if ("INACTIVE".equalsIgnoreCase(optStatus)) {
            return UniverseEvaluationResult.ofExcluded(
                optId, amfiCode, isin, schemeName, amcName, planType, optionType, optStatus,
                UniverseExclusionReason.RECORD_INACTIVE, null
            );
        }
        if ("DATA_QUALITY_LIMITED".equalsIgnoreCase(optStatus)) {
            return UniverseEvaluationResult.ofExcluded(
                optId, amfiCode, isin, schemeName, amcName, planType, optionType, optStatus,
                UniverseExclusionReason.DATA_QUALITY_LIMITED, null
            );
        }

        // 2. Plan Check (Must be DIRECT)
        if (planType == null || !"DIRECT".equalsIgnoreCase(planType.trim())) {
            return UniverseEvaluationResult.ofExcluded(
                optId, amfiCode, isin, schemeName, amcName, planType, optionType, optStatus,
                UniverseExclusionReason.PLAN_NOT_DIRECT, null
            );
        }

        // 3. Option Check (Must be GROWTH)
        if (optionType == null || !"GROWTH".equalsIgnoreCase(optionType.trim())) {
            return UniverseEvaluationResult.ofExcluded(
                optId, amfiCode, isin, schemeName, amcName, planType, optionType, optStatus,
                UniverseExclusionReason.OPTION_NOT_GROWTH, null
            );
        }

        // 4. Category Classification Check (Must be in the 5 initial core equity categories)
        if (scheme == null) {
            return UniverseEvaluationResult.ofExcluded(
                optId, amfiCode, isin, schemeName, amcName, planType, optionType, optStatus,
                UniverseExclusionReason.CATEGORY_UNCLASSIFIED,
                "Underlying scheme record is unresolved."
            );
        }

        String rawCategory = scheme.getCategory();
        String rawSubcategory = scheme.getSubcategory();
        EquityUniverseCategory category = EquityUniverseCategory.fromSource(rawCategory, rawSubcategory);

        if (category == null) {
            boolean hasNoCategory = (rawCategory == null || rawCategory.isBlank())
                && (rawSubcategory == null || rawSubcategory.isBlank());
            if (hasNoCategory) {
                return UniverseEvaluationResult.ofExcluded(
                    optId, amfiCode, isin, schemeName, amcName, planType, optionType, optStatus,
                    UniverseExclusionReason.CATEGORY_UNCLASSIFIED, null
                );
            } else {
                String reportedCategory = (rawSubcategory != null && !rawSubcategory.isBlank())
                    ? rawSubcategory
                    : rawCategory;
                return UniverseEvaluationResult.ofExcluded(
                    optId, amfiCode, isin, schemeName, amcName, planType, optionType, optStatus,
                    UniverseExclusionReason.CATEGORY_OUT_OF_SCOPE,
                    "Category '" + reportedCategory + "' is outside the 5 initial core equity categories (Flexi Cap, Large Cap, Large & Mid Cap, Mid Cap, Small Cap)."
                );
            }
        }

        // 5. All Invariants Satisfied -> Included
        return UniverseEvaluationResult.ofIncluded(
            optId, amfiCode, isin, schemeName, amcName, planType, optionType, optStatus, category
        );
    }

    /**
     * Enriches a SchemeOption's transient fields with reference universe membership information.
     */
    public void enrichOption(SchemeOption option) {
        if (option == null) return;
        UniverseEvaluationResult result = evaluateOption(option);
        option.setInReferenceUniverse(result.included());
        option.setUniverseCategory(result.category() != null ? result.category().name() : null);
        option.setUniverseExclusionReason(result.exclusionReason() != null ? result.exclusionReason().name() : null);
    }

    /**
     * Returns true if the option satisfies all reference universe criteria.
     */
    public boolean isOptionInReferenceUniverse(SchemeOption option) {
        return evaluateOption(option).included();
    }

    /**
     * Retrieves all SchemeOptions currently persisted in the database that belong to the reference universe.
     */
    @Transactional(readOnly = true)
    public List<SchemeOption> getEligibleReferenceUniverseOptions() {
        return schemeOptionRepository.findDirectGrowthActiveCandidates().stream()
            .filter(this::isOptionInReferenceUniverse)
            .peek(this::enrichOption)
            .collect(Collectors.toList());
    }

    /**
     * Generates a comprehensive, reproducible reference universe denominator and coverage report
     * derived strictly from actual persisted records.
     */
    @Transactional(readOnly = true)
    public ReferenceUniverseReportDto generateUniverseReport() {
        List<SchemeOption> allOptions = schemeOptionRepository.findAll();
        long totalEvaluated = allOptions.size();

        long directGrowthCount = 0;
        long coreFiveCategoryCandidates = 0;
        long includedCount = 0;

        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (EquityUniverseCategory cat : EquityUniverseCategory.values()) {
            categoryCounts.put(cat.name(), 0L);
        }

        Map<String, Long> exclusionCounts = new LinkedHashMap<>();
        for (UniverseExclusionReason r : UniverseExclusionReason.values()) {
            exclusionCounts.put(r.name(), 0L);
        }

        for (SchemeOption opt : allOptions) {
            boolean isDirect = opt.getPlan() != null && "DIRECT".equalsIgnoreCase(opt.getPlan().getPlanType());
            boolean isGrowth = "GROWTH".equalsIgnoreCase(opt.getOptionType());
            if (isDirect && isGrowth) {
                directGrowthCount++;
            }

            Scheme scheme = opt.getPlan() != null ? opt.getPlan().getScheme() : null;
            if (scheme != null) {
                EquityUniverseCategory cat = EquityUniverseCategory.fromSource(scheme.getCategory(), scheme.getSubcategory());
                if (cat != null) {
                    coreFiveCategoryCandidates++;
                }
            }

            UniverseEvaluationResult result = evaluateOption(opt);
            if (result.included()) {
                includedCount++;
                String catKey = result.category().name();
                categoryCounts.put(catKey, categoryCounts.getOrDefault(catKey, 0L) + 1L);
            } else {
                String reasonKey = result.exclusionReason().name();
                exclusionCounts.put(reasonKey, exclusionCounts.getOrDefault(reasonKey, 0L) + 1L);
            }
        }

        // Traceability & Provenance
        Optional<SourceArtifact> latestCatalogArtifact = sourceArtifactRepository
            .findTopByArtifactTypeOrderByRetrievalTimestampDesc("UNIVERSE_CATALOG_TEXT");
        if (latestCatalogArtifact.isEmpty()) {
            latestCatalogArtifact = sourceArtifactRepository.findTopByOrderByRetrievalTimestampDesc();
        }

        OffsetDateTime retrievalTime = latestCatalogArtifact.map(SourceArtifact::getRetrievalTimestamp).orElse(null);
        String sha256 = latestCatalogArtifact.map(SourceArtifact::getSha256Hash).orElse(null);

        boolean isProvisional = totalEvaluated < PROVISIONAL_THRESHOLD;

        List<String> limitations = new ArrayList<>();
        if (isProvisional) {
            limitations.add("Database currently contains a provisional / sample catalog (" + totalEvaluated + " options); live AMFI universe ingestion has not yet been executed in production.");
        }
        limitations.add("AMFI daily NAV feed provides category banners and option types, but does not provide benchmark assignment or inception dates.");

        List<String> governanceNotes = List.of(
            "Rule Version: " + RULE_VERSION,
            "Authorized Categories: 5 core equity categories (Flexi Cap, Large Cap, Large & Mid Cap, Mid Cap, Small Cap).",
            "Eligibility Invariants: DIRECT plan only; GROWTH option only; ACTIVE status only.",
            "Inclusion in reference universe does NOT imply quantitative metric validation or scoring approval.",
            "Complete searchable scheme master remains preserved; out-of-universe schemes remain discoverable with explicit exclusion reasons."
        );

        return new ReferenceUniverseReportDto(
            RULE_VERSION,
            COUNTING_UNIT,
            isProvisional,
            totalEvaluated,
            directGrowthCount,
            coreFiveCategoryCandidates,
            includedCount,
            categoryCounts,
            exclusionCounts,
            retrievalTime,
            sha256,
            limitations,
            governanceNotes
        );
    }
}
