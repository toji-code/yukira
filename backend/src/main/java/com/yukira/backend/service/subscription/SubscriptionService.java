package com.yukira.backend.service.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorBillingRecord;
import com.yukira.backend.domain.entity.InvestorSubscription;
import com.yukira.backend.domain.entity.SubscriptionPlan;
import com.yukira.backend.domain.entity.SubscriptionWebhookEvent;
import com.yukira.backend.dto.subscription.*;
import com.yukira.backend.repository.InvestorBillingRecordRepository;
import com.yukira.backend.repository.InvestorSubscriptionRepository;
import com.yukira.backend.repository.SubscriptionPlanRepository;
import com.yukira.backend.repository.SubscriptionWebhookEventRepository;
import com.yukira.backend.service.InvestorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionPlanRepository planRepository;
    private final InvestorSubscriptionRepository subscriptionRepository;
    private final SubscriptionWebhookEventRepository webhookEventRepository;
    private final InvestorService investorService;
    private final RazorpayGatewayService razorpayGatewayService;
    private final InvestorBillingRecordRepository billingRecordRepository;
    private final EntitlementService entitlementService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public SubscriptionService(
            SubscriptionPlanRepository planRepository,
            InvestorSubscriptionRepository subscriptionRepository,
            SubscriptionWebhookEventRepository webhookEventRepository,
            InvestorService investorService,
            RazorpayGatewayService razorpayGatewayService,
            InvestorBillingRecordRepository billingRecordRepository,
            EntitlementService entitlementService
    ) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.investorService = investorService;
        this.razorpayGatewayService = razorpayGatewayService;
        this.billingRecordRepository = billingRecordRepository;
        this.entitlementService = entitlementService;
    }

    public SubscriptionService(
            SubscriptionPlanRepository planRepository,
            InvestorSubscriptionRepository subscriptionRepository,
            SubscriptionWebhookEventRepository webhookEventRepository,
            InvestorService investorService,
            RazorpayGatewayService razorpayGatewayService,
            InvestorBillingRecordRepository billingRecordRepository
    ) {
        this(planRepository, subscriptionRepository, webhookEventRepository, investorService, razorpayGatewayService, billingRecordRepository, null);
    }

    public SubscriptionService(
            SubscriptionPlanRepository planRepository,
            InvestorSubscriptionRepository subscriptionRepository,
            SubscriptionWebhookEventRepository webhookEventRepository,
            InvestorService investorService,
            RazorpayGatewayService razorpayGatewayService
    ) {
        this(planRepository, subscriptionRepository, webhookEventRepository, investorService, razorpayGatewayService, null, null);
    }

    public List<SubscriptionPlanDto> getAvailablePlans() {
        return planRepository.findByIsActiveTrue().stream()
                .map(this::mapToPlanDto)
                .collect(Collectors.toList());
    }

    public InvestorSubscriptionDto getInvestorSubscription(String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorSubscription sub = subscriptionRepository.findByInvestorId(investor.getId())
                .orElseGet(() -> createDefaultFreeSubscription(investor));
        reconcileLifecycleState(sub);
        return mapToSubscriptionDto(sub);
    }

    public CheckoutInitiateResponse initiateCheckout(String auth0Subject, CheckoutInitiateRequest req) {
        if (req == null || req.planId() == null || req.planId().isBlank()) {
            throw new IllegalArgumentException("Plan identifier is required for checkout initiation.");
        }

        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        SubscriptionPlan plan = planRepository.findByIdAndIsActiveTrue(req.planId())
                .orElseThrow(() -> new IllegalArgumentException("Requested plan '" + req.planId() + "' not found or inactive."));

        InvestorSubscription sub = subscriptionRepository.findByInvestorId(investor.getId())
                .orElseGet(() -> createDefaultFreeSubscription(investor));

        // Free plan requires no payment gateway interaction
        if ("FREE".equalsIgnoreCase(plan.getId())) {
            sub.setPlan(plan);
            sub.setStatus("ACTIVE");
            sub.setPaymentStatus("PAID");
            sub.setEntitlementStatus("STANDARD");
            sub.setProviderOrderId(null);
            sub.setProviderPaymentId(null);
            subscriptionRepository.save(sub);
            return new CheckoutInitiateResponse(
                    "SUCCESS",
                    null,
                    0L,
                    "INR",
                    null,
                    plan.getId(),
                    plan.getName(),
                    true,
                    "Plan updated to Community Explorer (Free)."
            );
        }

        // Paid plan requested
        long amountPaise = plan.getPriceInr().multiply(new BigDecimal("100")).longValue();

        if (!razorpayGatewayService.isConfigured()) {
            log.info("Checkout initiated for plan '{}' while Razorpay gateway is unconfigured. Reporting unconfigured status.", plan.getId());
            sub.setPlan(plan);
            sub.setStatus("CHECKOUT_INITIATED");
            sub.setPaymentStatus("PENDING");
            sub.setEntitlementStatus("STANDARD"); // Never grant premium access on unconfigured gateway
            subscriptionRepository.save(sub);

            return CheckoutInitiateResponse.unconfigured(
                    plan.getId(),
                    plan.getName(),
                    "Razorpay payment gateway credentials are not yet configured in this environment. In accordance with YUKIRA governance, live transactions cannot proceed and simulated payments are strictly prohibited."
            );
        }

        // When Razorpay is configured: generate order ID and link to subscription
        String generatedOrderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        sub.setPlan(plan);
        sub.setProvider("RAZORPAY");
        sub.setProviderOrderId(generatedOrderId);
        sub.setStatus("CHECKOUT_INITIATED");
        sub.setPaymentStatus("PENDING");
        sub.setEntitlementStatus("STANDARD");
        subscriptionRepository.save(sub);

        return CheckoutInitiateResponse.success(
                generatedOrderId,
                amountPaise,
                "INR",
                razorpayGatewayService.getKeyId(),
                plan.getId(),
                plan.getName()
        );
    }

    public InvestorSubscriptionDto verifyPayment(String auth0Subject, PaymentVerificationRequest req) {
        if (req == null || req.orderId() == null || req.paymentId() == null || req.signature() == null) {
            throw new IllegalArgumentException("Order ID, payment ID, and cryptographic signature are required.");
        }

        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorSubscription sub = subscriptionRepository.findByInvestorId(investor.getId())
                .orElseThrow(() -> new IllegalStateException("No subscription record found for investor."));

        if (!req.orderId().equals(sub.getProviderOrderId())) {
            throw new IllegalArgumentException("Order ID does not match active checkout session for this investor.");
        }

        boolean verified = razorpayGatewayService.verifyPaymentSignature(req.orderId(), req.paymentId(), req.signature());
        if (!verified) {
            log.warn("Payment verification failed: Invalid HMAC-SHA256 signature for order {}", req.orderId());
            sub.setStatus("PAYMENT_FAILED");
            sub.setPaymentStatus("FAILED");
            subscriptionRepository.save(sub);
            throw new SecurityException("Cryptographic payment signature verification failed.");
        }

        ZonedDateTime now = ZonedDateTime.now();
        boolean hasActivePeriod = sub.getCurrentPeriodEnd() != null && sub.getCurrentPeriodEnd().isAfter(now);
        ZonedDateTime baseTime = hasActivePeriod ? sub.getCurrentPeriodEnd() : now;
        ZonedDateTime periodEnd = "ANNUAL".equalsIgnoreCase(sub.getPlan().getBillingInterval())
                ? baseTime.plusYears(1)
                : baseTime.plusMonths(1);

        sub.setStatus("ACTIVE");
        sub.setPaymentStatus("PAID");
        sub.setEntitlementStatus("ACTIVE_PRO");
        sub.setCancelAtPeriodEnd(false);
        sub.setProviderPaymentId(req.paymentId());
        if (sub.getCurrentPeriodStart() == null || !hasActivePeriod) {
            sub.setCurrentPeriodStart(now);
        }
        sub.setCurrentPeriodEnd(periodEnd);
        InvestorSubscription saved = subscriptionRepository.save(sub);

        // Record immutable billing transaction
        recordPaymentTransaction(investor, saved, req.orderId(), req.paymentId(), "PAID", hasActivePeriod ? "RENEWAL" : "INITIAL_CHECKOUT", null);

        log.info("Payment verified and subscription activated for investor {} (Plan: {})", investor.getId(), sub.getPlan().getId());
        return mapToSubscriptionDto(saved);
    }

    public List<BillingRecordDto> getBillingHistory(String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        if (billingRecordRepository == null) {
            return List.of();
        }
        return billingRecordRepository.findByInvestorIdOrderByCreatedAtDesc(investor.getId()).stream()
                .map(this::mapToBillingRecordDto)
                .collect(Collectors.toList());
    }

    public String getInvoiceUrl(String auth0Subject, Long recordId) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        if (billingRecordRepository == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Billing records not initialized."
            );
        }
        InvestorBillingRecord record = billingRecordRepository.findByInvestorIdAndId(investor.getId(), recordId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Billing record #" + recordId + " not found for this investor account."
                ));

        if (record.getProviderInvoiceUrl() == null || record.getProviderInvoiceUrl().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Invoice document unavailable. Official GST invoices require automated Razorpay Invoices integration."
            );
        }
        return record.getProviderInvoiceUrl();
    }

    public SubscriptionActionResponse cancelSubscription(String auth0Subject, SubscriptionCancelRequest req) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorSubscription sub = subscriptionRepository.findByInvestorId(investor.getId())
                .orElseGet(() -> createDefaultFreeSubscription(investor));

        if ("FREE".equalsIgnoreCase(sub.getPlan().getId())) {
            return new SubscriptionActionResponse(
                    "INFO",
                    "Community Explorer is a complimentary free tier. There are no active recurring charges to cancel.",
                    mapToSubscriptionDto(sub)
            );
        }

        if (sub.isCancelAtPeriodEnd()) {
            String endDateStr = sub.getCurrentPeriodEnd() != null
                    ? sub.getCurrentPeriodEnd().toLocalDate().toString()
                    : "the end of the current billing cycle";
            return new SubscriptionActionResponse(
                    "INFO",
                    "Subscription cancellation is already scheduled for " + endDateStr + ".",
                    mapToSubscriptionDto(sub)
            );
        }

        sub.setCancelAtPeriodEnd(true);
        ZonedDateTime periodEnd = sub.getCurrentPeriodEnd();
        String message;
        if (periodEnd != null && periodEnd.isAfter(ZonedDateTime.now())) {
            sub.setStatus("ACTIVE");
            sub.setEntitlementStatus("ACTIVE_PRO");
            message = "Subscription cancellation scheduled. Your " + sub.getPlan().getName() + " privileges will remain fully active until " +
                    periodEnd.toLocalDate() + ". On that date, your account will smoothly revert to Community Explorer. Existing research notes and custom portfolios will NOT be deleted.";
        } else {
            sub.setStatus("CANCELLED");
            sub.setEntitlementStatus("STANDARD");
            message = "Subscription cancelled. Account reverted to Community Explorer.";
        }

        InvestorSubscription saved = subscriptionRepository.save(sub);
        log.info("Subscription cancellation processed for investor {} (cancelAtPeriodEnd={})", investor.getId(), saved.isCancelAtPeriodEnd());
        return new SubscriptionActionResponse("SCHEDULED", message, mapToSubscriptionDto(saved));
    }

    public SubscriptionActionResponse reactivateSubscription(String auth0Subject) {
        Investor investor = investorService.getOrCreateInvestor(auth0Subject);
        InvestorSubscription sub = subscriptionRepository.findByInvestorId(investor.getId())
                .orElseThrow(() -> new IllegalStateException("No subscription record found."));

        if (!sub.isCancelAtPeriodEnd()) {
            return new SubscriptionActionResponse(
                    "INFO",
                    "Subscription is currently active with auto-renewal enabled.",
                    mapToSubscriptionDto(sub)
            );
        }

        sub.setCancelAtPeriodEnd(false);
        sub.setStatus("ACTIVE");
        sub.setEntitlementStatus("ACTIVE_PRO");
        InvestorSubscription saved = subscriptionRepository.save(sub);
        log.info("Subscription reactivated for investor {}", investor.getId());
        return new SubscriptionActionResponse("SUCCESS", "Subscription reactivated. Auto-renewal has been restored for " + saved.getPlan().getName() + ".", mapToSubscriptionDto(saved));
    }

    public void processWebhook(String rawPayload, String signatureHeader) {
        if (!razorpayGatewayService.isWebhookConfigured()) {
            log.warn("Webhook received but Razorpay webhook secret is not configured. Rejecting.");
            throw new IllegalStateException("Razorpay webhook secret not configured.");
        }

        boolean signatureValid = razorpayGatewayService.verifyWebhookSignature(rawPayload, signatureHeader);
        if (!signatureValid) {
            log.warn("Webhook rejected: Invalid X-Razorpay-Signature header.");
            throw new SecurityException("Invalid webhook signature.");
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String eventId = root.path("event_id").asText(null);
            if (eventId == null || eventId.isBlank()) {
                eventId = root.path("id").asText("evt_" + UUID.randomUUID());
            }

            // Idempotency check: prevent duplicate event processing
            if (webhookEventRepository.existsByEventId(eventId)) {
                log.info("Webhook event {} already processed. Acknowledging idempotently.", eventId);
                return;
            }

            String eventType = root.path("event").asText("unknown");

            SubscriptionWebhookEvent auditEvent = new SubscriptionWebhookEvent();
            auditEvent.setEventId(eventId);
            auditEvent.setProvider("RAZORPAY");
            auditEvent.setEventType(eventType);
            auditEvent.setPayload(rawPayload);
            auditEvent.setSignatureVerified(true);

            // Handle specific lifecycle events
            handleWebhookLifecycleEvent(root, eventType);

            auditEvent.setProcessed(true);
            webhookEventRepository.save(auditEvent);
        } catch (Exception e) {
            log.error("Failed to process Razorpay webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Webhook processing error: " + e.getMessage(), e);
        }
    }

    private void handleWebhookLifecycleEvent(JsonNode root, String eventType) {
        JsonNode payloadNode = root.path("payload");
        ZonedDateTime now = ZonedDateTime.now();

        if ("order.paid".equalsIgnoreCase(eventType) || "payment.captured".equalsIgnoreCase(eventType)) {
            String resolvedOrderId = payloadNode.path("order").path("entity").path("id").asText(null);
            if (resolvedOrderId == null) {
                resolvedOrderId = payloadNode.path("payment").path("entity").path("order_id").asText(null);
            }
            final String orderId = resolvedOrderId;
            String paymentId = payloadNode.path("payment").path("entity").path("id").asText(null);
            String invoiceId = payloadNode.path("payment").path("entity").path("invoice_id").asText(null);

            if (orderId != null) {
                subscriptionRepository.findByProviderOrderId(orderId).ifPresent(sub -> {
                    boolean hasActive = sub.getCurrentPeriodEnd() != null && sub.getCurrentPeriodEnd().isAfter(now);
                    ZonedDateTime base = hasActive ? sub.getCurrentPeriodEnd() : now;
                    ZonedDateTime periodEnd = "ANNUAL".equalsIgnoreCase(sub.getPlan().getBillingInterval())
                            ? base.plusYears(1)
                            : base.plusMonths(1);

                    sub.setStatus("ACTIVE");
                    sub.setPaymentStatus("PAID");
                    sub.setEntitlementStatus("ACTIVE_PRO");
                    sub.setCancelAtPeriodEnd(false);
                    if (paymentId != null) {
                        sub.setProviderPaymentId(paymentId);
                    }
                    if (sub.getCurrentPeriodStart() == null || !hasActive) {
                        sub.setCurrentPeriodStart(now);
                    }
                    sub.setCurrentPeriodEnd(periodEnd);

                    InvestorSubscription saved = subscriptionRepository.save(sub);
                    recordPaymentTransaction(saved.getInvestor(), saved, orderId, paymentId, "PAID", hasActive ? "WEBHOOK_RENEWAL" : "INITIAL_CHECKOUT", null);
                    if (invoiceId != null && billingRecordRepository != null && paymentId != null) {
                        billingRecordRepository.findByProviderPaymentId(paymentId).ifPresent(r -> {
                            r.setProviderInvoiceId(invoiceId);
                            billingRecordRepository.save(r);
                        });
                    }
                    log.info("Authoritative subscription activation via webhook for order {}", orderId);
                });
            }
        } else if ("payment.failed".equalsIgnoreCase(eventType)) {
            String orderId = payloadNode.path("payment").path("entity").path("order_id").asText(null);
            String paymentId = payloadNode.path("payment").path("entity").path("id").asText(null);
            String errorReason = payloadNode.path("payment").path("entity").path("error_description").asText(null);

            if (orderId != null) {
                subscriptionRepository.findByProviderOrderId(orderId).ifPresent(sub -> {
                    boolean paidPeriodActive = sub.getCurrentPeriodEnd() != null && sub.getCurrentPeriodEnd().isAfter(now);
                    sub.setPaymentStatus("FAILED");
                    if (paidPeriodActive) {
                        // Renewal payment failed, but paid access remains valid until currentPeriodEnd!
                        sub.setStatus("PAST_DUE");
                        sub.setEntitlementStatus("ACTIVE_PRO");
                        log.info("Renewal payment failed for order {}, but paid period remains active until {}", orderId, sub.getCurrentPeriodEnd());
                    } else {
                        // Initial checkout failed or already expired
                        sub.setStatus("PAYMENT_FAILED");
                        sub.setEntitlementStatus("STANDARD");
                        log.info("Payment failure recorded via webhook for order {}", orderId);
                    }
                    InvestorSubscription saved = subscriptionRepository.save(sub);
                    recordPaymentTransaction(saved.getInvestor(), saved, orderId, paymentId, "FAILED", "WEBHOOK_FAILURE", errorReason);
                });
            }
        } else if ("subscription.cancelled".equalsIgnoreCase(eventType)) {
            String subId = payloadNode.path("subscription").path("entity").path("id").asText(null);
            if (subId != null) {
                subscriptionRepository.findByProviderSubscriptionId(subId).ifPresent(sub -> {
                    boolean paidPeriodActive = sub.getCurrentPeriodEnd() != null && sub.getCurrentPeriodEnd().isAfter(now);
                    sub.setCancelAtPeriodEnd(true);
                    if (paidPeriodActive) {
                        sub.setStatus("ACTIVE");
                        sub.setEntitlementStatus("ACTIVE_PRO");
                        log.info("Provider subscription cancellation scheduled at period end ({}) for subscription {}", sub.getCurrentPeriodEnd(), subId);
                    } else {
                        sub.setStatus("CANCELLED");
                        sub.setEntitlementStatus("STANDARD");
                        log.info("Provider subscription cancelled immediately for subscription {}", subId);
                    }
                    subscriptionRepository.save(sub);
                });
            }
        }
    }

    private void recordPaymentTransaction(Investor investor, InvestorSubscription sub, String orderId, String paymentId, String status, String reason, String failureReason) {
        if (billingRecordRepository == null) return;
        if (paymentId != null && billingRecordRepository.findByProviderPaymentId(paymentId).isPresent()) {
            return; // Idempotent: prevent duplicate billing record
        }

        InvestorBillingRecord record = new InvestorBillingRecord();
        record.setInvestor(investor);
        record.setSubscription(sub);
        record.setPlan(sub.getPlan());
        record.setProvider("RAZORPAY");
        record.setProviderOrderId(orderId);
        record.setProviderPaymentId(paymentId);
        long amountPaise = sub.getPlan().getPriceInr().multiply(new BigDecimal("100")).longValue();
        record.setAmountPaise(amountPaise);
        record.setAmountInr(sub.getPlan().getPriceInr());
        record.setCurrency("INR");
        record.setPaymentStatus(status);
        record.setBillingReason(reason != null ? reason : "INITIAL_CHECKOUT");
        record.setFailureReason(failureReason);
        record.setPeriodStart(sub.getCurrentPeriodStart());
        record.setPeriodEnd(sub.getCurrentPeriodEnd());
        record.setProviderEventTimestamp(ZonedDateTime.now());
        billingRecordRepository.save(record);
    }

    private BillingRecordDto mapToBillingRecordDto(InvestorBillingRecord r) {
        boolean invoiceAvailable = r.getProviderInvoiceUrl() != null && !r.getProviderInvoiceUrl().isBlank();
        String invoiceMsg = invoiceAvailable
                ? "Official invoice ready for access."
                : "Statutory GST invoice generation requires active Razorpay Invoices / GST compliance configuration.";
        return new BillingRecordDto(
                r.getId(),
                r.getPlan().getId(),
                r.getPlan().getName(),
                r.getProvider(),
                r.getProviderPaymentId(),
                r.getProviderOrderId(),
                r.getProviderInvoiceId(),
                r.getProviderInvoiceUrl(),
                r.getAmountInr(),
                r.getCurrency(),
                r.getPaymentStatus(),
                r.getBillingReason(),
                r.getFailureReason(),
                r.getPeriodStart(),
                r.getPeriodEnd(),
                r.getCreatedAt(),
                invoiceAvailable,
                invoiceMsg
        );
    }

    private InvestorSubscription createDefaultFreeSubscription(Investor investor) {
        SubscriptionPlan freePlan = planRepository.findById("FREE")
                .orElseGet(() -> {
                    SubscriptionPlan defaultFree = new SubscriptionPlan();
                    defaultFree.setId("FREE");
                    defaultFree.setName("Community Explorer");
                    defaultFree.setDescription("Full access to mutual fund discovery and research.");
                    defaultFree.setPriceInr(BigDecimal.ZERO);
                    defaultFree.setBillingInterval("FREE");
                    defaultFree.setActive(true);
                    return planRepository.save(defaultFree);
                });

        InvestorSubscription sub = new InvestorSubscription();
        sub.setInvestor(investor);
        sub.setPlan(freePlan);
        sub.setProvider("RAZORPAY");
        sub.setStatus("ACTIVE");
        sub.setPaymentStatus("PAID");
        sub.setEntitlementStatus("STANDARD");
        return subscriptionRepository.save(sub);
    }

    private SubscriptionPlanDto mapToPlanDto(SubscriptionPlan plan) {
        return new SubscriptionPlanDto(
                plan.getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getPriceInr(),
                plan.getBillingInterval(),
                plan.getMaxPortfolios(),
                plan.getMaxHoldingsPerPortfolio(),
                plan.getMaxSavedFunds(),
                plan.isCanImportCas(),
                plan.isCanAccessApi(),
                plan.getApiRateLimitPerMin(),
                plan.isActive()
        );
    }

    public void reconcileLifecycleState(InvestorSubscription sub) {
        if (sub == null || sub.getPlan() == null || "FREE".equalsIgnoreCase(sub.getPlan().getId())) {
            return;
        }

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime periodEnd = sub.getCurrentPeriodEnd();

        // 1. Check if paid period has elapsed
        if (periodEnd != null && now.isAfter(periodEnd)) {
            if (!"EXPIRED".equalsIgnoreCase(sub.getStatus())) {
                log.info("Subscription period ended on {} for investor {}. Reconciling to EXPIRED / STANDARD.", periodEnd, sub.getInvestor().getId());
                sub.setStatus("EXPIRED");
                sub.setEntitlementStatus("STANDARD");
                subscriptionRepository.save(sub);
            }
            return;
        }

        // 2. If paid period is still valid in the future:
        if (periodEnd != null && !now.isAfter(periodEnd)) {
            if ("PAID".equalsIgnoreCase(sub.getPaymentStatus())) {
                if (!"ACTIVE_PRO".equalsIgnoreCase(sub.getEntitlementStatus())) {
                    sub.setEntitlementStatus("ACTIVE_PRO");
                    if (!"CANCELLED".equalsIgnoreCase(sub.getStatus()) && !"PAST_DUE".equalsIgnoreCase(sub.getStatus())) {
                        sub.setStatus("ACTIVE");
                    }
                    subscriptionRepository.save(sub);
                }
            } else if ("FAILED".equalsIgnoreCase(sub.getPaymentStatus())) {
                // Renewal failed, but current paid period is still active
                if (!"ACTIVE_PRO".equalsIgnoreCase(sub.getEntitlementStatus())) {
                    sub.setEntitlementStatus("ACTIVE_PRO");
                    sub.setStatus("PAST_DUE");
                    subscriptionRepository.save(sub);
                }
            }
        }
    }

    private InvestorSubscriptionDto mapToSubscriptionDto(InvestorSubscription sub) {
        boolean configured = razorpayGatewayService.isConfigured();
        boolean isFree = "FREE".equalsIgnoreCase(sub.getPlan().getId());
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime periodEnd = sub.getCurrentPeriodEnd();
        boolean isExpired = periodEnd != null && now.isAfter(periodEnd);

        String message;
        String nextAction;
        String periodEndBehavior;
        boolean canReactivate = false;
        boolean attentionRequired = false;

        if (isFree) {
            message = "Standard Community Explorer tier active. Complimentary access to fund discovery, metrics, and Evidence Explorer.";
            nextAction = "UPGRADE_TO_PRO";
            periodEndBehavior = "Continuous free access. No expiration.";
        } else if (isExpired) {
            message = "Your " + sub.getPlan().getName() + " billing period expired on " + periodEnd.toLocalDate() + ". Your account has smoothly transitioned to Community Explorer. All saved research and portfolios remain safely preserved.";
            nextAction = "RENEW_SUBSCRIPTION";
            periodEndBehavior = "Expired. Account operates under Community Explorer quota (25 funds, 20 holdings).";
        } else if (sub.isCancelAtPeriodEnd()) {
            canReactivate = true;
            message = "Subscription cancellation scheduled. Your " + sub.getPlan().getName() + " privileges remain fully active until " + periodEnd.toLocalDate() + ". No further renewals will be billed.";
            nextAction = "REACTIVATE";
            periodEndBehavior = "On " + periodEnd.toLocalDate() + ", your account will revert to Community Explorer. Existing research notes and custom portfolios will NOT be deleted.";
        } else if ("PAST_DUE".equalsIgnoreCase(sub.getStatus()) || "FAILED".equalsIgnoreCase(sub.getPaymentStatus())) {
            attentionRequired = true;
            message = "Renewal payment could not be processed. However, your paid access remains active until " + (periodEnd != null ? periodEnd.toLocalDate() : "period end") + ". Please review your payment method to ensure uninterrupted Pro access.";
            nextAction = "UPDATE_PAYMENT";
            periodEndBehavior = "If unresolved by " + (periodEnd != null ? periodEnd.toLocalDate() : "period end") + ", account reverts to Community Explorer. All saved research is preserved.";
        } else if ("ACTIVE".equalsIgnoreCase(sub.getStatus())) {
            message = "Active " + sub.getPlan().getName() + " entitlement with automatic renewal.";
            nextAction = "MANAGE_OR_CHANGE";
            periodEndBehavior = "Renews automatically on " + (periodEnd != null ? periodEnd.toLocalDate() : "period end") + ". You can cancel anytime before this date.";
        } else if ("CHECKOUT_INITIATED".equalsIgnoreCase(sub.getStatus())) {
            message = "Checkout initiated but pending payment completion. Your account remains on Community Explorer.";
            nextAction = "COMPLETE_CHECKOUT";
            periodEndBehavior = "No paid period active until payment verification.";
        } else {
            message = "Subscription status: " + sub.getStatus() + ". Standard Community Explorer features active.";
            nextAction = "VIEW_PLANS";
            periodEndBehavior = "Standard access.";
        }

        if (!configured && !isFree) {
            message = message + " Note: Commercial payment gateway credentials pending merchant configuration.";
        }

        return new InvestorSubscriptionDto(
                sub.getId(),
                sub.getInvestor().getId(),
                sub.getPlan().getId(),
                sub.getPlan().getName(),
                sub.getPlan().getBillingInterval(),
                sub.getPlan().getPriceInr(),
                sub.getProvider(),
                sub.getProviderOrderId(),
                sub.getProviderSubscriptionId(),
                sub.getStatus(),
                sub.getPaymentStatus(),
                sub.getEntitlementStatus(),
                sub.isCancelAtPeriodEnd(),
                sub.getCurrentPeriodStart(),
                sub.getCurrentPeriodEnd(),
                configured,
                message,
                canReactivate,
                attentionRequired,
                nextAction,
                periodEndBehavior
        );
    }
}
