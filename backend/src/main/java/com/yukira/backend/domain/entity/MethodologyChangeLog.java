package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity
@Table(name = "methodology_change_log")
public class MethodologyChangeLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "methodology_version_id", nullable = false)
    private MethodologyVersion methodologyVersion;

    @Column(name = "transition_type", nullable = false, length = 50)
    private String transitionType; // INITIAL_CREATION, VERSION_FORK, LIFECYCLE_TRANSITION, LOCK_ENFORCEMENT

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public MethodologyChangeLog() {}

    public MethodologyChangeLog(
        MethodologyVersion methodologyVersion,
        String transitionType,
        String fromStatus,
        String toStatus,
        String reason,
        String actor
    ) {
        this.methodologyVersion = methodologyVersion;
        this.transitionType = transitionType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.actor = actor;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MethodologyVersion getMethodologyVersion() { return methodologyVersion; }
    public void setMethodologyVersion(MethodologyVersion methodologyVersion) { this.methodologyVersion = methodologyVersion; }

    public String getTransitionType() { return transitionType; }
    public void setTransitionType(String transitionType) { this.transitionType = transitionType; }

    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }

    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
