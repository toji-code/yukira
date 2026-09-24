package com.yukira.backend.ingestion.fbil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FBIL 91-Day T-Bill Parser Tests")
class FbilTBillParserTest {

    private FbilTBillParser parser;

    @BeforeEach
    void setUp() {
        parser = new FbilTBillParser();
    }

    @Test
    @DisplayName("Parses official FBIL JSON array payload and normalizes percentage yield to decimal")
    void testParseOfficialFbilJsonPayload() {
        String json = """
            [
              {
                "processRunDate": "2024-01-01 00:00:00",
                "displayTime": "2024-01-01 17:30:00",
                "tenorName": "3 Months",
                "rate": 6.86,
                "comments": " "
              },
              {
                "processRunDate": "2024-01-02 00:00:00",
                "displayTime": "2024-01-02 17:30:00",
                "tenorName": "3 Months",
                "rate": 6.84,
                "comments": " "
              }
            ]
            """;

        List<FbilTBillRecord> records = parser.parse(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(2, records.size());

        FbilTBillRecord r1 = records.get(0);
        assertEquals(LocalDate.of(2024, 1, 1), r1.effectiveDate());
        assertEquals("FBIL_91D_TBILL", r1.benchmarkCode());
        assertEquals("3 Months", r1.tenor());
        assertEquals(new BigDecimal("0.06860000"), r1.quotedYield());
        assertEquals("ACT_365", r1.daycountConvention());
        assertEquals(OffsetDateTime.of(2024, 1, 1, 17, 30, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)), r1.availabilityTime());

        FbilTBillRecord r2 = records.get(1);
        assertEquals(LocalDate.of(2024, 1, 2), r2.effectiveDate());
        assertEquals(new BigDecimal("0.06840000"), r2.quotedYield());
    }

    @Test
    @DisplayName("Strict tenor filtering: Extracts only 3 Months / 91D tenors and rejects all other tenors")
    void testTenorFilteringRejectsNon91DTenors() {
        String json = """
            [
              {"processRunDate": "2024-01-01 00:00:00", "displayTime": "2024-01-01 17:30:00", "tenorName": "7 Days", "rate": 6.83},
              {"processRunDate": "2024-01-01 00:00:00", "displayTime": "2024-01-01 17:30:00", "tenorName": "14 Days", "rate": 6.85},
              {"processRunDate": "2024-01-01 00:00:00", "displayTime": "2024-01-01 17:30:00", "tenorName": "1 Month", "rate": 6.85},
              {"processRunDate": "2024-01-01 00:00:00", "displayTime": "2024-01-01 17:30:00", "tenorName": "2 Months", "rate": 6.85},
              {"processRunDate": "2024-01-01 00:00:00", "displayTime": "2024-01-01 17:30:00", "tenorName": "3 Months", "rate": 6.86},
              {"processRunDate": "2024-01-01 00:00:00", "displayTime": "2024-01-01 17:30:00", "tenorName": "4 Months", "rate": 6.88},
              {"processRunDate": "2024-01-01 00:00:00", "displayTime": "2024-01-01 17:30:00", "tenorName": "6 Months", "rate": 7.10},
              {"processRunDate": "2024-01-01 00:00:00", "displayTime": "2024-01-01 17:30:00", "tenorName": "12 Months", "rate": 7.15}
            ]
            """;

        List<FbilTBillRecord> records = parser.parse(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, records.size());
        assertEquals("3 Months", records.get(0).tenor());
        assertEquals(new BigDecimal("0.06860000"), records.get(0).quotedYield());
    }

    @Test
    @DisplayName("Parses real authoritative FBIL January 2024 artifact (154 raw objects -> 11 benchmark observations)")
    void testParseRealAuthoritativeFbilJanuary2024Artifact() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("fixtures/fbil_tbill_jan2024.json");
        assertNotNull(stream, "Real FBIL fixture must exist in test classpath");
        byte[] payload = stream.readAllBytes();

