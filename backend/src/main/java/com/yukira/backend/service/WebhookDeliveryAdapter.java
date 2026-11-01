package com.yukira.backend.service;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorMonitoringEvent;
import com.yukira.backend.domain.entity.InvestorWebhookConfig;
import com.yukira.backend.repository.InvestorWebhookConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Signed HMAC outbound webhook delivery adapter.
 *
 * Security & Reliability Directives:
 * 1. Signs every outbound payload using HMAC-SHA256 with the registered endpoint secret.
 * 2. Transmits signature in 'X-Yukira-Signature', event ID in 'X-Yukira-Event-Id', and epoch in 'X-Yukira-Timestamp'.
 * 3. Never leaks internal secrets or cross-investor portfolio data in payload.
 * 4. Tracks failure count per endpoint; automatically transitions to SUSPENDED after 5 consecutive failures.
 */
@Component
public class WebhookDeliveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryAdapter.class);

    private final InvestorWebhookConfigRepository webhookRepository;
    private final HttpClient httpClient;

    @Autowired
    public WebhookDeliveryAdapter(InvestorWebhookConfigRepository webhookRepository) {
        this.webhookRepository = webhookRepository;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public boolean sendSignedWebhook(Investor investor, String endpointUrl, String payloadJson, InvestorMonitoringEvent event) {
        List<InvestorWebhookConfig> configs = webhookRepository.findByInvestorAndIsActiveTrue(investor);
        InvestorWebhookConfig matchingConfig = configs.stream()
            .filter(c -> c.getEndpointUrl().equalsIgnoreCase(endpointUrl))
            .findFirst()
            .orElse(null);

        if (matchingConfig == null) {
            log.warn("No active webhook config found for investor ID {} and endpoint {}", investor.getId(), endpointUrl);
            return false;
        }

        if ("SUSPENDED".equalsIgnoreCase(matchingConfig.getStatus())) {
            log.warn("Webhook endpoint {} for investor ID {} is suspended due to excessive failures.", endpointUrl, investor.getId());
            return false;
        }

        long timestamp = Instant.now().getEpochSecond();
        String signature = computeHmacSha256(payloadJson, matchingConfig.getSecretHash(), timestamp);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .header("Content-Type", "application/json")
                .header("User-Agent", "YUKIRA-Webhook-Dispatcher/1.0")
                .header("X-Yukira-Signature", signature)
                .header("X-Yukira-Timestamp", String.valueOf(timestamp))
                .header("X-Yukira-Event-Id", String.valueOf(event != null && event.getId() != null ? event.getId() : 0))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                .build();

            // Note: In local/offline or test environments without reachable remote host, catch connection failure gracefully
            log.info("Dispatching signed webhook to {} for investor ID {} (Signature: {})...", endpointUrl, investor.getId(), signature.substring(0, Math.min(16, signature.length())));
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                matchingConfig.setFailureCount(0);
                webhookRepository.save(matchingConfig);
                return true;
            } else {
                handleFailure(matchingConfig, "HTTP response " + status);
                return false;
            }
        } catch (Exception e) {
            log.warn("Webhook delivery error for endpoint {}: {}", endpointUrl, e.getMessage());
            handleFailure(matchingConfig, e.getMessage());
            return false;
        }
    }

    private void handleFailure(InvestorWebhookConfig config, String reason) {
        int failures = config.getFailureCount() != null ? config.getFailureCount() + 1 : 1;
        config.setFailureCount(failures);
        if (failures >= 5) {
            config.setStatus("SUSPENDED");
            config.setIsActive(false);
            log.warn("Webhook endpoint {} suspended after {} consecutive delivery failures.", config.getEndpointUrl(), failures);
        }
        webhookRepository.save(config);
    }

    public static String computeHmacSha256(String payload, String secret, long timestamp) {
        try {
            String message = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] bytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            return "error_signature";
        }
    }
}

