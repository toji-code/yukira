package com.yukira.backend.controller;

import com.yukira.backend.service.subscription.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks")
public class RazorpayWebhookController {
    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    private final SubscriptionService subscriptionService;

    public RazorpayWebhookController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/razorpay")
    public ResponseEntity<?> handleRazorpayWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) {
        if (signature == null || signature.isBlank()) {
            log.warn("Webhook rejected: Missing X-Razorpay-Signature header.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Missing X-Razorpay-Signature header"));
        }

        try {
            subscriptionService.processWebhook(rawPayload, signature);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (SecurityException se) {
            log.warn("Webhook signature validation failed: {}", se.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid signature"));
        } catch (Exception e) {
            log.error("Error processing Razorpay webhook event: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Webhook processing failure"));
        }
    }
}
