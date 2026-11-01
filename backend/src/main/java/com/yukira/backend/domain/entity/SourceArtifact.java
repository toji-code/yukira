package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "source_artifact")
public class SourceArtifact implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_source_id", nullable = false)
    private DataSource dataSource;

    @Column(name = "retrieval_timestamp", nullable = false)
    private OffsetDateTime retrievalTimestamp;

    @Column(name = "artifact_type", nullable = false, length = 30)
    private String artifactType;

    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    @Column(name = "byte_size", nullable = false)
    private Long byteSize;

    @Column(name = "storage_uri", length = 500)
    private String storageUri;

    @Column(name = "payload_blob")
    private byte[] payloadBlob;

    @Column(name = "source_location_ref", length = 512)
    private String sourceLocationRef;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "effective_start_date")
    private LocalDate effectiveStartDate;

    @Column(name = "effective_end_date")
    private LocalDate effectiveEndDate;

    @Column(name = "ingestion_status", nullable = false, length = 64)
    private String ingestionStatus = "INGESTED";

    @Column(name = "storage_backend", nullable = false, length = 32)
    private String storageBackend = "DATABASE_BLOB";

    @Column(name = "source_authority_tier", length = 50)
    private String sourceAuthorityTier = "OFFICIAL_PRIMARY";

    @Column(name = "storage_state", length = 50)
    private String storageState = "VERIFIED_PERSISTED";

    @Column(name = "is_investor_private")
    private Boolean isInvestorPrivate = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owning_investor_id")
    private Investor owningInvestor;

    // Extended Authenticity, Corroboration & Retention Policies (Tasks 371, 372, 373)
    @Column(name = "authenticity_status", length = 50)
    private String authenticityStatus = "OFFICIAL_VERIFIED"; // OFFICIAL_VERIFIED, UNVERIFIED_THIRD_PARTY, MANUALLY_ATTESTED, SUSPECT_TAMPERED

    @Column(name = "corroboration_status", length = 50)
    private String corroborationStatus = "SINGLE_SOURCE_UNCHECKED"; // INDEPENDENTLY_CORROBORATED, SINGLE_SOURCE_UNCHECKED, DISCREPANCY_FLAGGED

    @Column(name = "publisher_identity", length = 100)
    private String publisherIdentity = "AMFI";

    @Column(name = "retrieval_origin", length = 255)
    private String retrievalOrigin;

    @Column(name = "retention_classification", length = 50)
    private String retentionClassification = "PERMANENT_REGULATORY"; // PERMANENT_REGULATORY, TRANSACTION_EVIDENCE_8_YEARS, EPHEMERAL_WORKSPACE_90_DAYS

    @Column(name = "retention_policy_ref", length = 100)
    private String retentionPolicyRef = "SEBI_RECORD_KEEPING_CIRCULAR";

    @Column(name = "deletion_eligibility")
    private Boolean deletionEligibility = false;

    public SourceArtifact() {}

    public SourceArtifact(DataSource dataSource, OffsetDateTime retrievalTimestamp, String artifactType, String sha256Hash, Long byteSize) {
        this.dataSource = dataSource;
        this.retrievalTimestamp = retrievalTimestamp;
        this.artifactType = artifactType;
        this.sha256Hash = sha256Hash;
        this.byteSize = byteSize;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DataSource getDataSource() { return dataSource; }
    public void setDataSource(DataSource dataSource) { this.dataSource = dataSource; }

    public OffsetDateTime getRetrievalTimestamp() { return retrievalTimestamp; }
    public void setRetrievalTimestamp(OffsetDateTime retrievalTimestamp) { this.retrievalTimestamp = retrievalTimestamp; }

    public String getArtifactType() { return artifactType; }
    public void setArtifactType(String artifactType) { this.artifactType = artifactType; }

    public String getSha256Hash() { return sha256Hash; }
    public String getSha256() { return sha256Hash; }
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }

    public Long getByteSize() { return byteSize; }
    public void setByteSize(Long byteSize) { this.byteSize = byteSize; }

    public String getStorageUri() { return storageUri; }
    public String getCasPath() { return storageUri; }
    public void setStorageUri(String storageUri) { this.storageUri = storageUri; }

    public byte[] getPayloadBlob() { return payloadBlob; }
    public void setPayloadBlob(byte[] payloadBlob) { this.payloadBlob = payloadBlob; }

    public String getSourceLocationRef() { return sourceLocationRef; }
    public void setSourceLocationRef(String sourceLocationRef) { this.sourceLocationRef = sourceLocationRef; }

    public LocalDate getPublicationDate() { return publicationDate; }
    public void setPublicationDate(LocalDate publicationDate) { this.publicationDate = publicationDate; }

    public LocalDate getEffectiveStartDate() { return effectiveStartDate; }
    public void setEffectiveStartDate(LocalDate effectiveStartDate) { this.effectiveStartDate = effectiveStartDate; }

    public LocalDate getEffectiveEndDate() { return effectiveEndDate; }
    public void setEffectiveEndDate(LocalDate effectiveEndDate) { this.effectiveEndDate = effectiveEndDate; }

    public String getIngestionStatus() { return ingestionStatus; }
    public void setIngestionStatus(String ingestionStatus) { this.ingestionStatus = ingestionStatus; }

    public String getStorageBackend() { return storageBackend; }
    public void setStorageBackend(String storageBackend) { this.storageBackend = storageBackend; }

    public String getSourceAuthorityTier() { return sourceAuthorityTier; }
    public void setSourceAuthorityTier(String sourceAuthorityTier) { this.sourceAuthorityTier = sourceAuthorityTier; }

    public String getStorageState() { return storageState; }
    public void setStorageState(String storageState) { this.storageState = storageState; }

    public Boolean getIsInvestorPrivate() { return isInvestorPrivate; }
    public void setIsInvestorPrivate(Boolean isInvestorPrivate) { this.isInvestorPrivate = isInvestorPrivate; }

    public Investor getOwningInvestor() { return owningInvestor; }
    public void setOwningInvestor(Investor owningInvestor) { this.owningInvestor = owningInvestor; }

    public String getAuthenticityStatus() { return authenticityStatus; }
    public void setAuthenticityStatus(String authenticityStatus) { this.authenticityStatus = authenticityStatus; }

    public String getCorroborationStatus() { return corroborationStatus; }
    public void setCorroborationStatus(String corroborationStatus) { this.corroborationStatus = corroborationStatus; }

    public String getPublisherIdentity() { return publisherIdentity; }
    public void setPublisherIdentity(String publisherIdentity) { this.publisherIdentity = publisherIdentity; }

    public String getRetrievalOrigin() { return retrievalOrigin; }
    public void setRetrievalOrigin(String retrievalOrigin) { this.retrievalOrigin = retrievalOrigin; }

    public String getRetentionClassification() { return retentionClassification; }
    public void setRetentionClassification(String retentionClassification) { this.retentionClassification = retentionClassification; }

    public String getRetentionPolicyRef() { return retentionPolicyRef; }
    public void setRetentionPolicyRef(String retentionPolicyRef) { this.retentionPolicyRef = retentionPolicyRef; }

    public Boolean getDeletionEligibility() { return deletionEligibility; }
    public void setDeletionEligibility(Boolean deletionEligibility) { this.deletionEligibility = deletionEligibility; }
}
