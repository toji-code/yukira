package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity
@Table(name = "metric_definition")
public class MetricDefinition implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_code", nullable = false, unique = true, length = 50)
    private String metricCode; // e.g. "RET-02"

    @Column(name = "metric_name", nullable = false, length = 255)
    private String metricName; // e.g. "Simple Period Return"

    @Column(name = "analytical_dimension", nullable = false, length = 50)
    private String analyticalDimension; // RETURNS, RISK, RISK_ADJUSTED, RELATIVE, ATTRIBUTION

    @Column(name = "metric_category", nullable = false, length = 50)
    private String metricCategory; // PERFORMANCE, VOLATILITY, DRAWDOWN, BENCHMARK_RELATIVE

    @Column(name = "purpose", nullable = false, columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "formula_display", nullable = false, columnDefinition = "TEXT")
    private String formulaDisplay; // e.g. "(NAV_end - NAV_start) / NAV_start"

    @Column(name = "units", nullable = false, length = 30)
    private String units; // PERCENTAGE, RATIO, CURRENCY, BASIS_POINTS

    @Column(name = "default_frequency", nullable = false, length = 30)
    private String defaultFrequency; // DAILY, DISCRETE_PERIOD, MONTHLY

    @Column(name = "benchmark_required", nullable = false)
    private boolean benchmarkRequired = false;

    @Column(name = "risk_free_required", nullable = false)
    private boolean riskFreeRequired = false;

    @Column(name = "point_in_time_required", nullable = false)
    private boolean pointInTimeRequired = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public MetricDefinition() {}

    public MetricDefinition(
        String metricCode,
        String metricName,
        String analyticalDimension,
        String metricCategory,
        String purpose,
        String formulaDisplay,
        String units,
        String defaultFrequency,
        boolean benchmarkRequired,
        boolean riskFreeRequired,
        boolean pointInTimeRequired
    ) {
        this.metricCode = metricCode;
        this.metricName = metricName;
        this.analyticalDimension = analyticalDimension;
        this.metricCategory = metricCategory;
        this.purpose = purpose;
        this.formulaDisplay = formulaDisplay;
        this.units = units;
        this.defaultFrequency = defaultFrequency;
        this.benchmarkRequired = benchmarkRequired;
        this.riskFreeRequired = riskFreeRequired;
        this.pointInTimeRequired = pointInTimeRequired;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public String getAnalyticalDimension() { return analyticalDimension; }
    public void setAnalyticalDimension(String analyticalDimension) { this.analyticalDimension = analyticalDimension; }

    public String getMetricCategory() { return metricCategory; }
    public void setMetricCategory(String metricCategory) { this.metricCategory = metricCategory; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getFormulaDisplay() { return formulaDisplay; }
    public void setFormulaDisplay(String formulaDisplay) { this.formulaDisplay = formulaDisplay; }

    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }

    public String getDefaultFrequency() { return defaultFrequency; }
    public void setDefaultFrequency(String defaultFrequency) { this.defaultFrequency = defaultFrequency; }

    public boolean isBenchmarkRequired() { return benchmarkRequired; }
    public void setBenchmarkRequired(boolean benchmarkRequired) { this.benchmarkRequired = benchmarkRequired; }

    public boolean isRiskFreeRequired() { return riskFreeRequired; }
    public void setRiskFreeRequired(boolean riskFreeRequired) { this.riskFreeRequired = riskFreeRequired; }

    public boolean isPointInTimeRequired() { return pointInTimeRequired; }
    public void setPointInTimeRequired(boolean pointInTimeRequired) { this.pointInTimeRequired = pointInTimeRequired; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
