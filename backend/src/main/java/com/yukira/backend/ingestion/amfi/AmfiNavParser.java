package com.yukira.backend.ingestion.amfi;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class AmfiNavParser {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("dd-MMM-yyyy")
        .toFormatter(Locale.ENGLISH);

    /**
     * Parses raw AMFI historical NAV payload bytes deterministically.
     * Supports Windows-1252 with fallback to UTF-8.
     * Never silently coercing or discarding malformed rows.
     */
    public List<AmfiNavRecord> parse(byte[] payloadBytes) {
        List<AmfiNavRecord> records = new ArrayList<>();
        if (payloadBytes == null || payloadBytes.length == 0) {
            return records;
        }

        Charset charset = WINDOWS_1252;
        int schemeCodeCol = 0;
        int schemeNameCol = 1;
        int isinGrowthCol = 4;
        int isinReinvCol = 5;
        int navCol = 6;
        int dateCol = 7;
        boolean headerDetected = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(payloadBytes), charset))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue; // Skip purely empty lines
                }

                // Check for header line: Scheme Code;...
                if (trimmed.toLowerCase().startsWith("scheme code;") || trimmed.toLowerCase().startsWith("scheme code ;")) {
                    String[] headerTokens = trimmed.split(";", -1);
                    for (int i = 0; i < headerTokens.length; i++) {
                        String h = headerTokens[i].toLowerCase().trim();
                        if (h.contains("scheme code")) {
                            schemeCodeCol = i;
                        } else if (h.contains("scheme name") || h.contains("nav name")) {
                            schemeNameCol = i;
                        } else if (h.contains("isin div payout") || h.contains("isin growth") || (h.contains("isin") && !h.contains("reinvest"))) {
                            isinGrowthCol = i;
                        } else if (h.contains("isin div reinvest") || (h.contains("isin") && h.contains("reinvest"))) {
                            isinReinvCol = i;
                        } else if (h.contains("net asset value") || h.equals("nav")) {
                            navCol = i;
                        } else if (h.contains("date")) {
                            dateCol = i;
                        }
                    }
                    headerDetected = true;
                    continue;
                }

                // Semicolon delimited
                String[] tokens = line.split(";", -1);
                if (tokens.length < 5) {
                    // Category banner or non-numeric header line
                    if (!isNumeric(tokens[0].trim())) {
                        continue; // Structural category banner, not a NAV row
                    }
                    records.add(AmfiNavRecord.malformed(lineNumber, line, "Malformed row: expected at least 5 tokens, found " + tokens.length));
                    continue;
                }

                String schemeCode = tokens[0].trim();
                if (!isNumeric(schemeCode)) {
                    // Non-numeric scheme code on delimited row -> malformed or unexpected header
                    records.add(AmfiNavRecord.malformed(lineNumber, line, "Malformed row: non-numeric Scheme Code: '" + schemeCode + "'"));
                    continue;
                }

                String schemeName = (schemeNameCol < tokens.length) ? tokens[schemeNameCol].trim() : "";
                String isinGrowth = (isinGrowthCol < tokens.length) ? tokens[isinGrowthCol].trim() : "";
                String isinReinvestment = (isinReinvCol < tokens.length) ? tokens[isinReinvCol].trim() : "";
                String navStr = (navCol < tokens.length) ? tokens[navCol].trim() : "";
                String dateStr = (dateCol < tokens.length) ? tokens[dateCol].trim() : (tokens.length > 5 ? tokens[tokens.length - 1].trim() : "");

                // Validate NAV numeric
                BigDecimal navValue;
                try {
                    navValue = new BigDecimal(navStr);
                } catch (Exception e) {
                    records.add(AmfiNavRecord.malformed(lineNumber, line, "Invalid numeric NAV: '" + navStr + "'"));
                    continue;
                }

                // Validate date
                LocalDate navDate;
                try {
                    navDate = LocalDate.parse(dateStr, DATE_FORMATTER);
                } catch (Exception e) {
                    records.add(AmfiNavRecord.malformed(lineNumber, line, "Invalid date format: '" + dateStr + "'"));
                    continue;
                }

                records.add(AmfiNavRecord.valid(
                    lineNumber, line, schemeCode, schemeName, isinGrowth, isinReinvestment, navValue, navDate
                ));
            }
        } catch (Exception e) {
            records.add(AmfiNavRecord.malformed(0, "", "Fatal parsing exception: " + e.getMessage()));
        }

        return records;
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) return false;
        }
        return true;
    }
}
