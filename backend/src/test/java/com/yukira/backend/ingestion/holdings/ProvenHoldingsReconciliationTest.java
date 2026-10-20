package com.yukira.backend.ingestion.holdings;

import com.yukira.backend.domain.entity.PortfolioSnapshot;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.domain.entity.SourceArtifact;
import com.yukira.backend.service.HoldingsCoverageAuditService;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

public class ProvenHoldingsReconciliationTest {

    @Test
    void testFixtureArtifactCannotBecomeProductionVerified() {
        SourceArtifact fixtureArtifact = new SourceArtifact();
        fixtureArtifact.setSha256Hash("SEED_HOLDINGS_118955_2024-01-31");
        fixtureArtifact.setPayloadBlob(new byte[100]);

        assertFalse(HoldingsCoverageAuditService.isProductionVerifiedArtifact(fixtureArtifact),
                "SourceArtifact with SEED_HOLDINGS_ prefix must NOT be classified as production verified");
    }

    @Test
    void testMissingSourceArtifactCannotBecomeProductionVerified() {
        assertFalse(HoldingsCoverageAuditService.isProductionVerifiedArtifact(null),
                "Null SourceArtifact must NOT be classified as production verified");
    }

    @Test
    void testSyntheticShaCannotBecomeProductionVerified() {
        SourceArtifact syntheticArtifact = new SourceArtifact();
        syntheticArtifact.setSha256Hash("7f8a9b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a");
        syntheticArtifact.setPayloadBlob(null);

        assertFalse(HoldingsCoverageAuditService.isProductionVerifiedArtifact(syntheticArtifact),
                "SourceArtifact with hardcoded synthetic SHA and null payload blob must NOT be classified as production verified");
    }

    @Test
    void testArtifactWithoutPayloadBlobCannotBecomeProductionVerified() {
        SourceArtifact noBlobArtifact = new SourceArtifact();
        noBlobArtifact.setSha256Hash("a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2");
        noBlobArtifact.setPayloadBlob(null);

        assertFalse(HoldingsCoverageAuditService.isProductionVerifiedArtifact(noBlobArtifact),
                "SourceArtifact without stored payload bytes must NOT be classified as production verified");
    }

    @Test
    void testValidRealSourceArtifactIsClassifiedAsProductionVerified() throws Exception {
        byte[] realBytes = "SchemeCode,SecurityName\n122639,HDFC Bank Ltd.".getBytes();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String sha256 = HexFormat.of().formatHex(md.digest(realBytes));

        SourceArtifact realArtifact = new SourceArtifact();
        realArtifact.setSha256Hash(sha256);
        realArtifact.setPayloadBlob(realBytes);

        assertTrue(HoldingsCoverageAuditService.isProductionVerifiedArtifact(realArtifact),
                "SourceArtifact with real payload bytes and valid SHA256 must be classified as production verified");
    }

    @Test
    void testPpfasFlexiAndPpfasTaxSaverIdentitySeparation() {
        MonthlyHoldingsSourceRegistry registry = new MonthlyHoldingsSourceRegistry();

        var flexiOpt = registry.findByAmfiCode("122639");
        assertTrue(flexiOpt.isPresent());
        assertEquals("PPFAS_FLEXI", flexiOpt.get().schemeCode());
        assertEquals("122639", flexiOpt.get().amfiCode());
        assertEquals("INF879O01027", flexiOpt.get().isin());

        var taxOpt = registry.findByAmfiCode("147481");
        assertTrue(taxOpt.isPresent());
        assertEquals("PPFAS_TAX", taxOpt.get().schemeCode());
        assertEquals("147481", taxOpt.get().amfiCode());
        assertEquals("INF879O01100", taxOpt.get().isin());

        assertNotEquals(flexiOpt.get().amfiCode(), taxOpt.get().amfiCode());
        assertNotEquals(flexiOpt.get().isin(), taxOpt.get().isin());
    }
}
