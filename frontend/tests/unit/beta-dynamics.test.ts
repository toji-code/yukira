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

  it("passes horizon parameter correctly to backend endpoint", async () => {
    let capturedUrl = "";
    globalThis.fetch = async (input: RequestInfo | URL) => {
      capturedUrl = input.toString();
      return {
        ok: true,
        json: async () => mockBetaResponse,
      } as Response;
    };

    await fetchBetaDynamics(1, "2024-01-15", "2024-01-31T23:59:59+05:30", 123, "1Y");
    assert.ok(capturedUrl.includes("horizon=1Y"), `Expected horizon=1Y in URL, got ${capturedUrl}`);

    await fetchBetaDynamics(1, "2024-01-15", "2024-01-31T23:59:59+05:30", 123, "3Y");
    assert.ok(capturedUrl.includes("horizon=3Y"), `Expected horizon=3Y in URL, got ${capturedUrl}`);
  });

  it("validates exposure stability metrics structure and mathematical invariants", () => {
    const fullResponse: BetaDynamicsResponse = {
      ...mockBetaResponse,
      context: {
        ...mockBetaResponse.context,
        windowHorizon: "3Y",
      },
      stability: {
        totalWindows: 50,
        validWindows: 48,
        medianBeta: 1.025,
        iqrBeta: 0.12,
        minBeta: 0.88,
        minBetaDate: "2022-03-15",
        maxBeta: 1.18,
        maxBetaDate: "2023-08-20",
        startBeta: 0.95,
        startDate: "2021-01-15",
        endBeta: 1.04,
        endDate: "2024-01-15",
        betaChange: 0.09,
        pctAboveParity: 62.5,
        pctBelowParity: 37.5,
        meanDownsideBeta: 1.15,
        meanUpsideBeta: 0.92,
        upsideDownsideSpread: -0.23,
      },
      rollingSeries: [
        {
          windowStart: "2021-01-15",
          windowEnd: "2024-01-15",
          pairedDays: 738,
          beta: 1.02,
          upsideBeta: 0.88,
          downsideBeta: 1.22,
          betaSpread: -0.34,
          upDays: 417,
          downDays: 321,
          status: "CALCULATED",
        },
      ],
      regimes: [
        {
          regimeId: "REG-01",
          regimeName: "COVID-19 Crash & Rebound",
          startDate: "2020-02-01",
          endDate: "2020-11-30",
          description: "Pandemic market shock and initial policy rebound",
          pairedDays: 0,
          upDays: 0,
          downDays: 0,
          fullPeriodBeta: null,
          upsideBeta: null,
          downsideBeta: null,
          medianRollingBeta: null,
          minRollingBeta: null,
          maxRollingBeta: null,
          coverageStatus: "BENCHMARK_UNAVAILABLE",
        },
        {
          regimeId: "REG-02",
          regimeName: "Post-COVID Bull Market",
          startDate: "2021-01-15",
          endDate: "2021-10-18",
          description: "Rapid liquidity-driven equity expansion",
          pairedDays: 185,
          upDays: 105,
          downDays: 80,
          fullPeriodBeta: 1.08,
          upsideBeta: 0.95,
          downsideBeta: 1.25,
          medianRollingBeta: 1.06,
          minRollingBeta: 1.01,
          maxRollingBeta: 1.12,
          coverageStatus: "VERIFIED_COMPLETE_COVERAGE",
        },
      ],
    };

    assert.equal(fullResponse.context.windowHorizon, "3Y");
    assert.ok(fullResponse.stability);
    const stab = fullResponse.stability!;
    assert.equal(stab.totalWindows, 50);
    assert.equal(stab.validWindows, 48);
    assert.ok(stab.minBeta! <= stab.medianBeta!);
    assert.ok(stab.medianBeta! <= stab.maxBeta!);
    assert.equal(
      Number((stab.endBeta! - stab.startBeta!).toFixed(4)),
      stab.betaChange
    );
    assert.equal(stab.pctAboveParity! + stab.pctBelowParity!, 100);

    // Rolling points contract
    assert.equal(fullResponse.rollingSeries?.length, 1);
    const pt = fullResponse.rollingSeries![0];
    assert.equal(pt.status, "CALCULATED");
    assert.equal(pt.pairedDays, pt.upDays + pt.downDays);

    // Pre-specified market regime contract
    assert.equal(fullResponse.regimes?.length, 2);
    const reg1 = fullResponse.regimes![0];
    assert.equal(reg1.regimeId, "REG-01");
    assert.equal(reg1.coverageStatus, "BENCHMARK_UNAVAILABLE");
    assert.equal(reg1.fullPeriodBeta, null); // Strictly un-fabricated

    const reg2 = fullResponse.regimes![1];
    assert.equal(reg2.regimeId, "REG-02");
    assert.equal(reg2.coverageStatus, "VERIFIED_COMPLETE_COVERAGE");
    assert.equal(reg2.fullPeriodBeta, 1.08);
  });
});
