package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_in_app_notification")
public class InvestorInAppNotification implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private InvestorMonitoringEvent event;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "notification_category", nullable = false, length = 64)
    private String notificationCategory; // MONITORING_TRIGGER, CONDITION_ATTENTION, RESEARCH_UPDATE, SYSTEM_ALERT

    @Column(name = "severity", nullable = false, length = 16)
    private String severity = "INFO"; // INFO, LOW, MEDIUM, HIGH, CRITICAL

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "read_at")
    private ZonedDateTime readAt;

    @Column(name = "is_archived", nullable = false)
    private Boolean isArchived = false;

    @Column(name = "archived_at")
    private ZonedDateTime archivedAt;

    @Column(name = "action_url")
    private String actionUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public InvestorInAppNotification() {}

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        if (isRead == null) isRead = false;
        if (isArchived == null) isArchived = false;
        if (severity == null) severity = "INFO";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public InvestorMonitoringEvent getEvent() { return event; }
    public void setEvent(InvestorMonitoringEvent event) { this.event = event; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getNotificationCategory() { return notificationCategory; }
    public void setNotificationCategory(String notificationCategory) { this.notificationCategory = notificationCategory; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    public ZonedDateTime getReadAt() { return readAt; }
    public void setReadAt(ZonedDateTime readAt) { this.readAt = readAt; }

    public Boolean getIsArchived() { return isArchived; }
    public void setIsArchived(Boolean isArchived) { this.isArchived = isArchived; }

    public ZonedDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(ZonedDateTime archivedAt) { this.archivedAt = archivedAt; }

    public String getActionUrl() { return actionUrl; }
    public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorInAppNotification that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

