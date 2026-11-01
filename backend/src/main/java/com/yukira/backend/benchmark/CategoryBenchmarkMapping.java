package com.yukira.backend.benchmark;

import com.yukira.backend.universe.EquityUniverseCategory;

import java.util.*;

/**
 * Deterministic mapping between the five reference-universe equity categories and their canonical
 * Total Return Index (TRI) benchmark identifiers (TSK-M1-04).
 *
 * Epistemic & Methodological Guardrails:
 * 1. Return Variant Invariant: Prefer Total Return Indices (TRI) for all equity performance comparisons.
 *    Price Return Indices (PRI) omit dividend reinvestment and must NEVER be labeled or substituted as TRI.
 * 2. Governance Status: Category-level mappings are candidate conventions ("CANDIDATE_METHODOLOGY_MAPPING").
 *    They do NOT constitute final review-committee approval (Phase 2H freeze rule).
 * 3. Precedence: An explicit fund-level mapping in scheme_benchmark_hist ALWAYS supersedes category defaults.
 * 4. Explicit Unmapped State: Schemes outside the 5 reference-universe categories return UNMAPPED.
 */
public final class CategoryBenchmarkMapping {

    public static final String RULE_VERSION = "YUKIRA_V1_M1_CATEGORY_BENCHMARK_MAPPING_V1";
    public static final String PROVIDER_NSE = "NSE Indices Limited";
    public static final String VARIANT_TRI = "TRI";

    public record BenchmarkDescriptor(
        String benchmarkCode,
        String benchmarkName,
        String nseIndexQueryName,
        String provider,
        String returnVariant,
        EquityUniverseCategory category,
        String methodologyStatus,
        String rationalization
    ) {
        public boolean isTri() {
            return VARIANT_TRI.equalsIgnoreCase(returnVariant);
        }
    }

    private static final Map<EquityUniverseCategory, BenchmarkDescriptor> CATEGORY_BENCHMARK_MAP = new EnumMap<>(EquityUniverseCategory.class);

    static {
        // 1. Flexi Cap -> NIFTY 500 TRI (Broad-market multi-cap representative index)
        CATEGORY_BENCHMARK_MAP.put(EquityUniverseCategory.FLEXI_CAP, new BenchmarkDescriptor(
            "NIFTY_500_TRI",
            "NIFTY 500 Total Returns Index",
            "Nifty 500",
            PROVIDER_NSE,
            VARIANT_TRI,
            EquityUniverseCategory.FLEXI_CAP,
            "CANDIDATE_METHODOLOGY_MAPPING",
            "Broad-market multi-cap benchmark representing top 500 companies by market capitalization."
        ));

        // 2. Large Cap -> NIFTY 100 TRI (Top 100 large-cap companies)
        CATEGORY_BENCHMARK_MAP.put(EquityUniverseCategory.LARGE_CAP, new BenchmarkDescriptor(
            "NIFTY_100_TRI",
            "NIFTY 100 Total Returns Index",
            "Nifty 100",
            PROVIDER_NSE,
            VARIANT_TRI,
            EquityUniverseCategory.LARGE_CAP,
            "CANDIDATE_METHODOLOGY_MAPPING",
            "Large-cap representative benchmark covering top 100 companies by market capitalization."
        ));

        // 3. Large & Mid Cap -> NIFTY LargeMidcap 250 TRI (100 Large + 150 Mid)
        CATEGORY_BENCHMARK_MAP.put(EquityUniverseCategory.LARGE_AND_MID_CAP, new BenchmarkDescriptor(
            "NIFTY_LARGEMIDCAP_250_TRI",
            "NIFTY LargeMidcap 250 Total Returns Index",
            "Nifty LargeMidcap 250",
            PROVIDER_NSE,
            VARIANT_TRI,
            EquityUniverseCategory.LARGE_AND_MID_CAP,
            "CANDIDATE_METHODOLOGY_MAPPING",
            "Composite benchmark combining 100 large-cap and 150 mid-cap companies."
        ));

        // 4. Mid Cap -> NIFTY Midcap 150 TRI (Mid-cap companies ranked 101-250)
        CATEGORY_BENCHMARK_MAP.put(EquityUniverseCategory.MID_CAP, new BenchmarkDescriptor(
            "NIFTY_MIDCAP_150_TRI",
            "NIFTY Midcap 150 Total Returns Index",
            "Nifty Midcap 150",
            PROVIDER_NSE,
            VARIANT_TRI,
            EquityUniverseCategory.MID_CAP,
            "CANDIDATE_METHODOLOGY_MAPPING",
            "Pure mid-cap benchmark representing companies ranked 101 to 250 by market capitalization."
        ));

        // 5. Small Cap -> NIFTY Smallcap 250 TRI (Small-cap companies ranked 251-500)
        CATEGORY_BENCHMARK_MAP.put(EquityUniverseCategory.SMALL_CAP, new BenchmarkDescriptor(
            "NIFTY_SMALLCAP_250_TRI",
            "NIFTY Smallcap 250 Total Returns Index",
            "Nifty Smallcap 250",
            PROVIDER_NSE,
            VARIANT_TRI,
            EquityUniverseCategory.SMALL_CAP,
            "CANDIDATE_METHODOLOGY_MAPPING",
            "Pure small-cap benchmark representing companies ranked 251 to 500 by market capitalization."
        ));
    }

    private CategoryBenchmarkMapping() {}

    /**
     * Resolves the canonical candidate benchmark descriptor for a given reference universe category.
     * Returns Optional.empty() if the category is null or outside the 5 reference-universe categories.
     */
    public static Optional<BenchmarkDescriptor> getDescriptorForCategory(EquityUniverseCategory category) {
        if (category == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CATEGORY_BENCHMARK_MAP.get(category));
    }

    /**
     * Returns an unmodifiable collection of all canonical category benchmark descriptors.
     */
    public static Collection<BenchmarkDescriptor> getAllDescriptors() {
        return Collections.unmodifiableCollection(CATEGORY_BENCHMARK_MAP.values());
    }

    /**
     * Finds descriptor by benchmark code (e.g. NIFTY_500_TRI, NIFTY_100_TRI).
     */
    public static Optional<BenchmarkDescriptor> getDescriptorByCode(String benchmarkCode) {
        if (benchmarkCode == null || benchmarkCode.isBlank()) {
            return Optional.empty();
        }
        String normalized = benchmarkCode.trim().toUpperCase();
        for (BenchmarkDescriptor desc : CATEGORY_BENCHMARK_MAP.values()) {
            if (desc.benchmarkCode().equalsIgnoreCase(normalized)) {
                return Optional.of(desc);
            }
        }
        return Optional.empty();
    }
}
