package com.yukira.backend.dto.subscription;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record BillingRecordDto(
        Long id,
        String planId,
        String planName,
        String provider,
        String providerPaymentId,
        String providerOrderId,
        String providerInvoiceId,
        String providerInvoiceUrl,
        BigDecimal amountInr,
        String currency,
        String paymentStatus,
        String billingReason,
        String failureReason,
        ZonedDateTime periodStart,
        ZonedDateTime periodEnd,
        ZonedDateTime createdAt,
        boolean invoiceAvailable,
        String invoiceStatusMessage
) {}
