package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "scheme")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Scheme implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amc_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Amc amc;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "inception_date", nullable = true)
    private LocalDate inceptionDate;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "subcategory", length = 100)
    private String subcategory;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE";

    @Transient
    private com.yukira.backend.scoring.dto.YukiraScoreSummary yukiraScore;

    public Scheme() {}

    public Scheme(Amc amc, String name, String code, LocalDate inceptionDate) {
        this.amc = amc;
        this.name = name;
        this.code = code;
        this.inceptionDate = inceptionDate;
    }

    public Scheme(Amc amc, String name, String code, LocalDate inceptionDate, String category, String subcategory) {
        this.amc = amc;
        this.name = name;
        this.code = code;
        this.inceptionDate = inceptionDate;
        this.category = category;
        this.subcategory = subcategory;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Amc getAmc() { return amc; }
    public void setAmc(Amc amc) { this.amc = amc; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public LocalDate getInceptionDate() { return inceptionDate; }
    public void setInceptionDate(LocalDate inceptionDate) { this.inceptionDate = inceptionDate; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public com.yukira.backend.scoring.dto.YukiraScoreSummary getYukiraScore() { return yukiraScore; }
    public void setYukiraScore(com.yukira.backend.scoring.dto.YukiraScoreSummary yukiraScore) { this.yukiraScore = yukiraScore; }
}
