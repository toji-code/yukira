package com.yukira.backend.ingestion.holdings;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Immutable candidate disclosure item resolved during official AMC source discovery.
 *
 * Epistemic Rules:
 * 1. Contains authentic scheme identity, discovered URL, reporting date, and provenance metadata.
 * 2. Unambiguous: Fails closed with AMBIGUOUS_SOURCE or INVALID_DISCLOSURE if evidence is conflicting.
 * 3. Does not invent or guess URLs or dates.
 */
public record DiscoveredHoldingsCandidate(
    String amcCode,
    String amfiCode,
    String schemeName,
    LocalDate reportingDate,
    OffsetDateTime publicationTime,
    String sourceUrl,
    DiscoveryStatus status,
    String statusMessage,
    MonthlyHoldingsSourceDescriptor descriptor
) {
    public boolean isDiscovered() {
        return status == DiscoveryStatus.DISCOVERED && descriptor != null;
    }

    public static DiscoveredHoldingsCandidate discovered(
        String amcCode,
        String amfiCode,
        String schemeName,
        LocalDate reportingDate,
        OffsetDateTime publicationTime,
        String sourceUrl,
        MonthlyHoldingsSourceDescriptor descriptor
    ) {
        return new DiscoveredHoldingsCandidate(
            amcCode,
            amfiCode,
            schemeName,
            reportingDate,
            publicationTime,
            sourceUrl,
            DiscoveryStatus.DISCOVERED,
            "Authentic disclosure discovered: " + sourceUrl,
            descriptor
        );
    }

    public static DiscoveredHoldingsCandidate notYetPublished(
        String amcCode,
        String amfiCode,
        String schemeName,
        LocalDate reportingDate,
        String message
    ) {
        return new DiscoveredHoldingsCandidate(
            amcCode,
            amfiCode,
            schemeName,
            reportingDate,
            null,
            null,
            DiscoveryStatus.NOT_YET_PUBLISHED,
            message != null ? message : "Disclosure not yet published by AMC for " + reportingDate,
            null
        );
    }

    public static DiscoveredHoldingsCandidate sourceUnavailable(
        String amcCode,
        String amfiCode,
        String schemeName,
        LocalDate reportingDate,
        String message
    ) {
        return new DiscoveredHoldingsCandidate(
            amcCode,
            amfiCode,
            schemeName,
            reportingDate,
            null,
            null,
            DiscoveryStatus.SOURCE_UNAVAILABLE,
            message != null ? message : "Official AMC disclosure source is unavailable",
            null
        );
    }

    public static DiscoveredHoldingsCandidate ambiguous(
        String amcCode,
        String amfiCode,
        String schemeName,
        LocalDate reportingDate,
        String message
    ) {
        return new DiscoveredHoldingsCandidate(
            amcCode,
            amfiCode,
            schemeName,
            reportingDate,
            null,
            null,
            DiscoveryStatus.AMBIGUOUS_SOURCE,
            message != null ? message : "Multiple conflicting disclosure candidates detected for " + reportingDate,
            null
        );
    }

    public static DiscoveredHoldingsCandidate invalid(
        String amcCode,
        String amfiCode,
        String schemeName,
        LocalDate reportingDate,
        String sourceUrl,
        String message
    ) {
        return new DiscoveredHoldingsCandidate(
            amcCode,
            amfiCode,
            schemeName,
            reportingDate,
            null,
            sourceUrl,
            DiscoveryStatus.INVALID_DISCLOSURE,
            message != null ? message : "Invalid or unsupported disclosure link: " + sourceUrl,
            null
        );
    }
}
