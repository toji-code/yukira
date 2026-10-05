package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "score_metric_contribution")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ScoreMetricContribution implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "score_dimension_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ScoreDimension scoreDimension;

    @Column(name = "metric_code", nullable = false, length = 50)
    private String metricCode;

    @Column(name = "metric_name", nullable = false, length = 255)
    private String metricName;

    @Column(name = "raw_value", precision = 30, scale = 10)
    private BigDecimal rawValue;

    @Column(name = "normalized_value", precision = 10, scale = 4)
    private BigDecimal normalizedValue;

    @Column(name = "direction", nullable = false, length = 30)
    private String direction; // HIGHER_IS_BETTER, LOWER_IS_BETTER, TARGET_VALUE

    @Column(name = "weight", nullable = false, precision = 5, scale = 4)
    private BigDecimal weight;

    @Column(name = "contribution", precision = 10, scale = 4)
    private BigDecimal contribution;

    @Column(name = "eligibility", nullable = false, length = 30)
    private String eligibility; // ELIGIBLE, INELIGIBLE, MISSING, DATA_QUALITY_EXCLUDED

    @Column(name = "exclusion_reason", columnDefinition = "TEXT")
    private String exclusionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "metric_result_id")
    private MetricResult metricResult;

    @Column(name = "unit", length = 30)
    private String unit;

    public ScoreMetricContribution() {}

    public ScoreMetricContribution(
        ScoreDimension scoreDimension,
        String metricCode,
        String metricName,
        BigDecimal rawValue,
        BigDecimal normalizedValue,
        String direction,
        BigDecimal weight,
        BigDecimal contribution,
        String eligibility,
        String exclusionReason,
        MetricResult metricResult,
        String unit
    ) {
        this.scoreDimension = scoreDimension;
        this.metricCode = metricCode;
        this.metricName = metricName;
        this.rawValue = rawValue;
        this.normalizedValue = normalizedValue;
        this.direction = direction;
        this.weight = weight;
        this.contribution = contribution;
        this.eligibility = eligibility;
        this.exclusionReason = exclusionReason;
        this.metricResult = metricResult;
        this.unit = unit;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ScoreDimension getScoreDimension() { return scoreDimension; }
    public void setScoreDimension(ScoreDimension scoreDimension) { this.scoreDimension = scoreDimension; }

    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public BigDecimal getRawValue() { return rawValue; }
    public void setRawValue(BigDecimal rawValue) { this.rawValue = rawValue; }

    public BigDecimal getNormalizedValue() { return normalizedValue; }
    public void setNormalizedValue(BigDecimal normalizedValue) { this.normalizedValue = normalizedValue; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }

    public BigDecimal getContribution() { return contribution; }
    public void setContribution(BigDecimal contribution) { this.contribution = contribution; }

    public String getEligibility() { return eligibility; }
    public void setEligibility(String eligibility) { this.eligibility = eligibility; }

    public String getExclusionReason() { return exclusionReason; }
    public void setExclusionReason(String exclusionReason) { this.exclusionReason = exclusionReason; }

    public MetricResult getMetricResult() { return metricResult; }
    public void setMetricResult(MetricResult metricResult) { this.metricResult = metricResult; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
