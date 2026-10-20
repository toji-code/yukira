package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.PortfolioHolding;
import com.yukira.backend.domain.entity.PortfolioSnapshot;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.dto.portfolio.PortfolioDriftResponseDto;
import com.yukira.backend.dto.portfolio.PortfolioSnapshotSummaryDto;
import com.yukira.backend.repository.SourceArtifactRepository;
import com.yukira.backend.repository.PortfolioHoldingRepository;
import com.yukira.backend.repository.PortfolioSnapshotRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.PortfolioIntelligenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HistoricalHoldingsAcquisitionTest {

    private static final Logger log = LoggerFactory.getLogger(HistoricalHoldingsAcquisitionTest.class);

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
    @Transactional
    @DisplayName("Acquire, Ingest and Validate Real Multi-Period Historical Disclosures for 3 Pilot Schemes")
    void testRealMultiPeriodHistoricalAcquisitionAndDrift() throws Exception {
        bootstrapService.ensurePpfasMaster();
        bootstrapService.ensureCanonicalPilotMaster();

        // 1. Execute Batch Refresh Across All Configured Historical Descriptors
        MonthlyHoldingsRefreshRun refreshRun = refreshService.executeMonthlyHoldingsRefresh();
        log.info("Historical Refresh Run Status: {}", refreshRun.overallStatus());
        log.info("Total Successes: {}, Duplicates: {}, Failed: {}", refreshRun.successCount(), refreshRun.duplicateCount(), refreshRun.failedCount());
        for (String auditLog : refreshRun.auditLogs()) {
            log.info("  {}", auditLog);
        }

        assertTrue(
            "SUCCESS".equals(refreshRun.overallStatus()) || "PARTIAL_SUCCESS".equals(refreshRun.overallStatus()),
            "Batch historical refresh run must complete with SUCCESS or PARTIAL_SUCCESS"
        );

        List<String> pilotAmfiCodes = List.of("122639", "147481", "118955");

        System.out.println("==========================================================================================================");
        System.out.println("REAL MULTI-PERIOD HISTORICAL DISCLOSURE & DRIFT AUDIT TABLE");
        System.out.println("==========================================================================================================");

        for (String amfiCode : pilotAmfiCodes) {
            Optional<SchemeOption> optionOpt = schemeOptionRepository.findByAmfiCode(amfiCode);
            assertTrue(optionOpt.isPresent(), "Scheme option AMFI #" + amfiCode + " must exist in database");
            SchemeOption option = optionOpt.get();

            List<PortfolioSnapshotSummaryDto> availableSnapshots = intelligenceService.getAvailableSnapshots(option.getId(), null);
            log.info("Scheme AMFI #{}: {} distinct historical snapshot dates available", amfiCode, availableSnapshots.size());
            assertTrue(availableSnapshots.size() >= 2, "Scheme AMFI #" + amfiCode + " must have at least 2 distinct historical snapshot dates for drift validation");

            System.out.println("SCHEME: " + option.getPlan().getScheme().getName() + " (AMFI #" + amfiCode + " | ISIN " + option.getIsin() + ")");
            System.out.println("Distinct Eligible Observations: " + availableSnapshots.size());

            for (PortfolioSnapshotSummaryDto summary : availableSnapshots) {
                PortfolioSnapshot snap = snapshotRepository.findById(summary.snapshotId()).orElseThrow();
                SourceArtifact artifact = sourceArtifactRepository.findById(snap.getSourceArtifact().getId()).orElseThrow();
                assertNotNull(artifact);
                assertNotNull(artifact.getSha256Hash());

                boolean verified = true;
                if (artifact.getPayloadBlob() != null) {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] recalculated = digest.digest(artifact.getPayloadBlob());
                    StringBuilder sb = new StringBuilder();
                    for (byte b : recalculated) {
                        sb.append(String.format("%02x", b));
                    }
                    String recalculatedHash = sb.toString();
                    verified = recalculatedHash.equals(artifact.getSha256Hash());
                    assertTrue(verified, "SHA-256 hash must be verified against stored payload blob");
                }

                List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snap.getId());
                BigDecimal sumW = holdings.stream().map(PortfolioHolding::getReportedWeight).filter(w -> w != null).reduce(BigDecimal.ZERO, BigDecimal::add);

                System.out.printf("  - Date: %s | Snapshot #%d | Artifact #%d | SHA-256: %s | Verified: %s | Holdings: %d | Sum Weight: %.2f%%\n",
                    snap.getPortfolioDate(), snap.getId(), artifact.getId(), artifact.getSha256Hash().substring(0, 16) + "...",
                    verified ? "YES" : "NO", holdings.size(), sumW.doubleValue() * 100);

                assertFalse(holdings.isEmpty(), "Parsed holdings count must be > 0");
            }

            // 2. Validate Real Multi-Period Portfolio Drift
            PortfolioDriftResponseDto drift = intelligenceService.calculatePortfolioDrift(option.getId(), null, null, null);
            assertEquals("COMPARABLE", drift.status(), "Multi-period drift must report COMPARABLE for scheme AMFI #" + amfiCode);
            assertTrue(drift.isComparable(), "isComparable flag must be true");

            System.out.println("  MULTI-PERIOD DRIFT RESULT:");
            System.out.printf("    Comparison Window: %s to %s\n", drift.earlierSnapshot().portfolioDate(), drift.laterSnapshot().portfolioDate());
            System.out.printf("    Concentration Deltas: Top-5 Δ %s%%, Top-10 Δ %s%%, HHI Δ %s\n",
                drift.top5PctDelta(), drift.top10PctDelta(), drift.hhiDelta());
            System.out.printf("    Positions Tracked: %d drift items evaluated\n", drift.holdingChanges().size());

            var summary = drift.classificationSummary();
            assertNotNull(summary, "Classification summary must not be null");
            System.out.printf("    Drift Breakdown: Total: %d | Newly Reported: %d | No Longer Reported: %d | Weight Increased: %d | Weight Decreased: %d | Unchanged: %d | Unclassified: %d\n",
                summary.totalComparedPositions(), summary.newlyReportedCount(), summary.noLongerReportedCount(),
                summary.weightIncreasedCount(), summary.weightDecreasedCount(), summary.unchangedCount(), summary.unclassifiedCount());

            assertEquals(summary.totalComparedPositions(),
                summary.newlyReportedCount() + summary.noLongerReportedCount() + summary.weightIncreasedCount() + summary.weightDecreasedCount() + summary.unchangedCount() + summary.unclassifiedCount(),
                "Reconciliation invariant: Total compared positions must equal sum of mutually exclusive categories");

            System.out.println("    DETAILED DRIFT POSITIONS:");
            for (var item : drift.holdingChanges()) {
                System.out.printf("      %-35s | Earlier: %7s | Later: %7s | Delta: %7s | Change: %s\n",
                    item.securityName().length() > 35 ? item.securityName().substring(0, 32) + "..." : item.securityName(),
                    item.earlierWeightPctFormatted() != null ? item.earlierWeightPctFormatted() : "N/A",
                    item.laterWeightPctFormatted() != null ? item.laterWeightPctFormatted() : "N/A",
                    item.weightDeltaPctFormatted(), item.changeType());
            }
            System.out.println("----------------------------------------------------------------------------------------------------------");
        }
    }
}
