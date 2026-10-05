package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "analytical_score")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AnalyticalScore implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_option_id", nullable = false)
    private SchemeOption schemeOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_run_id")
    private CalculationRun calculationRun;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "status", nullable = false, length = 30)
    private String status; // AVAILABLE, PARTIAL, INSUFFICIENT_DATA, DATA_QUALITY_LIMITED, NOT_APPLICABLE

    @Column(name = "score_version", nullable = false, length = 50)
    private String scoreVersion = "YUKIRA_SCORE_V1";

    @Column(name = "methodology_status", nullable = false, length = 30)
    private String methodologyStatus = "CANDIDATE";

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "knowledge_cutoff_time", nullable = false)
    private OffsetDateTime knowledgeCutoffTime;

    @Column(name = "reference_population", nullable = false, length = 100)
    private String referencePopulation;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "analyticalScore", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ScoreDimension> dimensions = new ArrayList<>();

    public AnalyticalScore() {}

    public AnalyticalScore(
        SchemeOption schemeOption,
        CalculationRun calculationRun,
        BigDecimal score,
        BigDecimal confidence,
        String status,
        String scoreVersion,
        String methodologyStatus,
        LocalDate asOfDate,
        OffsetDateTime knowledgeCutoffTime,
        String referencePopulation,
        String summary
    ) {
        this.schemeOption = schemeOption;
        this.calculationRun = calculationRun;
        this.score = score;
        this.confidence = confidence;
        this.status = status;
        this.scoreVersion = scoreVersion != null ? scoreVersion : "YUKIRA_SCORE_V1";
        this.methodologyStatus = methodologyStatus != null ? methodologyStatus : "CANDIDATE";
        this.asOfDate = asOfDate;
        this.knowledgeCutoffTime = knowledgeCutoffTime;
        this.referencePopulation = referencePopulation;
        this.summary = summary;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SchemeOption getSchemeOption() { return schemeOption; }
    public void setSchemeOption(SchemeOption schemeOption) { this.schemeOption = schemeOption; }

    public CalculationRun getCalculationRun() { return calculationRun; }
    public void setCalculationRun(CalculationRun calculationRun) { this.calculationRun = calculationRun; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getScoreVersion() { return scoreVersion; }
    public void setScoreVersion(String scoreVersion) { this.scoreVersion = scoreVersion; }

    public String getMethodologyStatus() { return methodologyStatus; }
    public void setMethodologyStatus(String methodologyStatus) { this.methodologyStatus = methodologyStatus; }

    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }

    public OffsetDateTime getKnowledgeCutoffTime() { return knowledgeCutoffTime; }
    public void setKnowledgeCutoffTime(OffsetDateTime knowledgeCutoffTime) { this.knowledgeCutoffTime = knowledgeCutoffTime; }

    public String getReferencePopulation() { return referencePopulation; }
    public void setReferencePopulation(String referencePopulation) { this.referencePopulation = referencePopulation; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public List<ScoreDimension> getDimensions() { return dimensions; }
    public void setDimensions(List<ScoreDimension> dimensions) { this.dimensions = dimensions; }

    public void addDimension(ScoreDimension dimension) {
        dimensions.add(dimension);
        dimension.setAnalyticalScore(this);
    }
}
