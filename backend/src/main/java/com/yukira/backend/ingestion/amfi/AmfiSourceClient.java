package com.yukira.backend.ingestion.amfi;

import com.yukira.backend.domain.entity.DataSource;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.DataSourceRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@Component
public class AmfiSourceClient {

    private static final String BASE_URL = "https://portal.amfiindia.com/DownloadNAVHistoryReport_Po.aspx";
    private static final DateTimeFormatter AMFI_URL_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final DataSourceRepository dataSourceRepository;
    private final SourceArtifactRepository sourceArtifactRepository;

    public AmfiSourceClient(DataSourceRepository dataSourceRepository, SourceArtifactRepository sourceArtifactRepository) {
        this.dataSourceRepository = dataSourceRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
    }

    /**
     * Constructs deterministic query URL for scheme and date range with optional AMC mutual fund code.
     * When amcMfCode is provided (e.g. "9" for HDFC Mutual Fund), AMFI scopes the response to that AMC,
     * preventing excessive payload sizes and HTTP timeouts on large multi-year date ranges.
     */
    public String buildUrl(String amfiSchemeCode, String amcMfCode, LocalDate startDate, LocalDate endDate) {
        if (amfiSchemeCode == null || amfiSchemeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("amfiSchemeCode must not be null or empty");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate must not be null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
        String fromStr = startDate.format(AMFI_URL_DATE);
        String toStr = endDate.format(AMFI_URL_DATE);
        String mfParam = (amcMfCode != null && !amcMfCode.isBlank()) ? amcMfCode.trim() : "";
        return String.format("%s?mf=%s&scheme=%s&frmdt=%s&todt=%s", BASE_URL, mfParam, amfiSchemeCode.trim(), fromStr, toStr);
    }

    /**
     * Constructs deterministic query URL for scheme and date range without AMC filtering.
     * Backwards-compatible overload.
     */
    public String buildUrl(String amfiSchemeCode, LocalDate startDate, LocalDate endDate) {
        return buildUrl(amfiSchemeCode, null, startDate, endDate);
    }

    /**
     * Executes HTTP GET and captures exact raw bytes BEFORE parsing with optional AMC filtering.
     * Computes SHA-256 hash across full payload and performs deterministic deduplication.
     */
    public SourceArtifact fetchAndPersistArtifact(String amfiSchemeCode, String amcMfCode, LocalDate startDate, LocalDate endDate) {
        String targetUrl = buildUrl(amfiSchemeCode, amcMfCode, startDate, endDate);
        OffsetDateTime retrievalTimestamp = OffsetDateTime.now();

        byte[] rawBytes = executeHttpGet(targetUrl);
        String sha256Hash = computeSha256(rawBytes);

        // Deduplication: if exact SHA-256 hash already exists, return existing artifact
        Optional<SourceArtifact> existing = sourceArtifactRepository.findBySha256Hash(sha256Hash);
        if (existing.isPresent()) {
            return existing.get();
        }

        DataSource amfiSource = dataSourceRepository.findByCode("AMFI_PORTAL")
            .orElseGet(() -> dataSourceRepository.save(new DataSource("AMFI_PORTAL", "AMFI NAV Historical Portal", "AMFI")));

        SourceArtifact artifact = new SourceArtifact(
            amfiSource,
            retrievalTimestamp,
            "NAV_HISTORY_TEXT",
            sha256Hash,
            (long) rawBytes.length
        );
        artifact.setStorageUri(targetUrl);
        artifact.setPayloadBlob(rawBytes);

        return sourceArtifactRepository.save(artifact);
    }

    /**
     * Executes HTTP GET and captures exact raw bytes BEFORE parsing without AMC filtering.
     * Backwards-compatible overload.
     */
    public SourceArtifact fetchAndPersistArtifact(String amfiSchemeCode, LocalDate startDate, LocalDate endDate) {
        return fetchAndPersistArtifact(amfiSchemeCode, null, startDate, endDate);
    }

    /**
     * Persists a mock or fixture raw byte payload directly into source_artifact with exact provenance.
     * Used for deterministic offline tests and test fixtures.
     */
    public SourceArtifact persistRawPayload(byte[] rawBytes, String sourceUri, OffsetDateTime retrievalTimestamp) {
        if (rawBytes == null) {
            throw new IllegalArgumentException("rawBytes must not be null");
        }
        String sha256Hash = computeSha256(rawBytes);
        Optional<SourceArtifact> existing = sourceArtifactRepository.findBySha256Hash(sha256Hash);
        if (existing.isPresent()) {
            return existing.get();
        }

        DataSource amfiSource = dataSourceRepository.findByCode("AMFI_PORTAL")
            .orElseGet(() -> dataSourceRepository.save(new DataSource("AMFI_PORTAL", "AMFI NAV Historical Portal", "AMFI")));

        SourceArtifact artifact = new SourceArtifact(
            amfiSource,
            retrievalTimestamp != null ? retrievalTimestamp : OffsetDateTime.now(),
            "NAV_HISTORY_TEXT",
            sha256Hash,
            (long) rawBytes.length
        );
        artifact.setStorageUri(sourceUri);
        artifact.setPayloadBlob(rawBytes);

        return sourceArtifactRepository.save(artifact);
    }

    private byte[] executeHttpGet(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new java.net.URI(urlString).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("AMFI portal returned HTTP status: " + responseCode + " for " + urlString);
            }

            byte[] payload;
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                payload = out.toByteArray();
            }

            String prefix = new String(payload, 0, Math.min(payload.length, 120), java.nio.charset.StandardCharsets.ISO_8859_1).trim();
            if (prefix.startsWith("<") || prefix.contains("<html") || prefix.contains("<!DOCTYPE")) {
                throw new IllegalStateException("AMFI portal returned HTML page instead of NAV text stream for " + urlString);
            }

            return payload;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch raw artifact from AMFI portal: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
