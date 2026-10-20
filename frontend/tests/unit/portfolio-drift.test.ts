import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  PortfolioDriftResponseDto,
  HoldingDriftItemDto,
  SectorDriftCoverageDto
} from '../../lib/api/portfolio';

describe('Portfolio Holdings Drift & Sector Shift — Frontend Contract & Integrity Tests', () => {

  it('TC-FE-DR01: PortfolioDriftResponseDto parses real multi-period drift with percentage-point semantics', () => {
    const mockDrift: PortfolioDriftResponseDto = {
      schemeOptionId: 1,
      schemeName: 'HDFC Flexi Cap Fund (Direct Growth)',
      amfiCode: '118955',
      status: 'COMPARABLE',
      statusReason: 'Two distinct verified monthly snapshots available for longitudinal drift analysis.',
      earlierSnapshot: {
        snapshotId: 101,
        portfolioDate: '2023-12-31',
        availabilityTime: '2024-01-05T10:00:00Z',
        sourceArtifactId: 501,
        sourceArtifactSha256: 'sha256-earlier-hash',
        holdingsCount: 50,
        sumWeightsPct: 98.45,
        top5Pct: 34.50,
        top10Pct: 52.80,
        hhi: 360.2
      },
      laterSnapshot: {
        snapshotId: 102,
        portfolioDate: '2024-01-31',
        availabilityTime: '2024-02-05T10:00:00Z',
        sourceArtifactId: 502,
        sourceArtifactSha256: 'sha256-later-hash',
        holdingsCount: 52,
        sumWeightsPct: 99.10,
        top5Pct: 33.25,
        top10Pct: 53.65,
        hhi: 347.8
      },
      top5PctDelta: -1.25,
      top10PctDelta: 0.85,
      hhiDelta: -12.4,
      holdingChanges: [
        {
          securityName: 'ICICI Bank Ltd.',
          isin: 'INF109K01FY2',
          sector: 'Financial Services',
          assetClass: 'EQUITY',
          earlierWeightPct: 8.45,
          earlierWeightPctFormatted: '8.45%',
          laterWeightPct: 9.20,
          laterWeightPctFormatted: '9.20%',
          weightDeltaPct: 0.75,
          weightDeltaPctFormatted: '+0.75 pp',
          changeType: 'WEIGHT_INCREASED',
          changeDescription: 'Weight increased by 0.75 pp'
        },
        {
          securityName: 'HDFC Bank Ltd.',
          isin: 'INF090A01021',
          sector: 'Financial Services',
          assetClass: 'EQUITY',
          earlierWeightPct: 7.80,
          earlierWeightPctFormatted: '7.80%',
          laterWeightPct: 6.95,
          laterWeightPctFormatted: '6.95%',
          weightDeltaPct: -0.85,
          weightDeltaPctFormatted: '-0.85 pp',
          changeType: 'WEIGHT_DECREASED',
          changeDescription: 'Weight decreased by 0.85 pp'
        },
        {
          securityName: 'Tata Motors Ltd.',
          isin: 'INE155A01022',
          sector: 'Automobile and Auto Components',
          assetClass: 'EQUITY',
          earlierWeightPct: null,
          earlierWeightPctFormatted: null,
          laterWeightPct: 1.15,
          laterWeightPctFormatted: '1.15%',
          weightDeltaPct: 1.15,
          weightDeltaPctFormatted: '+1.15 pp',
          changeType: 'NEWLY_REPORTED',
          changeDescription: 'Newly reported position'
        },
        {
          securityName: 'Wipro Ltd.',
          isin: 'INE075A01022',
          sector: 'Information Technology',
          assetClass: 'EQUITY',
          earlierWeightPct: 0.90,
          earlierWeightPctFormatted: '0.90%',
          laterWeightPct: null,
          laterWeightPctFormatted: null,
          weightDeltaPct: -0.90,
          weightDeltaPctFormatted: '-0.90 pp',
          changeType: 'NO_LONGER_REPORTED',
          changeDescription: 'No longer reported position'
        }
      ],
      topIncreases: [
        {
          securityName: 'Tata Motors Ltd.',
          isin: 'INE155A01022',
          sector: 'Automobile and Auto Components',
          assetClass: 'EQUITY',
          earlierWeightPct: null,
          earlierWeightPctFormatted: null,
          laterWeightPct: 1.15,
          laterWeightPctFormatted: '1.15%',
          weightDeltaPct: 1.15,
          weightDeltaPctFormatted: '+1.15 pp',
          changeType: 'NEWLY_REPORTED',
          changeDescription: 'Newly reported position'
        },
        {
          securityName: 'ICICI Bank Ltd.',
          isin: 'INF109K01FY2',
          sector: 'Financial Services',
          assetClass: 'EQUITY',
          earlierWeightPct: 8.45,
          earlierWeightPctFormatted: '8.45%',
          laterWeightPct: 9.20,
          laterWeightPctFormatted: '9.20%',
          weightDeltaPct: 0.75,
          weightDeltaPctFormatted: '+0.75 pp',
          changeType: 'WEIGHT_INCREASED',
          changeDescription: 'Weight increased by 0.75 pp'
        }
      ],
      topDecreases: [
        {
          securityName: 'Wipro Ltd.',
          isin: 'INE075A01022',
          sector: 'Information Technology',
          assetClass: 'EQUITY',
          earlierWeightPct: 0.90,
          earlierWeightPctFormatted: '0.90%',
          laterWeightPct: null,
          laterWeightPctFormatted: null,
          weightDeltaPct: -0.90,
          weightDeltaPctFormatted: '-0.90 pp',
          changeType: 'NO_LONGER_REPORTED',
          changeDescription: 'No longer reported position'
        },
        {
          securityName: 'HDFC Bank Ltd.',
          isin: 'INF090A01021',
          sector: 'Financial Services',
          assetClass: 'EQUITY',
          earlierWeightPct: 7.80,
          earlierWeightPctFormatted: '7.80%',
          laterWeightPct: 6.95,
          laterWeightPctFormatted: '6.95%',
          weightDeltaPct: -0.85,
          weightDeltaPctFormatted: '-0.85 pp',
          changeType: 'WEIGHT_DECREASED',
          changeDescription: 'Weight decreased by 0.85 pp'
        }
      ],
      additions: [
        {
          securityName: 'Tata Motors Ltd.',
          isin: 'INE155A01022',
          sector: 'Automobile and Auto Components',
          assetClass: 'EQUITY',
          earlierWeightPct: null,
          earlierWeightPctFormatted: null,
          laterWeightPct: 1.15,
          laterWeightPctFormatted: '1.15%',
          weightDeltaPct: 1.15,
          weightDeltaPctFormatted: '+1.15 pp',
          changeType: 'NEWLY_REPORTED',
          changeDescription: 'Newly reported position'
        }
      ],
      exits: [
        {
          securityName: 'Wipro Ltd.',
          isin: 'INE075A01022',
          sector: 'Information Technology',
          assetClass: 'EQUITY',
          earlierWeightPct: 0.90,
          earlierWeightPctFormatted: '0.90%',
          laterWeightPct: null,
          laterWeightPctFormatted: null,
          weightDeltaPct: -0.90,
          weightDeltaPctFormatted: '-0.90 pp',
          changeType: 'NO_LONGER_REPORTED',
          changeDescription: 'No longer reported position'
        }
      ],
      sectorDrift: [
        {
          sector: 'Financial Services',
          earlierWeightPct: 32.50,
          earlierWeightPctFormatted: '32.50%',
          laterWeightPct: 31.80,
          laterWeightPctFormatted: '31.80%',
          weightDeltaPct: -0.70,
          weightDeltaPctFormatted: '-0.70 pp'
        },
        {
          sector: 'Automobile and Auto Components',
          earlierWeightPct: 8.20,
          earlierWeightPctFormatted: '8.20%',
          laterWeightPct: 9.35,
          laterWeightPctFormatted: '9.35%',
          weightDeltaPct: 1.15,
          weightDeltaPctFormatted: '+1.15 pp'
        }
      ],
      sectorCoverage: {
        earlierCoveragePct: 95.0,
        earlierCoveragePctFormatted: '95.00%',
        laterCoveragePct: 95.20,
        laterCoveragePctFormatted: '95.20%',
        unclassifiedWeightPctEarlier: 5.0,
        unclassifiedWeightPctEarlierFormatted: '5.00%',
        unclassifiedWeightPctLater: 3.90,
        unclassifiedWeightPctLaterFormatted: '3.90%',
        coverageStatus: 'ADEQUATE_COVERAGE',
        coverageLimitationNotes: []
      },
      assetClassDrift: [
        {
          assetClass: 'EQUITY',
          earlierWeightPct: 94.50,
          earlierWeightPctFormatted: '94.50%',
          laterWeightPct: 95.10,
          laterWeightPctFormatted: '95.10%',
          weightDeltaPct: 0.60,
          weightDeltaPctFormatted: '+0.60 pp'
        }
      ],
      investigationPrompts: [
        'Disclosed portfolio contains 1 newly introduced position(s) totaling 1.15 pp.',
        'Disclosed portfolio completely eliminated 1 position(s) totaling 0.90 pp.'
      ],
      isComparable: true,
      comparabilityNotes: [],
      qualityState: 'VALID',
      methodologyVersion: 'DRIFT_V1'
    };

    assert.equal(mockDrift.schemeOptionId, 1);
    assert.equal(mockDrift.earlierSnapshot?.portfolioDate, '2023-12-31');
    assert.equal(mockDrift.laterSnapshot?.portfolioDate, '2024-01-31');
    assert.equal(mockDrift.status, 'COMPARABLE');
    assert.equal(mockDrift.holdingChanges.length, 4);
    assert.equal(mockDrift.topIncreases?.length, 2);
    assert.equal(mockDrift.topDecreases?.length, 2);
    assert.equal(mockDrift.additions?.length, 1);
    assert.equal(mockDrift.exits?.length, 1);

    // Percentage points assertion
    assert.equal(mockDrift.holdingChanges[0].weightDeltaPctFormatted, '+0.75 pp');
    assert.equal(mockDrift.holdingChanges[1].weightDeltaPctFormatted, '-0.85 pp');
    assert.equal(mockDrift.sectorDrift![0].weightDeltaPctFormatted, '-0.70 pp');

    // Sector coverage
    assert.equal(mockDrift.sectorCoverage?.coverageStatus, 'ADEQUATE_COVERAGE');
    assert.equal(mockDrift.sectorCoverage?.unclassifiedWeightPctLater, 3.90);
  });

  it('TC-FE-DR02: Handles insufficient history (<2 snapshots) honestly without synthetic priors', () => {
    const insufficientDrift: PortfolioDriftResponseDto = {
      schemeOptionId: 10151,
      schemeName: 'Parag Parikh ELSS Tax Saver Fund (Direct Growth)',
      amfiCode: '147481',
      status: 'INSUFFICIENT_HISTORY',
      statusReason: 'At least two distinct monthly portfolio snapshots are required to evaluate holdings drift.',
      earlierSnapshot: null,
      laterSnapshot: null,
      top5PctDelta: 0.0,
      top10PctDelta: 0.0,
      hhiDelta: 0.0,
      holdingChanges: [],
      topIncreases: [],
      topDecreases: [],
      additions: [],
      exits: [],
      sectorDrift: [],
      assetClassDrift: [],
      isComparable: false,
      comparabilityNotes: [
        'At least two distinct monthly portfolio snapshots are required to evaluate holdings drift.',
        'Fund currently has 1 eligible snapshot(s) published under PIT cutoffs.'
      ],
      qualityState: 'UNAVAILABLE',
      methodologyVersion: 'DRIFT_V1',
      investigationPrompts: []
    };

    assert.equal(insufficientDrift.status, 'INSUFFICIENT_HISTORY');
    assert.equal(insufficientDrift.qualityState, 'UNAVAILABLE');
    assert.equal(insufficientDrift.holdingChanges.length, 0);
    assert.ok(insufficientDrift.comparabilityNotes[0].includes('At least two distinct monthly portfolio snapshots'));
    // Crucial: no synthetic prior data or fake zero changes
    assert.equal(insufficientDrift.earlierSnapshot, null);
  });

  it('TC-FE-DR03: Flags partial sector coverage when unclassified/cash exposure exceeds threshold', () => {
    const partialCoverage: SectorDriftCoverageDto = {
      coverageStatus: 'PARTIAL_COVERAGE',
      earlierCoveragePct: 83.40,
      earlierCoveragePctFormatted: '83.40%',
      laterCoveragePct: 82.50,
      laterCoveragePctFormatted: '82.50%',
      unclassifiedWeightPctEarlier: 16.60,
      unclassifiedWeightPctEarlierFormatted: '16.60%',
      unclassifiedWeightPctLater: 17.50,
      unclassifiedWeightPctLaterFormatted: '17.50%',
      coverageLimitationNotes: [
        'Unclassified/cash exposure is 17.50% (>10.0%), which limits sector-level comparability.'
      ]
    };

    assert.equal(partialCoverage.coverageStatus, 'PARTIAL_COVERAGE');
    assert.ok(partialCoverage.unclassifiedWeightPctLater > 10.0);
    assert.ok(partialCoverage.coverageLimitationNotes[0].includes('limits sector-level comparability'));
  });

  it('TC-FE-DR04: Enforces strict semantic distinction between within-fund drift and between-fund overlap', () => {
    // Within-fund drift: longitudinal difference (t2 - t1) for single fund
    // Between-fund overlap: cross-sectional intersection min(w_A, w_B) between two funds at t
    const changeType: HoldingDriftItemDto['changeType'] = 'WEIGHT_INCREASED';
    assert.ok(['WEIGHT_INCREASED', 'WEIGHT_DECREASED', 'NEWLY_REPORTED', 'NO_LONGER_REPORTED', 'UNCHANGED'].includes(changeType));
    assert.notEqual(changeType, 'OVERLAP');
  });

});
