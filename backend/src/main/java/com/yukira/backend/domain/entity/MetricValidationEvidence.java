package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Structured validation evidence record supporting authoritative methodology authorization (Task 317, 318).
 * Enforces the strict governance rule: IMPLEMENTED != VALIDATED != APPROVED != RETIRED.
 */
@Entity
@Table(name = "metric_validation_evidence", indexes = {
    @Index(name = "idx_val_evidence_metric", columnList = "metric_code")
})
public class MetricValidationEvidence implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_code", nullable = false, length = 50)
    private String metricCode;

    @Column(name = "metric_version", nullable = false, length = 20)
    private String metricVersion;

    @Column(name = "formula_version", nullable = false, length = 20)
    private String formulaVersion;

    @Column(name = "validation_date", nullable = false)
    private LocalDate validationDate;

    @Column(name = "validation_scope", nullable = false, length = 100)
    private String validationScope;

    @Column(name = "reference_dataset_ref", nullable = false, length = 255)
    private String referenceDatasetRef;

    @Column(name = "independent_methodology", nullable = false, length = 255)
    private String independentMethodology;

    @Column(name = "numerical_tolerance", nullable = false, precision = 15, scale = 10)
    private BigDecimal numericalTolerance;

    @Column(name = "known_limitations", columnDefinition = "TEXT")
    private String knownLimitations;

    @Column(name = "validation_result", nullable = false, length = 50)
    private String validationResult; // PASS, FAIL, INCONCLUSIVE

    @Column(name = "reviewer_identity", nullable = false, length = 100)
    private String reviewerIdentity;

    @Column(name = "authorization_status", nullable = false, length = 50)
    private String authorizationStatus; // CANDIDATE, VALIDATED, APPROVED, RETIRED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_id")
    private MetricValidationEvidence supersedes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public MetricValidationEvidence() {}

    public MetricValidationEvidence(
        String metricCode,
        String metricVersion,
        String formulaVersion,
        LocalDate validationDate,
        String validationScope,
        String referenceDatasetRef,
        String independentMethodology,
        BigDecimal numericalTolerance,
        String knownLimitations,
        String validationResult,
        String reviewerIdentity,
        String authorizationStatus
    ) {
        this.metricCode = metricCode;
        this.metricVersion = metricVersion;
        this.formulaVersion = formulaVersion;
        this.validationDate = validationDate;
        this.validationScope = validationScope;
        this.referenceDatasetRef = referenceDatasetRef;
        this.independentMethodology = independentMethodology;
        this.numericalTolerance = numericalTolerance;
        this.knownLimitations = knownLimitations;
        this.validationResult = validationResult;
        this.reviewerIdentity = reviewerIdentity;
        this.authorizationStatus = authorizationStatus;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }

    public String getMetricVersion() { return metricVersion; }
    public void setMetricVersion(String metricVersion) { this.metricVersion = metricVersion; }

    public String getFormulaVersion() { return formulaVersion; }
    public void setFormulaVersion(String formulaVersion) { this.formulaVersion = formulaVersion; }

    public LocalDate getValidationDate() { return validationDate; }
    public void setValidationDate(LocalDate validationDate) { this.validationDate = validationDate; }

    public String getValidationScope() { return validationScope; }
    public void setValidationScope(String validationScope) { this.validationScope = validationScope; }

    public String getReferenceDatasetRef() { return referenceDatasetRef; }
    public void setReferenceDatasetRef(String referenceDatasetRef) { this.referenceDatasetRef = referenceDatasetRef; }

    public String getIndependentMethodology() { return independentMethodology; }
    public void setIndependentMethodology(String independentMethodology) { this.independentMethodology = independentMethodology; }

    public BigDecimal getNumericalTolerance() { return numericalTolerance; }
    public void setNumericalTolerance(BigDecimal numericalTolerance) { this.numericalTolerance = numericalTolerance; }

    public String getKnownLimitations() { return knownLimitations; }
    public void setKnownLimitations(String knownLimitations) { this.knownLimitations = knownLimitations; }

    public String getValidationResult() { return validationResult; }
    public void setValidationResult(String validationResult) { this.validationResult = validationResult; }

    public String getReviewerIdentity() { return reviewerIdentity; }
    public void setReviewerIdentity(String reviewerIdentity) { this.reviewerIdentity = reviewerIdentity; }

    public String getAuthorizationStatus() { return authorizationStatus; }
    public void setAuthorizationStatus(String authorizationStatus) { this.authorizationStatus = authorizationStatus; }

    public MetricValidationEvidence getSupersedes() { return supersedes; }
    public void setSupersedes(MetricValidationEvidence supersedes) { this.supersedes = supersedes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

