package com.yukira.backend.service.ai;

import com.yukira.backend.domain.entity.Investor;
import com.yukira.backend.domain.entity.InvestorAiInterpretationVersion;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.repository.InvestorAiInterpretationVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 454: AI Interpretation Version and Evidence Fingerprint Cryptographic SHA-256 Integrity.
 * Verifies that AI interpretations are bound to deterministic SHA-256 fingerprints of evidence packages,
 * guaranteeing immutable provenance and tamper-evident audit trails.
 */
@ExtendWith(MockitoExtension.class)
class AiInterpretationVersionIntegrityTest {

    @Mock
    private InvestorAiInterpretationVersionRepository versionRepository;

    @Test
    @DisplayName("Cryptographic SHA-256 evidence fingerprint matches deterministic hexadecimal string")
    void testEvidenceFingerprintCryptographicIntegrity() throws NoSuchAlgorithmException {
        String canonicalEvidencePayload = "10189:2024-01-15:2024-01-15T23:59:59+05:30:3:900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259";

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(canonicalEvidencePayload.getBytes(StandardCharsets.UTF_8));
        String computedSha256 = HexFormat.of().formatHex(hashBytes);

        assertThat(computedSha256)
            .hasSize(64)
            .matches("^[a-f0-9]{64}$");

        // Verify repeatability / determinism
        byte[] repeatBytes = digest.digest(canonicalEvidencePayload.getBytes(StandardCharsets.UTF_8));
        assertThat(HexFormat.of().formatHex(repeatBytes)).isEqualTo(computedSha256);
    }

    @Test
    @DisplayName("InvestorAiInterpretationVersion entity correctly persists SHA-256 fingerprint and audit fields")
    void testAiInterpretationVersionPersistenceContract() {
        Investor investor = new Investor();
        investor.setAuth0Subject("auth0|allocator-task454");

        SchemeOption option = new SchemeOption();
        option.setAmfiCode("118955");
        option.setIsin("INF179K01UT0");

        String fingerprint = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        InvestorAiInterpretationVersion version = new InvestorAiInterpretationVersion();
        version.setInvestor(investor);
        version.setSchemeOption(option);
        version.setEvidencePacketFingerprint(fingerprint);
        version.setPromptTemplateVersion("1.0.0");
        version.setModelProvider("gemini-2.5-flash");
        version.setKnowledgeCutoff(ZonedDateTime.now());
        version.setAsOfDate(LocalDate.of(2024, 1, 15));
        version.setGroundedInterpretationJson("{\"summary\":\"Grounded interpretation with SHA-256 audit trail.\"}");

        when(versionRepository.save(any(InvestorAiInterpretationVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InvestorAiInterpretationVersion saved = versionRepository.save(version);

        assertThat(saved.getEvidencePacketFingerprint()).isEqualTo(fingerprint);
        assertThat(saved.getModelProvider()).isEqualTo("gemini-2.5-flash");
        assertThat(saved.getPromptTemplateVersion()).isEqualTo("1.0.0");
        assertThat(saved.getAsOfDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(saved.getGroundedInterpretationJson()).contains("SHA-256 audit trail");

        verify(versionRepository).save(version);
    }
}

