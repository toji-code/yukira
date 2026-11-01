package com.yukira.backend.ingestion.nse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NiftyTriParser Deterministic Audit & Validation Unit Tests")
class NiftyTriParserTest {

    private NiftyTriParser parser;

    @BeforeEach
    void setUp() {
        parser = new NiftyTriParser();
    }

    @Test
    @DisplayName("Parses standard valid NSE TRI array payload with exact audit accounting")
    void testParseValidTriArray() {
        String json = """
            [
                {"Date": "01 Jan 2024", "TotalReturnsIndex": "30480.99", "Index Name": "Nifty 500"},
                {"Date": "02 Jan 2024", "TotalReturnsIndex": "30400.92", "Index Name": "Nifty 500"},
                {"Date": "03 Jan 2024", "TotalReturnsIndex": "30320.70", "Index Name": "Nifty 500"}
            ]
            """;

        NiftyTriParser.NiftyParseResult result = parser.parseWithAudit(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(3, result.rowsReceived());
        assertEquals(3, result.rowsAccepted());
        assertEquals(0, result.rowsRejected());
        assertEquals(LocalDate.of(2024, 1, 1), result.minEffectiveDate());
        assertEquals(LocalDate.of(2024, 1, 3), result.maxEffectiveDate());

        List<NiftyTriRecord> records = result.records();
        assertEquals(3, records.size());
        assertEquals(new BigDecimal("30480.99000000"), records.get(0).totalReturnsIndex());
        assertEquals("Nifty 500", records.get(0).indexName());

        // Verify 18:00 IST availability time
        OffsetDateTime expectedAvail = LocalDate.of(2024, 1, 1)
            .atTime(LocalTime.of(18, 0))
            .atOffset(ZoneOffset.ofHoursMinutes(5, 30));
        assertEquals(expectedAvail, records.get(0).availabilityTime());
    }

    @Test
    @DisplayName("Parses wrapped 'd' field JSON object from NSE endpoint")
    void testParseWrappedDFormat() {
        String innerJson = "[{\\\"Date\\\": \\\"15 Jan 2024\\\", \\\"TotalReturnsIndex\\\": \\\"31132.31\\\", \\\"Index Name\\\": \\\"Nifty 100\\\"}]";
        String json = "{\"d\": \"" + innerJson + "\"}";

        NiftyTriParser.NiftyParseResult result = parser.parseWithAudit(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, result.rowsReceived());
        assertEquals(1, result.rowsAccepted());
        assertEquals(0, result.rowsRejected());
        assertEquals(LocalDate.of(2024, 1, 15), result.minEffectiveDate());
        assertEquals(new BigDecimal("31132.31000000"), result.records().get(0).totalReturnsIndex());
        assertEquals("Nifty 100", result.records().get(0).indexName());
    }

    @Test
    @DisplayName("Rejects HTML or error responses from provider endpoint")
    void testRejectHtmlResponse() {
        String html = "<!DOCTYPE html><html><head><title>Access Denied / 403</title></head><body>WAF Blocked</body></html>";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            parser.parseWithAudit(html.getBytes(StandardCharsets.UTF_8))
        );
        assertTrue(ex.getMessage().contains("Unexpected HTML/XML response"));
    }

    @Test
    @DisplayName("Rejects empty or null byte payloads")
    void testRejectEmptyPayloads() {
        assertThrows(IllegalArgumentException.class, () -> parser.parseWithAudit(null));
        assertThrows(IllegalArgumentException.class, () -> parser.parseWithAudit(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> parser.parseWithAudit("   ".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Enforces strict TRI verification: ignores Close / PRI and rejects non-TRI or invalid rows")
    void testStrictTriVerificationAndRowRejection() {
        String json = """
            [
                {"Date": "01 Jan 2024", "TotalReturnsIndex": "10000.50", "Index Name": "Nifty Midcap 150"},
                {"Date": "02 Jan 2024", "Close": "9950.00", "Index Name": "Nifty Midcap 150"},
                {"Date": "invalid-date", "TotalReturnsIndex": "10020.00", "Index Name": "Nifty Midcap 150"},
                {"Date": "03 Jan 2024", "TotalReturnsIndex": "-50.00", "Index Name": "Nifty Midcap 150"},
                {"Date": "04 Jan 2024", "TotalReturnsIndex": "null", "Index Name": "Nifty Midcap 150"},
                {"Date": "05 Jan 2024", "TotalReturnsIndex": "10150.25", "Index Name": "Nifty Midcap 150"}
            ]
            """;

        NiftyTriParser.NiftyParseResult result = parser.parseWithAudit(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(6, result.rowsReceived());
        assertEquals(2, result.rowsAccepted(), "Only rows with valid Date and positive TotalReturnsIndex must be accepted");
        assertEquals(4, result.rowsRejected(), "Non-TRI Close, invalid date, negative level, and null must be rejected");
        assertEquals(2, result.records().size());
        assertEquals(LocalDate.of(2024, 1, 1), result.minEffectiveDate());
        assertEquals(LocalDate.of(2024, 1, 5), result.maxEffectiveDate());
    }
}
