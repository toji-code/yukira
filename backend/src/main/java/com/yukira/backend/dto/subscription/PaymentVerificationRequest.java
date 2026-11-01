package com.yukira.backend.dto.subscription;

public record PaymentVerificationRequest(
        String orderId,
        String paymentId,
        String signature
) {}
