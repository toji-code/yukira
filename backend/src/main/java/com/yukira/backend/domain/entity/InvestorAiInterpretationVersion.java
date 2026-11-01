package com.yukira.backend.domain.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "investor_ai_interpretation_version")
public class InvestorAiInterpretationVersion implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id")
    private Investor investor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_option_id")
    private SchemeOption schemeOption;

    @Column(name = "evidence_packet_fingerprint", nullable = false, length = 128)
    private String evidencePacketFingerprint;

    @Column(name = "prompt_template_version", nullable = false, length = 32)
    private String promptTemplateVersion;

    @Column(name = "model_provider", nullable = false, length = 64)
    private String modelProvider;

    @Column(name = "knowledge_cutoff", nullable = false)
    private ZonedDateTime knowledgeCutoff;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Column(name = "abstention_state", nullable = false, length = 32)
    private String abstentionState = "NONE"; // NONE, PARTIAL_ABSTENTION, FULL_ABSTENTION

    @Column(name = "abstention_reason")
    private String abstentionReason;

    @Column(name = "grounded_interpretation_json", nullable = false, columnDefinition = "text")
    private String groundedInterpretationJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public InvestorAiInterpretationVersion() {}

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        if (abstentionState == null) abstentionState = "NONE";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Investor getInvestor() { return investor; }
    public void setInvestor(Investor investor) { this.investor = investor; }

    public SchemeOption getSchemeOption() { return schemeOption; }
    public void setSchemeOption(SchemeOption schemeOption) { this.schemeOption = schemeOption; }

    public String getEvidencePacketFingerprint() { return evidencePacketFingerprint; }
    public void setEvidencePacketFingerprint(String evidencePacketFingerprint) { this.evidencePacketFingerprint = evidencePacketFingerprint; }

    public String getPromptTemplateVersion() { return promptTemplateVersion; }
    public void setPromptTemplateVersion(String promptTemplateVersion) { this.promptTemplateVersion = promptTemplateVersion; }

    public String getModelProvider() { return modelProvider; }
    public void setModelProvider(String modelProvider) { this.modelProvider = modelProvider; }

    public ZonedDateTime getKnowledgeCutoff() { return knowledgeCutoff; }
    public void setKnowledgeCutoff(ZonedDateTime knowledgeCutoff) { this.knowledgeCutoff = knowledgeCutoff; }

    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }

    public String getAbstentionState() { return abstentionState; }
    public void setAbstentionState(String abstentionState) { this.abstentionState = abstentionState; }

    public String getAbstentionReason() { return abstentionReason; }
    public void setAbstentionReason(String abstentionReason) { this.abstentionReason = abstentionReason; }

    public String getGroundedInterpretationJson() { return groundedInterpretationJson; }
    public void setGroundedInterpretationJson(String groundedInterpretationJson) { this.groundedInterpretationJson = groundedInterpretationJson; }

    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvestorAiInterpretationVersion that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

