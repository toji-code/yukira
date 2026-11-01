package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Versioned entity representing an authoritative capital market holiday schedule (Task 331).
 * Enforces calendar coverage bounds and prevents assuming unverified future dates.
 */
@Entity
@Table(name = "capital_market_calendar_version", uniqueConstraints = {
    @UniqueConstraint(name = "uq_calendar_ver", columnNames = {"exchange", "market_segment", "effective_year", "calendar_version"})
})
public class CapitalMarketCalendarVersion implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exchange", nullable = false, length = 50)
    private String exchange;

    @Column(name = "market_segment", nullable = false, length = 50)
    private String marketSegment;

    @Column(name = "effective_year", nullable = false)
    private Integer effectiveYear;

    @Column(name = "calendar_version", nullable = false, length = 50)
    private String calendarVersion;

    @Column(name = "holiday_count", nullable = false)
    private Integer holidayCount;

    @Column(name = "source_reference", nullable = false, length = 255)
    private String sourceReference;

    @Column(name = "publication_date", nullable = false)
    private LocalDate publicationDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public CapitalMarketCalendarVersion() {}

    public CapitalMarketCalendarVersion(
        String exchange,
        String marketSegment,
        Integer effectiveYear,
        String calendarVersion,
        Integer holidayCount,
        String sourceReference,
        LocalDate publicationDate,
        Boolean isActive
    ) {
        this.exchange = exchange;
        this.marketSegment = marketSegment;
        this.effectiveYear = effectiveYear;
        this.calendarVersion = calendarVersion;
        this.holidayCount = holidayCount;
        this.sourceReference = sourceReference;
        this.publicationDate = publicationDate;
        this.isActive = isActive;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getMarketSegment() { return marketSegment; }
    public void setMarketSegment(String marketSegment) { this.marketSegment = marketSegment; }

    public Integer getEffectiveYear() { return effectiveYear; }
    public void setEffectiveYear(Integer effectiveYear) { this.effectiveYear = effectiveYear; }

    public String getCalendarVersion() { return calendarVersion; }
    public void setCalendarVersion(String calendarVersion) { this.calendarVersion = calendarVersion; }

    public Integer getHolidayCount() { return holidayCount; }
    public void setHolidayCount(Integer holidayCount) { this.holidayCount = holidayCount; }

    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String sourceReference) { this.sourceReference = sourceReference; }

    public LocalDate getPublicationDate() { return publicationDate; }
    public void setPublicationDate(LocalDate publicationDate) { this.publicationDate = publicationDate; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

