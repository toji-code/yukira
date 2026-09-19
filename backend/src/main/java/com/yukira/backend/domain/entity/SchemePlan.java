package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "scheme_plan")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SchemePlan implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @Column(name = "plan_type", nullable = false, length = 30)
    private String planType; // DIRECT, REGULAR

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    public SchemePlan() {}

    public SchemePlan(Scheme scheme, String planType, String code) {
        this.scheme = scheme;
        this.planType = planType;
        this.code = code;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }

    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
