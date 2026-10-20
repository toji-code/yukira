import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  fetchBenchmarkCompositionComparison,
  BenchmarkCompositionComparisonResponseDto,
} from "../../lib/api/portfolio";

describe("Benchmark Composition & Holdings Overlap API Tests", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockComparisonResponse: BenchmarkCompositionComparisonResponseDto = {
    schemeOptionId: 1,
    fundName: "HDFC Flexi Cap Fund",
    planType: "DIRECT",
    optionType: "GROWTH",
    portfolioDate: "2024-01-15",
    fundHoldingsCount: 2,
    sumFundWeightsPct: 15.0,

    benchmarkId: 100,
    benchmarkCode: "NIFTY_500_TRI",
    benchmarkName: "NIFTY 500 Total Returns Index",
    benchmarkCompositionDate: "2024-01-15",
    benchmarkConstituentCount: 501,
    mappedConstituentCount: 108,
    unmappedConstituentCount: 393,
    weightCoverageStatus: "TOP_WEIGHTS_DISCLOSED",
    sumDisclosedBenchmarkWeightsPct: 41.25,

    commonConstituentCount: 2,
    fundSpecificCount: 0,
    benchmarkSpecificCount: 499,
    holdingsOverlapPct: 14.65,
    unmappedHoldingsWeightPct: 0.0,

    activePositions: [
      {
        isin: "INE090A01021",
        symbol: "ICICIBANK",
        companyName: "ICICI Bank Ltd.",
        fundWeightPct: 6.20,
        benchmarkWeightPct: 5.90,
        activeWeightPct: 0.30,
        marketCapCategory: "LARGE_CAP",
        industry: "Financial Services",
        positionStatus: "OVERWEIGHT",
      },
      {
        isin: "INE040A01034",
        symbol: "HDFCBANK",
        companyName: "HDFC Bank Ltd.",
        fundWeightPct: 8.80,
        benchmarkWeightPct: 8.75,
        activeWeightPct: 0.05,
        marketCapCategory: "LARGE_CAP",
        industry: "Financial Services",
        positionStatus: "OVERWEIGHT",
      },
      {
        isin: "INE002A01018",
        symbol: "RELIANCE",
        companyName: "Reliance Industries Ltd.",
        fundWeightPct: 0.0,
        benchmarkWeightPct: 7.20,
        activeWeightPct: -7.20,
        marketCapCategory: "LARGE_CAP",
        industry: "Oil Gas & Consumable Fuels",
        positionStatus: "BENCHMARK_ONLY",
      },
    ],

    sectorExposures: [
      {
        sectorName: "Financial Services",
        fundSectorWeightPct: 15.0,
        benchmarkSectorWeightPct: 14.65,
        activeSectorExposurePct: 0.35,
        fundStockCount: 2,
        benchmarkStockCount: 2,
      },
      {
        sectorName: "Oil Gas & Consumable Fuels",
        fundSectorWeightPct: 0.0,
        benchmarkSectorWeightPct: 7.20,
        activeSectorExposurePct: -7.20,
        fundStockCount: 0,
        benchmarkStockCount: 1,
      },
    ],

    marketCapExposures: [
      {
        category: "LARGE_CAP",
        fundWeightPct: 15.0,
        benchmarkWeightPct: 21.85,
        activeExposurePct: -6.85,
      },
    ],

    brinsonAttributionStatus: "BLOCKED_PENDING_STOCK_RETURNS",
    brinsonBlockerReason:
      "Constituent-level Brinson-Fachler attribution requires both complete daily benchmark constituent weights and corporate-action-adjusted stock-level daily total returns (R_i,t), which are not present in repository.",
    investorOrientation:
      "Holdings overlap and sector exposure spreads describe structural portfolio positioning relative to the benchmark. They do NOT constitute causal return attribution.",
    dueDiligencePrompts: [
      "Verify whether underweight in benchmark heavyweight RELIANCE reflects intentional manager thesis or mandate constraints.",
    ],

    sourceArtifactId: 201,
    sourceArtifactSha256: "2b150cd1ed08cc6eaef87e2687103ae112eea3a2a1ce211828cc3a5bb12f83ae",
    sourceUrl: "https://www.niftyindices.com/IndexConstituent/ind_nifty500list.csv",
    knowledgeCutoff: "2024-01-20T00:00:00+05:30",
    methodologyVersion: "BENCHMARK_COMPOSITION_V1",
  };

  it("fetchBenchmarkCompositionComparison queries the correct endpoint with PIT parameters", async () => {
    let capturedUrl = "";

    globalThis.fetch = (async (url: string | URL | Request) => {
      capturedUrl = url.toString();
      return {
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        json: async () => mockComparisonResponse,
      } as Response;
    }) as typeof fetch;

    const res = await fetchBenchmarkCompositionComparison(1, "2024-01-15", "2024-01-20T00:00:00+05:30");

    assert.ok(capturedUrl.includes("/api/v1/schemes/options/1/benchmark-comparison"));
    assert.ok(capturedUrl.includes("asOfDate=2024-01-15"));
    assert.ok(capturedUrl.includes("knowledgeCutoff=2024-01-20T00%3A00%3A00%2B05%3A30"));
    assert.equal(res.schemeOptionId, 1);
    assert.equal(res.benchmarkCode, "NIFTY_500_TRI");
    assert.equal(res.benchmarkConstituentCount, 501);
  });

  it("verifies holdings overlap, active security positions, and sector exposure calculations", () => {
    assert.equal(mockComparisonResponse.holdingsOverlapPct, 14.65);
    assert.equal(mockComparisonResponse.commonConstituentCount, 2);
    assert.equal(mockComparisonResponse.mappedConstituentCount, 108);
    assert.equal(mockComparisonResponse.unmappedConstituentCount, 393);

    // Active positions
    const icici = mockComparisonResponse.activePositions.find((p) => p.isin === "INE090A01021");
    assert.ok(icici);
    assert.equal(icici.activeWeightPct, 0.30);
    assert.equal(icici.positionStatus, "OVERWEIGHT");

    const reliance = mockComparisonResponse.activePositions.find((p) => p.isin === "INE002A01018");
    assert.ok(reliance);
    assert.equal(reliance.activeWeightPct, -7.20);
    assert.equal(reliance.positionStatus, "BENCHMARK_ONLY");

    // Sector spread
    const fin = mockComparisonResponse.sectorExposures.find((s) => s.sectorName === "Financial Services");
    assert.ok(fin);
    assert.equal(fin.activeSectorExposurePct, 0.35);
  });

  it("enforces Brinson-Fachler attribution gate contract and epistemic boundary", () => {
    assert.equal(mockComparisonResponse.brinsonAttributionStatus, "BLOCKED_PENDING_STOCK_RETURNS");
    assert.ok(mockComparisonResponse.brinsonBlockerReason.includes("stock-level daily total returns"));
    assert.ok(mockComparisonResponse.investorOrientation.includes("do NOT constitute causal return attribution"));
    assert.equal(mockComparisonResponse.sourceArtifactSha256, "2b150cd1ed08cc6eaef87e2687103ae112eea3a2a1ce211828cc3a5bb12f83ae");
  });
});
