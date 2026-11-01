package com.yukira.backend.ingestion.holdings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Official source discoverer for HDFC Mutual Fund.
 *
 * Epistemic & Discovery Rules:
 * 1. Targets official disclosure index:
 * https://www.hdfcfund.com/statutory-disclosures/monthly-fortnightly-scheme-portfolio
 * 2. Accurately isolates HDFC Flexi Cap Fund (AMFI 118955) from other schemes
 * (Top 100, Mid-Cap, Small Cap).
 * 3. Extracts authentic reporting month from table row / container metadata.
 * 4. Preserves AWS S3 immutable VersionId query parameters where present.
 * 5. Rejects non-XLSX, malformed links, or conflicting ambiguous candidates.
 */
@Component
public class HdfcHoldingsSourceDiscoverer implements OfficialHoldingsDiscoveryClient {

    private static final Logger log = LoggerFactory.getLogger(HdfcHoldingsSourceDiscoverer.class);

    public static final String DEFAULT_INDEX_URL = "https://www.hdfcfund.com/statutory-disclosures/monthly-fortnightly-scheme-portfolio";
    public static final String HDFC_BASE_URL = "https://www.hdfcfund.com";

    private static final Pattern ROW_OR_BLOCK_PATTERN = Pattern.compile(
            "<(?:tr|div|li)[^>]*>(.*?)</(?:tr|div|li)>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern ANCHOR_PATTERN = Pattern.compile(
            "<a\\b([^>]*?)href=[\"']([^\"']+)[\"']([^>]*?)>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));

    private static final Pattern MONTH_YEAR_PATTERN = Pattern.compile(
            "\\b(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{4})\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EXACT_DATE_PATTERN = Pattern.compile(
            "\\b(\\d{1,2})[- ]+(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[- ]+(\\d{4})\\b",
            Pattern.CASE_INSENSITIVE);

    private final AuthoritativeHoldingsSourceFetcher sourceFetcher;
    private final String indexUrl;

    public HdfcHoldingsSourceDiscoverer(
            AuthoritativeHoldingsSourceFetcher sourceFetcher,
            @Value("${yukira.holdings.discovery.hdfc-url:" + DEFAULT_INDEX_URL + "}") String indexUrl) {
        this.sourceFetcher = sourceFetcher;
        this.indexUrl = indexUrl != null && !indexUrl.isBlank() ? indexUrl.trim() : DEFAULT_INDEX_URL;
    }

    @Override
    public boolean supports(String amcCode, String amfiCode) {
        if ("HDFC_MF".equalsIgnoreCase(amcCode))
            return true;
        return "118955".equals(amfiCode);
    }

    @Override
    public List<DiscoveredHoldingsCandidate> discoverDisclosures(
            MonthlyHoldingsSourceRegistry.RegisteredFundConfig fundConfig,
            LocalDate windowStart,
            LocalDate windowEnd) {
        if (fundConfig == null) {
            return Collections.emptyList();
        }

        log.info("Querying official HDFC portfolio disclosure index at: {}", indexUrl);
        AuthoritativeHoldingsSourceFetcher.HtmlFetchResult fetchResult = sourceFetcher.fetchHtml(indexUrl);

        if (!fetchResult.success()) {
            log.warn("HDFC official disclosure index unreachable (HTTP {}): {}", fetchResult.statusCode(),
                    fetchResult.errorMessage());
            return List.of(DiscoveredHoldingsCandidate.sourceUnavailable(
                    fundConfig.amcCode(),
                    fundConfig.amfiCode(),
                    fundConfig.schemeName(),
                    windowEnd,
                    "HDFC disclosure index returned HTTP " + fetchResult.statusCode() + ": "
                            + fetchResult.errorMessage()));
        }

        return parseDisclosuresFromHtml(fetchResult.html(), HDFC_BASE_URL, fundConfig, windowStart, windowEnd);
    }

    /**
     * Pure deterministic parser for HDFC portfolio disclosure index HTML.
     */
    public List<DiscoveredHoldingsCandidate> parseDisclosuresFromHtml(
            String html,
            String baseUri,
            MonthlyHoldingsSourceRegistry.RegisteredFundConfig fundConfig,
            LocalDate windowStart,
            LocalDate windowEnd) {
        if (html == null || html.isBlank()) {
            return List.of(DiscoveredHoldingsCandidate.invalid(
                    fundConfig.amcCode(), fundConfig.amfiCode(), fundConfig.schemeName(), windowEnd, null,
                    "Empty HTML response from HDFC"));
        }

        Map<LocalDate, List<String>> candidatesByDate = new LinkedHashMap<>();

        Matcher blockMatcher = ROW_OR_BLOCK_PATTERN.matcher(html);
        while (blockMatcher.find()) {
            String blockHtml = blockMatcher.group(1);
            String blockUpper = blockHtml.toUpperCase(Locale.ROOT);

            // Must match target scheme specifically and avoid colliding with other schemes
            if (!matchesTargetScheme(blockUpper, fundConfig.schemeName())) {
                continue;
            }

            // Extract reporting date
            LocalDate reportingDate = extractDateFromBlock(blockHtml);
            if (reportingDate == null) {
                continue;
            }

            if (windowStart != null && reportingDate.isBefore(windowStart))
                continue;
            if (windowEnd != null && reportingDate.isAfter(windowEnd))
                continue;

            // Find XLSX download link
            Matcher anchorMatcher = ANCHOR_PATTERN.matcher(blockHtml);
            while (anchorMatcher.find()) {
                String href = anchorMatcher.group(2).trim();
                if (href.toLowerCase(Locale.ROOT).contains(".xlsx")) {
                    String fullUrl = resolveUrl(href, baseUri);
                    candidatesByDate.computeIfAbsent(reportingDate, k -> new ArrayList<>()).add(fullUrl);
                }
            }
        }

        if (candidatesByDate.isEmpty()) {
            return List.of(DiscoveredHoldingsCandidate.notYetPublished(
                    fundConfig.amcCode(),
                    fundConfig.amfiCode(),
                    fundConfig.schemeName(),
                    windowEnd,
                    "No published disclosure discovered for HDFC Flexi Cap in requested window"));
        }

        List<DiscoveredHoldingsCandidate> results = new ArrayList<>();
        for (Map.Entry<LocalDate, List<String>> entry : candidatesByDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<String> urls = entry.getValue();

            Set<String> distinctUrls = new LinkedHashSet<>(urls);
            if (distinctUrls.size() > 1) {
                log.warn("Ambiguous multiple disclosure candidates for HDFC Flexi Cap on {}: {}", date, distinctUrls);
                results.add(DiscoveredHoldingsCandidate.ambiguous(
                        fundConfig.amcCode(),
                        fundConfig.amfiCode(),
                        fundConfig.schemeName(),
                        date,
                        "Ambiguous candidates found for " + date + ": " + distinctUrls));
            } else {
                String selectedUrl = urls.get(0);
                OffsetDateTime statutoryAvailability = date.plusMonths(1).withDayOfMonth(10).atTime(23, 59, 59, 0)
                        .atOffset(ZoneOffset.ofHoursMinutes(5, 30));

                MonthlyHoldingsSourceDescriptor descriptor = new MonthlyHoldingsSourceDescriptor(
                        fundConfig.amcCode(),
                        fundConfig.amcName(),
                        fundConfig.schemeCode(),
                        fundConfig.schemeName(),
                        fundConfig.amfiCode(),
                        fundConfig.isin(),
                        null,
                        selectedUrl,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "XLSX",
                        date,
                        statutoryAvailability,
                        "SEBI_AMC_DISCLOSURE",
                        "OFFICIAL_DISCOVERY",
                        "V1_OPENXML");

                results.add(DiscoveredHoldingsCandidate.discovered(
                        fundConfig.amcCode(),
                        fundConfig.amfiCode(),
                        fundConfig.schemeName(),
                        date,
                        statutoryAvailability,
                        selectedUrl,
                        descriptor));
            }
        }

        return results;
    }

    private LocalDate extractDateFromBlock(String block) {
        // 1. Try exact day-month-year pattern
        Matcher exactM = EXACT_DATE_PATTERN.matcher(block);
        if (exactM.find()) {
            String dateStr = exactM.group(0).replaceAll("[- ]+", " ").trim();
            for (DateTimeFormatter fmt : DATE_FORMATTERS) {
                try {
                    return LocalDate.parse(dateStr, fmt);
                } catch (Exception ignored) {
                }
            }
        }

        // 2. Try month-year pattern
        Matcher myM = MONTH_YEAR_PATTERN.matcher(block);
        if (myM.find()) {
            String monthName = myM.group(1);
            int year = Integer.parseInt(myM.group(2));
            try {
                Month month = Month.valueOf(monthName.toUpperCase(Locale.ROOT));
                int lastDay = month.length(java.time.Year.isLeap(year));
                return LocalDate.of(year, month, lastDay);
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private boolean matchesTargetScheme(String blockUpper, String schemeName) {
        if (schemeName == null || schemeName.isBlank())
            return false;
        String sNameUpper = schemeName.toUpperCase(Locale.ROOT);

        if (sNameUpper.contains("TOP 100") || sNameUpper.contains("TOP-100")) {
            return (blockUpper.contains("TOP 100") || blockUpper.contains("TOP-100")) && !blockUpper.contains("MID-CAP")
                    && !blockUpper.contains("FLEXI");
        } else if (sNameUpper.contains("MID-CAP") || sNameUpper.contains("MID CAP") || sNameUpper.contains("MIDCAP")) {
            return (blockUpper.contains("MID-CAP") || blockUpper.contains("MID CAP") || blockUpper.contains("MIDCAP"))
                    && !blockUpper.contains("SMALL") && !blockUpper.contains("TOP 100")
                    && !blockUpper.contains("FLEXI");
        } else if (sNameUpper.contains("SMALL CAP") || sNameUpper.contains("SMALL-CAP")
                || sNameUpper.contains("SMALLCAP")) {
            return (blockUpper.contains("SMALL CAP") || blockUpper.contains("SMALL-CAP")
                    || blockUpper.contains("SMALLCAP")) && !blockUpper.contains("MID-CAP")
                    && !blockUpper.contains("TOP 100") && !blockUpper.contains("FLEXI");
        } else if (sNameUpper.contains("LARGE AND MID") || sNameUpper.contains("LARGE & MID")) {
            return (blockUpper.contains("LARGE AND MID") || blockUpper.contains("LARGE & MID")
                    || blockUpper.contains("LARGE & MIDCAP")) && !blockUpper.contains("FLEXI");
        } else if (sNameUpper.contains("FLEXI")) {
            boolean isFlexi = blockUpper.contains("FLEXI CAP") || blockUpper.contains("FLEXICAP");
            if (!isFlexi)
                return false;
            return !blockUpper.contains("TOP 100") && !blockUpper.contains("MID-CAP")
                    && !blockUpper.contains("SMALL CAP") && !blockUpper.contains("BALANCED ADVANTAGE");
        }
        return false;
    }

    private String resolveUrl(String href, String baseUri) {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        if (href.startsWith("/")) {
            return baseUri + href;
        }
        return baseUri + "/" + href;
    }
}
