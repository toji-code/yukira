package com.yukira.backend.service;

import com.yukira.backend.domain.entity.*;
import com.yukira.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task 451: Portfolio Valuation Denominator and Coverage Contract Tests")
class PortfolioValuationAndDriftServiceTest {

    @Mock
    private InvestorPortfolioHoldingRepository holdingRepository;

    @Mock
    private InvestorTargetAllocationRepository targetAllocationRepository;

    @Mock
    private NavObservationRepository navObservationRepository;

    @Mock
    private InvestorRepository investorRepository;

    private PortfolioValuationAndDriftService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioValuationAndDriftService(
            holdingRepository,
            targetAllocationRepository,
            navObservationRepository,
            investorRepository
        );
    }

    @Test
    @DisplayName("Should explicitly separate priced and unpriced holdings and compute correct valuation coverage ratio")
    void shouldSeparatePricedAndUnpricedHoldings() {
        Investor investor = new Investor();
        investor.setId(101L);
        investor.setAuth0Subject("auth0|test-investor");

        Scheme schemeEquity = new Scheme();
        schemeEquity.setCategory("Flexi Cap Fund");
        SchemePlan planEquity = new SchemePlan();
        planEquity.setScheme(schemeEquity);

        SchemeOption optPriced = new SchemeOption();
        optPriced.setId(201L);
        optPriced.setPlan(planEquity);

        Scheme schemeDebt = new Scheme();
        schemeDebt.setCategory("Liquid Fund");
        SchemePlan planDebt = new SchemePlan();
        planDebt.setScheme(schemeDebt);

        SchemeOption optUnpriced = new SchemeOption();
        optUnpriced.setId(202L);
        optUnpriced.setPlan(planDebt);

        InvestorPortfolioHolding holding1 = new InvestorPortfolioHolding();
        holding1.setInvestor(investor);
        holding1.setSchemeOption(optPriced);
        holding1.setUnits(new BigDecimal("100.00"));
        holding1.setCostBasis(new BigDecimal("10000.00"));

        InvestorPortfolioHolding holding2 = new InvestorPortfolioHolding();
        holding2.setInvestor(investor);
        holding2.setSchemeOption(optUnpriced);
        holding2.setUnits(new BigDecimal("50.00"));
        holding2.setCostBasis(new BigDecimal("5000.00"));

        when(investorRepository.findById(101L)).thenReturn(Optional.of(investor));
        when(holdingRepository.findByInvestor(investor)).thenReturn(List.of(holding1, holding2));

        // Priced holding has NAV 120.00
        NavObservation navObs = new NavObservation();
        navObs.setNavValue(new BigDecimal("120.0000"));
        navObs.setEffectiveDate(LocalDate.of(2024, 1, 15));
        when(navObservationRepository.findTopBySchemeOptionIdOrderByEffectiveDateDesc(201L)).thenReturn(Optional.of(navObs));

        // Unpriced holding has no NAV observation
        when(navObservationRepository.findTopBySchemeOptionIdOrderByEffectiveDateDesc(202L)).thenReturn(Optional.empty());

        InvestorTargetAllocation targetEquity = new InvestorTargetAllocation();
        targetEquity.setInvestor(investor);
        targetEquity.setCategoryName("EQUITY");
        targetEquity.setTargetWeightPercentage(new BigDecimal("70.0"));

        InvestorTargetAllocation targetDebt = new InvestorTargetAllocation();
        targetDebt.setInvestor(investor);
        targetDebt.setCategoryName("DEBT");
        targetDebt.setTargetWeightPercentage(new BigDecimal("30.0"));

        when(targetAllocationRepository.findByInvestor(investor)).thenReturn(List.of(targetEquity, targetDebt));

        Optional<PortfolioValuationAndDriftService.PortfolioValuationSummaryDto> result = service.evaluatePortfolioDrift(101L);

        assertThat(result).isPresent();
        PortfolioValuationAndDriftService.PortfolioValuationSummaryDto summary = result.get();

        // 1 priced, 1 unpriced out of 2 total holdings -> coverage ratio 0.50 (50%)
        assertThat(summary.pricedHoldingsCount()).isEqualTo(1);
        assertThat(summary.unpricedHoldingsCount()).isEqualTo(1);
        assertThat(summary.valuationCoverageRatio()).isEqualTo(0.50);

        // Priced value: 100 * 120.00 = 12000.00
        assertThat(summary.totalCurrentValue()).isEqualByComparingTo(new BigDecimal("12000.0000"));
        // Total cost basis: 10000 + 5000 = 15000
        assertThat(summary.totalCostBasis()).isEqualByComparingTo(new BigDecimal("15000.00"));
    }
}
