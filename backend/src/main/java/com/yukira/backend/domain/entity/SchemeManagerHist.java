package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "scheme_manager_hist")
public class SchemeManagerHist implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @Column(name = "manager_name", nullable = false)
    private String managerName;

    @Column(name = "role", nullable = false, length = 100)
    private String role = "PRIMARY_EQUITY";

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_artifact_id")
    private SourceArtifact sourceArtifact;

    @Column(name = "as_of_date")
    private LocalDate asOfDate;

    @Column(name = "quality_assessment", length = 30)
    private String qualityAssessment = "VALID";

    public SchemeManagerHist() {}

    public SchemeManagerHist(Scheme scheme, String managerName, String role, LocalDate startDate) {
        this.scheme = scheme;
        this.managerName = managerName;
        this.role = role;
        this.startDate = startDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }

    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public SourceArtifact getSourceArtifact() { return sourceArtifact; }
    public void setSourceArtifact(SourceArtifact sourceArtifact) { this.sourceArtifact = sourceArtifact; }

    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }

    public String getQualityAssessment() { return qualityAssessment; }
    public void setQualityAssessment(String qualityAssessment) { this.qualityAssessment = qualityAssessment; }
}
