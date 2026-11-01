package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Entity tracking version history and audit trail for material changes
 * to the canonical V1 implementation ledger (Task 315).
 */
@Entity
@Table(name = "v1_implementation_ledger_version", indexes = {
    @Index(name = "idx_ledger_version_task", columnList = "task_number"),
    @Index(name = "idx_ledger_version_cap", columnList = "capability_id")
})
public class V1ImplementationLedgerVersion implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_number", nullable = false)
    private Integer taskNumber;

    @Column(name = "capability_id", nullable = false, length = 100)
    private String capabilityId;

    @Column(name = "previous_status", nullable = false, length = 50)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 50)
    private String newStatus;

    @Column(name = "change_reason", nullable = false, columnDefinition = "TEXT")
    private String changeReason;

    @Column(name = "implementation_ref", length = 255)
    private String implementationRef;

    @Column(name = "author_actor", nullable = false, length = 100)
    private String authorActor;

    @Column(name = "superseded_record_ref", length = 100)
    private String supersededRecordRef;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt = OffsetDateTime.now();

    public V1ImplementationLedgerVersion() {}

    public V1ImplementationLedgerVersion(
        Integer taskNumber,
        String capabilityId,
        String previousStatus,
        String newStatus,
        String changeReason,
        String implementationRef,
        String authorActor,
        String supersededRecordRef
    ) {
        this.taskNumber = taskNumber;
        this.capabilityId = capabilityId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changeReason = changeReason;
        this.implementationRef = implementationRef;
        this.authorActor = authorActor;
        this.supersededRecordRef = supersededRecordRef;
        this.changedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getTaskNumber() { return taskNumber; }
    public void setTaskNumber(Integer taskNumber) { this.taskNumber = taskNumber; }

    public String getCapabilityId() { return capabilityId; }
    public void setCapabilityId(String capabilityId) { this.capabilityId = capabilityId; }

    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    public String getImplementationRef() { return implementationRef; }
    public void setImplementationRef(String implementationRef) { this.implementationRef = implementationRef; }

    public String getAuthorActor() { return authorActor; }
    public void setAuthorActor(String authorActor) { this.authorActor = authorActor; }

    public String getSupersededRecordRef() { return supersededRecordRef; }
    public void setSupersededRecordRef(String supersededRecordRef) { this.supersededRecordRef = supersededRecordRef; }

    public OffsetDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(OffsetDateTime changedAt) { this.changedAt = changedAt; }
}

