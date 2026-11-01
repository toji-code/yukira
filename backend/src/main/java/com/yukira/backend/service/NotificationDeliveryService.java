package com.yukira.backend.service;

import com.yukira.backend.domain.entity.InvestorMonitoringEvent;
import com.yukira.backend.domain.entity.MonitoringNotificationDelivery;
import com.yukira.backend.repository.InvestorMonitoringEventRepository;
import com.yukira.backend.repository.MonitoringNotificationDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service implementing the reliable notification outbox pattern (Tasks 305, 306).
 * Decouples alert detection from channel dispatch, ensuring delivery status traceability
 * across In-App, Email, and Webhook outboxes with retry tracking.
 */
@Service
@Transactional
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

    private final MonitoringNotificationDeliveryRepository deliveryRepository;
    private final InvestorMonitoringEventRepository alertRepository;

    public record NotificationDeliveryDto(
        Long id,
        Long alertId,
        String channel,
        String destination,
        String payloadJson,
        String deliveryStatus, // QUEUED, ATTEMPTED, ACCEPTED, FAILED, CONFIGURATION_REQUIRED
        OffsetDateTime attemptedAt,
        OffsetDateTime deliveredAt,
        String failureReason,
        int retryCount,
        OffsetDateTime createdAt
    ) {}

    public NotificationDeliveryService(
        MonitoringNotificationDeliveryRepository deliveryRepository,
        InvestorMonitoringEventRepository alertRepository
    ) {
        this.deliveryRepository = deliveryRepository;
        this.alertRepository = alertRepository;
    }

    public NotificationDeliveryDto queueNotification(Long alertId, String channel, String destination, String payloadJson) {
        InvestorMonitoringEvent alert = alertRepository.findById(alertId)
            .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        MonitoringNotificationDelivery delivery = new MonitoringNotificationDelivery();
        delivery.setAlert(alert);
        delivery.setChannel(channel != null ? channel.toUpperCase() : "IN_APP");
        delivery.setDestination(destination != null ? destination : "IN_APP_FEED");
        delivery.setPayloadJson(payloadJson != null ? payloadJson : "{}");
        delivery.setDeliveryStatus("QUEUED");

        MonitoringNotificationDelivery saved = deliveryRepository.save(delivery);
        log.info("Queued notification delivery {} for alert {} via channel {}", saved.getId(), alertId, channel);
        return toDto(saved);
    }

    public void enqueueNotificationsForEvent(InvestorMonitoringEvent event) {
        if (event == null || event.getId() == null) return;
        queueNotification(event.getId(), "IN_APP", "IN_APP_FEED", "{\"eventId\":" + event.getId() + "}");
    }

    public List<NotificationDeliveryDto> dispatchPendingNotifications(int batchSize) {
        List<MonitoringNotificationDelivery> pending = deliveryRepository.findByDeliveryStatusOrderByCreatedAtAsc("QUEUED");
        int count = 0;

        for (MonitoringNotificationDelivery d : pending) {
            if (count >= batchSize) break;

            d.setAttemptedAt(OffsetDateTime.now());
            d.setRetryCount(d.getRetryCount() + 1);

            // Channel dispatch logic
            if ("IN_APP".equalsIgnoreCase(d.getChannel())) {
                d.setDeliveryStatus("ACCEPTED");
                d.setDeliveredAt(OffsetDateTime.now());
            } else if ("EMAIL_OUTBOX".equalsIgnoreCase(d.getChannel())) {
                if (d.getDestination() != null && d.getDestination().contains("@")) {
                    d.setDeliveryStatus("ACCEPTED");
                    d.setDeliveredAt(OffsetDateTime.now());
                } else {
                    d.setDeliveryStatus("CONFIGURATION_REQUIRED");
                    d.setFailureReason("Recipient email address unconfigured or invalid");
                }
            } else if ("WEBHOOK_OUTBOX".equalsIgnoreCase(d.getChannel())) {
                if (d.getDestination() != null && d.getDestination().startsWith("http")) {
                    d.setDeliveryStatus("ACCEPTED");
                    d.setDeliveredAt(OffsetDateTime.now());
                } else {
                    d.setDeliveryStatus("CONFIGURATION_REQUIRED");
                    d.setFailureReason("Webhook destination URL invalid or missing endpoint");
                }
            } else {
                d.setDeliveryStatus("FAILED");
                d.setFailureReason("Unsupported delivery channel: " + d.getChannel());
            }

            deliveryRepository.save(d);
            count++;
        }

        return pending.stream().limit(count).map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public int processPendingOutboxDeliveries() {
        return dispatchPendingNotifications(100).size();
    }

    @Transactional(readOnly = true)
    public List<NotificationDeliveryDto> getRecentDeliveries(int limit) {
        return deliveryRepository.findTop50ByOrderByCreatedAtDesc().stream()
            .limit(limit)
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NotificationDeliveryDto> getDeliveriesForAlert(Long alertId) {
        return deliveryRepository.findByAlertIdOrderByCreatedAtDesc(alertId).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    private NotificationDeliveryDto toDto(MonitoringNotificationDelivery d) {
        return new NotificationDeliveryDto(
            d.getId(),
            d.getAlert() != null ? d.getAlert().getId() : null,
            d.getChannel(),
            d.getDestination(),
            d.getPayloadJson(),
            d.getDeliveryStatus(),
            d.getAttemptedAt(),
            d.getDeliveredAt(),
            d.getFailureReason(),
            d.getRetryCount(),
            d.getCreatedAt()
        );
    }
}
