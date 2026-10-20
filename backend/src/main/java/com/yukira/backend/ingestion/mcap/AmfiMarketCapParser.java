package com.yukira.backend.ingestion.mcap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Deterministic OpenXML parser for official AMFI Market Capitalization categorization spreadsheets (.xlsx).
 * Operates using Java standard library (ZipInputStream + DOM) without third-party heavy dependencies.
 */
@Component
public class AmfiMarketCapParser {

    private static final Logger log = LoggerFactory.getLogger(AmfiMarketCapParser.class);
    private static final Pattern CELL_COL_PATTERN = Pattern.compile("^([A-Z]+)(\\d+)$");

    public record ParsedMarketCapRecord(
        int rank,
        String companyName,
        String isin,
        String bseSymbol,
        String nseSymbol,
        BigDecimal avgMarketCapCrores,
        String officialCategory
    ) {}

    public record ParseResult(
        String title,
        List<ParsedMarketCapRecord> records,
        List<String> validationErrors,
        List<String> warnings
    ) {}

    public ParseResult parse(byte[] xlsxBytes) {
        List<String> sharedStrings = new ArrayList<>();
        byte[] sheetBytes = null;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsxBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("xl/sharedStrings.xml")) {
                    sharedStrings = parseSharedStrings(zis.readAllBytes());
                } else if (entry.getName().startsWith("xl/worksheets/sheet") && sheetBytes == null) {
                    sheetBytes = zis.readAllBytes();
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read AMFI XLSX archive: " + e.getMessage(), e);
        }

        if (sheetBytes == null) {
            throw new IllegalArgumentException("Invalid AMFI XLSX archive: sheet XML worksheet not found.");
        }

