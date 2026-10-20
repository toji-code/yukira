package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.dto.analysis.HoldingsCoverageReportDto;
import com.yukira.backend.service.HoldingsCoverageAuditService;
import com.yukira.backend.service.PilotHoldingsDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class HoldingsCoverageAuditTest {

    private static final Logger log = LoggerFactory.getLogger(HoldingsCoverageAuditTest.class);

    @Autowired
    private HoldingsCoverageAuditService auditService;

    @Autowired
    private PilotBootstrapService bootstrapService;

    @Autowired
    private PilotHoldingsDataService holdingsDataService;

    @Test
    @DisplayName("Canonical Universe Holdings Coverage Audit & HDFC Pilot Integrity Verification")
    void testHoldingsCoverageAudit() {
        // 1. Ensure master data & seed pilot holdings
        bootstrapService.bootstrapPilot();
        var seedSummary = holdingsDataService.seedControlledPilotHoldingsCohort();
        log.info("Pilot holdings seed summary: targeted={}, snapshots={}, holdings={}, securities={}",
            seedSummary.totalFundsTargeted(), seedSummary.snapshotsCreated(),
            seedSummary.totalHoldingsIngested(), seedSummary.securitiesResolved());

        LocalDate asOfDate = LocalDate.of(2024, 1, 15);
        HoldingsCoverageReportDto report = auditService.generateCoverageReport(asOfDate);

        assertNotNull(report);
        assertTrue(report.totalActiveUniverse() > 0, "Total active universe must be > 0");
        assertTrue(report.activeWithHoldings() >= 3, "Active with holdings count should be >= 3 after seeding pilot cohort");
        assertTrue(report.activeWithLatestHoldings() >= 3, "Active with latest holdings count should be >= 3");
        assertTrue(report.activeWithSecurityIdentities() >= 3, "Active with resolved ISIN identities should be >= 3");

        // Verify HDFC Pilot audit
        var hdfc = report.hdfcPilotAudit();
        assertNotNull(hdfc);
        assertEquals("118955", hdfc.amfiCode());
        assertEquals("INF179K01UT0", hdfc.isin());
        assertTrue(hdfc.positionCount() == 10 || hdfc.positionCount() == 85,
            "HDFC Flexi Cap snapshot must contain either 10 positions (seed fixture) or 85 positions (production disclosure)");
        if (hdfc.positionCount() == 85) {
            assertEquals(100.53, hdfc.totalDisclosedWeightPct(), 0.01, "Disclosed weight sum must equal 100.53%");
            assertEquals(85, hdfc.resolvedIdentityCount(), "All 85 holdings must have resolved ISIN identities");
        } else {
            assertEquals(50.82, hdfc.totalDisclosedWeightPct(), 0.01, "Disclosed weight sum must equal 50.82%");
            assertEquals(10, hdfc.resolvedIdentityCount(), "All 10 holdings must have resolved ISIN identities");
        }
        assertEquals(0, hdfc.unresolvedIdentityCount(), "Unresolved identities must be 0");
        assertEquals(0, hdfc.duplicateSecurityCount(), "Duplicate ISIN count must be 0");
        assertNotNull(hdfc.sha256Hash(), "Source artifact SHA-256 hash must not be null");

        // Verify Source Classifications
        assertFalse(report.sourceClassifications().isEmpty(), "Source classifications must not be empty");
        boolean hdfcFound = report.sourceClassifications().stream()
            .anyMatch(s -> "118955".equals(s.amfiCode()) && ("UNVERIFIED".equals(s.sourceState()) || "VERIFIED_OFFICIAL".equals(s.sourceState()) || "SOURCE_CONFIGURED".equals(s.sourceState())));
        assertTrue(hdfcFound, "HDFC 118955 must be present in source classifications");

        System.out.println("\n==========================================================================");
        System.out.println("YUKIRA CANONICAL HOLDINGS COVERAGE & PROVENANCE AUDIT REPORT");
        System.out.println("==========================================================================");
        System.out.println(String.format("As Of Date: %s", report.asOfDate()));
        System.out.println(String.format("Total Active Universe: %,d", report.totalActiveUniverse()));
        System.out.println(String.format("Active Options with NAV: %,d", report.activeWithNav()));
        System.out.println(String.format("Active Options 3Y Ready: %,d", report.active3yReady()));
        System.out.println(String.format("Active Options with Holdings: %,d", report.activeWithHoldings()));
        System.out.println(String.format("  - Production Verified Holdings: %,d", report.productionVerifiedHoldingsCount()));
        System.out.println(String.format("  - Controlled Fixture Holdings: %,d", report.controlledFixtureHoldingsCount()));
        System.out.println(String.format("Active Options with Latest Holdings: %,d", report.activeWithLatestHoldings()));
        System.out.println(String.format("Active Options with Recent Holdings: %,d", report.activeWithRecentHoldings()));
        System.out.println(String.format("Active Options with Valid Holdings: %,d", report.activeWithValidHoldings()));
        System.out.println(String.format("Active Options with Security ISIN Identities: %,d", report.activeWithSecurityIdentities()));

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("HDFC FLEXI CAP CANONICAL PILOT HOLDINGS AUDIT");
        System.out.println("--------------------------------------------------------------------------");
        System.out.println(String.format("Scheme: %s (AMFI: %s, ISIN: %s)", hdfc.schemeName(), hdfc.amfiCode(), hdfc.isin()));
        System.out.println(String.format("Snapshot Date: %s | Position Count: %d | Disclosed Weight: %.2f%%",
            hdfc.portfolioDate(), hdfc.positionCount(), hdfc.totalDisclosedWeightPct()));
        System.out.println(String.format("Resolved Identities: %d | Unresolved: %d | Duplicates: %d",
            hdfc.resolvedIdentityCount(), hdfc.unresolvedIdentityCount(), hdfc.duplicateSecurityCount()));
        System.out.println(String.format("Source Artifact ID: #%d | SHA-256: %s", hdfc.sourceArtifactId(), hdfc.sha256Hash()));
        System.out.println(String.format("Freshness State: %s | Quality State: %s | Provenance: %s",
            hdfc.freshnessState(), hdfc.qualityState(), hdfc.provenanceType()));

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("PRODUCTION HOLDINGS SOURCE CLASSIFICATIONS");
        System.out.println("--------------------------------------------------------------------------");
        for (var sc : report.sourceClassifications()) {
            System.out.println(String.format("AMFI: %-7s | Fund: %-30s | State: %-12s | Provenance: %-18s | HTTP: %s",
                sc.amfiCode(), sc.schemeName(), sc.sourceState(), sc.provenanceType(), sc.httpVerificationStatus()));
        }

        System.out.println("\n--------------------------------------------------------------------------");
        System.out.println("PERFORMANCE METRICS");
        System.out.println("--------------------------------------------------------------------------");
        var perf = report.performanceMetrics();
        System.out.println(String.format("Options Scanned: %,d | DB Query Count: %d | Duration: %d ms | Memory: %.2f MB",
            perf.optionsScanned(), perf.queryCount(), perf.durationMs(), (double) perf.peakMemoryBytes() / (1024 * 1024)));
        System.out.println("==========================================================================\n");
    }
}
