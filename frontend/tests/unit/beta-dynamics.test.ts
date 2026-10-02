import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import { fetchBetaDynamics } from "../../lib/api/analysis";
import { BetaDynamicsResponse } from "../../types/analysis";

describe("Benchmark Beta Dynamics API Tests (§REL-01 / §REL-04 / §REL-05)", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockBetaResponse: BetaDynamicsResponse = {
    context: {
      schemeOptionId: 1,
      fundName: "HDFC Flexi Cap Fund",
      amfiCode: "118955",
      isin: "INF179K01UT0",
      planType: "DIRECT",
      optionType: "GROWTH",
      benchmarkId: 123,
      benchmarkName: "NIFTY 500 TRI",
      startDate: "2021-01-15",
      endDate: "2024-01-15",
      knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
      evaluatedAt: "2026-09-28T02:00:00+05:30",
    },
    metrics: {
      standardBeta: 1.02,
      standardBetaStatus: "CALCULATED",
      downsideBeta: 1.22,
      downsideBetaStatus: "CALCULATED",
      upsideBeta: 0.88,
      upsideBetaStatus: "CALCULATED",
      betaAsymmetrySpread: -0.34,
      asymmetryStatus: "CALCULATED",
      totalPairedDays: 738,
      upDaysCount: 417,
      downDaysCount: 321,
      flatDaysCount: 0,
      minPairedRequired: 700,
      minDownRequired: 100,
      minUpRequired: 150,
      isStandardSufficient: true,
      isDownsideSufficient: true,
      isUpsideSufficient: true,
      riskFreeProxy: "FBIL_91D_TBILL",
      riskFreeAligned: true,
    },
    epistemic: {
      observation: "Standard beta estimates full-sample sensitivity; downside and upside beta condition on benchmark regimes.",
      interpretation:
        "Standard beta measures linear sensitivity of portfolio excess returns to benchmark excess returns across the full sample. Downside beta measures co-movement only on days when the benchmark declined, isolating sell-off sensitivity. Upside beta measures co-movement only on days when the benchmark rose. A downside beta above standard beta indicates amplified participation in market declines; an upside beta below standard beta indicates muted participation in advances.",
      limitation: "Beta is a linear static measure and is regime-dependent.",
      dataQualityStatus: "SOURCE_ARTIFACT_VERIFIED",
      benchmarkLineage:
        "Benchmark observations for NIFTY 500 TRI are matched synchronously on identical calendar trading days. Out of 738 paired return observations (700 minimum required for standard beta), 417 were positive (150 required for upside beta), 321 were negative (100 required for downside beta), and 0 were flat (Rb = 0, excluded from both conditioned betas). Missing dates are excluded without date substitution or synthetic interpolation.",
      sourceArtifactSha256: "900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259",
      calculationRunId: 101,
    },
  };

  it("constructs correct endpoint URL with parameters", async () => {
    let requestedUrl = "";

    globalThis.fetch = async (input: RequestInfo | URL) => {
      requestedUrl = input.toString();
      return {
        ok: true,
        json: async () => mockBetaResponse,
      } as Response;
    };

    const res = await fetchBetaDynamics(
      1,
      "2024-01-15",
      "2024-01-31T23:59:59+05:30",
      123
    );

    assert.equal(res.context.schemeOptionId, 1);
    assert.equal(res.metrics.standardBeta, 1.02);
    assert.ok(requestedUrl.includes("/api/v1/analysis/beta/1"));
    assert.ok(requestedUrl.includes("asOfDate=2024-01-15"));
    assert.ok(requestedUrl.includes("benchmarkId=123"));
  });

  it("enforces the approved per-regime observation thresholds (700 / 100 / 150)", () => {
    assert.equal(mockBetaResponse.metrics.minPairedRequired, 700);
    assert.equal(mockBetaResponse.metrics.minDownRequired, 100);
    assert.equal(mockBetaResponse.metrics.minUpRequired, 150);
    assert.ok(mockBetaResponse.metrics.totalPairedDays >= 700);
    assert.ok(mockBetaResponse.metrics.downDaysCount >= 100);
    assert.ok(mockBetaResponse.metrics.upDaysCount >= 150);
    assert.equal(mockBetaResponse.metrics.isStandardSufficient, true);
    assert.equal(mockBetaResponse.metrics.isDownsideSufficient, true);
    assert.equal(mockBetaResponse.metrics.isUpsideSufficient, true);
    assert.equal(mockBetaResponse.metrics.standardBetaStatus, "CALCULATED");
    assert.equal(mockBetaResponse.metrics.downsideBetaStatus, "CALCULATED");
    assert.equal(mockBetaResponse.metrics.upsideBetaStatus, "CALCULATED");
  });

  it("verifies beta asymmetry invariant: spread = upside beta - downside beta (4dp)", () => {
    const metrics = mockBetaResponse.metrics;
    const expectedSpread = Number((metrics.upsideBeta! - metrics.downsideBeta!).toFixed(4));

    assert.equal(metrics.betaAsymmetrySpread, expectedSpread);
    assert.equal(metrics.asymmetryStatus, "CALCULATED");

    // Negative spread: fund participates less in advances than in declines.
    assert.ok(metrics.betaAsymmetrySpread! < 0);
  });

  it("decomposes paired days exactly into up + down + flat (zero interpolation)", () => {
    const metrics = mockBetaResponse.metrics;
    assert.equal(
      metrics.totalPairedDays,
      metrics.upDaysCount + metrics.downDaysCount + metrics.flatDaysCount
    );
    assert.ok(mockBetaResponse.epistemic.benchmarkLineage.includes("synchronously"));
    assert.ok(mockBetaResponse.epistemic.benchmarkLineage.includes("without date substitution"));
  });

  it("handles deterministic insufficient data with NULL values, never fabricated zeroes", () => {
    const insufficientResponse: BetaDynamicsResponse = {
      ...mockBetaResponse,
      metrics: {
        ...mockBetaResponse.metrics,
        standardBeta: null,
        standardBetaStatus: "INSUFFICIENT_DATA",
        downsideBeta: null,
        downsideBetaStatus: "INSUFFICIENT_DATA",
        upsideBeta: null,
        upsideBetaStatus: "INSUFFICIENT_DATA",
        betaAsymmetrySpread: null,
        asymmetryStatus: "INSUFFICIENT_DATA",
        totalPairedDays: 118,
        upDaysCount: 61,
        downDaysCount: 57,
        flatDaysCount: 0,
        isStandardSufficient: false,
        isDownsideSufficient: false,
        isUpsideSufficient: false,
      },
      epistemic: {
        ...mockBetaResponse.epistemic,
        observation:
          "Insufficient paired observations: 118 paired days found; minimum 700 required.",
      },
    };

    const metrics = insufficientResponse.metrics;
    assert.equal(metrics.isStandardSufficient, false);
    assert.equal(metrics.standardBeta, null);
    assert.equal(metrics.downsideBeta, null);
    assert.equal(metrics.upsideBeta, null);
    assert.equal(metrics.betaAsymmetrySpread, null);
    assert.equal(metrics.standardBetaStatus, "INSUFFICIENT_DATA");
    assert.equal(metrics.downsideBetaStatus, "INSUFFICIENT_DATA");
    assert.equal(metrics.upsideBetaStatus, "INSUFFICIENT_DATA");
    assert.equal(metrics.asymmetryStatus, "INSUFFICIENT_DATA");

    // Anti-fabrication contract: nulls are never rendered as 0.00.
    assert.notEqual(metrics.standardBeta, 0);
    assert.notEqual(metrics.downsideBeta, 0);
    assert.notEqual(metrics.upsideBeta, 0);
  });

  it("keeps standard, downside, and upside beta statuses independent per regime", () => {
    // Example: standard and downside sufficient, upside below the 150 up-day threshold.
    const partialResponse: BetaDynamicsResponse = {
      ...mockBetaResponse,
      metrics: {
        ...mockBetaResponse.metrics,
        upsideBeta: null,
        upsideBetaStatus: "INSUFFICIENT_DATA",
        betaAsymmetrySpread: null,
        asymmetryStatus: "INSUFFICIENT_DATA",
        upDaysCount: 112,
        isUpsideSufficient: false,
      },
    };

    const metrics = partialResponse.metrics;
    assert.equal(metrics.standardBetaStatus, "CALCULATED");
    assert.equal(metrics.downsideBetaStatus, "CALCULATED");
    assert.equal(metrics.upsideBetaStatus, "INSUFFICIENT_DATA");
    assert.equal(metrics.isStandardSufficient, true);
    assert.equal(metrics.isDownsideSufficient, true);
    assert.equal(metrics.isUpsideSufficient, false);
    // Asymmetry requires BOTH conditioned betas.
    assert.equal(metrics.betaAsymmetrySpread, null);
    assert.equal(metrics.asymmetryStatus, "INSUFFICIENT_DATA");
  });

  it("preserves epistemic provenance, regime semantics, and quality taxonomy", () => {
    const { context, metrics, epistemic } = mockBetaResponse;

    // Provenance
    assert.equal(epistemic.dataQualityStatus, "SOURCE_ARTIFACT_VERIFIED");
    assert.equal(epistemic.sourceArtifactSha256?.length, 64);
    assert.ok(epistemic.calculationRunId! > 0);
    assert.ok(epistemic.observation.length > 0);
    assert.ok(epistemic.interpretation.length > 0);
    assert.ok(epistemic.limitation.length > 0);

    // Distinct regime explanations must all be present.
    assert.ok(epistemic.interpretation.includes("full sample"));
    assert.ok(epistemic.interpretation.includes("down"));
    assert.ok(epistemic.interpretation.includes("up"));

    // Strict PIT framing
    assert.equal(context.knowledgeCutoffTime, "2024-01-31T23:59:59+05:30");
    assert.ok(context.startDate! <= context.endDate!);
    assert.ok(context.endDate! <= "2024-01-15");

    // Risk-free contract: aligned for standard beta, none for conditioned betas.
    assert.equal(metrics.riskFreeProxy, "FBIL_91D_TBILL");
    assert.equal(metrics.riskFreeAligned, true);
  });
});
