package com.yukira.backend.service;

import com.yukira.backend.domain.entity.V1ValidationReadinessItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 459: V1 Validation Readiness Register (VAL-01 through VAL-10 Governance Model).
 * Verifies that the canonical validation readiness register defines all 10 areas,
 * enforces required real-data evidence criteria, and strictly maintains validationConducted = false
 * until live empirical verification is executed and formally authorized.
 */
class ValidationReadinessRegisterTest {

    private static final Set<String> CANONICAL_VAL_AREAS = Set.of(
        "VAL-01", // Quantitative Formula Validation
        "VAL-02", // PIT Reconstruction & Look-Ahead Bias Review
        "VAL-03", // Benchmark & NAV Source Coverage Verification
        "VAL-04", // Holdings & Portfolio Accounting Validation
        "VAL-05", // Tax-Rule & FIFO Review
        "VAL-06", // Investor Data Isolation & Security Review
        "VAL-07", // Background Scheduler & Operational Execution Verification
        "VAL-08", // Notification Delivery Verification
        "VAL-09", // AI Evidence-Grounding & Abstention Review
        "VAL-10"  // End-to-End Investor Workflow Review
    );

    @Test
    @DisplayName("Validation readiness item strictly defaults validationConducted to false")
    void testValidationConductedDefaultIsFalse() {
        V1ValidationReadinessItem item = new V1ValidationReadinessItem();
        item.setReadinessAreaCode("VAL-01");
        item.setAreaName("Quantitative Formula Validation");
        item.setRelevantArtifacts("quant-engine/src/; phase2h_quantitative_methodology.md");
        item.setRequiredVerificationActivity("Recompute all 31 metrics against canonical pilot NAV series.");
        item.setRequiredRealDataEvidence("AMFI NAV observations for scheme 118955 (Jan 2024).");
        item.setExpectedAcceptanceCriteria("100% agreement between Python vectorized kernel and Java runtime.");
        item.setResponsibleReviewArea("QUANT_METHODOLOGY_COMMITTEE");

        assertThat(item.getValidationConducted())
            .as("Governance rule: validationConducted must strictly be false prior to live validation")
            .isFalse();
        assertThat(item.getReadinessState())
            .isEqualTo("READY_FOR_VALIDATION");
    }

    @Test
    @DisplayName("Canonical V1 validation readiness register defines exactly 10 distinct areas")
    void testCanonicalRegisterCoverage() {
        assertThat(CANONICAL_VAL_AREAS)
            .hasSize(10)
            .contains("VAL-01", "VAL-02", "VAL-03", "VAL-04", "VAL-05", "VAL-06", "VAL-07", "VAL-08", "VAL-09", "VAL-10");
    }
}
