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
     * Audit record capturing parser accounting for deterministic verification.
     */
    public record NiftyParseResult(
        List<NiftyTriRecord> records,
        int rowsReceived,
        int rowsAccepted,
        int rowsRejected,
        LocalDate minEffectiveDate,
        LocalDate maxEffectiveDate
    ) {}

    private static class AuditState {
        int received = 0;
        int accepted = 0;
        int rejected = 0;
        LocalDate minDate = null;
        LocalDate maxDate = null;

        void recordDate(LocalDate d) {
            if (minDate == null || d.isBefore(minDate)) {
                minDate = d;
            }
            if (maxDate == null || d.isAfter(maxDate)) {
                maxDate = d;
            }
        }
    }

    /**
     * Parses raw NSE Indices Total Returns Index (TRI) JSON payload deterministically.
     */
    public List<NiftyTriRecord> parse(byte[] payloadBytes) {
        return parseWithAudit(payloadBytes).records();
    }

    /**
     * Parses raw NSE Indices TRI payload with full accounting of rows received, accepted, and rejected.
     */
    public NiftyParseResult parseWithAudit(byte[] payloadBytes) {
        if (payloadBytes == null || payloadBytes.length == 0) {
            throw new IllegalArgumentException("payloadBytes must not be empty");
        }

        String rawContent = new String(payloadBytes, StandardCharsets.UTF_8).trim();
        if (rawContent.isEmpty()) {
            throw new IllegalArgumentException("payload content must not be blank");
        }

        if (rawContent.startsWith("<") || rawContent.startsWith("<!DOCTYPE") || rawContent.startsWith("<html")) {
            throw new IllegalArgumentException("Unexpected HTML/XML response from NSE endpoint: " +
                rawContent.substring(0, Math.min(100, rawContent.length())));
        }

        List<NiftyTriRecord> records = new ArrayList<>();
        AuditState state = new AuditState();

        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawContent);
            if (root.isArray()) {
                state.received = root.size();
                for (JsonNode node : root) {
                    processJsonNode(node, records, state);
                }
            } else if (root.isObject()) {
                // In case the response is wrapped inside {"d": "[...]"}
                if (root.has("d")) {
                    String dStr = root.get("d").asText();
                    if (dStr != null && dStr.startsWith("[")) {
                        JsonNode innerArray = OBJECT_MAPPER.readTree(dStr);
                        if (innerArray.isArray()) {
                            state.received = innerArray.size();
                            for (JsonNode node : innerArray) {
                                processJsonNode(node, records, state);
                            }
                        } else {
                            throw new IllegalArgumentException("Wrapped 'd' field did not contain a valid JSON array");
                        }
                    } else if (dStr == null || dStr.isBlank() || "null".equalsIgnoreCase(dStr)) {
                        // Empty wrapped payload
                        state.received = 0;
                    } else {
                        throw new IllegalArgumentException("Malformed 'd' payload from NSE Indices: " + dStr.substring(0, Math.min(80, dStr.length())));
                    }
                } else {
                    state.received = 1;
                    processJsonNode(root, records, state);
                }
            } else {
                throw new IllegalArgumentException("Payload root is neither JSON array nor JSON object");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Nifty TRI JSON payload: " + e.getMessage(), e);
        }

        return new NiftyParseResult(
            records,
            state.received,
            state.accepted,
            state.rejected,
            state.minDate,
            state.maxDate
        );
    }

    private void processJsonNode(JsonNode node, List<NiftyTriRecord> records, AuditState state) {
        // 1. Date resolution
        JsonNode dateNode = node.get("Date");
        if (dateNode == null || dateNode.isNull()) {
            dateNode = node.get("HistoricalDate");
        }
        if (dateNode == null || dateNode.isNull()) {
            state.rejected++;
            return;
        }

        String dateStr = dateNode.asText().trim();
        LocalDate effectiveDate = parseDate(dateStr);
        if (effectiveDate == null) {
            state.rejected++;
            return;
        }

        // 2. Total Returns Index level resolution (Strict TRI verification: never fall back to PRI/Close)
        JsonNode triNode = node.get("TotalReturnsIndex");
        if (triNode == null || triNode.isNull()) {
            triNode = node.get("TotalReturnIndex");
        }
        if (triNode == null || triNode.isNull()) {
            state.rejected++;
            return;
        }

        String triStr = triNode.asText().trim().replace(",", "");
        if (triStr.isEmpty() || triStr.equalsIgnoreCase("null") || triStr.equalsIgnoreCase("nan") || triStr.equalsIgnoreCase("-")) {
            state.rejected++;
            return;
        }

        BigDecimal triLevel;
        try {
            triLevel = new BigDecimal(triStr).setScale(8, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            state.rejected++;
            return;
        }

        if (triLevel.compareTo(BigDecimal.ZERO) <= 0) {
            state.rejected++;
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
        state.accepted++;
        state.recordDate(effectiveDate);
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
