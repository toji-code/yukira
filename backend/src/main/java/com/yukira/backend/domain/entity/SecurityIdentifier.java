package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "security_identifier", uniqueConstraints = {
    @UniqueConstraint(name = "uq_sec_id", columnNames = {"id_type", "id_value", "valid_from"})
})
public class SecurityIdentifier implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(name = "id_type", nullable = false, length = 30)
    private String idType;

    @Column(name = "id_value", nullable = false, length = 100)
    private String idValue;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    public SecurityIdentifier() {}

    public SecurityIdentifier(Security security, String idType, String idValue, LocalDate validFrom) {
        this.security = security;
        this.idType = idType;
        this.idValue = idValue;
        this.validFrom = validFrom;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public String getIdType() { return idType; }
    public void setIdType(String idType) { this.idType = idType; }

    public String getIdValue() { return idValue; }
    public void setIdValue(String idValue) { this.idValue = idValue; }

    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }

    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
}
