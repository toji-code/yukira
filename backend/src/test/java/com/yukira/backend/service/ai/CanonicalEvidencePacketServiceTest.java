package com.yukira.backend.service.ai;

import com.yukira.backend.dto.ai.EvidenceClaimDto;
import com.yukira.backend.repository.*;
import com.yukira.backend.service.InvestorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task 452: Canonical Evidence Packet Claim-Level Binding Tests")
class CanonicalEvidencePacketServiceTest {

    @Mock
    private SchemeOptionRepository schemeOptionRepository;

    @Mock
    private InvestorAiInterpretationVersionRepository interpretationVersionRepository;

    @Mock
    private InvestorService investorService;

    @Mock
    private NavObservationRepository navObservationRepository;

    @Mock
    private SourceArtifactRepository sourceArtifactRepository;

    private CanonicalEvidencePacketService service;

    @BeforeEach
    void setUp() {
        service = new CanonicalEvidencePacketService(
            schemeOptionRepository,
            interpretationVersionRepository,
            investorService,
            navObservationRepository,
            sourceArtifactRepository
        );
    }

    @Test
    @DisplayName("Should verify all 10 structured abstention codes exist in the contract")
    void shouldVerifyAllTenStructuredAbstentionCodes() {
        CanonicalEvidencePacketService.StructuredAbstentionCode[] codes =
            CanonicalEvidencePacketService.StructuredAbstentionCode.values();

        assertThat(codes).hasSize(10);
        assertThat(codes).containsExactlyInAnyOrder(
            CanonicalEvidencePacketService.StructuredAbstentionCode.UNCONFIGURED_PROVIDER,
            CanonicalEvidencePacketService.StructuredAbstentionCode.INSUFFICIENT_OBSERVATION_COUNT,
            CanonicalEvidencePacketService.StructuredAbstentionCode.BENCHMARK_UNMAPPED,
            CanonicalEvidencePacketService.StructuredAbstentionCode.AMBIGUOUS_HISTORICAL_EXPOSURE,
            CanonicalEvidencePacketService.StructuredAbstentionCode.QUALITY_DEFICIENT_DATA,
            CanonicalEvidencePacketService.StructuredAbstentionCode.HORIZON_TOO_SHORT,
            CanonicalEvidencePacketService.StructuredAbstentionCode.UNRESOLVED_DISCREPANCY,
            CanonicalEvidencePacketService.StructuredAbstentionCode.SAFETY_AUDIT_VIOLATION,
            CanonicalEvidencePacketService.StructuredAbstentionCode.PROVIDER_TIMEOUT,
            CanonicalEvidencePacketService.StructuredAbstentionCode.AUTHENTICATION_FAILED
        );
    }

    @Test
    @DisplayName("Should enforce claim-level binding of text to supporting metric codes")
    void shouldEnforceClaimLevelEvidenceBinding() {
        EvidenceClaimDto claim = new EvidenceClaimDto(
            "Fund generated 2.45% return over the 15-day canonical pilot period.",
            "OBSERVATION",
            List.of("RET-02"),
            "15D",
            "APPROVED"
        );

        assertThat(claim.claimText()).isNotBlank();
        assertThat(claim.claimType()).isEqualTo("OBSERVATION");
        assertThat(claim.supportingMetricCodes()).containsExactly("RET-02");
        assertThat(claim.methodologyStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("Should default claim type to OBSERVATION when null or blank")
    void shouldDefaultClaimTypeWhenBlank() {
        EvidenceClaimDto claim = new EvidenceClaimDto(
            "Sample observation without explicit type.",
            "",
            null,
            "1Y",
            "CANDIDATE"
        );

        assertThat(claim.claimType()).isEqualTo("OBSERVATION");
        assertThat(claim.supportingMetricCodes()).isEmpty();
    }
}

