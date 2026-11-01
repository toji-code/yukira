package com.yukira.backend.service.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class RazorpayGatewayService {
    private static final Logger log = LoggerFactory.getLogger(RazorpayGatewayService.class);
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    @Value("${yukira.razorpay.key-id:}")
    private String keyId;

    @Value("${yukira.razorpay.key-secret:}")
    private String keySecret;

    @Value("${yukira.razorpay.webhook-secret:}")
    private String webhookSecret;

    @Value("${yukira.razorpay.mode:test}")
    private String mode;

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }

    public boolean isWebhookConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    public String getKeyId() {
        return keyId;
    }

    public String getMode() {
        return mode;
    }

    /**
     * Verifies the payment signature returned by the client-side Razorpay modal.
     * Expected signature = HMAC-SHA256(orderId + "|" + paymentId, keySecret)
     */
    public boolean verifyPaymentSignature(String orderId, String paymentId, String receivedSignature) {
        if (!isConfigured() || orderId == null || paymentId == null || receivedSignature == null) {
            log.warn("Payment signature verification aborted: Gateway unconfigured or null inputs.");
            return false;
        }

        try {
            String payload = orderId + "|" + paymentId;
            String expectedSignature = calculateHmacSha256(payload, keySecret);
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    receivedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error computing payment signature HMAC-SHA256: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifies the webhook signature sent in the X-Razorpay-Signature header.
     * Expected signature = HMAC-SHA256(rawPayload, webhookSecret)
     */
    public boolean verifyWebhookSignature(String rawPayload, String receivedSignature) {
        if (!isWebhookConfigured() || rawPayload == null || receivedSignature == null) {
            log.warn("Webhook signature verification aborted: Webhook secret unconfigured or null inputs.");
            return false;
        }

        try {
            String expectedSignature = calculateHmacSha256(rawPayload, webhookSecret);
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    receivedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error computing webhook signature HMAC-SHA256: {}", e.getMessage());
            return false;
        }
    }

    private String calculateHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
        mac.init(secretKey);
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(rawHmac);
    }
}
