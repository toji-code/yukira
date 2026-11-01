package com.yukira.backend.controller;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorInAppNotification;
import com.yukira.backend.domain.entity.InvestorNotificationPreference;
import com.yukira.backend.domain.entity.InvestorWebhookConfig;
import com.yukira.backend.dto.notification.*;
import com.yukira.backend.repository.InvestorInAppNotificationRepository;
import com.yukira.backend.repository.InvestorNotificationPreferenceRepository;
import com.yukira.backend.repository.InvestorWebhookConfigRepository;
import com.yukira.backend.service.InvestorService;
import com.yukira.backend.service.NotificationDeliveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/notifications")
public class InvestorNotificationController {

    private final InvestorService investorService;
    private final NotificationDeliveryService deliveryService;
    private final InvestorInAppNotificationRepository inAppRepository;
    private final InvestorNotificationPreferenceRepository preferenceRepository;
    private final InvestorWebhookConfigRepository webhookRepository;

    @Autowired
    public InvestorNotificationController(
        InvestorService investorService,
        NotificationDeliveryService deliveryService,
        InvestorInAppNotificationRepository inAppRepository,
        InvestorNotificationPreferenceRepository preferenceRepository,
        InvestorWebhookConfigRepository webhookRepository
    ) {
        this.investorService = investorService;
        this.deliveryService = deliveryService;
        this.inAppRepository = inAppRepository;
        this.preferenceRepository = preferenceRepository;
        this.webhookRepository = webhookRepository;
    }

