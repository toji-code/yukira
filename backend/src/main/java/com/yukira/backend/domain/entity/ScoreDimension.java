package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "score_dimension")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ScoreDimension implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analytical_score_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private AnalyticalScore analyticalScore;

    @Column(name = "dimension", nullable = false, length = 50)
    private String dimension; // RETURN_QUALITY, RISK_QUALITY, BENCHMARK_RELATIVE_QUALITY, CONSISTENCY_DOWNSIDE_QUALITY, EVIDENCE_CONFIDENCE

    @Column(name = "dimension_name", nullable = false, length = 100)
    private String dimensionName;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "weight", nullable = false, precision = 5, scale = 4)
    private BigDecimal weight;

    @Column(name = "status", nullable = false, length = 30)
    private String status; // AVAILABLE, PARTIAL, INSUFFICIENT_DATA, DATA_QUALITY_LIMITED, NOT_APPLICABLE

    @Column(name = "confidence", nullable = false, precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "eligible_metric_count", nullable = false)
    private Integer eligibleMetricCount = 0;

    @Column(name = "total_metric_count", nullable = false)
    private Integer totalMetricCount = 0;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "diagnostics", columnDefinition = "jsonb")
    private String diagnostics;

    @OneToMany(mappedBy = "scoreDimension", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ScoreMetricContribution> metricContributions = new ArrayList<>();

    public ScoreDimension() {}

    public ScoreDimension(
        AnalyticalScore analyticalScore,
        String dimension,
        String dimensionName,
        BigDecimal score,
        BigDecimal weight,
        String status,
        BigDecimal confidence,
        Integer eligibleMetricCount,
        Integer totalMetricCount,
        String diagnostics
    ) {
        this.analyticalScore = analyticalScore;
        this.dimension = dimension;
        this.dimensionName = dimensionName;
        this.score = score;
        this.weight = weight;
        this.status = status;
        this.confidence = confidence;
        this.eligibleMetricCount = eligibleMetricCount != null ? eligibleMetricCount : 0;
        this.totalMetricCount = totalMetricCount != null ? totalMetricCount : 0;
        this.diagnostics = diagnostics;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AnalyticalScore getAnalyticalScore() { return analyticalScore; }
    public void setAnalyticalScore(AnalyticalScore analyticalScore) { this.analyticalScore = analyticalScore; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public String getDimensionName() { return dimensionName; }
    public void setDimensionName(String dimensionName) { this.dimensionName = dimensionName; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public Integer getEligibleMetricCount() { return eligibleMetricCount; }
    public void setEligibleMetricCount(Integer eligibleMetricCount) { this.eligibleMetricCount = eligibleMetricCount; }

    public Integer getTotalMetricCount() { return totalMetricCount; }
    public void setTotalMetricCount(Integer totalMetricCount) { this.totalMetricCount = totalMetricCount; }

    public String getDiagnostics() { return diagnostics; }
    public void setDiagnostics(String diagnostics) { this.diagnostics = diagnostics; }

    public List<ScoreMetricContribution> getMetricContributions() { return metricContributions; }
    public void setMetricContributions(List<ScoreMetricContribution> metricContributions) { this.metricContributions = metricContributions; }

    public void addMetricContribution(ScoreMetricContribution contribution) {
        metricContributions.add(contribution);
        contribution.setScoreDimension(this);
    }
}
