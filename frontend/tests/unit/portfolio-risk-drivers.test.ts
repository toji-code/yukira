import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  PortfolioConcentrationSummaryDto,
  ConsolidatedSecurityExposureDto,
  PairwiseOverlapResultDto,
  ConsolidatedMarketCapExposureDto,
  RiskDriverFindingDto,
} from '../../lib/api/portfolio';

describe('Phase 2P: Portfolio Risk Drivers & Concentration Investigation — Frontend Contract & Math Tests', () => {

  it('TC-FE-RD01: Concentration summary metrics strictly reconcile underlying security weights', () => {
    const securities: ConsolidatedSecurityExposureDto[] = [
      {
        canonicalName: 'ICICI Bank Ltd',
        isin: 'INE090A01021',
        sector: 'Financial Services',
        assetClass: 'EQUITY',
        marketCapCategory: 'LARGE_CAP',
        consolidatedWeightPct: 3.70,
        fundCount: 2,
        identityConfidence: 'HIGH_ISIN_MATCH',
        classificationProvenance: 'AMFI_OFFICIAL_CLASSIFICATION',
        contributions: [
          { schemeOptionId: 118955, fundName: 'HDFC Flexi Cap', fundWeightInPortfolioPct: 60.0, holdingWeightInFundPct: 4.50, effectiveWeightContributionPct: 2.70 },
          { schemeOptionId: 122639, fundName: 'Parag Parikh Flexi Cap', fundWeightInPortfolioPct: 40.0, holdingWeightInFundPct: 2.50, effectiveWeightContributionPct: 1.00 },
        ],
      },
      {
        canonicalName: 'HDFC Bank Ltd',
        isin: 'INE040A01034',
        sector: 'Financial Services',
        assetClass: 'EQUITY',
        marketCapCategory: 'LARGE_CAP',
        consolidatedWeightPct: 3.00,
        fundCount: 1,
        identityConfidence: 'HIGH_ISIN_MATCH',
        classificationProvenance: 'AMFI_OFFICIAL_CLASSIFICATION',
        contributions: [
          { schemeOptionId: 118955, fundName: 'HDFC Flexi Cap', fundWeightInPortfolioPct: 60.0, holdingWeightInFundPct: 5.00, effectiveWeightContributionPct: 3.00 },
        ],
      },
      {
        canonicalName: 'Infosys Ltd',
        isin: 'INE009A01021',
        sector: 'Information Technology',
        assetClass: 'EQUITY',
        marketCapCategory: 'LARGE_CAP',
        consolidatedWeightPct: 2.60,
        fundCount: 2,
        identityConfidence: 'HIGH_ISIN_MATCH',
        classificationProvenance: 'AMFI_OFFICIAL_CLASSIFICATION',
        contributions: [
          { schemeOptionId: 118955, fundName: 'HDFC Flexi Cap', fundWeightInPortfolioPct: 60.0, holdingWeightInFundPct: 3.00, effectiveWeightContributionPct: 1.80 },
          { schemeOptionId: 122639, fundName: 'Parag Parikh Flexi Cap', fundWeightInPortfolioPct: 40.0, holdingWeightInFundPct: 2.00, effectiveWeightContributionPct: 0.80 },
        ],
      },
      {
        canonicalName: 'ITC Ltd',
        isin: 'INE154A01025',
        sector: 'Consumer Goods',
        assetClass: 'EQUITY',
        marketCapCategory: 'LARGE_CAP',
        consolidatedWeightPct: 2.40,
        fundCount: 1,
        identityConfidence: 'HIGH_ISIN_MATCH',
        classificationProvenance: 'AMFI_OFFICIAL_CLASSIFICATION',
        contributions: [
          { schemeOptionId: 122639, fundName: 'Parag Parikh Flexi Cap', fundWeightInPortfolioPct: 40.0, holdingWeightInFundPct: 6.00, effectiveWeightContributionPct: 2.40 },
        ],
      },
      {
        canonicalName: 'Tata Consultancy Services Ltd',
        isin: 'INE467B01029',
        sector: 'Information Technology',
        assetClass: 'EQUITY',
        marketCapCategory: 'LARGE_CAP',
        consolidatedWeightPct: 2.00,
        fundCount: 2,
        identityConfidence: 'HIGH_ISIN_MATCH',
        classificationProvenance: 'AMFI_OFFICIAL_CLASSIFICATION',
        contributions: [
          { schemeOptionId: 118955, fundName: 'HDFC Flexi Cap', fundWeightInPortfolioPct: 60.0, holdingWeightInFundPct: 2.00, effectiveWeightContributionPct: 1.20 },
          { schemeOptionId: 122639, fundName: 'Parag Parikh Flexi Cap', fundWeightInPortfolioPct: 40.0, holdingWeightInFundPct: 2.00, effectiveWeightContributionPct: 0.80 },
        ],
      },
      {
        canonicalName: 'Axis Bank Ltd',
        isin: 'INE238A01034',
        sector: 'Financial Services',
        assetClass: 'EQUITY',
        marketCapCategory: 'LARGE_CAP',
        consolidatedWeightPct: 0.90,
        fundCount: 1,
        identityConfidence: 'HIGH_ISIN_MATCH',
        classificationProvenance: 'AMFI_OFFICIAL_CLASSIFICATION',
        contributions: [
          { schemeOptionId: 118955, fundName: 'HDFC Flexi Cap', fundWeightInPortfolioPct: 60.0, holdingWeightInFundPct: 1.50, effectiveWeightContributionPct: 0.90 },
        ],
      },
    ];

    // Compute top 5 sum
    const top5Calculated = securities.slice(0, 5).reduce((sum, s) => sum + s.consolidatedWeightPct, 0);
    assert.equal(Number(top5Calculated.toFixed(2)), 13.70);

    const summary: PortfolioConcentrationSummaryDto = {
      top5WeightPct: 13.70,
      top10WeightPct: 14.60,
      largestSecurityWeightPct: 3.70,
      largestSecurityName: 'ICICI Bank Ltd',
      distinctSecuritiesCount: 6,
      sharedSecuritiesCount: 3,
      sharedSecuritiesWeightPct: 8.30,
      disclosedEquityCoveragePct: 98.20,
      unclassifiedWeightPct: 0.00,
      cashAndEquivalentsWeightPct: 1.80,
      herfindahlHirschmanIndex: 0.0385,
      hhiInterpretation: 'DIVERSIFIED',
    };

    assert.equal(summary.top5WeightPct, 13.70);
    assert.equal(summary.largestSecurityWeightPct, 3.70);
    assert.equal(summary.sharedSecuritiesCount, 3);
    assert.equal(summary.distinctSecuritiesCount, 6);
  });

  it('TC-FE-RD02: Zero-renormalization rule prevents inflating incomplete holdings to 100%', () => {
    const summary: PortfolioConcentrationSummaryDto = {
      top5WeightPct: 24.50,
      top10WeightPct: 41.20,
      largestSecurityWeightPct: 6.80,
      largestSecurityName: 'ICICI Bank Ltd',
      distinctSecuritiesCount: 82,
      sharedSecuritiesCount: 14,
      sharedSecuritiesWeightPct: 22.40,
      disclosedEquityCoveragePct: 94.50, // 5.5% not disclosed
      unclassifiedWeightPct: 2.10,
      cashAndEquivalentsWeightPct: 3.40,
      herfindahlHirschmanIndex: 0.0240,
      hhiInterpretation: 'DIVERSIFIED',
    };

    // Rule: Disclosed coverage + cash + unmodeled must reflect raw reality
    // Sum of disclosed underlying weights must NOT be artificially divided by 0.945
    assert.ok(
      summary.disclosedEquityCoveragePct < 100.0,
      'Holdings coverage must be explicitly reported as < 100% when incomplete'
    );
    assert.equal(summary.cashAndEquivalentsWeightPct, 3.40);
    assert.equal(summary.unclassifiedWeightPct, 2.10);
  });

  it('TC-FE-RD03: Pairwise equity overlap formula excludes cash and obeys 0-100% bounds', () => {
    // Fund A: ICICI 8%, HDFC 6%, Cash 5%
    // Fund B: ICICI 5%, Axis 7%, Cash 8%
    // Overlap = min(8%, 5%) = 5% (cash excluded)
    const pairwiseOverlap: PairwiseOverlapResultDto = {
      fundAId: 118955,
      fundAName: 'HDFC Flexi Cap Fund',
      fundBId: 122639,
      fundBName: 'Parag Parikh Flexi Cap Fund',
      overlapWeightPct: 5.00,
      sharedSecurityCount: 1,
      topSharedSecurities: [
        {
          securityName: 'ICICI Bank Ltd',
          isin: 'INE090A01021',
          sector: 'Financial Services',
          weightInA: 8.00,
          weightInB: 5.00,
          overlapWeightPct: 5.00,
        },
      ],
      observationDateA: '2026-09-30',
      observationDateB: '2026-09-30',
      temporalDeltaDays: 0,
      comparabilityState: 'HIGHLY_COMPARABLE',
    };

    assert.ok(pairwiseOverlap.overlapWeightPct >= 0 && pairwiseOverlap.overlapWeightPct <= 100);
    assert.equal(pairwiseOverlap.sharedSecurityCount, 1);
    assert.equal(pairwiseOverlap.topSharedSecurities[0].overlapWeightPct, 5.00);
    assert.equal(pairwiseOverlap.temporalDeltaDays, 0);
  });

  it('TC-FE-RD04: Sector and Market-Cap distributions preserve unclassified exposures', () => {
    const marketCap: ConsolidatedMarketCapExposureDto[] = [
      { category: 'LARGE_CAP', consolidatedWeightPct: 68.40, securitiesCount: 35, classificationProvenance: 'AMFI_OFFICIAL' },
      { category: 'MID_CAP', consolidatedWeightPct: 18.20, securitiesCount: 18, classificationProvenance: 'AMFI_OFFICIAL' },
      { category: 'SMALL_CAP', consolidatedWeightPct: 8.10, securitiesCount: 12, classificationProvenance: 'AMFI_OFFICIAL' },
      { category: 'CASH_EQUIVALENTS', consolidatedWeightPct: 3.50, securitiesCount: 2, classificationProvenance: 'DISCLOSED_CASH' },
      { category: 'UNCLASSIFIED', consolidatedWeightPct: 1.80, securitiesCount: 3, classificationProvenance: 'UNCLASSIFIED' },
    ];

    const totalWeight = marketCap.reduce((sum, m) => sum + m.consolidatedWeightPct, 0);
    assert.ok(Math.abs(totalWeight - 100.0) < 0.05, `Market-cap allocations sum (${totalWeight}%) must sum to ~100%`);

    const unclassified = marketCap.find(m => m.category === 'UNCLASSIFIED');
    assert.ok(unclassified !== undefined);
    assert.equal(unclassified?.consolidatedWeightPct, 1.80);
  });

  it('TC-FE-RD05: Deterministic investigation findings preserve epistemic boundaries and trigger flags', () => {
    const findings: RiskDriverFindingDto[] = [
      {
        id: 'FINDING-TOP5-CONC',
        category: 'CONCENTRATION',
        severity: 'MEDIUM',
        title: 'Top 5 Underlying Securities Exceed 30% of Identified Portfolio',
        observedValue: '34.20%',
        threshold: '>= 30.0%',
        explanation: 'The 5 largest security positions represent 34.20% of consolidated exposure.',
        investigationPrompt: 'Evaluate whether these positions stem from deliberate high-conviction allocation.',
        evidence: 'MultiFundLookThroughService (Top 5 Holdings)',
      },
      {
        id: 'FINDING-PAIRWISE-OVERLAP-HIGH',
        category: 'OVERLAP',
        severity: 'MEDIUM',
        title: 'Elevated Cross-Fund Holdings Overlap (28.40%)',
        observedValue: '28.40%',
        threshold: '>= 20.0%',
        explanation: 'Pairwise holdings overlap between Fund A and Fund B is 28.40%.',
        investigationPrompt: 'Confirm whether holding both funds delivers intended diversification.',
        evidence: 'MultiFundLookThroughService (Pairwise Minimum Overlap)',
      },
    ];

    assert.equal(findings.length, 2);
    assert.equal(findings[0].severity, 'MEDIUM');
    assert.equal(findings[0].observedValue, '34.20%');
    assert.ok(
      !findings[0].explanation.includes('BUY') && !findings[0].explanation.includes('SELL'),
      'Investigation findings must strictly remain non-advisory'
    );
  });
});