    @GetMapping
    public ResponseEntity<List<InvestorInAppNotificationDto>> getNotifications(@AuthenticationPrincipal Jwt jwt) {
        Investor investor = investorService.getOrCreateInvestor(jwt != null ? jwt.getSubject() : null);
        List<InvestorInAppNotification> list = inAppRepository.findByInvestorAndIsArchivedFalseOrderByCreatedAtDesc(investor);
        List<InvestorInAppNotificationDto> dtos = list.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        Investor investor = investorService.getOrCreateInvestor(jwt != null ? jwt.getSubject() : null);
        long count = inAppRepository.countByInvestorAndIsReadFalseAndIsArchivedFalse(investor);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Investor investor = investorService.getOrCreateInvestor(jwt != null ? jwt.getSubject() : null);
        inAppRepository.findByIdAndInvestor(id, investor).ifPresent(n -> {
            n.setIsRead(true);
            n.setReadAt(ZonedDateTime.now());
            inAppRepository.save(n);
        });
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> markAsArchived(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Investor investor = investorService.getOrCreateInvestor(jwt != null ? jwt.getSubject() : null);
        inAppRepository.findByIdAndInvestor(id, investor).ifPresent(n -> {
            n.setIsArchived(true);
            n.setArchivedAt(ZonedDateTime.now());
            inAppRepository.save(n);
        });
        return ResponseEntity.ok().build();
    }

    @GetMapping("/preferences")
    public ResponseEntity<InvestorNotificationPreferenceDto> getPreferences(@AuthenticationPrincipal Jwt jwt) {
        Investor investor = investorService.getOrCreateInvestor(jwt != null ? jwt.getSubject() : null);
        InvestorNotificationPreference pref = getOrCreatePreferences(investor);
        return ResponseEntity.ok(new InvestorNotificationPreferenceDto(
            pref.getEmailEnabled(),
            pref.getInAppEnabled(),
            pref.getWebhookEnabled(),
            pref.getEmailAddress(),
            pref.getMonitoringTriggersEnabled(),
            pref.getResearchUpdatesEnabled(),
            pref.getDataQualityAlertsEnabled()
        ));
    }

    @PutMapping("/preferences")
    public ResponseEntity<InvestorNotificationPreferenceDto> updatePreferences(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody InvestorNotificationPreferenceDto dto
    ) {
        Investor investor = investorService.getOrCreateInvestor(jwt != null ? jwt.getSubject() : null);
        InvestorNotificationPreference pref = getOrCreatePreferences(investor);

        if (dto.emailEnabled() != null) pref.setEmailEnabled(dto.emailEnabled());
        if (dto.inAppEnabled() != null) pref.setInAppEnabled(dto.inAppEnabled());
        if (dto.webhookEnabled() != null) pref.setWebhookEnabled(dto.webhookEnabled());
        if (dto.emailAddress() != null) pref.setEmailAddress(dto.emailAddress());
        if (dto.monitoringTriggersEnabled() != null) pref.setMonitoringTriggersEnabled(dto.monitoringTriggersEnabled());
        if (dto.researchUpdatesEnabled() != null) pref.setResearchUpdatesEnabled(dto.researchUpdatesEnabled());
        if (dto.dataQualityAlertsEnabled() != null) pref.setDataQualityAlertsEnabled(dto.dataQualityAlertsEnabled());

        preferenceRepository.save(pref);

        return ResponseEntity.ok(new InvestorNotificationPreferenceDto(
            pref.getEmailEnabled(),
            pref.getInAppEnabled(),
            pref.getWebhookEnabled(),
            pref.getEmailAddress(),
            pref.getMonitoringTriggersEnabled(),
            pref.getResearchUpdatesEnabled(),
            pref.getDataQualityAlertsEnabled()
        ));
    }

    private InvestorNotificationPreference getOrCreatePreferences(Investor investor) {
        return preferenceRepository.findByInvestor(investor)
            .orElseGet(() -> {
                InvestorNotificationPreference p = new InvestorNotificationPreference();
                p.setInvestor(investor);
                return preferenceRepository.save(p);
            });
    }

    @GetMapping("/webhooks")
    public ResponseEntity<List<InvestorWebhookConfigDto>> getWebhooks(@AuthenticationPrincipal Jwt jwt) {
        Investor investor = investorService.getOrCreateInvestor(jwt != null ? jwt.getSubject() : null);
        List<InvestorWebhookConfig> configs = webhookRepository.findByInvestor(investor);
        List<InvestorWebhookConfigDto> dtos = configs.stream().map(c -> new InvestorWebhookConfigDto(
            c.getId(),
            c.getEndpointUrl(),
            c.getSubscriptionEventsCsv(),
            c.getIsActive(),
            c.getStatus(),
            c.getFailureCount(),
            c.getCreatedAt()
        )).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/webhooks")
    public ResponseEntity<InvestorWebhookConfigDto> registerWebhook(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody RegisterWebhookRequest req
    ) {
        Investor investor = investorService.getOrCreateInvestor(jwt != null ? jwt.getSubject() : null);
        if (req.endpointUrl() == null || req.endpointUrl().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        InvestorWebhookConfig config = new InvestorWebhookConfig();
        config.setInvestor(investor);
        config.setEndpointUrl(req.endpointUrl().trim());
        config.setSecretHash(hashSecret(req.secretKey() != null ? req.secretKey() : "default_secret"));
        config.setSubscriptionEventsCsv(req.subscriptionEventsCsv() != null ? req.subscriptionEventsCsv() : "MONITORING_TRIGGER,CONDITION_STATUS_CHANGE");
        config.setIsActive(true);
        config.setStatus("ACTIVE");
        config.setFailureCount(0);

        InvestorWebhookConfig saved = webhookRepository.save(config);

        return ResponseEntity.status(HttpStatus.CREATED).body(new InvestorWebhookConfigDto(
            saved.getId(),
            saved.getEndpointUrl(),
            saved.getSubscriptionEventsCsv(),
            saved.getIsActive(),
            saved.getStatus(),
            saved.getFailureCount(),
            saved.getCreatedAt()
        ));
    }

    @DeleteMapping("/webhooks/{id}")
    public ResponseEntity<Void> deleteWebhook(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Investor investor = investorService.getOrCreateInvestor(jwt != null ? jwt.getSubject() : null);
        webhookRepository.findByIdAndInvestor(id, investor).ifPresent(webhookRepository::delete);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/webhooks/{id}/rotate-secret")
    public ResponseEntity<Map<String, String>> rotateSecret(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @RequestBody Map<String, String> payload
    ) {
        Investor investor = investorService.getOrCreateInvestor(jwt != null ? jwt.getSubject() : null);
        String newSecret = payload.get("newSecretKey");
        if (newSecret == null || newSecret.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "newSecretKey required"));
        }

        return webhookRepository.findByIdAndInvestor(id, investor).map(cfg -> {
            cfg.setSecretHash(hashSecret(newSecret));
            webhookRepository.save(cfg);
            return ResponseEntity.ok(Map.of("status", "SECRET_ROTATED"));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private InvestorInAppNotificationDto toDto(InvestorInAppNotification n) {
        return new InvestorInAppNotificationDto(
            n.getId(),
            n.getEvent() != null ? n.getEvent().getId() : null,
            n.getTitle(),
            n.getMessage(),
            n.getNotificationCategory(),
            n.getSeverity(),
            n.getIsRead(),
            n.getReadAt(),
            n.getIsArchived(),
            n.getActionUrl(),
            n.getCreatedAt()
        );
    }

    private String hashSecret(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return Integer.toHexString(secret.hashCode());
        }
    }
}

