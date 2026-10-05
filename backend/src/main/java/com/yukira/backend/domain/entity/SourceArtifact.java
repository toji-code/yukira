package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
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

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "payload_blob")
    private byte[] payloadBlob;

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
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }

    public Long getByteSize() { return byteSize; }
    public void setByteSize(Long byteSize) { this.byteSize = byteSize; }

    public String getStorageUri() { return storageUri; }
    public void setStorageUri(String storageUri) { this.storageUri = storageUri; }

    public byte[] getPayloadBlob() { return payloadBlob; }
    public void setPayloadBlob(byte[] payloadBlob) { this.payloadBlob = payloadBlob; }
}
