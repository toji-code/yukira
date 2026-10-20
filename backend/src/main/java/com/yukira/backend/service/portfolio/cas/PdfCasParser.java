package com.yukira.backend.service.portfolio.cas;

import com.yukira.backend.dto.portfolio.CasImportParsedRecordDto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PdfCasParser {

    private static final Logger log = LoggerFactory.getLogger(PdfCasParser.class);
    public static final String PARSER_VERSION_GENERIC = "CAS_PARSER_V2_PDF_DETERMINISTIC";
    public static final String PARSER_VERSION_CAMS = "CAS_PARSER_V2_CAMS_PDF";
    public static final String PARSER_VERSION_KFIN = "CAS_PARSER_V2_KFINTECH_PDF";

    public CasParser.ParseResult parsePdf(byte[] pdfBytes, String password) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return new CasParser.ParseResult(PARSER_VERSION_GENERIC, null, List.of(), List.of("INVALID_FILE: Empty PDF payload."), "INVALID_FILE", "UNKNOWN");
        }

        // Validate PDF Magic Bytes (%PDF-)
        if (pdfBytes.length < 5 || pdfBytes[0] != 0x25 || pdfBytes[1] != 0x50 || pdfBytes[2] != 0x44 || pdfBytes[3] != 0x46) {
            return new CasParser.ParseResult(PARSER_VERSION_GENERIC, null, List.of(), List.of("INVALID_FILE: Payload does not contain valid PDF magic bytes."), "INVALID_FILE", "UNKNOWN");
        }

        PDDocument document = null;
        try {
            String safePassword = password != null ? password.trim() : "";
            document = Loader.loadPDF(pdfBytes, safePassword);
        } catch (InvalidPasswordException e) {
            if (password == null || password.isBlank()) {
                return new CasParser.ParseResult(PARSER_VERSION_GENERIC, null, List.of(), List.of("PASSWORD_REQUIRED: CAS PDF is encrypted. Password required to parse."), "PASSWORD_REQUIRED", "UNKNOWN");
            } else {
                return new CasParser.ParseResult(PARSER_VERSION_GENERIC, null, List.of(), List.of("INVALID_PASSWORD: Incorrect password provided for encrypted CAS PDF."), "INVALID_PASSWORD", "UNKNOWN");
            }
        } catch (Exception e) {
            log.error("Failed to load PDF document: {}", e.getMessage());
            return new CasParser.ParseResult(PARSER_VERSION_GENERIC, null, List.of(), List.of("PARSE_FAILED: Error opening PDF document: " + e.getMessage()), "PARSE_FAILED", "UNKNOWN");
        }

        try {
            PDFTextStripper stripper = new PDFTextStripper();
            String extractedText = stripper.getText(document);

            if (extractedText == null || extractedText.isBlank()) {
                return new CasParser.ParseResult(PARSER_VERSION_GENERIC, null, List.of(), List.of("NO_TEXT_CONTENT: PDF contains no extractable text."), "NO_TEXT_CONTENT", "UNKNOWN");
            }

            // Template / Provider Recognition (CAMS vs KFintech / Karvy vs Generic CAS)
            String upperText = extractedText.toUpperCase(Locale.ROOT);
            boolean isCams = upperText.contains("CAMS") || upperText.contains("COMPUTER AGE MANAGEMENT") || upperText.contains("CAMSONLINE");
            boolean isKfin = upperText.contains("KFIN") || upperText.contains("KARVY") || upperText.contains("KFINTECH");
            boolean isCasStatement = upperText.contains("CONSOLIDATED ACCOUNT STATEMENT") || (upperText.contains("FOLIO") && (upperText.contains("ISIN") || upperText.contains("AMFI") || upperText.contains("CLOSING BALANCE") || upperText.contains("VALUATION")));

            if (!isCams && !isKfin && !isCasStatement) {
                return new CasParser.ParseResult(PARSER_VERSION_GENERIC, null, List.of(), List.of("UNSUPPORTED_CAS_TEMPLATE: PDF document does not match CAMS / KFintech CAS structural markers."), "UNSUPPORTED_CAS_TEMPLATE", "UNKNOWN");
            }

            String providerTag = isCams ? "CAMS" : isKfin ? "KFINTECH" : "UNKNOWN";
            String parserVersion = isCams ? PARSER_VERSION_CAMS : isKfin ? PARSER_VERSION_KFIN : PARSER_VERSION_GENERIC;

            LocalDate statementDate = extractStatementDate(extractedText);
            List<CasImportParsedRecordDto> records = extractHoldingsFromText(extractedText);

            if (records.isEmpty()) {
                return new CasParser.ParseResult(parserVersion, statementDate, List.of(), List.of("NO_HOLDINGS_FOUND: Statement parsed successfully, but zero mutual fund holdings were recognized."), "NO_HOLDINGS_FOUND", providerTag);
            }

            List<String> limitations = new ArrayList<>();
            limitations.add("Parsed via " + providerTag + " deterministic PDF parser engine (" + parserVersion + ").");

            return new CasParser.ParseResult(parserVersion, statementDate, records, limitations, "SUCCESS", providerTag);

        } catch (Exception e) {
            log.error("Error during CAS PDF text extraction: {}", e.getMessage(), e);
            return new CasParser.ParseResult(PARSER_VERSION_GENERIC, null, List.of(), List.of("PARSE_FAILED: Failed to parse PDF CAS content: " + e.getMessage()), "PARSE_FAILED", "UNKNOWN");
        } finally {
            if (document != null) {
                try { document.close(); } catch (Exception ignored) {}
            }
        }
    }

    private LocalDate extractStatementDate(String text) {
        Pattern p1 = Pattern.compile("(?:Statement Date|Valuation as on|As on|Date)\\s*[:\\-]?\\s*(\\d{1,2}[\\-/\\s][A-Za-z]{3}[\\-/\\s]\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            LocalDate d = parseDateString(m1.group(1));
            if (d != null) return d;
        }

        Pattern p2 = Pattern.compile("To\\s+(\\d{1,2}[\\-/\\s][A-Za-z]{3}[\\-/\\s]\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            LocalDate d = parseDateString(m2.group(1));
            if (d != null) return d;
        }

        Pattern p3 = Pattern.compile("(\\d{4}\\-\\d{2}\\-\\d{2})");
        Matcher m3 = p3.matcher(text);
        if (m3.find()) {
            try { return LocalDate.parse(m3.group(1)); } catch (Exception ignored) {}
        }

        return LocalDate.now();
    }

    private LocalDate parseDateString(String str) {
        if (str == null) return null;
        str = str.trim().replaceAll("[\\/\\.]", "-");
        List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
        );

        for (DateTimeFormatter fmt : formatters) {
            try { return LocalDate.parse(str, fmt); } catch (Exception ignored) {}
        }
        return null;
    }

    private List<CasImportParsedRecordDto> extractHoldingsFromText(String text) {
        List<CasImportParsedRecordDto> records = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        String currentFolio = null;
        String currentSchemeName = null;
        String currentIsin = null;
        String currentAmfi = null;
        BigDecimal currentUnits = null;
        BigDecimal currentCostBasis = null;
        BigDecimal currentMarketValue = null;
        BigDecimal currentNav = null;

        Pattern isinPattern = Pattern.compile("(INF[A-Za-z0-9]{9})");
        Pattern amfiPattern = Pattern.compile("(?:AMFI|Scheme Code|Code)\\s*[:\\-]?\\s*(\\d{6})", Pattern.CASE_INSENSITIVE);
        Pattern folioPattern = Pattern.compile("(?:Folio\\s*(?:No|Number)?\\s*[:\\-]?\\s*)([0-9\\/A-Za-z\\-]+)", Pattern.CASE_INSENSITIVE);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // Skip page headers, repeated titles, subtotals, and disclaimers
            if (isHeaderOrSubtotalLine(line)) {
                continue;
            }

            // Check for Folio line
            Matcher folioMatcher = folioPattern.matcher(line);
            if (folioMatcher.find()) {
                currentFolio = folioMatcher.group(1).trim();
            }

            // Check for ISIN
            Matcher isinMatcher = isinPattern.matcher(line);
            if (isinMatcher.find()) {
                currentIsin = isinMatcher.group(1);
            }

            // Check for AMFI
            Matcher amfiMatcher = amfiPattern.matcher(line);
            if (amfiMatcher.find()) {
                currentAmfi = amfiMatcher.group(1);
            }

            // Detect Scheme Name Line
            if (isSchemeNameLine(line)) {
                // Flush pending completed record
                if (currentSchemeName != null && (currentUnits != null || currentMarketValue != null || currentIsin != null)) {
                    records.add(new CasImportParsedRecordDto(
                        currentSchemeName, currentFolio, currentIsin, currentAmfi,
                        currentUnits != null ? currentUnits : BigDecimal.ZERO,
                        currentCostBasis, currentMarketValue, currentNav
                    ));
                    currentSchemeName = null;
                    currentIsin = null;
                    currentAmfi = null;
                    currentUnits = null;
                    currentCostBasis = null;
                    currentMarketValue = null;
                    currentNav = null;
                }
                currentSchemeName = cleanSchemeName(line);
            }

            // Parse Units, Cost, Market Value, NAV (supporting Indian comma formatting e.g. 2,50,000.00)
            BigDecimal unitsFound = parseNamedAmount(line, "Closing Unit Balance|Balance Units|Closing Balance|Balance|Units");
            if (unitsFound != null && currentUnits == null) currentUnits = unitsFound;

            BigDecimal costFound = parseNamedAmount(line, "Cost Value|Purchase Value|Cost Basis|Cost Amount|Cost");
            if (costFound != null && currentCostBasis == null) currentCostBasis = costFound;

            BigDecimal mvFound = parseNamedAmount(line, "Market Value|Valuation|Current Value|Value");
            if (mvFound != null && currentMarketValue == null) currentMarketValue = mvFound;

            BigDecimal navFound = parseNamedAmount(line, "NAV|Net Asset Value");
            if (navFound != null && currentNav == null) currentNav = navFound;
        }

        // Flush last record
        if (currentSchemeName != null && (currentUnits != null || currentMarketValue != null || currentIsin != null)) {
            records.add(new CasImportParsedRecordDto(
                currentSchemeName, currentFolio, currentIsin, currentAmfi,
                currentUnits != null ? currentUnits : BigDecimal.ZERO,
                currentCostBasis, currentMarketValue, currentNav
            ));
        }

        return records;
    }

    private boolean isHeaderOrSubtotalLine(String line) {
        String u = line.toUpperCase(Locale.ROOT);
        return u.contains("SUB TOTAL") || u.contains("SUBTOTAL") || u.contains("TOTAL VALUATION") || u.contains("GRAND TOTAL")
            || u.contains("PORTFOLIO VALUE") || u.contains("PAGE ") || u.contains("DISCLAIMER")
            || u.contains("SUBJECT TO MARKET RISKS") || u.contains("MUTUAL FUND SEARCH");
    }

    private boolean isSchemeNameLine(String line) {
        String u = line.toUpperCase(Locale.ROOT);
        if (u.contains("FOLIO") || u.contains("STATEMENT") || u.contains("SUMMARY") || u.contains("TOTAL") || u.startsWith("PAGE ") || u.contains("CONSOLIDATED ACCOUNT")) {
            return false;
        }
        return (u.contains("FUND") || u.contains("SCHEME") || u.contains("DIRECT") || u.contains("REGULAR") || u.contains("GROWTH") || u.contains("IDCW") || u.contains("PLAN"))
            && !u.contains("VALUATION AS ON") && !u.contains("SEARCH");
    }

    private String cleanSchemeName(String line) {
        return line.replaceAll("^[0-9\\.\\*\\-\\s]+", "").trim();
    }

    private BigDecimal parseNamedAmount(String line, String keywords) {
        // Support Indian comma formatting: e.g. 2,50,000.00 or 1,25,435.12
        Pattern pattern = Pattern.compile("(?:" + keywords + ")\\s*[:\\-]?\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            try {
                String val = matcher.group(1).replaceAll(",", "");
                return new BigDecimal(val);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
