package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_target_allocation")
public class InvestorTargetAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_option_id")
    private SchemeOption schemeOption;

    @Column(name = "category_name", length = 128)
    private String categoryName;

    @Column(name = "target_weight_percentage", nullable = false, precision = 7, scale = 4)
    private BigDecimal targetWeightPercentage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public SchemeOption getSchemeOption() { return schemeOption; }
    public void setSchemeOption(SchemeOption schemeOption) { this.schemeOption = schemeOption; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public BigDecimal getTargetWeightPercentage() { return targetWeightPercentage; }
    public void setTargetWeightPercentage(BigDecimal targetWeightPercentage) { this.targetWeightPercentage = targetWeightPercentage; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    public ZonedDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorTargetAllocation that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
