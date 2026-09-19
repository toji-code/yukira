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
            119062;HDFC Flexi Cap Fund - Direct Plan - Growth Option;INF179K01BE2;;1560.1234;;;01-Jan-2024
            119062;HDFC Flexi Cap Fund - Direct Plan - Growth Option;INF179K01BE2;;1565.5678;;;02-Jan-2024
            """;

        List<AmfiNavRecord> records = parser.parse(content.getBytes(WINDOWS_1252));

        assertEquals(2, records.size());
        AmfiNavRecord r1 = records.get(0);
        assertTrue(r1.isValid());
        assertEquals("119062", r1.schemeCode());
        assertEquals("INF179K01BE2", r1.isinGrowth());
        assertEquals(new BigDecimal("1560.1234"), r1.navValue());
        assertEquals(LocalDate.of(2024, 1, 1), r1.navDate());

        AmfiNavRecord r2 = records.get(1);
        assertTrue(r2.isValid());
        assertEquals(new BigDecimal("1565.5678"), r2.navValue());
        assertEquals(LocalDate.of(2024, 1, 2), r2.navDate());
    }

    @Test
    @DisplayName("Handle malformed rows: missing tokens, non-numeric scheme code, invalid NAV, invalid date")
    void testMalformedRowsPreservedWithErrors() {
        String content = """
            Scheme Code;Scheme Name;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Repurchase Price;Sale Price;Date
            119062;ShortRow;INF123
            119062;BadNav;INF123;;N.A.;;;01-Jan-2024
            119062;BadDate;INF123;;100.50;;;32-InvalidMonth-2024
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
        String content = "119062;Fund;ISIN;;100.00;;;15-Jan-2024\n";
        byte[] bytes = content.getBytes(WINDOWS_1252);

        List<AmfiNavRecord> run1 = parser.parse(bytes);
        List<AmfiNavRecord> run2 = parser.parse(bytes);

        assertEquals(run1.size(), run2.size());
        assertEquals(run1.get(0).navValue(), run2.get(0).navValue());
        assertEquals(run1.get(0).navDate(), run2.get(0).navDate());
    }
}
