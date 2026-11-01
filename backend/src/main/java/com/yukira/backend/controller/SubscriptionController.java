package com.yukira.backend.controller;

import com.yukira.backend.dto.subscription.*;
import com.yukira.backend.service.subscription.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Public endpoint: list active commercial plans and included capabilities.
     */
    @GetMapping("/api/subscription/plans")
    public List<SubscriptionPlanDto> getAvailablePlans() {
        return subscriptionService.getAvailablePlans();
    }

    /**
     * Authenticated endpoint: retrieve current investor subscription and entitlement status.
     */
    @GetMapping("/api/investor/subscription")
    public InvestorSubscriptionDto getInvestorSubscription(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return subscriptionService.getInvestorSubscription(subject);
    }

    /**
     * Authenticated endpoint: initiate checkout for a selected plan.
     */
    @PostMapping("/api/investor/subscription/checkout")
    public CheckoutInitiateResponse initiateCheckout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CheckoutInitiateRequest req
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return subscriptionService.initiateCheckout(subject, req);
    }

    /**
     * Authenticated endpoint: cryptographically verify client-side payment signature.
     */
    @PostMapping("/api/investor/subscription/verify")
    public ResponseEntity<InvestorSubscriptionDto> verifyPayment(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody PaymentVerificationRequest req
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        InvestorSubscriptionDto updated = subscriptionService.verifyPayment(subject, req);
        return ResponseEntity.ok(updated);
    }

    /**
     * Authenticated endpoint: retrieve immutable billing transactions for the authenticated investor.
     */
    @GetMapping("/api/investor/billing/history")
    public List<BillingRecordDto> getBillingHistory(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt != null ? jwt.getSubject() : null;
        return subscriptionService.getBillingHistory(subject);
    }

    /**
     * Authenticated endpoint: retrieve authoritative provider invoice URL for an owned billing record.
     */
    @GetMapping("/api/investor/billing/records/{id}/invoice")
    public ResponseEntity<java.util.Map<String, String>> getInvoiceUrl(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") Long recordId
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        String url = subscriptionService.getInvoiceUrl(subject, recordId);
        return ResponseEntity.ok(java.util.Map.of("invoiceUrl", url));
    }

    /**
     * Authenticated endpoint: self-service cancellation scheduling for recurring subscription.
     */
    @PostMapping("/api/investor/subscription/cancel")
    public ResponseEntity<SubscriptionActionResponse> cancelSubscription(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) SubscriptionCancelRequest req
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        SubscriptionActionResponse response = subscriptionService.cancelSubscription(
                subject,
                req != null ? req : new SubscriptionCancelRequest(null)
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Authenticated endpoint: self-service reactivation for scheduled cancelled subscription.
     */
    @PostMapping("/api/investor/subscription/reactivate")
    public ResponseEntity<SubscriptionActionResponse> reactivateSubscription(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String subject = jwt != null ? jwt.getSubject() : null;
        SubscriptionActionResponse response = subscriptionService.reactivateSubscription(subject);
        return ResponseEntity.ok(response);
    }
}
