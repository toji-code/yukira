import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  FundConcentrationDto,
  PortfolioOverlapResponseDto,
  PortfolioSecurityAggregationDto,
  HoldingsCoverageReportDto
} from '../../lib/api/portfolio';

describe('Portfolio Intelligence & Holdings Analytics — Frontend Contract Tests', () => {

  it('TC-FE-PI01: FundConcentrationDto verifies concentration metrics and quality states', () => {
    const mockConcentration: FundConcentrationDto = {
      schemeOptionId: 1,
      fundName: 'HDFC Flexi Cap Fund (Direct Growth)',
      amfiCode: '118955',
      isin: 'INF179K01UT0',
      portfolioDate: '2024-01-31',
      positionCount: 10,
      totalDisclosedWeightPct: 54.82,
      formattedDisclosedWeight: '54.82%',
      top5DisclosedWeightPct: 34.35,
      formattedTop5Weight: '34.35%',
      top10DisclosedWeightPct: 54.82,
      formattedTop10Weight: '54.82%',
      hhiIndex: 342.65,
      formattedHhi: '342.65',
      concentrationClassification: 'DIVERSIFIED',
      topDisclosedHoldings: [
        {
          positionNumber: 1,
          securityName: 'ICICI Bank Ltd.',
          isin: 'INF109K01FY2',
          sector: 'Financial Services',
          assetClass: 'EQUITY',
          reportedWeightPct: 8.45,
          formattedWeight: '8.45%'
        },
        {
          positionNumber: 2,
          securityName: 'HDFC Bank Ltd.',
          isin: 'INF090A01021',
          sector: 'Financial Services',
          assetClass: 'EQUITY',
          reportedWeightPct: 7.82,
          formattedWeight: '7.82%'
        }
      ],
      qualityState: 'DATA_QUALITY_LIMITED',
      limitations: ['Disclosed weight sum is 54.82% (<90% coverage)']
    };

    assert.equal(mockConcentration.schemeOptionId, 1);
    assert.equal(mockConcentration.hhiIndex, 342.65);
    assert.equal(mockConcentration.concentrationClassification, 'DIVERSIFIED');
    assert.equal(mockConcentration.topDisclosedHoldings.length, 2);
    assert.equal(mockConcentration.qualityState, 'DATA_QUALITY_LIMITED');
    assert.ok(mockConcentration.limitations[0].includes('<90% coverage'));
  });

  it('TC-FE-PI02: PortfolioOverlapResponseDto enforces pairwise overlap calculation contract', () => {
    const mockOverlap: PortfolioOverlapResponseDto = {
      schemeOptionIdA: 1,
      fundNameA: 'HDFC Flexi Cap Fund',
      portfolioDateA: '2024-01-31',
      schemeOptionIdB: 2,
      fundNameB: 'Parag Parikh Flexi Cap Fund',
      portfolioDateB: '2024-01-31',
      overlapPercentage: 26.27,
      formattedOverlapPercentage: '26.27%',
      commonSecuritiesCount: 5,
      fundAPositionCount: 10,
      fundBPositionCount: 10,
      uniqueSecuritiesFundA: 5,
      uniqueSecuritiesFundB: 5,
      overlappingSecurities: [
        {
          securityName: 'HDFC Bank Ltd.',
          isin: 'INF090A01021',
          sector: 'Financial Services',
          fundAWeightPct: 7.82,
          fundBWeightPct: 6.50,
          overlapContributionPct: 6.50,
          formattedOverlapContribution: '6.50%'
        }
      ],
      qualityState: 'VALID',
      limitations: []
    };

    assert.equal(mockOverlap.overlapPercentage, 26.27);
    assert.equal(mockOverlap.commonSecuritiesCount, 5);
    assert.equal(mockOverlap.overlappingSecurities[0].overlapContributionPct, 6.50);
    assert.equal(mockOverlap.qualityState, 'VALID');
  });

  it('TC-FE-PI03: PortfolioSecurityAggregationDto enforces look-through multi-fund exposure', () => {
    const mockAggregation: PortfolioSecurityAggregationDto = {
      totalConstituentFunds: 3,
      valuedConstituentFunds: 3,
      totalPortfolioValue: 100000,
      disclosedCoveragePercentage: 51.15,
      uniqueSecuritiesCount: 18,
      topSecurityExposures: [
        {
          securityName: 'HDFC Bank Ltd.',
          isin: 'INF090A01021',
          sector: 'Financial Services',
          assetClass: 'EQUITY',
          lookThroughWeightPct: 8.72,
          formattedLookThroughWeight: '8.72%',
          estimatedMarketValue: 8720,
          holdingFundCount: 3,
          constituentFundExposures: [
            {
              schemeOptionId: 1,
              fundName: 'HDFC Flexi Cap Fund',
              fundWeightInPortfolioPct: 40.0,
              holdingWeightInFundPct: 7.82,
              effectiveLookThroughWeightPct: 3.128
            }
          ]
        }
      ],
      qualityState: 'DATA_QUALITY_LIMITED',
      limitations: ['Disclosed coverage percentage is 51.15% (<90%).']
    };

    assert.equal(mockAggregation.totalConstituentFunds, 3);
    assert.equal(mockAggregation.uniqueSecuritiesCount, 18);
    assert.equal(mockAggregation.topSecurityExposures[0].lookThroughWeightPct, 8.72);
    assert.equal(mockAggregation.topSecurityExposures[0].holdingFundCount, 3);
  });

  it('TC-FE-PI04: HoldingsCoverageReportDto verifies universe coverage audit stats', () => {
    const mockAudit: HoldingsCoverageReportDto = {
      auditAsOfDate: '2026-10-08',
      totalActiveUniverse: 9127,
      activeWithNav: 8483,
      active3YReady: 1771,
      activeWithHoldings: 3,
      activeWithLatestHoldings: 3,
      activeWithRecentHoldings: 3,
      activeWithValidHoldings: 3,
      activeWithSecurityIdentities: 3,
      staleHoldingsCount: 0,
      identityUnresolvedCount: 0,
      sourceNotConfiguredCount: 9124,
      sourceFailedCount: 0,
      sourceClassifications: [
        {
          schemeOptionId: 1,
          amfiCode: '118955',
          fundName: 'HDFC Flexi Cap Fund',
          sourceClassification: 'SOURCE_CONFIGURED',
          positionCount: 10,
          qualityState: 'VALID'
        }
      ]
    };

    assert.equal(mockAudit.totalActiveUniverse, 9127);
    assert.equal(mockAudit.activeWithHoldings, 3);
    assert.equal(mockAudit.sourceClassifications[0].sourceClassification, 'SOURCE_CONFIGURED');
  });

});
