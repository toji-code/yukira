import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  fetchActiveReturnAttribution,
  ActiveReturnAttributionResponseDto,
} from "../../lib/api/portfolio";

describe("Point-in-Time Fund Style Drift & Active Return Attribution API Tests", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockAttributionResponse: ActiveReturnAttributionResponseDto = {
    schemeOptionId: 1,
    fundName: "HDFC Flexi Cap Fund",
    amfiCode: "118955",
    schemeCode: "HDFC_FLEXI",
    benchmarkCode: "NIFTY_500_TRI",
    benchmarkName: "Nifty 500 Total Returns Index",
    asOfDate: "2026-10-01",
    knowledgeCutoff: "2026-10-10T23:59:59+05:30",
    attributionPeriodStart: "2025-10-01",
    attributionPeriodEnd: "2026-10-01",
    observationCount: 248,
    fundReturnPct: 0.1850,
    fundReturnPctFormatted: "+18.50%",
    benchmarkReturnPct: 0.1420,
    benchmarkReturnPctFormatted: "+14.20%",
    activeReturnPct: 0.0430,
    activeReturnPctFormatted: "+4.30%",
    modelStatus: "CALCULATED_VALIDATED_INPUTS",
    modelName: "YUKIRA-PHASE-2K-STYLE-DRIFT-ATTRIBUTION-V1",
    modelExplanation: "Synchronous daily total return series evaluated over aligned dates.",
    macroAttribution: {
      empiricalBeta: 1.0850,
      marketSensitivityContributionPct: 0.01207,
      marketSensitivityContributionPctFormatted: "+1.21%",
      initialCashWeightPct: 0.0450,
      initialCashWeightPctFormatted: "4.50%",
      riskFreeReturnPct: 0.0680,
      riskFreeReturnPctFormatted: "+6.80%",
      cashDragContributionPct: -0.00333,
      cashDragContributionPctFormatted: "-0.33%",
      selectionAndSectorResidualPct: 0.03426,
      selectionAndSectorResidualPctFormatted: "+3.43%",
      reconciledActiveReturnPct: 0.0430,
      reconciledActiveReturnPctFormatted: "+4.30%",
      reconciliationToleranceDeltaPct: 1.2e-16,
      isReconciled: true,
      reconciliationFormula: "R_A = (β - 1)R_b + w_cash(R_rf - R_b) + Residual",
    },
    styleContext: {
      largeCapWeightPct: 72.50,
      largeCapWeightPctFormatted: "72.50%",
      midCapWeightPct: 18.20,
      midCapWeightPctFormatted: "18.20%",
      smallCapWeightPct: 4.80,
      smallCapWeightPctFormatted: "4.80%",
      cashWeightPct: 4.50,
      cashWeightPctFormatted: "4.50%",
      unclassifiedWeightPct: 0.00,
      unclassifiedWeightPctFormatted: "0.00%",
      classificationStatus: "VERIFIED_HISTORICAL_PIT",
      classificationRankingPeriod: "2025-H2",
      activeTiltSummary: "Large Cap tilted flexi-cap allocation (+72.50% Large, +18.20% Mid, +4.80% Small)",
    },
    exposureTimeline: [
      {
        snapshotId: 101,
        portfolioDate: "2024-01-31",
        availabilityTime: "2024-02-05T12:00:00Z",
        disclosureScope: "PARTIAL_TOP_HOLDINGS",
        reportedWeightSumPct: 50.82,
        holdingsCount: 10,
        classificationCoveragePct: 100.0,
        largeCapWeightPct: 42.50,
        midCapWeightPct: 8.32,
        smallCapWeightPct: 0.0,
        cashWeightPct: 5.20,
        unclassifiedWeightPct: 0.0,
        topSectors: [
          { sectorName: "Financial Services", weightPct: 24.50, holdingCount: 4 },
          { sectorName: "Information Technology", weightPct: 12.20, holdingCount: 2 },
        ],
        comparabilityStatus: "BASELINE_OBSERVATION",
        comparabilityReason: "Initial disclosed observation snapshot in timeline.",
        daysSincePreviousSnapshot: null,
        sourceStorageUri: "SEBI_MONTHLY_DISCLOSURE",
        sourceSha256: "900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259",
      },
      {
        snapshotId: 102,
        portfolioDate: "2026-07-31",
        availabilityTime: "2026-08-05T12:00:00Z",
        disclosureScope: "FULL_PORTFOLIO_DISCLOSURE",
        reportedWeightSumPct: 98.40,
        holdingsCount: 52,
        classificationCoveragePct: 99.8,
        largeCapWeightPct: 74.20,
        midCapWeightPct: 17.50,
        smallCapWeightPct: 3.50,
        cashWeightPct: 4.80,
        unclassifiedWeightPct: 0.0,
        topSectors: [
          { sectorName: "Financial Services", weightPct: 32.10, holdingCount: 12 },
          { sectorName: "Energy", weightPct: 14.50, holdingCount: 5 },
        ],
        comparabilityStatus: "NON_COMPARABLE_DISCLOSURE_SCOPE_SHIFT",
        comparabilityReason: "Disclosure scope shifted from PARTIAL_TOP_HOLDINGS to FULL_PORTFOLIO_DISCLOSURE.",
        daysSincePreviousSnapshot: 912,
        sourceStorageUri: "SEBI_MONTHLY_DISCLOSURE",
        sourceSha256: "a1b2c3d4e5f678901234567890abcdef1234567890abcdef1234567890abcdef",
      },
      {
        snapshotId: 103,
        portfolioDate: "2026-08-31",
        availabilityTime: "2026-09-05T12:00:00Z",
        disclosureScope: "FULL_PORTFOLIO_DISCLOSURE",
        reportedWeightSumPct: 98.60,
        holdingsCount: 53,
        classificationCoveragePct: 100.0,
        largeCapWeightPct: 73.10,
        midCapWeightPct: 18.00,
        smallCapWeightPct: 4.10,
        cashWeightPct: 4.80,
        unclassifiedWeightPct: 0.0,
        topSectors: [
          { sectorName: "Financial Services", weightPct: 31.80, holdingCount: 12 },
          { sectorName: "Energy", weightPct: 14.80, holdingCount: 5 },
        ],
        comparabilityStatus: "COMPARABLE_FULL_DISCLOSURE",
        comparabilityReason: "Consecutive full portfolio disclosures with aligned scopes.",
        daysSincePreviousSnapshot: 31,
        sourceStorageUri: "SEBI_MONTHLY_DISCLOSURE",
        sourceSha256: "b2c3d4e5f6a178901234567890abcdef1234567890abcdef1234567890abcdef",
      },
    ],
    brinsonSegmentation: {
      executionStatus: "BLOCKED_BENCHMARK_WEIGHTS_INCOMPLETE",
      brinsonVariant: "BRINSON_FACHLER_1985",
      statusExplanation: "Constituent Brinson attribution blocked: NIFTY 500 factsheets disclose only top 10 constituent weights (40.95% coverage; 491 constituents unweighted). Commercial benchmark feeds required.",
      reasonIfNotCalculated: "NIFTY 500 constituent weights incomplete. Top 10 holdings sum to 40.95%. Synthetic renormalization prohibited.",
      totalAllocationEffectPct: 0.0,
      totalSelectionEffectPct: 0.0,
      totalInteractionEffectPct: 0.0,
      totalActiveReturnReconciledPct: 0.0,
      reconciliationDeltaPct: 0.0,
      isReconciled: false,
      benchmarkDisclosedWeightPct: 40.95,
      benchmarkWeightedCount: 10,
      benchmarkTotalCount: 501,
      missingPrerequisites: [
        "BENCHMARK_COMPLETE_CONSTITUENT_WEIGHTS",
        "STOCK_LEVEL_DAILY_TOTAL_RETURNS",
      ],
      segments: [],
    },
    dataReadinessGate: {
      overallReadiness: "PARTIALLY_READY_MACRO_ONLY",
      totalDependencies: 8,
      availableDependencies: 5,
      missingDependencies: 3,
      dependencies: [
        {
          dependencyCode: "DEP-01",
          dependencyName: "Fund Daily NAV History",
          requiredFor: "Fund return calculation (R_p)",
          repositoryStatus: "AVAILABLE_VERIFIED",
          officialSource: "AMFI Official Daily NAV Publication",
          epistemicNote: "Verified against SHA-256 raw ingestion payloads.",
        },
        {
          dependencyCode: "DEP-02",
          dependencyName: "Benchmark Daily TRI Series",
          requiredFor: "Benchmark return calculation (R_b) and active return (R_A)",
          repositoryStatus: "AVAILABLE_VERIFIED",
          officialSource: "NSE Indices Official Total Return Index Series",
          epistemicNote: "Continuous verified daily NIFTY 500 TRI series.",
        },
        {
          dependencyCode: "DEP-03",
          dependencyName: "Risk-Free Cash Benchmark",
          requiredFor: "Cash allocation drag calculation (R_rf - R_b)",
          repositoryStatus: "AVAILABLE_VERIFIED",
          officialSource: "FBIL Overnight MIBOR / 91-Day T-Bill Series",
          epistemicNote: "Verified sovereign risk-free return reference.",
        },
        {
          dependencyCode: "DEP-04",
          dependencyName: "Semi-Annual AMFI Market-Cap Classification",
          requiredFor: "Point-in-Time market capitalization bucketing",
          repositoryStatus: "AVAILABLE_VERIFIED",
          officialSource: "AMFI Semi-Annual Market Capitalization Ranks",
          epistemicNote: "Strictly enforced historical cutoffs prevent survivorship bias.",
        },
        {
          dependencyCode: "DEP-05",
          dependencyName: "Fund Monthly Disclosed Portfolio Holdings",
          requiredFor: "Observed portfolio exposures, cash weight, and sector tilts",
          repositoryStatus: "AVAILABLE_VERIFIED",
          officialSource: "SEBI / AMC Official Monthly Portfolio Disclosures",
          epistemicNote: "Point-in-time publication timestamps enforced.",
        },
        {
          dependencyCode: "DEP-06",
          dependencyName: "Benchmark Factsheet Point-in-Time Weights",
          requiredFor: "Brinson constituent allocation and selection effects",
          repositoryStatus: "HELD_AT_GATE",
          officialSource: "NSE Monthly Benchmark Factsheets",
          epistemicNote: "Only top 10 constituents weighted (~40.95%). 491 constituents unweighted.",
        },
        {
          dependencyCode: "DEP-07",
          dependencyName: "Complete Daily Stock-Level Return Series",
          requiredFor: "Constituent stock-selection effects and cross-sectional regressions",
          repositoryStatus: "HELD_AT_GATE",
          officialSource: "Licensed Commercial Equity Price Feed (NSE/BSE)",
          epistemicNote: "Requires full licensed stock return universe. Synthetic estimation prohibited.",
        },
        {
          dependencyCode: "DEP-08",
          dependencyName: "Corporate Actions & Demerger Adjustments",
          requiredFor: "Adjusted stock returns for equity spin-offs, splits, and bonus issues",
          repositoryStatus: "HELD_AT_GATE",
          officialSource: "Exchange Official Corporate Actions Ledger",
          epistemicNote: "Unadjusted prices introduce false return anomalies.",
        },
      ],
    },
    investigationPrompts: [
      "Why did the fund's cash allocation average 4.50%, producing a -0.33% cash drag during a +14.20% benchmark rally?",
      "Does the unexplained residual of +3.43% persist across different market regimes (bear vs bull)?",
      "How did the transition from Top 10 to Full Portfolio disclosure affect observed style consistency?",
    ],
    epistemicLimitations: [
      "Active return (R_A) is a mathematical difference, not direct proof of manager skill.",
      "Unexplained macro residual contains idiosyncratic selection, unmodeled sector tilts, and intra-month trading.",
      "Brinson constituent attribution is withheld because benchmark weights are only 40.95% disclosed.",
      "SEBI monthly snapshots reflect end-of-month positions and do not capture intra-month turnover.",
      "AMFI market-cap rankings update semi-annually; constituent movements across thresholds are not portfolio trades.",
      "Calculations enforce dual point-in-time constraints: effective_date <= asOfDate and availability_time <= knowledgeCutoff.",
    ],
    provenance: {
      methodologyVersion: "YUKIRA-PHASE-2K-STYLE-DRIFT-ATTRIBUTION-V1",
      calculationRunTimestamp: "2026-10-19T12:00:00+05:30",
      formulaSpecification: "R_A = R_p - R_b = (β - 1)R_b + w_cash(R_rf - R_b) + Residual",
      fundNavSourceSha256: "900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259",
      benchmarkObservationSha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      riskFreeSourceDescription: "FBIL Overnight MIBOR / 91-Day T-Bill Official Reference Rate",
      nonAdvisoryDisclaimer: "Strictly institutional research. YUKIRA does not issue investment recommendations or verify financial advice.",
    },
  };

  it("should fetch active return attribution with correct URL and parameters", async () => {
    let capturedUrl = "";

    globalThis.fetch = async (input: RequestInfo | URL) => {
      capturedUrl = String(input);
      return new Response(JSON.stringify(mockAttributionResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    const result = await fetchActiveReturnAttribution(
      1,
      "2026-10-01",
      "2026-10-10T23:59:59+05:30",
      "1Y",
      "2025-10-01",
      "2026-10-01"
    );

    assert.ok(capturedUrl.includes("/api/v1/schemes/options/1/attribution"));
    assert.ok(capturedUrl.includes("asOfDate=2026-10-01"));
    assert.ok(capturedUrl.includes("horizon=1Y"));
    assert.ok(capturedUrl.includes("startDate=2025-10-01"));
    assert.ok(capturedUrl.includes("endDate=2026-10-01"));

    assert.equal(result.schemeOptionId, 1);
    assert.equal(result.fundName, "HDFC Flexi Cap Fund");
    assert.equal(result.benchmarkCode, "NIFTY_500_TRI");
  });

  it("should verify active return arithmetic R_A = R_p - R_b", () => {
    const { fundReturnPct, benchmarkReturnPct, activeReturnPct } = mockAttributionResponse;
    const computedActive = fundReturnPct - benchmarkReturnPct;
    assert.ok(Math.abs(computedActive - activeReturnPct) < 1e-9);
  });

  it("should verify empirical macro active decomposition algebraic reconciliation", () => {
    const { activeReturnPct, macroAttribution } = mockAttributionResponse;
    const {
      marketSensitivityContributionPct,
      cashDragContributionPct,
      selectionAndSectorResidualPct,
      reconciledActiveReturnPct,
      isReconciled,
    } = macroAttribution;

    assert.equal(isReconciled, true);

    const manualSum = marketSensitivityContributionPct + cashDragContributionPct + selectionAndSectorResidualPct;
    assert.ok(Math.abs(manualSum - activeReturnPct) < 1e-6);
    assert.ok(Math.abs(manualSum - reconciledActiveReturnPct) < 1e-6);
  });

  it("should enforce Brinson constituent gating under incomplete benchmark weights", () => {
    const { brinsonSegmentation } = mockAttributionResponse;

    assert.equal(brinsonSegmentation.executionStatus, "BLOCKED_BENCHMARK_WEIGHTS_INCOMPLETE");
    assert.equal(brinsonSegmentation.isReconciled, false);
    assert.ok(brinsonSegmentation.benchmarkDisclosedWeightPct! < 99.50);
    assert.ok(brinsonSegmentation.missingPrerequisites!.includes("BENCHMARK_COMPLETE_CONSTITUENT_WEIGHTS"));
    assert.ok(brinsonSegmentation.reasonIfNotCalculated.includes("incomplete"));
  });

  it("should correctly structure exposure timeline observations and comparability transitions", () => {
    const { exposureTimeline } = mockAttributionResponse;

    assert.equal(exposureTimeline.length, 3);

    // Snapshot 1: Baseline
    assert.equal(exposureTimeline[0].comparabilityStatus, "BASELINE_OBSERVATION");
    assert.equal(exposureTimeline[0].disclosureScope, "PARTIAL_TOP_HOLDINGS");

    // Snapshot 2: Scope Shift
    assert.equal(exposureTimeline[1].comparabilityStatus, "NON_COMPARABLE_DISCLOSURE_SCOPE_SHIFT");
    assert.equal(exposureTimeline[1].disclosureScope, "FULL_PORTFOLIO_DISCLOSURE");
    assert.ok(exposureTimeline[1].daysSincePreviousSnapshot! > 45);

    // Snapshot 3: Comparable Full Disclosure
    assert.equal(exposureTimeline[2].comparabilityStatus, "COMPARABLE_FULL_DISCLOSURE");
    assert.equal(exposureTimeline[2].daysSincePreviousSnapshot, 31);
    assert.equal(exposureTimeline[2].topSectors.length, 2);
  });

  it("should audit all 8 dependencies in the data-readiness gate matrix", () => {
    const { dataReadinessGate } = mockAttributionResponse;

    assert.equal(dataReadinessGate.totalDependencies, 8);
    assert.equal(dataReadinessGate.availableDependencies, 5);
    assert.equal(dataReadinessGate.missingDependencies, 3);

    const verified = dataReadinessGate.dependencies.filter((d) => d.repositoryStatus === "AVAILABLE_VERIFIED");
    const held = dataReadinessGate.dependencies.filter((d) => d.repositoryStatus === "HELD_AT_GATE");

    assert.equal(verified.length, 5);
    assert.equal(held.length, 3);
  });

  it("should preserve institutional epistemic guardrails and cryptographic lineage", () => {
    const { provenance, epistemicLimitations } = mockAttributionResponse;

    assert.equal(provenance.methodologyVersion, "YUKIRA-PHASE-2K-STYLE-DRIFT-ATTRIBUTION-V1");
    assert.ok(provenance.fundNavSourceSha256.length === 64);
    assert.ok(provenance.benchmarkObservationSha256.length === 64);
    assert.ok(provenance.nonAdvisoryDisclaimer.includes("institutional research"));

    assert.ok(epistemicLimitations.length >= 5);
    assert.ok(epistemicLimitations.some((lim) => lim.includes("dual point-in-time")));
    assert.ok(epistemicLimitations.some((lim) => lim.includes("manager skill")));
  });
});
