package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.Scheme;
import com.yukira.backend.domain.entity.SchemePlan;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.AmcRepository;
import com.yukira.backend.repository.NavObservationRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.repository.SchemePlanRepository;
import com.yukira.backend.repository.SchemeRepository;
import com.yukira.backend.scoring.population.PeerPopulationCriteria;
import com.yukira.backend.scoring.population.PeerPopulationEvaluationReport;
import com.yukira.backend.scoring.population.PeerPopulationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test executing real live AMFI universe expansion
 * and the controlled historical NAV sample load.
 *
 * Verifies Part A, Part B, Part C, Part D, Part E, Part F, and Part G.
 */
@SpringBootTest
@ActiveProfiles("test")
class RealUniverseAndNavLoadIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(RealUniverseAndNavLoadIntegrationTest.class);

    @Autowired
    private AmfiUniverseIngestionService universeIngestionService;

    @Autowired
    private HistoricalNavIngestionService historicalNavIngestionService;

    @Autowired
    private PeerPopulationService peerPopulationService;

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private AmcRepository amcRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private SchemePlanRepository schemePlanRepository;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private NavObservationRepository navObservationRepository;

    @Test
    @DisplayName("End-to-end: Ingest live AMFI universe, controlled historical NAV sample, and verify coverage diagnostics")
    void testRealUniverseAndNavIngestion() {
        // 1. Ensure canonical pilot master is bootstrapped
        pilotBootstrapService.ensureCanonicalPilotMaster();

        // 2. Fetch and Ingest Live AMFI Universe from portal.amfiindia.com/spages/NAVAll.txt
        log.info("Starting live AMFI universe expansion...");
        UniverseIngestionSummary universeSummary = universeIngestionService.fetchAndIngestLiveUniverse();
        assertNotNull(universeSummary, "Universe ingestion summary must not be null");

        log.info("Live Universe Ingestion Result: parsed={}, AMCs={}, schemes={}, plans={}, options={}, duplicatesSkipped={}",
            universeSummary.totalRowsParsed(), universeSummary.amcsRegistered(),
            universeSummary.schemesRegistered(), universeSummary.plansRegistered(),
            universeSummary.optionsRegistered(), universeSummary.duplicatesSkipped());

        assertTrue(universeSummary.totalRowsParsed() > 10000, "Should parse >10,000 rows from NAVAll.txt");
        assertTrue(schemeOptionRepository.count() > 10000, "Database must have >10,000 options");

        // Canonical pilot preservation check
        SchemeOption pilot = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        SchemePlan plan = schemePlanRepository.findById(pilot.getPlan().getId()).orElseThrow();
        Scheme scheme = schemeRepository.findById(plan.getScheme().getId()).orElseThrow();
        assertEquals("HDFC_FLEXI", scheme.getCode());
        assertEquals("HDFC_FLEXI_DIR", plan.getCode());
        assertEquals("INF179K01UT0", pilot.getIsin());

        // 3. Ingest Controlled Sample Historical NAVs (5 funds)
        log.info("Starting controlled sample historical NAV ingestion...");
        Map<String, Object> navReport = historicalNavIngestionService.ingestControlledSample();
        assertNotNull(navReport);
        assertEquals(5, navReport.get("totalFundsAttempted"));
        log.info("Controlled Sample NAV Ingestion: {}", navReport);

        // Verify each target fund has observations
        for (HistoricalNavIngestionService.ControlledFundTarget target : HistoricalNavIngestionService.CONTROLLED_SAMPLE_TARGETS) {
            SchemeOption opt = schemeOptionRepository.findByAmfiCode(target.amfiCode()).orElseThrow();
            long obsCount = navObservationRepository.countBySchemeOptionId(opt.getId());
            assertTrue(obsCount > 0, "Target fund " + target.label() + " must have historical NAV observations in database");
            log.info("Target {}: AMFI={}, ISIN={}, observations={}", target.label(), target.amfiCode(), target.isin(), obsCount);
        }

        // 4. Evaluate Reference Population (Part F)
        PeerPopulationEvaluationReport popReport = peerPopulationService.evaluatePopulation(PeerPopulationCriteria.defaultFlexiCap());
        assertNotNull(popReport);
        assertEquals("INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1", popReport.activeProvisionalPopulation());
        assertEquals("PROVISIONAL_INSUFFICIENT_DATA", popReport.status());
        assertFalse(popReport.empiricallyReady());
        log.info("Reference Population Evaluation: status={}, candidates={}, sufficientNav={}, dataGaps={}",
            popReport.status(), popReport.catalogUniverseCandidatesCount(), popReport.optionsWithSufficientNavCount(), popReport.dataGaps());

        // 5. Generate Part G Machine-Readable Coverage Report
        Map<String, Object> sampleCoverage = historicalNavIngestionService.getControlledSampleCoverage();
        UniverseCoverageReport coverage = universeIngestionService.generateUniverseCoverageReport(sampleCoverage);
        assertNotNull(coverage);

        log.info("=== PART G UNIVERSE COVERAGE DIAGNOSTICS ===");
        log.info("Total AMCs: {}", coverage.totalAmcs());
        log.info("Total Schemes: {}", coverage.totalSchemes());
        log.info("Total Plans: {}", coverage.totalPlans());
        log.info("Total Options: {}", coverage.totalOptions());
        log.info("Active Options: {}", coverage.activeOptionsCount());
        log.info("Inactive Options: {}", coverage.inactiveOptionsCount());
        log.info("Direct Options: {}", coverage.directOptionsCount());
        log.info("Regular Options: {}", coverage.regularOptionsCount());
        log.info("Growth Options: {}", coverage.growthOptionsCount());
        log.info("IDCW Options: {}", coverage.idcwOptionsCount());
        log.info("Missing ISINs: {}", coverage.missingIsinCount());
        log.info("Missing AMFI IDs: {}", coverage.missingAmfiCodeCount());
        log.info("Missing Categories: {}", coverage.missingCategoryCount());
        log.info("Missing Subcategories: {}", coverage.missingSubcategoryCount());
        log.info("Missing Benchmarks: {}", coverage.missingBenchmarkCount());
        log.info("Duplicate Findings: {}", coverage.duplicateIdentityFindings());
        log.info("Quality Notes: {}", coverage.qualityNotes());

        assertTrue(coverage.totalAmcs() >= 40);
        assertTrue(coverage.totalSchemes() >= 2000);
        assertTrue(coverage.totalOptions() >= 10000);
    }
}
