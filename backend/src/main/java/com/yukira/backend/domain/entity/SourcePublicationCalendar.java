package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "source_publication_calendar")
public class SourcePublicationCalendar implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_name", nullable = false, length = 64)
    private String sourceName; // AMFI_NAV, AMC_MONTHLY_HOLDINGS, RBI_TREPS_TRI, NSE_BENCHMARK_TRI

    @Column(name = "frequency", nullable = false, length = 32)
    private String frequency = "DAILY"; // DAILY, MONTHLY, WEEKLY

    @Column(name = "expected_lag_business_days", nullable = false)
    private int expectedLagBusinessDays = 1;

    @Column(name = "expected_cutoff_time", nullable = false)
    private LocalTime expectedCutoffTime = LocalTime.of(23, 0);

    @Column(name = "timezone", nullable = false, length = 32)
    private String timezone = "Asia/Kolkata";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_observed_date")
    private LocalDate lastObservedDate;

    @Column(name = "last_observed_at")
    private ZonedDateTime lastObservedAt;

    @Column(name = "next_expected_date")
    private LocalDate nextExpectedDate;

    @Column(name = "next_expected_at")
    private ZonedDateTime nextExpectedAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public SourcePublicationCalendar() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
        if (isActive == null) isActive = true;
        if (frequency == null) frequency = "DAILY";
        if (timezone == null) timezone = "Asia/Kolkata";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public int getExpectedLagBusinessDays() { return expectedLagBusinessDays; }
    public void setExpectedLagBusinessDays(int expectedLagBusinessDays) { this.expectedLagBusinessDays = expectedLagBusinessDays; }

    public LocalTime getExpectedCutoffTime() { return expectedCutoffTime; }
    public void setExpectedCutoffTime(LocalTime expectedCutoffTime) { this.expectedCutoffTime = expectedCutoffTime; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDate getLastObservedDate() { return lastObservedDate; }
    public void setLastObservedDate(LocalDate lastObservedDate) { this.lastObservedDate = lastObservedDate; }

    public ZonedDateTime getLastObservedAt() { return lastObservedAt; }
    public void setLastObservedAt(ZonedDateTime lastObservedAt) { this.lastObservedAt = lastObservedAt; }

    public LocalDate getNextExpectedDate() { return nextExpectedDate; }
    public void setNextExpectedDate(LocalDate nextExpectedDate) { this.nextExpectedDate = nextExpectedDate; }

    public ZonedDateTime getNextExpectedAt() { return nextExpectedAt; }
    public void setNextExpectedAt(ZonedDateTime nextExpectedAt) { this.nextExpectedAt = nextExpectedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourcePublicationCalendar that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

