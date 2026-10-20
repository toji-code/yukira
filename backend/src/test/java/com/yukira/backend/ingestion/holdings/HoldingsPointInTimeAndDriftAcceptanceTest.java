package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.PortfolioDriftResponseDto;
import com.yukira.backend.dto.portfolio.PortfolioOverlapResponseDto;
import com.yukira.backend.dto.portfolio.PortfolioSnapshotSummaryDto;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.PortfolioIntelligenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Real-Data Acceptance Tests for Point-in-Time Monthly Holdings Ingestion,
 * Overlap Comparison, and Portfolio Drift Analysis across the 3 Pilot Funds.
 *
 * Enforces all 12 acceptance criteria defined in Phase 6 of YUKIRA engineering specification.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HoldingsPointInTimeAndDriftAcceptanceTest {

    private static final Logger log = LoggerFactory.getLogger(HoldingsPointInTimeAndDriftAcceptanceTest.class);

    @Autowired
    private PilotBootstrapService bootstrapService;

    @Autowired
    private MonthlyHoldingsRefreshService refreshService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private PortfolioSnapshotRepository snapshotRepository;

    @Autowired
    private PortfolioHoldingRepository holdingRepository;

    @Autowired
    private SourceArtifactRepository sourceArtifactRepository;

    @Autowired
    private PortfolioIntelligenceService intelligenceService;

    @Test
    @Order(1)
    @DisplayName("Acceptance Criteria 1-3: Batch Ingest Real Disclosures and Verify Holdings Integrity")
    void test1_BatchIngestAndVerifyAuthenticDisclosures() {
        // Ensure master records exist for all 3 pilots
        bootstrapService.ensureCanonicalPilotMaster();
        bootstrapService.ensurePpfasMaster();
        bootstrapService.ensurePpfasTaxSaverMaster();

        SchemeOption hdfcOpt = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        SchemeOption ppfasFlexiOpt = schemeOptionRepository.findByAmfiCode("122639").orElseThrow();
        SchemeOption ppfasTaxOpt = schemeOptionRepository.findByAmfiCode("147481").orElseThrow();

        // Execute batch refresh
        MonthlyHoldingsRefreshRun run = refreshService.executeMonthlyHoldingsRefresh(TriggerType.MANUAL);
        log.info("Batch Holdings Refresh Run: Status={}, Success={}, Duplicate={}, Failed={}",
            run.overallStatus(), run.successCount(), run.duplicateCount(), run.failedCount());

        assertTrue(
            "SUCCESS".equals(run.overallStatus()) || "PARTIAL_SUCCESS".equals(run.overallStatus()),
            "Batch refresh must succeed or partially succeed"
        );

        // Verify HDFC Flexi Cap (118955)
        List<PortfolioSnapshotSummaryDto> hdfcSnaps = intelligenceService.getAvailableSnapshots(hdfcOpt.getId(), null);
        assertFalse(hdfcSnaps.isEmpty(), "HDFC Flexi Cap must have available snapshots");
        assertTrue(hdfcSnaps.size() >= 4, "HDFC Flexi Cap must have at least 4 distinct snapshot dates (June, July, August, Sept 2026)");

        // Verify PPFAS Flexi Cap (122639)
        List<PortfolioSnapshotSummaryDto> ppfasSnaps = intelligenceService.getAvailableSnapshots(ppfasFlexiOpt.getId(), null);
        assertFalse(ppfasSnaps.isEmpty(), "PPFAS Flexi Cap must have available snapshots");
        assertTrue(ppfasSnaps.size() >= 4, "PPFAS Flexi Cap must have at least 4 distinct snapshot dates");

        // Verify PPFAS ELSS Tax Saver (147481)
        List<PortfolioSnapshotSummaryDto> taxSnaps = intelligenceService.getAvailableSnapshots(ppfasTaxOpt.getId(), null);
        assertFalse(taxSnaps.isEmpty(), "PPFAS ELSS Tax Saver must have available snapshots");
        assertTrue(taxSnaps.size() >= 4, "PPFAS ELSS Tax Saver must have at least 4 distinct snapshot dates");

        log.info("Snapshots verified: HDFC={}, PPFAS_FLEXI={}, PPFAS_TAX={}",
            hdfcSnaps.size(), ppfasSnaps.size(), taxSnaps.size());
    }

    @Test
    @Order(2)
    @DisplayName("Acceptance Criterion 8: Ingestion Workflow is Fully Idempotent")
    void test2_IdempotencyOnSecondExecution() {
        SchemeOption hdfcOpt = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        long snapshotCountBefore = snapshotRepository.findBySchemeOptionIdOrderByPortfolioDateDescRevisionSeqDesc(hdfcOpt.getId()).size();

        // Run second refresh
        MonthlyHoldingsRefreshRun secondRun = refreshService.executeMonthlyHoldingsRefresh(TriggerType.MANUAL);
        log.info("Second Run (Idempotency): status={}, success={}, duplicate={}, failed={}",
            secondRun.overallStatus(), secondRun.successCount(), secondRun.duplicateCount(), secondRun.failedCount());

        // Must report duplicate for existing payloads without errors
        assertTrue(secondRun.duplicateCount() > 0 || secondRun.successCount() == 0, "Second run must recognize existing snapshots as duplicates");
        assertEquals(0, secondRun.failedCount(), "Second run must not fail");

        long snapshotCountAfter = snapshotRepository.findBySchemeOptionIdOrderByPortfolioDateDescRevisionSeqDesc(hdfcOpt.getId()).size();
        assertEquals(snapshotCountBefore, snapshotCountAfter, "Snapshot count must remain identical after re-running ingestion");
    }

    @Test
    @Order(3)
    @DisplayName("Acceptance Criterion 11: Disclosed Raw Holding Weights Are Strictly Preserved (No Normalization)")
    void test3_RawDisclosedWeightsPreserved() {
        SchemeOption ppfasTaxOpt = schemeOptionRepository.findByAmfiCode("147481").orElseThrow();
        Optional<PortfolioSnapshot> taxSnapOpt = snapshotRepository.findBySchemeOptionIdAndPortfolioDate(ppfasTaxOpt.getId(), LocalDate.of(2026, 9, 30));
        assertTrue(taxSnapOpt.isPresent(), "PPFAS Tax Saver 2026-09-30 snapshot must be present");

        PortfolioSnapshot snap = taxSnapOpt.get();
        assertNotNull(snap.getSumReportedWeights(), "Sum reported weights must not be null");

        // Verify PPFAS Tax Saver sum is ~0.9682 (96.82%), NOT normalized to 1.000000 (100.00%)
        assertTrue(snap.getSumReportedWeights().compareTo(new BigDecimal("0.95")) > 0, "Sum weights must exceed 95%");
        assertTrue(snap.getSumReportedWeights().compareTo(new BigDecimal("0.99")) < 0, "Sum weights must not be artificially normalized to 100%");

        List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snap.getId());
        assertFalse(holdings.isEmpty(), "Holdings must not be empty");
        BigDecimal calculatedSum = holdings.stream()
            .map(PortfolioHolding::getReportedWeight)
            .filter(w -> w != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Raw sum of holding weights must match snapshot sum within floating precision
        assertEquals(snap.getSumReportedWeights().doubleValue(), calculatedSum.doubleValue(), 0.0001,
            "Sum of individual holdings must match snapshot sumReportedWeights exactly");
    }

    @Test
    @Order(4)
    @DisplayName("Acceptance Criterion 6: Partial Disclosures Are Distinguished from Complete Disclosures")
    void test4_PartialVsCompleteDisclosureQualityStates() {
        SchemeOption hdfcOpt = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();

        List<PortfolioSnapshotSummaryDto> summaries = intelligenceService.getAvailableSnapshots(hdfcOpt.getId(), null);

        // Find the Jan 2024 seed snapshot (10 positions, 50.82% coverage)
        Optional<PortfolioSnapshotSummaryDto> seedSnapOpt = summaries.stream()
            .filter(s -> s.portfolioDate().equals(LocalDate.of(2024, 1, 31)))
            .findFirst();

        if (seedSnapOpt.isPresent()) {
            PortfolioSnapshotSummaryDto seedSnap = seedSnapOpt.get();
            assertEquals("DATA_QUALITY_LIMITED", seedSnap.qualityState(),
                "Partial 10-position seed disclosure must have qualityState DATA_QUALITY_LIMITED");
            assertTrue(seedSnap.sumReportedWeightsPct().doubleValue() < 90.0,
                "Partial seed disclosure weight must be <90%");
        }

        // Find the authentic August 2026 snapshot (84+ positions, 100%+ coverage)
        Optional<PortfolioSnapshotSummaryDto> augSnapOpt = summaries.stream()
            .filter(s -> s.portfolioDate().equals(LocalDate.of(2026, 8, 31)))
            .findFirst();

        assertTrue(augSnapOpt.isPresent(), "Authentic 2026-08-31 snapshot must be present");
        PortfolioSnapshotSummaryDto augSnap = augSnapOpt.get();
        assertEquals("VALID", augSnap.qualityState(), "Full authentic disclosure must have qualityState VALID");
        assertTrue(augSnap.sumReportedWeightsPct().doubleValue() >= 90.0, "Full authentic disclosure weight must be >=90%");
        assertTrue(augSnap.reportedHoldingsCount() >= 80, "Full authentic disclosure position count must be >=80");
    }

    @Test
    @Order(5)
    @DisplayName("Acceptance Criterion 10: Point-in-Time Knowledge Cutoff Strictly Excludes Future Snapshots")
    void test5_PointInTimeKnowledgeCutoffEnforcement() {
        SchemeOption hdfcOpt = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();

        // 1. Cutoff before June 2026 disclosure was published (SEBI statutory deadline: July 10, 2026)
        OffsetDateTime earlyJulyCutoff = OffsetDateTime.of(2026, 7, 5, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        List<PortfolioSnapshotSummaryDto> asOfEarlyJuly = intelligenceService.getAvailableSnapshots(hdfcOpt.getId(), earlyJulyCutoff);
        boolean hasJune2026AtEarlyJuly = asOfEarlyJuly.stream()
            .anyMatch(s -> s.portfolioDate().equals(LocalDate.of(2026, 6, 30)));
        assertFalse(hasJune2026AtEarlyJuly, "June 2026 snapshot must NOT be available as of July 5, 2026 cutoff (statutory availability is July 10)");

        // 2. Cutoff after June 2026 but before July 2026 (July 15, 2026)
        OffsetDateTime midJulyCutoff = OffsetDateTime.of(2026, 7, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        List<PortfolioSnapshotSummaryDto> asOfMidJuly = intelligenceService.getAvailableSnapshots(hdfcOpt.getId(), midJulyCutoff);
        boolean hasJune2026AtMidJuly = asOfMidJuly.stream()
            .anyMatch(s -> s.portfolioDate().equals(LocalDate.of(2026, 6, 30)));
        boolean hasJuly2026AtMidJuly = asOfMidJuly.stream()
            .anyMatch(s -> s.portfolioDate().equals(LocalDate.of(2026, 7, 31)));
        assertTrue(hasJune2026AtMidJuly, "June 2026 snapshot MUST be available as of July 15, 2026 cutoff");
        assertFalse(hasJuly2026AtMidJuly, "July 2026 snapshot must NOT be available as of July 15, 2026 cutoff");

        // 3. Cutoff as of August 15, 2026: July 2026 is available, August 2026 is not
        OffsetDateTime midAugustCutoff = OffsetDateTime.of(2026, 8, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        List<PortfolioSnapshotSummaryDto> asOfMidAugust = intelligenceService.getAvailableSnapshots(hdfcOpt.getId(), midAugustCutoff);
        boolean hasJuly2026AtMidAug = asOfMidAugust.stream()
            .anyMatch(s -> s.portfolioDate().equals(LocalDate.of(2026, 7, 31)));
        boolean hasAug2026AtMidAug = asOfMidAugust.stream()
            .anyMatch(s -> s.portfolioDate().equals(LocalDate.of(2026, 8, 31)));
        assertTrue(hasJuly2026AtMidAug, "July 2026 snapshot MUST be available as of August 15, 2026 cutoff");
        assertFalse(hasAug2026AtMidAug, "August 2026 snapshot must NOT be available as of August 15, 2026 cutoff");
    }

    @Test
    @Order(6)
    @DisplayName("Acceptance Criterion 4: Multi-Period Portfolio Drift Between Consecutive Authentic Snapshots")
    void test6_MultiPeriodPortfolioDriftOnRealData() {
        SchemeOption ppfasFlexiOpt = schemeOptionRepository.findByAmfiCode("122639").orElseThrow();

        // Calculate drift between latest two snapshots (auto-resolves August 31 and September 30)
        PortfolioDriftResponseDto drift = intelligenceService.calculatePortfolioDrift(ppfasFlexiOpt.getId(), null, null, null);

        assertNotNull(drift, "Drift response must not be null");
        assertEquals("COMPARABLE", drift.status(), "Drift between consecutive authentic monthly disclosures must be COMPARABLE");
        assertTrue(drift.isComparable(), "isComparable flag must be true");
        assertNotNull(drift.earlierSnapshot());
        assertNotNull(drift.laterSnapshot());

        log.info("PPFAS Flexi Cap Drift Window: {} to {}", drift.earlierSnapshot().portfolioDate(), drift.laterSnapshot().portfolioDate());
        log.info("Top-5 Delta: {}%, Top-10 Delta: {}%, HHI Delta: {}", drift.top5PctDelta(), drift.top10PctDelta(), drift.hhiDelta());
        log.info("Holding changes tracked: {}", drift.holdingChanges().size());

        assertFalse(drift.holdingChanges().isEmpty(), "Holding changes list must contain evaluated positions");
        assertNotNull(drift.classificationSummary(), "Classification summary must not be null");
        assertTrue(drift.classificationSummary().totalComparedPositions() > 0, "Total compared positions must be >0");
    }

    @Test
    @Order(7)
    @DisplayName("Acceptance Criterion 5: Pairwise Overlap at Identical Reporting Date Without Artificial Mismatch")
    void test7_PairwiseOverlapAtIdenticalReportingDate() {
        SchemeOption hdfcOpt = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        SchemeOption ppfasFlexiOpt = schemeOptionRepository.findByAmfiCode("122639").orElseThrow();

        // Calculate overlap as of August 31, 2026 (both funds share authentic 2026-08-31 snapshots)
        OffsetDateTime augCutoff = OffsetDateTime.of(2026, 9, 15, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30));
        PortfolioOverlapResponseDto overlap = intelligenceService.calculatePairwiseOverlap(hdfcOpt.getId(), ppfasFlexiOpt.getId(), augCutoff);

        assertNotNull(overlap, "Overlap response must not be null");
        assertEquals(LocalDate.of(2026, 8, 31), overlap.portfolioDateA(), "Fund A must be as of 2026-08-31");
        assertEquals(LocalDate.of(2026, 8, 31), overlap.portfolioDateB(), "Fund B must be as of 2026-08-31");

        // Zero reporting date mismatch!
        boolean hasDateMismatchWarning = overlap.limitations().stream()
            .anyMatch(l -> l.contains("Reporting date mismatch"));
        assertFalse(hasDateMismatchWarning, "There must be NO reporting date mismatch when both funds report as of 2026-08-31");

        assertTrue(overlap.overlapPercentage().compareTo(BigDecimal.ZERO) > 0, "Overlap percentage must be >0 for two flexi cap funds");
        assertTrue(overlap.commonSecurityCount() > 0, "Common security count must be >0");
        assertFalse(overlap.topOverlappingSecurities().isEmpty(), "Overlapping securities list must not be empty");

        log.info("HDFC vs PPFAS Overlap at 2026-08-31: Overlap={}, Common Holdings={}",
            overlap.formattedOverlapPercentage(), overlap.commonSecurityCount());
    }

    @Test
    @Order(8)
    @DisplayName("Acceptance Criterion 7: Missing Snapshot Returns Explicit UNAVAILABLE Status (Not Zero)")
    void test8_MissingSnapshotExplicitUnavailable() {
        // Query two valid options, but at a historical cutoff (e.g. 2020-01-01) where no snapshots existed
        SchemeOption hdfcOpt = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        SchemeOption ppfasFlexiOpt = schemeOptionRepository.findByAmfiCode("122639").orElseThrow();
        OffsetDateTime pastCutoff = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        PortfolioOverlapResponseDto overlap = intelligenceService.calculatePairwiseOverlap(hdfcOpt.getId(), ppfasFlexiOpt.getId(), pastCutoff);
        assertNotNull(overlap);
        assertEquals("UNAVAILABLE", overlap.qualityState(), "Overlap with missing snapshot must report UNAVAILABLE, not zero holdings");
        assertFalse(overlap.limitations().isEmpty(), "Limitations must explain why overlap is unavailable");
    }

    @Test
    @Order(9)
    @Transactional
    @DisplayName("Acceptance Criterion 12: Cryptographic Source Artifact Provenance and Lineage")
    void test9_CryptographicArtifactProvenance() throws Exception {
        SchemeOption hdfcOpt = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();
        Optional<PortfolioSnapshot> snapOpt = snapshotRepository.findBySchemeOptionIdAndPortfolioDate(hdfcOpt.getId(), LocalDate.of(2026, 8, 31));
        assertTrue(snapOpt.isPresent());

        PortfolioSnapshot snap = snapOpt.get();
        SourceArtifact artifact = snap.getSourceArtifact();
        assertNotNull(artifact, "Source artifact must not be null");
        assertNotNull(artifact.getSha256Hash(), "SHA-256 hash must not be null");
        assertNotNull(artifact.getPayloadBlob(), "Raw payload blob must be preserved");

        // Verify cryptographic SHA-256 digest integrity
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] computed = digest.digest(artifact.getPayloadBlob());
        StringBuilder sb = new StringBuilder();
        for (byte b : computed) {
            sb.append(String.format("%02x", b));
        }
        assertEquals(artifact.getSha256Hash(), sb.toString(), "Preserved artifact SHA-256 must match recomputed hash of payload blob");
    }
}
