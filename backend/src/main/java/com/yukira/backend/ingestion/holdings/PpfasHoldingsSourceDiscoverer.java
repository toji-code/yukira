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
 * Official source discoverer for Parag Parikh Mutual Fund (PPFAS).
 *
 * Epistemic & Discovery Rules:
 * 1. Targets official disclosure index: https://amc.ppfas.com/downloads/portfolio-disclosure/
 * 2. Unambiguously separates Parag Parikh Flexi Cap (AMFI 122639, PPFCF) from ELSS Tax Saver (AMFI 147481, PPTSF).
 * 3. Extracts authentic reporting month from accordion headers (e.g. "September 2026") and links.
 * 4. Extracts official publication dates from query timestamps (e.g. "?07102026" = 07-10-2026).
 * 5. Rejects non-XLSX (e.g. consolidated .xls or .pdf), other funds (PPLF, PPCHF, PPDAAF, PPLCF), and malformed structures.
 * 6. Fails closed with AMBIGUOUS_SOURCE if conflicting links exist for the same reporting month.
 */
@Component
public class PpfasHoldingsSourceDiscoverer implements OfficialHoldingsDiscoveryClient {

    private static final Logger log = LoggerFactory.getLogger(PpfasHoldingsSourceDiscoverer.class);

    public static final String DEFAULT_INDEX_URL = "https://amc.ppfas.com/downloads/portfolio-disclosure/";
    public static final String PPFAS_BASE_URL = "https://amc.ppfas.com";

