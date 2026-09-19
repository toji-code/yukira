package com.yukira.backend;

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
class NavObservationPitResolutionTest {

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
    private com.yukira.backend.service.PitObservationResolutionService pitResolutionService;

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
        schemeOption = schemeOptionRepository.findByAmfiCode("118989")
            .orElseGet(() -> schemeOptionRepository.save(new SchemeOption(plan, "GROWTH", "118989", "INF118989BE2")));
    }

    @Test
    @DisplayName("CASE 1: Revision 1 available before cutoff, Revision 2 after cutoff -> Revision 1 selected")
    void testCase1_revision1SelectedWhenRevision2AfterCutoff() {
        LocalDate dateD = LocalDate.of(2026, 1, 15);
        OffsetDateTime t1 = OffsetDateTime.of(2026, 1, 15, 23, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime t2 = OffsetDateTime.of(2026, 1, 16, 14, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // R1: original observation
        NavObservation r1 = new NavObservation(schemeOption, dateD, new BigDecimal("100.00000000"), 1, t1);
        navObservationRepository.save(r1);

        // R2: revision published next afternoon
        NavObservation r2 = new NavObservation(schemeOption, dateD, new BigDecimal("101.50000000"), 2, t2);
        r2.setRevisionStatus("REVISED");
        navObservationRepository.save(r2);

        // Cutoff: 2026-01-16 10:00 IST (Between T1 and T2)
        OffsetDateTime tCutoff = OffsetDateTime.of(2026, 1, 16, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        List<NavObservation> results = navObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(schemeOption.getId(), dateD, tCutoff);

        // Exactly 1 authoritative observation for date D
        assertEquals(1, results.size(), "Must return exactly one authoritative observation for effective date D");
        NavObservation selected = results.get(0);
        assertEquals(1, selected.getRevisionSeq(), "Must select Revision 1");
        assertEquals(0, new BigDecimal("100.00000000").compareTo(selected.getNavValue()), "Must have Revision 1 NAV value");
    }

    @Test
    @DisplayName("CASE 2: Both Revision 1 and Revision 2 available before cutoff -> Revision 2 selected")
    void testCase2_revision2SelectedWhenBothAvailableBeforeCutoff() {
        LocalDate dateD = LocalDate.of(2026, 1, 15);
        OffsetDateTime t1 = OffsetDateTime.of(2026, 1, 15, 23, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime t2 = OffsetDateTime.of(2026, 1, 16, 14, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        NavObservation r1 = new NavObservation(schemeOption, dateD, new BigDecimal("100.00000000"), 1, t1);
        navObservationRepository.save(r1);

        NavObservation r2 = new NavObservation(schemeOption, dateD, new BigDecimal("101.50000000"), 2, t2);
        r2.setRevisionStatus("REVISED");
        navObservationRepository.save(r2);

        // Cutoff: 2026-01-16 18:00 IST (After both T1 and T2)
        OffsetDateTime tCutoff = OffsetDateTime.of(2026, 1, 16, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        List<NavObservation> results = navObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(schemeOption.getId(), dateD, tCutoff);

        assertEquals(1, results.size(), "Must return exactly one authoritative observation for effective date D");
        NavObservation selected = results.get(0);
        assertEquals(2, selected.getRevisionSeq(), "Must select Revision 2 as authoritative");
        assertEquals(0, new BigDecimal("101.50000000").compareTo(selected.getNavValue()), "Must have Revision 2 NAV value");
    }

    @Test
    @DisplayName("CASE 3: Observation available only after cutoff -> Observation excluded")
    void testCase3_observationExcludedWhenAvailableAfterCutoff() {
        LocalDate dateFuture = LocalDate.of(2026, 1, 16);
        OffsetDateTime tPublished = OffsetDateTime.of(2026, 1, 16, 23, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        NavObservation obs = new NavObservation(schemeOption, dateFuture, new BigDecimal("102.00000000"), 1, tPublished);
        navObservationRepository.save(obs);

        // Cutoff before publication: 2026-01-16 12:00 IST
        OffsetDateTime tCutoff = OffsetDateTime.of(2026, 1, 16, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        List<NavObservation> results = navObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(schemeOption.getId(), dateFuture, tCutoff);

        assertTrue(results.isEmpty(), "Observation must be completely excluded when availability_time > knowledge_cutoff");
    }

    @Test
    @DisplayName("CASE 4 (Regression): Higher revision_seq unavailable at knowledge cutoff MUST NOT override an eligible observation")
    void testCase4_higherRevisionSeqUnavailableAtCutoffMustNotOverrideEligibleObservation() {
        LocalDate dateD = LocalDate.of(2026, 1, 15);
        // R1: available at 10:00 (prior to cutoff)
        OffsetDateTime t1 = OffsetDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        // R2: restatement with higher revision_seq (2), but only available at 16:00 (after cutoff)
        OffsetDateTime t2 = OffsetDateTime.of(2026, 1, 15, 16, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        NavObservation r1 = new NavObservation(schemeOption, dateD, new BigDecimal("100.00000000"), 1, t1);
        navObservationRepository.save(r1);

        NavObservation r2 = new NavObservation(schemeOption, dateD, new BigDecimal("105.00000000"), 2, t2);
        r2.setRevisionStatus("REVISED");
        navObservationRepository.save(r2);

        // Cutoff at 14:00 IST (between T1 10:00 and T2 16:00)
        OffsetDateTime tCutoff = OffsetDateTime.of(2026, 1, 15, 14, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        List<NavObservation> results = navObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(schemeOption.getId(), dateD, tCutoff);

        assertEquals(1, results.size(), "Must return exactly one eligible observation");
        NavObservation selected = results.get(0);
        assertEquals(1, selected.getRevisionSeq(),
            "R1 (revision_seq=1) must be selected because higher revision_seq=2 was unavailable at cutoff");
        assertEquals(0, new BigDecimal("100.00000000").compareTo(selected.getNavValue()),
            "Must preserve eligible Revision 1 value without leakage of future revision");
    }

    @Test
    @DisplayName("CASE 5: Multiple eligible revisions at identical availability_time with conflicting values -> query returns all candidates and resolution detects PIT_AUTHORITY_AMBIGUITY")
    void testCase5_conflictingValuesAtIdenticalAvailabilityTimeYieldsAmbiguity() {
        LocalDate dateD = LocalDate.of(2026, 1, 15);
        // R1 and R2 both delivered/stamped at exact same instant
        OffsetDateTime tSame = OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        NavObservation r1 = new NavObservation(schemeOption, dateD, new BigDecimal("100.00000000"), 1, tSame);
        navObservationRepository.save(r1);

        NavObservation r2 = new NavObservation(schemeOption, dateD, new BigDecimal("102.00000000"), 2, tSame);
        r2.setRevisionStatus("REVISED");
        navObservationRepository.save(r2);

        OffsetDateTime tCutoff = OffsetDateTime.of(2026, 1, 15, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Step 1: Verify the repository CTE query returns BOTH candidates and does NOT silently discard R1 via DISTINCT ON
        List<NavObservation> results = navObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(schemeOption.getId(), dateD, tCutoff);

        assertEquals(2, results.size(), "Repository query must return all candidates at max availability time, not discard one prematurely");

        // Step 2: Verify service resolution correctly flags ambiguity instead of arbitrarily picking revision_seq 2
        var resolutionResult = pitResolutionService.resolveAuthoritativeObservation(schemeOption.getId(), dateD, tCutoff);
        assertTrue(resolutionResult.isAmbiguous(), "Conflicting NAVs at identical availability time must produce an ambiguous result");
        assertTrue(resolutionResult.authoritativeObservation().isEmpty(), "No authoritative observation may be selected when values conflict");

        List<ValidationIssue> issues = validationIssueRepository
            .findByTargetEntityTypeAndTargetEntityId("SCHEME_OPTION", schemeOption.getId());
        assertFalse(issues.isEmpty());
        ValidationIssue issue = issues.get(issues.size() - 1);
        assertEquals("PIT_AUTHORITY_AMBIGUITY", issue.getCheckCode());
        assertEquals("SUSPICIOUS", issue.getQualityAssessment());
        assertEquals("CONFLICTING", issue.getIntegrityCondition());
    }

    @Test
    @DisplayName("CASE 6: Multiple eligible revisions at identical availability_time with identical values -> deterministic tie-break selects higher revision_seq")
    void testCase6_identicalValuesAtIdenticalAvailabilityTimeResolvesDeterministically() {
        LocalDate dateD = LocalDate.of(2026, 1, 15);
        OffsetDateTime tSame = OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        NavObservation r1 = new NavObservation(schemeOption, dateD, new BigDecimal("100.00000000"), 1, tSame);
        navObservationRepository.save(r1);

        NavObservation r2 = new NavObservation(schemeOption, dateD, new BigDecimal("100.00000000"), 2, tSame);
        r2.setRevisionStatus("REVISED");
        navObservationRepository.save(r2);

        OffsetDateTime tCutoff = OffsetDateTime.of(2026, 1, 15, 18, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        var resolutionResult = pitResolutionService.resolveAuthoritativeObservation(schemeOption.getId(), dateD, tCutoff);
        assertFalse(resolutionResult.isAmbiguous(), "Identical NAVs at identical availability time should resolve deterministically");
        assertTrue(resolutionResult.authoritativeObservation().isPresent());
        NavObservation selected = resolutionResult.authoritativeObservation().get();
        assertEquals(2, selected.getRevisionSeq(), "revision_seq DESC breaks tie when values are identical");
        assertEquals(0, new BigDecimal("100.00000000").compareTo(selected.getNavValue()));
    }
}
