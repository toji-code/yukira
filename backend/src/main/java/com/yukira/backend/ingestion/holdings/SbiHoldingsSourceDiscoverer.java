package com.yukira.backend.ingestion.holdings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Official statutory source discoverer for SBI Mutual Fund.
 *
 * Epistemic & Discovery Rules:
 * 1. Targets official disclosure portal: https://www.sbimf.com/en-us/portfolios
 * 2. Matches SBI equity schemes (SBI Bluechip, SBI Flexicap, SBI Small Cap) from statutory disclosures.
 * 3. Bounded, safe discovery with graceful fallback to SOURCE_UNAVAILABLE when offline.
 */
@Component
public class SbiHoldingsSourceDiscoverer implements OfficialHoldingsDiscoveryClient {

    private static final Logger log = LoggerFactory.getLogger(SbiHoldingsSourceDiscoverer.class);

    public static final String DEFAULT_INDEX_URL = "https://www.sbimf.com/en-us/portfolios";
    public static final String SBI_BASE_URL = "https://www.sbimf.com";

    private static final Pattern ANCHOR_PATTERN = Pattern.compile(
        "<a\\b([^>]*?)href=[\"']([^\"']+)[\"']([^>]*?)>(.*?)</a>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final AuthoritativeHoldingsSourceFetcher sourceFetcher;
    private final String indexUrl;

    public SbiHoldingsSourceDiscoverer(
        AuthoritativeHoldingsSourceFetcher sourceFetcher,
        @Value("${yukira.holdings.discovery.sbi-url:" + DEFAULT_INDEX_URL + "}") String indexUrl
    ) {
        this.sourceFetcher = sourceFetcher;
        this.indexUrl = indexUrl != null && !indexUrl.isBlank() ? indexUrl.trim() : DEFAULT_INDEX_URL;
    }

    @Override
    public boolean supports(String amcCode, String amfiCode) {
        if ("SBI_MF".equalsIgnoreCase(amcCode)) return true;
        return "120503".equals(amfiCode) || "119842".equals(amfiCode) || "119844".equals(amfiCode);
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

        log.info("Querying official SBI Mutual Fund portfolio disclosure index at: {}", indexUrl);
        AuthoritativeHoldingsSourceFetcher.HtmlFetchResult fetchResult = sourceFetcher.fetchHtml(indexUrl);

        if (!fetchResult.success()) {
            log.warn("SBI official disclosure index unreachable (HTTP {}): {}", fetchResult.statusCode(), fetchResult.errorMessage());
            return List.of(DiscoveredHoldingsCandidate.sourceUnavailable(
                fundConfig.amcCode(),
                fundConfig.amfiCode(),
                fundConfig.schemeName(),
                windowEnd,
                "SBI disclosure index returned HTTP " + fetchResult.statusCode() + ": " + fetchResult.errorMessage()
            ));
        }

        return parseDisclosuresFromHtml(fetchResult.html(), SBI_BASE_URL, fundConfig, windowStart, windowEnd);
    }

    public List<DiscoveredHoldingsCandidate> parseDisclosuresFromHtml(
        String html,
        String baseUri,
        MonthlyHoldingsSourceRegistry.RegisteredFundConfig fundConfig,
        LocalDate windowStart,
        LocalDate windowEnd
    ) {
        if (html == null || html.isBlank()) {
            return List.of(DiscoveredHoldingsCandidate.invalid(
                fundConfig.amcCode(), fundConfig.amfiCode(), fundConfig.schemeName(), windowEnd, null, "Empty HTML response from SBI"
            ));
        }

        List<DiscoveredHoldingsCandidate> candidates = new ArrayList<>();
        Matcher matcher = ANCHOR_PATTERN.matcher(html);

        String targetName = fundConfig.schemeName() != null ? fundConfig.schemeName().toUpperCase(Locale.ROOT) : "";

        while (matcher.find()) {
            String href = matcher.group(2).trim();
            String linkText = matcher.group(4).replaceAll("<[^>]*>", "").trim().toUpperCase(Locale.ROOT);

            boolean matchesScheme = false;
            if (targetName.contains("BLUECHIP") && (linkText.contains("BLUECHIP") || href.toUpperCase(Locale.ROOT).contains("BLUECHIP"))) {
                matchesScheme = true;
            } else if (targetName.contains("FLEXICAP") && (linkText.contains("FLEXICAP") || href.toUpperCase(Locale.ROOT).contains("FLEXICAP"))) {
                matchesScheme = true;
            } else if (targetName.contains("SMALL") && (linkText.contains("SMALL") || href.toUpperCase(Locale.ROOT).contains("SMALL"))) {
                matchesScheme = true;
            }

            if (matchesScheme && (href.toLowerCase(Locale.ROOT).endsWith(".xlsx") || href.toLowerCase(Locale.ROOT).endsWith(".xls") || href.toLowerCase(Locale.ROOT).endsWith(".csv"))) {
                String fullUrl = href.startsWith("http") ? href : baseUri + (href.startsWith("/") ? "" : "/") + href;
                LocalDate reportingDate = windowEnd != null ? windowEnd : LocalDate.now().withDayOfMonth(1).minusDays(1);

                MonthlyHoldingsSourceDescriptor descriptor = MonthlyHoldingsSourceDescriptor.of(
                    fundConfig.amcCode(),
                    fundConfig.amcName(),
                    fundConfig.schemeCode(),
                    fundConfig.schemeName(),
                    fundConfig.amfiCode(),
                    fundConfig.isin(),
                    fullUrl,
                    reportingDate
                );

                candidates.add(DiscoveredHoldingsCandidate.discovered(
                    fundConfig.amcCode(),
                    fundConfig.amfiCode(),
                    fundConfig.schemeName(),
                    reportingDate,
                    OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)),
                    fullUrl,
                    descriptor
                ));
            }
        }

        if (candidates.isEmpty()) {
            return List.of(DiscoveredHoldingsCandidate.notYetPublished(
                fundConfig.amcCode(),
                fundConfig.amfiCode(),
                fundConfig.schemeName(),
                windowEnd,
                "No published disclosure matching '" + fundConfig.schemeName() + "' found in SBI disclosure index"
            ));
        }

        return candidates;
    }
}

