package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity
@Table(name = "validation_issue")
public class ValidationIssue implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_entity_type", nullable = false, length = 50)
    private String targetEntityType;

    @Column(name = "target_entity_id", nullable = false)
    private Long targetEntityId;

    @Column(name = "check_code", nullable = false, length = 50)
    private String checkCode;

    @Column(name = "quality_assessment", nullable = false, length = 30)
    private String qualityAssessment = "VALID";

    @Column(name = "verification_status", nullable = false, length = 30)
    private String verificationStatus = "UNVERIFIED";

    @Column(name = "revision_status", nullable = false, length = 30)
    private String revisionStatus = "ORIGINAL";

    @Column(name = "temporal_status", nullable = false, length = 30)
    private String temporalStatus = "CURRENT";

    @Column(name = "presence_status", nullable = false, length = 30)
    private String presenceStatus = "AVAILABLE";

    @Column(name = "integrity_condition", nullable = false, length = 30)
    private String integrityCondition = "NONE";

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt = OffsetDateTime.now();

    public ValidationIssue() {}

    public ValidationIssue(String targetEntityType, Long targetEntityId, String checkCode, String message) {
        this.targetEntityType = targetEntityType;
        this.targetEntityId = targetEntityId;
        this.checkCode = checkCode;
        this.message = message;
        this.detectedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTargetEntityType() { return targetEntityType; }
    public void setTargetEntityType(String targetEntityType) { this.targetEntityType = targetEntityType; }

    public Long getTargetEntityId() { return targetEntityId; }
    public void setTargetEntityId(Long targetEntityId) { this.targetEntityId = targetEntityId; }

    public String getCheckCode() { return checkCode; }
    public void setCheckCode(String checkCode) { this.checkCode = checkCode; }

    public String getQualityAssessment() { return qualityAssessment; }
    public void setQualityAssessment(String qualityAssessment) { this.qualityAssessment = qualityAssessment; }

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }

    public String getRevisionStatus() { return revisionStatus; }
    public void setRevisionStatus(String revisionStatus) { this.revisionStatus = revisionStatus; }

    public String getTemporalStatus() { return temporalStatus; }
    public void setTemporalStatus(String temporalStatus) { this.temporalStatus = temporalStatus; }

    public String getPresenceStatus() { return presenceStatus; }
    public void setPresenceStatus(String presenceStatus) { this.presenceStatus = presenceStatus; }

    public String getIntegrityCondition() { return integrityCondition; }
    public void setIntegrityCondition(String integrityCondition) { this.integrityCondition = integrityCondition; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public OffsetDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(OffsetDateTime detectedAt) { this.detectedAt = detectedAt; }
}
