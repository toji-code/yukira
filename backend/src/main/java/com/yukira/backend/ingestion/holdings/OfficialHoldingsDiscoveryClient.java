package com.yukira.backend.ingestion.holdings;

import java.time.LocalDate;
import java.util.List;

/**
 * Interface for official AMC monthly portfolio disclosure source discovery clients.
 */
public interface OfficialHoldingsDiscoveryClient {
    boolean supports(String amcCode, String amfiCode);

    /**
     * Discovers official portfolio disclosure candidates for a registered fund within a bounded reporting window.
     */
    List<DiscoveredHoldingsCandidate> discoverDisclosures(
        MonthlyHoldingsSourceRegistry.RegisteredFundConfig fundConfig,
        LocalDate windowStart,
        LocalDate windowEnd
    );
}
