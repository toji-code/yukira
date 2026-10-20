package com.yukira.backend.ingestion.holdings;

/**
 * Explicit outcomes for official AMC monthly portfolio disclosure source discovery.
 *
 * Epistemic Rules:
 * 1. DISCOVERED: Official report URL and reporting period verified with authentic metadata.
 * 2. NOT_YET_PUBLISHED: Index queried successfully, but report has not yet been published by the AMC.
 * 3. SOURCE_UNAVAILABLE: Official index endpoint unreachable, timed out, or returned HTTP error (403/404/500).
 * 4. AMBIGUOUS_SOURCE: Multiple conflicting candidates found for the same fund/month; fails closed.
 * 5. INVALID_DISCLOSURE: Link context or metadata indicates non-XLSX, malformed, or unsupported disclosure.
 */
public enum DiscoveryStatus {
    DISCOVERED,
    NOT_YET_PUBLISHED,
    SOURCE_UNAVAILABLE,
    AMBIGUOUS_SOURCE,
    INVALID_DISCLOSURE
}
