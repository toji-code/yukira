package com.yukira.backend.ingestion.fbil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class FbilTBillParser {

    private static final ZoneOffset IST_OFFSET = ZoneOffset.ofHoursMinutes(5, 30);
    private static final LocalTime DEFAULT_EOD_TIME = LocalTime.of(17, 30);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[] {
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd HH:mm:ss").toFormatter(Locale.ENGLISH),
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy HH:mm:ss").toFormatter(Locale.ENGLISH),
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.ENGLISH),
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("yyyy-MM-dd").toFormatter(Locale.ENGLISH),
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd/MM/yyyy").toFormatter(Locale.ENGLISH),
        new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MM-yyyy").toFormatter(Locale.ENGLISH)
    };

    /**
     * Parses raw FBIL Treasury Bill benchmark yield payload deterministically.
     * Supports both official FBIL JSON response array and delimited CSV/text.
     * Extracts strictly 91-Day / 3-Month Treasury Bill cutoff yield observations.
     */
    public List<FbilTBillRecord> parse(byte[] payloadBytes) {
        List<FbilTBillRecord> records = new ArrayList<>();
        if (payloadBytes == null || payloadBytes.length == 0) {
            return records;
        }

        String rawContent = new String(payloadBytes, StandardCharsets.UTF_8).trim();
        if (rawContent.isEmpty()) {
            return records;
        }

        if (rawContent.startsWith("[") || rawContent.startsWith("{")) {
            return parseJson(rawContent);
        } else {
            return parseCsv(payloadBytes);
        }
    }

    private List<FbilTBillRecord> parseJson(String jsonContent) {
        List<FbilTBillRecord> records = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonContent);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    processJsonNode(node, records);
                }
            } else if (root.isObject()) {
                processJsonNode(root, records);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse FBIL T-Bill JSON payload: " + e.getMessage(), e);
        }
        return records;
    }

    private void processJsonNode(JsonNode node, List<FbilTBillRecord> records) {
        // 1. Tenor filtering
        JsonNode tenorNode = node.get("tenorName");
        if (tenorNode == null || tenorNode.isNull()) {
            tenorNode = node.get("tenor");
        }
        if (tenorNode == null || tenorNode.isNull()) {
            return;
        }
        String tenorStr = tenorNode.asText().trim();
        if (!isApproved91DTenor(tenorStr)) {
            return; // Skip non-91D/3M tenors
        }

        // 2. Parse Effective Date
        JsonNode dateNode = node.get("processRunDate");
        if (dateNode == null || dateNode.isNull()) {
            dateNode = node.get("effective_date");
        }
        if (dateNode == null || dateNode.isNull()) {
            dateNode = node.get("date");
        }
        if (dateNode == null || dateNode.isNull()) {
            return;
        }
        LocalDate effectiveDate = parseDate(dateNode.asText().trim());
        if (effectiveDate == null) {
            return;
        }

        // 3. Parse Yield Rate
        JsonNode rateNode = node.get("rate");
        if (rateNode == null || rateNode.isNull()) {
            rateNode = node.get("yield");
        }
        if (rateNode == null || rateNode.isNull()) {
            rateNode = node.get("quoted_yield");
        }
        if (rateNode == null || rateNode.isNull()) {
            return;
        }

        String rateStr = rateNode.asText().trim().replace("%", "");
        if (rateStr.isEmpty() || rateStr.equalsIgnoreCase("NA") || rateStr.equalsIgnoreCase("NULL")) {
            return;
        }

        BigDecimal rawYield;
        try {
            rawYield = new BigDecimal(rateStr);
        } catch (NumberFormatException e) {
            return;
        }

        if (rawYield.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Negative quoted yield is invalid: " + rawYield);
        }

        // Normalize: percent (e.g. 6.86 -> 0.06860000) vs decimal (0.0686)
        BigDecimal normalizedYield;
        if (rawYield.compareTo(new BigDecimal("0.50")) > 0) {
            normalizedYield = rawYield.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
        } else {
            normalizedYield = rawYield.setScale(8, RoundingMode.HALF_UP);
        }

        // 4. Parse Availability Timestamp
        OffsetDateTime availabilityTime = null;
        JsonNode timeNode = node.get("displayTime");
        if (timeNode == null || timeNode.isNull()) {
            timeNode = node.get("time");
        }
        if (timeNode == null || timeNode.isNull()) {
            timeNode = node.get("availability_time");
        }

        if (timeNode != null && !timeNode.isNull()) {
            String timeStr = timeNode.asText().trim();
            if (!timeStr.isEmpty()) {
                availabilityTime = parseTimestamp(timeStr, effectiveDate);
            }
        }

        if (availabilityTime == null) {
            availabilityTime = effectiveDate.atTime(DEFAULT_EOD_TIME).atOffset(IST_OFFSET);
        }

        records.add(new FbilTBillRecord(
            effectiveDate,
            "FBIL_91D_TBILL",
            tenorStr,
            normalizedYield,
            "ACT_365",
            availabilityTime,
            1
        ));
    }

    private List<FbilTBillRecord> parseCsv(byte[] payloadBytes) {
        List<FbilTBillRecord> records = new ArrayList<>();
        Charset charset = StandardCharsets.UTF_8;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(payloadBytes), charset))) {
            String line;
            int lineNumber = 0;
            int dateCol = 0;
            int tenorCol = 1;
            int yieldCol = 2;
            int timeCol = -1;
            boolean hasHeader = false;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                    continue;
                }

                // Delimiter auto-detection: comma or semicolon
                String delimiter = trimmed.contains(";") ? ";" : ",";
                String[] tokens = trimmed.split(delimiter, -1);

                // Check header
                if (!hasHeader && (tokens[0].equalsIgnoreCase("date") || tokens[0].equalsIgnoreCase("effective_date"))) {
                    hasHeader = true;
                    for (int i = 0; i < tokens.length; i++) {
                        String col = tokens[i].trim().toLowerCase(Locale.ENGLISH);
                        if (col.contains("date")) dateCol = i;
                        else if (col.contains("tenor") || col.contains("maturity") || col.contains("benchmark")) tenorCol = i;
                        else if (col.contains("yield") || col.contains("rate") || col.contains("cutoff") || col.contains("value")) yieldCol = i;
                        else if (col.contains("time") || col.contains("availability")) timeCol = i;
                    }
                    continue;
                }

                if (tokens.length < 2) {
                    continue;
                }

                // If only 2 columns: assume Date, Yield (for 91D dedicated feed)
                if (tokens.length == 2 && tenorCol == 1 && yieldCol == 2) {
                    yieldCol = 1;
                    tenorCol = -1;
                }

                String dateStr = tokens[dateCol].trim().replace("\"", "");
                LocalDate effectiveDate = parseDate(dateStr);
                if (effectiveDate == null) {
                    continue; // Skip unparseable row
                }

                // Check tenor if tenor column exists
                String tenor = "91D";
                if (tenorCol >= 0 && tenorCol < tokens.length) {
                    String rawTenor = tokens[tenorCol].trim().replace("\"", "");
                    if (!rawTenor.isEmpty()) {
                        if (!isApproved91DTenor(rawTenor)) {
                            continue; // Skip non-91D tenors if multiple tenors are in file
                        }
                        tenor = rawTenor;
                    }
                }

                // Parse yield value
                if (yieldCol >= tokens.length) {
                    continue;
                }

                String yieldStr = tokens[yieldCol].trim().replace("\"", "").replace("%", "");
                if (yieldStr.isEmpty() || yieldStr.equalsIgnoreCase("NA") || yieldStr.equalsIgnoreCase("NULL")) {
                    continue;
                }

                BigDecimal rawYield;
                try {
                    rawYield = new BigDecimal(yieldStr);
                } catch (NumberFormatException e) {
                    continue;
                }

                if (rawYield.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Negative quoted yield is invalid on line " + lineNumber + ": " + rawYield);
                }

                // Normalize: if reported as percentage (e.g. 6.95% -> 0.06950000)
                BigDecimal normalizedYield;
                if (rawYield.compareTo(new BigDecimal("0.50")) > 0) {
                    normalizedYield = rawYield.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
                } else {
                    normalizedYield = rawYield.setScale(8, RoundingMode.HALF_UP);
                }

                // Determine availability time
                OffsetDateTime availabilityTime = null;
                if (timeCol >= 0 && timeCol < tokens.length) {
                    String timeStr = tokens[timeCol].trim().replace("\"", "");
                    if (!timeStr.isEmpty()) {
                        availabilityTime = parseTimestamp(timeStr, effectiveDate);
                    }
                }

                if (availabilityTime == null) {
                    availabilityTime = effectiveDate.atTime(DEFAULT_EOD_TIME).atOffset(IST_OFFSET);
                }

                records.add(new FbilTBillRecord(
                    effectiveDate,
                    "FBIL_91D_TBILL",
                    tenor,
                    normalizedYield,
                    "ACT_365",
                    availabilityTime,
                    1
                ));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse FBIL T-Bill payload: " + e.getMessage(), e);
        }

        return records;
    }

    public static boolean isApproved91DTenor(String tenorStr) {
        if (tenorStr == null || tenorStr.isBlank()) return false;
        String norm = tenorStr.trim().toUpperCase(Locale.ENGLISH);
        return norm.equals("3 MONTHS")
            || norm.equals("3 MONTH")
            || norm.equals("3M")
            || norm.equals("3-MONTH")
            || norm.equals("91D")
            || norm.equals("91 DAYS")
            || norm.equals("91-DAY")
            || norm.equals("91 DAY")
            || norm.equals("FBIL_91D_TBILL")
            || norm.equals("91 D");
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private OffsetDateTime parseTimestamp(String timeStr, LocalDate effectiveDate) {
        if (timeStr == null || timeStr.isBlank()) return null;
        try {
            return OffsetDateTime.parse(timeStr);
        } catch (Exception ignored) {}

        for (String pattern : new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "dd-MMM-yyyy HH:mm:ss", "dd/MM/yyyy HH:mm:ss"}) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
                return ldt.atOffset(IST_OFFSET);
            } catch (Exception ignored) {}
        }

        try {
            LocalTime lt = LocalTime.parse(timeStr);
            return effectiveDate.atTime(lt).atOffset(IST_OFFSET);
        } catch (Exception ignored) {}

        return null;
    }
}
