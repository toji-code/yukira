package com.yukira.backend.service;

import com.yukira.backend.bootstrap.PilotBootstrapService;
import com.yukira.backend.domain.entity.SchemeOption;
import com.yukira.backend.dto.enrichment.EnrichedFundProfileDto;
import com.yukira.backend.dto.portfolio.HoldingDto;
import com.yukira.backend.repository.SchemeOptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@Commit
public class FundEnrichmentIntegrationTest {

    @Autowired
    private PilotBootstrapService pilotBootstrapService;

    @Autowired
    private FundEnrichmentService fundEnrichmentService;

    @Autowired
    private SchemeOptionRepository schemeOptionRepository;

    private SchemeOption canonicalOption;

    @BeforeEach
    void setUp() {
        canonicalOption = pilotBootstrapService.ensureCanonicalPilotMaster();
        pilotBootstrapService.bootstrapFundEnrichment();
    }

    @Test
    @DisplayName("Verify end-to-end enrichment for canonical pilot HDFC Flexi Cap Fund Direct Growth")
    void testCanonicalPilotEnrichment() {
        EnrichedFundProfileDto profile = fundEnrichmentService.getEnrichedProfile(canonicalOption.getId(), null);

        assertNotNull(profile, "Profile must not be null");
        assertEquals(canonicalOption.getId(), profile.schemeOptionId());
        assertEquals("HDFC_FLEXI", profile.schemeCode());
        assertEquals("DIRECT", profile.planType());
        assertEquals("GROWTH", profile.optionType());
        assertEquals("118955", profile.amfiCode());
        assertEquals("INF179K01UT0", profile.isin());
        assertEquals("VERIFIED_PRIMARY_SOURCE", profile.epistemicStatus());

        // 1. AUM
        assertNotNull(profile.aum());
        assertEquals("AVAILABLE", profile.aum().status());
        assertEquals("VERIFIED", profile.aum().dataQuality());
        assertEquals("INR", profile.aum().currency());
        assertEquals("Crores", profile.aum().unit());
        assertEquals(0, new BigDecimal("47642.42").compareTo(profile.aum().amount()));
        assertEquals("₹47,642.42 Cr", profile.aum().formattedAmount());
        assertEquals(LocalDate.of(2024, 1, 31), profile.aum().asOfDate());
        assertNotNull(profile.aum().sourceArtifactId());

        // 2. Expense Ratio (TER)
        assertNotNull(profile.expenseRatio());
        assertEquals("AVAILABLE", profile.expenseRatio().status());
        assertEquals("VALID", profile.expenseRatio().dataQuality());
        assertEquals(0, new BigDecimal("0.0078").compareTo(profile.expenseRatio().expenseRatio()));
        assertEquals("0.78%", profile.expenseRatio().formattedPercentage());
        assertEquals(0, new BigDecimal("0.0144").compareTo(profile.expenseRatio().regularPlanRatio()));
        assertEquals("1.44%", profile.expenseRatio().formattedRegularPlanPercentage());
        assertEquals("DIRECT", profile.expenseRatio().planType());
        assertEquals(LocalDate.of(2024, 1, 31), profile.expenseRatio().asOfDate());
        assertNotNull(profile.expenseRatio().sourceArtifactId());

        // 3. Fund Manager
        assertFalse(profile.fundManagers().isEmpty());
        var manager = profile.fundManagers().get(0);
        assertEquals("Ms. Roshi Jain", manager.name());
        assertEquals("Senior Fund Manager - Equity", manager.role());
        assertEquals(LocalDate.of(2022, 7, 29), manager.startDate());
        assertEquals(LocalDate.of(2024, 1, 31), manager.asOfDate());
        assertEquals("VALID", manager.dataQuality());

        // 4. Investment Terms (SIP / Lumpsum)
        assertNotNull(profile.investmentTerms());
        assertEquals("AVAILABLE", profile.investmentTerms().status());
        assertEquals("VALID", profile.investmentTerms().dataQuality());
        assertEquals(0, new BigDecimal("100.00").compareTo(profile.investmentTerms().minSipAmount()));
        assertEquals("₹100", profile.investmentTerms().formattedMinSip());
        assertTrue(profile.investmentTerms().sipFrequencies().contains("Monthly"));
        assertEquals(0, new BigDecimal("100.00").compareTo(profile.investmentTerms().minLumpsumAmount()));
        assertEquals("₹100", profile.investmentTerms().formattedMinLumpsum());
        assertEquals("1.00% if redeemed within 1 year; Nil after 1 year", profile.investmentTerms().exitLoadDescription());
        assertEquals(LocalDate.of(2024, 1, 1), profile.investmentTerms().asOfDate());

        // 5. Holdings (Verifying ICICI Bank, HDFC Bank, Cipla)
        List<HoldingDto> holdings = fundEnrichmentService.getHoldings(canonicalOption.getId(), null);
        assertFalse(holdings.isEmpty(), "Holdings list must not be empty");
        assertEquals(10, holdings.size(), "Canonical pilot has 10 top holdings seeded");

        HoldingDto topHolding = holdings.get(0);
        assertEquals("ICICI Bank Ltd.", topHolding.securityName());
        assertEquals("9.50%", topHolding.formattedWeight());
        assertEquals("INE090A01021", topHolding.isin());
        assertEquals("Financial Services", topHolding.sector());
        assertEquals("Equity", topHolding.assetClass());

        HoldingDto secondHolding = holdings.get(1);
        assertEquals("HDFC Bank Ltd.", secondHolding.securityName());
        assertEquals("9.27%", secondHolding.formattedWeight());

        HoldingDto thirdHolding = holdings.get(2);
        assertEquals("Cipla Ltd.", thirdHolding.securityName());
        assertEquals("5.39%", thirdHolding.formattedWeight());
        assertEquals("INE059A01026", thirdHolding.isin());
        assertEquals("Healthcare", thirdHolding.sector());

        // 6. Verify Scheme-Level Isolation (Option-specific facts must NOT bleed into scheme level)
        EnrichedFundProfileDto schemeProfile = fundEnrichmentService.getSchemeLevelEnrichment(canonicalOption.getPlan().getScheme().getId(), null);
        assertNull(schemeProfile.schemeOptionId(), "Scheme-level profile must not have a schemeOptionId");
        assertEquals("MISSING", schemeProfile.expenseRatio().status(), "TER must be MISSING at scheme level");
        assertEquals("MISSING", schemeProfile.aum().status(), "Option AUM must be MISSING at scheme level");
        assertEquals("MISSING", schemeProfile.holdingsSummary().status(), "Holdings must be MISSING at scheme level");
        assertEquals("AVAILABLE", schemeProfile.investmentTerms().status(), "KIM terms must be AVAILABLE at scheme level");
        assertFalse(schemeProfile.fundManagers().isEmpty(), "Fund managers must be AVAILABLE at scheme level");
    }
}
