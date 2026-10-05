package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "scheme_option")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SchemeOption implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private SchemePlan plan;

    @Column(name = "option_type", nullable = false, length = 30)
    private String optionType; // GROWTH, IDCW_PAYOUT, IDCW_REINVESTMENT

    @Column(name = "amfi_code", unique = true, length = 50)
    private String amfiCode;

    @Column(name = "isin", unique = true, length = 20)
    private String isin;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE";

    @Transient
    private com.yukira.backend.scoring.dto.YukiraScoreSummary yukiraScore;

    public SchemeOption() {}

    public SchemeOption(SchemePlan plan, String optionType, String amfiCode, String isin) {
        this.plan = plan;
        this.optionType = optionType;
        this.amfiCode = amfiCode;
        this.isin = isin;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SchemePlan getPlan() { return plan; }
    public void setPlan(SchemePlan plan) { this.plan = plan; }

    public String getOptionType() { return optionType; }
    public void setOptionType(String optionType) { this.optionType = optionType; }

    public String getAmfiCode() { return amfiCode; }
    public void setAmfiCode(String amfiCode) { this.amfiCode = amfiCode; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public com.yukira.backend.scoring.dto.YukiraScoreSummary getYukiraScore() { return yukiraScore; }
    public void setYukiraScore(com.yukira.backend.scoring.dto.YukiraScoreSummary yukiraScore) { this.yukiraScore = yukiraScore; }
}
