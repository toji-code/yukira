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

    @Column(name = "methodology_code", nullable = false, length = 100)
    private String methodologyCode;

    @Column(name = "version_tag", nullable = false, length = 50)
    private String versionTag;

    @Column(name = "approval_status", nullable = false, length = 30)
    private String approvalStatus = "CANDIDATE"; // CANDIDATE, APPROVED, RETIRED

    @Column(name = "git_commit_hash", nullable = false, length = 40)
    private String gitCommitHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameter_configuration", columnDefinition = "jsonb", nullable = false)
    private String parameterConfiguration = "{}";

    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom = OffsetDateTime.now();

    public MethodologyVersion() {}

    public MethodologyVersion(String methodologyCode, String versionTag, String approvalStatus, String gitCommitHash) {
        this.methodologyCode = methodologyCode;
        this.versionTag = versionTag;
        this.approvalStatus = approvalStatus;
        this.gitCommitHash = gitCommitHash;
        this.effectiveFrom = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMethodologyCode() { return methodologyCode; }
    public void setMethodologyCode(String methodologyCode) { this.methodologyCode = methodologyCode; }

    public String getVersionTag() { return versionTag; }
    public void setVersionTag(String versionTag) { this.versionTag = versionTag; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }

    public String getGitCommitHash() { return gitCommitHash; }
    public void setGitCommitHash(String gitCommitHash) { this.gitCommitHash = gitCommitHash; }

    public String getParameterConfiguration() { return parameterConfiguration; }
    public void setParameterConfiguration(String parameterConfiguration) { this.parameterConfiguration = parameterConfiguration; }

    public OffsetDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(OffsetDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
}
