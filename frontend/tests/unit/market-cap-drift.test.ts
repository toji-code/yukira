import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  fetchMarketCapDrift,
  MarketCapDriftResponseDto,
} from "../../lib/api/portfolio";

describe("Point-in-Time Market-Cap Style Drift & Allocation Decomposition API Tests", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockMarketCapResponse: MarketCapDriftResponseDto = {
    schemeOptionId: 1,
    fundName: "HDFC Flexi Cap Fund",
    planType: "DIRECT",
    optionType: "GROWTH",
    amfiCode: "118955",
    schemeCode: "HDFC_FLEXI",
    benchmarkId: 100,
    benchmarkCode: "NIFTY_500_TRI",
    benchmarkName: "NIFTY 500 Total Returns Index",
    status: "SUCCESS",
    message: "Point-in-Time Market-Cap Style Drift & Allocation Decomposition successfully calculated.",
    methodologyVersion: "YUKIRA-MCAP-DRIFT-1.0 (Phase 2K Deterministic)",
    runId: "test-run-123",
    knowledgeCutoff: "2026-10-18T13:00:00+05:30",
    totalSnapshotsFound: 2,
    eligibleSnapshotsCount: 2,
    skippedSnapshotsCount: 0,
    observations: [
      {
        snapshotId: 101,
        portfolioDate: "2024-06-30",
        availabilityTime: "2024-07-05T12:00:00Z",
        portfolioDisclosureScope: "FULL_PORTFOLIO_DISCLOSURE",
        sumReportedWeightsPct: 98.50,
        weightReconciliationDeltaPct: 1.50,
        totalHoldingsCount: 52,
        classificationCoverageCountPct: 96.15,
        classificationCoverageWeightPct: 97.80,
        unclassifiedWeightPct: 0.70,
        unclassifiedHoldingCount: 2,
        sourceRankingPeriod: "Jul-Dec 2023",
        sourcePublicationDate: "2024-01-05",
        effectiveFrom: "2024-02-01",
        effectiveTo: "2024-07-31",
        classificationSourceArtifactId: 28,
        classificationSourceArtifactSha256: "900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259",
        classificationStatus: "VERIFIED_HISTORICAL_PIT",
        bucketExposures: [
          {
            category: "LARGE_CAP",
            categoryLabel: "Large-Cap (1st–100th)",
            reportedWeightPct: 72.40,
            normalizedWeightPct: 73.50,
            holdingCount: 28,
            topHoldings: ["ICICI Bank Ltd. (9.80%)", "HDFC Bank Ltd. (8.90%)"],
            classificationSource: "AMFI Semi-Annual Market-Cap Ledger (Jul-Dec 2023)",
            rankingPeriod: "Jul-Dec 2023",
            effectiveFrom: "2024-02-01",
            effectiveTo: "2024-07-31",
            provenance: "VERIFIED_HISTORICAL_PIT"
          },
          {
            category: "MID_CAP",
            categoryLabel: "Mid-Cap (101st–250th)",
            reportedWeightPct: 15.20,
            normalizedWeightPct: 15.43,
            holdingCount: 12,
            topHoldings: ["Trent Ltd. (4.50%)", "Bharat Forge Ltd. (2.30%)"],
            classificationSource: "AMFI Semi-Annual Market-Cap Ledger (Jul-Dec 2023)",
            rankingPeriod: "Jul-Dec 2023",
            effectiveFrom: "2024-02-01",
            effectiveTo: "2024-07-31",
            provenance: "VERIFIED_HISTORICAL_PIT"
          },
          {
            category: "SMALL_CAP",
            categoryLabel: "Small-Cap (251st+)",
            reportedWeightPct: 5.40,
            normalizedWeightPct: 5.48,
            holdingCount: 6,
            topHoldings: ["Neuland Laboratories Ltd. (1.50%)"],
            classificationSource: "AMFI Semi-Annual Market-Cap Ledger (Jul-Dec 2023)",
            rankingPeriod: "Jul-Dec 2023",
            effectiveFrom: "2024-02-01",
            effectiveTo: "2024-07-31",
            provenance: "VERIFIED_HISTORICAL_PIT"
          },
          {
            category: "CASH",
            categoryLabel: "Cash & Liquid Assets",
            reportedWeightPct: 3.80,
            normalizedWeightPct: 3.86,
            holdingCount: 2,
            topHoldings: ["TREPS (3.80%)"],
            classificationSource: "AMC Disclosed Asset Class",
            rankingPeriod: null,
            effectiveFrom: null,
            effectiveTo: null,
            provenance: "ASSET_CLASS_DISCLOSURE"
          },
          {
            category: "DEBT_OTHER",
            categoryLabel: "Debt & Other Non-Equity",
            reportedWeightPct: 1.00,
            normalizedWeightPct: 1.02,
            holdingCount: 2,
            topHoldings: ["GOI Sovereign Bond (1.00%)"],
            classificationSource: "AMC Disclosed Asset Class",
            rankingPeriod: null,
            effectiveFrom: null,
            effectiveTo: null,
            provenance: "ASSET_CLASS_DISCLOSURE"
          },
          {
            category: "UNCLASSIFIED",
            categoryLabel: "Unclassified / Unresolved Holdings",
            reportedWeightPct: 0.70,
            normalizedWeightPct: 0.71,
            holdingCount: 2,
            topHoldings: ["Mystery Corp (0.70%)"],
            classificationSource: "Unresolved Classification",
            rankingPeriod: null,
            effectiveFrom: null,
            effectiveTo: null,
            provenance: "UNMAPPED"
          }
        ],
        benchmarkComparison: {
          benchmarkId: 100,
          benchmarkCode: "NIFTY_500_TRI",
          benchmarkName: "NIFTY 500 Total Returns Index",
          benchmarkDate: "2024-06-30",
          benchmarkAvailabilityTime: "2024-07-01T12:00:00Z",
          benchmarkConstituentsCount: 501,
          weightedConstituentsCount: 10,
          benchmarkWeightCoveragePct: 33.80,
          isWeightCoverageComplete: false,
          benchmarkLargeCapDisclosedWeightPct: 33.80,
          benchmarkMidCapDisclosedWeightPct: 0.00,
          benchmarkSmallCapDisclosedWeightPct: 0.00,
          gatingStatus: "GATED_INCOMPLETE_BENCHMARK_WEIGHTS",
          gatingReason: "Benchmark constituent weights are only partially disclosed (33.80% weight coverage across 10 weighted constituents). Definitive active market-cap exposure is gated to prevent artificial active overweight distortion.",
          disclaimer: "Disclosed benchmark weights represent partial top-holding lower bounds (33.80% total coverage). Unknown constituent weights are NEVER treated as zero."
        }
      },
      {
        snapshotId: 102,
        portfolioDate: "2024-08-31",
        availabilityTime: "2024-09-05T12:00:00Z",
        portfolioDisclosureScope: "FULL_PORTFOLIO_DISCLOSURE",
        sumReportedWeightsPct: 98.90,
        weightReconciliationDeltaPct: 1.10,
        totalHoldingsCount: 53,
        classificationCoverageCountPct: 96.23,
        classificationCoverageWeightPct: 98.10,
        unclassifiedWeightPct: 0.80,
        unclassifiedHoldingCount: 2,
        sourceRankingPeriod: "Jan-Jun 2024",
        sourcePublicationDate: "2024-07-05",
        effectiveFrom: "2024-08-01",
        effectiveTo: "2025-01-31",
        classificationSourceArtifactId: 29,
        classificationSourceArtifactSha256: "abcdef...",
        classificationStatus: "VERIFIED_HISTORICAL_PIT",
        bucketExposures: [
          {
            category: "LARGE_CAP",
            categoryLabel: "Large-Cap (1st–100th)",
            reportedWeightPct: 77.80,
            normalizedWeightPct: 78.67,
            holdingCount: 29,
            topHoldings: ["ICICI Bank Ltd. (10.10%)", "Trent Ltd. (5.20%)"],
            classificationSource: "AMFI Semi-Annual Market-Cap Ledger (Jan-Jun 2024)",
            rankingPeriod: "Jan-Jun 2024",
            effectiveFrom: "2024-08-01",
            effectiveTo: "2025-01-31",
            provenance: "VERIFIED_HISTORICAL_PIT"
          },
          {
            category: "MID_CAP",
            categoryLabel: "Mid-Cap (101st–250th)",
            reportedWeightPct: 10.30,
            normalizedWeightPct: 10.41,
            holdingCount: 11,
            topHoldings: ["Bharat Forge Ltd. (2.40%)"],
            classificationSource: "AMFI Semi-Annual Market-Cap Ledger (Jan-Jun 2024)",
            rankingPeriod: "Jan-Jun 2024",
            effectiveFrom: "2024-08-01",
            effectiveTo: "2025-01-31",
            provenance: "VERIFIED_HISTORICAL_PIT"
          },
          {
            category: "SMALL_CAP",
            categoryLabel: "Small-Cap (251st+)",
            reportedWeightPct: 5.60,
            normalizedWeightPct: 5.66,
            holdingCount: 7,
            topHoldings: ["Neuland Laboratories Ltd. (1.60%)"],
            classificationSource: "AMFI Semi-Annual Market-Cap Ledger (Jan-Jun 2024)",
            rankingPeriod: "Jan-Jun 2024",
            effectiveFrom: "2024-08-01",
            effectiveTo: "2025-01-31",
            provenance: "VERIFIED_HISTORICAL_PIT"
          },
          {
            category: "CASH",
            categoryLabel: "Cash & Liquid Assets",
            reportedWeightPct: 3.40,
            normalizedWeightPct: 3.44,
            holdingCount: 2,
            topHoldings: ["TREPS (3.40%)"],
            classificationSource: "AMC Disclosed Asset Class",
            rankingPeriod: null,
            effectiveFrom: null,
            effectiveTo: null,
            provenance: "ASSET_CLASS_DISCLOSURE"
          },
          {
            category: "DEBT_OTHER",
            categoryLabel: "Debt & Other Non-Equity",
            reportedWeightPct: 1.00,
            normalizedWeightPct: 1.01,
            holdingCount: 2,
            topHoldings: ["GOI Sovereign Bond (1.00%)"],
            classificationSource: "AMC Disclosed Asset Class",
            rankingPeriod: null,
            effectiveFrom: null,
            effectiveTo: null,
            provenance: "ASSET_CLASS_DISCLOSURE"
          },
          {
            category: "UNCLASSIFIED",
            categoryLabel: "Unclassified / Unresolved Holdings",
            reportedWeightPct: 0.80,
            normalizedWeightPct: 0.81,
            holdingCount: 2,
            topHoldings: ["Mystery Corp (0.80%)"],
            classificationSource: "Unresolved Classification",
            rankingPeriod: null,
            effectiveFrom: null,
            effectiveTo: null,
            provenance: "UNMAPPED"
          }
        ],
        benchmarkComparison: {
          benchmarkId: 100,
          benchmarkCode: "NIFTY_500_TRI",
          benchmarkName: "NIFTY 500 Total Returns Index",
          benchmarkDate: "2024-08-31",
          benchmarkAvailabilityTime: "2024-09-01T12:00:00Z",
          benchmarkConstituentsCount: 501,
          weightedConstituentsCount: 10,
          benchmarkWeightCoveragePct: 34.10,
          isWeightCoverageComplete: false,
          benchmarkLargeCapDisclosedWeightPct: 34.10,
          benchmarkMidCapDisclosedWeightPct: 0.00,
          benchmarkSmallCapDisclosedWeightPct: 0.00,
          gatingStatus: "GATED_INCOMPLETE_BENCHMARK_WEIGHTS",
          gatingReason: "Benchmark constituent weights are only partially disclosed.",
          disclaimer: "Disclosed benchmark weights represent partial top-holding lower bounds."
        }
      }
    ],
    transitions: [
      {
        earlierDate: "2024-06-30",
        laterDate: "2024-08-31",
        category: "LARGE_CAP",
        earlierWeightPct: 72.40,
        laterWeightPct: 77.80,
        deltaWeightPct: 5.40,
        weightChangeComponent: 0.90,
        reclassificationComponent: 4.50,
        disclosureScopeComponent: 0.00,
        unresolvedComponent: 0.00,
        isReconciled: true,
        reconciliationTolerance: 0.02,
        primaryDriver: "MARKET_CAP_RECLASSIFICATION",
        isComparable: true,
        nonComparableReason: null,
        explanation: "Category exposure changed by +5.40 pp, primarily driven by AMFI semi-annual reclassification (+4.50 pp reclassification component; +0.90 pp weight component).",
        earlierCoveragePct: 97.80,
        laterCoveragePct: 98.10,
        earlierHoldingCount: 28,
        laterHoldingCount: 29,
        reclassifiedHoldings: [
          {
            isin: "INE849A01020",
            securityName: "Trent Ltd.",
            earlierCategory: "MID_CAP",
            earlierRank: 105,
            laterCategory: "LARGE_CAP",
            laterRank: 92,
            earlierWeightPct: 4.50,
            laterWeightPct: 5.20,
            weightDeltaPct: 0.70,
            rankingPeriodChange: "Jul-Dec 2023 -> Jan-Jun 2024",
            explanation: "AMFI reclassified Trent Ltd. from MID_CAP (#105) to LARGE_CAP (#92) under ranking period update."
          }
        ]
      }
    ],
    allReclassifiedHoldings: [
      {
        isin: "INE849A01020",
        securityName: "Trent Ltd.",
        earlierCategory: "MID_CAP",
        earlierRank: 105,
        laterCategory: "LARGE_CAP",
        laterRank: 92,
        earlierWeightPct: 4.50,
        laterWeightPct: 5.20,
        weightDeltaPct: 0.70,
        rankingPeriodChange: "Jul-Dec 2023 -> Jan-Jun 2024",
        explanation: "AMFI reclassified Trent Ltd. from MID_CAP (#105) to LARGE_CAP (#92) under ranking period update."
      }
    ],
    dataQualityWarnings: [
      "Portfolio reported weight sum (98.90%) diverges from 100.00% by 1.10 pp (cash/derivative netting or partial reporting)."
    ],
    investigationQuestions: [
      "How much of the observed market-cap drift was caused by AMFI ranking reclassifications versus active manager allocation changes?"
    ],
    disclaimers: [
      "Descriptive portfolio construction analysis only. Does not constitute investment advice or manager skill scoring.",
      "Zero fake data: All market-cap classifications are grounded in verified point-in-time AMFI ranking ledgers."
    ]
  };

  it("fetchMarketCapDrift: successfully calls endpoint and deserializes response", async () => {
    let capturedUrl = "";
    globalThis.fetch = async (input: RequestInfo | URL) => {
      capturedUrl = String(input);
      return {
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        json: async () => mockMarketCapResponse,
      } as Response;
    };

    const result = await fetchMarketCapDrift(1, "2024-08-31", "2024-01-01", "2024-08-31", "2024-09-10T23:59:59+05:30");

    assert.ok(capturedUrl.includes("/api/v1/schemes/options/1/market-cap-drift"));
    assert.ok(capturedUrl.includes("asOfDate=2024-08-31"));
    assert.equal(result.status, "SUCCESS");
    assert.equal(result.observations.length, 2);
  });

  it("fetchMarketCapDrift: enforces honest gating when benchmark coverage is incomplete", async () => {
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => mockMarketCapResponse,
    } as Response);

    const result = await fetchMarketCapDrift(1);
    const obs = result.observations[0];

    assert.equal(obs.benchmarkComparison.gatingStatus, "GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
    assert.equal(obs.benchmarkComparison.isWeightCoverageComplete, false);
    assert.ok(obs.benchmarkComparison.benchmarkWeightCoveragePct < 99.50);
  });

  it("fetchMarketCapDrift: accurately tracks drift decomposition and mathematical reconciliation", async () => {
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => mockMarketCapResponse,
    } as Response);

    const result = await fetchMarketCapDrift(1);
    assert.equal(result.transitions.length, 1);
    const transition = result.transitions[0];

    assert.equal(transition.category, "LARGE_CAP");
    assert.equal(transition.primaryDriver, "MARKET_CAP_RECLASSIFICATION");
    assert.equal(transition.isReconciled, true);
    // Verify mathematical reconciliation: deltaObs == weightChange + reclass + disclosure + unresolved
    const sumComponents = transition.weightChangeComponent + transition.reclassificationComponent + transition.disclosureScopeComponent + transition.unresolvedComponent;
    assert.ok(Math.abs(transition.deltaWeightPct - sumComponents) <= transition.reconciliationTolerance);
  });

  it("fetchMarketCapDrift: surfaces reclassified securities across AMFI ranking periods", async () => {
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => mockMarketCapResponse,
    } as Response);

    const result = await fetchMarketCapDrift(1);
    assert.equal(result.allReclassifiedHoldings.length, 1);
    const reclass = result.allReclassifiedHoldings[0];
    assert.equal(reclass.securityName, "Trent Ltd.");
    assert.equal(reclass.earlierCategory, "MID_CAP");
    assert.equal(reclass.laterCategory, "LARGE_CAP");
    assert.equal(reclass.earlierRank, 105);
    assert.equal(reclass.laterRank, 92);
  });

  it("fetchMarketCapDrift: handles server error gracefully", async () => {
    globalThis.fetch = async () => ({
      ok: false,
      status: 500,
      statusText: "Internal Server Error",
      text: async () => "Internal Server Error",
    } as unknown as Response);

    await assert.rejects(
      async () => {
        await fetchMarketCapDrift(999);
      },
      {
        message: /Backend request failed: HTTP 500/,
      }
    );
  });
});
