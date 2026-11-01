package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Entity tracking original task requirement vs reported implementation reconciliation (Tasks 410-413).
 */
@Entity
@Table(name = "v1_task_requirement_reconciliation", indexes = {
    @Index(name = "idx_task_req_recon_num", columnList = "task_number"),
    @Index(name = "idx_task_req_recon_status", columnList = "implementation_status")
})
public class V1TaskRequirementReconciliation implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_number", nullable = false, unique = true)
    private Integer taskNumber;

    @Column(name = "original_title", nullable = false)
    private String originalTitle;

    @Column(name = "original_objective", nullable = false, columnDefinition = "TEXT")
    private String originalObjective;

    @Column(name = "required_backend_behavior", columnDefinition = "TEXT")
    private String requiredBackendBehavior;

    @Column(name = "required_persistence_behavior", columnDefinition = "TEXT")
    private String requiredPersistenceBehavior;

    @Column(name = "required_api_ui_behavior", columnDefinition = "TEXT")
    private String requiredApiUiBehavior;

    @Column(name = "reported_title")
    private String reportedTitle;

    @Column(name = "discrepancy_type", nullable = false, length = 50)
    private String discrepancyType; // EXACT_MATCH, TITLE_REWORDED, OBJECTIVE_SHIFTED, SCOPE_NARROWED, UNREPORTED

    @Column(name = "implementation_status", nullable = false, length = 50)
    private String implementationStatus; // IMPLEMENTED, PARTIALLY_IMPLEMENTED, NOT_IMPLEMENTED, BLOCKED

    @Column(name = "evidence_classifications", nullable = false, columnDefinition = "TEXT")
    private String evidenceClassifications;

    @Column(name = "implementation_artifacts", columnDefinition = "TEXT")
    private String implementationArtifacts;

    @Column(name = "remaining_gap", columnDefinition = "TEXT")
    private String remainingGap;

    @Column(name = "reconciliation_version")
    private Integer reconciliationVersion = 1;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public V1TaskRequirementReconciliation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getTaskNumber() { return taskNumber; }
    public void setTaskNumber(Integer taskNumber) { this.taskNumber = taskNumber; }

    public String getOriginalTitle() { return originalTitle; }
    public void setOriginalTitle(String originalTitle) { this.originalTitle = originalTitle; }

    public String getOriginalObjective() { return originalObjective; }
    public void setOriginalObjective(String originalObjective) { this.originalObjective = originalObjective; }

    public String getRequiredBackendBehavior() { return requiredBackendBehavior; }
    public void setRequiredBackendBehavior(String requiredBackendBehavior) { this.requiredBackendBehavior = requiredBackendBehavior; }

    public String getRequiredPersistenceBehavior() { return requiredPersistenceBehavior; }
    public void setRequiredPersistenceBehavior(String requiredPersistenceBehavior) { this.requiredPersistenceBehavior = requiredPersistenceBehavior; }

    public String getRequiredApiUiBehavior() { return requiredApiUiBehavior; }
    public void setRequiredApiUiBehavior(String requiredApiUiBehavior) { this.requiredApiUiBehavior = requiredApiUiBehavior; }

    public String getReportedTitle() { return reportedTitle; }
    public void setReportedTitle(String reportedTitle) { this.reportedTitle = reportedTitle; }

    public String getDiscrepancyType() { return discrepancyType; }
    public void setDiscrepancyType(String discrepancyType) { this.discrepancyType = discrepancyType; }

    public String getImplementationStatus() { return implementationStatus; }
    public void setImplementationStatus(String implementationStatus) { this.implementationStatus = implementationStatus; }

    public String getEvidenceClassifications() { return evidenceClassifications; }
    public void setEvidenceClassifications(String evidenceClassifications) { this.evidenceClassifications = evidenceClassifications; }

    public String getImplementationArtifacts() { return implementationArtifacts; }
    public void setImplementationArtifacts(String implementationArtifacts) { this.implementationArtifacts = implementationArtifacts; }

    public String getRemainingGap() { return remainingGap; }
    public void setRemainingGap(String remainingGap) { this.remainingGap = remainingGap; }

    public Integer getReconciliationVersion() { return reconciliationVersion; }
    public void setReconciliationVersion(Integer reconciliationVersion) { this.reconciliationVersion = reconciliationVersion; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