    private static final Pattern CARD_PATTERN = Pattern.compile(
        "<div[^>]*class=\"[^\"]*card[^\"]*\"[^>]*>(.*?)<div[^>]*class=\"[^\"]*card-body[^\"]*\"[^>]*>(.*?)</div>\\s*</div>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern HEADER_MONTH_PATTERN = Pattern.compile(
        "(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{4})",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ANCHOR_PATTERN = Pattern.compile(
        "<a\\b([^>]*?)href=[\"']([^\"']+)[\"']([^>]*?)>(.*?)</a>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern TIMESTAMP_QUERY_PATTERN = Pattern.compile("\\?(\\d{2})(\\d{2})(\\d{4})");

    private final AuthoritativeHoldingsSourceFetcher sourceFetcher;
    private final String indexUrl;

    public PpfasHoldingsSourceDiscoverer(
        AuthoritativeHoldingsSourceFetcher sourceFetcher,
        @Value("${yukira.holdings.discovery.ppfas-url:" + DEFAULT_INDEX_URL + "}") String indexUrl
    ) {
        this.sourceFetcher = sourceFetcher;
        this.indexUrl = indexUrl != null && !indexUrl.isBlank() ? indexUrl.trim() : DEFAULT_INDEX_URL;
    }

    @Override
    public boolean supports(String amcCode, String amfiCode) {
        if ("PPFAS_MF".equalsIgnoreCase(amcCode)) return true;
        return "122639".equals(amfiCode) || "147481".equals(amfiCode);
    }

    @Override
    public List<DiscoveredHoldingsCandidate> discoverDisclosures(
        MonthlyHoldingsSourceRegistry.RegisteredFundConfig fundConfig,
        LocalDate windowStart,
        LocalDate windowEnd
    ) {
        if (fundConfig == null) {
            return Collections.emptyList();
        }

        log.info("Querying official PPFAS portfolio disclosure index at: {}", indexUrl);
        AuthoritativeHoldingsSourceFetcher.HtmlFetchResult fetchResult = sourceFetcher.fetchHtml(indexUrl);

        if (!fetchResult.success()) {
            log.warn("PPFAS official disclosure index unreachable (HTTP {}): {}", fetchResult.statusCode(), fetchResult.errorMessage());
            return List.of(DiscoveredHoldingsCandidate.sourceUnavailable(
                fundConfig.amcCode(),
                fundConfig.amfiCode(),
                fundConfig.schemeName(),
                windowEnd,
                "PPFAS disclosure index returned HTTP " + fetchResult.statusCode() + ": " + fetchResult.errorMessage()
            ));
        }

        return parseDisclosuresFromHtml(fetchResult.html(), PPFAS_BASE_URL, fundConfig, windowStart, windowEnd);
    }

    /**
     * Pure deterministic parser for PPFAS portfolio disclosure index HTML.
     */
    public List<DiscoveredHoldingsCandidate> parseDisclosuresFromHtml(
        String html,
        String baseUri,
        MonthlyHoldingsSourceRegistry.RegisteredFundConfig fundConfig,
        LocalDate windowStart,
        LocalDate windowEnd
    ) {
        if (html == null || html.isBlank()) {
            return List.of(DiscoveredHoldingsCandidate.invalid(
                fundConfig.amcCode(), fundConfig.amfiCode(), fundConfig.schemeName(), windowEnd, null, "Empty HTML response from PPFAS"
            ));
        }

        boolean isTaxSaver = "147481".equals(fundConfig.amfiCode()) ||
            (fundConfig.schemeName() != null && fundConfig.schemeName().toUpperCase(Locale.ROOT).contains("TAX"));
        boolean isFlexiCap = "122639".equals(fundConfig.amfiCode()) ||
            (fundConfig.schemeName() != null && fundConfig.schemeName().toUpperCase(Locale.ROOT).contains("FLEXI"));

        Map<LocalDate, List<CandidateLink>> candidatesByDate = new LinkedHashMap<>();

        Matcher cardMatcher = CARD_PATTERN.matcher(html);
        while (cardMatcher.find()) {
            String headerSection = cardMatcher.group(1);
            String bodySection = cardMatcher.group(2);

            LocalDate periodDate = extractMonthFromText(headerSection);
            if (periodDate == null) {
                continue;
            }

            if (windowStart != null && periodDate.isBefore(windowStart)) continue;
            if (windowEnd != null && periodDate.isAfter(windowEnd)) continue;

            Matcher anchorMatcher = ANCHOR_PATTERN.matcher(bodySection);
            while (anchorMatcher.find()) {
                String beforeHref = anchorMatcher.group(1);
                String href = anchorMatcher.group(2).trim();
                String afterHref = anchorMatcher.group(3);
                String anchorText = anchorMatcher.group(4).trim();

                String allContext = (beforeHref + " " + href + " " + afterHref + " " + anchorText).toUpperCase(Locale.ROOT);

                // Strictly require XLSX
                if (!href.toLowerCase(Locale.ROOT).contains(".xlsx")) {
                    continue;
                }

                // Scheme identity matching
                boolean matchesTarget = false;
                if (isTaxSaver) {
                    if ((allContext.contains("PPTSF") || allContext.contains("TAX SAVER") || allContext.contains("ELSS")) &&
                        !allContext.contains("PPFCF") && !allContext.contains("FLEXI CAP")) {
                        matchesTarget = true;
                    }
                } else if (isFlexiCap) {
                    if ((allContext.contains("PPFCF") || allContext.contains("FLEXI CAP")) &&
                        !allContext.contains("PPTSF") && !allContext.contains("TAX SAVER")) {
                        matchesTarget = true;
                    }
                }

                if (matchesTarget) {
                    String fullUrl = resolveUrl(href, baseUri);
                    OffsetDateTime publicationTime = extractPublicationTimeFromQuery(href, periodDate);
                    candidatesByDate.computeIfAbsent(periodDate, k -> new ArrayList<>())
                        .add(new CandidateLink(fullUrl, publicationTime));
                }
            }
        }

        if (candidatesByDate.isEmpty()) {
            return List.of(DiscoveredHoldingsCandidate.notYetPublished(
                fundConfig.amcCode(),
                fundConfig.amfiCode(),
                fundConfig.schemeName(),
                windowEnd,
                "No published disclosure discovered for fund in requested window"
            ));
        }

        List<DiscoveredHoldingsCandidate> results = new ArrayList<>();
        for (Map.Entry<LocalDate, List<CandidateLink>> entry : candidatesByDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<CandidateLink> links = entry.getValue();

            // Distinct URLs
            Set<String> distinctUrls = new LinkedHashSet<>();
            for (CandidateLink cl : links) {
                distinctUrls.add(cl.url);
            }

            if (distinctUrls.size() > 1) {
                log.warn("Ambiguous multiple disclosure candidates for PPFAS AMFI #{} on {}: {}", fundConfig.amfiCode(), date, distinctUrls);
                results.add(DiscoveredHoldingsCandidate.ambiguous(
                    fundConfig.amcCode(),
                    fundConfig.amfiCode(),
                    fundConfig.schemeName(),
                    date,
                    "Ambiguous candidates found for " + date + ": " + distinctUrls
                ));
            } else {
                CandidateLink selected = links.get(0);
                MonthlyHoldingsSourceDescriptor descriptor = new MonthlyHoldingsSourceDescriptor(
                    fundConfig.amcCode(),
                    fundConfig.amcName(),
                    fundConfig.schemeCode(),
                    fundConfig.schemeName(),
                    fundConfig.amfiCode(),
                    fundConfig.isin(),
                    null,
                    selected.url,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "XLSX",
                    date,
                    selected.publicationTime,
                    "SEBI_AMC_DISCLOSURE",
                    "OFFICIAL_DISCOVERY",
                    "V1_OPENXML"
                );

                results.add(DiscoveredHoldingsCandidate.discovered(
                    fundConfig.amcCode(),
                    fundConfig.amfiCode(),
                    fundConfig.schemeName(),
                    date,
                    selected.publicationTime,
                    selected.url,
                    descriptor
                ));
            }
        }

        return results;
    }

    private LocalDate extractMonthFromText(String text) {
        if (text == null) return null;
        Matcher m = HEADER_MONTH_PATTERN.matcher(text);
        if (m.find()) {
            String monthName = m.group(1);
            int year = Integer.parseInt(m.group(2));
            try {
                Month month = Month.valueOf(monthName.toUpperCase(Locale.ROOT));
                int lastDay = month.length(java.time.Year.isLeap(year));
                return LocalDate.of(year, month, lastDay);
            } catch (Exception ignored) {}
        }
        return null;
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

    private OffsetDateTime extractPublicationTimeFromQuery(String href, LocalDate periodDate) {
        Matcher m = TIMESTAMP_QUERY_PATTERN.matcher(href);
        if (m.find()) {
            try {
                int day = Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(2));
                int year = Integer.parseInt(m.group(3));
                return LocalDate.of(year, month, day).atTime(23, 59, 59, 0).atOffset(ZoneOffset.ofHoursMinutes(5, 30));
            } catch (Exception ignored) {}
        }
        // Statutory SEBI deadline fallback (10th of succeeding month)
        if (periodDate != null) {
            return periodDate.plusMonths(1).withDayOfMonth(10).atTime(23, 59, 59, 0).atOffset(ZoneOffset.ofHoursMinutes(5, 30));
        }
        return OffsetDateTime.now();
    }

    private record CandidateLink(String url, OffsetDateTime publicationTime) {}
}
