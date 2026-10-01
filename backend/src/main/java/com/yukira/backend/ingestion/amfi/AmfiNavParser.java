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
        boolean isHistoricalReportFormat = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(payloadBytes), charset))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue; // Skip purely empty lines
                }

                // Check for header line: Scheme Code;Scheme Name;... or Scheme Code;NAV Name;Plan;Option;...
                if (trimmed.startsWith("Scheme Code;")) {
                    if (trimmed.contains("Plan") && trimmed.contains("Option")) {
                        isHistoricalReportFormat = true;
                    }
                    continue;
                }

                // Semicolon delimited
                String[] tokens = line.split(";", -1);
                if (tokens.length < 5) {
                    // Category banner or malformed line
                    // If it's a category group banner (e.g. "Open Ended Schemes ( Equity Scheme - Large Cap Fund )")
                    if (tokens.length == 1 && !isNumeric(tokens[0].trim())) {
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

                String schemeName = tokens.length > 1 ? tokens[1].trim() : "";
                String isinGrowth;
                String isinReinvestment;
                String navStr;
                String dateStr;

                if (isHistoricalReportFormat) {
                    // Format: Scheme Code;NAV Name;Plan;Option;ISIN Div Payout/ISIN Growth;ISIN Div Reinvestment;Net Asset Value;Date
                    isinGrowth = tokens.length > 4 ? tokens[4].trim() : "";
                    isinReinvestment = tokens.length > 5 ? tokens[5].trim() : "";
                    navStr = tokens.length > 6 ? tokens[6].trim() : "";
                    dateStr = tokens.length > 7 ? tokens[7].trim() : "";
                } else {
                    // Format: Scheme Code;Scheme Name;ISIN Growth;ISIN Reinv;Net Asset Value;Repurchase;Sale;Date
                    isinGrowth = tokens.length > 2 ? tokens[2].trim() : "";
                    isinReinvestment = tokens.length > 3 ? tokens[3].trim() : "";
                    navStr = tokens.length > 4 ? tokens[4].trim() : "";
                    dateStr = tokens.length > 7 ? tokens[7].trim() : (tokens.length > 5 ? tokens[tokens.length - 1].trim() : "");
                }

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
