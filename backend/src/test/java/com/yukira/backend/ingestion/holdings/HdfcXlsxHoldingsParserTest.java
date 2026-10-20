package com.yukira.backend.ingestion.holdings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class HdfcXlsxHoldingsParserTest {

    private HdfcXlsxHoldingsParser parser;

    @BeforeEach
    void setUp() {
        parser = new HdfcXlsxHoldingsParser();
    }

    @Test
    void testParseEmptyPayload() {
        PpfasXlsxHoldingsParser.ParseResult result = parser.parse(new byte[0], "118955");
        assertNull(result.portfolioDate());
        assertTrue(result.records().isEmpty());
        assertEquals(0, result.totalRowsParsed());
        assertEquals(0, result.validHoldingsCount());
    }

    @Test
    void testPercentageWeightConversions() {
        assertEquals(new BigDecimal("0.091900"), parser.parseWeightForTest("9.19%"));
        assertEquals(new BigDecimal("0.091900"), parser.parseWeightForTest("9.19"));
        assertEquals(new BigDecimal("0.005700"), parser.parseWeightForTest("0.57%"));
        assertEquals(new BigDecimal("0.005700"), parser.parseWeightForTest("0.57"));
        assertEquals(new BigDecimal("0.001200"), parser.parseWeightForTest("0.12%"));
        assertEquals(new BigDecimal("0.001200"), parser.parseWeightForTest("0.12"));
        assertEquals(new BigDecimal("0.000300"), parser.parseWeightForTest("0.03%"));
        assertEquals(new BigDecimal("0.000300"), parser.parseWeightForTest("0.03"));
        assertEquals(BigDecimal.ZERO, parser.parseWeightForTest("0.00%"));
        assertEquals(BigDecimal.ZERO, parser.parseWeightForTest("0.00"));
        assertEquals(new BigDecimal("-0.000300"), parser.parseWeightForTest("-0.03%"));
        assertEquals(new BigDecimal("-0.000300"), parser.parseWeightForTest("-0.03"));
        assertEquals(new BigDecimal("-0.000300"), parser.parseWeightForTest("(0.03%)"));
        assertEquals(new BigDecimal("-0.000300"), parser.parseWeightForTest("(0.03)"));
        assertNull(parser.parseWeightForTest(null));
        assertNull(parser.parseWeightForTest(""));
        assertNull(parser.parseWeightForTest("N/A"));
        assertNull(parser.parseWeightForTest("INVALID"));
    }

    @Test
    void testParseRealHdfcXlsxPayload() throws Exception {
        File sampleFile = new File("C:/Users/DEUS/.gemini/antigravity-ide/brain/2f068f23-dc14-486b-8a91-5acdf0caf033/scratch/hdfc_flexi_cap_aug2026.xlsx");
        if (!sampleFile.exists()) {
            return;
        }

        byte[] payload;
        try (FileInputStream fis = new FileInputStream(sampleFile)) {
            payload = fis.readAllBytes();
        }

        PpfasXlsxHoldingsParser.ParseResult result = parser.parse(payload, "118955");
        assertEquals(LocalDate.of(2026, 8, 31), result.portfolioDate(), "Portfolio date should be extracted as 2026-08-31");
        assertFalse(result.records().isEmpty(), "Parsed records should not be empty");
        assertTrue(result.validHoldingsCount() >= 80, "Valid holdings count should be >= 80");

        boolean foundIcici = result.records().stream().anyMatch(r -> "INE090A01021".equals(r.isin()));
        boolean foundAxis = result.records().stream().anyMatch(r -> "INE040A01034".equals(r.isin()));
        boolean foundHdfcBank = result.records().stream().anyMatch(r -> "INE062A01020".equals(r.isin()));
        assertTrue(foundIcici && foundAxis && foundHdfcBank, "Should extract canonical equity ISINs for top holdings");
    }
}
