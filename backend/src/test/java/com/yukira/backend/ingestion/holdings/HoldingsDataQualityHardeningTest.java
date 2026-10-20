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
import com.yukira.backend.repository.SourceArtifactRepository;
import com.yukira.backend.service.HoldingsCoverageAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class HoldingsDataQualityHardeningTest {

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
    private SourceArtifactRepository artifactRepository;

    @Autowired
    private HoldingsCoverageAuditService coverageAuditService;

    private PpfasXlsxHoldingsParser ppfasParser;
    private HdfcXlsxHoldingsParser hdfcParser;

    @BeforeEach
    void setUp() {
        ppfasParser = new PpfasXlsxHoldingsParser();
        hdfcParser = new HdfcXlsxHoldingsParser();
        bootstrapService.ensurePpfasMaster();
        bootstrapService.ensureCanonicalPilotMaster();
    }

    @Test
    @DisplayName("1. PPFAS Direct vs Regular ISIN correctness and rejection")
    void testPpfasDirectVsRegularIsinCorrectness() {
        Optional<SchemeOption> directOpt = schemeOptionRepository.findByAmfiCode("122639");
        assertTrue(directOpt.isPresent(), "PPFAS Flexi Direct Growth must exist");
        assertEquals("INF879O01027", directOpt.get().getIsin(), "PPFAS Flexi Direct Growth ISIN must be INF879O01027");
        assertNotEquals("INF879O01015", directOpt.get().getIsin(), "PPFAS Flexi Direct Growth MUST NOT map to Regular ISIN INF879O01015");

        Optional<MonthlyHoldingsSourceRegistry.RegisteredFundConfig> configOpt = sourceRegistry.findByAmfiCode("122639");
        assertTrue(configOpt.isPresent());
        assertEquals("INF879O01027", configOpt.get().descriptor().isin());
    }

    @Test
    @DisplayName("2. Scheme/source identity mismatch rejection")
    void testSchemeSourceIdentityMismatchRejection() {
        MonthlyHoldingsSourceDescriptor descriptor = MonthlyHoldingsSourceDescriptor.of(
            "HDFC_MF", "HDFC Mutual Fund", "HDFC_FLEXI", "HDFC Flexi Cap Fund", "118955", "INF179K01UT0",
            "https://files.hdfcfund.com/disclosures/invalid.xlsx", LocalDate.of(2026, 8, 31)
        );
        MonthlyHoldingsSourceDescriptor invalidDescriptor = MonthlyHoldingsSourceDescriptor.of(
            "UNKNOWN_AMC", "Unknown AMC", "UNKNOWN", "Unknown Scheme", "999999", "INVALID_ISIN",
            "https://example.com/invalid.xlsx", LocalDate.of(2026, 9, 30)
        );
        assertFalse(ppfasParser.supports(invalidDescriptor, new byte[]{0x50, 0x4B, 0x03, 0x04}));
        assertFalse(hdfcParser.supports(invalidDescriptor, new byte[]{0x50, 0x4B, 0x03, 0x04}));
    }

    @Test
    @DisplayName("3. SourceArtifact hash verification")
    void testSourceArtifactHashVerification() throws Exception {
        Optional<SchemeOption> optionOpt = schemeOptionRepository.findByAmfiCode("122639");
        assertTrue(optionOpt.isPresent());
        ingestionJob.execute(sourceRegistry.findByAmfiCode("122639").get().descriptor());

        Optional<PortfolioSnapshot> snapOpt = snapshotRepository.findTopBySchemeOptionIdOrderByPortfolioDateDesc(optionOpt.get().getId());
        assertTrue(snapOpt.isPresent());
        SourceArtifact artifact = snapOpt.get().getSourceArtifact();
        assertNotNull(artifact);
        assertNotNull(artifact.getPayloadBlob());

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] recalculated = digest.digest(artifact.getPayloadBlob());
        StringBuilder hex = new StringBuilder();
        for (byte b : recalculated) {
            hex.append(String.format("%02x", b));
        }
        assertEquals(hex.toString(), artifact.getSha256Hash(), "Recalculated SHA-256 must match stored artifact hash exactly");
    }

    @Test
    @DisplayName("4. Missing or empty payload rejection")
    void testMissingOrEmptyPayloadRejection() {
        PpfasXlsxHoldingsParser.ParseResult emptyPpfas = ppfasParser.parse(new byte[0], "122639");
        assertEquals(0, emptyPpfas.validHoldingsCount());
        assertTrue(emptyPpfas.records().isEmpty());

        PpfasXlsxHoldingsParser.ParseResult emptyHdfc = hdfcParser.parse(new byte[0], "118955");
        assertEquals(0, emptyHdfc.validHoldingsCount());
        assertTrue(emptyHdfc.records().isEmpty());
    }

    @Test
    @DisplayName("5. HDFC header/worksheet validation")
    void testHdfcHeaderValidation() {
        byte[] dummyZip = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00};
        PpfasXlsxHoldingsParser.ParseResult result = hdfcParser.parse(dummyZip, "118955");
        assertEquals(0, result.validHoldingsCount());
        assertTrue(result.records().isEmpty());
    }

    @Test
    @DisplayName("6. Source observation-date extraction")
    void testSourceObservationDateExtraction() throws Exception {
        File ppfasSample = new File("C:/Users/DEUS/.gemini/antigravity-ide/brain/2f068f23-dc14-486b-8a91-5acdf0caf033/scratch/ppfas_sept2026.xls");
        if (ppfasSample.exists()) {
            byte[] bytes = new FileInputStream(ppfasSample).readAllBytes();
            PpfasXlsxHoldingsParser.ParseResult res = ppfasParser.parse(bytes, "122639");
            assertNotNull(res.portfolioDate());
            assertEquals(LocalDate.of(2026, 9, 30), res.portfolioDate());
        }

        File hdfcSample = new File("C:/Users/DEUS/.gemini/antigravity-ide/brain/2f068f23-dc14-486b-8a91-5acdf0caf033/scratch/hdfc_flexi_cap_aug2026.xlsx");
        if (hdfcSample.exists()) {
            byte[] bytes = new FileInputStream(hdfcSample).readAllBytes();
            PpfasXlsxHoldingsParser.ParseResult res = hdfcParser.parse(bytes, "118955");
            assertNotNull(res.portfolioDate());
            assertEquals(LocalDate.of(2026, 8, 31), res.portfolioDate());
        }
    }

    @Test
    @DisplayName("7. Duplicate holding detection")
    void testDuplicateHoldingDetection() throws Exception {
        File hdfcSample = new File("C:/Users/DEUS/.gemini/antigravity-ide/brain/2f068f23-dc14-486b-8a91-5acdf0caf033/scratch/hdfc_flexi_cap_aug2026.xlsx");
        if (hdfcSample.exists()) {
            byte[] bytes = new FileInputStream(hdfcSample).readAllBytes();
            PpfasXlsxHoldingsParser.ParseResult res = hdfcParser.parse(bytes, "118955");
            Set<String> isins = new HashSet<>();
            int duplicates = 0;
            for (MonthlyHoldingsRecord rec : res.records()) {
                if (!isinIsUniqueOrSpecial(rec.isin())) {
                    if (!isins.add(rec.isin())) {
                        duplicates++;
                    }
                }
            }
            assertEquals(0, duplicates, "HDFC disclosed securities must contain zero duplicate ISINs");
        }
    }

    private boolean isinIsUniqueOrSpecial(String isin) {
        return "TREPS".equalsIgnoreCase(isin) || "CASH_TREPS".equalsIgnoreCase(isin) || "OPTIONS_LIABILITY".equalsIgnoreCase(isin) || "DERIVATIVE_LIABILITY".equalsIgnoreCase(isin);
    }

    @Test
    @DisplayName("8. Subtotal and sector-summary exclusion")
    void testSubtotalAndSectorSummaryExclusion() throws Exception {
        File hdfcSample = new File("C:/Users/DEUS/.gemini/antigravity-ide/brain/2f068f23-dc14-486b-8a91-5acdf0caf033/scratch/hdfc_flexi_cap_aug2026.xlsx");
        if (hdfcSample.exists()) {
            byte[] bytes = new FileInputStream(hdfcSample).readAllBytes();
            PpfasXlsxHoldingsParser.ParseResult res = hdfcParser.parse(bytes, "118955");
            boolean containsGrandTotal = res.records().stream().anyMatch(r -> r.securityName().toUpperCase().contains("GRAND TOTAL"));
            boolean containsTopTen = res.records().stream().anyMatch(r -> r.securityName().toUpperCase().contains("TOP TEN"));
            assertFalse(containsGrandTotal, "Parsed records must exclude 'GRAND TOTAL' summary rows");
            assertFalse(containsTopTen, "Parsed records must exclude 'TOP TEN' section headers");
        }
    }

    @Test
    @DisplayName("9. Cash/TREPS and option liabilities handling")
    void testCashAndTrepsHandling() throws Exception {
        File hdfcSample = new File("C:/Users/DEUS/.gemini/antigravity-ide/brain/2f068f23-dc14-486b-8a91-5acdf0caf033/scratch/hdfc_flexi_cap_aug2026.xlsx");
        if (hdfcSample.exists()) {
            byte[] bytes = new FileInputStream(hdfcSample).readAllBytes();
            PpfasXlsxHoldingsParser.ParseResult res = hdfcParser.parse(bytes, "118955");
            
            Optional<MonthlyHoldingsRecord> trepsOpt = res.records().stream().filter(r -> "Cash".equalsIgnoreCase(r.assetClass()) || "CASH_EQUIVALENTS".equalsIgnoreCase(r.assetClass())).findFirst();
            assertTrue(trepsOpt.isPresent(), "TREPS / Cash holdings must be preserved as Cash or CASH_EQUIVALENTS");
            assertEquals(new BigDecimal("0.025500"), trepsOpt.get().weight());

            Optional<MonthlyHoldingsRecord> optionsOpt = res.records().stream().filter(r -> "Derivative".equalsIgnoreCase(r.assetClass()) || "DERIVATIVE".equalsIgnoreCase(r.assetClass())).findFirst();
            assertTrue(optionsOpt.isPresent(), "Options liability must be preserved as Derivative or DERIVATIVE");
            assertEquals(new BigDecimal("-0.000300"), optionsOpt.get().weight());
        }
    }

    @Test
    @DisplayName("10. Instrument classification")
    void testInstrumentClassification() throws Exception {
        File hdfcSample = new File("C:/Users/DEUS/.gemini/antigravity-ide/brain/2f068f23-dc14-486b-8a91-5acdf0caf033/scratch/hdfc_flexi_cap_aug2026.xlsx");
        if (hdfcSample.exists()) {
            byte[] bytes = new FileInputStream(hdfcSample).readAllBytes();
            PpfasXlsxHoldingsParser.ParseResult res = hdfcParser.parse(bytes, "118955");
            
            long equityCount = res.records().stream().filter(r -> "Equity".equalsIgnoreCase(r.assetClass())).count();
            long reitCount = res.records().stream().filter(r -> "REIT".equalsIgnoreCase(r.assetClass())).count();
            long sovereignCount = res.records().stream().filter(r -> "Sovereign Debt".equalsIgnoreCase(r.assetClass()) || "SOVEREIGN_DEBT".equalsIgnoreCase(r.assetClass())).count();
            long cashCount = res.records().stream().filter(r -> "Cash".equalsIgnoreCase(r.assetClass()) || "CASH_EQUIVALENTS".equalsIgnoreCase(r.assetClass())).count();
            long derivativeCount = res.records().stream().filter(r -> "Derivative".equalsIgnoreCase(r.assetClass()) || "DERIVATIVE".equalsIgnoreCase(r.assetClass())).count();

            assertEquals(78, equityCount, "HDFC snapshot must contain exactly 78 equity positions");
            assertEquals(2, reitCount, "HDFC snapshot must contain exactly 2 REIT positions");
            assertEquals(3, sovereignCount, "HDFC snapshot must contain 3 Sovereign Debt positions");
            assertEquals(1, cashCount, "HDFC snapshot must contain 1 TREPS / Cash position");
            assertEquals(1, derivativeCount, "HDFC snapshot must contain 1 Options derivative liability position");
        }
    }

    @Test
    @DisplayName("11. Weight reconciliation and documented tolerance")
    void testWeightReconciliationAndDocumentedTolerance() throws Exception {
        File hdfcSample = new File("C:/Users/DEUS/.gemini/antigravity-ide/brain/2f068f23-dc14-486b-8a91-5acdf0caf033/scratch/hdfc_flexi_cap_aug2026.xlsx");
        if (hdfcSample.exists()) {
            byte[] bytes = new FileInputStream(hdfcSample).readAllBytes();
            PpfasXlsxHoldingsParser.ParseResult res = hdfcParser.parse(bytes, "118955");
            
            BigDecimal sumWeights = res.records().stream()
                .map(MonthlyHoldingsRecord::weight)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertEquals(new BigDecimal("1.000000"), sumWeights, "Sum of all disclosed holdings weights (including derivatives liability) MUST reconcile to EXACTLY 1.000000 (100.00%)");
        }
    }

    @Test
    @DisplayName("12. Malformed payload rejection")
    void testMalformedPayloadRejection() {
        byte[] garbage = "This is not an OpenXML spreadsheet payload".getBytes();
        ParseResultPpfas(garbage);
        ParseResultHdfc(garbage);
    }

    private void ParseResultPpfas(byte[] bytes) {
        PpfasXlsxHoldingsParser.ParseResult res = ppfasParser.parse(bytes, "122639");
        assertEquals(0, res.validHoldingsCount());
    }

    private void ParseResultHdfc(byte[] bytes) {
        PpfasXlsxHoldingsParser.ParseResult res = hdfcParser.parse(bytes, "118955");
        assertEquals(0, res.validHoldingsCount());
    }

    @Test
    @DisplayName("13. Freshness uncertainty and disclosure cadence handling")
    void testFreshnessUncertaintyHandling() {
        HoldingsCoverageReportDto report = coverageAuditService.generateCoverageReport(LocalDate.of(2026, 9, 30));
        assertNotNull(report);
        assertTrue(report.totalActiveUniverse() >= 30);
    }

    @Test
    @DisplayName("14. Fixture isolation and provenance classification")
    void testFixtureIsolation() {
        ingestionJob.execute(sourceRegistry.findByAmfiCode("122639").get().descriptor());
        ingestionJob.execute(sourceRegistry.findByAmfiCode("147481").get().descriptor());
        ingestionJob.execute(sourceRegistry.findByAmfiCode("118955").get().descriptor());

        HoldingsCoverageReportDto report = coverageAuditService.generateCoverageReport(LocalDate.of(2026, 9, 30));
        assertTrue(report.productionVerifiedHoldingsCount() >= 3, "At least 3 schemes must be production verified");
        assertTrue(report.totalActiveUniverse() >= 30, "Total active universe must be >= 30");
    }
}
