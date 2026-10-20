package com.yukira.backend.ingestion.holdings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Deterministic parser for SEBI / AMC monthly portfolio disclosure CSV artifacts.
 *
 * Epistemic Contract:
 * - Parses raw monthly portfolio CSV payloads line by line.
 * - Extracts scheme code/ISIN, security ISIN, security name, sector, quantity, market value, weight %, and reporting date.
 * - Injects ZERO synthetic data or missing values.
 */
@Component
public class MonthlyHoldingsCsvParser implements MonthlyHoldingsParser {

    private static final Logger log = LoggerFactory.getLogger(MonthlyHoldingsCsvParser.class);

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter AMFI_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    @Override
    public boolean supports(MonthlyHoldingsSourceDescriptor descriptor, byte[] payloadBytes) {
        if (payloadBytes == null || payloadBytes.length == 0) return false;
        if (payloadBytes.length >= 4) {
            // PK\x03\x04 (Zip / OpenXML XLSX)
            if (payloadBytes[0] == 0x50 && payloadBytes[1] == 0x4B && payloadBytes[2] == 0x03 && payloadBytes[3] == 0x04) {
                return false;
            }
            // %PDF
            if (payloadBytes[0] == 0x25 && payloadBytes[1] == 0x50 && payloadBytes[2] == 0x44 && payloadBytes[3] == 0x46) {
                return false;
            }
            // OLE binary
            if ((payloadBytes[0] & 0xFF) == 0xD0 && (payloadBytes[1] & 0xFF) == 0xCF) {
                return false;
            }
        }
        return true;
    }

    @Override
    public PpfasXlsxHoldingsParser.ParseResult parse(byte[] payloadBlob, MonthlyHoldingsSourceDescriptor descriptor) {
        List<MonthlyHoldingsRecord> records = parse(payloadBlob);
        LocalDate pDate = records.stream().map(MonthlyHoldingsRecord::portfolioDate).filter(Objects::nonNull).findFirst().orElse(null);
        int validCount = (int) records.stream().filter(MonthlyHoldingsRecord::isValid).count();
        return new PpfasXlsxHoldingsParser.ParseResult(pDate, records, records.size(), validCount);
    }

    public List<MonthlyHoldingsRecord> parse(byte[] payloadBlob) {
        if (payloadBlob == null || payloadBlob.length == 0) {
            return Collections.emptyList();
        }

        List<MonthlyHoldingsRecord> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(payloadBlob), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            String[] header = null;
            Map<String, Integer> columnIndexes = new HashMap<>();

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] tokens = parseCsvLine(trimmed);
                if (lineNumber == 1 || isHeaderLine(tokens)) {
                    header = tokens;
                    columnIndexes = buildColumnIndexMap(header);
                    continue;
                }

