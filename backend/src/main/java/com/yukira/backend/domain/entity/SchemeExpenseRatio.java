package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "scheme_expense_ratio", uniqueConstraints = {
    @UniqueConstraint(name = "uq_scheme_option_expense", columnNames = {"scheme_option_id", "as_of_date"})
})
public class SchemeExpenseRatio implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_option_id", nullable = false)
    private SchemeOption schemeOption;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "expense_ratio", nullable = false, precision = 10, scale = 6)
    private BigDecimal expenseRatio;

    @Column(name = "plan_type", nullable = false, length = 30)
    private String planType;

    @Column(name = "option_type", nullable = false, length = 30)
    private String optionType;

    @Column(name = "regular_plan_ratio", precision = 10, scale = 6)
    private BigDecimal regularPlanRatio;

    @Column(name = "availability_time", nullable = false)
    private OffsetDateTime availabilityTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_artifact_id")
    private SourceArtifact sourceArtifact;

    @Column(name = "quality_assessment", nullable = false, length = 30)
    private String qualityAssessment = "VALID";

    public SchemeExpenseRatio() {}

    public SchemeExpenseRatio(SchemeOption schemeOption, LocalDate asOfDate, BigDecimal expenseRatio,
                              String planType, String optionType, OffsetDateTime availabilityTime) {
        this.schemeOption = schemeOption;
        this.asOfDate = asOfDate;
        this.expenseRatio = expenseRatio;
        this.planType = planType;
        this.optionType = optionType;
        this.availabilityTime = availabilityTime;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SchemeOption getSchemeOption() { return schemeOption; }
    public void setSchemeOption(SchemeOption schemeOption) { this.schemeOption = schemeOption; }

    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }

    public BigDecimal getExpenseRatio() { return expenseRatio; }
    public void setExpenseRatio(BigDecimal expenseRatio) { this.expenseRatio = expenseRatio; }

    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }

    public String getOptionType() { return optionType; }
    public void setOptionType(String optionType) { this.optionType = optionType; }

    public BigDecimal getRegularPlanRatio() { return regularPlanRatio; }
    public void setRegularPlanRatio(BigDecimal regularPlanRatio) { this.regularPlanRatio = regularPlanRatio; }

    public OffsetDateTime getAvailabilityTime() { return availabilityTime; }
    public void setAvailabilityTime(OffsetDateTime availabilityTime) { this.availabilityTime = availabilityTime; }

    public SourceArtifact getSourceArtifact() { return sourceArtifact; }
    public void setSourceArtifact(SourceArtifact sourceArtifact) { this.sourceArtifact = sourceArtifact; }

    public String getQualityAssessment() { return qualityAssessment; }
    public void setQualityAssessment(String qualityAssessment) { this.qualityAssessment = qualityAssessment; }
}
