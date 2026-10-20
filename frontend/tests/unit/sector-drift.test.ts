import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  fetchSectorDrift,
  SectorDriftResponseDto,
  SectorActiveComparisonDto,
} from "../../lib/api/portfolio";

describe("Point-in-Time Sector & Industry Exposure Drift API Tests", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockSectorDriftResponse: SectorDriftResponseDto = {
    schemeOptionId: 1,
    fundName: "HDFC Flexi Cap Fund",
    planType: "DIRECT",
    optionType: "GROWTH",
    benchmarkId: 100,
    benchmarkCode: "NIFTY_500_TRI",
    benchmarkName: "NIFTY 500 Total Returns Index",
    overallStatus: "PARTIAL_SECTOR_VIEW_GATED_BENCHMARK",
    statusReason: "Benchmark disclosed constituent weights cover 32.22% (<99.50% threshold). Active sector weights gated.",
    methodologyVersion: "PIT-SECTOR-DRIFT-V1",
    runId: "run-test-123",
    knowledgeCutoff: "2026-10-18T13:00:00+05:30",
    totalSnapshotsFound: 2,
    eligibleObservationsCount: 2,
    skippedPeriodsCount: 0,
    observations: [
      {
        portfolioSnapshotId: 101,
        portfolioDate: "2024-01-15",
        portfolioAvailabilityTime: "2024-01-16T18:30:00+05:30",
        portfolioDisclosureScope: "PARTIAL_TOP_HOLDINGS",
        sumFundWeightsPct: 45.50,
        fundHoldingsCount: 10,
        benchmarkId: 100,
        benchmarkCode: "NIFTY_500_TRI",
        benchmarkDate: "2024-01-15",
        benchmarkAvailabilityTime: "2024-01-15T23:59:59+05:30",
        benchmarkWeightCoveragePct: 41.25,
        benchmarkConstituentsCount: 501,
        benchmarkWeightedCount: 10,
        snapshotDateDiffDays: 0,
        isTemporallyComparable: true,
        observationStatus: "GATED_INCOMPLETE_BENCHMARK_WEIGHTS",
        statusReason: "Benchmark disclosed constituent weights cover 41.25% (<99.50% threshold). Active sector weights gated to prevent false overweight distortion.",
        fundClassificationCoveragePct: 100.0,
        sourceArtifactUri: "https://files.hdfcfund.com/snapshot-2024-01.xlsx",
        sourceArtifactSha256: "900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259",
        sectors: [
          {
            sectorName: "Financial Services",
            fundWeightPct: 22.50,
            holdingCount: 4,
            classificationProvenance: "BENCHMARK_PIT_CLASSIFICATION",
            topHoldings: ["ICICI Bank Ltd. (8.80%)", "HDFC Bank Ltd. (6.03%)"],
          },
        ],
        benchmarkSectors: [
          {
            sectorName: "Financial Services",
            disclosedWeightPct: 18.50,
            weightedConstituentsCount: 4,
            totalConstituentsCount: 52,
            constituentDisclosedCoveragePct: 35.58,
            weightStatus: "PARTIAL_DISCLOSURE_ONLY",
          },
        ],
        sectorComparisons: [
          {
            sectorName: "Financial Services",
            fundWeightPct: 22.50,
            benchmarkDisclosedWeightPct: 18.50,
            definitiveActiveWeightPct: null, // Strictly gated
            comparisonStatus: "GATED_INCOMPLETE_BENCHMARK_WEIGHTS",
            statusReason: "Benchmark tail weights unweighted. Active weight withheld to prevent artificial distortion.",
            fundHoldingCount: 4,
            benchmarkConstituentCount: 52,
            benchmarkWeightedConstituentCount: 4,
          },
          {
            sectorName: "Information Technology",
            fundWeightPct: 8.50,
            benchmarkDisclosedWeightPct: 6.20,
            definitiveActiveWeightPct: null,
            comparisonStatus: "GATED_INCOMPLETE_BENCHMARK_WEIGHTS",
            statusReason: "Benchmark tail weights unweighted. Active weight withheld to prevent artificial distortion.",
            fundHoldingCount: 2,
            benchmarkConstituentCount: 38,
            benchmarkWeightedConstituentCount: 2,
          },
        ],
        industries: [
          {
            industryName: "Banks",
            parentSectorName: "Financial Services",
            classificationLevel: "NSE_INDUSTRY_LEVEL_2",
            fundWeightPct: 14.83,
            holdingCount: 2,
            classificationSource: "NIFTY_500_CONSTITUENTS",
            classificationProvenance: "BENCHMARK_PIT_CLASSIFICATION",
            effectiveDate: "2024-01-15",
            availabilityTime: "2024-01-15T23:59:59+05:30",
            classificationCoveragePct: 100.0,
            topHoldings: ["ICICI Bank Ltd. (8.80%)", "HDFC Bank Ltd. (6.03%)"],
          },
        ],
        industryComparisons: [
          {
            industryName: "Banks",
            parentSectorName: "Financial Services",
            fundWeightPct: 14.83,
            benchmarkDisclosedWeightPct: 14.83,
            definitiveActiveWeightPct: null,
            comparisonStatus: "GATED_INCOMPLETE_BENCHMARK_WEIGHTS",
            statusReason: "Benchmark constituent tail weights unweighted. Active weight gated.",
            fundHoldingCount: 2,
            benchmarkConstituentCount: 16,
            benchmarkWeightedConstituentCount: 2,
          },
        ],
        securityWaterfall: [
          {
            securityId: 10,
            companyName: "ICICI Bank Ltd.",
            isin: "INE090A01021",
            symbol: "ICICIBANK",
            parentSectorName: "Financial Services",
            industryName: "Banks",
            fundWeightPct: 8.80,
            benchmarkWeightPct: 7.86,
            activeSpreadPct: 0.94,
            exposureCategory: "COMPARABLE_ACTIVE_EXPOSURE",
            membershipStatus: "BENCHMARK_CONSTITUENT_WEIGHTED",
            classificationProvenance: "BENCHMARK_PIT_CLASSIFICATION",
            mappingStatus: "MAPPED_VERIFIED",
            inclusionReason: "Top-weighted benchmark constituent (Factsheet disclosed)",
          },
          {
            securityId: 11,
            companyName: "Federal Bank Ltd.",
            isin: "INE171A01029",
            symbol: "FEDERALBNK",
            parentSectorName: "Financial Services",
            industryName: "Banks",
            fundWeightPct: 1.50,
            benchmarkWeightPct: null,
            activeSpreadPct: null,
            exposureCategory: "BENCHMARK_MEMBER_WEIGHT_UNKNOWN",
            membershipStatus: "BENCHMARK_CONSTITUENT_UNWEIGHTED",
            classificationProvenance: "BENCHMARK_PIT_CLASSIFICATION",
            mappingStatus: "MAPPED_VERIFIED",
            inclusionReason: "NIFTY 500 constituent, weight undisclosed in factsheet (Tail member)",
          },
          {
            securityId: 99,
            companyName: "Unlisted Tech Corp",
            isin: "INE999X01011",
            symbol: null,
            parentSectorName: "Financial Services",
            industryName: "Banks",
            fundWeightPct: 0.75,
            benchmarkWeightPct: 0.0,
            activeSpreadPct: 0.75,
            exposureCategory: "OFF_BENCHMARK_HOLDING",
            membershipStatus: "OFF_BENCHMARK",
            classificationProvenance: "FUND_DISCLOSURE_MAPPED",
            mappingStatus: "MAPPED_VERIFIED",
            inclusionReason: "Verified absent from 501-constituent NIFTY 500 snapshot",
          },
        ],
      },
      {
        portfolioSnapshotId: 102,
        portfolioDate: "2026-09-30",
        portfolioAvailabilityTime: "2026-10-10T12:00:00+05:30",
        portfolioDisclosureScope: "FULL_PORTFOLIO_DISCLOSURE",
        sumFundWeightsPct: 98.45,
        fundHoldingsCount: 77,
        benchmarkId: 100,
        benchmarkCode: "NIFTY_500_TRI",
        benchmarkDate: "2026-09-30",
        benchmarkAvailabilityTime: "2026-10-05T18:00:00+05:30",
        benchmarkWeightCoveragePct: 32.22,
        benchmarkConstituentsCount: 500,
        benchmarkWeightedCount: 10,
        snapshotDateDiffDays: 0,
        isTemporallyComparable: true,
        observationStatus: "GATED_INCOMPLETE_BENCHMARK_WEIGHTS",
        statusReason: "Benchmark disclosed constituent weights cover 32.22% (<99.50% threshold). Active sector weights gated to prevent false overweight distortion.",
        fundClassificationCoveragePct: 99.54,
        sourceArtifactUri: "https://files.hdfcfund.com/snapshot-2026-09.xlsx",
        sourceArtifactSha256: "2959bf206239284e145f7aecc65095b18d11556d2642323a70f2d26efe0f5cb3",
        sectors: [
          {
            sectorName: "Financial Services",
            fundWeightPct: 34.54,
            holdingCount: 12,
            classificationProvenance: "BENCHMARK_PIT_CLASSIFICATION",
            topHoldings: ["ICICI Bank (8.80%)", "Axis Bank (6.11%)", "HDFC Bank (6.03%)"],
          },
        ],
        benchmarkSectors: [
          {
            sectorName: "Financial Services",
            disclosedWeightPct: 14.49,
            weightedConstituentsCount: 4,
            totalConstituentsCount: 55,
            constituentDisclosedCoveragePct: 26.35,
            weightStatus: "PARTIAL_DISCLOSURE_ONLY",
          },
        ],
        sectorComparisons: [
          {
            sectorName: "Financial Services",
            fundWeightPct: 34.54,
            benchmarkDisclosedWeightPct: 14.49,
            definitiveActiveWeightPct: null,
            comparisonStatus: "GATED_INCOMPLETE_BENCHMARK_WEIGHTS",
            statusReason: "Benchmark tail weights unweighted. Active weight withheld to prevent artificial distortion.",
            fundHoldingCount: 12,
            benchmarkConstituentCount: 55,
            benchmarkWeightedConstituentCount: 4,
          },
        ],
        industries: [
          {
            industryName: "Banks",
            parentSectorName: "Financial Services",
            classificationLevel: "NSE_INDUSTRY_LEVEL_2",
            fundWeightPct: 24.50,
            holdingCount: 5,
            classificationSource: "NIFTY_500_CONSTITUENTS",
            classificationProvenance: "BENCHMARK_PIT_CLASSIFICATION",
            effectiveDate: "2026-09-30",
            availabilityTime: "2026-10-05T18:00:00+05:30",
            classificationCoveragePct: 100.0,
            topHoldings: ["ICICI Bank (8.80%)", "Axis Bank (6.11%)", "HDFC Bank (6.03%)"],
          },
        ],
        industryComparisons: [
          {
            industryName: "Banks",
            parentSectorName: "Financial Services",
            fundWeightPct: 24.50,
            benchmarkDisclosedWeightPct: 14.49,
            definitiveActiveWeightPct: null,
            comparisonStatus: "GATED_INCOMPLETE_BENCHMARK_WEIGHTS",
            statusReason: "Benchmark constituent tail weights unweighted. Active weight gated.",
            fundHoldingCount: 5,
            benchmarkConstituentCount: 18,
            benchmarkWeightedConstituentCount: 3,
          },
        ],
        securityWaterfall: [
          {
            securityId: 10,
            companyName: "ICICI Bank Ltd.",
            isin: "INE090A01021",
            symbol: "ICICIBANK",
            parentSectorName: "Financial Services",
            industryName: "Banks",
            fundWeightPct: 8.80,
            benchmarkWeightPct: 7.50,
            activeSpreadPct: 1.30,
            exposureCategory: "COMPARABLE_ACTIVE_EXPOSURE",
            membershipStatus: "BENCHMARK_CONSTITUENT_WEIGHTED",
            classificationProvenance: "BENCHMARK_PIT_CLASSIFICATION",
            mappingStatus: "MAPPED_VERIFIED",
            inclusionReason: "Top-weighted benchmark constituent (Factsheet disclosed)",
          },
          {
            securityId: 50,
            companyName: "Reliance Industries Ltd.",
            isin: "INE002A01018",
            symbol: "RELIANCE",
            parentSectorName: "Energy",
            industryName: "Oil & Gas",
            fundWeightPct: 0.0,
            benchmarkWeightPct: 8.50,
            activeSpreadPct: -8.50,
            exposureCategory: "COMPARABLE_ACTIVE_EXPOSURE",
            membershipStatus: "BENCHMARK_CONSTITUENT_WEIGHTED",
            classificationProvenance: "BENCHMARK_PIT_CLASSIFICATION",
            mappingStatus: "MAPPED_VERIFIED",
            inclusionReason: "Benchmark constituent with disclosed weight, not held in portfolio",
          },
        ],
      },
    ],
    sectorDriftChanges: [
      {
        earlierDate: "2024-01-15",
        laterDate: "2026-09-30",
        sectorName: "Financial Services",
        earlierWeightPct: 22.50,
        laterWeightPct: 34.54,
        deltaWeightPct: 12.04,
        changeDriver: "DISCLOSURE_SCOPE_SHIFT",
        driverExplanation: "Apparent drift influenced by portfolio disclosure scope expansion from TOP_HOLDINGS to FULL_PORTFOLIO.",
        earlierHoldingCount: 4,
        laterHoldingCount: 12,
      },
    ],
    industryDriftChanges: [
      {
        earlierDate: "2024-01-15",
        laterDate: "2026-09-30",
        parentSectorName: "Financial Services",
        industryName: "Banks",
        earlierWeightPct: 14.83,
        laterWeightPct: 24.50,
        deltaWeightPct: 9.67,
        changeDriver: "PORTFOLIO_WEIGHT_ADJUSTMENT",
        driverExplanation: "Net observed change in Banks exposure across reporting periods.",
        earlierHoldingCount: 2,
        laterHoldingCount: 5,
      },
    ],
    missingDataRequirements: [
      "DEP-05: Complete Benchmark Weights (Tail 490 Unweighted in Factsheet)",
    ],
    dataQualityLimitations: [
      "Benchmark constituent weights are only disclosed for the top 10 heavyweights (~32% to 44% weight coverage).",
      "Unweighted constituents are NEVER treated as 0.00% weight.",
      "Changes in sector weight reflect rebalancing, market movements, fund flows, or corporate actions, not solely manager skill.",
    ],
    investigationPrompts: [
      "How much of the apparent +12.04% Financial Services shift is due to disclosure expansion vs true buying?",
      "Are tail benchmark sector constituents missing from the published factsheets?",
    ],
  };

  it("fetchSectorDrift: successfully calls endpoint and deserializes response", async () => {
    globalThis.fetch = async (url: string | URL | Request) => {
      const urlStr = url.toString();
      assert.ok(urlStr.includes("/api/v1/schemes/options/1/sector-drift"));
      assert.ok(urlStr.includes("asOfDate=2026-09-30"));
      assert.ok(urlStr.includes("startDate=2024-01-01"));
      assert.ok(urlStr.includes("knowledgeCutoff="));

      return {
        ok: true,
        status: 200,
        json: async () => mockSectorDriftResponse,
      } as unknown as Response;
    };

    const result = await fetchSectorDrift(1, "2026-09-30", "2024-01-01", "2026-09-30", "2026-10-18T13:00:00+05:30");
    assert.equal(result.schemeOptionId, 1);
    assert.equal(result.benchmarkCode, "NIFTY_500_TRI");
    assert.equal(result.observations.length, 2);
    assert.equal(result.overallStatus, "PARTIAL_SECTOR_VIEW_GATED_BENCHMARK");
  });

  it("fetchSectorDrift: enforces honest gating when benchmark coverage is incomplete", async () => {
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      json: async () => mockSectorDriftResponse,
    } as unknown as Response);

    const result = await fetchSectorDrift(1);
    const obs = result.observations[0];

    assert.equal(obs.observationStatus, "GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
    assert.ok(obs.benchmarkWeightCoveragePct < 99.50);

    // Active weight must be strictly null to avoid misleading investors
    for (const comp of obs.sectorComparisons) {
      assert.equal(comp.definitiveActiveWeightPct, null);
      assert.equal(comp.comparisonStatus, "GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
    }
  });

  it("fetchSectorDrift: preserves lower bound disclosed benchmark weight without assuming tail is zero", async () => {
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      json: async () => mockSectorDriftResponse,
    } as unknown as Response);

    const result = await fetchSectorDrift(1);
    const fsComp = result.observations[0].sectorComparisons.find((c: SectorActiveComparisonDto) => c.sectorName === "Financial Services");

    assert.ok(fsComp);
    assert.equal(fsComp.fundWeightPct, 22.50);
    assert.equal(fsComp.benchmarkDisclosedWeightPct, 18.50);
    assert.equal(fsComp.benchmarkWeightedConstituentCount, 4);
    assert.equal(fsComp.benchmarkConstituentCount, 52);
    // Disclosed weight is 18.50%, but 48 tail stocks are unweighted
    assert.ok(fsComp.benchmarkWeightedConstituentCount < fsComp.benchmarkConstituentCount);
  });

  it("fetchSectorDrift: accurately tracks drift driver disentanglement", async () => {
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      json: async () => mockSectorDriftResponse,
    } as unknown as Response);

    const result = await fetchSectorDrift(1);
    assert.equal(result.sectorDriftChanges.length, 1);
    const change = result.sectorDriftChanges[0];

    assert.equal(change.sectorName, "Financial Services");
    assert.equal(change.changeDriver, "DISCLOSURE_SCOPE_SHIFT");
    assert.equal(change.deltaWeightPct, 12.04);
    assert.ok(change.driverExplanation.includes("scope expansion"));
  });

  it("fetchSectorDrift: deserializes industry drill-down and gates definitive active weight", async () => {
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      json: async () => mockSectorDriftResponse,
    } as unknown as Response);

    const result = await fetchSectorDrift(1);
    const obs = result.observations[0];

    assert.ok(obs.industries && obs.industries.length > 0);
    const bankInd = obs.industries.find((i) => i.industryName === "Banks");
    assert.ok(bankInd);
    assert.equal(bankInd.parentSectorName, "Financial Services");
    assert.equal(bankInd.fundWeightPct, 14.83);
    assert.equal(bankInd.classificationLevel, "NSE_INDUSTRY_LEVEL_2");

    const indComp = obs.industryComparisons?.find((ic) => ic.industryName === "Banks");
    assert.ok(indComp);
    assert.equal(indComp.benchmarkDisclosedWeightPct, 14.83);
    assert.equal(indComp.definitiveActiveWeightPct, null); // Strictly gated!
    assert.equal(indComp.comparisonStatus, "GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
  });

  it("fetchSectorDrift: enforces tri-category security waterfall invariants", async () => {
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      json: async () => mockSectorDriftResponse,
    } as unknown as Response);

    const result = await fetchSectorDrift(1);
    const waterfall = result.observations[0].securityWaterfall;
    assert.ok(waterfall && waterfall.length === 3);

    // Category 1: Comparable Active Exposure
    const comparable = waterfall.find((w) => w.exposureCategory === "COMPARABLE_ACTIVE_EXPOSURE");
    assert.ok(comparable);
    assert.equal(comparable.companyName, "ICICI Bank Ltd.");
    assert.equal(comparable.fundWeightPct, 8.80);
    assert.equal(comparable.benchmarkWeightPct, 7.86);
    assert.equal(comparable.activeSpreadPct, 0.94); // Mathematically valid spread

    // Category 2: Benchmark Member, Weight Unknown
    const unweighted = waterfall.find((w) => w.exposureCategory === "BENCHMARK_MEMBER_WEIGHT_UNKNOWN");
    assert.ok(unweighted);
    assert.equal(unweighted.companyName, "Federal Bank Ltd.");
    assert.equal(unweighted.benchmarkWeightPct, null);
    assert.equal(unweighted.activeSpreadPct, null); // Strictly withheld to prevent zero-fallacy

    // Category 3: Off-Benchmark Holding
    const offBenchmark = waterfall.find((w) => w.exposureCategory === "OFF_BENCHMARK_HOLDING");
    assert.ok(offBenchmark);
    assert.equal(offBenchmark.companyName, "Unlisted Tech Corp");
    assert.equal(offBenchmark.benchmarkWeightPct, 0.0);
    assert.equal(offBenchmark.activeSpreadPct, 0.75); // Genuine off-benchmark active overweight
  });

  it("fetchSectorDrift: surfaces unheld benchmark heavyweights with full active underweights", async () => {
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      json: async () => mockSectorDriftResponse,
    } as unknown as Response);

    const result = await fetchSectorDrift(1);
    const obs2 = result.observations[1];
    const unheldReliance = obs2.securityWaterfall?.find((w) => w.symbol === "RELIANCE");
    assert.ok(unheldReliance);
    assert.equal(unheldReliance.fundWeightPct, 0.0);
    assert.equal(unheldReliance.benchmarkWeightPct, 8.50);
    assert.equal(unheldReliance.activeSpreadPct, -8.50);
    assert.equal(unheldReliance.exposureCategory, "COMPARABLE_ACTIVE_EXPOSURE");
  });

  it("fetchSectorDrift: tracks chronological sub-industry drift changes", async () => {
    globalThis.fetch = async () => ({
      ok: true,
      status: 200,
      json: async () => mockSectorDriftResponse,
    } as unknown as Response);

    const result = await fetchSectorDrift(1);
    assert.ok(result.industryDriftChanges && result.industryDriftChanges.length > 0);
    const bankChange = result.industryDriftChanges.find((c) => c.industryName === "Banks");
    assert.ok(bankChange);
    assert.equal(bankChange.parentSectorName, "Financial Services");
    assert.equal(bankChange.earlierWeightPct, 14.83);
    assert.equal(bankChange.laterWeightPct, 24.50);
    assert.equal(bankChange.deltaWeightPct, 9.67);
    assert.equal(bankChange.changeDriver, "PORTFOLIO_WEIGHT_ADJUSTMENT");
  });

  it("fetchSectorDrift: handles server error gracefully", async () => {
    globalThis.fetch = async () => ({
      ok: false,
      status: 500,
      statusText: "Internal Server Error",
      text: async () => "Internal Server Error",
    } as unknown as Response);

    await assert.rejects(
      async () => {
        await fetchSectorDrift(999);
      },
      {
        message: /Backend request failed: HTTP 500/,
      }
    );
  });
});
