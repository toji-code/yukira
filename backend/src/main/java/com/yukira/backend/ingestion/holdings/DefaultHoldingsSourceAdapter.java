package com.yukira.backend.ingestion.holdings;

import org.springframework.stereotype.Component;
import java.time.LocalDate;

/**
 * Standard default holdings source adapter supporting generic disclosure endpoints.
 */
@Component
public class DefaultHoldingsSourceAdapter implements AuthoritativeHoldingsSourceAdapter {

    @Override
    public boolean supports(String amcCode) {
        return true; // Fallback adapter supports all AMCs
    }

    @Override
    public MonthlyHoldingsSourceDescriptor getSourceDescriptor(String amcCode, String amfiCode, LocalDate reportingDate) {
        String safeAmc = amcCode != null ? amcCode : "GENERIC_AMC";
        String safeAmfi = amfiCode != null ? amfiCode : "118955";
        String url = String.format("https://portal.amfiindia.com/disclosures/monthly-holdings-%s.csv", safeAmfi);

        return new MonthlyHoldingsSourceDescriptor(
            safeAmc,
            safeAmc + " Asset Management",
            "SCHEME_" + safeAmfi,
            "Mutual Fund Scheme #" + safeAmfi,
            safeAmfi,
            null,
            null,
            url,
            "text/csv",
            "CSV",
            reportingDate,
            "SEBI_AMC_DISCLOSURE",
            "DIRECT_HTTP",
            "V1_CSV"
        );
    }
}
