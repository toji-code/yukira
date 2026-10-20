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
class RealPpfasProductionIngestionTest {

    private static final Logger log = LoggerFactory.getLogger(RealPpfasProductionIngestionTest.class);

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
    void testRealProductionPpfasFlexiCapAcquisitionAndIngestion() {
        bootstrapService.ensurePpfasMaster();

        Optional<SchemeOption> optionOpt = schemeOptionRepository.findByAmfiCode("122639");
        assertTrue(optionOpt.isPresent(), "PPFAS Flexi Cap scheme option #122639 must be present in database");
        SchemeOption option = optionOpt.get();

        Optional<MonthlyHoldingsSourceRegistry.RegisteredFundConfig> configOpt = sourceRegistry.findByAmfiCode("122639");
        assertTrue(configOpt.isPresent(), "PPFAS Flexi Cap configuration must be registered");
        MonthlyHoldingsSourceDescriptor descriptor = configOpt.get().descriptor();
        assertNotNull(descriptor, "PPFAS Flexi Cap descriptor must not be null");

        MonthlyHoldingsAcquisitionResult result = ingestionJob.execute(descriptor);
        log.info("Live PPFAS Flexi Cap Ingestion Status: {}", result.status());
        log.info("SourceArtifact ID: #{}", result.sourceArtifactId());
        log.info("SHA-256 Hash: {}", result.sha256Hash());
        log.info("Parsed Holdings Count: {}", result.holdingsCount());

        assertTrue(
            result.status() == MonthlyHoldingsAcquisitionResult.Status.INGESTED ||
            result.status() == MonthlyHoldingsAcquisitionResult.Status.DUPLICATE
        );

        Optional<PortfolioSnapshot> snapOpt = snapshotRepository.findTopBySchemeOptionIdOrderByPortfolioDateDesc(option.getId());
        assertTrue(snapOpt.isPresent());
        PortfolioSnapshot snapshot = snapOpt.get();

        SourceArtifact artifact = snapshot.getSourceArtifact();
        assertNotNull(artifact);
        assertNotNull(artifact.getSha256Hash());
        assertFalse(artifact.getSha256Hash().startsWith("SEED_HOLDINGS_"));

        List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snapshot.getId());
        assertFalse(holdings.isEmpty());

        log.info("DATABASE TRACE 1 (PPFAS Flexi Cap):");
        log.info("  Option ID #{} (AMFI {}) | Snapshot ID #{} | Artifact ID #{} | SHA-256 {}", option.getId(), option.getAmfiCode(), snapshot.getId(), artifact.getId(), artifact.getSha256Hash());
    }

    @Test
    @Transactional
    void testRealProductionPpfasTaxSaverAcquisitionAndIngestion() {
        bootstrapService.ensurePpfasMaster();

        Optional<SchemeOption> optionOpt = schemeOptionRepository.findByAmfiCode("147481");
        assertTrue(optionOpt.isPresent(), "PPFAS Tax Saver scheme option #147481 must be present in database");
        SchemeOption option = optionOpt.get();

        Optional<MonthlyHoldingsSourceRegistry.RegisteredFundConfig> configOpt = sourceRegistry.findByAmfiCode("147481");
        assertTrue(configOpt.isPresent(), "PPFAS Tax Saver configuration must be registered");
        MonthlyHoldingsSourceDescriptor descriptor = configOpt.get().descriptor();
        assertNotNull(descriptor, "PPFAS Tax Saver descriptor must not be null");

        MonthlyHoldingsAcquisitionResult result = ingestionJob.execute(descriptor);
        log.info("Live PPFAS Tax Saver Ingestion Status: {}", result.status());
        log.info("SourceArtifact ID: #{}", result.sourceArtifactId());
        log.info("SHA-256 Hash: {}", result.sha256Hash());
        log.info("Parsed Holdings Count: {}", result.holdingsCount());

        assertTrue(
            result.status() == MonthlyHoldingsAcquisitionResult.Status.INGESTED ||
            result.status() == MonthlyHoldingsAcquisitionResult.Status.DUPLICATE
        );

        Optional<PortfolioSnapshot> snapOpt = snapshotRepository.findTopBySchemeOptionIdOrderByPortfolioDateDesc(option.getId());
        assertTrue(snapOpt.isPresent());
        PortfolioSnapshot snapshot = snapOpt.get();

        SourceArtifact artifact = snapshot.getSourceArtifact();
        assertNotNull(artifact);
        assertNotNull(artifact.getSha256Hash());
        assertFalse(artifact.getSha256Hash().startsWith("SEED_HOLDINGS_"));

        List<PortfolioHolding> holdings = holdingRepository.findByPortfolioSnapshotIdOrderByReportedWeightDesc(snapshot.getId());
        assertFalse(holdings.isEmpty());

        log.info("DATABASE TRACE 2 (PPFAS Tax Saver):");
        log.info("  Option ID #{} (AMFI {}) | Snapshot ID #{} | Artifact ID #{} | SHA-256 {}", option.getId(), option.getAmfiCode(), snapshot.getId(), artifact.getId(), artifact.getSha256Hash());

        HoldingsCoverageReportDto report = coverageAuditService.generateCoverageReport(LocalDate.of(2026, 9, 30));
        log.info("Coverage Audit Production Verified Holdings Count: {}", report.productionVerifiedHoldingsCount());
        assertTrue(report.productionVerifiedHoldingsCount() >= 2, "productionVerifiedHoldingsCount must be >= 2 when 2 real sources are ingested");
    }
}
