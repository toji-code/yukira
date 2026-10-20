package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.*;
import com.yukira.backend.dto.portfolio.PortfolioDriftResponseDto;
import com.yukira.backend.dto.portfolio.PortfolioSnapshotSummaryDto;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.PortfolioIntelligenceService;
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
class PortfolioDriftAndHistoryTest {

    @Autowired
    private PilotBootstrapService bootstrapService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private PortfolioSnapshotRepository snapshotRepository;

    @Autowired
    private PortfolioHoldingRepository holdingRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private SecurityIdentifierRepository securityIdentifierRepository;

    @Autowired
    private SourceArtifactRepository sourceArtifactRepository;


    @Autowired
    private DataSourceRepository dataSourceRepository;

    @Autowired
    private PortfolioIntelligenceService intelligenceService;

    private SchemeOption testOption1;
    private SchemeOption testOption2;
    private DataSource testSource;

    @BeforeEach
    void setUp() {
        bootstrapService.ensurePpfasMaster();
        bootstrapService.ensureCanonicalPilotMaster();

        testOption1 = schemeOptionRepository.findByAmfiCode("122639").orElseThrow();
        testOption2 = schemeOptionRepository.findByAmfiCode("118955").orElseThrow();

        testSource = dataSourceRepository.findByCode("SEBI_AMC_DISCLOSURE")
            .orElseGet(() -> dataSourceRepository.save(new DataSource("SEBI_AMC_DISCLOSURE", "SEBI AMC Disclosure Feed", "https://www.amfiindia.com")));
    }

    @Test
    @DisplayName("Single-snapshot scheme returns INSUFFICIENT_HISTORY status for drift analysis without fabrication")
    void testSingleSnapshotInsufficientHistory() {
        // Option 10151 (AMFI 147481) has 1 snapshot in DB
        SchemeOption elssOption = schemeOptionRepository.findByAmfiCode("147481").orElseThrow();

        List<PortfolioSnapshotSummaryDto> snapshots = intelligenceService.getAvailableSnapshots(elssOption.getId(), null);
        assertNotNull(snapshots, "Snapshots list must not be null");

        PortfolioDriftResponseDto drift = intelligenceService.calculatePortfolioDrift(elssOption.getId(), null, null, null);
        assertNotNull(drift, "Drift response must not be null");

        if (snapshots.size() < 2) {
            assertEquals("INSUFFICIENT_HISTORY", drift.status(), "Status must be INSUFFICIENT_HISTORY when <2 snapshots exist");
            assertFalse(drift.isComparable(), "isComparable must be false for single snapshot");
            assertTrue(drift.statusReason().contains("Only 1 historical snapshot") || drift.statusReason().contains("No holdings snapshots"),
                "Reason must communicate snapshot count explicitly");
        }
    }

    @Test
    @DisplayName("Cross-scheme snapshot comparison is strictly rejected")
    void testCrossSchemeComparisonRejection() {
        // Create 1 snapshot for option 1 and 1 for option 2
        PortfolioSnapshot snap1 = createTestSnapshot(testOption1, LocalDate.of(2025, 8, 31), OffsetDateTime.now());
        PortfolioSnapshot snap2 = createTestSnapshot(testOption2, LocalDate.of(2025, 9, 30), OffsetDateTime.now());

        PortfolioDriftResponseDto drift = intelligenceService.calculatePortfolioDrift(testOption1.getId(), snap1.getId(), snap2.getId(), null);
        assertNotNull(drift);
        assertEquals("INVALID_SELECTION", drift.status(), "Cross-scheme snapshot comparison must be rejected with INVALID_SELECTION");
        assertFalse(drift.isComparable(), "Cross-scheme comparison must not be comparable");
    }

