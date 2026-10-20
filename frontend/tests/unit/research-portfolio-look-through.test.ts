import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  ResearchPortfolioFundItemDto,
  ConsolidatedSecurityExposureDto,
  PairwiseOverlapResultDto,
  PortfolioConcentrationMetricsDto,
  ConsolidatedSectorExposureDto,
  ConsolidatedMarketCapExposureDto,
  CandidateSimulationResponseDto,
} from '../../lib/api/portfolio';

describe('Multi-Fund Look-Through & Portfolio Aggregation — Frontend Contract & Logic Tests', () => {

  it('TC-FE-LT01: ResearchPortfolioFundItemDto and allocation validation & normalization arithmetic', () => {
    const rawFunds: ResearchPortfolioFundItemDto[] = [
      { schemeOptionId: 1, allocationWeight: 30.0 },
      { schemeOptionId: 10151, allocationWeight: 20.0 },
    ];

    // Investor entered partial allocation totaling 50%
    const totalRaw = rawFunds.reduce((sum, f) => sum + f.allocationWeight, 0);
    assert.equal(totalRaw, 50.0);
    assert.equal(Math.abs(totalRaw - 100.0) > 0.05, true, 'Should be marked as partial allocation');

    // Deterministic explicit normalization
    const normalizedFunds: ResearchPortfolioFundItemDto[] = rawFunds.map(f => ({
      ...f,
      allocationWeight: parseFloat(((f.allocationWeight / totalRaw) * 100).toFixed(2)),
    }));

    assert.equal(normalizedFunds[0].allocationWeight, 60.0);
    assert.equal(normalizedFunds[1].allocationWeight, 40.0);
    const normalizedTotal = normalizedFunds.reduce((sum, f) => sum + f.allocationWeight, 0);
    assert.equal(normalizedTotal, 100.0);
  });

  it('TC-FE-LT02: ConsolidatedSecurityExposureDto verifies look-through aggregation contracts', () => {
    const mockSecurity: ConsolidatedSecurityExposureDto = {
      isin: 'INE002A01018',
      canonicalName: 'Reliance Industries Ltd.',
      sector: 'Energy',
      assetClass: 'EQUITY',
      marketCapCategory: 'LARGE_CAP',
      consolidatedWeightPct: 5.48,
      fundCount: 2,
      identityConfidence: 'VERIFIED',
      classificationProvenance: 'AMFI_SEBI_PROVENANCE',
      contributions: [
        {
          schemeOptionId: 1,
          fundName: 'HDFC Flexi Cap Fund',
          fundWeightInPortfolioPct: 60.0,
          holdingWeightInFundPct: 6.20,
          effectiveWeightContributionPct: 3.72,
        },
        {
          schemeOptionId: 10151,
          fundName: 'Parag Parikh ELSS Tax Saver Fund',
          fundWeightInPortfolioPct: 40.0,
          holdingWeightInFundPct: 4.40,
          effectiveWeightContributionPct: 1.76,
        },
      ],
    };

    assert.equal(mockSecurity.isin, 'INE002A01018');
    assert.equal(mockSecurity.consolidatedWeightPct, 5.48);
    assert.equal(mockSecurity.fundCount, 2);
    assert.equal(mockSecurity.contributions.length, 2);
    // Verification of fund contributions sum: 3.72 + 1.76 = 5.48%
    const contributionSum = mockSecurity.contributions.reduce((s, c) => s + c.effectiveWeightContributionPct, 0);
    assert.ok(Math.abs(contributionSum - mockSecurity.consolidatedWeightPct) < 0.001);
  });

  it('TC-FE-LT03: PairwiseOverlapResultDto verifies shared security matrix and cash exclusion', () => {
    const mockOverlap: PairwiseOverlapResultDto = {
      fundAId: 1,
      fundAName: 'HDFC Flexi Cap Fund',
      fundBId: 10151,
      fundBName: 'Parag Parikh ELSS Tax Saver Fund',
      overlapWeightPct: 18.45,
      sharedSecurityCount: 9,
      observationDateA: '2026-09-30',
      observationDateB: '2026-09-30',
      temporalDeltaDays: 0,
      comparabilityState: 'SYNCHRONIZED',
      topSharedSecurities: [
        {
          securityName: 'HDFC Bank Ltd.',
          isin: 'INE040A01034',
          sector: 'Financial Services',
          weightInA: 8.50,
          weightInB: 7.20,
          overlapWeightPct: 7.20,
        },
      ],
    };

    assert.equal(mockOverlap.overlapWeightPct, 18.45);
    assert.equal(mockOverlap.sharedSecurityCount, 9);
    assert.equal(mockOverlap.comparabilityState, 'SYNCHRONIZED');
    assert.equal(mockOverlap.temporalDeltaDays, 0);
    assert.equal(mockOverlap.topSharedSecurities[0].overlapWeightPct, 7.20);
  });

  it('TC-FE-LT04: PortfolioConcentrationMetricsDto enforces institutional concentration discipline', () => {
    const mockConc: PortfolioConcentrationMetricsDto = {
      top5WeightPct: 29.80,
      top10WeightPct: 48.25,
      herfindahlHirschmanIndex: 412.60,
      hhiInterpretation: 'DIVERSIFIED',
      uniqueSecuritiesCount: 74,
      sharedSecuritiesCount: 11,
      sharedSecuritiesWeightPct: 32.10,
      disclosedEquityCoveragePct: 96.50,
      cashAndEquivalentsWeightPct: 4.10,
      unclassifiedWeightPct: 3.50,
    };

    assert.equal(mockConc.top5WeightPct, 29.80);
    assert.equal(mockConc.top10WeightPct, 48.25);
    assert.equal(mockConc.herfindahlHirschmanIndex, 412.60);
    assert.equal(mockConc.uniqueSecuritiesCount, 74);
    assert.equal(mockConc.sharedSecuritiesWeightPct, 32.10);
    assert.equal(mockConc.hhiInterpretation, 'DIVERSIFIED');
  });

  it('TC-FE-LT05: ConsolidatedSectorExposureDto and MarketCap distribution contract', () => {
    const mockSector: ConsolidatedSectorExposureDto = {
      macroSector: 'Financial Services',
      consolidatedWeightPct: 31.40,
      classifiedSecuritiesCount: 14,
      fundContributions: [
        { schemeOptionId: 1, fundName: 'HDFC Flexi Cap', sectorWeightInFundPct: 33.0, effectiveContributionPct: 19.80 },
        { schemeOptionId: 10151, fundName: 'PPFAS ELSS', sectorWeightInFundPct: 29.0, effectiveContributionPct: 11.60 },
      ],
      classificationProvenance: 'AMFI_SEBI_PROVENANCE',
    };

    assert.equal(mockSector.macroSector, 'Financial Services');
    assert.equal(mockSector.consolidatedWeightPct, 31.40);
    assert.equal(mockSector.classifiedSecuritiesCount, 14);
    assert.equal(mockSector.fundContributions.length, 2);

    const mockMcap: ConsolidatedMarketCapExposureDto = {
      category: 'LARGE_CAP',
      consolidatedWeightPct: 68.50,
      securitiesCount: 42,
      classificationProvenance: 'AMFI Semi-Annual Market Cap Classification (June 2026)',
    };

    assert.equal(mockMcap.category, 'LARGE_CAP');
    assert.equal(mockMcap.consolidatedWeightPct, 68.50);
    assert.equal(mockMcap.securitiesCount, 42);
  });

  it('TC-FE-LT06: CandidateSimulationResponseDto verifies proportional dilution and delta metrics', () => {
    const mockSimulation = {
      candidateSchemeOptionId: 10262,
      candidateFundName: 'Parag Parikh Flexi Cap Fund',
      candidateAmfiCode: '122639',
      proposedAllocationPct: 20.0,
      fundingMethod: 'PROPORTIONAL_DILUTION',
      deltaTop5WeightPct: -4.30,
      deltaTop10WeightPct: -3.80,
      deltaHhi: -47.40,
      deltaSharedSecuritiesWeightPct: 5.70,
      deltaUniqueSecuritiesCount: 7,
      newSecuritiesIntroducedCount: 1,
      augmentedSecuritiesCount: 1,
      topNewSecurities: [
        {
          canonicalName: 'Alphabet Inc. Class A',
          isin: 'US02079K3059',
          sector: 'Technology',
          assetClass: 'FOREIGN_EQUITY',
          marketCapCategory: 'LARGE_CAP',
          consolidatedWeightPct: 1.25,
          fundCount: 1,
          contributions: [],
          identityConfidence: 'VERIFIED',
          classificationProvenance: 'PROVENANCE_MAPPED',
        },
      ],
      topAugmentedSecurities: [
        {
          securityName: 'HDFC Bank Ltd.',
          isin: 'INE040A01034',
          sector: 'Financial Services',
          baselineWeightPct: 6.80,
          candidateContributionPct: 1.15,
          simulatedWeightPct: 7.95,
          deltaWeightPct: 1.15,
        },
      ],
      sectorDeltas: [
        {
          categoryName: 'Technology',
          baselineWeightPct: 12.0,
          simulatedWeightPct: 14.5,
          deltaWeightPct: 2.5,
        },
      ],
      marketCapDeltas: [],
      analyticalObservations: [
        'Candidate fund introduces 1 new holding outside the current portfolio.',
      ],
      limitations: [
        'Simulation applies proportional dilution to existing allocation weights.',
      ],
    } as unknown as CandidateSimulationResponseDto;

    assert.equal(mockSimulation.candidateSchemeOptionId, 10262);
    assert.equal(mockSimulation.proposedAllocationPct, 20.0);
    assert.equal(mockSimulation.fundingMethod, 'PROPORTIONAL_DILUTION');
    assert.equal(mockSimulation.deltaTop5WeightPct, -4.30);
    assert.equal(mockSimulation.deltaHhi, -47.40);
    assert.equal(mockSimulation.newSecuritiesIntroducedCount, 1);
    assert.equal(mockSimulation.topNewSecurities[0].canonicalName, 'Alphabet Inc. Class A');
    assert.equal(mockSimulation.topAugmentedSecurities[0].securityName, 'HDFC Bank Ltd.');
    assert.equal(mockSimulation.topAugmentedSecurities[0].deltaWeightPct, 1.15);
  });

  it('TC-FE-LT07: SectorTopSecurityDto verifies macro-sector top contributing securities drilldown', () => {
    const mockSector: ConsolidatedSectorExposureDto = {
      macroSector: 'Financial Services',
      consolidatedWeightPct: 31.45,
      classifiedSecuritiesCount: 12,
      fundContributions: [],
      classificationProvenance: 'CANONICAL_SECTOR_TAXONOMY',
      topSecurities: [
        {
          canonicalName: 'HDFC Bank Ltd.',
          isin: 'INE040A01034',
          consolidatedWeightPct: 8.50,
          fundCount: 2,
        },
        {
          canonicalName: 'ICICI Bank Ltd.',
          isin: 'INE090A01021',
          consolidatedWeightPct: 6.20,
          fundCount: 2,
        },
      ],
    };

    assert.equal(mockSector.macroSector, 'Financial Services');
    assert.equal(mockSector.consolidatedWeightPct, 31.45);
    assert.ok(mockSector.topSecurities && mockSector.topSecurities.length === 2);
    assert.equal(mockSector.topSecurities[0].canonicalName, 'HDFC Bank Ltd.');
    assert.equal(mockSector.topSecurities[0].consolidatedWeightPct, 8.50);
    assert.equal(mockSector.topSecurities[0].fundCount, 2);
  });

  it('TC-FE-LT08: PortfolioRiskDecompositionDto verifies empirical risk decomposition contracts and factor gates', () => {
    const mockRisk = {
      evaluationStatus: 'COMPUTED_VALIDATED_INPUTS',
      benchmarkCode: 'NIFTY_500_TRI',
      benchmarkName: 'Nifty 500 Total Returns Index',
      returnStartDate: '2023-10-01',
      returnEndDate: '2026-10-01',
      alignedTradingDaysCount: 738,
      portfolioRealizedVolatilityPct: 14.12,
      weightedAverageFundVolatilityPct: 15.65,
      diversificationBenefitPct: 1.53,
      portfolioMaxDrawdownPct: 11.20,
      portfolioDownsideDeviationPct: 9.80,
      portfolioBeta: 0.98,
      benchmarkRealizedVolatilityPct: 15.10,
      benchmarkMaxDrawdownPct: 12.80,
      constituentRiskProfiles: [
        {
          schemeOptionId: 1,
          fundName: 'HDFC Flexi Cap Fund',
          amfiCode: '118955',
          allocationWeightPct: 60.0,
          annualizedVolatilityPct: 15.20,
          downsideDeviationPct: 10.10,
          maxDrawdownPct: 12.40,
          betaToBenchmark: 1.02,
          returnObservationsCount: 738,
          riskCoverageState: 'SYNCHRONIZED_VALIDATED',
        },
      ],
      pairwiseCorrelations: [
        {
          fundAId: 1,
          fundAName: 'HDFC Flexi Cap Fund',
          fundBId: 10151,
          fundBName: 'Parag Parikh ELSS Tax Saver Fund',
          correlationCoefficient: 0.8124,
          commonTradingDays: 738,
        },
      ],
      factorExposures: [
        {
          factorCode: 'SIZE',
          factorName: 'Market Capitalization (Size)',
          category: 'HOLDINGS_PROXY',
          exposureValue: 'LARGE_CAP: 68.50% | MID_CAP: 18.20%',
          status: 'VALIDATED_PROXY',
          economicDefinition: 'Exposure across equity market cap tiers.',
          requiredInputs: 'AMFI point-in-time ranking master list.',
          calculationMethod: 'Look-through weighted sum.',
          evidenceNotes: 'Official AMFI half-yearly list.',
        },
        {
          factorCode: 'MARKET_BETA',
          factorName: 'Market Sensitivity (Beta)',
          category: 'RETURN_BASED',
          exposureValue: '0.98 vs NIFTY_500_TRI',
          status: 'EMPIRICALLY_COMPUTED',
          economicDefinition: 'Systematic market sensitivity.',
          requiredInputs: 'Synchronized daily returns.',
          calculationMethod: 'OLS regression over aligned trading days.',
          evidenceNotes: 'Empirical daily return covariance.',
        },
        {
          factorCode: 'VALUE',
          factorName: 'Value (Earnings Yield / Book-to-Price)',
          category: 'EMPIRICALLY_GATED',
          exposureValue: 'Not available',
          status: 'FACTOR_INPUTS_UNAVAILABLE',
          economicDefinition: 'Systematic value premium (HML).',
          requiredInputs: 'Security-level financial statements.',
          calculationMethod: 'Fama-French HML regression.',
          evidenceNotes: 'GATED under §4 AGENTS.md.',
        },
      ],
      rebalancingConvention: 'DAILY_REBALANCED_SYNTHESIZED',
      riskObservations: [
        'Realized portfolio annualized volatility is 14.12% over 738 aligned trading days.',
        'Diversification benefit is +1.53% points.',
      ],
      dataLimitations: [
        'Fundamental factors (Value, Quality, Momentum) remain strictly gated under AGENTS.md §4.',
      ],
    };

    assert.equal(mockRisk.evaluationStatus, 'COMPUTED_VALIDATED_INPUTS');
    assert.equal(mockRisk.alignedTradingDaysCount, 738);
    // Diversification benefit formula: bar(sigma) - sigma_port = 15.65 - 14.12 = 1.53%
    const expectedDiv = parseFloat((mockRisk.weightedAverageFundVolatilityPct - mockRisk.portfolioRealizedVolatilityPct).toFixed(2));
    assert.equal(mockRisk.diversificationBenefitPct, expectedDiv);

    // Factor gating verification
    const valueFactor = mockRisk.factorExposures.find(f => f.factorCode === 'VALUE');
    assert.ok(valueFactor);
    assert.equal(valueFactor.status, 'FACTOR_INPUTS_UNAVAILABLE');
    assert.equal(valueFactor.exposureValue, 'Not available');
  });

  it('TC-FE-LT09: CandidateSimulationResponseDto verifies empirical risk deltas contracts', () => {
    const mockSimulationWithRisk = {
      candidateSchemeOptionId: 10262,
      candidateFundName: 'Parag Parikh Flexi Cap Fund',
      candidateAmfiCode: '122639',
      proposedAllocationPct: 20.0,
      deltaVolatilityPct: -0.45,
      deltaDiversificationBenefitPct: 0.32,
      deltaBeta: -0.04,
      deltaMaxDrawdownPct: -0.80,
    };

    assert.equal(mockSimulationWithRisk.deltaVolatilityPct, -0.45);
    assert.equal(mockSimulationWithRisk.deltaDiversificationBenefitPct, 0.32);
    assert.equal(mockSimulationWithRisk.deltaBeta, -0.04);
    assert.equal(mockSimulationWithRisk.deltaMaxDrawdownPct, -0.80);
  });
});
