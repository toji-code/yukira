import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  fetchBenchmarkDivergence,
  fetchBenchmarkReconstitution,
  BenchmarkDivergenceResponseDto,
  BenchmarkReconstitutionResponseDto,
} from "../../lib/api/portfolio";

describe("Benchmark Divergence & Multi-Period Reconstitution Unit Tests", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockDivergenceResponse: BenchmarkDivergenceResponseDto = {
    schemeOptionId: 1,
    fundName: "HDFC Flexi Cap Fund",
    benchmarkCode: "NIFTY_500_TRI",
    benchmarkName: "NIFTY 500 Total Returns Index",
    startDate: "2024-01-15",
    endDate: "2024-01-31",
    knowledgeCutoff: "2026-10-17T23:59:59+05:30",
    overallStatus: "STALENESS_WARNINGS_PRESENT",
    coverageExplanation:
      "Calculated across 2 disclosed portfolio snapshots against eligible NIFTY_500_TRI constituent snapshots under the Point-in-Time cutoff.",
    periods: [
      {
        portfolioDate: "2024-01-15",
        benchmarkDate: "2019-03-29",
        snapshotDateDifferenceDays: 1753,
        snapshotDateCompatible: false,
        fundHoldingsCount: 10,
        benchmarkConstituentCount: 501,
        commonCount: 9,
        fundOnlyCount: 1,
        benchmarkOnlyCount: 492,
        overlapPct: 17.65,
        fundMappedPct: 100.0,
        benchmarkDisclosedWeightPct: 34.61,
        offBenchmarkCapitalAllocationPct: 2.15,
        periodStatus: "UNVERIFIED_SOURCE",
        warningMessage:
          "Snapshot date mismatch (1753 days). Benchmark constituents reflect 2019-03-29 archival data.",
      },
      {
        portfolioDate: "2024-01-31",
        benchmarkDate: "2019-03-29",
        snapshotDateDifferenceDays: 1769,
        snapshotDateCompatible: false,
        fundHoldingsCount: 10,
        benchmarkConstituentCount: 501,
        commonCount: 9,
        fundOnlyCount: 1,
        benchmarkOnlyCount: 492,
        overlapPct: 18.10,
        fundMappedPct: 100.0,
        benchmarkDisclosedWeightPct: 34.61,
        offBenchmarkCapitalAllocationPct: 1.95,
        periodStatus: "UNVERIFIED_SOURCE",
        warningMessage:
          "Snapshot date mismatch (1769 days). Benchmark constituents reflect 2019-03-29 archival data.",
      },
    ],
    apparentChanges: [
      {
        changeCategory: "FUND_PORTFOLIO_CHANGE",
        securityName: "HDFC Bank Ltd.",
        isin: "INE040A01034",
        earlierState: "8.80%",
        laterState: "9.20%",
        impactWeightPct: 0.40,
        description:
          "Disclosed holding weight shifted from 8.80% to 9.20% (+0.40%).",
        epistemicNote:
          "Fund portfolio change represents reported holding weight differences across monthly disclosures.",
      },
      {
        changeCategory: "BENCHMARK_MEMBERSHIP_CHANGE",
        securityName: "Allahabad Bank",
        isin: "INE428A01015",
        earlierState: "IN_BENCHMARK",
        laterState: "EXITED_BENCHMARK",
        impactWeightPct: null,
        description: "Constituent removed from benchmark index.",
        epistemicNote:
          "Benchmark membership change reflects index additions or deletions.",
      },
      {
        changeCategory: "CLASSIFICATION_COVERAGE_CHANGE",
        securityName: "Voltas Ltd.",
        isin: "INE226A01021",
        earlierState: "MID_CAP",
        laterState: "LARGE_CAP",
        impactWeightPct: null,
        description:
          "AMFI semi-annual market-cap reclassification: MID_CAP -> LARGE_CAP.",
        epistemicNote:
          "Classification shift stems strictly from official AMFI semi-annual market-cap rankings.",
      },
    ],
    dueDiligencePrompts: [
      "Review whether off-benchmark capital allocation of 2.15% represents small-cap high-conviction ideas or cash substitutes.",
    ],
  };

  const mockReconstitutionResponse: BenchmarkReconstitutionResponseDto = {
    benchmarkCode: "NIFTY_500_TRI",
    benchmarkName: "NIFTY 500 Total Returns Index",
    earlierDate: "2019-03-29",
    laterDate: "2026-09-30",
    intervalDays: 2741,
    intervalClassification: "ARCHIVAL_INTERVAL_2741_DAYS",
    earlierConstituentCount: 501,
    laterConstituentCount: 500,
    enteredCount: 199,
    exitedCount: 200,
    retainedCount: 301,
    turnoverPct: 39.8,
    unresolvedCount: 0,
    coverageStatus: "COMPLETE",
    provenanceStatus: "TWO_POINT_OBSERVED_TRANSITION",
    enteredConstituents: [
      {
        isin: "INE102D01028",
        symbol: "POLYCAB",
        companyName: "Polycab India Ltd.",
        industry: "Capital Goods",
        effectiveDate: "2026-09-30",
        sourceWeightPct: null,
        mappingStatus: "MAPPED",
        canonicalSecurityId: 450,
        changeType: "ENTERED",
        notes: "Listed April 2019; absent from March 2019 archival snapshot.",
      },
    ],
    exitedConstituents: [
      {
        isin: "INE428A01015",
        symbol: "ALBK",
        companyName: "Allahabad Bank",
        industry: "Financial Services",
        effectiveDate: "2019-03-29",
        sourceWeightPct: null,
        mappingStatus: "MAPPED",
        canonicalSecurityId: 902,
        changeType: "EXITED",
        notes: "Amalgamated into Indian Bank effective April 2020.",
      },
    ],
    retainedConstituents: [
      {
        isin: "INE040A01034",
        symbol: "HDFCBANK",
        companyName: "HDFC Bank Ltd.",
        industry: "Financial Services",
        effectiveDate: "2026-09-30",
        sourceWeightPct: 5.66,
        mappingStatus: "MAPPED",
        canonicalSecurityId: 101,
        changeType: "RETAINED",
        notes: "Continuous constituent across corporate actions (stock split 2:1 in 2019).",
      },
    ],
    investorNotice:
      "Notice: This comparison measures observed net differences between two archival snapshots separated by 2,741 days.",
    knowledgeCutoff: "2026-10-17T23:59:59+05:30",
  };

  it("fetchBenchmarkDivergence queries the correct endpoint with PIT cutoff", async () => {
    let capturedUrl = "";

    globalThis.fetch = (async (url: string | URL | Request) => {
      capturedUrl = url.toString();
      return {
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        json: async () => mockDivergenceResponse,
      } as Response;
    }) as typeof fetch;

    const res = await fetchBenchmarkDivergence(1, "2024-01-15", "2024-01-31", "2026-10-17T23:59:59+05:30");

    assert.ok(capturedUrl.includes("/api/v1/schemes/options/1/benchmark-divergence"));
    assert.ok(capturedUrl.includes("startDate=2024-01-15"));
    assert.ok(capturedUrl.includes("endDate=2024-01-31"));
    assert.equal(res.schemeOptionId, 1);
    assert.equal(res.periods.length, 2);
    assert.equal(res.apparentChanges.length, 3);
  });

  it("fetchBenchmarkReconstitution queries the correct endpoint", async () => {
    let capturedUrl = "";

    globalThis.fetch = (async (url: string | URL | Request) => {
      capturedUrl = url.toString();
      return {
        ok: true,
        status: 200,
        headers: new Headers({ "content-type": "application/json" }),
        json: async () => mockReconstitutionResponse,
      } as Response;
    }) as typeof fetch;

    const res = await fetchBenchmarkReconstitution(1, "NIFTY_500_TRI");

    assert.ok(capturedUrl.includes("/api/v1/schemes/options/1/benchmark-reconstitution"));
    assert.ok(capturedUrl.includes("benchmarkCode=NIFTY_500_TRI"));
    assert.equal(res.benchmarkCode, "NIFTY_500_TRI");
    assert.equal(res.enteredCount, 199);
    assert.equal(res.exitedCount, 200);
    assert.equal(res.retainedCount, 301);
  });

  it("separates Three Sources of Apparent Change correctly", () => {
    const changes = mockDivergenceResponse.apparentChanges;
    const fundChanges = changes.filter((c) => c.changeCategory === "FUND_PORTFOLIO_CHANGE");
    const bmChanges = changes.filter((c) => c.changeCategory === "BENCHMARK_MEMBERSHIP_CHANGE");
    const classChanges = changes.filter((c) => c.changeCategory === "CLASSIFICATION_COVERAGE_CHANGE");

    assert.equal(fundChanges.length, 1);
    assert.equal(fundChanges[0].securityName, "HDFC Bank Ltd.");
    assert.equal(fundChanges[0].impactWeightPct, 0.40);

    assert.equal(bmChanges.length, 1);
    assert.equal(bmChanges[0].securityName, "Allahabad Bank");
    assert.equal(bmChanges[0].laterState, "EXITED_BENCHMARK");

    assert.equal(classChanges.length, 1);
    assert.equal(classChanges[0].securityName, "Voltas Ltd.");
    assert.equal(classChanges[0].earlierState, "MID_CAP");
    assert.equal(classChanges[0].laterState, "LARGE_CAP");
  });

  it("flags timing mismatch and staleness warning when benchmark snapshot gap exceeds 45 days", () => {
    const period = mockDivergenceResponse.periods[0];
    assert.equal(period.snapshotDateCompatible, false);
    assert.ok(period.snapshotDateDifferenceDays > 45);
    assert.equal(period.periodStatus, "UNVERIFIED_SOURCE");
    assert.ok(period.warningMessage?.includes("Snapshot date mismatch"));
  });

  it("enforces two-point transition classification over long intervals without inventing continuous history", () => {
    assert.equal(mockReconstitutionResponse.provenanceStatus, "TWO_POINT_OBSERVED_TRANSITION");
    assert.ok(mockReconstitutionResponse.intervalDays > 365);
    assert.ok(mockReconstitutionResponse.investorNotice.includes("two archival snapshots separated by 2,741 days"));
  });
});
