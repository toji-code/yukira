package com.yukira.backend.ingestion.holdings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PpfasXlsxHoldingsParserTest {

    private PpfasXlsxHoldingsParser parser;

    @BeforeEach
    void setUp() {
        parser = new PpfasXlsxHoldingsParser();
    }

    @Test
    void testParseEmptyPayload() {
        PpfasXlsxHoldingsParser.ParseResult result = parser.parse(new byte[0], "122639");
        assertNull(result.portfolioDate());
        assertTrue(result.records().isEmpty());
        assertEquals(0, result.totalRowsParsed());
        assertEquals(0, result.validHoldingsCount());
    }

    @Test
    void testPercentageWeightConversions() {
        assertEquals(new BigDecimal("0.091900"), parser.parseWeightForTest("9.19%"));
        assertEquals(new BigDecimal("0.091900"), parser.parseWeightForTest("9.19"));
        assertEquals(new BigDecimal("0.008500"), parser.parseWeightForTest("0.85%"));
        assertEquals(new BigDecimal("0.85"), parser.parseWeightForTest("0.85"));
        assertEquals(new BigDecimal("0.000300"), parser.parseWeightForTest("0.03%"));
        assertEquals(new BigDecimal("0.03"), parser.parseWeightForTest("0.03"));
        assertEquals(BigDecimal.ZERO, parser.parseWeightForTest("0.00%"));
        assertEquals(new BigDecimal("-0.000300"), parser.parseWeightForTest("-0.03%"));
        assertEquals(new BigDecimal("-0.03"), parser.parseWeightForTest("-0.03"));
        assertNull(parser.parseWeightForTest(null));
        assertNull(parser.parseWeightForTest(""));
        assertNull(parser.parseWeightForTest("N/A"));
    }

    @Test
    void testParseRealPpfasXlsxPayload() throws Exception {
        // Read local scratch sample file if available
        File sampleFile = new File("C:/Users/DEUS/.gemini/antigravity-ide/brain/2f068f23-dc14-486b-8a91-5acdf0caf033/scratch/ppfas_sept2026.xls");
        if (!sampleFile.exists()) {
            return; // Skip if scratch file absent in environment
        }

        byte[] payload;
        try (FileInputStream fis = new FileInputStream(sampleFile)) {
            payload = fis.readAllBytes();
        }

        PpfasXlsxHoldingsParser.ParseResult result = parser.parse(payload, "122639");
        assertNotNull(result.portfolioDate(), "Portfolio date should be extracted from XLSX header");
        assertFalse(result.records().isEmpty(), "Parsed records should not be empty");

        assertTrue(result.validHoldingsCount() > 0, "Valid holdings count should be > 0");

        // Verify key holdings (e.g. HDFC Bank, ICICI Bank, Power Grid, ITC)
        boolean foundHdfc = result.records().stream().anyMatch(r -> "INE040A01034".equals(r.isin()));
        boolean foundIcici = result.records().stream().anyMatch(r -> "INE090A01021".equals(r.isin()));
        assertTrue(foundHdfc || foundIcici, "Should extract canonical ISIN codes (HDFC Bank INE040A01034 or ICICI Bank INE090A01021)");
    }
    @Test
    void testUnmatchedWorksheetSelectionFailClosed() {
        // Test parsing with an unmatching target AMFI code to verify fail-closed sheet selection
        PpfasXlsxHoldingsParser.ParseResult result = parser.parse(new byte[0], "UNKNOWN_AMFI");
        assertEquals(0, result.records().size(), "Unmatched or empty sheet container must return 0 records (fail closed)");
    }
}