        return parseSheetXml(sheetBytes, sharedStrings);
    }

    private List<String> parseSharedStrings(byte[] xmlBytes) {
        List<String> list = new ArrayList<>();
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(xmlBytes));
            doc.getDocumentElement().normalize();

            NodeList siNodes = doc.getElementsByTagName("si");
            for (int i = 0; i < siNodes.getLength(); i++) {
                Node si = siNodes.item(i);
                StringBuilder sb = new StringBuilder();
                extractTextRecursive(si, sb);
                list.add(sb.toString().trim());
            }
        } catch (Exception e) {
            log.error("Failed to parse sharedStrings.xml: {}", e.getMessage(), e);
        }
        return list;
    }

    private void extractTextRecursive(Node node, StringBuilder sb) {
        if ("t".equals(node.getNodeName())) {
            sb.append(node.getTextContent());
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            extractTextRecursive(children.item(i), sb);
        }
    }

    private ParseResult parseSheetXml(byte[] xmlBytes, List<String> sharedStrings) {
        List<ParsedMarketCapRecord> records = new ArrayList<>();
        List<String> validationErrors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String sheetTitle = null;

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(xmlBytes));
            doc.getDocumentElement().normalize();

            NodeList rowNodes = doc.getElementsByTagName("row");
            int expectedNextRank = 1;
            Set<String> seenIsins = new HashSet<>();

            for (int rIdx = 0; rIdx < rowNodes.getLength(); rIdx++) {
                Element rowElem = (Element) rowNodes.item(rIdx);
                Map<Integer, String> colValues = extractRowCells(rowElem, sharedStrings);

                if (colValues.isEmpty()) continue;

                // Title row check (typically row 1)
                if (rIdx == 0 && sheetTitle == null) {
                    sheetTitle = colValues.getOrDefault(0, "");
                }

                // Header row check
                String firstCol = colValues.getOrDefault(0, "").trim();
                if (firstCol.equalsIgnoreCase("Sr. No.") || firstCol.equalsIgnoreCase("Sr No") || firstCol.equalsIgnoreCase("Rank") || firstCol.equalsIgnoreCase("S.No.")) {
                    continue; // Skip header
                }

                // Check if row is a data row with numeric rank
                if (!firstCol.matches("^\\d+$")) {
                    continue;
                }

                int rank = Integer.parseInt(firstCol);
                String companyName = colValues.getOrDefault(1, "").trim();
                String isin = colValues.getOrDefault(2, "").trim().toUpperCase(Locale.ROOT);
                String bseSymbol = colValues.getOrDefault(3, "").trim();
                String nseSymbol = colValues.getOrDefault(5, "").trim();
                String mcapStr = colValues.getOrDefault(9, "").trim();
                String catCol = colValues.getOrDefault(10, "").trim();

                // Validate Rank Sequence
                if (rank != expectedNextRank) {
                    warnings.add("Ranking sequence gap or duplicate: expected #" + expectedNextRank + " but encountered #" + rank + " (" + companyName + ")");
                }
                expectedNextRank = rank + 1;

                // Validate ISIN
                if (isin.isBlank() || isin.length() != 12 || !isin.startsWith("IN")) {
                    validationErrors.add("Invalid or non-standard ISIN '" + isin + "' for rank #" + rank + " (" + companyName + ")");
                }
                if (seenIsins.contains(isin)) {
                    validationErrors.add("Duplicate ISIN '" + isin + "' observed at rank #" + rank + " (" + companyName + ")");
                }
                seenIsins.add(isin);

                // Market Cap parsing
                BigDecimal avgMcap = null;
                if (!mcapStr.isBlank()) {
                    try {
                        avgMcap = new BigDecimal(mcapStr.replaceAll("[^0-9.]", ""));
                    } catch (Exception ignored) {}
                }

                // Category boundary check (1-100 Large, 101-250 Mid, 251+ Small)
                String resolvedCategory;
                if (rank <= 100) {
                    resolvedCategory = "LARGE_CAP";
                } else if (rank <= 250) {
                    resolvedCategory = "MID_CAP";
                } else {
                    resolvedCategory = "SMALL_CAP";
                }

                // Cross-check with source category column if present
                if (!catCol.isBlank()) {
                    String catUpper = catCol.toUpperCase(Locale.ROOT);
                    if (resolvedCategory.equals("LARGE_CAP") && !catUpper.contains("LARGE")) {
                        warnings.add("Rank #" + rank + " is Large Cap by rank boundary (1-100) but source labeled '" + catCol + "'");
                    } else if (resolvedCategory.equals("MID_CAP") && !catUpper.contains("MID")) {
                        warnings.add("Rank #" + rank + " is Mid Cap by rank boundary (101-250) but source labeled '" + catCol + "'");
                    } else if (resolvedCategory.equals("SMALL_CAP") && !catUpper.contains("SMALL")) {
                        warnings.add("Rank #" + rank + " is Small Cap by rank boundary (251+) but source labeled '" + catCol + "'");
                    }
                }

                records.add(new ParsedMarketCapRecord(
                    rank,
                    companyName,
                    isin,
                    bseSymbol.equalsIgnoreCase("-") ? null : bseSymbol,
                    nseSymbol.equalsIgnoreCase("-") ? null : nseSymbol,
                    avgMcap,
                    resolvedCategory
                ));
            }
        } catch (Exception e) {
            validationErrors.add("Sheet parsing failure: " + e.getMessage());
            log.error("Failed to parse sheet XML: {}", e.getMessage(), e);
        }

        return new ParseResult(sheetTitle, records, validationErrors, warnings);
    }

    private Map<Integer, String> extractRowCells(Element rowElem, List<String> sharedStrings) {
        Map<Integer, String> cells = new LinkedHashMap<>();
        NodeList cNodes = rowElem.getElementsByTagName("c");

        for (int i = 0; i < cNodes.getLength(); i++) {
            Element c = (Element) cNodes.item(i);
            String rAttr = c.getAttribute("r");
            int colIdx = parseColIndex(rAttr, i);

            String tAttr = c.getAttribute("t");
            NodeList vNodes = c.getElementsByTagName("v");
            String vText = vNodes.getLength() > 0 ? vNodes.item(0).getTextContent().trim() : "";

            String cellVal;
            if ("s".equals(tAttr) && vText.matches("^\\d+$")) {
                int sIdx = Integer.parseInt(vText);
                cellVal = sIdx < sharedStrings.size() ? sharedStrings.get(sIdx) : vText;
            } else if ("inlineStr".equals(tAttr)) {
                NodeList tNodes = c.getElementsByTagName("t");
                cellVal = tNodes.getLength() > 0 ? tNodes.item(0).getTextContent().trim() : vText;
            } else {
                cellVal = vText;
            }

            cells.put(colIdx, cellVal);
        }
        return cells;
    }

    private int parseColIndex(String rAttr, int fallbackIdx) {
        if (rAttr == null || rAttr.isBlank()) return fallbackIdx;
        Matcher m = CELL_COL_PATTERN.matcher(rAttr);
        if (m.find()) {
            String colStr = m.group(1);
            int col = 0;
            for (int i = 0; i < colStr.length(); i++) {
                col = col * 26 + (colStr.charAt(i) - 'A' + 1);
            }
            return col - 1; // 0-indexed
        }
        return fallbackIdx;
    }
}
