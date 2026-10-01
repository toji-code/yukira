package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@SuppressWarnings({"null", "deprecation"})
class MethodologyGovernanceTest {

    @Autowired
    private MetricDefinitionRepository metricDefinitionRepository;

    @Autowired
    private MethodologyVersionRepository methodologyVersionRepository;

    @Autowired
    private CalculationRunRepository calculationRunRepository;

    @Autowired
    private MethodologyGovernanceService methodologyGovernanceService;

    @Autowired
    private PeriodReturnCalculationService periodReturnCalculationService;

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

    @Autowired
    private MetricResultRepository metricResultRepository;

    private SchemeOption schemeOption;

    @BeforeEach
    void setUp() {
        Amc amc = amcRepository.findByCode("HDFC_MF")
            .orElseGet(() -> amcRepository.save(new Amc("HDFC Mutual Fund", "HDFC_MF")));
        Scheme scheme = schemeRepository.findByCode("HDFC_FLEXI")
            .orElseGet(() -> schemeRepository.save(new Scheme(amc, "HDFC Flexi Cap Fund", "HDFC_FLEXI", LocalDate.of(1995, 1, 1))));
        SchemePlan plan = schemePlanRepository.findByCode("HDFC_FLEXI_DIR")
            .orElseGet(() -> schemePlanRepository.save(new SchemePlan(scheme, "DIRECT", "HDFC_FLEXI_DIR")));
        schemeOption = schemeOptionRepository.findByAmfiCode("118989")
            .orElseGet(() -> schemeOptionRepository.save(new SchemeOption(plan, "GROWTH", "118989", "INF118989BE2")));
    }

    @Test
    @DisplayName("Phase 2G Test 1: Metric identity - MetricDefinition uniquely identifies code, dimension, units, and PIT requirements")
    void testMetricIdentity() {
        // No null guard: RET-02 is a Flyway-seeded registry row. A missing row is a registry
        // failure and must fail the test, not be silently created by the test itself.
        MetricDefinition metric = metricDefinitionRepository.findByMetricCode("RET-02")
            .orElseThrow(() -> new AssertionError(
                "Metric registry is missing required row for RET-02"));

        assertNotNull(metric.getId());
        assertEquals("RET-02", metric.getMetricCode());
        assertEquals("Simple Period Return", metric.getMetricName());
        assertEquals("RETURNS", metric.getAnalyticalDimension());
        assertEquals("PERCENTAGE", metric.getUnits());
        assertEquals("DISCRETE_PERIOD", metric.getDefaultFrequency());
        assertFalse(metric.isBenchmarkRequired(), "RET-02 must not require a benchmark");
        assertFalse(metric.isRiskFreeRequired(), "RET-02 must not require a risk-free rate");
        assertTrue(metric.isPointInTimeRequired(), "RET-02 strictly requires PIT filtering");
    }

    @Test
    @DisplayName("Phase 2G Test 2: RET-02 status remains strictly CANDIDATE and version CANDIDATE_V1")
    void testRet02RemainsCandidateV1() {
        Optional<MethodologyVersion> opt = methodologyVersionRepository
            .findByMethodologyCodeAndVersionTag("RET_02_SIMPLE_RETURN", "CANDIDATE_V1");

        assertTrue(opt.isPresent(), "RET_02_SIMPLE_RETURN CANDIDATE_V1 must exist in the database");
        MethodologyVersion ret02 = opt.get();

        assertEquals("RET_02_SIMPLE_RETURN", ret02.getMethodologyCode());
        assertEquals("CANDIDATE_V1", ret02.getVersionTag());
        assertEquals("CANDIDATE", ret02.getLifecycleStatus(), "Lifecycle status must be CANDIDATE");
        assertEquals("CANDIDATE", ret02.getApprovalStatus(), "Approval status must be CANDIDATE");
        assertTrue(ret02.isCandidate(), "isCandidate() must return true");
        assertFalse(ret02.isApproved(), "isApproved() must return false");
        assertFalse(ret02.isValidated(), "isValidated() must return false");
    }

    @Test
    @DisplayName("Phase 2G Test 3: No fake validation evidence - RET-02 is UNVALIDATED and has zero fabricated studies or approval dates")
    void testNoFakeValidationEvidence() {
        MethodologyVersion ret02 = methodologyVersionRepository
            .findByMethodologyCodeAndVersionTag("RET_02_SIMPLE_RETURN", "CANDIDATE_V1")
            .orElseThrow();

        assertEquals("UNVALIDATED", ret02.getValidationStatus(), "Must be UNVALIDATED");
        assertNull(ret02.getValidationEvidenceReference(), "Must NOT have fake validation evidence reference");
        assertNull(ret02.getApprovalRecord(), "Must NOT have fake approval record");
        assertNull(ret02.getApprovedBy(), "Must NOT have fake approver");
        assertNull(ret02.getApprovedAt(), "Must NOT have fake approval date");
    }

