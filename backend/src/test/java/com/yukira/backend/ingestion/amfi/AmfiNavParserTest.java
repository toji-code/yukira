package com.yukira.backend.ingestion.amfi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AmfiNavParserTest {

    private final AmfiNavParser parser = new AmfiNavParser();
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    @Test
    @DisplayName("Parse valid AMFI historical NAV records with header and category banners")
    void testParseValidRecords() {
        String content = """
            Open Ended Schemes ( Equity Scheme - Large Cap Fund )

            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Repurchase Price;Sale Price;Date
            118955;HDFC Flexi Cap Fund - Growth Option - Direct Plan;INF179K01UT0;;1630.7330;;;01-Jan-2024
            118955;HDFC Flexi Cap Fund - Growth Option - Direct Plan;INF179K01UT0;;1625.1540;;;02-Jan-2024
            """;

        List<AmfiNavRecord> records = parser.parse(content.getBytes(WINDOWS_1252));

        assertEquals(2, records.size());
        AmfiNavRecord r1 = records.get(0);
        assertTrue(r1.isValid());
        assertEquals("118955", r1.schemeCode());
        assertEquals("INF179K01UT0", r1.isinGrowth());
        assertEquals(new BigDecimal("1630.7330"), r1.navValue());
        assertEquals(LocalDate.of(2024, 1, 1), r1.navDate());

        AmfiNavRecord r2 = records.get(1);
        assertTrue(r2.isValid());
        assertEquals(new BigDecimal("1625.1540"), r2.navValue());
        assertEquals(LocalDate.of(2024, 1, 2), r2.navDate());
    }

    @Test
    @DisplayName("Handle malformed rows: missing tokens, non-numeric scheme code, invalid NAV, invalid date")
    void testMalformedRowsPreservedWithErrors() {
        String content = """
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Repurchase Price;Sale Price;Date
            999999;ShortRow;INF123
            999999;BadNav;INF123;;N.A.;;;01-Jan-2024
            999999;BadDate;INF123;;100.50;;;32-InvalidMonth-2024
            ABCDEF;NotNumericCode;INF123;;100.50;;;01-Jan-2024
            """;

        List<AmfiNavRecord> records = parser.parse(content.getBytes(WINDOWS_1252));

        assertEquals(4, records.size());
        for (AmfiNavRecord r : records) {
            assertFalse(r.isValid(), "Row should be marked invalid: " + r.rawLine());
            assertNotNull(r.parseErrorMessage());
        }

        assertTrue(records.get(0).parseErrorMessage().contains("expected at least 5 tokens"));
        assertTrue(records.get(1).parseErrorMessage().contains("Invalid numeric NAV"));
        assertTrue(records.get(2).parseErrorMessage().contains("Invalid date format"));
        assertTrue(records.get(3).parseErrorMessage().contains("non-numeric Scheme Code"));
    }

    @Test
    @DisplayName("Determinism: Identical bytes produce identical parse output without side-effects")
    void testParseDeterminism() {
        String content = "999999;Fund;ISIN;;100.00;;;15-Jan-2024\n";
        byte[] bytes = content.getBytes(WINDOWS_1252);

        List<AmfiNavRecord> run1 = parser.parse(bytes);
        List<AmfiNavRecord> run2 = parser.parse(bytes);

        assertEquals(run1.size(), run2.size());
        assertEquals(run1.get(0).navValue(), run2.get(0).navValue());
        assertEquals(run1.get(0).navDate(), run2.get(0).navDate());
    }

    @Test
    @DisplayName("Parse historical report format with Plan and Option columns (DownloadNAVHistoryReport_Po.aspx)")
    void testParseHistoricalReportFormat() {
        String content = """
            Scheme Code;NAV Name;Plan;Option;ISIN Div Payout/ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Date
            118955;HDFC Flexi Cap Fund;Direct;Growth;INF179K01UT0;;1630.7330;01-Jan-2024
            118955;HDFC Flexi Cap Fund;Direct;Growth;INF179K01UT0;;1670.6720;15-Jan-2024
            """;

        List<AmfiNavRecord> records = parser.parse(content.getBytes(WINDOWS_1252));

        assertEquals(2, records.size());
        AmfiNavRecord r1 = records.get(0);
        assertTrue(r1.isValid());
        assertEquals("118955", r1.schemeCode());
        assertEquals("HDFC Flexi Cap Fund", r1.schemeName());
        assertEquals("INF179K01UT0", r1.isinGrowth());
        assertEquals(new BigDecimal("1630.7330"), r1.navValue());
        assertEquals(LocalDate.of(2024, 1, 1), r1.navDate());

        AmfiNavRecord r2 = records.get(1);
        assertTrue(r2.isValid());
        assertEquals("1670.6720", r2.navValue().toPlainString());
        assertEquals(LocalDate.of(2024, 1, 15), r2.navDate());
    }

    @Test
    @DisplayName("Parse daily NAVAll format with ISIN before Scheme Name")
    void testParseDailyNavAllFormat() {
        String content = """
            Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Plan;Option;Net Asset Value;Date
            118955;INF179K01UT0;;HDFC Flexi Cap Fund - Direct Plan - Growth Option;Direct;Growth;1950.4500;02-Oct-2026
            """;

        List<AmfiNavRecord> records = parser.parse(content.getBytes(WINDOWS_1252));

        assertEquals(1, records.size());
        AmfiNavRecord r1 = records.get(0);
        assertTrue(r1.isValid());
        assertEquals("118955", r1.schemeCode());
        assertEquals("INF179K01UT0", r1.isinGrowth());
        assertEquals("HDFC Flexi Cap Fund - Direct Plan - Growth Option", r1.schemeName());
        assertEquals(new BigDecimal("1950.4500"), r1.navValue());
        assertEquals(LocalDate.of(2026, 10, 2), r1.navDate());
    }
}
