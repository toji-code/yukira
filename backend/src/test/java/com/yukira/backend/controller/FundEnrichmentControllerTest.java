package com.yukira.backend.controller;

import com.yukira.backend.dto.enrichment.*;
import com.yukira.backend.dto.portfolio.HoldingDto;
import com.yukira.backend.repository.SchemeRepository;
import com.yukira.backend.service.FundEnrichmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FundEnrichmentControllerTest {

    @Mock
    private FundEnrichmentService enrichmentService;

    @Mock
    private SchemeRepository schemeRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FundEnrichmentController controller = new FundEnrichmentController(enrichmentService, schemeRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/schemes/options/{id}/enrichment returns serialized enriched profile")
    void shouldReturnEnrichedProfile() throws Exception {
        EnrichedFundProfileDto mockProfile = new EnrichedFundProfileDto(
                10L,
                1L,
                "HDFC Flexi Cap Fund",
                "HDFC_FLEXI",
                "DIRECT",
                "GROWTH",
                "118955",
                "INF179K01UT0",
                new AumDto(
                        new BigDecimal("47642.42"),
                        "₹47,642.42 Cr",
                        "INR",
                        "Crores",
                        LocalDate.of(2024, 1, 31),
                        10L,
                        "Official AMC Monthly Portfolio Disclosure",
                        "VERIFIED",
                        "AVAILABLE"
                ),
                new ExpenseRatioDto(
                        new BigDecimal("0.0078"),
                        "0.78%",
                        "DIRECT",
                        "GROWTH",
                        new BigDecimal("0.0144"),
                        "1.44%",
                        LocalDate.of(2024, 1, 31),
                        10L,
                        "AMC Statutory Total Expense Ratio (TER) Disclosure",
                        "VERIFIED",
                        "AVAILABLE"
                ),
                List.of(
                        new FundManagerDto(
                                "Ms. Roshi Jain",
                                "Senior Fund Manager",
                                LocalDate.of(2022, 7, 29),
                                null,
                                LocalDate.of(2024, 1, 31),
                                10L,
                                "Official AMC Scheme Factsheet & SID",
                                "VERIFIED",
                                "AVAILABLE"
                        )
                ),
                new InvestmentTermsDto(
                        new BigDecimal("100.00"),
                        "₹100",
                        List.of("Monthly", "Weekly", "Daily", "Quarterly"),
                        new BigDecimal("100.00"),
                        "₹100",
                        new BigDecimal("100.00"),
                        "₹100",
                        null,
                        "1.00% if redeemed within 1 year; Nil thereafter",
                        LocalDate.of(2024, 1, 1),
                        11L,
                        "Official Key Information Memorandum (KIM)",
                        "VERIFIED",
                        "AVAILABLE"
                ),
                new HoldingsSummaryDto(
                        LocalDate.of(2024, 1, 31),
                        10,
                        new BigDecimal("0.5082"),
                        "50.82%",
                        10L,
                        "SEBI Monthly Portfolio Disclosure",
                        "VERIFIED",
                        "AVAILABLE"
                ),
                "VERIFIED_PRIMARY_SOURCE"
        );

        when(enrichmentService.getEnrichedProfile(eq(1L), any())).thenReturn(mockProfile);

        mockMvc.perform(get("/api/v1/schemes/options/1/enrichment")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemeOptionId").value(1))
                .andExpect(jsonPath("$.schemeCode").value("HDFC_FLEXI"))
                .andExpect(jsonPath("$.planType").value("DIRECT"))
                .andExpect(jsonPath("$.aum.formattedAmount").value("₹47,642.42 Cr"))
                .andExpect(jsonPath("$.expenseRatio.formattedPercentage").value("0.78%"))
                .andExpect(jsonPath("$.expenseRatio.formattedRegularPlanPercentage").value("1.44%"))
                .andExpect(jsonPath("$.fundManagers[0].name").value("Ms. Roshi Jain"))
                .andExpect(jsonPath("$.investmentTerms.formattedMinSip").value("₹100"))
                .andExpect(jsonPath("$.holdingsSummary.reportedHoldingsCount").value(10))
                .andExpect(jsonPath("$.epistemicStatus").value("VERIFIED_PRIMARY_SOURCE"));
    }

    @Test
    @DisplayName("GET /api/v1/schemes/{schemeId}/enrichment returns strictly scheme-level facts without option bleed")
    void shouldReturnSchemeLevelEnrichmentWithoutOptionBleed() throws Exception {
        when(schemeRepository.existsById(10L)).thenReturn(true);

        EnrichedFundProfileDto mockSchemeProfile = new EnrichedFundProfileDto(
                10L,
                null,
                "HDFC Flexi Cap Fund",
                "HDFC_FLEXI",
                null,
                null,
                null,
                null,
                AumDto.missing(),
                ExpenseRatioDto.missing("ALL_PLANS", "ALL_OPTIONS"),
                List.of(
                        new FundManagerDto(
                                "Ms. Roshi Jain",
                                "Senior Fund Manager",
                                LocalDate.of(2022, 7, 29),
                                null,
                                LocalDate.of(2024, 1, 31),
                                10L,
                                "Official AMC Scheme Factsheet & SID",
                                "VERIFIED",
                                "AVAILABLE"
                        )
                ),
                new InvestmentTermsDto(
                        new BigDecimal("100.00"),
                        "₹100",
                        List.of("Monthly"),
                        new BigDecimal("100.00"),
                        "₹100",
                        new BigDecimal("100.00"),
                        "₹100",
                        null,
                        "1.00% if redeemed within 1 year; Nil thereafter",
                        LocalDate.of(2024, 1, 1),
                        11L,
                        "Official Key Information Memorandum (KIM)",
                        "VERIFIED",
                        "AVAILABLE"
                ),
                HoldingsSummaryDto.missing(),
                "SCHEME_LEVEL_AGGREGATE"
        );

        when(enrichmentService.getSchemeLevelEnrichment(eq(10L), any())).thenReturn(mockSchemeProfile);

        mockMvc.perform(get("/api/v1/schemes/10/enrichment")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemeId").value(10))
                .andExpect(jsonPath("$.schemeOptionId").doesNotExist())
                .andExpect(jsonPath("$.expenseRatio.status").value("MISSING"))
                .andExpect(jsonPath("$.aum.status").value("MISSING"))
                .andExpect(jsonPath("$.holdingsSummary.status").value("MISSING"))
                .andExpect(jsonPath("$.investmentTerms.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.investmentTerms.formattedMinSip").value("₹100"))
                .andExpect(jsonPath("$.fundManagers[0].name").value("Ms. Roshi Jain"))
                .andExpect(jsonPath("$.epistemicStatus").value("SCHEME_LEVEL_AGGREGATE"));
    }
}
