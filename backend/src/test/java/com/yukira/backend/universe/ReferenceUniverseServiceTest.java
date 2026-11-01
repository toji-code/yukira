package com.yukira.backend.universe;

import com.yukira.backend.domain.entity.Amc;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferenceUniverseServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private SourceArtifactRepository sourceArtifactRepository;

    private ReferenceUniverseService service;

    @BeforeEach
    void setUp() {
        service = new ReferenceUniverseService(schemeOptionRepository, sourceArtifactRepository);
    }

    private SchemeOption createCandidate(
        Long id,
        String amfiCode,
        String isin,
        String category,
        String subcategory,
        String planType,
        String optionType,
        String status
    ) {
        Amc amc = new Amc("Test AMC", "TEST_MF");
        amc.setId(10L);
        Scheme scheme = new Scheme(amc, "Test Scheme", "TEST_SCHEME", null, category, subcategory);
        scheme.setId(20L);
        SchemePlan plan = new SchemePlan(scheme, planType, "TEST_PLAN");
        plan.setId(30L);
        SchemeOption opt = new SchemeOption(plan, optionType, amfiCode, isin);
        opt.setId(id);
        opt.setStatus(status);
        return opt;
    }

    @Test
    @DisplayName("Deterministic Category Classification: Maps all 5 core equity categories accurately")
    void testCategoryClassification() {
        // 1. Flexi Cap
        assertEquals(EquityUniverseCategory.FLEXI_CAP, EquityUniverseCategory.fromSource("Equity Scheme", "Flexi Cap Fund"));
        assertEquals(EquityUniverseCategory.FLEXI_CAP, EquityUniverseCategory.fromSource(null, "Flexi Cap"));
        assertEquals(EquityUniverseCategory.FLEXI_CAP, EquityUniverseCategory.fromSource("Open Ended Schemes", "Equity Scheme - Flexi Cap Fund"));

        // 2. Large Cap
        assertEquals(EquityUniverseCategory.LARGE_CAP, EquityUniverseCategory.fromSource("Equity Scheme", "Large Cap Fund"));
        assertEquals(EquityUniverseCategory.LARGE_CAP, EquityUniverseCategory.fromSource(null, "Large Cap"));

        // 3. Large & Mid Cap (must not collide with Large Cap or Mid Cap)
        assertEquals(EquityUniverseCategory.LARGE_AND_MID_CAP, EquityUniverseCategory.fromSource("Equity Scheme", "Large & Mid Cap Fund"));
        assertEquals(EquityUniverseCategory.LARGE_AND_MID_CAP, EquityUniverseCategory.fromSource("Equity Scheme", "Large and Mid Cap Fund"));
        assertEquals(EquityUniverseCategory.LARGE_AND_MID_CAP, EquityUniverseCategory.fromSource(null, "Large & Mid Cap"));

        // 4. Mid Cap
        assertEquals(EquityUniverseCategory.MID_CAP, EquityUniverseCategory.fromSource("Equity Scheme", "Mid Cap Fund"));
        assertEquals(EquityUniverseCategory.MID_CAP, EquityUniverseCategory.fromSource(null, "Mid Cap"));

        // 5. Small Cap
        assertEquals(EquityUniverseCategory.SMALL_CAP, EquityUniverseCategory.fromSource("Equity Scheme", "Small Cap Fund"));
        assertEquals(EquityUniverseCategory.SMALL_CAP, EquityUniverseCategory.fromSource(null, "Small Cap"));
    }

    @Test
    @DisplayName("Category Out of Scope & Unclassified: Rejects Debt, Multi Cap, ELSS, and blank metadata")
    void testCategoryOutOfScopeAndUnclassified() {
        // Out of scope: Other Equity
        assertNull(EquityUniverseCategory.fromSource("Equity Scheme", "Multi Cap Fund"));
        assertNull(EquityUniverseCategory.fromSource("Equity Scheme", "ELSS"));
        assertNull(EquityUniverseCategory.fromSource("Equity Scheme", "Sectoral / Thematic"));

        // Out of scope: Debt / Hybrid
        assertNull(EquityUniverseCategory.fromSource("Debt Scheme", "Liquid Fund"));
        assertNull(EquityUniverseCategory.fromSource("Hybrid Scheme", "Aggressive Hybrid Fund"));

        // Unclassified / Blank
        assertNull(EquityUniverseCategory.fromSource(null, null));
        assertNull(EquityUniverseCategory.fromSource("", "   "));
    }

    @Test
    @DisplayName("Inclusion: Eligible Direct Growth in Core 5 Categories is Included")
    void testEligibleOptionIncluded() {
        SchemeOption opt = createCandidate(1L, "118955", "INF179K01UT0", "Equity Scheme", "Flexi Cap Fund", "DIRECT", "GROWTH", "ACTIVE");
        UniverseEvaluationResult result = service.evaluateOption(opt);

        assertTrue(result.included());
        assertEquals(EquityUniverseCategory.FLEXI_CAP, result.category());
        assertNull(result.exclusionReason());
    }

    @Test
    @DisplayName("Plan Separation: Regular plan is excluded with PLAN_NOT_DIRECT")
    void testExcludeRegularPlan() {
        SchemeOption opt = createCandidate(2L, "100123", "INF179K01BE2", "Equity Scheme", "Flexi Cap Fund", "REGULAR", "GROWTH", "ACTIVE");
        UniverseEvaluationResult result = service.evaluateOption(opt);

        assertFalse(result.included());
        assertEquals(UniverseExclusionReason.PLAN_NOT_DIRECT, result.exclusionReason());
    }

    @Test
    @DisplayName("Option Separation: IDCW and Bonus options are excluded with OPTION_NOT_GROWTH")
    void testExcludeIdcwAndBonusOption() {
        SchemeOption idcwOpt = createCandidate(3L, "100124", "INF179K01UU8", "Equity Scheme", "Flexi Cap Fund", "DIRECT", "IDCW", "ACTIVE");
        UniverseEvaluationResult r1 = service.evaluateOption(idcwOpt);
        assertFalse(r1.included());
        assertEquals(UniverseExclusionReason.OPTION_NOT_GROWTH, r1.exclusionReason());

        SchemeOption bonusOpt = createCandidate(4L, "100125", "INF179K01UU9", "Equity Scheme", "Large Cap Fund", "DIRECT", "BONUS", "ACTIVE");
        UniverseEvaluationResult r2 = service.evaluateOption(bonusOpt);
        assertFalse(r2.included());
        assertEquals(UniverseExclusionReason.OPTION_NOT_GROWTH, r2.exclusionReason());
    }

    @Test
    @DisplayName("Category Scope Exclusion: Debt or Multi Cap options excluded with CATEGORY_OUT_OF_SCOPE")
    void testExcludeOutOfScopeCategory() {
        SchemeOption liquidOpt = createCandidate(5L, "120801", "INF174K01UT9", "Debt Scheme", "Liquid Fund", "DIRECT", "GROWTH", "ACTIVE");
        UniverseEvaluationResult r1 = service.evaluateOption(liquidOpt);
        assertFalse(r1.included());
        assertEquals(UniverseExclusionReason.CATEGORY_OUT_OF_SCOPE, r1.exclusionReason());

        SchemeOption multiCapOpt = createCandidate(6L, "120901", "INF174K01UT8", "Equity Scheme", "Multi Cap Fund", "DIRECT", "GROWTH", "ACTIVE");
        UniverseEvaluationResult r2 = service.evaluateOption(multiCapOpt);
        assertFalse(r2.included());
        assertEquals(UniverseExclusionReason.CATEGORY_OUT_OF_SCOPE, r2.exclusionReason());
    }

    @Test
    @DisplayName("Category Unclassified Exclusion: Missing category metadata excluded with CATEGORY_UNCLASSIFIED")
    void testExcludeUnclassifiedCategory() {
        SchemeOption unclassOpt = createCandidate(7L, "120999", "INF174K01UT7", null, "", "DIRECT", "GROWTH", "ACTIVE");
        UniverseEvaluationResult result = service.evaluateOption(unclassOpt);
        assertFalse(result.included());
        assertEquals(UniverseExclusionReason.CATEGORY_UNCLASSIFIED, result.exclusionReason());
    }

    @Test
    @DisplayName("Status Exclusion: Inactive or Data Quality Limited options excluded")
    void testExcludeInactiveOrDefunct() {
        SchemeOption inactiveOpt = createCandidate(8L, "120555", "INF174K01UT6", "Equity Scheme", "Small Cap Fund", "DIRECT", "GROWTH", "INACTIVE");
        UniverseEvaluationResult r1 = service.evaluateOption(inactiveOpt);
        assertFalse(r1.included());
        assertEquals(UniverseExclusionReason.RECORD_INACTIVE, r1.exclusionReason());

        SchemeOption dqlOpt = createCandidate(9L, "120556", "INF174K01UT5", "Equity Scheme", "Small Cap Fund", "DIRECT", "GROWTH", "DATA_QUALITY_LIMITED");
        UniverseEvaluationResult r2 = service.evaluateOption(dqlOpt);
        assertFalse(r2.included());
        assertEquals(UniverseExclusionReason.DATA_QUALITY_LIMITED, r2.exclusionReason());
    }

    @Test
    @DisplayName("Denominator & Breakdown Report: Computes accurate counts without double counting")
    void testGenerateUniverseReport() {
        List<SchemeOption> mockOptions = List.of(
            // Included: Flexi Cap Direct Growth
            createCandidate(1L, "118955", "INF179K01UT0", "Equity Scheme", "Flexi Cap Fund", "DIRECT", "GROWTH", "ACTIVE"),
            // Included: Large Cap Direct Growth
            createCandidate(2L, "120501", "INF200K01UT1", "Equity Scheme", "Large Cap Fund", "DIRECT", "GROWTH", "ACTIVE"),
            // Excluded: Large Cap Regular Growth (PLAN_NOT_DIRECT)
            createCandidate(3L, "120502", "INF200K01UT2", "Equity Scheme", "Large Cap Fund", "REGULAR", "GROWTH", "ACTIVE"),
            // Excluded: Flexi Cap Direct IDCW (OPTION_NOT_GROWTH)
            createCandidate(4L, "100124", "INF179K01UU8", "Equity Scheme", "Flexi Cap Fund", "DIRECT", "IDCW", "ACTIVE"),
            // Excluded: Liquid Fund Direct Growth (CATEGORY_OUT_OF_SCOPE)
            createCandidate(5L, "120801", "INF174K01UT9", "Debt Scheme", "Liquid Fund", "DIRECT", "GROWTH", "ACTIVE"),
            // Excluded: Small Cap Direct Growth Inactive (RECORD_INACTIVE)
            createCandidate(6L, "120701", "INF204K01UT5", "Equity Scheme", "Small Cap Fund", "DIRECT", "GROWTH", "INACTIVE")
        );

        when(schemeOptionRepository.findAll()).thenReturn(mockOptions);

        SourceArtifact artifact = new SourceArtifact();
        artifact.setSha256Hash("test-sha256");
        artifact.setRetrievalTimestamp(OffsetDateTime.now());
        when(sourceArtifactRepository.findTopByArtifactTypeOrderByRetrievalTimestampDesc("UNIVERSE_CATALOG_TEXT"))
            .thenReturn(Optional.of(artifact));

        ReferenceUniverseReportDto report = service.generateUniverseReport();

        assertNotNull(report);
        assertEquals("YUKIRA_V1_M1_5CAT_DIRECT_GROWTH", report.ruleVersion());
        assertEquals("SCHEME_OPTION", report.countingUnit());
        assertTrue(report.isProvisional(), "Should be marked provisional since test count < 100");
        assertEquals(6, report.totalEvaluatedOptions());
        assertEquals(4, report.directGrowthOptionsCount()); // options 1, 2, 5, 6
        assertEquals(5, report.coreFiveCategoryCandidatesCount()); // options 1, 2, 3, 4, 6
        assertEquals(2, report.includedUniverseCount()); // options 1 and 2

        // Check category breakdown
        assertEquals(1L, report.includedCountsByCategory().get("FLEXI_CAP"));
        assertEquals(1L, report.includedCountsByCategory().get("LARGE_CAP"));
        assertEquals(0L, report.includedCountsByCategory().get("MID_CAP"));

        // Check exclusion reasons
        assertEquals(1L, report.excludedCountsByReason().get("PLAN_NOT_DIRECT"));
        assertEquals(1L, report.excludedCountsByReason().get("OPTION_NOT_GROWTH"));
        assertEquals(1L, report.excludedCountsByReason().get("CATEGORY_OUT_OF_SCOPE"));
        assertEquals(1L, report.excludedCountsByReason().get("RECORD_INACTIVE"));
    }

    @Test
    @DisplayName("Transient Field Enrichment: Sets inReferenceUniverse, universeCategory, universeExclusionReason")
    void testEnrichOption() {
        SchemeOption opt = createCandidate(1L, "118955", "INF179K01UT0", "Equity Scheme", "Flexi Cap Fund", "DIRECT", "GROWTH", "ACTIVE");
        service.enrichOption(opt);

        assertEquals(Boolean.TRUE, opt.getInReferenceUniverse());
        assertEquals("FLEXI_CAP", opt.getUniverseCategory());
        assertNull(opt.getUniverseExclusionReason());

        SchemeOption regularOpt = createCandidate(2L, "100123", "INF179K01BE2", "Equity Scheme", "Flexi Cap Fund", "REGULAR", "GROWTH", "ACTIVE");
        service.enrichOption(regularOpt);

        assertEquals(Boolean.FALSE, regularOpt.getInReferenceUniverse());
        assertNull(regularOpt.getUniverseCategory());
        assertEquals("PLAN_NOT_DIRECT", regularOpt.getUniverseExclusionReason());
    }
}
