package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "metric_result", uniqueConstraints = {
    @UniqueConstraint(name = "uq_run_metric_period", columnNames = {"calculation_run_id", "metric_code", "period_type"})
})
public class MetricResult implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculation_run_id", nullable = false)
    private CalculationRun calculationRun;

    @Column(name = "metric_code", nullable = false, length = 50)
    private String metricCode;

    @Column(name = "period_type", nullable = false, length = 30)
    private String periodType = "1Y";

    @Column(name = "numeric_value", precision = 30, scale = 10)
    private BigDecimal numericValue;

    @Column(name = "string_value", length = 100)
    private String stringValue;

    @Column(name = "units", nullable = false, length = 30)
    private String units;

    @Column(name = "calculation_status", nullable = false, length = 30)
    private String calculationStatus = "CALCULATED";

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "diagnostics", columnDefinition = "jsonb")
    private String diagnostics;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    public MetricResult() {}

    public MetricResult(CalculationRun calculationRun, String metricCode, String periodType,
                        BigDecimal numericValue, String units, String calculationStatus) {
        this.calculationRun = calculationRun;
        this.metricCode = metricCode;
        this.periodType = periodType;
        this.numericValue = numericValue;
        this.units = units;
        this.calculationStatus = calculationStatus;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CalculationRun getCalculationRun() { return calculationRun; }
    public void setCalculationRun(CalculationRun calculationRun) { this.calculationRun = calculationRun; }

    public String getMetricCode() { return metricCode; }
    public void setMetricCode(String metricCode) { this.metricCode = metricCode; }

    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }

    public BigDecimal getNumericValue() { return numericValue; }
    public void setNumericValue(BigDecimal numericValue) { this.numericValue = numericValue; }

    public String getStringValue() { return stringValue; }
    public void setStringValue(String stringValue) { this.stringValue = stringValue; }

    public String getUnits() { return units; }
    public void setUnits(String units) { this.units = units; }

    public String getCalculationStatus() { return calculationStatus; }
    public void setCalculationStatus(String calculationStatus) { this.calculationStatus = calculationStatus; }

    public String getDiagnostics() { return diagnostics; }
    public void setDiagnostics(String diagnostics) { this.diagnostics = diagnostics; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
