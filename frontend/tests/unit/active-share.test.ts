import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  fetchActiveShareTrajectory,
  ActiveShareTrajectoryResponseDto,
  fetchBenchmarkFactsheetArchive,
  BenchmarkFactsheetArchiveResponseDto,
} from "../../lib/api/portfolio";

describe("Point-in-Time Active Share & Off-Benchmark Trajectory Unit Tests", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockActiveShareResponse: ActiveShareTrajectoryResponseDto = {
    schemeOptionId: 1,
    fundName: "HDFC Flexi Cap Fund",
    planType: "DIRECT",
    optionType: "GROWTH",
    benchmarkId: 123,
    benchmarkCode: "NIFTY_500_TRI",
    benchmarkName: "Nifty 500 Total Returns Index",
    overallStatus: "GATED_INCOMPLETE_BENCHMARK_WEIGHTS",
    statusReason:
      "Benchmark constituent weights are only partially disclosed (~32.22% coverage for top-10 constituents). Definitive Active Share is gated to prevent artificial zero-tail distortion.",
    methodologyVersion: "YUKIRA-AS-1.0 (Phase 2K Deterministic)",
    calculationRunId: "test-run-12345",
    knowledgeCutoff: "2026-10-10T23:59:59+05:30",
    totalSnapshotsEvaluated: 4,
    eligibleObservationsCount: 4,
    skippedPeriodsCount: 0,
    missingDataRequirements: [
      "Full constituent-level weights for remaining ~490 constituents of NIFTY 500 TRI.",
      "Contemporary benchmark constituent snapshots aligned with each monthly fund portfolio disclosure date.",
    ],
    observations: [
      {
        portfolioDate: "2026-06-30",
        portfolioAvailabilityTime: "2026-07-10T18:29:59Z",
        portfolioSourceArtifactId: 8314,
        benchmarkDate: "2026-09-30",
        benchmarkAvailabilityTime: "2026-10-08T14:41:02Z",
        snapshotDateDiffDays: 92,
        isTemporallyComparable: false,
        activeSharePct: null,
        activeShareLowerBoundPct: 24.18,
        offBenchmarkAllocationPct: 35.20,
        membershipOverlapPct: 82.50,
        fundHoldingsCount: 52,
        benchmarkConstituentsCount: 500,
        portfolioWeightCoveragePct: 98.40,
        benchmarkWeightCoveragePct: 32.22,
        benchmarkMembershipCoveragePct: 100.0,
        securityMappingCoveragePct: 100.0,
        status: "UNVERIFIED_SOURCE",
        statusReason: "Temporal mismatch: Benchmark observation is 92 days from portfolio disclosure.",
        constituentInputs: [
          {
            isin: "INE040A01034",
            securityName: "HDFC Bank Ltd.",
            portfolioWeightPct: 8.50,
            benchmarkWeightPct: 9.80,
            isBenchmarkMember: true,
            absoluteDiscrepancyPct: 1.30,
            mappingStatus: "VERIFIED",
            marketCapCategory: "LARGE_CAP",
          },
          {
            isin: "INE999X01099",
            securityName: "Off-Benchmark Global Tech",
            portfolioWeightPct: 2.10,
            benchmarkWeightPct: null,
            isBenchmarkMember: false,
            absoluteDiscrepancyPct: 2.10,
            mappingStatus: "VERIFIED",
            marketCapCategory: "MID_CAP",
          },
        ],
      },
      {
        portfolioDate: "2026-09-30",
        portfolioAvailabilityTime: "2026-10-10T18:29:59Z",
        portfolioSourceArtifactId: 8311,
        benchmarkDate: "2026-09-30",
        benchmarkAvailabilityTime: "2026-10-08T14:41:02Z",
        snapshotDateDiffDays: 0,
        isTemporallyComparable: true,
        activeSharePct: null, // Gated due to partial benchmark weights!
        activeShareLowerBoundPct: 26.40,
        offBenchmarkAllocationPct: 34.80,
        membershipOverlapPct: 83.10,
        fundHoldingsCount: 54,
        benchmarkConstituentsCount: 500,
        portfolioWeightCoveragePct: 98.60,
        benchmarkWeightCoveragePct: 32.22,
        benchmarkMembershipCoveragePct: 100.0,
        securityMappingCoveragePct: 100.0,
        status: "GATED_INCOMPLETE_BENCHMARK_WEIGHTS",
        statusReason: "Benchmark constituent weights are only partially disclosed (32.22% coverage).",
        constituentInputs: [],
      },
    ],
    apparentChanges: [
      {
        earlierDate: "2026-08-31",
        laterDate: "2026-09-30",
        changeCategory: "PORTFOLIO_WEIGHT_CHANGE",
        isin: "INE040A01034",
        securityName: "HDFC Bank Ltd.",
        description: "Reported holding weight moved from 8.20% to 8.50% (+0.30%).",
        priorWeightPct: 8.20,
        laterWeightPct: 8.50,
        weightImpactPct: 0.30,
        sourceEvidence: "Fund Monthly Disclosed Portfolio Holdings",
        epistemicCaution:
          "Holding-weight shifts reflect combined trading, price movements, corporate actions, and flows; do not interpret as pure manager trading.",
      },
      {
        earlierDate: "2026-08-31",
        laterDate: "2026-09-30",
        changeCategory: "BENCHMARK_MEMBERSHIP_CHANGE",
        isin: "INE123A01011",
        securityName: "New Entrant Ltd.",
        description: "Constituent entered benchmark index (NIFTY_500_TRI) universe.",
        priorWeightPct: 0.0,
        laterWeightPct: 0.50,
        weightImpactPct: 0.50,
        sourceEvidence: "NSE Indices Official Index Reconstitution Ledger",
        epistemicCaution:
          "Index membership revisions shift portfolio off-benchmark classification without any fund manager trading action.",
      },
      {
        earlierDate: "2026-08-31",
        laterDate: "2026-09-30",
        changeCategory: "CLASSIFICATION_CHANGE",
        isin: "INE226A01021",
        securityName: "Voltas Ltd.",
        description: "AMFI semi-annual market-cap classification changed: MID_CAP -> LARGE_CAP.",
        priorWeightPct: 2.10,
        laterWeightPct: 2.10,
        weightImpactPct: 2.10,
        sourceEvidence: "AMFI Semi-Annual Market Capitalization Ranking",
        epistemicCaution:
          "Classification shifts alter market-cap bucket allocations independent of portfolio weight changes.",
      },
      {
        earlierDate: "2026-08-31",
        laterDate: "2026-09-30",
        changeCategory: "COVERAGE_IDENTITY_CHANGE",
        isin: "INE555A01055",
        securityName: "Reconciled Security Ltd.",
        description: "Security ISIN verified against official NSDL master.",
        priorWeightPct: 0.80,
        laterWeightPct: 0.80,
        weightImpactPct: 0.80,
        sourceEvidence: "Security Identifier Resolution & Lineage Audit",
        epistemicCaution: "Coverage change reflects data quality improvements, not market movements.",
      },
      {
        earlierDate: "2026-08-31",
        laterDate: "2026-09-30",
        changeCategory: "UNRESOLVED_CHANGE",
        isin: "INE777A01077",
        securityName: "Anomalous Entry Ltd.",
        description: "Uncorroborated classification gap.",
        priorWeightPct: 0.30,
        laterWeightPct: 0.30,
        weightImpactPct: 0.30,
        sourceEvidence: "Point-in-Time Discrepancy Ledger",
        epistemicCaution: "Evidence cannot distinguish cause.",
      },
    ],
    returnAssociations: [
      {
        periodStartDate: "2026-08-31",
        periodEndDate: "2026-09-30",
        startOffBenchmarkAllocationPct: 35.10,
        endOffBenchmarkAllocationPct: 34.80,
        fundReturnPct: 3.20,
        benchmarkReturnPct: 2.10,
        activeReturnPct: 1.10,
        sampleDays: 30,
        associationLabel: "DESCRIPTIVE_ASSOCIATION_ONLY",
        methodologicalCaveat:
          "Non-causal empirical association. Off-benchmark exposure does not generate alpha or establish manager skill without controlled econometric verification.",
      },
    ],
    epistemicDisclaimers: [
      "Active Share is gated when benchmark weights are partially disclosed to avoid deceptive estimates.",
      "Off-benchmark allocation is derived solely from verified benchmark membership records.",
      "Statistical associations between off-benchmark allocation and active returns are strictly descriptive and non-causal.",
    ],
  };

  it("fetchActiveShareTrajectory calls the correct endpoint with query parameters", async () => {
    let capturedUrl = "";
    globalThis.fetch = (async (url: string | URL | Request) => {
      capturedUrl = url.toString();
      return {
        ok: true,
        status: 200,
        json: async () => mockActiveShareResponse,
      } as Response;
    }) as unknown as typeof fetch;

    const result = await fetchActiveShareTrajectory(
      1,
      "2026-10-01",
      "2026-06-01",
      "2026-09-30",
      "2026-10-10T23:59:59+05:30"
    );

    assert.ok(capturedUrl.includes("/api/v1/schemes/options/1/active-share"));
    assert.ok(capturedUrl.includes("asOfDate=2026-10-01"));
    assert.ok(capturedUrl.includes("startDate=2026-06-01"));
    assert.ok(capturedUrl.includes("endDate=2026-09-30"));
    assert.ok(capturedUrl.includes("knowledgeCutoff=2026-10-10T23%3A59%3A59%2B05%3A30"));
    assert.equal(result.schemeOptionId, 1);
    assert.equal(result.overallStatus, "GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
  });

  it("strictly gates definitive Active Share when benchmark weights are partially disclosed", async () => {
    globalThis.fetch = (async () => ({
      ok: true,
      status: 200,
      json: async () => mockActiveShareResponse,
    })) as unknown as typeof fetch;

    const result = await fetchActiveShareTrajectory(1);

    assert.equal(result.overallStatus, "GATED_INCOMPLETE_BENCHMARK_WEIGHTS");
    assert.equal(result.observations[1].activeSharePct, null); // Strictly null!
    assert.equal(result.observations[1].activeShareLowerBoundPct, 26.40);
    assert.equal(result.observations[1].benchmarkWeightCoveragePct, 32.22);
    assert.ok(result.missingDataRequirements.length > 0);
  });

  it("deserializes and preserves all 5 separate categories of apparent changes", async () => {
    globalThis.fetch = (async () => ({
      ok: true,
      status: 200,
      json: async () => mockActiveShareResponse,
    })) as unknown as typeof fetch;

    const result = await fetchActiveShareTrajectory(1);

    const categories = new Set(result.apparentChanges.map((c) => c.changeCategory));
    assert.ok(categories.has("PORTFOLIO_WEIGHT_CHANGE"));
    assert.ok(categories.has("BENCHMARK_MEMBERSHIP_CHANGE"));
    assert.ok(categories.has("CLASSIFICATION_CHANGE"));
    assert.ok(categories.has("COVERAGE_IDENTITY_CHANGE"));
    assert.ok(categories.has("UNRESOLVED_CHANGE"));

    // Verify non-causal epistemic caution is present on every change record
    result.apparentChanges.forEach((change) => {
      assert.ok(change.epistemicCaution.length > 0);
    });
  });

  it("deserializes descriptive return association with strict non-causal label", async () => {
    globalThis.fetch = (async () => ({
      ok: true,
      status: 200,
      json: async () => mockActiveShareResponse,
    })) as unknown as typeof fetch;

    const result = await fetchActiveShareTrajectory(1);

    assert.equal(result.returnAssociations.length, 1);
    const assoc = result.returnAssociations[0];
    assert.equal(assoc.associationLabel, "DESCRIPTIVE_ASSOCIATION_ONLY");
    assert.equal(assoc.fundReturnPct, 3.20);
    assert.equal(assoc.benchmarkReturnPct, 2.10);
    assert.equal(assoc.activeReturnPct, 1.10);
    assert.ok(assoc.methodologicalCaveat.includes("Non-causal"));
  });

  it("preserves inspectable constituent-level inputs for historical observations", async () => {
    globalThis.fetch = (async () => ({
      ok: true,
      status: 200,
      json: async () => mockActiveShareResponse,
    })) as unknown as typeof fetch;

    const result = await fetchActiveShareTrajectory(1);

    const obs = result.observations[0];
    assert.equal(obs.constituentInputs.length, 2);

    const inBmConstituent = obs.constituentInputs.find((c) => c.isBenchmarkMember);
    assert.ok(inBmConstituent);
    assert.equal(inBmConstituent.isin, "INE040A01034");
    assert.equal(inBmConstituent.portfolioWeightPct, 8.50);
    assert.equal(inBmConstituent.benchmarkWeightPct, 9.80);
    assert.equal(inBmConstituent.absoluteDiscrepancyPct, 1.30);

    const offBmConstituent = obs.constituentInputs.find((c) => !c.isBenchmarkMember);
    assert.ok(offBmConstituent);
    assert.equal(offBmConstituent.benchmarkWeightPct, null);
    assert.equal(offBmConstituent.absoluteDiscrepancyPct, 2.10);
  });

  const mockFactsheetArchiveResponse: BenchmarkFactsheetArchiveResponseDto = {
    benchmarkId: 100,
    benchmarkCode: "NIFTY_500_TRI",
    benchmarkName: "Nifty 500 Total Returns Index",
    publisher: "NSE Indices Limited",
    methodologyVersion: "YUKIRA-BMA-1.0 (Phase 2K PIT Factsheet Archive)",
    knowledgeCutoff: "2026-10-18T23:59:59+05:30",
    totalSnapshotsArchived: 4,
    snapshots: [
      {
        effectiveDate: "2019-09-30",
        availabilityTime: "2019-10-01T00:00:00+05:30",
        sourceArtifactId: 8101,
        sourceArtifactSha256: "2b150cd1ed08cc6eaef87e2687103ae112eea3a2a1ce211828cc3a5bb12f83ae",
        sourceStorageUri: "classpath:seeds/benchmark/ind_nifty500list_historical.csv",
        sourcePublisher: "NSE Indices Limited (Historical Archive)",
        totalConstituentsCount: 501,
        weightedConstituentsCount: 8,
        unknownWeightConstituentsCount: 493,
        disclosedWeightSumPct: 37.10,
        membershipCoveragePct: 100.0,
        identifierMappingCoveragePct: 98.40,
        weightCoveragePct: 37.10,
        membershipCoverageStatus: "VERIFIED_COMPLETE",
        mappingCoverageStatus: "VERIFIED_COMPLETE",
        weightCoverageStatus: "INSUFFICIENT_DATA",
        sourceConfidenceStatus: "VERIFIED_HISTORICAL_ARCHIVE",
        unmetDataDependencies: "DEP-05: NSE historical weights beyond top constituents require commercial index licensing.",
        notes: "Pre-merger baseline containing Allahabad Bank and HDFC Ltd.",
      },
      {
        effectiveDate: "2024-01-31",
        availabilityTime: "2024-02-07T23:39:33+05:30",
        sourceArtifactId: 8102,
        sourceArtifactSha256: "0189acf6590bc224688c1e74bdc249fe867efd159791b01a86477c048a2e5e71",
        sourceStorageUri: "classpath:seeds/benchmark/ind_nifty500list_20240207.csv",
        sourcePublisher: "NSE Indices Limited (Official Index Factsheet)",
        totalConstituentsCount: 501,
        weightedConstituentsCount: 10,
        unknownWeightConstituentsCount: 491,
        disclosedWeightSumPct: 39.80,
        membershipCoveragePct: 100.0,
        identifierMappingCoveragePct: 100.0,
        weightCoveragePct: 39.80,
        membershipCoverageStatus: "VERIFIED_COMPLETE",
        mappingCoverageStatus: "VERIFIED_COMPLETE",
        weightCoverageStatus: "INSUFFICIENT_DATA",
        sourceConfidenceStatus: "VERIFIED_OFFICIAL_NSE",
        unmetDataDependencies: "DEP-05: Tail ~490 constituent weights undisclosed in public factsheet.",
        notes: "Authentic early 2024 constituent universe directly aligned with Jan 2024 fund portfolio.",
      },
    ],
    historicalCoverageGaps: [
      "2020-2023: Monthly factsheet archives remain unsupported pending public archive retrieval.",
      "Weights for non-top-10 constituents require commercial license (DEP-05).",
    ],
    governanceDisclosures: [
      "All benchmark snapshots are evaluated with Point-in-Time availability cutoffs.",
      "Unknown weights are kept strictly null; no fake weights are fabricated.",
    ],
  };

  it("fetchBenchmarkFactsheetArchive calls the correct endpoint with query parameters", async () => {
    let capturedUrl = "";
    globalThis.fetch = (async (url: string | URL | Request) => {
      capturedUrl = url.toString();
      return {
        ok: true,
        status: 200,
        json: async () => mockFactsheetArchiveResponse,
      } as Response;
    }) as unknown as typeof fetch;

    const result = await fetchBenchmarkFactsheetArchive("NIFTY_500_TRI", "2026-10-18T23:59:59+05:30");

    assert.ok(capturedUrl.includes("/api/v1/benchmarks/NIFTY_500_TRI/factsheet-archive"));
    assert.ok(capturedUrl.includes("knowledgeCutoff=2026-10-18T23%3A59%3A59%2B05%3A30"));
    assert.equal(result.benchmarkCode, "NIFTY_500_TRI");
    assert.equal(result.totalSnapshotsArchived, 4);
    assert.equal(result.snapshots.length, 2);
  });

  it("deserializes and preserves 5 independent coverage dimensions for each benchmark factsheet snapshot", async () => {
    globalThis.fetch = (async () => ({
      ok: true,
      status: 200,
      json: async () => mockFactsheetArchiveResponse,
    })) as unknown as typeof fetch;

    const result = await fetchBenchmarkFactsheetArchive("NIFTY_500_TRI");

    const snap2024 = result.snapshots.find((s) => s.effectiveDate === "2024-01-31");
    assert.ok(snap2024);

    // 1. Membership coverage
    assert.equal(snap2024.totalConstituentsCount, 501);
    assert.equal(snap2024.membershipCoveragePct, 100.0);
    assert.equal(snap2024.membershipCoverageStatus, "VERIFIED_COMPLETE");

    // 2. Identifier mapping coverage
    assert.equal(snap2024.identifierMappingCoveragePct, 100.0);
    assert.equal(snap2024.mappingCoverageStatus, "VERIFIED_COMPLETE");

    // 3. Weight coverage (separating disclosed sum and unweighted count)
    assert.equal(snap2024.weightedConstituentsCount, 10);
    assert.equal(snap2024.unknownWeightConstituentsCount, 491);
    assert.equal(snap2024.disclosedWeightSumPct, 39.80);
    assert.equal(snap2024.weightCoverageStatus, "INSUFFICIENT_DATA");

    // 4. Source confidence & provenance
    assert.equal(snap2024.sourceConfidenceStatus, "VERIFIED_OFFICIAL_NSE");
    assert.equal(snap2024.sourceArtifactSha256, "0189acf6590bc224688c1e74bdc249fe867efd159791b01a86477c048a2e5e71");

    // 5. Unmet dependencies & gaps
    assert.ok(snap2024.unmetDataDependencies.includes("DEP-05"));
    assert.ok(result.historicalCoverageGaps.length > 0);
  });
});

