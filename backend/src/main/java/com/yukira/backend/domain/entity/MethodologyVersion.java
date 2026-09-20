package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "methodology_version", uniqueConstraints = {
    @UniqueConstraint(name = "uq_methodology_ver", columnNames = {"methodology_code", "version_tag"})
})
public class MethodologyVersion implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "metric_definition_id")
    private MetricDefinition metricDefinition;

    @Column(name = "methodology_code", nullable = false, length = 100)
    private String methodologyCode;

    @Column(name = "version_tag", nullable = false, length = 50)
    private String versionTag; // e.g. "CANDIDATE_V1"

    @Column(name = "approval_status", nullable = false, length = 30)
    private String approvalStatus = "CANDIDATE"; // CANDIDATE, APPROVED, RETIRED

    @Column(name = "lifecycle_status", nullable = false, length = 30)
    private String lifecycleStatus = "CANDIDATE"; // CANDIDATE, VALIDATED, APPROVED, RETIRED

    @Column(name = "validation_status", nullable = false, length = 30)
    private String validationStatus = "UNVALIDATED"; // UNVALIDATED, IN_VALIDATION, VALIDATED, FAILED

    @Column(name = "validation_evidence_reference", columnDefinition = "TEXT")
    private String validationEvidenceReference;

    @Column(name = "approval_record", columnDefinition = "TEXT")
    private String approvalRecord;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "git_commit_hash", nullable = false, length = 40)
    private String gitCommitHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameter_configuration", columnDefinition = "jsonb", nullable = false)
    private String parameterConfiguration = "{}";

    @Column(name = "mathematical_definition", columnDefinition = "TEXT")
    private String mathematicalDefinition;

    @Column(name = "formula_reference", length = 255)
    private String formulaReference;

    @Column(name = "required_inputs", columnDefinition = "TEXT")
    private String requiredInputs;

    @Column(name = "frequency_assumptions", length = 100)
    private String frequencyAssumptions;

    @Column(name = "lookback_rule", columnDefinition = "TEXT")
    private String lookbackRule;

    @Column(name = "observation_date_semantics", length = 100)
    private String observationDateSemantics;

    @Column(name = "information_set_requirement", columnDefinition = "TEXT")
    private String informationSetRequirement;

    @Column(name = "annualization_convention", length = 50)
    private String annualizationConvention;

    @Column(name = "denominator_convention", length = 50)
    private String denominatorConvention;

    @Column(name = "missing_data_rule", columnDefinition = "TEXT")
    private String missingDataRule;

    @Column(name = "insufficient_history_rule", columnDefinition = "TEXT")
    private String insufficientHistoryRule;

    @Column(name = "invalid_data_rule", columnDefinition = "TEXT")
    private String invalidDataRule;

    @Column(name = "quality_prerequisites", columnDefinition = "TEXT")
    private String qualityPrerequisites;

    @Column(name = "known_limitations", columnDefinition = "TEXT")
    private String knownLimitations;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_version_id")
    private MethodologyVersion supersedesVersion;

    @Column(name = "is_locked", nullable = false)
    private boolean isLocked = false;

    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom = OffsetDateTime.now();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public MethodologyVersion() {}

    public MethodologyVersion(String methodologyCode, String versionTag, String approvalStatus, String gitCommitHash) {
        this.methodologyCode = methodologyCode;
        this.versionTag = versionTag;
        this.approvalStatus = approvalStatus;
        this.lifecycleStatus = approvalStatus;
        this.gitCommitHash = gitCommitHash;
        this.effectiveFrom = OffsetDateTime.now();
        this.createdAt = OffsetDateTime.now();
    }

    public boolean isCandidate() {
        return "CANDIDATE".equalsIgnoreCase(lifecycleStatus) || "CANDIDATE".equalsIgnoreCase(approvalStatus);
    }

    public boolean isValidated() {
        return "VALIDATED".equalsIgnoreCase(lifecycleStatus);
    }

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(lifecycleStatus) && "APPROVED".equalsIgnoreCase(approvalStatus);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MetricDefinition getMetricDefinition() { return metricDefinition; }
    public void setMetricDefinition(MetricDefinition metricDefinition) { this.metricDefinition = metricDefinition; }

    public String getMethodologyCode() { return methodologyCode; }
    public void setMethodologyCode(String methodologyCode) { this.methodologyCode = methodologyCode; }

    public String getVersionTag() { return versionTag; }
    public void setVersionTag(String versionTag) { this.versionTag = versionTag; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }

    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }

    public String getValidationEvidenceReference() { return validationEvidenceReference; }
    public void setValidationEvidenceReference(String validationEvidenceReference) { this.validationEvidenceReference = validationEvidenceReference; }

    public String getApprovalRecord() { return approvalRecord; }
    public void setApprovalRecord(String approvalRecord) { this.approvalRecord = approvalRecord; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(OffsetDateTime approvedAt) { this.approvedAt = approvedAt; }

    public String getGitCommitHash() { return gitCommitHash; }
    public void setGitCommitHash(String gitCommitHash) { this.gitCommitHash = gitCommitHash; }

    public String getParameterConfiguration() { return parameterConfiguration; }
    public void setParameterConfiguration(String parameterConfiguration) { this.parameterConfiguration = parameterConfiguration; }

    public String getMathematicalDefinition() { return mathematicalDefinition; }
    public void setMathematicalDefinition(String mathematicalDefinition) { this.mathematicalDefinition = mathematicalDefinition; }

    public String getFormulaReference() { return formulaReference; }
    public void setFormulaReference(String formulaReference) { this.formulaReference = formulaReference; }

    public String getRequiredInputs() { return requiredInputs; }
    public void setRequiredInputs(String requiredInputs) { this.requiredInputs = requiredInputs; }

    public String getFrequencyAssumptions() { return frequencyAssumptions; }
    public void setFrequencyAssumptions(String frequencyAssumptions) { this.frequencyAssumptions = frequencyAssumptions; }

    public String getLookbackRule() { return lookbackRule; }
    public void setLookbackRule(String lookbackRule) { this.lookbackRule = lookbackRule; }

    public String getObservationDateSemantics() { return observationDateSemantics; }
    public void setObservationDateSemantics(String observationDateSemantics) { this.observationDateSemantics = observationDateSemantics; }

    public String getInformationSetRequirement() { return informationSetRequirement; }
    public void setInformationSetRequirement(String informationSetRequirement) { this.informationSetRequirement = informationSetRequirement; }

    public String getAnnualizationConvention() { return annualizationConvention; }
    public void setAnnualizationConvention(String annualizationConvention) { this.annualizationConvention = annualizationConvention; }

    public String getDenominatorConvention() { return denominatorConvention; }
    public void setDenominatorConvention(String denominatorConvention) { this.denominatorConvention = denominatorConvention; }

    public String getMissingDataRule() { return missingDataRule; }
    public void setMissingDataRule(String missingDataRule) { this.missingDataRule = missingDataRule; }

    public String getInsufficientHistoryRule() { return insufficientHistoryRule; }
    public void setInsufficientHistoryRule(String insufficientHistoryRule) { this.insufficientHistoryRule = insufficientHistoryRule; }

    public String getInvalidDataRule() { return invalidDataRule; }
    public void setInvalidDataRule(String invalidDataRule) { this.invalidDataRule = invalidDataRule; }

    public String getQualityPrerequisites() { return qualityPrerequisites; }
    public void setQualityPrerequisites(String qualityPrerequisites) { this.qualityPrerequisites = qualityPrerequisites; }

    public String getKnownLimitations() { return knownLimitations; }
    public void setKnownLimitations(String knownLimitations) { this.knownLimitations = knownLimitations; }

    public MethodologyVersion getSupersedesVersion() { return supersedesVersion; }
    public void setSupersedesVersion(MethodologyVersion supersedesVersion) { this.supersedesVersion = supersedesVersion; }

    public boolean isLocked() { return isLocked; }
    public void setLocked(boolean locked) { isLocked = locked; }

    public OffsetDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(OffsetDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