        List<FbilTBillRecord> records = parser.parse(payload);
        assertEquals(11, records.size(), "Must extract exactly 11 trading day 3M benchmark observations from 154 raw records");

        // The real FBIL payload delivers records in reverse-chronological order (from 2024-01-15 down to 2024-01-01)
        assertEquals(LocalDate.of(2024, 1, 15), records.get(0).effectiveDate());
        assertEquals(new BigDecimal("0.06950000"), records.get(0).quotedYield());
        assertEquals(OffsetDateTime.of(2024, 1, 15, 18, 45, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)), records.get(0).availabilityTime());

        assertEquals(LocalDate.of(2024, 1, 12), records.get(1).effectiveDate());
        assertEquals(new BigDecimal("0.06940000"), records.get(1).quotedYield());

        assertEquals(LocalDate.of(2024, 1, 11), records.get(2).effectiveDate());
        assertEquals(new BigDecimal("0.06930000"), records.get(2).quotedYield());

        assertEquals(LocalDate.of(2024, 1, 10), records.get(3).effectiveDate());
        assertEquals(new BigDecimal("0.06920000"), records.get(3).quotedYield());

        assertEquals(LocalDate.of(2024, 1, 9), records.get(4).effectiveDate());
        assertEquals(new BigDecimal("0.06950000"), records.get(4).quotedYield());

        assertEquals(LocalDate.of(2024, 1, 8), records.get(5).effectiveDate());
        assertEquals(new BigDecimal("0.06940000"), records.get(5).quotedYield());

        assertEquals(LocalDate.of(2024, 1, 5), records.get(6).effectiveDate());
        assertEquals(new BigDecimal("0.06940000"), records.get(6).quotedYield());

        assertEquals(LocalDate.of(2024, 1, 4), records.get(7).effectiveDate());
        assertEquals(new BigDecimal("0.06890000"), records.get(7).quotedYield());

        assertEquals(LocalDate.of(2024, 1, 3), records.get(8).effectiveDate());
        assertEquals(new BigDecimal("0.06930000"), records.get(8).quotedYield());

        assertEquals(LocalDate.of(2024, 1, 2), records.get(9).effectiveDate());
        assertEquals(new BigDecimal("0.06840000"), records.get(9).quotedYield());

        assertEquals(LocalDate.of(2024, 1, 1), records.get(10).effectiveDate());
        assertEquals(new BigDecimal("0.06860000"), records.get(10).quotedYield());
    }

    @Test
    @DisplayName("Rejects negative yield values in JSON with exception")
    void testRejectNegativeYieldInJson() {
        String json = """
            [
              {"processRunDate": "2024-01-01 00:00:00", "displayTime": "2024-01-01 17:30:00", "tenorName": "3 Months", "rate": -0.05}
            ]
            """;

        assertThrows(RuntimeException.class, () -> parser.parse(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Handles malformed JSON cleanly by throwing RuntimeException")
    void testMalformedJsonThrowsException() {
        String malformedJson = "[{\"processRunDate\": \"2024-01-01 00:00:00\", \"rate\": ";
        assertThrows(RuntimeException.class, () -> parser.parse(malformedJson.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Handles missing required fields in JSON objects by cleanly skipping record")
    void testMissingRequiredFieldsSkippedCleanly() {
        String json = """
            [
              {"tenorName": "3 Months", "rate": 6.86},
              {"processRunDate": "2024-01-01 00:00:00", "tenorName": "3 Months"},
              {"processRunDate": "2024-01-01 00:00:00", "rate": 6.86},
              {"processRunDate": "2024-01-02 00:00:00", "tenorName": "3 Months", "rate": 6.84}
            ]
            """;

        List<FbilTBillRecord> records = parser.parse(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, records.size());
        assertEquals(LocalDate.of(2024, 1, 2), records.get(0).effectiveDate());
    }

    @Test
    @DisplayName("Parses standard FBIL CSV export with percentage yields")
    void testParseStandardFbilCsvWithPercentages() {
        String csv = """
            Date,Tenor,Yield(%)
            01-Jan-2024,91D,6.9500
            02-Jan-2024,91D,6.9650
            03-Jan-2024,91D,6.9400
            """;

        List<FbilTBillRecord> records = parser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(3, records.size());

        FbilTBillRecord r1 = records.get(0);
        assertEquals(LocalDate.of(2024, 1, 1), r1.effectiveDate());
        assertEquals("FBIL_91D_TBILL", r1.benchmarkCode());
        assertEquals("91D", r1.tenor());
        assertEquals(new BigDecimal("0.06950000"), r1.quotedYield());
        assertEquals("ACT_365", r1.daycountConvention());
    }

    @Test
    @DisplayName("Parses FBIL CSV with decimal yields directly")
    void testParseFbilCsvWithDecimals() {
        String csv = """
            effective_date,tenor,quoted_yield
            2024-01-01,91D,0.06950000
            2024-01-02,91D,0.07120000
            """;

        List<FbilTBillRecord> records = parser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(2, records.size());
        assertEquals(new BigDecimal("0.06950000"), records.get(0).quotedYield());
        assertEquals(new BigDecimal("0.07120000"), records.get(1).quotedYield());
    }

    @Test
    @DisplayName("Filters multi-tenor FBIL yield curve export to strictly 91D observations")
    void testParseMultiTenorCsvFiltersFor91D() {
        String csv = """
            Date,Tenor,Rate(%)
            01-Jan-2024,91D,6.95
            01-Jan-2024,182D,7.10
            01-Jan-2024,364D,7.15
            02-Jan-2024,91D,6.96
            02-Jan-2024,182D,7.11
            """;

        List<FbilTBillRecord> records = parser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(2, records.size());
        assertEquals(LocalDate.of(2024, 1, 1), records.get(0).effectiveDate());
        assertEquals(LocalDate.of(2024, 1, 2), records.get(1).effectiveDate());
        assertEquals("91D", records.get(0).tenor());
        assertEquals("91D", records.get(1).tenor());
    }

    @Test
    @DisplayName("Parses multiple date formats (ISO, dd-MMM-yyyy, dd/MM/yyyy)")
    void testParseVariousDateFormats() {
        String csv = """
            Date,Tenor,Yield
            01-Jan-2024,91D,6.95
            2024-01-02,91D,6.96
            03/01/2024,91D,6.97
            """;

        List<FbilTBillRecord> records = parser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(3, records.size());
        assertEquals(LocalDate.of(2024, 1, 1), records.get(0).effectiveDate());
        assertEquals(LocalDate.of(2024, 1, 2), records.get(1).effectiveDate());
        assertEquals(LocalDate.of(2024, 1, 3), records.get(2).effectiveDate());
    }

    @Test
    @DisplayName("Rejects negative yield values with exception")
    void testRejectNegativeYield() {
        String csv = """
            Date,Tenor,Yield
            01-Jan-2024,91D,-0.05
            """;

        assertThrows(RuntimeException.class, () -> parser.parse(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Empty or null payload returns empty list without exception")
    void testEmptyPayloadReturnsEmptyList() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse(new byte[0]).isEmpty());
        assertTrue(parser.parse("   ".getBytes(StandardCharsets.UTF_8)).isEmpty());
    }

    @Test
    @DisplayName("Malformed text rows with non-numeric yields are skipped cleanly")
    void testMalformedRowsSkippedCleanly() {
        String csv = """
            Date,Tenor,Yield
            01-Jan-2024,91D,INVALID_NUMBER
            02-Jan-2024,91D,6.95
            ,,
            """;

        List<FbilTBillRecord> records = parser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, records.size());
        assertEquals(LocalDate.of(2024, 1, 2), records.get(0).effectiveDate());
    }
}
