package com.yukira.backend.ingestion.nse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yukira.backend.domain.entity.DataSource;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.DataSourceRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class NiftySourceClient {

    private static final String DEFAULT_POST_URL = "https://www.niftyindices.com/BackPage/getTotalReturnIndexString";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DataSourceRepository dataSourceRepository;
    private final SourceArtifactRepository sourceArtifactRepository;

    public NiftySourceClient(DataSourceRepository dataSourceRepository, SourceArtifactRepository sourceArtifactRepository) {
        this.dataSourceRepository = dataSourceRepository;
        this.sourceArtifactRepository = sourceArtifactRepository;
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

        DataSource dataSource = dataSourceRepository.findByCode("NSE_INDICES")
            .orElseGet(() -> dataSourceRepository.save(new DataSource(
                "NSE_INDICES", "NSE Indices Limited", "NSE"
            )));

        SourceArtifact artifact = new SourceArtifact();
        artifact.setDataSource(dataSource);
        artifact.setArtifactType("BENCHMARK_TRI_JSON");
        artifact.setStorageUri(sourceUri);
        artifact.setSha256Hash(sha256);
        artifact.setByteSize((long) payloadBytes.length);
        artifact.setRetrievalTimestamp(OffsetDateTime.now());
        artifact.setPayloadBlob(payloadBytes);

        return sourceArtifactRepository.save(artifact);
    }

    /**
     * Fetches raw Total Return Index (TRI) observations from official Nifty Indices POST endpoint.
     */
    public SourceArtifact fetchAndPersistArtifact(String indexName, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate must not be null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
        if (indexName == null || indexName.isBlank()) {
            indexName = "Nifty 500";
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
        String sDateStr = startDate.format(dtf);
        String eDateStr = endDate.format(dtf);

        String sourceUri = String.format("%s?name=%s&startDate=%s&endDate=%s", DEFAULT_POST_URL, indexName, sDateStr, eDateStr);

        try {
            Map<String, String> cinfoMap = new HashMap<>();
            cinfoMap.put("name", indexName);
            cinfoMap.put("startDate", sDateStr);
            cinfoMap.put("endDate", eDateStr);
            cinfoMap.put("indexName", indexName);

            Map<String, String> payloadMap = new HashMap<>();
            payloadMap.put("cinfo", OBJECT_MAPPER.writeValueAsString(cinfoMap));
            byte[] postBody = OBJECT_MAPPER.writeValueAsBytes(payloadMap);

            URL url = new java.net.URI(DEFAULT_POST_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            conn.setRequestProperty("Origin", "https://www.niftyindices.com");
            conn.setRequestProperty("Referer", "https://www.niftyindices.com/reports/historical-data");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(postBody);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("HTTP " + responseCode + " received from Nifty Indices endpoint: " + DEFAULT_POST_URL);
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream is = conn.getInputStream()) {
                byte[] data = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, bytesRead);
                }
            }

            byte[] payloadBytes = buffer.toByteArray();
            if (payloadBytes.length == 0) {
                throw new RuntimeException("Empty payload received from Nifty Indices: " + sourceUri);
            }

            return createOrGetSourceArtifact(sourceUri, payloadBytes);

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch official Nifty TRI artifact: " + e.getMessage(), e);
        }
    }

    public static String calculateSha256(byte[] data) {
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
