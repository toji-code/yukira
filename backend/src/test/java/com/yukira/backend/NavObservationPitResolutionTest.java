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

    private SchemeOption schemeOption;

    @BeforeEach
    void setUp() {
        Amc amc = amcRepository.save(new Amc("HDFC Mutual Fund", "HDFC_MF"));
        Scheme scheme = schemeRepository.save(new Scheme(amc, "HDFC Flexi Cap Fund", "HDFC_FLEXI", LocalDate.of(1995, 1, 1)));
        SchemePlan plan = schemePlanRepository.save(new SchemePlan(scheme, "DIRECT", "HDFC_FLEXI_DIR"));
        schemeOption = schemeOptionRepository.save(new SchemeOption(plan, "GROWTH", "118989", "INF179K01BE2"));
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
    @DisplayName("CASE 4: Revision-order edge case: Revision 2 has earlier availability_time than Revision 1, both before cutoff -> Revision 2 selected by revision_seq")
    void testCase4_revisionSeqIsAuthoritativeOverAvailabilityTimestampEdgeCase() {
        LocalDate dateD = LocalDate.of(2026, 1, 15);
        // R1: ingested/stamped with availability at 12:00
        OffsetDateTime t1 = OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        // R2: backfilled/corrected with artifact availability timestamped at 11:00 (earlier than R1)
        OffsetDateTime t2 = OffsetDateTime.of(2026, 1, 15, 11, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        NavObservation r1 = new NavObservation(schemeOption, dateD, new BigDecimal("100.00000000"), 1, t1);
        navObservationRepository.save(r1);

        NavObservation r2 = new NavObservation(schemeOption, dateD, new BigDecimal("105.00000000"), 2, t2);
        r2.setRevisionStatus("REVISED");
        navObservationRepository.save(r2);

        // Cutoff at 15:00 IST (after both T1 and T2)
        OffsetDateTime tCutoff = OffsetDateTime.of(2026, 1, 15, 15, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        List<NavObservation> results = navObservationRepository
            .findAuthoritativeObservationsAsOfCutoff(schemeOption.getId(), dateD, tCutoff);

        assertEquals(1, results.size(), "Must return exactly one observation");
        NavObservation selected = results.get(0);
        assertEquals(2, selected.getRevisionSeq(),
            "Must select Revision 2 based on authoritative revision_seq DESC, not naive availability_time DESC");
        assertEquals(0, new BigDecimal("105.00000000").compareTo(selected.getNavValue()),
            "Must have Revision 2 NAV value");
    }
}
