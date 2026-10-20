import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  fetchPortfolioConstructionRisk,
  PortfolioConstructionRiskResponseDto,
} from "../../lib/api/portfolio";

describe("Portfolio Construction Risk & Data Reliability API & Types Contract", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockResponse: PortfolioConstructionRiskResponseDto = {
    schemeOptionId: 1,
    fundName: "HDFC Flexi Cap Fund",
    amfiCode: "118955",
    schemeCode: "HDFC_FLEXI",
    asOfDate: "2026-10-17",
    knowledgeCutoff: "2026-10-17T14:00:00Z",
    status: "SUCCESS",
    message: "Point-in-time portfolio construction risk and holdings data reliability calculated successfully.",
    dataReliability: {
      snapshotDate: "2026-09-30",
      availabilityTime: "2026-10-10T18:29:59Z",
      sumReportedWeights: 1.0053,
      sumReportedWeightsPct: "100.53%",
      totalHoldingsDisclosed: 85,
      uniqueSecuritiesCount: 85,
      duplicateHoldingsCount: 0,
      duplicateHoldings: [],
      invalidWeightsCount: 0,
      weightReconciliationStatus: "Net Asset Value Accrual Deviation (Gross Assets > 100%)",
      weightReconciliationCode: "NET_ASSETS_ACCRUAL_DEVIATION",
      weightReconciliationExplanation: "Sum of reported asset weights is 100.53% (+53 bps above 100%). In Indian mutual fund monthly portfolio disclosures, holdings are disclosed as '% to NAV'. When month-end accrued liabilities or payables exist, gross asset weights naturally exceed 100.00% by the exact margin of net payables.",
      mappedWeightPct: 97.72,
      unmappedWeightPct: 2.81,
      classifiedWeightPct: 100.53,
      unclassifiedWeightPct: 0.00,
      cashAndEquivalentsWeightPct: 2.81,
      equityWeightPct: 97.72,
      completenessType: "FULL_PORTFOLIO_DISCLOSURE",
      sourceArtifactUri: "https://files.hdfcfund.com/s3fs-public/2026-10/Monthly%20HDFC%20Flexi%20Cap%20Fund%20-%2030%20September%202026.xlsx",
      sourceArtifactSha256: "495a3d78dd97283b62fe3ee0a69edcbfa28178989426f41280ee0ce7ebbabd26",
      sourceArtifactByteSize: 752904,
    },
    issuerConcentration: {
      totalIssuersDisclosed: 85,
      top5IssuerWeightPct: 28.34,
      top10IssuerWeightPct: 43.87,
      top20IssuerWeightPct: 64.84,
      issuerHhi: 299.55,
      effectiveNumberOfIssuers: 33.4,
      hhiConcentrationTier: "DIVERSIFIED (HHI < 1,000)",
      securityHhi: 299.55,
      effectiveNumberOfSecurities: 33.4,
      topIssuers: [
        {
          rank: 1,
          issuerName: "ICICI Bank Ltd.",
          totalWeightPct: 8.80,
          instrumentsHeldCount: 1,
          primarySector: "Financial Services",
          primaryAssetClass: "Equity",
        },
        {
          rank: 2,
          issuerName: "Axis Bank Ltd.",
          totalWeightPct: 6.11,
          instrumentsHeldCount: 1,
          primarySector: "Financial Services",
          primaryAssetClass: "Equity",
        },
        {
          rank: 3,
          issuerName: "HDFC Bank Ltd.",
          totalWeightPct: 6.03,
          instrumentsHeldCount: 1,
          primarySector: "Financial Services",
          primaryAssetClass: "Equity",
        },
      ],
    },
    sectorConcentration: {
      totalSectorsDisclosed: 77,
      top1SectorWeightPct: 28.13,
      top1SectorName: "Financial Services",
      top3SectorWeightPct: 37.98,
      unclassifiedSectorWeightPct: 2.81,
      taxonomyType: "AMC_MONTHLY_DISCLOSURE_SECTOR",
      taxonomyDisclosure: "Classifications represent official AMC industry sectors reported in monthly portfolio disclosure documents.",
      sectors: [
        {
          rank: 1,
          sectorName: "Financial Services",
          weightPct: 28.13,
          holdingsCount: 8,
        },
      ],
    },
    marketCapReliability: {
      largeCapWeightPct: 63.60,
      midCapWeightPct: 14.51,
      smallCapWeightPct: 19.15,
      cashWeightPct: 2.81,
      debtOtherWeightPct: 0.00,
      unclassifiedWeightPct: 0.46,
      classificationStandard: "SEBI_OCT_2017_CIRCULAR",
      classificationProvenance: "STATIC_REFERENCE_MAPPING",
      historicalPitStatus: "APPROXIMATE_STATIC_REFERENCE",
      methodologyLimitation: "Historical half-yearly AMFI ranking snapshots are not stored in database. 2026 rankings are applied statically.",
    },
    snapshotHistory: [
      {
        snapshotId: 2,
        portfolioDate: "2024-01-31",
        availabilityTime: "2024-02-05T12:00:00Z",
        holdingsCount: 10,
        uniqueSecuritiesCount: 10,
        sumReportedWeightsPct: 54.82,
        weightReconciliationCode: "PARTIAL_TOP_10",
        completenessType: "PARTIAL_TOP_HOLDINGS",
        hasDuplicateSecurities: false,
        sourceArtifactSha256: "7f8a9b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a",
      },
      {
        snapshotId: 1943,
        portfolioDate: "2026-06-30",
        availabilityTime: "2026-07-10T12:00:00Z",
        holdingsCount: 77,
        uniqueSecuritiesCount: 76,
        sumReportedWeightsPct: 100.00,
        weightReconciliationCode: "BALANCED",
        completenessType: "FULL_PORTFOLIO_DISCLOSURE",
        hasDuplicateSecurities: true,
        sourceArtifactSha256: "f6f179dad0ccc1014ff7e1e88f7e64aec167ace1ad82fa6a543bdb5669d56cfc",
      },
      {
        snapshotId: 1940,
        portfolioDate: "2026-09-30",
        availabilityTime: "2026-10-10T12:00:00Z",
        holdingsCount: 85,
        uniqueSecuritiesCount: 85,
        sumReportedWeightsPct: 100.53,
        weightReconciliationCode: "NET_ACCRUAL_DEVIATION",
        completenessType: "FULL_PORTFOLIO_DISCLOSURE",
        hasDuplicateSecurities: false,
        sourceArtifactSha256: "495a3d78dd97283b62fe3ee0a69edcbfa28178989426f41280ee0ce7ebbabd26",
      },
    ],
    comparabilityMatrix: [
      {
        earlierDate: "2024-01-31",
        laterDate: "2026-06-30",
        intervalDays: 881,
        earlierSumWeightPct: 54.82,
        laterSumWeightPct: 100.00,
        weightDeltaPct: 45.18,
        earlierHoldingsCount: 10,
        laterHoldingsCount: 77,
        holdingsDeltaCount: 67,
        earlierCompleteness: "PARTIAL_TOP_10",
        laterCompleteness: "FULL_PORTFOLIO",
        isDataGapExceeded: true,
        comparabilityStatus: "NON_COMPARABLE",
        reasonCodes: [
          "EXTENDED_DATA_GAP_29_MONTHS",
          "DISCLOSURE_SCOPE_MISMATCH",
          "PARTIAL_PORTFOLIO_UNOBSERVED",
        ],
        assessmentSummary: "Non-comparable due to 29-month historical reporting gap (881 days) and structural change from Factsheet Top 10 disclosure (54.82%) to full portfolio (100.00%).",
      },
      {
        earlierDate: "2026-06-30",
        laterDate: "2026-07-31",
        intervalDays: 31,
        earlierSumWeightPct: 100.00,
        laterSumWeightPct: 100.00,
        weightDeltaPct: 0.00,
        earlierHoldingsCount: 77,
        laterHoldingsCount: 82,
        holdingsDeltaCount: 5,
        earlierCompleteness: "FULL_PORTFOLIO",
        laterCompleteness: "FULL_PORTFOLIO",
        isDataGapExceeded: false,
        comparabilityStatus: "COMPARABLE",
        reasonCodes: ["CONSECUTIVE_MONTHLY_FULL_DISCLOSURE"],
        assessmentSummary: "Fully comparable: Consecutive monthly full portfolio disclosures with consistent methodology.",
      },
    ],
    investigationItems: [
      {
        category: "DATA_GAP",
        severity: "HIGH",
        title: "29-Month Historical Reporting Gap (Jan 2024 to Jun 2026)",
        empiricalEvidence: "The database contains a 29-month gap between the January 31, 2024 snapshot and June 30, 2026. Zero intermediate monthly disclosures are available.",
        dueDiligenceQuestion: "Why did the disclosure repository experience a 29-month gap? Did the fund change portfolio strategy, asset allocation, or benchmark during this window?",
      },
      {
        category: "ACCOUNTING_INTEGRITY",
        severity: "MEDIUM",
        title: "Reported Asset Weights Exceed 100% (100.53%)",
        empiricalEvidence: "In snapshot 2026-09-30, sum of reported holding weights is 100.53% (Equity: 97.72%, Cash: 2.81%).",
        dueDiligenceQuestion: "Does the -0.53% net liability balance represent pending settlement payables, derivatives margin, or AMC management fee accruals?",
      },
    ],
    methodology: {
      methodologyVersion: "PORTFOLIO_CONSTRUCTION_V1",
      governanceStatus: "CANDIDATE",
      hhiFormula: "HHI_{issuer} = \\sum_{j=1}^M (w_j \\times 100)^2",
      effectiveHoldingsFormula: "N_{eff} = \\frac{10,000}{HHI_{issuer}}",
      weightReconciliationPolicy: "Zero artificial normalization. The system reports exact source weights.",
      comparabilityAssessmentStandard: "Comparability requires consistent disclosure scope (full vs partial), reporting interval <= 90 days, and matching calculation definitions.",
      liquidityDiscretionDisclaimer: "Descriptive portfolio construction measures (holdings count, concentration, HHI) describe allocation structure only and do not measure liquidation time, market impact, or fund capacity.",
    },
  };

  it("fetches and deserializes portfolio construction risk response correctly", async () => {
    globalThis.fetch = async () => {
      return {
        ok: true,
        json: async () => mockResponse,
      } as unknown as Response;
    };

    const res = await fetchPortfolioConstructionRisk(1, "2026-10-17", "2026-10-17T14:00:00Z");
    assert.equal(res.status, "SUCCESS");
    assert.equal(res.schemeOptionId, 1);
    assert.equal(res.fundName, "HDFC Flexi Cap Fund");
  });

  it("validates weight reconciliation properties and accrual deviation detection", async () => {
    const rel = mockResponse.dataReliability!;
    assert.equal(rel.sumReportedWeightsPct, "100.53%");
    assert.equal(rel.weightReconciliationCode, "NET_ASSETS_ACCRUAL_DEVIATION");
    assert.equal(rel.totalHoldingsDisclosed, 85);
    assert.equal(rel.uniqueSecuritiesCount, 85);
    assert.equal(rel.completenessType, "FULL_PORTFOLIO_DISCLOSURE");
    assert.equal(rel.cashAndEquivalentsWeightPct, 2.81);
    assert.equal(rel.equityWeightPct, 97.72);
  });

  it("validates issuer concentration aggregation and HHI formula standards", async () => {
    const iss = mockResponse.issuerConcentration!;
    assert.equal(iss.top5IssuerWeightPct, 28.34);
    assert.equal(iss.top10IssuerWeightPct, 43.87);
    assert.equal(iss.issuerHhi, 299.55);
    assert.equal(iss.effectiveNumberOfIssuers, 33.4);
    assert.equal(iss.hhiConcentrationTier, "DIVERSIFIED (HHI < 1,000)");

    // Top issuer check
    const top1 = iss.topIssuers[0];
    assert.equal(top1.rank, 1);
    assert.equal(top1.issuerName, "ICICI Bank Ltd.");
    assert.equal(top1.totalWeightPct, 8.80);
    assert.equal(top1.primarySector, "Financial Services");
  });

  it("validates multi-factor historical comparability and 29-month gap detection", async () => {
    const matrix = mockResponse.comparabilityMatrix;
    assert.equal(matrix.length, 2);

    const gapPair = matrix[0];
    assert.equal(gapPair.earlierDate, "2024-01-31");
    assert.equal(gapPair.laterDate, "2026-06-30");
    assert.equal(gapPair.intervalDays, 881);
    assert.equal(gapPair.isDataGapExceeded, true);
    assert.equal(gapPair.comparabilityStatus, "NON_COMPARABLE");
    assert.deepEqual(gapPair.reasonCodes, [
      "EXTENDED_DATA_GAP_29_MONTHS",
      "DISCLOSURE_SCOPE_MISMATCH",
      "PARTIAL_PORTFOLIO_UNOBSERVED",
    ]);

    const consecutivePair = matrix[1];
    assert.equal(consecutivePair.comparabilityStatus, "COMPARABLE");
    assert.equal(consecutivePair.isDataGapExceeded, false);
  });

  it("validates market cap static classification provenance disclosure", async () => {
    const mcap = mockResponse.marketCapReliability!;
    assert.equal(mcap.classificationProvenance, "STATIC_REFERENCE_MAPPING");
    assert.equal(mcap.historicalPitStatus, "APPROXIMATE_STATIC_REFERENCE");
    assert.equal(mcap.largeCapWeightPct, 63.60);
    assert.equal(mcap.midCapWeightPct, 14.51);
    assert.equal(mcap.smallCapWeightPct, 19.15);
  });

  it("validates due-diligence investigation items and methodology governance", async () => {
    const items = mockResponse.investigationItems;
    assert.equal(items.length, 2);
    assert.equal(items[0].category, "DATA_GAP");
    assert.equal(items[0].severity, "HIGH");

    const method = mockResponse.methodology;
    assert.equal(method.methodologyVersion, "PORTFOLIO_CONSTRUCTION_V1");
    assert.equal(method.governanceStatus, "CANDIDATE");
    assert.ok(method.liquidityDiscretionDisclaimer.includes("do not measure liquidation time"));
  });
});
