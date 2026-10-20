package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.domain.entity.DataSource;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.repository.DataSourceRepository;
import com.yukira.backend.repository.SourceArtifactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Component providing controlled HTTP source acquisition for authoritative monthly disclosure files.
 *
 * Epistemic & Provenance Rules:
 * 1. HTTPS Enforcement: Production URLs must use HTTPS; http is allowed for local test endpoints.
 * 2. Immutable Source Artifacts: Computed SHA-256 digests identify artifacts; duplicate payloads reuse existing artifacts.
 * 3. Content Validation: Inspects payload headers to reject HTML login/error pages and unsupported binary formats (PDF, Zip, OLE).
 * 4. Safe Failure Handling: Network timeouts, HTTP errors, or format rejections do NOT mutate existing holdings or snapshots.
 */
@Component
@SuppressWarnings("null")
public class AuthoritativeHoldingsSourceFetcher {

    private static final Logger log = LoggerFactory.getLogger(AuthoritativeHoldingsSourceFetcher.class);

    private static final long MAX_PAYLOAD_BYTES = 20L * 1024L * 1024L; // 20 MB max payload size
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final SourceArtifactRepository sourceArtifactRepository;
    private final DataSourceRepository dataSourceRepository;
    private final HttpClient httpClient;

    public AuthoritativeHoldingsSourceFetcher(
        SourceArtifactRepository sourceArtifactRepository,
        DataSourceRepository dataSourceRepository
    ) {
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Acquisition result wrapper containing either a persisted SourceArtifact or an acquisition failure status.
     */
    public record FetchResult(
        SourceArtifact artifact,
        MonthlyHoldingsAcquisitionResult.Status status,
        String statusMessage,
        Integer httpStatusCode,
        byte[] payloadBytes
    ) {
        public boolean isSuccess() {
            return status == MonthlyHoldingsAcquisitionResult.Status.ACQUIRED && artifact != null;
        }
    }

    /**
     * Controlled HTML fetch result wrapper for official source discovery.
     */
    public record HtmlFetchResult(
        String html,
        int statusCode,
        boolean success,
        String errorMessage
    ) {
        public static HtmlFetchResult success(String html, int statusCode) {
            return new HtmlFetchResult(html, statusCode, true, null);
        }

        public static HtmlFetchResult failure(int statusCode, String errorMessage) {
            return new HtmlFetchResult(null, statusCode, false, errorMessage);
        }
    }

    /**
     * Executes safe, bounded HTTP GET to retrieve official AMC HTML disclosure pages.
     */
    public HtmlFetchResult fetchHtml(String url) {
        if (url == null || url.isBlank()) {
            return HtmlFetchResult.failure(400, "Discovery URL is missing or blank");
        }

        String targetUrl = url.trim();
        if (!targetUrl.toLowerCase(Locale.ROOT).startsWith("https://") &&
            !targetUrl.toLowerCase(Locale.ROOT).startsWith("http://localhost") &&
            !targetUrl.toLowerCase(Locale.ROOT).startsWith("http://127.0.0.1")) {
            return HtmlFetchResult.failure(400, "Production discovery URL must use HTTPS: " + targetUrl);
        }

        int maxAttempts = 2;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(targetUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) YUKIRA Quantitative Engine/1.0")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int statusCode = response.statusCode();

                // Retry on transient server errors (502, 503, 504)
                if ((statusCode == 502 || statusCode == 503 || statusCode == 504) && attempt < maxAttempts) {
                    log.warn("Authoritative holdings discovery URL returned transient HTTP {} (attempt {}/{}): {}. Retrying...",
                        statusCode, attempt, maxAttempts, targetUrl);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                // 403 Forbidden / 404 Not Found fail fast immediately without wasteful retries
                if (statusCode != 200) {
                    log.warn("Authoritative holdings discovery URL returned HTTP {}: {}", statusCode, targetUrl);
                    return HtmlFetchResult.failure(statusCode, "HTTP " + statusCode + " returned from " + targetUrl);
                }

                byte[] body = response.body();
                if (body == null || body.length == 0) {
                    return HtmlFetchResult.failure(200, "Empty response received from " + targetUrl);
                }

                if (body.length > 10L * 1024L * 1024L) {
                    return HtmlFetchResult.failure(200, "Response size exceeds 10MB limit: " + body.length + " bytes");
                }

                String html = new String(body, java.nio.charset.StandardCharsets.UTF_8);
                return HtmlFetchResult.success(html, 200);

            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    log.warn("Transient network error fetching discovery HTML from {} (attempt {}/{}): {}. Retrying...",
                        targetUrl, attempt, maxAttempts, e.getMessage());
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        String err = "Network failure fetching discovery HTML: " + (lastException != null ? lastException.getMessage() : "Max retries exceeded");
        log.error("Failed fetching discovery HTML from {}: {}", targetUrl, err);
        return HtmlFetchResult.failure(503, err);
    }

    /**
     * Executes controlled acquisition from an authoritative holdings source descriptor.
     */
    public FetchResult acquireArtifact(MonthlyHoldingsSourceDescriptor descriptor) {
        if (descriptor == null || descriptor.sourceUrl() == null || descriptor.sourceUrl().isBlank()) {
            return new FetchResult(null, MonthlyHoldingsAcquisitionResult.Status.INVALID_RESPONSE, "Source URL is missing or blank", null, null);
        }

        String url = descriptor.sourceUrl().trim();

        // 1. HTTPS enforcement for non-local URLs
        if (!url.toLowerCase(Locale.ROOT).startsWith("https://") &&
            !url.toLowerCase(Locale.ROOT).startsWith("http://localhost") &&
            !url.toLowerCase(Locale.ROOT).startsWith("http://127.0.0.1")) {
            return new FetchResult(null, MonthlyHoldingsAcquisitionResult.Status.INVALID_RESPONSE, "Production acquisition URL must use HTTPS: " + url, null, null);
        }

        int maxAttempts = 2;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) YUKIRA Quantitative Engine/1.0")
                    .header("Accept", "text/csv,text/plain,application/octet-stream;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int statusCode = response.statusCode();

                // Retry on transient server errors (502 Bad Gateway, 503 Service Unavailable, 504 Gateway Timeout)
                if ((statusCode == 502 || statusCode == 503 || statusCode == 504) && attempt < maxAttempts) {
                    log.warn("Authoritative holdings source URL returned transient HTTP {} (attempt {}/{}): {}. Retrying...",
                        statusCode, attempt, maxAttempts, url);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                if (statusCode != 200) {
                    log.warn("Authoritative holdings source URL returned HTTP {}: {}", statusCode, url);
                    return new FetchResult(null, MonthlyHoldingsAcquisitionResult.Status.HTTP_ERROR, "HTTP " + statusCode + " error returned from " + url, statusCode, null);
                }

                byte[] body = response.body();
                return processRawBytes(body, url, descriptor);

            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    log.warn("Transient network error acquiring holdings artifact from {} (attempt {}/{}): {}. Retrying...",
                        url, attempt, maxAttempts, e.getMessage());
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Network or HTTP failure acquiring holdings artifact from {}: {}", url, lastException != null ? lastException.getMessage() : "Unknown");
        return new FetchResult(null, MonthlyHoldingsAcquisitionResult.Status.SOURCE_UNAVAILABLE, "Network failure acquiring source: " + (lastException != null ? lastException.getMessage() : "Max retries exceeded"), null, null);
    }

    /**
     * Processes raw bytes from direct payload streams or HTTP responses.
     */
    public FetchResult processRawBytes(byte[] body, String sourceUri, MonthlyHoldingsSourceDescriptor descriptor) {
        if (body == null || body.length == 0) {
            return new FetchResult(null, MonthlyHoldingsAcquisitionResult.Status.INVALID_RESPONSE, "Empty payload returned from source", 200, null);
        }

        if (body.length > MAX_PAYLOAD_BYTES) {
            return new FetchResult(null, MonthlyHoldingsAcquisitionResult.Status.INVALID_RESPONSE, "Payload size (" + body.length + " bytes) exceeds limit of " + MAX_PAYLOAD_BYTES + " bytes", 200, null);
        }

        // 2. HTML Error / Login Page Detection
        String prefixStr = new String(body, 0, Math.min(body.length, 250), java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        if (prefixStr.contains("<html") || prefixStr.contains("<!doctype") || prefixStr.contains("<head") || prefixStr.contains("captcha") || prefixStr.contains("login")) {
            return new FetchResult(null, MonthlyHoldingsAcquisitionResult.Status.INVALID_RESPONSE, "HTML page or login wall returned instead of disclosure CSV payload", 200, body);
        }

        // 3. Binary Magic Header Detection (PDF vs Supported OpenXML/XLSX)
        if (body.length >= 4) {
            // %PDF
            if (body[0] == 0x25 && body[1] == 0x50 && body[2] == 0x44 && body[3] == 0x46) {
                return new FetchResult(null, MonthlyHoldingsAcquisitionResult.Status.UNSUPPORTED_FORMAT, "PDF binary disclosure format is not directly supported", 200, body);
            }
            // OLE / Legacy XLS (\xD0\xCF\x11\xE0)
            if ((body[0] & 0xFF) == 0xD0 && (body[1] & 0xFF) == 0xCF && (body[2] & 0xFF) == 0x11 && (body[3] & 0xFF) == 0xE0) {
                return new FetchResult(null, MonthlyHoldingsAcquisitionResult.Status.UNSUPPORTED_FORMAT, "Legacy XLS OLE binary disclosure format is not directly supported", 200, body);
            }
            // Note: PK\x03\x04 (Zip / XLSX OpenXML) is fully supported for authoritative spreadsheet acquisition.
        }

        // 4. SHA-256 digest & Immutable SourceArtifact persistence
        String sha256 = computeSha256(body);
        OffsetDateTime retrievalTime = OffsetDateTime.now();

        Optional<SourceArtifact> existingOpt = sourceArtifactRepository.findBySha256Hash(sha256);
        if (existingOpt.isPresent()) {
            return new FetchResult(existingOpt.get(), MonthlyHoldingsAcquisitionResult.Status.ACQUIRED, "Artifact acquired (existing SHA-256 match found)", 200, body);
        }

        DataSource source = dataSourceRepository.findByCode("SEBI_AMC_DISCLOSURE")
            .orElseGet(() -> dataSourceRepository.save(new DataSource(
                "SEBI_AMC_DISCLOSURE",
                "SEBI Monthly Portfolio Disclosure Feed",
                "https://www.amfiindia.com"
            )));

        SourceArtifact artifact = new SourceArtifact(
            source,
            retrievalTime,
            "PORTFOLIO_DISCLOSURE_CSV",
            sha256,
            (long) body.length
        );
        artifact.setStorageUri(sourceUri != null ? sourceUri : (descriptor != null ? descriptor.sourceUrl() : "SEBI_AMC_DISCLOSURE"));
        artifact.setPayloadBlob(body);
        SourceArtifact savedArtifact = sourceArtifactRepository.save(artifact);

        return new FetchResult(savedArtifact, MonthlyHoldingsAcquisitionResult.Status.ACQUIRED, "Artifact acquired and persisted successfully", 200, body);
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
