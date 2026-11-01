package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "scheme_lifecycle_record")
public class SchemeLifecycleRecord implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private String lifecycleStatus = "ACTIVE"; // ACTIVE, RENAMED, MERGED, DISCONTINUED, SUSPENDED

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "successor_scheme_id")
    private Scheme successorScheme;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "predecessor_scheme_id")
    private Scheme predecessorScheme;

    @Column(name = "regulatory_circular_ref")
    private String regulatoryCircularRef;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public SchemeLifecycleRecord() {}

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        if (lifecycleStatus == null) lifecycleStatus = "ACTIVE";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }

    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public Scheme getSuccessorScheme() { return successorScheme; }
    public void setSuccessorScheme(Scheme successorScheme) { this.successorScheme = successorScheme; }

    public Scheme getPredecessorScheme() { return predecessorScheme; }
    public void setPredecessorScheme(Scheme predecessorScheme) { this.predecessorScheme = predecessorScheme; }

    public String getRegulatoryCircularRef() { return regulatoryCircularRef; }
    public void setRegulatoryCircularRef(String regulatoryCircularRef) { this.regulatoryCircularRef = regulatoryCircularRef; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchemeLifecycleRecord that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

