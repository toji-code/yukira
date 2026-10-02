import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import { fetchRollingConsistency } from "../../lib/api/analysis";
import { RollingConsistencyResponse } from "../../types/analysis";

describe("Rolling Return & Outperformance Consistency API Tests (§RET-05 / §RET-06)", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockRollingResponse: RollingConsistencyResponse = {
    context: {
      schemeOptionId: 1,
      fundName: "HDFC Flexi Cap Fund",
      amfiCode: "118955",
      isin: "INF179K01UT0",
      planType: "DIRECT",
      optionType: "GROWTH",
      benchmarkId: 123,
      benchmarkName: "NIFTY 500 TRI",
      startDate: "2019-01-01",
      endDate: "2024-01-15",
      knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
      executionCompletedAt: "2026-09-28T01:00:00+05:30",
    },
    primary3Y: {
      periodType: "3Y",
      windowYears: 3,
      minWindowsRequired: 450,
      sufficientData: false,
      returnStatus: "CALCULATED",
      totalWindows: 502,
      meanReturn: 0.240711,
      medianReturn: 0.212329,
      minReturn: 0.106291,
      maxReturn: 0.402638,
      p25Return: 0.182900,
      p75Return: 0.321500,
      stdDev: 0.082100,
      outperformanceStatus: "INSUFFICIENT_DATA",
      pairedWindows: 1,
      outperformingWindows: 1,
      underperformingWindows: 0,
      outperformancePercentage: 100.0,
      meanExcessReturn: 0.085000,
      statusReason: "Benchmark NIFTY 500 TRI history in verified ledger covers 1 paired rolling 3Y windows (minimum 450 required). Missing benchmark history is strictly distinguished from underperformance.",
    },
    supporting1Y: {
      periodType: "1Y",
      windowYears: 1,
      minWindowsRequired: 450,
      sufficientData: true,
      returnStatus: "CALCULATED",
      totalWindows: 994,
      meanReturn: 0.221500,
      medianReturn: 0.201000,
      minReturn: -0.152300,
      maxReturn: 0.584100,
      p25Return: 0.124000,
      p75Return: 0.312000,
      stdDev: 0.141200,
      outperformanceStatus: "CALCULATED",
      pairedWindows: 492,
      outperformingWindows: 492,
      underperformingWindows: 0,
      outperformancePercentage: 100.0,
      meanExcessReturn: 0.091200,
      statusReason: "Synchronously paired across 492 1Y rolling horizons.",
    },
    sampleWindows: [
      {
        startDate: "2021-01-15",
        endDate: "2024-01-15",
        fundReturn: 0.248231,
        benchmarkReturn: 0.183421,
        outperforming: true,
      },
    ],
    epistemic: {
      observation: "Evaluated across 502 rolling 3Y windows.",
      interpretation: "Rolling distributions reduce point-to-point end-point bias.",
      limitation: "Past rolling consistency does not guarantee future performance across regimes.",
      dataQualityStatus: "AUTHORITATIVE_DATA_QUALITY_VERIFIED",
      benchmarkIntegrityDisclosure: "Zero synthetic benchmark data. Synchronously paired on calendar ending dates.",
      sourceArtifactSha256: "900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259",
      calculationRunId: 101,
    },
  };

  it("fetchRollingConsistency queries the correct endpoint with parameters", async () => {
    let capturedUrl = "";
    globalThis.fetch = async (url) => {
      capturedUrl = String(url);
      return new Response(JSON.stringify(mockRollingResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    const res = await fetchRollingConsistency(
      1,
      "2024-01-15",
      "2024-01-31T23:59:59+05:30",
      123
    );

    assert.ok(capturedUrl.includes("/api/v1/analysis/rolling/1"));
    assert.ok(capturedUrl.includes("asOfDate=2024-01-15"));
    assert.ok(capturedUrl.includes("knowledgeCutoffTime=2024-01-31T23%3A59%3A59%2B05%3A30"));
    assert.ok(capturedUrl.includes("benchmarkId=123"));

    assert.equal(res.context.schemeOptionId, 1);
    assert.equal(res.primary3Y.totalWindows, 502);
    assert.equal(res.primary3Y.minWindowsRequired, 450);
    assert.equal(res.primary3Y.meanReturn, 0.240711);
    assert.equal(res.primary3Y.medianReturn, 0.212329);
  });

  it("strictly distinguishes insufficient benchmark history from genuine underperformance", async () => {
    globalThis.fetch = async () =>
      new Response(JSON.stringify(mockRollingResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const res = await fetchRollingConsistency(1);

    // 3Y primary horizon has INSUFFICIENT_DATA due to benchmark length, NOT underperformance
    assert.equal(res.primary3Y.outperformanceStatus, "INSUFFICIENT_DATA");
    assert.equal(res.primary3Y.sufficientData, false);
    assert.ok(res.primary3Y.statusReason.includes("Missing benchmark history is strictly distinguished"));

    // 1Y supporting horizon has full 492 windows and is CALCULATED
    assert.equal(res.supporting1Y.outperformanceStatus, "CALCULATED");
    assert.equal(res.supporting1Y.sufficientData, true);
    assert.equal(res.supporting1Y.outperformingWindows, 492);
    assert.equal(res.supporting1Y.outperformancePercentage, 100.0);
  });

  it("preserves epistemic disclosure triad and cryptographic provenance", async () => {
    globalThis.fetch = async () =>
      new Response(JSON.stringify(mockRollingResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const res = await fetchRollingConsistency(1);

    assert.ok(res.epistemic.observation.length > 0);
    assert.ok(res.epistemic.interpretation.length > 0);
    assert.ok(res.epistemic.limitation.length > 0);
    assert.equal(
      res.epistemic.sourceArtifactSha256,
      "900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259"
    );
  });
});
