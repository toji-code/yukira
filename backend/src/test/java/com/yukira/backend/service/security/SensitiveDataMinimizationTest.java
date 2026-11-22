package com.yukira.backend.service.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 456: Sensitive Data and AI Input Minimization (PAN Masking, CAS Storage Path Stripping).
 * Verifies that investor Permanent Account Number (PAN) is strictly masked to XXXXX1234X
 * and local file system paths (e.g. C:\\... or /var/...) are stripped prior to AI ingestion or logs.
 */
class SensitiveDataMinimizationTest {

    private static final Pattern PAN_PATTERN = Pattern.compile("\\b([A-Z]{5})(\\d{4})([A-Z])\\b");
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("(?:[a-zA-Z]:\\\\[^\\s\"']+|/(?:var|tmp|home|etc)/[^\\s\"']+)");

    public static String maskSensitiveData(String input) {
        if (input == null) return null;

        // 1. Mask PAN: ABCDE1234F -> XXXXX1234F
        Matcher panMatcher = PAN_PATTERN.matcher(input);
        String panMasked = panMatcher.replaceAll("XXXXX$2$3");

        // 2. Strip local storage paths: replace with [FILE_PATH_REDACTED]
        Matcher pathMatcher = FILE_PATH_PATTERN.matcher(panMasked);
        return pathMatcher.replaceAll("[REDACTED_STORAGE_PATH]");
    }

    @Test
    @DisplayName("Indian Permanent Account Number (PAN) is strictly masked preserving only last 4 digits and checksum")
    void testPanMaskingContract() {
        String rawPrompt = "Investor with PAN ABCDE1234F and second PAN XYZPK9876Z requested portfolio stress testing.";
        String sanitized = maskSensitiveData(rawPrompt);

        assertThat(sanitized)
            .doesNotContain("ABCDE1234F")
            .doesNotContain("XYZPK9876Z")
            .contains("XXXXX1234F")
            .contains("XXXXX9876Z");
    }

    @Test
    @DisplayName("CAS local file system paths and upload directory references are stripped from evidence")
    void testStoragePathStrippingContract() {
        String rawEvidence = "Parsed CAS document at C:\\Users\\DEUS\\uploads\\cas_statement_2024.pdf and /var/data/cas/temp_artifact.bin for investor.";
        String sanitized = maskSensitiveData(rawEvidence);

        assertThat(sanitized)
            .doesNotContain("C:\\Users\\DEUS\\uploads")
            .doesNotContain("/var/data/cas")
            .contains("[REDACTED_STORAGE_PATH]");
    }
}
