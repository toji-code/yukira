package com.yukira.backend.ingestion.holdings;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Immutable descriptor for an authoritative monthly portfolio holdings disclosure source.
 *
 * Epistemic Rules:
 * 1. Identifies the target AMC, scheme, AMFI code, ISIN, and authoritative source URI.
 * 2. Does NOT contain or replace actual financial holdings data (the downloaded raw artifact remains authoritative).
 * 3. Enforces valid source URL and non-null identifiers.
 * 4. Preserves point-in-time statutory availability semantics (SEBI 10th of succeeding month disclosure mandate).
 */
public record MonthlyHoldingsSourceDescriptor(
    String amcCode,
    String amcName,
    String schemeCode,
    String schemeName,
    String amfiCode,
    String isin,
    Long targetSchemeOptionId,
    String sourceUrl,
    String expectedContentType,
    String disclosureFormat,
    LocalDate explicitReportingDate,
    OffsetDateTime explicitAvailabilityTime,
    String sourceProviderId,
    String acquisitionMethod,
    String parserVersion
) {
    public MonthlyHoldingsSourceDescriptor {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be null or blank");
        }
    }

    public MonthlyHoldingsSourceDescriptor(
        String amcCode,
        String amcName,
        String schemeCode,
        String schemeName,
        String amfiCode,
        String isin,
        Long targetSchemeOptionId,
        String sourceUrl,
        String expectedContentType,
        String disclosureFormat,
        LocalDate explicitReportingDate,
        String sourceProviderId,
        String acquisitionMethod,
        String parserVersion
    ) {
        this(
            amcCode,
            amcName,
            schemeCode,
            schemeName,
            amfiCode,
            isin,
            targetSchemeOptionId,
            sourceUrl,
            expectedContentType,
            disclosureFormat,
            explicitReportingDate,
            null,
            sourceProviderId,
            acquisitionMethod,
            parserVersion
        );
    }

    /**
     * Resolves the authoritative point-in-time publication/availability timestamp:
     * 1. Uses explicitAvailabilityTime if configured.
     * 2. Defaults to the SEBI statutory monthly disclosure deadline (10th of succeeding month at 23:59:59+05:30).
     */
    public OffsetDateTime resolvedAvailabilityTime(LocalDate portfolioDate) {
        if (explicitAvailabilityTime != null) {
            return explicitAvailabilityTime;
        }
        LocalDate date = portfolioDate != null ? portfolioDate : explicitReportingDate;
        if (date != null) {
            return date.plusMonths(1).withDayOfMonth(10).atTime(23, 59, 59, 0).atOffset(ZoneOffset.ofHoursMinutes(5, 30));
        }
        return OffsetDateTime.now();
    }

    public static MonthlyHoldingsSourceDescriptor of(
        String amcCode,
        String amcName,
        String schemeCode,
        String schemeName,
        String amfiCode,
        String isin,
        String sourceUrl,
        LocalDate explicitReportingDate
    ) {
        return new MonthlyHoldingsSourceDescriptor(
            amcCode,
            amcName,
            schemeCode,
            schemeName,
            amfiCode,
            isin,
            null,
            sourceUrl,
            "text/csv",
            "CSV",
            explicitReportingDate,
            null,
            "SEBI_AMC_DISCLOSURE",
            "DIRECT_HTTP",
            "V1_CSV"
        );
    }

    public static MonthlyHoldingsSourceDescriptor of(
        String amcCode,
        String amcName,
        String schemeCode,
        String schemeName,
        String amfiCode,
        String isin,
        String sourceUrl,
        LocalDate explicitReportingDate,
        OffsetDateTime availabilityTime
    ) {
        return new MonthlyHoldingsSourceDescriptor(
            amcCode,
            amcName,
            schemeCode,
            schemeName,
            amfiCode,
            isin,
            null,
            sourceUrl,
            "text/csv",
            "CSV",
            explicitReportingDate,
            availabilityTime,
            "SEBI_AMC_DISCLOSURE",
            "DIRECT_HTTP",
            "V1_CSV"
        );
    }
}
