package com.yukira.backend.service.portfolio.cas;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.dto.portfolio.CasImportConfirmResponseDto;
import com.yukira.backend.dto.portfolio.CasImportResultDto;
import com.yukira.backend.dto.portfolio.FundConcentrationDto;
import com.yukira.backend.domain.entity.InvestorPortfolioHolding;
import com.yukira.backend.repository.InvestorPortfolioHoldingRepository;
import com.yukira.backend.service.PilotHoldingsDataService;
import com.yukira.backend.service.PortfolioIntelligenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CasImportServiceTest {

    @Autowired
    private CasImportService casImportService;

    @Autowired
    private InvestorPortfolioHoldingRepository investorHoldingRepository;

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private PilotHoldingsDataService pilotHoldingsDataService;

    @Autowired
    private PortfolioIntelligenceService intelligenceService;

    @BeforeEach
    void setUp() {
        pilotBootstrapService.bootstrapPilot();
        pilotHoldingsDataService.seedControlledPilotHoldingsCohort();
    }

    @Test
    void testCasImportStagingAndConfirmationWorkflow() {
        String mockCasJson = """
            {
              "statementDate": "2024-01-31",
              "holdings": [
                {
                  "rawSchemeName": "HDFC Flexi Cap Fund - Direct Growth",
                  "isin": "INF179K01UT0",
                  "amfiCode": "118955",
                  "folioNumber": "12345678/90",
                  "units": 150.5000,
                  "costBasisAmount": 220000.00,
                  "marketValue": 251435.12
                },
                {
                  "rawSchemeName": "Parag Parikh Flexi Cap Fund - Direct Growth",
                  "isin": "INF879O01015",
                  "amfiCode": "122639",
                  "folioNumber": "98765432/10",
                  "units": 500.0000,
                  "costBasisAmount": 300000.00,
                  "marketValue": 345000.00
                },
                {
                  "rawSchemeName": "Unknown Obscure Fund Option XYZ",
                  "isin": "INF999999999",
                  "amfiCode": "999999",
                  "folioNumber": "11111111/00",
                  "units": 10.0000,
                  "costBasisAmount": 5000.00,
                  "marketValue": 5500.00
                }
              ]
            }
            """;

        String testSubject = "test-investor-cas-01";
        ByteArrayInputStream is = new ByteArrayInputStream(mockCasJson.getBytes(StandardCharsets.UTF_8));

        // 1. Stage CAS Import
        CasImportResultDto stageResult = casImportService.stageImport(testSubject, "cams_statement_sample.json", is);

        assertNotNull(stageResult.importId());
        assertEquals("PENDING_REVIEW", stageResult.status());
        assertEquals(3, stageResult.totalRecordsDetected());
        assertEquals(2, stageResult.resolvedRecordsCount());
        assertEquals(1, stageResult.unresolvedRecordsCount());
        assertEquals("CAS_PARSER_V1_STRUCTURED", stageResult.parserVersion());
        assertNotNull(stageResult.sourceArtifactId(), "Source artifact ID must be non-null for raw file provenance.");
        assertNotNull(stageResult.sha256Hash(), "SHA-256 hash must be non-null.");
        assertEquals(64, stageResult.sha256Hash().length(), "SHA-256 hash must be 64 hexadecimal characters.");

        // Verify ISIN exact resolution
        var hdfcHolding = stageResult.holdings().stream()
            .filter(h -> "118955".equals(h.amfiCode()) || "INF179K01UT0".equals(h.isin()))
            .findFirst()
            .orElseThrow();
        assertEquals("RESOLVED", hdfcHolding.resolutionStatus());
        assertEquals("ISIN_EXACT", hdfcHolding.resolutionMethod());
        assertEquals(1L, hdfcHolding.resolvedSchemeOptionId());

        // Verify upload does NOT mutate investor portfolio holdings before confirmation
        List<InvestorPortfolioHolding> beforeConfirmHoldings = investorHoldingRepository.findAll().stream()
            .filter(h -> testSubject.equals(h.getInvestor().getAuth0Subject()))
            .toList();
        assertEquals(0, beforeConfirmHoldings.size(), "Upload must not mutate investor portfolio holdings before explicit confirmation.");

        // 2. Confirm CAS Import (MERGE mode)
        CasImportConfirmResponseDto confirmResult = casImportService.confirmImport(testSubject, stageResult.importId(), "MERGE");

        assertEquals("CONFIRMED", confirmResult.status());
        assertEquals(2, confirmResult.holdingsAddedCount());
        assertEquals(1, confirmResult.unresolvedSkippedCount());

        // Verify investor holdings are now persisted under testSubject
        List<InvestorPortfolioHolding> afterConfirmHoldings = investorHoldingRepository.findAll().stream()
            .filter(h -> testSubject.equals(h.getInvestor().getAuth0Subject()))
            .toList();
        assertEquals(2, afterConfirmHoldings.size());

        // 3. Test End-to-End Integration: Imported holdings flow directly into Portfolio Intelligence engines
        FundConcentrationDto hdfcConcentration = intelligenceService.calculateFundConcentration(1L, null);
        assertNotNull(hdfcConcentration);
        assertEquals(1L, hdfcConcentration.schemeOptionId());
        assertEquals("DIVERSIFIED", hdfcConcentration.hhiClassification());

        // 4. Test Idempotency of Confirmation
        CasImportConfirmResponseDto repeatConfirmResult = casImportService.confirmImport(testSubject, stageResult.importId(), "MERGE");
        assertEquals("CONFIRMED", repeatConfirmResult.status());
        assertEquals(0, repeatConfirmResult.holdingsAddedCount());
        assertTrue(repeatConfirmResult.messages().get(0).contains("already confirmed"));

        // 5. Test Investor Ownership Authorization Isolation
        assertThrows(IllegalArgumentException.class, () -> {
            casImportService.confirmImport("unauthorized-other-investor", stageResult.importId(), "MERGE");
        });
    }

    @Test
    void testEmptyFileValidation() {
        String testSubject = "test-investor-empty";
        ByteArrayInputStream emptyStream = new ByteArrayInputStream(new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> {
            casImportService.stageImport(testSubject, "empty.json", emptyStream);
        });
    }

    @Test
    void testReplaceReconciliationMode() {
        String testSubject = "test-investor-replace-mode";

        // 1. Initial import in MERGE mode
        String initialCasJson = """
            {
              "statementDate": "2024-01-31",
              "holdings": [
                {
                  "rawSchemeName": "HDFC Flexi Cap Fund",
                  "isin": "INF179K01UT0",
                  "amfiCode": "118955",
                  "units": 10.0000
                }
              ]
            }
            """;
        CasImportResultDto stage1 = casImportService.stageImport(testSubject, "cas1.json", new ByteArrayInputStream(initialCasJson.getBytes(StandardCharsets.UTF_8)));
        casImportService.confirmImport(testSubject, stage1.importId(), "MERGE");

        List<InvestorPortfolioHolding> initialHoldings = investorHoldingRepository.findAll().stream()
            .filter(h -> testSubject.equals(h.getInvestor().getAuth0Subject()))
            .toList();
        assertEquals(1, initialHoldings.size());
        assertEquals("118955", initialHoldings.get(0).getSchemeOption().getAmfiCode());

        // 2. Stage a replacement CAS payload with different fund
        String replaceCasJson = """
            {
              "statementDate": "2024-01-31",
              "holdings": [
                {
                  "rawSchemeName": "Parag Parikh Flexi Cap Fund",
                  "isin": "INF879O01015",
                  "amfiCode": "122639",
                  "units": 999.0000
                }
              ]
            }
            """;

        CasImportResultDto stage2 = casImportService.stageImport(testSubject, "cas2.json", new ByteArrayInputStream(replaceCasJson.getBytes(StandardCharsets.UTF_8)));
        CasImportConfirmResponseDto confirmResult = casImportService.confirmImport(testSubject, stage2.importId(), "REPLACE");

        assertEquals("CONFIRMED", confirmResult.status());
        List<InvestorPortfolioHolding> activeHoldings = investorHoldingRepository.findAll().stream()
            .filter(h -> testSubject.equals(h.getInvestor().getAuth0Subject()))
            .toList();
        assertEquals(1, activeHoldings.size());
        assertEquals("122639", activeHoldings.get(0).getSchemeOption().getAmfiCode());
    }

    @Test
    void testUnencryptedCamsPdfCasImportWorkflow() throws Exception {
        String testSubject = "test-investor-pdf-cams";
        byte[] pdfBytes = generateSyntheticCasPdf(null);

        ByteArrayInputStream is = new ByteArrayInputStream(pdfBytes);
        CasImportResultDto stageResult = casImportService.stageImport(testSubject, "cams_statement.pdf", is, null);

        assertNotNull(stageResult.importId());
        assertEquals("PENDING_REVIEW", stageResult.status());
        assertEquals(3, stageResult.totalRecordsDetected());
        assertEquals(2, stageResult.resolvedRecordsCount());
        assertEquals(1, stageResult.unresolvedRecordsCount());
        assertEquals("CAS_PARSER_V2_CAMS_PDF", stageResult.parserVersion());
        assertEquals("CAMS", stageResult.casProvider());
        assertNotNull(stageResult.sourceArtifactId());
        assertEquals(64, stageResult.sha256Hash().length());

        CasImportConfirmResponseDto confirmResult = casImportService.confirmImport(testSubject, stageResult.importId(), "MERGE");
        assertEquals("CONFIRMED", confirmResult.status());
        assertEquals(2, confirmResult.holdingsAddedCount());

        List<InvestorPortfolioHolding> holdings = investorHoldingRepository.findAll().stream()
            .filter(h -> testSubject.equals(h.getInvestor().getAuth0Subject()))
            .toList();
        assertEquals(2, holdings.size());
    }

    @Test
    void testEncryptedPdfCasImportWithPassword() throws Exception {
        String testSubject = "test-investor-pdf-encrypted";
        String password = "yukiracaspassword123";
        byte[] pdfBytes = generateSyntheticCasPdf(password);

        // 1. Attempt staging without password -> EXPECT PASSWORD_REQUIRED FAILURE
        CasImportResultDto failedResult = casImportService.stageImport(testSubject, "encrypted_cams.pdf", new ByteArrayInputStream(pdfBytes), null);
        assertEquals("FAILED", failedResult.status());
        assertTrue(failedResult.limitations().get(0).contains("PASSWORD_REQUIRED"));

        // 2. Attempt staging with wrong password -> EXPECT INVALID_PASSWORD FAILURE
        CasImportResultDto wrongPassResult = casImportService.stageImport(testSubject, "encrypted_cams.pdf", new ByteArrayInputStream(pdfBytes), "wrongpass");
        assertEquals("FAILED", wrongPassResult.status());
        assertTrue(wrongPassResult.limitations().get(0).contains("INVALID_PASSWORD"));

        // 3. Stage with correct password -> EXPECT SUCCESSFUL STAGING
        CasImportResultDto successResult = casImportService.stageImport(testSubject, "encrypted_cams.pdf", new ByteArrayInputStream(pdfBytes), password);
        assertEquals("PENDING_REVIEW", successResult.status());
        assertEquals(3, successResult.totalRecordsDetected());
        assertEquals(2, successResult.resolvedRecordsCount());
    }

    @Test
    void testUnsupportedPdfTemplate() throws Exception {
        String testSubject = "test-investor-pdf-unsupported";
        byte[] genericPdf = generateGenericPdf();

        CasImportResultDto stageResult = casImportService.stageImport(testSubject, "generic_invoice.pdf", new ByteArrayInputStream(genericPdf), null);
        assertEquals("FAILED", stageResult.status());
        assertTrue(stageResult.limitations().get(0).contains("UNSUPPORTED_CAS_TEMPLATE"));
    }

    @Test
    void testKfintechPdfProviderDetection() throws Exception {
        String testSubject = "test-investor-pdf-kfintech";
        byte[] kfinPdfBytes = generateSyntheticKfintechCasPdf();

        CasImportResultDto stageResult = casImportService.stageImport(testSubject, "kfintech_statement.pdf", new ByteArrayInputStream(kfinPdfBytes), null);

        assertNotNull(stageResult.importId());
        assertEquals("PENDING_REVIEW", stageResult.status());
        assertEquals("KFINTECH", stageResult.casProvider());
        assertEquals("CAS_PARSER_V2_KFINTECH_PDF", stageResult.parserVersion());
        assertEquals(2, stageResult.totalRecordsDetected());
        assertEquals(2, stageResult.resolvedRecordsCount());
    }

    @Test
    void testMultiFolioSchemeAggregationOnConfirmation() throws Exception {
        String testSubject = "test-investor-multi-folio";
        byte[] multiFolioPdfBytes = generateMultiFolioCasPdf();

        CasImportResultDto stageResult = casImportService.stageImport(testSubject, "multi_folio_statement.pdf", new ByteArrayInputStream(multiFolioPdfBytes), null);
        assertEquals(2, stageResult.totalRecordsDetected(), "Staging preserves 2 separate folio rows in staging.");
        assertEquals("VALID", stageResult.importQualityState());

        // Confirm import in MERGE mode
        CasImportConfirmResponseDto confirmResult = casImportService.confirmImport(testSubject, stageResult.importId(), "MERGE");
        assertEquals("CONFIRMED", confirmResult.status());

        List<InvestorPortfolioHolding> liveHoldings = investorHoldingRepository.findAll().stream()
            .filter(h -> testSubject.equals(h.getInvestor().getAuth0Subject()))
            .toList();

        assertEquals(1, liveHoldings.size(), "Live portfolio holdings aggregate multiple folios of same scheme into 1 scheme-level holding.");
        assertEquals("118955", liveHoldings.get(0).getSchemeOption().getAmfiCode());
        assertEquals(0, new java.math.BigDecimal("150.5000").compareTo(liveHoldings.get(0).getUnits()), "Aggregated units must equal 100.0 + 50.5 = 150.5 units.");
    }

    private byte[] generateSyntheticKfintechCasPdf() throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("KFintech Mutual Fund CAS Statement - Page 1 of 2");
                cs.newLineAtOffset(0, -20);
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 10);
                cs.showText("Valuation as on: 31-Jan-2024");
                cs.newLineAtOffset(0, -30);

                cs.showText("Folio No: 98765432/10");
                cs.newLineAtOffset(0, -15);
                cs.showText("Parag Parikh Flexi Cap Fund - Direct Growth");
                cs.newLineAtOffset(0, -15);
                cs.showText("ISIN: INF879O01015  AMFI Code: 122639");
                cs.newLineAtOffset(0, -15);
                cs.showText("Closing Unit Balance: 500.000  Cost Value: 3,00,000.00  Market Value: 3,45,000.00  NAV: 690.0000");
                cs.newLineAtOffset(0, -20);
                cs.showText("Sub Total : 3,45,000.00");
                cs.newLineAtOffset(0, -30);

                cs.showText("Folio No: 12345678/90");
                cs.newLineAtOffset(0, -15);
                cs.showText("HDFC Flexi Cap Fund - Direct Plan - Growth Option");
                cs.newLineAtOffset(0, -15);
                cs.showText("ISIN: INF179K01UT0  AMFI Code: 118955");
                cs.newLineAtOffset(0, -15);
                cs.showText("Closing Unit Balance: 150.500  Cost Value: 2,20,000.00  Market Value: 2,51,435.12  NAV: 1670.6720");
                cs.newLineAtOffset(0, -20);
                cs.showText("Sub Total : 2,51,435.12");

                cs.endText();
            }
            doc.save(baos);
        }
        return baos.toByteArray();
    }

    private byte[] generateMultiFolioCasPdf() throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("CAMS Consolidated Account Statement");
                cs.newLineAtOffset(0, -20);
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 10);
                cs.showText("Statement Date: 31-Jan-2024");
                cs.newLineAtOffset(0, -30);

                // Folio 1 for HDFC Flexi Cap
                cs.showText("Folio No: 11111111/01");
                cs.newLineAtOffset(0, -15);
                cs.showText("HDFC Flexi Cap Fund - Direct Plan - Growth Option");
                cs.newLineAtOffset(0, -15);
                cs.showText("ISIN: INF179K01UT0  AMFI Code: 118955");
                cs.newLineAtOffset(0, -15);
                cs.showText("Closing Unit Balance: 100.000  Cost Value: 1,50,000.00  Market Value: 1,67,067.20  NAV: 1670.6720");
                cs.newLineAtOffset(0, -30);

                // Folio 2 for SAME HDFC Flexi Cap scheme
                cs.showText("Folio No: 22222222/02");
                cs.newLineAtOffset(0, -15);
                cs.showText("HDFC Flexi Cap Fund - Direct Plan - Growth Option");
                cs.newLineAtOffset(0, -15);
                cs.showText("ISIN: INF179K01UT0  AMFI Code: 118955");
                cs.newLineAtOffset(0, -15);
                cs.showText("Closing Unit Balance: 50.500  Cost Value: 70,000.00  Market Value: 84,367.93  NAV: 1670.6720");

                cs.endText();
            }
            doc.save(baos);
        }
        return baos.toByteArray();
    }

    private byte[] generateSyntheticCasPdf(String password) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("CAMS Consolidated Account Statement");
                cs.newLineAtOffset(0, -20);
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 10);
                cs.showText("Statement Date: 31-Jan-2024");
                cs.newLineAtOffset(0, -30);

                cs.showText("Folio No: 12345678/90");
                cs.newLineAtOffset(0, -15);
                cs.showText("HDFC Flexi Cap Fund - Direct Plan - Growth Option");
                cs.newLineAtOffset(0, -15);
                cs.showText("ISIN: INF179K01UT0  AMFI Code: 118955");
                cs.newLineAtOffset(0, -15);
                cs.showText("Closing Unit Balance: 150.500  Cost Value: 220000.00  Market Value: 251435.12  NAV: 1670.6720");
                cs.newLineAtOffset(0, -30);

                cs.showText("Folio No: 98765432/10");
                cs.newLineAtOffset(0, -15);
                cs.showText("Parag Parikh Flexi Cap Fund - Direct Growth");
                cs.newLineAtOffset(0, -15);
                cs.showText("ISIN: INF879O01015  AMFI Code: 122639");
                cs.newLineAtOffset(0, -15);
                cs.showText("Closing Unit Balance: 500.000  Cost Value: 300000.00  Market Value: 345000.00  NAV: 690.0000");
                cs.newLineAtOffset(0, -30);

                cs.showText("Folio No: 11112222/00");
                cs.newLineAtOffset(0, -15);
                cs.showText("Unknown Obscure Fund Option XYZ");
                cs.newLineAtOffset(0, -15);
                cs.showText("ISIN: INF999999999  AMFI Code: 999999");
                cs.newLineAtOffset(0, -15);
                cs.showText("Closing Unit Balance: 10.000  Cost Value: 5000.00  Market Value: 5500.00  NAV: 550.0000");

                cs.endText();
            }

            if (password != null && !password.isBlank()) {
                org.apache.pdfbox.pdmodel.encryption.AccessPermission ap = new org.apache.pdfbox.pdmodel.encryption.AccessPermission();
                org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy spp = new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(password, password, ap);
                doc.protect(spp);
            }

            doc.save(baos);
        }
        return baos.toByteArray();
    }

    private byte[] generateGenericPdf() throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Invoice #999182");
                cs.newLineAtOffset(0, -20);
                cs.showText("This is an arbitrary billing invoice document with no mutual fund data.");
                cs.endText();
            }
            doc.save(baos);
        }
        return baos.toByteArray();
    }
}
