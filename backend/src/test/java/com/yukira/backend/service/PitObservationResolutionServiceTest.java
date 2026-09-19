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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PitObservationResolutionServiceTest {

    @Autowired
    private PitObservationResolutionService pitService;

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
    private ValidationIssueRepository validationIssueRepository;

    private SchemeOption schemeOption;

    @BeforeEach
    void setUp() {
        Amc amc = amcRepository.findByCode("HDFC_MF")
            .orElseGet(() -> amcRepository.save(new Amc("HDFC Mutual Fund", "HDFC_MF")));
        Scheme scheme = schemeRepository.findByCode("HDFC_FLEXI")
            .orElseGet(() -> schemeRepository.save(new Scheme(amc, "HDFC Flexi Cap Fund", "HDFC_FLEXI", LocalDate.of(1995, 1, 1))));
        SchemePlan plan = schemePlanRepository.findByCode("HDFC_FLEXI_DIR")
            .orElseGet(() -> schemePlanRepository.save(new SchemePlan(scheme, "DIRECT", "HDFC_FLEXI_DIR")));
        schemeOption = schemeOptionRepository.findByAmfiCode("TEST_PIT_01")
            .orElseGet(() -> schemeOptionRepository.save(new SchemeOption(plan, "GROWTH", "TEST_PIT_01", "INF_TEST_PIT_01")));
    }

    @Test
    @DisplayName("PIT Test 1: Original revision selected when later revision is beyond knowledge cutoff")
    void testOriginalSelectedWhenRevisedAfterCutoff() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        OffsetDateTime t1 = OffsetDateTime.of(2024, 1, 15, 23, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime t2 = OffsetDateTime.of(2024, 1, 16, 14, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        NavObservation r1 = new NavObservation(schemeOption, date, new BigDecimal("100.00000000"), 1, t1);
        navObservationRepository.save(r1);

        NavObservation r2 = new NavObservation(schemeOption, date, new BigDecimal("102.50000000"), 2, t2);
        r2.setRevisionStatus("REVISED");
        navObservationRepository.save(r2);

        // Cutoff between T1 and T2
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 16, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PitObservationResolutionService.PitResolutionResult res = pitService
            .resolveAuthoritativeObservation(schemeOption.getId(), date, cutoff);

        assertTrue(res.authoritativeObservation().isPresent());
        assertEquals(1, res.authoritativeObservation().get().getRevisionSeq());
        assertEquals(0, new BigDecimal("100.00000000").compareTo(res.authoritativeObservation().get().getNavValue()));
        assertEquals(1, res.eligibleRevisions().size());
    }

    @Test
    @DisplayName("PIT Test 2: Revised observation selected when revision is available before knowledge cutoff")
    void testRevisedSelectedWhenAvailableBeforeCutoff() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        OffsetDateTime t1 = OffsetDateTime.of(2024, 1, 15, 23, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime t2 = OffsetDateTime.of(2024, 1, 16, 14, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        navObservationRepository.save(new NavObservation(schemeOption, date, new BigDecimal("100.00000000"), 1, t1));
        NavObservation r2 = new NavObservation(schemeOption, date, new BigDecimal("102.50000000"), 2, t2);
        r2.setRevisionStatus("REVISED");
        navObservationRepository.save(r2);

        // Cutoff after both T1 and T2
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 16, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PitObservationResolutionService.PitResolutionResult res = pitService
            .resolveAuthoritativeObservation(schemeOption.getId(), date, cutoff);

        assertTrue(res.authoritativeObservation().isPresent());
        assertEquals(2, res.authoritativeObservation().get().getRevisionSeq());
        assertEquals(0, new BigDecimal("102.50000000").compareTo(res.authoritativeObservation().get().getNavValue()));
        assertEquals(2, res.eligibleRevisions().size());
    }

    @Test
    @DisplayName("PIT Test 3: Revision ordering differs from availability ordering; availability takes precedence before cutoff")
    void testRevisionOrderingDiffersFromAvailability() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        // R1 stamped at 14:00
        OffsetDateTime t1 = OffsetDateTime.of(2024, 1, 15, 14, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        // R2 backfilled/stamped at 10:00 (earlier availability)
        OffsetDateTime t2 = OffsetDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        navObservationRepository.save(new NavObservation(schemeOption, date, new BigDecimal("100.00000000"), 1, t1));
        NavObservation r2 = new NavObservation(schemeOption, date, new BigDecimal("105.00000000"), 2, t2);
        navObservationRepository.save(r2);

        // Cutoff at 12:00 (between T2 10:00 and T1 14:00) -> R1 not yet eligible, R2 is eligible!
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PitObservationResolutionService.PitResolutionResult res = pitService
            .resolveAuthoritativeObservation(schemeOption.getId(), date, cutoff);

        assertTrue(res.authoritativeObservation().isPresent());
        assertEquals(2, res.authoritativeObservation().get().getRevisionSeq(), "R2 is the only eligible revision at 12:00");
    }

    @Test
    @DisplayName("PIT Test 4: Future revision cannot leak into historical calculation (strict lookahead prevention)")
    void testFutureObservationCannotLeak() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        OffsetDateTime tFuture = OffsetDateTime.of(2024, 1, 16, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        navObservationRepository.save(new NavObservation(schemeOption, date, new BigDecimal("110.00000000"), 1, tFuture));

        // Cutoff on date itself before publication
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PitObservationResolutionService.PitResolutionResult res = pitService
            .resolveAuthoritativeObservation(schemeOption.getId(), date, cutoff);

        assertTrue(res.authoritativeObservation().isEmpty());
        assertFalse(res.isAmbiguous());
        assertTrue(res.diagnosticReason().contains("after knowledge cutoff"));
    }

    @Test
    @DisplayName("PIT Test 5 (Regression): Higher revision_seq unavailable at knowledge cutoff MUST NOT override eligible observation")
    void testHigherRevisionSeqUnavailableAtCutoffDoesNotOverrideEligible() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        // R1: available at 10:00 (eligible at cutoff 12:00)
        OffsetDateTime t1 = OffsetDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        // R2: revision with revision_seq=2, but only available at 16:00 (after cutoff 12:00)
        OffsetDateTime t2 = OffsetDateTime.of(2024, 1, 15, 16, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        navObservationRepository.save(new NavObservation(schemeOption, date, new BigDecimal("100.00000000"), 1, t1));
        NavObservation r2 = new NavObservation(schemeOption, date, new BigDecimal("105.00000000"), 2, t2);
        r2.setRevisionStatus("REVISED");
        navObservationRepository.save(r2);

        // Cutoff at 12:00 (R1 is eligible, R2 is after cutoff)
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PitObservationResolutionService.PitResolutionResult res = pitService
            .resolveAuthoritativeObservation(schemeOption.getId(), date, cutoff);

        assertTrue(res.authoritativeObservation().isPresent());
        assertEquals(1, res.authoritativeObservation().get().getRevisionSeq(),
            "Eligible Revision 1 must be selected; higher revision_seq=2 must NOT override it because it was unavailable at cutoff");
        assertEquals(0, new BigDecimal("100.00000000").compareTo(res.authoritativeObservation().get().getNavValue()));
        assertEquals(1, res.eligibleRevisions().size());
    }

    @Test
    @DisplayName("PIT Test 6 (Regression): Multiple eligible revisions at latest availability instant with conflicting NAV values MUST yield AMBIGUOUS/CONFLICTING")
    void testConflictingValuesAtLatestAvailabilityInstantYieldsAmbiguous() {
        LocalDate date = LocalDate.of(2024, 1, 16);
        OffsetDateTime t = OffsetDateTime.of(2024, 1, 16, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Observation A: same scheme, same date, availability=T, revision_seq=1, NAV=100
        navObservationRepository.save(new NavObservation(schemeOption, date, new BigDecimal("100.00000000"), 1, t));

        // Observation B: same scheme, same date, availability=T, revision_seq=2, NAV=101 (conflicting value)
        NavObservation b = new NavObservation(schemeOption, date, new BigDecimal("101.00000000"), 2, t);
        b.setRevisionStatus("REVISED");
        navObservationRepository.save(b);

        // Knowledge cutoff > T
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 16, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PitObservationResolutionService.PitResolutionResult res = pitService
            .resolveAuthoritativeObservation(schemeOption.getId(), date, cutoff);

        // Expected result MUST be AMBIGUOUS / CONFLICTING (NOT 101, NOT 100, NOT arbitrary winner)
        assertTrue(res.authoritativeObservation().isEmpty(),
            "Must NOT select an authoritative observation when conflicting values exist at latest availability instant");
        assertTrue(res.isAmbiguous(), "Must be explicitly flagged as ambiguous");
        assertNotNull(res.diagnosticReason());
        assertTrue(res.diagnosticReason().toLowerCase().contains("conflicting"));

        // Verify ValidationIssue saved with SUSPICIOUS and CONFLICTING
        List<ValidationIssue> issues = validationIssueRepository
            .findByTargetEntityTypeAndTargetEntityId("SCHEME_OPTION", schemeOption.getId());
        assertFalse(issues.isEmpty());
        ValidationIssue issue = issues.get(issues.size() - 1);
        assertEquals("PIT_AUTHORITY_AMBIGUITY", issue.getCheckCode());
        assertEquals("SUSPICIOUS", issue.getQualityAssessment());
        assertEquals("CONFLICTING", issue.getIntegrityCondition());
    }

    @Test
    @DisplayName("PIT Test 7: Multiple eligible revisions at latest availability instant with IDENTICAL NAV values -> revision_seq DESC breaks tie deterministically")
    void testIdenticalValuesAtLatestAvailabilityInstantResolvesDeterministically() {
        LocalDate date = LocalDate.of(2024, 1, 17);
        OffsetDateTime t = OffsetDateTime.of(2024, 1, 17, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Observation A: revision_seq=1, NAV=100
        navObservationRepository.save(new NavObservation(schemeOption, date, new BigDecimal("100.00000000"), 1, t));

        // Observation B: revision_seq=2, NAV=100 (identical value at same instant)
        NavObservation b = new NavObservation(schemeOption, date, new BigDecimal("100.00000000"), 2, t);
        b.setRevisionStatus("REVISED");
        navObservationRepository.save(b);

        // Knowledge cutoff > T
        OffsetDateTime cutoff = OffsetDateTime.of(2024, 1, 17, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        PitObservationResolutionService.PitResolutionResult res = pitService
            .resolveAuthoritativeObservation(schemeOption.getId(), date, cutoff);

        assertTrue(res.authoritativeObservation().isPresent(), "Identical values can be deterministically resolved");
        assertFalse(res.isAmbiguous());
        assertEquals(2, res.authoritativeObservation().get().getRevisionSeq(),
            "When values are identical, revision_seq=2 acts as deterministic tie-breaker");
        assertEquals(0, new BigDecimal("100.00000000").compareTo(res.authoritativeObservation().get().getNavValue()));
    }
}
