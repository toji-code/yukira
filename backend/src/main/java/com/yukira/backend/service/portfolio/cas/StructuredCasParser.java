package com.yukira.backend.service.portfolio.cas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yukira.backend.dto.portfolio.CasImportParsedRecordDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class StructuredCasParser {

    private static final Logger log = LoggerFactory.getLogger(StructuredCasParser.class);
    public static final String PARSER_VERSION = "CAS_PARSER_V1_STRUCTURED";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CasParser.ParseResult parse(byte[] bytes, String filename) {
        if (bytes == null || bytes.length == 0) {
            return new CasParser.ParseResult(PARSER_VERSION, null, List.of(), List.of("INVALID_FILE: Input payload is empty."), "INVALID_FILE");
        }

        String fn = filename != null ? filename.toLowerCase(Locale.ROOT) : "";
        try {
            if (fn.endsWith(".json")) {
                return parseJson(bytes);
            } else if (fn.endsWith(".csv") || fn.endsWith(".txt")) {
                return parseCsv(bytes);
            } else {
                // Try JSON first, then CSV fallback
                try {
                    return parseJson(bytes);
                } catch (Exception jsonEx) {
                    return parseCsv(bytes);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse structured CAS file: {}", e.getMessage(), e);
            return new CasParser.ParseResult(PARSER_VERSION, null, List.of(), List.of("PARSE_FAILED: Error parsing structured CAS file: " + e.getMessage()), "PARSE_FAILED");
        }
    }

    private CasParser.ParseResult parseJson(byte[] bytes) throws Exception {
        JsonNode root = objectMapper.readTree(bytes);
        List<CasImportParsedRecordDto> records = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        LocalDate statementDate = null;

        if (root.has("statementDate") && !root.get("statementDate").isNull()) {
            try {
                statementDate = LocalDate.parse(root.get("statementDate").asText());
            } catch (Exception ignored) {}
        }

        JsonNode holdingsNode = root.has("holdings") ? root.get("holdings") : root.isArray() ? root : null;
        if (holdingsNode == null || !holdingsNode.isArray()) {
            return new CasParser.ParseResult(PARSER_VERSION, statementDate, List.of(), List.of("UNSUPPORTED_FORMAT: JSON payload does not contain a valid 'holdings' array."), "UNSUPPORTED_FORMAT");
        }

        for (JsonNode item : holdingsNode) {
            String rawSchemeName = item.has("rawSchemeName") ? item.get("rawSchemeName").asText() :
                                 item.has("schemeName") ? item.get("schemeName").asText() : null;
            if (rawSchemeName == null || rawSchemeName.isBlank()) {
                limitations.add("Skipped record missing scheme name.");
                continue;
            }

            String folioNumber = item.has("folioNumber") ? item.get("folioNumber").asText() : null;
            String isin = item.has("isin") ? item.get("isin").asText() : null;
            String amfiCode = item.has("amfiCode") ? item.get("amfiCode").asText() : null;
            BigDecimal units = item.has("units") && !item.get("units").isNull() ? new BigDecimal(item.get("units").asText()) : BigDecimal.ZERO;
            BigDecimal costBasis = item.has("costBasisAmount") && !item.get("costBasisAmount").isNull() ? new BigDecimal(item.get("costBasisAmount").asText()) : null;
            BigDecimal marketValue = item.has("marketValue") && !item.get("marketValue").isNull() ? new BigDecimal(item.get("marketValue").asText()) : null;
            BigDecimal nav = item.has("nav") && !item.get("nav").isNull() ? new BigDecimal(item.get("nav").asText()) : null;

            records.add(new CasImportParsedRecordDto(
                rawSchemeName.trim(), folioNumber, isin, amfiCode, units, costBasis, marketValue, nav
            ));
        }

        String status = records.isEmpty() ? "NO_HOLDINGS_FOUND" : "SUCCESS";
        return new CasParser.ParseResult(PARSER_VERSION, statementDate, records, limitations, status);
    }

    private CasParser.ParseResult parseCsv(byte[] bytes) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
        List<CasImportParsedRecordDto> records = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        LocalDate statementDate = null;

        String line;
        boolean isHeader = true;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.toLowerCase(Locale.ROOT).startsWith("statement_date") || line.toLowerCase(Locale.ROOT).startsWith("statementdate")) {
                String[] parts = line.split("[,=:]");
                if (parts.length >= 2) {
                    try { statementDate = LocalDate.parse(parts[1].trim()); } catch (Exception ignored) {}
                }
                continue;
            }

            String[] parts = line.split(",");
            if (isHeader) {
                isHeader = false;
                if (parts[0].equalsIgnoreCase("rawSchemeName") || parts[0].equalsIgnoreCase("scheme_name") || parts[0].equalsIgnoreCase("schemeName")) {
                    continue; // Skip header line
                }
            }

            if (parts.length < 2) continue;

            String rawSchemeName = parts[0].trim();
            String folioNumber = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : null;
            String isin = parts.length > 2 && !parts[2].isBlank() ? parts[2].trim() : null;
            String amfiCode = parts.length > 3 && !parts[3].isBlank() ? parts[3].trim() : null;
            BigDecimal units = parts.length > 4 && !parts[4].isBlank() ? new BigDecimal(parts[4].trim()) : BigDecimal.ZERO;
            BigDecimal costBasis = parts.length > 5 && !parts[5].isBlank() ? new BigDecimal(parts[5].trim()) : null;
            BigDecimal marketValue = parts.length > 6 && !parts[6].isBlank() ? new BigDecimal(parts[6].trim()) : null;
            BigDecimal nav = parts.length > 7 && !parts[7].isBlank() ? new BigDecimal(parts[7].trim()) : null;

            records.add(new CasImportParsedRecordDto(
                rawSchemeName, folioNumber, isin, amfiCode, units, costBasis, marketValue, nav
            ));
        }

        String status = records.isEmpty() ? "NO_HOLDINGS_FOUND" : "SUCCESS";
        return new CasParser.ParseResult(PARSER_VERSION, statementDate, records, limitations, status);
    }
}