    @Test
    @DisplayName("PIT knowledge cutoff excludes snapshots available after cutoff timestamp")
    void testPitKnowledgeCutoffEnforcement() {
        OffsetDateTime pastCutoff = OffsetDateTime.of(2025, 8, 15, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime futureAvailability = OffsetDateTime.of(2025, 9, 5, 12, 0, 0, 0, ZoneOffset.UTC);

        PortfolioSnapshot snapFuture = createTestSnapshot(testOption1, LocalDate.of(2025, 8, 31), futureAvailability);

        List<PortfolioSnapshotSummaryDto> snapshotsAsOfPast = intelligenceService.getAvailableSnapshots(testOption1.getId(), pastCutoff);

        boolean containsFuture = snapshotsAsOfPast.stream()
            .anyMatch(s -> s.snapshotId().equals(snapFuture.getId()));

        assertFalse(containsFuture, "Snapshot with availability time after knowledge cutoff must be excluded by PIT query contract");
    }

    @Test
    @DisplayName("Multi-period portfolio drift computes deterministic position deltas, top-10 share delta, and HHI delta")
    void testMultiPeriodPortfolioDriftCalculation() {
        // Create 2 distinct historical snapshots for testOption1
        LocalDate date1 = LocalDate.of(2025, 7, 31);
        LocalDate date2 = LocalDate.of(2025, 8, 31);
        OffsetDateTime avail = OffsetDateTime.of(2025, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC);

        PortfolioSnapshot snap1 = createTestSnapshot(testOption1, date1, avail);
        PortfolioSnapshot snap2 = createTestSnapshot(testOption2.getId().equals(testOption1.getId()) ? testOption1 : testOption1, date2, avail);

        Security secHdfc = createTestSecurity("HDFC Bank Ltd.", "INE040A01034", "Financial Services", "Equity");
        Security secInfy = createTestSecurity("Infosys Ltd.", "INE009A01021", "Information Technology", "Equity");
        Security secTcs = createTestSecurity("TCS Ltd.", "INE467B01029", "Information Technology", "Equity");

        // Snap 1: HDFC Bank 10.0%, Infosys 5.0%
        addHolding(snap1, secHdfc, new BigDecimal("0.100000"), 1);
        addHolding(snap1, secInfy, new BigDecimal("0.050000"), 2);

        // Snap 2: HDFC Bank 12.0% (WEIGHT_INCREASED), TCS 4.0% (NEWLY_REPORTED), Infosys absent (NO_LONGER_REPORTED)
        addHolding(snap2, secHdfc, new BigDecimal("0.120000"), 1);
        addHolding(snap2, secTcs, new BigDecimal("0.040000"), 2);

        PortfolioDriftResponseDto drift = intelligenceService.calculatePortfolioDrift(testOption1.getId(), snap1.getId(), snap2.getId(), avail);

        assertNotNull(drift);
        assertEquals("COMPARABLE", drift.status(), "Multi-period drift between 2 valid snapshots of same scheme must be COMPARABLE");
        assertTrue(drift.isComparable(), "isComparable must be true");
        assertNotNull(drift.earlierSnapshot());
        assertNotNull(drift.laterSnapshot());

        assertEquals(date1, drift.earlierSnapshot().portfolioDate());
        assertEquals(date2, drift.laterSnapshot().portfolioDate());

        // Check Top-10 Delta: Snap 1 Top-10 = 15.00%, Snap 2 Top-10 = 16.00% -> Delta = +1.00%
        assertEquals(new BigDecimal("1.00"), drift.top10PctDelta());

        // Check Holding Changes
        List<PortfolioDriftResponseDto.HoldingDriftItemDto> changes = drift.holdingChanges();
        assertFalse(changes.isEmpty());

        var summary = drift.classificationSummary();
        assertNotNull(summary, "Classification summary must not be null");
        assertEquals(3, summary.totalComparedPositions());
        assertEquals(1, summary.newlyReportedCount());
        assertEquals(1, summary.noLongerReportedCount());
        assertEquals(1, summary.weightIncreasedCount());
        assertEquals(0, summary.weightDecreasedCount());
        assertEquals(0, summary.unchangedCount());
        assertEquals(0, summary.unclassifiedCount());
        assertEquals(summary.totalComparedPositions(),
            summary.newlyReportedCount() + summary.noLongerReportedCount() + summary.weightIncreasedCount() + summary.weightDecreasedCount() + summary.unchangedCount() + summary.unclassifiedCount(),
            "Total compared positions must equal sum of mutually exclusive category counts");

        var hdfcItem = changes.stream().filter(c -> "INE040A01034".equals(c.isin())).findFirst().orElseThrow();
        assertEquals("WEIGHT_INCREASED", hdfcItem.changeType());
        assertEquals(new BigDecimal("2.00"), hdfcItem.weightDeltaPct());

        var tcsItem = changes.stream().filter(c -> "INE467B01029".equals(c.isin())).findFirst().orElseThrow();
        assertEquals("NEWLY_REPORTED", tcsItem.changeType());
        assertEquals(new BigDecimal("4.00"), tcsItem.weightDeltaPct());

        var infyItem = changes.stream().filter(c -> "INE009A01021".equals(c.isin())).findFirst().orElseThrow();
        assertEquals("NO_LONGER_REPORTED", infyItem.changeType());
        assertEquals(new BigDecimal("-5.00"), infyItem.weightDeltaPct());
    }

    private PortfolioSnapshot createTestSnapshot(SchemeOption option, LocalDate date, OffsetDateTime availability) {
        SourceArtifact artifact = sourceArtifactRepository.save(
            new SourceArtifact(testSource, availability, "PORTFOLIO_DISCLOSURE_XLSX", "SHA256_TEST_" + System.nanoTime(), 5000L)
        );

        PortfolioSnapshot snap = new PortfolioSnapshot();
        snap.setSchemeOption(option);
        snap.setPortfolioDate(date);
        int nextSeq = snapshotRepository.findTopBySchemeOptionIdAndPortfolioDateOrderByRevisionSeqDesc(option.getId(), date)
            .map(s -> s.getRevisionSeq() + 1).orElse(1);
        snap.setRevisionSeq(nextSeq);
        snap.setLatestRevision(true);
        snap.setAvailabilityTime(availability);
        snap.setReportedTotalNetAssets(new BigDecimal("1000.00"));
        snap.setReportedHoldingsCount(2);
        snap.setSumReportedWeights(new BigDecimal("0.150000"));
        snap.setSourceArtifact(artifact);
        return snapshotRepository.save(snap);
    }

    private Security createTestSecurity(String name, String isin, String sector, String assetClass) {
        if (isin != null && !isin.isBlank()) {
            var existing = securityIdentifierRepository.findByIdTypeAndIdValue("ISIN", isin);
            if (existing.isPresent()) {
                return existing.get().getSecurity();
            }
        }

        Security sec = new Security();
        sec.setCanonicalName(name);
        sec.setSector(sector);
        sec.setAssetClass(assetClass);
        sec.setInstrumentType("EQUITY_SHARES");
        sec.setIssuerName(name);
        sec.setStatus("ACTIVE");
        Security savedSec = securityRepository.save(sec);

        if (isin != null && !isin.isBlank()) {
            SecurityIdentifier si = new SecurityIdentifier(savedSec, "ISIN", isin, LocalDate.of(2000, 1, 1));
            securityIdentifierRepository.save(si);
        }
        return savedSec;
    }



    private void addHolding(PortfolioSnapshot snap, Security sec, BigDecimal weight, int rank) {
        PortfolioHolding h = new PortfolioHolding();
        h.setPortfolioSnapshot(snap);
        h.setSecurity(sec);
        h.setReportedWeight(weight);
        h.setHoldingRank(rank);
        holdingRepository.save(h);
    }
}