    @Test
    @DisplayName("Phase 2G Test 4: Methodology version immutability - Locked or calculation-referenced versions cannot be mutated")
    void testMethodologyVersionImmutability() {
        MethodologyVersion ret02 = methodologyVersionRepository
            .findByMethodologyCodeAndVersionTag("RET_02_SIMPLE_RETURN", "CANDIDATE_V1")
            .orElseThrow();

        // Lock version
        ret02.setLocked(true);
        methodologyVersionRepository.save(ret02);

        // Attempt to assert mutability must throw IllegalStateException
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> methodologyGovernanceService.assertVersionMutable(ret02),
            "Mutating a locked methodology version must be rejected"
        );
        assertTrue(ex.getMessage().contains("immutable"));
    }

    @Test
    @DisplayName("Phase 2G Test 5: Candidate vs Validated vs Approved lifecycle transitions")
    void testLifecycleProgression() {
        MetricDefinition metric = metricDefinitionRepository.findByMetricCode("RET-02").orElseThrow();

        MethodologyVersion testVer = new MethodologyVersion(
            "TEST_METRIC", "CANDIDATE_TEST_V1", "CANDIDATE", "0000000000000000000000000000000000000000"
        );
        testVer.setMetricDefinition(metric);
        testVer = methodologyGovernanceService.registerMethodologyVersion(testVer, "AUDITOR_TEST");

        assertEquals("CANDIDATE", testVer.getLifecycleStatus());
        assertEquals("UNVALIDATED", testVer.getValidationStatus());

        // Step 1: Transition to VALIDATED with real empirical evidence reference
        MethodologyVersion validated = methodologyGovernanceService.transitionLifecycle(
            testVer.getId(),
            "VALIDATED",
            "EMPIRICAL-STUDY-REF-2026-001: 5-year historical backtest on 50 mutual funds across all market cycles",
            "QUANT_VALIDATOR"
        );
        assertEquals("VALIDATED", validated.getLifecycleStatus());
        assertEquals("VALIDATED", validated.getValidationStatus());
        assertNotNull(validated.getValidationEvidenceReference());

        // Step 2: Transition to APPROVED with governance sign-off
        MethodologyVersion approved = methodologyGovernanceService.transitionLifecycle(
            validated.getId(),
            "APPROVED",
            "GOVERNANCE-APPROVAL-MINUTES-2026-09-19: Unanimously approved by Quantitative Audit Committee",
            "CHIEF_INVESTMENT_OFFICER"
        );
        assertEquals("APPROVED", approved.getLifecycleStatus());
        assertEquals("APPROVED", approved.getApprovalStatus());
        assertEquals("CHIEF_INVESTMENT_OFFICER", approved.getApprovedBy());
        assertNotNull(approved.getApprovedAt());
    }

    @Test
    @DisplayName("Phase 2G Test 6: Invalid lifecycle transitions are strictly rejected")
    void testInvalidLifecycleTransitionsRejected() {
        MetricDefinition metric = metricDefinitionRepository.findByMetricCode("RET-02").orElseThrow();

        MethodologyVersion testVer = new MethodologyVersion(
            "TEST_METRIC_INVALID", "CANDIDATE_TEST_V1", "CANDIDATE", "0000000000000000000000000000000000000000"
        );
        testVer.setMetricDefinition(metric);
        testVer = methodologyGovernanceService.registerMethodologyVersion(testVer, "AUDITOR_TEST");

        final Long versionId = testVer.getId();

        // 1. Direct CANDIDATE -> APPROVED without validation is rejected
        IllegalStateException directApproveEx = assertThrows(
            IllegalStateException.class,
            () -> methodologyGovernanceService.transitionLifecycle(
                versionId, "APPROVED", "Attempting skip", "ACTOR"
            )
        );
        assertTrue(directApproveEx.getMessage().contains("must first achieve VALIDATED status"));

        // 2. Transition to VALIDATED without empirical evidence reference is rejected
        IllegalArgumentException noEvidenceEx = assertThrows(
            IllegalArgumentException.class,
            () -> methodologyGovernanceService.transitionLifecycle(
                versionId, "VALIDATED", "", "ACTOR"
            )
        );
        assertTrue(noEvidenceEx.getMessage().contains("without empirical validation evidence reference"));

        // 3. Backward transition from APPROVED to CANDIDATE is rejected
        // First validate properly
        methodologyGovernanceService.transitionLifecycle(
            versionId, "VALIDATED", "Valid evidence reference #1", "VALIDATOR"
        );
        // Then approve properly
        methodologyGovernanceService.transitionLifecycle(
            versionId, "APPROVED", "Valid approval record #1", "APPROVER"
        );

        // Attempt backward demotion from APPROVED to CANDIDATE
        IllegalStateException demoteCandidateEx = assertThrows(
            IllegalStateException.class,
            () -> methodologyGovernanceService.transitionLifecycle(
                versionId, "CANDIDATE", "Demoting", "ACTOR"
            )
        );
        assertTrue(demoteCandidateEx.getMessage().contains("Cannot demote methodology"));

        // 4. Backward demotion from APPROVED to VALIDATED is rejected
        IllegalStateException demoteValidatedEx = assertThrows(
            IllegalStateException.class,
            () -> methodologyGovernanceService.transitionLifecycle(
                versionId, "VALIDATED", "Demoting to validated", "ACTOR"
            )
        );
        assertTrue(demoteValidatedEx.getMessage().contains("Only 'CANDIDATE' methodologies can be validated"));

        // 5. Transitioning a RETIRED version is strictly rejected
        methodologyGovernanceService.transitionLifecycle(versionId, "RETIRED", "Retiring version", "ARCHIVIST");
        IllegalStateException retiredEx = assertThrows(
            IllegalStateException.class,
            () -> methodologyGovernanceService.transitionLifecycle(
                versionId, "VALIDATED", "Attempting revive", "ACTOR"
            )
        );
        assertTrue(retiredEx.getMessage().contains("Retired versions are immutable historical records"));
    }

    @Test
    @DisplayName("Phase 2G Test 7: Historical version preservation and forking")
    void testHistoricalVersionPreservationAndForking() {
        MethodologyVersion v1 = methodologyVersionRepository
            .findByMethodologyCodeAndVersionTag("RET_02_SIMPLE_RETURN", "CANDIDATE_V1")
            .orElseThrow();

        // Fork new version V2
        MethodologyVersion v2 = methodologyGovernanceService.createNewVersion(
            v1.getId(),
            "CANDIDATE_V2",
            "1111111111111111111111111111111111111111",
            "RESEARCHER_1",
            "Exploratory lookback window adjustment test"
        );

        assertNotNull(v2.getId());
        assertEquals("CANDIDATE_V2", v2.getVersionTag());
        assertEquals("CANDIDATE", v2.getLifecycleStatus());
        assertEquals("UNVALIDATED", v2.getValidationStatus());
        assertNotNull(v2.getSupersedesVersion(), "Must link to predecessor version");
        assertEquals(v1.getId(), v2.getSupersedesVersion().getId());

        // Predecessor V1 must remain locked and intact
        MethodologyVersion reloadedV1 = methodologyVersionRepository.findById(v1.getId()).orElseThrow();
        assertTrue(reloadedV1.isLocked(), "Predecessor version must be permanently locked");
        assertEquals("CANDIDATE_V1", reloadedV1.getVersionTag());

        // Check change log audit trail
        List<MethodologyChangeLog> logs = methodologyGovernanceService.getChangeHistory(v2.getId());
        assertFalse(logs.isEmpty(), "Fork event must be recorded in change log");
        assertEquals("VERSION_FORK", logs.get(0).getTransitionType());
    }

    @Test
    @DisplayName("Phase 2G Test 8: CalculationRun linkage to methodology version locks version and preserves lineage")
    void testCalculationRunLinkageLocksVersion() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Seed 2 observations
        navObservationRepository.save(new NavObservation(schemeOption, startDate, new BigDecimal("100.00"), 1, cutoff.minusMonths(1)));
        navObservationRepository.save(new NavObservation(schemeOption, endDate, new BigDecimal("105.00"), 1, cutoff.minusDays(10)));

        CalculationRun run = periodReturnCalculationService.executeRet02Calculation(
            schemeOption.getId(),
            startDate,
            endDate,
            cutoff,
            "CANDIDATE_V1"
        );

        assertNotNull(run.getId());
        assertNotNull(run.getMethodologyVersion());
        assertEquals("CANDIDATE_V1", run.getMethodologyVersion().getVersionTag());

        // Verify calculationRunRepository.existsByMethodologyVersionId is true
        assertTrue(calculationRunRepository.existsByMethodologyVersionId(run.getMethodologyVersion().getId()));

        // Verify version is now permanently locked against mutation
        assertTrue(run.getMethodologyVersion().isLocked());
        assertThrows(
            IllegalStateException.class,
            () -> methodologyGovernanceService.assertVersionMutable(run.getMethodologyVersion())
        );
    }

    @Test
    @DisplayName("Phase 2G Test 9: Unused candidate methodology is mutable until used or explicitly locked")
    void testUnusedCandidateIsMutableUntilUsedOrLocked() {
        MetricDefinition metric = metricDefinitionRepository.findByMetricCode("RET-02").orElseThrow();

        MethodologyVersion draftVer = new MethodologyVersion(
            "RET_02_DRAFT", "CANDIDATE_DRAFT_V1", "CANDIDATE", "0000000000000000000000000000000000000000"
        );
        draftVer.setMetricDefinition(metric);
        draftVer.setMathematicalDefinition("Draft Formula V1");
        final MethodologyVersion registered = methodologyGovernanceService.registerMethodologyVersion(draftVer, "DEVELOPER");

        assertFalse(registered.isLocked(), "Newly registered unused candidate must not be locked");

        // 1. assertVersionMutable succeeds on unused unlocked version
        assertDoesNotThrow(() -> methodologyGovernanceService.assertVersionMutable(registered));

        // 2. updateMethodologyDefinition succeeds on mutable version
        registered.setMathematicalDefinition("Draft Formula V1 - Refined");
        MethodologyVersion updated = methodologyGovernanceService.updateMethodologyDefinition(registered, "DEVELOPER");
        assertEquals("Draft Formula V1 - Refined", updated.getMathematicalDefinition());

        // 3. Explicitly locking the version renders it permanently immutable
        methodologyGovernanceService.lockVersion(updated.getId(), "AUDITOR_LOCK");
        MethodologyVersion locked = methodologyVersionRepository.findById(updated.getId()).orElseThrow();
        assertTrue(locked.isLocked());

        // 4. Assert and update now strictly throw IllegalStateException
        assertThrows(
            IllegalStateException.class,
            () -> methodologyGovernanceService.assertVersionMutable(locked)
        );

        locked.setMathematicalDefinition("Attempting mutation on locked");
        assertThrows(
            IllegalStateException.class,
            () -> methodologyGovernanceService.updateMethodologyDefinition(locked, "DEVELOPER")
        );
    }

    @Test
    @DisplayName("Phase 2G Test 10: Historical calculation reproducibility across methodology version forks")
    void testHistoricalReproducibilityAcrossMethodologyVersionFork() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 15);
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Seed 2 observations
        navObservationRepository.save(new NavObservation(schemeOption, startDate, new BigDecimal("100.00"), 1, cutoff.minusMonths(1)));
        navObservationRepository.save(new NavObservation(schemeOption, endDate, new BigDecimal("105.00"), 1, cutoff.minusDays(10)));

        // Run 1: Historical run under CANDIDATE_V1
        CalculationRun run1 = periodReturnCalculationService.executeRet02Calculation(
            schemeOption.getId(), startDate, endDate, cutoff, "CANDIDATE_V1"
        );
        assertEquals("COMPLETED", run1.getRunStatus());
        MetricResult res1 = metricResultRepository.findByCalculationRunIdAndMetricCode(run1.getId(), "RET-02").get(0);
        BigDecimal val1 = res1.getNumericValue();
        assertEquals(0, new BigDecimal("0.0500").compareTo(val1.setScale(4, java.math.RoundingMode.HALF_UP)));

        // Fork to CANDIDATE_V2
        MethodologyVersion v1 = run1.getMethodologyVersion();
        MethodologyVersion v2 = methodologyGovernanceService.createNewVersion(
            v1.getId(),
            "CANDIDATE_V2_REPRO_TEST",
            "2222222222222222222222222222222222222222",
            "FORKER",
            "Testing reproducibility across version forks"
        );
        assertNotNull(v2.getId());

        // Run 2: Re-executing historical run under CANDIDATE_V1 must reproduce identical mathematical result
        CalculationRun run2 = periodReturnCalculationService.executeRet02Calculation(
            schemeOption.getId(), startDate, endDate, cutoff, "CANDIDATE_V1"
        );
        assertEquals("COMPLETED", run2.getRunStatus());
        MetricResult res2 = metricResultRepository.findByCalculationRunIdAndMetricCode(run2.getId(), "RET-02").get(0);
        BigDecimal val2 = res2.getNumericValue();

        // Exact numerical reproducibility verified
        assertEquals(val1, val2, "Re-running historical calculation under CANDIDATE_V1 must reproduce identical result");
        assertEquals(v1.getId(), run2.getMethodologyVersion().getId(), "Lineage must remain bound to CANDIDATE_V1");
    }
}
