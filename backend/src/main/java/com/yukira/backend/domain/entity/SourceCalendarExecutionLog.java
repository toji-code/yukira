package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity
@Table(name = "source_calendar_execution_log")
public class SourceCalendarExecutionLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_id")
    private SourcePublicationCalendar calendar;

    @Column(name = "source_name", nullable = false, length = 64)
    private String sourceName;

    @Column(name = "scheduled_cutoff", nullable = false)
    private OffsetDateTime scheduledCutoff;

    @Column(name = "actual_ingested_at")
    private OffsetDateTime actualIngestedAt;

    @Column(name = "delay_minutes")
    private Integer delayMinutes;

    @Column(name = "status", nullable = false, length = 64)
    private String status = "ON_TIME";

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public SourceCalendarExecutionLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SourcePublicationCalendar getCalendar() { return calendar; }
    public void setCalendar(SourcePublicationCalendar calendar) { this.calendar = calendar; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public OffsetDateTime getScheduledCutoff() { return scheduledCutoff; }
    public void setScheduledCutoff(OffsetDateTime scheduledCutoff) { this.scheduledCutoff = scheduledCutoff; }

    public OffsetDateTime getActualIngestedAt() { return actualIngestedAt; }
    public void setActualIngestedAt(OffsetDateTime actualIngestedAt) { this.actualIngestedAt = actualIngestedAt; }

    public Integer getDelayMinutes() { return delayMinutes; }
    public void setDelayMinutes(Integer delayMinutes) { this.delayMinutes = delayMinutes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

