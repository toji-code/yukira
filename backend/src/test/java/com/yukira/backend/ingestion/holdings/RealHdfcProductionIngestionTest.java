package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.PortfolioHolding;
import com.yukira.backend.domain.entity.PortfolioSnapshot;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.dto.analysis.HoldingsCoverageReportDto;
import com.yukira.backend.repository.PortfolioHoldingRepository;
import com.yukira.backend.repository.PortfolioSnapshotRepository;
import com.yukira.backend.repository.SchemeOptionRepository;
import com.yukira.backend.service.HoldingsCoverageAuditService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RealHdfcProductionIngestionTest {

    private static final Logger log = LoggerFactory.getLogger(RealHdfcProductionIngestionTest.class);

    @Autowired
    private PilotBootstrapService bootstrapService;

    @Autowired
    private MonthlyHoldingsSourceRegistry sourceRegistry;

    @Autowired
    private MonthlyHoldingsIngestionJob ingestionJob;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    @Autowired
    private PortfolioSnapshotRepository snapshotRepository;

    @Autowired
    private PortfolioHoldingRepository holdingRepository;

    @Autowired
    private HoldingsCoverageAuditService coverageAuditService;

    @Test
    @Transactional
    void testRealProductionHdfcFlexiCapAcquisitionAndIngestion() {
        bootstrapService.ensureCanonicalPilotMaster();
        bootstrapService.ensurePpfasMaster();

        // 1. Verify scheme option master
        Optional<SchemeOption> hdfcOptionOpt = schemeOptionRepository.findByAmfiCode("118955");
        assertTrue(hdfcOptionOpt.isPresent(), "HDFC Flexi Cap scheme option #118955 must exist in database");
        SchemeOption hdfcOption = hdfcOptionOpt.get();
        assertEquals("INF179K01UT0", hdfcOption.getIsin(), "Canonical HDFC Flexi Cap ISIN must match INF179K01UT0");

        // 2. Ingest PPFAS sources first to establish 2 baseline PPFAS production sources
        Optional<MonthlyHoldingsSourceRegistry.RegisteredFundConfig> ppfasFlexiConfig = sourceRegistry.findByAmfiCode("122639");
        if (ppfasFlexiConfig.isPresent() && ppfasFlexiConfig.get().descriptor() != null) {
            ingestionJob.execute(ppfasFlexiConfig.get().descriptor());
        }
        Optional<MonthlyHoldingsSourceRegistry.RegisteredFundConfig> ppfasTaxConfig = sourceRegistry.findByAmfiCode("147481");
        if (ppfasTaxConfig.isPresent() && ppfasTaxConfig.get().descriptor() != null) {
            ingestionJob.execute(ppfasTaxConfig.get().descriptor());
        }

        // 3. Acquire & Ingest HDFC Flexi Cap official disclosure
        Optional<MonthlyHoldingsSourceRegistry.RegisteredFundConfig> hdfcConfigOpt = sourceRegistry.findByAmfiCode("118955");
        assertTrue(hdfcConfigOpt.isPresent(), "HDFC Flexi Cap configuration must be registered");
        MonthlyHoldingsSourceDescriptor hdfcDescriptor = hdfcConfigOpt.get().descriptor();
        assertNotNull(hdfcDescriptor, "HDFC Flexi Cap descriptor must not be null");

        MonthlyHoldingsAcquisitionResult result = ingestionJob.execute(hdfcDescriptor);
        log.info("Live HDFC Flexi Cap Ingestion Status: {}", result.status());
        log.info("SourceArtifact ID: #{}", result.sourceArtifactId());
        log.info("SHA-256 Hash: {}", result.sha256Hash());
        log.info("Parsed Holdings Count: {}", result.holdingsCount());

        assertTrue(
            result.status() == MonthlyHoldingsAcquisitionResult.Status.INGESTED ||
            result.status() == MonthlyHoldingsAcquisitionResult.Status.DUPLICATE,
            "Ingestion must succeed with INGESTED or DUPLICATE"
        );

        // 4. Verify Latest Portfolio Snapshot & Artifact
        Optional<PortfolioSnapshot> snapOpt = snapshotRepository.findTopBySchemeOptionIdOrderByPortfolioDateDesc(hdfcOption.getId());
        assertTrue(snapOpt.isPresent(), "Latest portfolio snapshot must be present for HDFC Flexi Cap");
        PortfolioSnapshot snapshot = snapOpt.get();
        assertEquals(LocalDate.of(2026, 9, 30), snapshot.getPortfolioDate(), "Snapshot portfolio date must be 2026-09-30");

        SourceArtifact artifact = snapshot.getSourceArtifact();
        assertNotNull(artifact, "Snapshot must link to a persisted SourceArtifact");
        assertNotNull(artifact.getSha256Hash(), "SourceArtifact SHA-256 hash must not be null");
        assertNotNull(artifact.getPayloadBlob(), "SourceArtifact payload_blob must contain actual raw bytes");
        assertTrue(artifact.getPayloadBlob().length > 500000, "HDFC raw payload size must exceed 500 KB");

        String computedSha = AuthoritativeHoldingsSourceFetcher.computeSha256(artifact.getPayloadBlob());
        assertEquals(artifact.getSha256Hash(), computedSha, "Stored SHA-256 hash must match calculated hash of payload_blob");
        assertFalse(artifact.getSha256Hash().startsWith("SEED_HOLDINGS_"), "Production artifact SHA must not be synthetic seed");

        // 5. Verify Holdings & Resolution
        List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snapshot.getId());
        assertFalse(holdings.isEmpty(), "Persisted holdings list must not be empty");
        assertTrue(holdings.size() >= 80, "Parsed HDFC holdings count must be >= 80");

        long resolvedCount = holdings.stream().filter(h -> h.getSecurity() != null).count();
        log.info("HDFC Holdings Total: {}, Resolved Securities: {}", holdings.size(), resolvedCount);
        assertTrue(resolvedCount >= 80, "At least 80 holdings must have resolved security master records");

        log.info("DATABASE TRACE (HDFC Flexi Cap Production):");
        log.info("  Option ID #{} (AMFI {}) | Snapshot ID #{} | Artifact ID #{} | SHA-256 {}", hdfcOption.getId(), hdfcOption.getAmfiCode(), snapshot.getId(), artifact.getId(), artifact.getSha256Hash());

        java.math.BigDecimal sumWeights = holdings.stream()
            .map(PortfolioHolding::getReportedWeight)
            .filter(java.util.Objects::nonNull)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        log.info("  HDFC Snapshot Calculated Sum of Persisted Weights: {}", sumWeights);
        assertEquals(new java.math.BigDecimal("1.005300"), sumWeights, "Full-precision raw weight sum must match exact disclosed sum 1.005300 (100.53%)");

        // 6. Verify Fixture Isolation (Snapshot #2 remains CONTROLLED_FIXTURE)
        List<PortfolioSnapshot> allHdfcSnapshots = snapshotRepository.findAll().stream()
            .filter(s -> s.getSchemeOption().getId().equals(hdfcOption.getId()))
            .toList();
        assertTrue(allHdfcSnapshots.size() >= 2, "HDFC scheme option should now have at least 2 snapshots (1 fixture + 1 production)");

        Optional<PortfolioSnapshot> fixtureSnapOpt = allHdfcSnapshots.stream()
            .filter(s -> s.getPortfolioDate().equals(LocalDate.of(2024, 1, 31)))
            .findFirst();
        assertTrue(fixtureSnapOpt.isPresent(), "Old 2024-01-31 HDFC fixture snapshot must remain preserved");
        PortfolioSnapshot fixtureSnap = fixtureSnapOpt.get();
        assertTrue(fixtureSnap.getSourceArtifact() == null || fixtureSnap.getSourceArtifact().getPayloadBlob() == null, "Fixture artifact payload_blob must remain null/synthetic");

        // 7. Execute Provenance & Coverage Audit
        HoldingsCoverageReportDto report = coverageAuditService.generateCoverageReport(LocalDate.of(2026, 9, 30));
        log.info("Coverage Audit Production Verified Holdings Count: {}", report.productionVerifiedHoldingsCount());
        assertEquals(3, report.productionVerifiedHoldingsCount(), "Total production verified holdings count must be EXACTLY 3 (PPFAS Flexi, PPFAS Tax, HDFC Flexi)");
    }
}
