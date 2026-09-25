package com.yukira.backend.service;

import com.yukira.backend.domain.entity.RiskFreeObservation;
import com.yukira.backend.repository.RiskFreeObservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@DisplayName("Risk-Free Point-in-Time Resolution Tests")
class RiskFreePitResolutionTest {

    @Autowired
    private PitObservationResolutionService pitResolutionService;

    @Autowired
    private RiskFreeObservationRepository riskFreeObservationRepository;

    private static final String BENCHMARK = "FBIL_TEST_TBILL";

    @Test
    @DisplayName("Resolves authoritative risk-free rate at valid knowledge cutoff")
    void testResolveAuthoritativeRiskFreeObservationAsOfKnowledgeCutoff() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        OffsetDateTime avail = OffsetDateTime.of(2024, 1, 15, 17, 30, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        RiskFreeObservation obs = new RiskFreeObservation(BENCHMARK, date, new BigDecimal("0.06950000"), "ACT_365", 1, avail);
        riskFreeObservationRepository.save(obs);

        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        var result = pitResolutionService.resolveAuthoritativeRiskFreeObservation(BENCHMARK, date, knowledgeCutoff);

        assertTrue(result.authoritativeObservation().isPresent());
        assertEquals(new BigDecimal("0.06950000"), result.authoritativeObservation().get().getQuotedYield());
        assertFalse(result.isAmbiguous());
    }

    @Test
    @DisplayName("Excludes future published observation when knowledge cutoff precedes availability time")
    void testFutureAvailabilityObservationExcluded() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        OffsetDateTime avail = OffsetDateTime.of(2024, 1, 15, 17, 30, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        RiskFreeObservation obs = new RiskFreeObservation(BENCHMARK, date, new BigDecimal("0.06950000"), "ACT_365", 1, avail);
        riskFreeObservationRepository.save(obs);

        // Knowledge cutoff is 12:00 PM (before publication at 5:30 PM)
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        var result = pitResolutionService.resolveAuthoritativeRiskFreeObservation(BENCHMARK, date, knowledgeCutoff);

        assertTrue(result.authoritativeObservation().isEmpty());
        assertFalse(result.isAmbiguous());
        assertTrue(result.diagnosticReason().contains("after knowledge cutoff"));
    }

    @Test
    @DisplayName("Selects highest revision available prior to knowledge cutoff without lookahead")
    void testHighestRevisionSelectedDeterministically() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        OffsetDateTime avail1 = OffsetDateTime.of(2024, 1, 15, 17, 30, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime avail2 = OffsetDateTime.of(2024, 1, 16, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        RiskFreeObservation r1 = new RiskFreeObservation(BENCHMARK, date, new BigDecimal("0.06950000"), "ACT_365", 1, avail1);
        r1.setLatestRevision(false);
        riskFreeObservationRepository.save(r1);

        RiskFreeObservation r2 = new RiskFreeObservation(BENCHMARK, date, new BigDecimal("0.06960000"), "ACT_365", 2, avail2);
        r2.setRevisionStatus("REVISED");
        riskFreeObservationRepository.save(r2);

        // Cutoff on Jan 15 only knows r1
        OffsetDateTime cutoffJan15 = OffsetDateTime.of(2024, 1, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        var res1 = pitResolutionService.resolveAuthoritativeRiskFreeObservation(BENCHMARK, date, cutoffJan15);
        assertTrue(res1.authoritativeObservation().isPresent());
        assertEquals(new BigDecimal("0.06950000"), res1.authoritativeObservation().get().getQuotedYield());
        assertEquals(1, res1.authoritativeObservation().get().getRevisionSeq());

        // Cutoff on Jan 16 knows r2
        OffsetDateTime cutoffJan16 = OffsetDateTime.of(2024, 1, 16, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        var res2 = pitResolutionService.resolveAuthoritativeRiskFreeObservation(BENCHMARK, date, cutoffJan16);
        assertTrue(res2.authoritativeObservation().isPresent());
        assertEquals(new BigDecimal("0.06960000"), res2.authoritativeObservation().get().getQuotedYield());
        assertEquals(2, res2.authoritativeObservation().get().getRevisionSeq());
    }

    @Test
    @DisplayName("Conflicting observations at the latest availability instant return ambiguous authority")
    void testConflictingRevisionsAtLatestInstantReturnsAmbiguous() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        OffsetDateTime avail = OffsetDateTime.of(2024, 1, 15, 17, 30, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        RiskFreeObservation obs1 = new RiskFreeObservation(BENCHMARK, date, new BigDecimal("0.06950000"), "ACT_365", 1, avail);
        riskFreeObservationRepository.save(obs1);

        RiskFreeObservation obs2 = new RiskFreeObservation(BENCHMARK, date, new BigDecimal("0.07100000"), "ACT_365", 2, avail);
        obs2.setIntegrityCondition("CONFLICTING");
        riskFreeObservationRepository.save(obs2);

        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 1, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        var result = pitResolutionService.resolveAuthoritativeRiskFreeObservation(BENCHMARK, date, knowledgeCutoff);

        assertTrue(result.authoritativeObservation().isEmpty());
        assertTrue(result.isAmbiguous());
        assertTrue(result.diagnosticReason().contains("Conflicting eligible risk-free revisions"));
    }

    @Test
    @DisplayName("Missing observation returns clean missing result without throwing")
    void testMissingObservationReturnsMissing() {
        LocalDate date = LocalDate.of(2024, 5, 20);
        OffsetDateTime knowledgeCutoff = OffsetDateTime.of(2024, 5, 20, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));

        var result = pitResolutionService.resolveAuthoritativeRiskFreeObservation(BENCHMARK, date, knowledgeCutoff);
        assertTrue(result.authoritativeObservation().isEmpty());
        assertFalse(result.isAmbiguous());
        assertTrue(result.diagnosticReason().contains("No risk-free observations found"));
    }
}
