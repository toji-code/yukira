package com.yukira.backend.scoring.population;

/**
 * Criteria specification for identifying eligible peer funds for empirical reference distributions.
 */
public record PeerPopulationCriteria(
    String populationCategory,
    String planType,
    String optionType,
    int minHistoryMonths,
    int minRequiredObservations,
    boolean requiresBenchmark,
    String benchmarkCode
) {
    /**
     * Canonical Flexi Cap Direct Growth peer criteria.
     *
     * benchmarkCode is the authoritative TRI benchmark code registered in the
     * {@code benchmark} table (code = NIFTY_500_TRI, id = 123). The price-return
     * alias "NIFTY_500" is NOT a registered benchmark and must never be used for
     * relative-metric calibration.
     */
    public static PeerPopulationCriteria defaultFlexiCap() {
        return new PeerPopulationCriteria(
            "Flexi Cap Fund",
            "DIRECT",
            "GROWTH",
            36,
            700,
            true,
            PeerPopulationConstants.CANONICAL_BENCHMARK_CODE
        );
    }
}
