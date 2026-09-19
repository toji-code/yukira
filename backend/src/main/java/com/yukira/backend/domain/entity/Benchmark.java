package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "benchmark")
public class Benchmark implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "provider", nullable = false, length = 100)
    private String provider;

    @Column(name = "return_variant", nullable = false, length = 30)
    private String returnVariant = "TRI";

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    public Benchmark() {}

    public Benchmark(String code, String name, String provider, String returnVariant) {
        this.code = code;
        this.name = name;
        this.provider = provider;
        this.returnVariant = returnVariant;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getReturnVariant() { return returnVariant; }
    public void setReturnVariant(String returnVariant) { this.returnVariant = returnVariant; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
