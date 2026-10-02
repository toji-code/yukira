import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import { fetchTrackingConsistency } from "../../lib/api/analysis";
import { TrackingConsistencyResponse } from "../../types/analysis";

describe("Tracking Consistency & Information Ratio API Tests (§REL-02 / §RAT-04)", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockTrackingResponse: TrackingConsistencyResponse = {
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
      trackingErrorAnnualized: 4.5214,
      trackingErrorStatus: "CALCULATED",
      meanDailyExcessReturn: 0.0152,
      annualizedMeanActiveReturn: 3.8304,
      informationRatio: 0.847171,
      informationRatioStatus: "CALCULATED",
      pairedObservationsCount: 738,
      minPairedObservationsRequired: 700,
      isSufficientObservations: true,
      periodsPerYear: 252,
      annualizationConvention: "SQRT_252",
      denominatorConvention: "N_MINUS_ONE",
      zeroTrackingError: false,
    },
    epistemic: {
      observation: "Over 738 paired trading days against NIFTY 500 TRI, the fund exhibited an annualized tracking error of 4.52% with an annualized mean active return of +3.83%, yielding an Information Ratio of +0.85.",
      interpretation: "An Information Ratio of +0.85 indicates that the fund generated 0.85 units of annualized active excess return per unit of volatility in excess returns relative to NIFTY 500 TRI.",
      limitation: "Tracking error assumes symmetric volatility of active returns. Deviations caused by cash drag or structural sector tilts are penalized identically to stock selection variance.",
      dataQualityStatus: "AUTHORITATIVE_DATA_QUALITY_VERIFIED",
      benchmarkLineage: "Synchronously paired observations from authoritative benchmark ledger.",
      sourceArtifactSha256: "900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259",
      calculationRunId: 202,
    },
  };

  it("fetchTrackingConsistency constructs correct endpoint URL with parameters", async () => {
    let requestedUrl = "";

    globalThis.fetch = async (input: RequestInfo | URL) => {
      requestedUrl = input.toString();
      return {
        ok: true,
        json: async () => mockTrackingResponse,
      } as Response;
    };

    const res = await fetchTrackingConsistency(1, "2024-01-15", "2024-01-31T23:59:59+05:30", 123);

    assert.equal(res.context.schemeOptionId, 1);
    assert.equal(res.metrics.trackingErrorAnnualized, 4.5214);
    assert.equal(res.metrics.annualizedMeanActiveReturn, 3.8304);
    assert.equal(res.metrics.informationRatio, 0.847171);
    assert.ok(requestedUrl.includes("/api/v1/analysis/tracking-consistency/1"));
    assert.ok(requestedUrl.includes("asOfDate=2024-01-15"));
    assert.ok(requestedUrl.includes("benchmarkId=123"));
  });

  it("enforces REL-02 and RAT-04 minimum observation threshold (N >= 700)", () => {
    assert.equal(mockTrackingResponse.metrics.minPairedObservationsRequired, 700);
    assert.ok(mockTrackingResponse.metrics.pairedObservationsCount >= 700);
    assert.equal(mockTrackingResponse.metrics.isSufficientObservations, true);
    assert.equal(mockTrackingResponse.metrics.trackingErrorStatus, "CALCULATED");
    assert.equal(mockTrackingResponse.metrics.informationRatioStatus, "CALCULATED");
  });

  it("verifies mathematical invariant: IR = annualizedMeanActiveReturn / trackingErrorAnnualized", () => {
    const te = mockTrackingResponse.metrics.trackingErrorAnnualized!;
    const activeMeanAnnual = mockTrackingResponse.metrics.annualizedMeanActiveReturn!;
    const ir = mockTrackingResponse.metrics.informationRatio!;

    const expectedIr = activeMeanAnnual / te;
    assert.ok(Math.abs(ir - expectedIr) < 1e-4, `Expected ${expectedIr}, got ${ir}`);
  });

  it("handles insufficient paired observations (< 700) deterministically", () => {
    const insufficientResponse: TrackingConsistencyResponse = {
      ...mockTrackingResponse,
      metrics: {
        ...mockTrackingResponse.metrics,
        trackingErrorAnnualized: null,
        trackingErrorStatus: "INSUFFICIENT_OBSERVATIONS",
        meanDailyExcessReturn: null,
        annualizedMeanActiveReturn: null,
        informationRatio: null,
        informationRatioStatus: "INSUFFICIENT_OBSERVATIONS",
        pairedObservationsCount: 350,
        isSufficientObservations: false,
      },
      epistemic: {
        ...mockTrackingResponse.epistemic,
        observation: "Insufficient paired observations: 350 paired days found; minimum 700 required.",
      },
    };

    assert.equal(insufficientResponse.metrics.isSufficientObservations, false);
    assert.equal(insufficientResponse.metrics.trackingErrorAnnualized, null);
    assert.equal(insufficientResponse.metrics.informationRatio, null);
    assert.equal(insufficientResponse.metrics.trackingErrorStatus, "INSUFFICIENT_OBSERVATIONS");
    assert.equal(insufficientResponse.metrics.informationRatioStatus, "INSUFFICIENT_OBSERVATIONS");
  });

  it("handles zero tracking error division by zero condition", () => {
    const zeroTeResponse: TrackingConsistencyResponse = {
      ...mockTrackingResponse,
      metrics: {
        ...mockTrackingResponse.metrics,
        trackingErrorAnnualized: 0.0,
        trackingErrorStatus: "ZERO_TRACKING_ERROR",
        meanDailyExcessReturn: 0.0,
        annualizedMeanActiveReturn: 0.0,
        informationRatio: null,
        informationRatioStatus: "ERROR",
        zeroTrackingError: true,
      },
      epistemic: {
        ...mockTrackingResponse.epistemic,
        observation: "Zero tracking error: Fund exactly matched benchmark variance. Information ratio division undefined.",
      },
    };

    assert.equal(zeroTeResponse.metrics.zeroTrackingError, true);
    assert.equal(zeroTeResponse.metrics.informationRatio, null);
    assert.equal(zeroTeResponse.metrics.informationRatioStatus, "ERROR");
  });

  it("preserves epistemic provenance and quality taxonomy", () => {
    assert.equal(mockTrackingResponse.epistemic.dataQualityStatus, "AUTHORITATIVE_DATA_QUALITY_VERIFIED");
    assert.equal(mockTrackingResponse.epistemic.sourceArtifactSha256.length, 64);
    assert.ok(mockTrackingResponse.epistemic.calculationRunId! > 0);
    assert.ok(mockTrackingResponse.epistemic.observation.length > 0);
    assert.ok(mockTrackingResponse.epistemic.interpretation.length > 0);
    assert.ok(mockTrackingResponse.epistemic.limitation.length > 0);
  });
});
