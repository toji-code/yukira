package com.yukira.backend.ingestion.fbil;

import com.yukira.backend.domain.entity.DataSource;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.DataSourceRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@Component
public class FbilSourceClient {

    private static final String DEFAULT_BASE_URL = "https://www.fbil.org.in/wasdm/tbill/fetchfiltered";

    private final DataSourceRepository dataSourceRepository;
    private final SourceArtifactRepository sourceArtifactRepository;

    public FbilSourceClient(DataSourceRepository dataSourceRepository, SourceArtifactRepository sourceArtifactRepository) {
        this.dataSourceRepository = dataSourceRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
    }

    public String buildUrl(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate must not be null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);
        return String.format("%s?fromDate=%s&toDate=%s&authenticated=false", DEFAULT_BASE_URL, startDate.format(dtf), endDate.format(dtf));
    }

    /**
     * Creates or retrieves an existing SourceArtifact by SHA-256 for a given payload.
     * Guarantees cryptographic immutability and exact provenance.
     */
    public SourceArtifact createOrGetSourceArtifact(String sourceUri, byte[] payloadBytes) {
        if (sourceUri == null || sourceUri.isBlank()) {
            throw new IllegalArgumentException("sourceUri must not be null or blank");
        }
        if (payloadBytes == null || payloadBytes.length == 0) {
            throw new IllegalArgumentException("payloadBytes must not be empty");
        }

        String sha256 = calculateSha256(payloadBytes);
        Optional<SourceArtifact> existing = sourceArtifactRepository.findBySha256Hash(sha256);
        if (existing.isPresent()) {
            return existing.get();
        }

        DataSource dataSource = dataSourceRepository.findByCode("FBIL")
            .orElseGet(() -> dataSourceRepository.save(new DataSource("FBIL", "Financial Benchmarks India Pvt Ltd", "FBIL")));

        SourceArtifact artifact = new SourceArtifact(
            dataSource,
            OffsetDateTime.now(),
            "FBIL_TBILL_BENCHMARK",
            sha256,
            (long) payloadBytes.length
        );
        artifact.setStorageUri(sourceUri);
        artifact.setPayloadBlob(payloadBytes);

        return sourceArtifactRepository.save(artifact);
    }

    public SourceArtifact fetchAndPersistArtifact(LocalDate startDate, LocalDate endDate) {
        String targetUrl = buildUrl(startDate, endDate);
        byte[] rawBytes = executeHttpGet(targetUrl);
        return createOrGetSourceArtifact(targetUrl, rawBytes);
    }

    private byte[] executeHttpGet(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("FBIL portal returned HTTP status: " + responseCode + " for " + urlString);
            }

            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                return out.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch raw artifact from FBIL portal: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public String calculateSha256(byte[] data) {
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
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
