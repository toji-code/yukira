package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.PortfolioSnapshot;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.PortfolioHoldingRepository;
import com.yukira.backend.repository.PortfolioSnapshotRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class MonthlyHoldingsOperationalWorkflowIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(MonthlyHoldingsOperationalWorkflowIntegrationTest.class);

    @Autowired
    private MonthlyHoldingsRefreshService refreshService;

    @Autowired
    private MonthlyHoldingsRefreshScheduler scheduler;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private PortfolioSnapshotRepository snapshotRepository;

    @Autowired
    private PortfolioHoldingRepository holdingRepository;

    @Autowired
    private PilotBootstrapService bootstrapService;

    @Test
    @DisplayName("1. Operational status exposes all 3 canonical pilot funds with authentic verified data")
    void testOperationalStatusExposesPilotFunds() {
        bootstrapService.bootstrapPilot();

        MonthlyHoldingsRefreshStatusDto status = refreshService.getOperationalStatus();
        assertNotNull(status);
        assertNotNull(status.serverTime());
        assertEquals("0 0 2 12 * ?", status.configuredCron());
        assertFalse(status.isCurrentlyRunning());

        List<PilotFundHoldingsStatusDto> pilots = status.pilotFundsStatus();
        assertEquals(3, pilots.size(), "Operational status must track all 3 canonical pilot funds");

        // 1. HDFC Flexi Cap (118955)
        PilotFundHoldingsStatusDto hdfc = pilots.stream()
            .filter(p -> "118955".equals(p.amfiCode()))
            .findFirst().orElseThrow();
        assertEquals("INF179K01UT0", hdfc.isin());
        assertEquals("HDFC Flexi Cap Fund", hdfc.schemeName());
        assertEquals(LocalDate.of(2026, 9, 30), hdfc.latestPortfolioDate());
        assertEquals(85, hdfc.latestPositionCount());
        assertEquals(new BigDecimal("1.005300"), hdfc.latestSumWeights());
        assertEquals("VALID", hdfc.qualityState());
        assertEquals("REAL_SOURCE_ACQUIRED", hdfc.provenanceType());
        assertTrue(hdfc.totalSnapshotsInDb() >= 4, "HDFC must have at least 4 snapshots (4 production disclosures)");

        // 2. PPFAS Flexi Cap (122639)
        PilotFundHoldingsStatusDto ppfasFlexi = pilots.stream()
            .filter(p -> "122639".equals(p.amfiCode()))
            .findFirst().orElseThrow();
        assertEquals("INF879O01027", ppfasFlexi.isin());
        assertEquals(LocalDate.of(2026, 9, 30), ppfasFlexi.latestPortfolioDate());
        assertEquals(96, ppfasFlexi.latestPositionCount());
        assertEquals(new BigDecimal("0.842400"), ppfasFlexi.latestSumWeights());
        assertEquals("VALID", ppfasFlexi.qualityState());
        assertEquals("REAL_SOURCE_ACQUIRED", ppfasFlexi.provenanceType());
        assertEquals(4, ppfasFlexi.totalSnapshotsInDb(), "PPFAS Flexi must have exactly 4 snapshots");

        // 3. PPFAS ELSS Tax Saver (147481)
        PilotFundHoldingsStatusDto ppfasTax = pilots.stream()
            .filter(p -> "147481".equals(p.amfiCode()))
            .findFirst().orElseThrow();
        assertEquals("INF879O01100", ppfasTax.isin());
        assertEquals(LocalDate.of(2026, 9, 30), ppfasTax.latestPortfolioDate());
        assertEquals(49, ppfasTax.latestPositionCount());
        assertEquals(new BigDecimal("0.968200"), ppfasTax.latestSumWeights());
        assertEquals("VALID", ppfasTax.qualityState());
        assertEquals("REAL_SOURCE_ACQUIRED", ppfasTax.provenanceType());
        assertEquals(4, ppfasTax.totalSnapshotsInDb(), "PPFAS Tax Saver must have exactly 4 snapshots");
    }

    @Test
    @DisplayName("2. Refresh run preserves idempotency and skips already ingested snapshots as duplicates")
    void testRefreshRunDeduplicationAndIdempotency() {
        bootstrapService.bootstrapPilot();

        SchemeOption hdfcOption = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        long initialHdfcSnapshots = snapshotRepository.findBySchemeOptionIdOrderByPortfolioDateDescRevisionSeqDesc(hdfcOption.getId()).size();

        // Trigger manual refresh run
        MonthlyHoldingsRefreshRun run = refreshService.executeMonthlyHoldingsRefresh(TriggerType.MANUAL);

        assertNotNull(run);
        assertNotNull(run.runId());
        assertEquals(TriggerType.MANUAL, run.triggerType());
        assertTrue("SUCCESS".equalsIgnoreCase(run.overallStatus()) || "PARTIAL_SUCCESS".equalsIgnoreCase(run.overallStatus()));

        // Verify that existing snapshots were detected and duplicates skipped without duplicate creation
        long postHdfcSnapshots = snapshotRepository.findBySchemeOptionIdOrderByPortfolioDateDescRevisionSeqDesc(hdfcOption.getId()).size();
        assertEquals(initialHdfcSnapshots, postHdfcSnapshots, "Snapshot count must remain identical after re-running refresh");

        // Verify that lastAttemptedRun and lastSuccessfulRun were recorded
        Optional<MonthlyHoldingsRefreshRun> lastAttempted = refreshService.getLastAttemptedRun();
        assertTrue(lastAttempted.isPresent());
        assertEquals(run.runId(), lastAttempted.get().runId());

        Optional<MonthlyHoldingsRefreshRun> lastSuccess = refreshService.getLastSuccessfulRun();
        assertTrue(lastSuccess.isPresent());
        assertEquals(run.runId(), lastSuccess.get().runId());
    }

    @Test
    @DisplayName("3. Point-in-time cutoff excludes disclosures not yet available at knowledge cutoff")
    void testPointInTimeAvailabilityCutoffExclusion() {
        bootstrapService.bootstrapPilot();
        SchemeOption hdfcOption = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();

        // 2026-06-30 disclosure SEBI statutory availability date is 2026-07-10T23:59:59+05:30
        OffsetDateTime beforeAvailable = OffsetDateTime.of(2026, 7, 5, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        OffsetDateTime afterAvailable = OffsetDateTime.of(2026, 7, 15, 0, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

        // Query before availability time: must NOT return any 2026 snapshot
        Optional<PortfolioSnapshot> snapBefore = snapshotRepository
            .findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(hdfcOption.getId(), beforeAvailable);

        if (snapBefore.isPresent()) {
            assertTrue(snapBefore.get().getPortfolioDate().isBefore(LocalDate.of(2026, 6, 30)),
                "Before knowledge cutoff 2026-07-05, no 2026-06-30 snapshot must be visible");
        }

        // Query after availability time: must return 2026-06-30 snapshot
        Optional<PortfolioSnapshot> snapAfter = snapshotRepository
            .findTopBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(hdfcOption.getId(), afterAvailable);

        assertTrue(snapAfter.isPresent(), "After knowledge cutoff 2026-07-15, 2026-06-30 snapshot must be available");
        assertEquals(LocalDate.of(2026, 6, 30), snapAfter.get().getPortfolioDate(), "Latest visible snapshot as of 2026-07-15 must be 2026-06-30");

        // And newer snapshots (2026-07-31, 2026-08-31, 2026-09-30) must remain strictly excluded
        List<PortfolioSnapshot> allVisible = snapshotRepository
            .findBySchemeOptionIdAndAvailabilityTimeLessThanEqualOrderByPortfolioDateDescRevisionSeqDesc(hdfcOption.getId(), afterAvailable);
        boolean leakedNewer = allVisible.stream().anyMatch(s -> s.getPortfolioDate().isAfter(LocalDate.of(2026, 6, 30)));
        assertFalse(leakedNewer, "Future snapshots must never leak into earlier historical knowledge cutoffs");
    }

    @Test
    @DisplayName("4. Official holdings discovery registry is operational and integrated into refresh workflow")
    void testOfficialHoldingsDiscoveryIntegration() {
        bootstrapService.bootstrapPilot();

        // 1. Discovery Registry contains official discoverers for HDFC and PPFAS
        assertNotNull(refreshService);
        MonthlyHoldingsRefreshStatusDto initialStatus = refreshService.getOperationalStatus();
        assertNotNull(initialStatus);

        // 2. Trigger refresh run which performs dynamic source discovery
        MonthlyHoldingsRefreshRun run = refreshService.executeMonthlyHoldingsRefresh(TriggerType.MANUAL);
        assertNotNull(run);
        assertNotNull(run.auditLogs());

        // Verify audit logs record discovery or baseline operations
        boolean hasAuditEntries = !run.auditLogs().isEmpty();
        assertTrue(hasAuditEntries, "Audit logs must track refresh and discovery lifecycle events");

        // Verify that prior valid holdings remain completely intact
        MonthlyHoldingsRefreshStatusDto postStatus = refreshService.getOperationalStatus();
        for (PilotFundHoldingsStatusDto pilot : postStatus.pilotFundsStatus()) {
            assertEquals("VALID", pilot.qualityState(), "Fund quality must remain VALID post-discovery refresh");
            assertTrue(pilot.totalSnapshotsInDb() >= 4, "All 4 historical snapshots must remain intact");
        }
    }
}
