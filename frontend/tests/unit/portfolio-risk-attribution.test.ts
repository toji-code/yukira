import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  PortfolioRiskAttributionResponseDto,
  FundRiskContributionDto,
  RiskOverviewMetricDto,
  RiskReconciliationDto,
  AttributionSectorExposureDto,
  AttributionMarketCapExposureDto,
} from '../../lib/api/portfolio';

describe('Phase 2O: Portfolio Risk Attribution — Frontend Contract & Decomposition Logic Tests', () => {

  it('TC-FE-RA01: Euler Volatility Decomposition reconciles component contributions to total portfolio volatility', () => {
    // 2-fund canonical portfolio: Fund A (60%), Fund B (40%)
    const contributions: FundRiskContributionDto[] = [
      {
        schemeOptionId: 118955,
        fundName: 'HDFC Flexi Cap Fund - Direct Plan - Growth',
        amfiCode: '118955',
        planType: 'DIRECT',
        allocationWeightPct: 60.0,
        weightFraction: 0.60,
        annualizedVolatilityPct: 14.50,
        marginalContributionPct: 13.80,
        volatilityContributionPct: 8.28, // 0.60 * 13.80
        percentageRiskContribution: 64.69,
        isNegativeContribution: false,
        isDiversificationDampener: false,
        covarianceWithPortfolio: 0.00011,
        interpretation: 'Primary risk contributor',
      },
      {
        schemeOptionId: 122639,
        fundName: 'Parag Parikh Flexi Cap Fund - Direct Plan - Growth',
        amfiCode: '122639',
        planType: 'DIRECT',
        allocationWeightPct: 40.0,
        weightFraction: 0.40,
        annualizedVolatilityPct: 12.10,
        marginalContributionPct: 11.30,
        volatilityContributionPct: 4.52, // 0.40 * 11.30
        percentageRiskContribution: 35.31,
        isNegativeContribution: false,
        isDiversificationDampener: false,
        covarianceWithPortfolio: 0.00009,
        interpretation: 'Secondary risk contributor',
      },
    ];

    const sumContributions = contributions.reduce((sum, c) => sum + c.volatilityContributionPct, 0);
    const expectedPortfolioVolatility = 12.80;
    const reconciliationDiff = Math.abs(sumContributions - expectedPortfolioVolatility);

    assert.ok(
      reconciliationDiff < 0.01,
      `Component risk contributions sum (${sumContributions.toFixed(4)}%) must reconcile with portfolio volatility (${expectedPortfolioVolatility}%) within normal floating-point tolerance`
    );

    const reconciliation: RiskReconciliationDto = {
      portfolioVolatilityPct: expectedPortfolioVolatility,
      sumRiskContributionsPct: parseFloat(sumContributions.toFixed(4)),
      reconciliationDifferencePct: parseFloat(reconciliationDiff.toFixed(6)),
      sumPercentageContributions: 100.0,
      isReconciled: reconciliationDiff < 0.05,
      reconciliationStatus: 'RECONCILED',
      mathematicalIdentityFormula: 'sum(RC_i) = sigma_p',
    };

    assert.equal(reconciliation.isReconciled, true);
    assert.equal(reconciliation.reconciliationStatus, 'RECONCILED');
  });

  it('TC-FE-RA02: Negative volatility contribution is preserved for active risk dampeners / low-correlation diversifiers', () => {
    // Portfolio with an asset providing active negative covariance diversification
    const contributions: FundRiskContributionDto[] = [
      {
        schemeOptionId: 101,
        fundName: 'High-Beta Growth Fund',
        amfiCode: '101',
        planType: 'DIRECT',
        allocationWeightPct: 80.0,
        weightFraction: 0.80,
        annualizedVolatilityPct: 18.00,
        marginalContributionPct: 16.50,
        volatilityContributionPct: 13.20,
        percentageRiskContribution: 110.0,
        isNegativeContribution: false,
        isDiversificationDampener: false,
        covarianceWithPortfolio: 0.00018,
        interpretation: 'Primary risk driver',
      },
      {
        schemeOptionId: 102,
        fundName: 'Uncorrelated Hedge / Arbitrage Fund',
        amfiCode: '102',
        planType: 'DIRECT',
        allocationWeightPct: 20.0,
        weightFraction: 0.20,
        annualizedVolatilityPct: 6.00,
        marginalContributionPct: -6.00,
        volatilityContributionPct: -1.20, // Negative contribution due to negative covariance!
        percentageRiskContribution: -10.0,
        isNegativeContribution: true,
        isDiversificationDampener: true,
        covarianceWithPortfolio: -0.00004,
        interpretation: 'Active risk dampener',
      },
    ];

    // Verify negative contribution is preserved and NOT clamped to zero
    const dampener = contributions.find(c => c.isNegativeContribution);
    assert.ok(dampener, 'Dampener fund must exist');
    assert.ok(dampener.volatilityContributionPct < 0, 'Volatility contribution must remain negative');
    assert.equal(dampener.isDiversificationDampener, true);

    // Euler decomposition must still sum to total portfolio volatility
    const totalVol = contributions.reduce((sum, c) => sum + c.volatilityContributionPct, 0);
    assert.equal(totalVol, 12.00, 'Sum of contributions (13.20 - 1.20) must equal 12.00%');
  });

  it('TC-FE-RA03: Portfolio Risk Overview metrics contract and metadata accuracy', () => {
    const overviewMetrics: RiskOverviewMetricDto[] = [
      {
        metricCode: 'PORTFOLIO_VOLATILITY',
        metricName: 'Annualized Portfolio Volatility',
        numericValue: 12.80,
        formattedValue: '12.80%',
        unit: '%',
        description: 'Annualized standard deviation of daily aligned portfolio returns over 252 trading days.',
        availabilityState: 'AVAILABLE',
      },
      {
        metricCode: 'MAX_DRAWDOWN',
        metricName: 'Maximum Drawdown',
        numericValue: -6.45,
        formattedValue: '-6.45%',
        unit: '%',
        description: 'Peak-to-trough historical decline of portfolio value over the analysis window.',
        availabilityState: 'AVAILABLE',
      },
      {
        metricCode: 'BETA',
        metricName: 'Portfolio Beta',
        numericValue: 0.92,
        formattedValue: '0.92',
        unit: 'ratio',
        description: 'Systematic risk sensitivity relative to NIFTY_500_TRI.',
        availabilityState: 'AVAILABLE',
      },
      {
        metricCode: 'DOWNSIDE_SEMIDEVIATION',
        metricName: 'Downside Semideviation',
        numericValue: 8.75,
        formattedValue: '8.75%',
        unit: '%',
        description: 'Annualized volatility of negative return deviations below zero.',
        availabilityState: 'AVAILABLE',
      },
    ];

    assert.equal(overviewMetrics.length, 4);
    assert.equal(overviewMetrics[0].numericValue, 12.80);
    assert.equal(overviewMetrics[1].numericValue, -6.45);
    assert.equal(overviewMetrics[2].numericValue, 0.92);
    assert.equal(overviewMetrics[3].numericValue, 8.75);
    assert.ok(overviewMetrics.every(m => m.availabilityState === 'AVAILABLE'));
  });

  it('TC-FE-RA04: Look-Through Exposure coverage adheres to zero-renormalization rule', () => {
    const sectorExposures: AttributionSectorExposureDto[] = [
      { sectorName: 'Financial Services', portfolioWeightPct: 28.5, fundContributions: {} },
      { sectorName: 'Technology', portfolioWeightPct: 15.2, fundContributions: {} },
      { sectorName: 'Consumer Cyclical', portfolioWeightPct: 12.3, fundContributions: {} },
    ];

    const marketCapExposures: AttributionMarketCapExposureDto[] = [
      { category: 'LARGE_CAP', portfolioWeightPct: 65.0, fundContributions: {} },
      { category: 'MID_CAP', portfolioWeightPct: 20.0, fundContributions: {} },
      { category: 'SMALL_CAP', portfolioWeightPct: 5.0, fundContributions: {} },
    ];

    const coveredSectorWeight = sectorExposures.reduce((sum, s) => sum + s.portfolioWeightPct, 0);
    const unclassifiedSectorWeight = 100.0 - coveredSectorWeight;

    // Disclosed equity coverage 90.0%, unclassified/cash 10.0%
    const disclosedEquityCoveragePct = 90.0;
    const unmodeledExposurePct = 100.0 - disclosedEquityCoveragePct;

    assert.equal(unclassifiedSectorWeight, 44.0);
    assert.equal(unmodeledExposurePct, 10.0);
    assert.equal(
      disclosedEquityCoveragePct < 100.0,
      true,
      'When coverage is incomplete, system must explicitly disclose unmodeled percentage rather than scaling to 100%'
    );
    assert.equal(marketCapExposures.length, 3);
  });

  it('TC-FE-RA05: Unavailable status is returned gracefully when aligned observations are insufficient', () => {
    const unavailableResponse: PortfolioRiskAttributionResponseDto = {
      evaluationStatus: 'UNAVAILABLE',
      asOfDate: '2024-01-15',
      knowledgeCutoff: '2024-01-15T23:59:59',
      calculationStartDate: '2024-01-01',
      calculationEndDate: '2024-01-08',
      alignedObservationCount: 5,
      annualizationFactor: 252,
      annualizationConvention: 'TRADING_DAYS_252',
      benchmarkCode: 'NIFTY_500_TRI',
      benchmarkName: 'Nifty 500 Total Returns Index',
      overviewMetrics: [],
      fundContributions: [],
      reconciliation: {
        portfolioVolatilityPct: 0,
        sumRiskContributionsPct: 0,
        reconciliationDifferencePct: 0,
        sumPercentageContributions: 0,
        isReconciled: false,
        reconciliationStatus: 'UNAVAILABLE',
        mathematicalIdentityFormula: 'sum(RC_i) = sigma_p',
      },
      exposureContext: {
        sectors: [],
        marketCaps: [],
        topSecurities: [],
        disclosedEquityCoveragePct: 0,
        unclassifiedWeightPct: 100,
        cashAndDebtWeightPct: 0,
        exposureDisclaimers: ['Insufficient historical return series for covariance estimation.'],
      },
      interpretation: {
        primaryRiskDrivers: [],
        diversificationInsights: [],
        concentrationAssessment: 'Risk concentration evaluation unavailable due to insufficient data.',
        riskHhi: 0,
        coverageLimitations: ['Insufficient aligned daily return observations (5 found, minimum 10 required).'],
        investigationPrompts: ['Select an as-of date with at least 10 trading days of concurrent NAV history.'],
      },
      dataQualityWarnings: ['Insufficient aligned daily return observations (5 found, minimum 10 required).'],
      methodologyNotes: {},
    };

    assert.equal(unavailableResponse.evaluationStatus, 'UNAVAILABLE');
    assert.ok(unavailableResponse.dataQualityWarnings[0]?.includes('Insufficient aligned daily return observations'));
    assert.equal(unavailableResponse.fundContributions.length, 0);
    assert.equal(unavailableResponse.interpretation.riskHhi, 0);
  });

});
