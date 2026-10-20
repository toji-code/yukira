package com.yukira.backend.ingestion.holdings;

import java.time.LocalDate;

/**
 * Adapter interface for AMC-specific monthly portfolio disclosure source resolution.
 */
public interface AuthoritativeHoldingsSourceAdapter {
    boolean supports(String amcCode);
    MonthlyHoldingsSourceDescriptor getSourceDescriptor(String amcCode, String amfiCode, LocalDate reportingDate);
}