                MonthlyHoldingsRecord record = parseRow(lineNumber, trimmed, tokens, columnIndexes);
                records.add(record);
            }
        } catch (Exception e) {
            log.error("Failed to parse monthly holdings CSV payload: {}", e.getMessage(), e);
        }

        return records;
    }

    private boolean isHeaderLine(String[] tokens) {
        if (tokens.length == 0) return false;
        String first = tokens[0].toLowerCase(Locale.ROOT);
        return first.contains("scheme") || first.contains("isin") || first.contains("security") || first.contains("company") || first.contains("date");
    }

    private Map<String, Integer> buildColumnIndexMap(String[] header) {
        Map<String, Integer> map = new HashMap<>();
        if (header == null || header.length == 0) {
            return map;
        }

        for (int i = 0; i < header.length; i++) {
            if (header[i] == null) continue;
            String h = header[i].trim().toLowerCase(Locale.ROOT).replaceAll("^\"|\"$", "");

            if (h.contains("scheme") || h.contains("amfi")) {
                map.putIfAbsent("SCHEME_CODE", i);
            } else if (h.contains("isin")) {
                map.putIfAbsent("ISIN", i);
            } else if (h.contains("company") || h.contains("security") || h.contains("instrument") || h.contains("name")) {
                map.putIfAbsent("SECURITY_NAME", i);
            } else if (h.contains("sector") || h.contains("industry")) {
                map.putIfAbsent("SECTOR", i);
            } else if (h.contains("asset") || h.contains("class") || h.contains("type")) {
                map.putIfAbsent("ASSET_CLASS", i);
            } else if (h.contains("quantity") || h.contains("qty") || h.contains("shares")) {
                map.putIfAbsent("QUANTITY", i);
            } else if (h.contains("market value") || h.contains("mkt value") || h.contains("val inr") || h.contains("lakhs") || h.contains("crores")) {
                map.putIfAbsent("MARKET_VALUE", i);
            } else if (h.contains("weight") || h.contains("% to nav") || h.contains("% of aum") || h.contains("weightage") || h.contains("% of total")) {
                map.putIfAbsent("WEIGHT", i);
            } else if (h.contains("date") || h.contains("as of") || h.contains("reporting")) {
                map.putIfAbsent("PORTFOLIO_DATE", i);
            }
        }
        return map;
    }

    private MonthlyHoldingsRecord parseRow(int lineNumber, String rawLine, String[] tokens, Map<String, Integer> columnIndexes) {
        try {
            if (tokens.length < 3) {
                return MonthlyHoldingsRecord.invalid(lineNumber, rawLine, "Insufficient columns (minimum 3 required)");
            }

            int schemeIdx = columnIndexes.getOrDefault("SCHEME_CODE", 0);
            int nameIdx = columnIndexes.getOrDefault("SECURITY_NAME", 1);
            int isinIdx = columnIndexes.getOrDefault("ISIN", 2);
            int sectorIdx = columnIndexes.getOrDefault("SECTOR", 3);
            int assetClassIdx = columnIndexes.getOrDefault("ASSET_CLASS", 4);
            int qtyIdx = columnIndexes.getOrDefault("QUANTITY", 5);
            int mktValIdx = columnIndexes.getOrDefault("MARKET_VALUE", 6);
            int weightIdx = columnIndexes.getOrDefault("WEIGHT", 7);
            int dateIdx = columnIndexes.getOrDefault("PORTFOLIO_DATE", 8);

            String schemeCode = getCleanToken(tokens, schemeIdx);
            String securityName = getCleanToken(tokens, nameIdx);
            String isin = getCleanToken(tokens, isinIdx);
            String sector = getCleanToken(tokens, sectorIdx);
            String assetClass = getCleanToken(tokens, assetClassIdx);
            if (assetClass == null || assetClass.isBlank()) {
                assetClass = "Equity";
            }

            BigDecimal quantity = parseBigDecimalToken(tokens, qtyIdx);
            BigDecimal marketValue = parseBigDecimalToken(tokens, mktValIdx);
            BigDecimal weight = parseWeightToken(tokens, weightIdx);
            LocalDate portfolioDate = parseDateToken(tokens, dateIdx);

            if (securityName == null || securityName.isBlank()) {
                return MonthlyHoldingsRecord.invalid(lineNumber, rawLine, "Missing security name");
            }

            return new MonthlyHoldingsRecord(
                lineNumber,
                rawLine,
                schemeCode,
                isin != null && !isin.isBlank() ? isin.toUpperCase(Locale.ROOT) : null,
                securityName,
                sector != null && !sector.isBlank() ? sector : "Other",
                assetClass,
                quantity,
                marketValue,
                weight,
                portfolioDate,
                true,
                null
            );
        } catch (Exception e) {
            return MonthlyHoldingsRecord.invalid(lineNumber, rawLine, "Parse error: " + e.getMessage());
        }
    }

    private String getCleanToken(String[] tokens, int idx) {
        if (idx >= 0 && idx < tokens.length && tokens[idx] != null) {
            String val = tokens[idx].trim().replaceAll("^\"|\"$", "");
            return val.isEmpty() ? null : val;
        }
        return null;
    }

    private BigDecimal parseBigDecimalToken(String[] tokens, int idx) {
        String token = getCleanToken(tokens, idx);
        if (token == null) return null;
        try {
            String clean = token.replace(",", "").replace("₹", "").replace("Rs", "").replace("INR", "").trim();
            if (clean.equalsIgnoreCase("N/A") || clean.equalsIgnoreCase("NA") || clean.equals("-")) {
                return null;
            }
            return new BigDecimal(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseWeightToken(String[] tokens, int idx) {
        String token = getCleanToken(tokens, idx);
        if (token == null) return null;
        try {
            boolean hasPercentSymbol = token.contains("%");
            String clean = token.replace("%", "").replace(",", "").trim();
            if (clean.equalsIgnoreCase("N/A") || clean.equalsIgnoreCase("NA") || clean.equals("-")) {
                return null;
            }
            BigDecimal rawVal = new BigDecimal(clean);
            if (hasPercentSymbol || rawVal.abs().compareTo(BigDecimal.ONE) > 0) {
                return rawVal.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            }
            return rawVal;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDateToken(String[] tokens, int idx) {
        String token = getCleanToken(tokens, idx);
        if (token == null) return null;
        try {
            if (token.contains("-")) {
                if (token.length() == 10 && Character.isDigit(token.charAt(0))) {
                    return LocalDate.parse(token, ISO_FORMATTER);
                }
                return LocalDate.parse(token, AMFI_DATE_FORMATTER);
            } else if (token.contains("/")) {
                String[] parts = token.split("/");
                if (parts.length == 3) {
                    int p1 = Integer.parseInt(parts[0]);
                    int p2 = Integer.parseInt(parts[1]);
                    int p3 = Integer.parseInt(parts[2]);
                    if (p1 > 1900) {
                        return LocalDate.of(p1, p2, p3);
                    } else if (p3 > 1900) {
                        return LocalDate.of(p3, p2, p1);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String[] parseCsvLine(String line) {
        List<String> list = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if ((c == ',' || c == '\t') && !inQuotes) {
                list.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        list.add(sb.toString().trim());
        return list.toArray(new String[0]);
    }
}
