package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_monitoring_event_note")
public class InvestorMonitoringEventNote implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private InvestorMonitoringEvent event;

    @Column(name = "note_text", nullable = false, columnDefinition = "text")
    private String noteText;

    @Column(name = "author", nullable = false, length = 128)
    private String author = "INVESTOR";

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public InvestorMonitoringEventNote() {}

    public InvestorMonitoringEventNote(InvestorMonitoringEvent event, String noteText, String author) {
        this.event = event;
        this.noteText = noteText;
        this.author = author != null ? author : "INVESTOR";
    }

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        if (author == null) author = "INVESTOR";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public InvestorMonitoringEvent getEvent() { return event; }
    public void setEvent(InvestorMonitoringEvent event) { this.event = event; }

    public String getNoteText() { return noteText; }
    public void setNoteText(String noteText) { this.noteText = noteText; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorMonitoringEventNote that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

