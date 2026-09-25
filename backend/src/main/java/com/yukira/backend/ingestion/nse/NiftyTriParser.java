package com.yukira.backend.ingestion.nse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class NiftyTriParser {

    private static final ZoneOffset IST_OFFSET = ZoneOffset.ofHoursMinutes(5, 30);
    private static final LocalTime DEFAULT_EOD_TIME = LocalTime.of(18, 0); // 18:00 IST EOD index publication
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[] {
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd MMM yyyy").toFormatter(Locale.ENGLISH),
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.ENGLISH),
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd").toFormatter(Locale.ENGLISH),
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd/MM/yyyy").toFormatter(Locale.ENGLISH),
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MM-yyyy").toFormatter(Locale.ENGLISH)
    };

    /**
     * Parses raw NSE Indices Total Returns Index (TRI) JSON payload deterministically.
     */
    public List<NiftyTriRecord> parse(byte[] payloadBytes) {
        List<NiftyTriRecord> records = new ArrayList<>();
        if (payloadBytes == null || payloadBytes.length == 0) {
            return records;
        }

        String rawContent = new String(payloadBytes, StandardCharsets.UTF_8).trim();
        if (rawContent.isEmpty()) {
            return records;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawContent);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    processJsonNode(node, records);
                }
            } else if (root.isObject()) {
                // In case the response is wrapped inside {"d": "[...]"}
                if (root.has("d")) {
                    String dStr = root.get("d").asText();
                    if (dStr != null && dStr.startsWith("[")) {
                        JsonNode innerArray = OBJECT_MAPPER.readTree(dStr);
                        if (innerArray.isArray()) {
                            for (JsonNode node : innerArray) {
                                processJsonNode(node, records);
                            }
                        }
                    }
                } else {
                    processJsonNode(root, records);
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Nifty TRI JSON payload: " + e.getMessage(), e);
        }

        return records;
    }

    private void processJsonNode(JsonNode node, List<NiftyTriRecord> records) {
        // 1. Date resolution
        JsonNode dateNode = node.get("Date");
        if (dateNode == null || dateNode.isNull()) {
            dateNode = node.get("HistoricalDate");
        }
        if (dateNode == null || dateNode.isNull()) {
            return;
        }

        String dateStr = dateNode.asText().trim();
        LocalDate effectiveDate = parseDate(dateStr);
        if (effectiveDate == null) {
            return;
        }

        // 2. Total Returns Index level resolution
        JsonNode triNode = node.get("TotalReturnsIndex");
        if (triNode == null || triNode.isNull()) {
            triNode = node.get("TotalReturnIndex");
        }
        if (triNode == null || triNode.isNull()) {
            triNode = node.get("Close");
        }
        if (triNode == null || triNode.isNull()) {
            return;
        }

        String triStr = triNode.asText().trim().replace(",", "");
        if (triStr.isEmpty() || triStr.equalsIgnoreCase("null") || triStr.equalsIgnoreCase("nan") || triStr.equalsIgnoreCase("-")) {
            return;
        }

        BigDecimal triLevel;
        try {
            triLevel = new BigDecimal(triStr).setScale(8, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return;
        }

        if (triLevel.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // 3. Index Name
        JsonNode nameNode = node.get("Index Name");
        if (nameNode == null || nameNode.isNull()) {
            nameNode = node.get("INDEX_NAME");
        }
        String indexName = (nameNode != null && !nameNode.isNull()) ? nameNode.asText().trim() : "Nifty 500";

        // 4. Availability time: Effective date at 18:00 IST
        OffsetDateTime availabilityTime = effectiveDate.atTime(DEFAULT_EOD_TIME).atOffset(IST_OFFSET);

        records.add(new NiftyTriRecord(
            effectiveDate,
            indexName,
            triLevel,
            availabilityTime,
            1
        ));
    }

    private LocalDate parseDate(String dateStr) {
        for (DateTimeFormatter dtf : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, dtf);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
