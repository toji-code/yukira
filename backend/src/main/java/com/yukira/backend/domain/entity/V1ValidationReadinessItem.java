package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Entity tracking canonical validation readiness areas for the subsequent validation phase (Task 459).
 */
@Entity
@Table(name = "v1_validation_readiness_item", indexes = {
    @Index(name = "idx_val_readiness_code", columnList = "readiness_area_code")
})
public class V1ValidationReadinessItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "readiness_area_code", nullable = false, unique = true, length = 50)
    private String readinessAreaCode;

    @Column(name = "area_name", nullable = false)
    private String areaName;

    @Column(name = "relevant_artifacts", nullable = false, columnDefinition = "TEXT")
    private String relevantArtifacts;

    @Column(name = "required_verification_activity", nullable = false, columnDefinition = "TEXT")
    private String requiredVerificationActivity;

    @Column(name = "required_real_data_evidence", nullable = false, columnDefinition = "TEXT")
    private String requiredRealDataEvidence;

    @Column(name = "expected_acceptance_criteria", nullable = false, columnDefinition = "TEXT")
    private String expectedAcceptanceCriteria;

    @Column(name = "dependencies", columnDefinition = "TEXT")
    private String dependencies;

    @Column(name = "risk_if_not_verified", nullable = false, columnDefinition = "TEXT")
    private String riskIfNotVerified;

    @Column(name = "responsible_review_area", nullable = false, length = 100)
    private String responsibleReviewArea;

    @Column(name = "validation_conducted", nullable = false)
    private Boolean validationConducted = false;

    @Column(name = "readiness_state", nullable = false, length = 50)
    private String readinessState = "READY_FOR_VALIDATION";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public V1ValidationReadinessItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReadinessAreaCode() { return readinessAreaCode; }
    public void setReadinessAreaCode(String readinessAreaCode) { this.readinessAreaCode = readinessAreaCode; }

    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }

    public String getRelevantArtifacts() { return relevantArtifacts; }
    public void setRelevantArtifacts(String relevantArtifacts) { this.relevantArtifacts = relevantArtifacts; }

    public String getRequiredVerificationActivity() { return requiredVerificationActivity; }
    public void setRequiredVerificationActivity(String requiredVerificationActivity) { this.requiredVerificationActivity = requiredVerificationActivity; }

    public String getRequiredRealDataEvidence() { return requiredRealDataEvidence; }
    public void setRequiredRealDataEvidence(String requiredRealDataEvidence) { this.requiredRealDataEvidence = requiredRealDataEvidence; }

    public String getExpectedAcceptanceCriteria() { return expectedAcceptanceCriteria; }
    public void setExpectedAcceptanceCriteria(String expectedAcceptanceCriteria) { this.expectedAcceptanceCriteria = expectedAcceptanceCriteria; }

    public String getDependencies() { return dependencies; }
    public void setDependencies(String dependencies) { this.dependencies = dependencies; }

    public String getRiskIfNotVerified() { return riskIfNotVerified; }
    public void setRiskIfNotVerified(String riskIfNotVerified) { this.riskIfNotVerified = riskIfNotVerified; }

    public String getResponsibleReviewArea() { return responsibleReviewArea; }
    public void setResponsibleReviewArea(String responsibleReviewArea) { this.responsibleReviewArea = responsibleReviewArea; }

    public Boolean getValidationConducted() { return validationConducted; }
    public void setValidationConducted(Boolean validationConducted) { this.validationConducted = validationConducted; }

    public String getReadinessState() { return readinessState; }
    public void setReadinessState(String readinessState) { this.readinessState = readinessState; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
