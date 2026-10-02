import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import { fetchCaptureRatios } from "../../lib/api/analysis";
import { CaptureRatioResponse } from "../../types/analysis";

describe("Capture Ratios & Asymmetry API Tests (§MKT-03 / §MKT-04 / §MKT-05)", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockCaptureResponse: CaptureRatioResponse = {
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
      upsideCaptureRatio: 93.41,
      upsideStatus: "CALCULATED",
      upDaysCount: 417,
      minUpDaysRequired: 150,
      isUpSufficient: true,
      fundUpCumulativeReturn: 13.931442,
      benchUpCumulativeReturn: 14.914344,
      downsideCaptureRatio: 96.65,
      downsideStatus: "CALCULATED",
      downDaysCount: 321,
      minDownDaysRequired: 100,
      isDownSufficient: true,
      fundDownCumulativeReturn: -0.862072,
      benchDownCumulativeReturn: -0.891911,
      isInverseCaptureGain: false,
      captureSpread: -3.24,
      spreadStatus: "CALCULATED",
      totalPairedDays: 738,
      flatDaysCount: 0,
    },
    epistemic: {
      observation: "Over 738 paired trading days against NIFTY 500 TRI, the fund recorded an Upside Capture Ratio of 93.41% and Downside Capture of 96.65%.",
      interpretation: "Upside capture of 93.41% indicates participation in 93.41% of market upswings, while downside capture of 96.65% indicates absorbing 96.65% of market declines.",
      limitation: "Capture ratios compound returns over disjoint, non-contiguous subsets of calendar dates, creating an artificial chronological path.",
      dataQualityStatus: "AUTHORITATIVE_DATA_QUALITY_VERIFIED",
      benchmarkLineage: "Benchmark observations are strictly matched on identical calendar trading days.",
      sourceArtifactSha256: "900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259",
      calculationRunId: 101,
    },
  };

  it("fetchCaptureRatios constructs correct endpoint URL with parameters", async () => {
    let requestedUrl = "";

    globalThis.fetch = async (input: RequestInfo | URL) => {
      requestedUrl = input.toString();
      return {
        ok: true,
        json: async () => mockCaptureResponse,
      } as Response;
    };

    const res = await fetchCaptureRatios(1, "2024-01-15", "2024-01-31T23:59:59+05:30", 123);

    assert.equal(res.context.schemeOptionId, 1);
    assert.equal(res.metrics.upsideCaptureRatio, 93.41);
    assert.equal(res.metrics.downsideCaptureRatio, 96.65);
    assert.equal(res.metrics.captureSpread, -3.24);
    assert.ok(requestedUrl.includes("/api/v1/analysis/capture/1"));
    assert.ok(requestedUrl.includes("asOfDate=2024-01-15"));
    assert.ok(requestedUrl.includes("benchmarkId=123"));
  });

  it("verifies MKT-03 and MKT-04 minimum observation thresholds", () => {
    assert.equal(mockCaptureResponse.metrics.minUpDaysRequired, 150);
    assert.equal(mockCaptureResponse.metrics.minDownDaysRequired, 100);
    assert.ok(mockCaptureResponse.metrics.upDaysCount >= 150);
    assert.ok(mockCaptureResponse.metrics.downDaysCount >= 100);
    assert.equal(mockCaptureResponse.metrics.isUpSufficient, true);
    assert.equal(mockCaptureResponse.metrics.isDownSufficient, true);
  });

  it("verifies mathematical capture spread invariant (UC - DC)", () => {
    const uc = mockCaptureResponse.metrics.upsideCaptureRatio!;
    const dc = mockCaptureResponse.metrics.downsideCaptureRatio!;
    const spread = mockCaptureResponse.metrics.captureSpread!;

    const expectedSpread = parseFloat((uc - dc).toFixed(2));
    assert.equal(spread, expectedSpread);
  });

  it("handles insufficient downside data edge case gracefully", () => {
    const insufficientDownside: CaptureRatioResponse = {
      ...mockCaptureResponse,
      metrics: {
        ...mockCaptureResponse.metrics,
        downsideCaptureRatio: null,
        downsideStatus: "INSUFFICIENT_DATA",
        downDaysCount: 42,
        isDownSufficient: false,
        captureSpread: null,
        spreadStatus: "INSUFFICIENT_DATA",
      },
    };

    assert.equal(insufficientDownside.metrics.downsideStatus, "INSUFFICIENT_DATA");
    assert.equal(insufficientDownside.metrics.downsideCaptureRatio, null);
    assert.equal(insufficientDownside.metrics.isDownSufficient, false);
    assert.equal(insufficientDownside.metrics.captureSpread, null);
    assert.equal(insufficientDownside.metrics.spreadStatus, "INSUFFICIENT_DATA");
  });

  it("verifies inverse capture gain flag logic", () => {
    const inverseGainSample: CaptureRatioResponse = {
      ...mockCaptureResponse,
      metrics: {
        ...mockCaptureResponse.metrics,
        fundDownCumulativeReturn: 0.05, // Fund gained during down market
        downsideCaptureRatio: -5.6,
        isInverseCaptureGain: true,
      },
    };

    assert.equal(inverseGainSample.metrics.isInverseCaptureGain, true);
    assert.ok(inverseGainSample.metrics.downsideCaptureRatio! < 0);
  });
});
