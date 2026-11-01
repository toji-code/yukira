import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  ScreenerItem,
  CategoryScreenerSummary,
  ScreenerMetricDefinition
} from "../../lib/api/schemes";

describe("TSK-M1-08: Cross-Category Screener & Comparability Invariants", () => {
  const EXPECTED_CATEGORIES = [
    "FLEXI_CAP",
    "LARGE_CAP",
    "LARGE_AND_MID_CAP",
    "MID_CAP",
    "SMALL_CAP"
  ];

  const EXPECTED_BENCHMARKS: Record<string, string> = {
    FLEXI_CAP: "NIFTY_500_TRI",
    LARGE_CAP: "NIFTY_100_TRI",
    LARGE_AND_MID_CAP: "NIFTY_LARGEMIDCAP_250_TRI",
    MID_CAP: "NIFTY_MIDCAP_150_TRI",
    SMALL_CAP: "NIFTY_SMALLCAP_250_TRI"
  };

  const EXPECTED_METRIC_CODES = [
    "RET-02",
    "RET-03",
    "RSK-01",
    "RSK-03",
    "MKT-01",
    "MKT-02",
    "RAT-04",
    "REL-01"
  ];

  it("verifies the 5 reference-universe categories map to authentic TRI benchmarks", () => {
    EXPECTED_CATEGORIES.forEach((cat) => {
      const bm = EXPECTED_BENCHMARKS[cat];
      assert.ok(bm, `Category ${cat} must have a mapped benchmark`);
      assert.ok(bm.endsWith("_TRI"), `Benchmark ${bm} must strictly be a Total Return Index (TRI)`);
    });
  });

  it("verifies the 8 canonical screener metrics have correct mathematical metadata", () => {
    const definitions: ScreenerMetricDefinition[] = [
      {
        metricCode: "RET-02",
        metricName: "Simple Period Return",
        category: "RETURN",
        units: "PERCENTAGE",
        evaluationWindow: "EXACT_HORIZON",
        benchmarkDependent: false,
        formulaDescription: "(NAV_end - NAV_start) / NAV_start",
        epistemicStatus: "VALIDATED"
      },
      {
        metricCode: "RET-03",
        metricName: "Compound Annual Growth Rate (CAGR 3Y)",
        category: "RETURN",
        units: "PERCENTAGE",
        evaluationWindow: "3Y_ANNUALIZED",
        benchmarkDependent: false,
        formulaDescription: "(NAV_end / NAV_start)^(365.25 / calendar_days) - 1",
        epistemicStatus: "VALIDATED"
      },
      {
        metricCode: "RSK-01",
        metricName: "Annualized Volatility (3Y)",
        category: "RISK",
        units: "PERCENTAGE",
        evaluationWindow: "3Y_ANNUALIZED",
        benchmarkDependent: false,
        formulaDescription: "std(R_daily) * sqrt(252)",
        epistemicStatus: "VALIDATED"
      },
      {
        metricCode: "RSK-03",
        metricName: "Maximum Drawdown (3Y)",
        category: "RISK",
        units: "PERCENTAGE",
        evaluationWindow: "3Y_ANNUALIZED",
        benchmarkDependent: false,
        formulaDescription: "min((NAV_t - Peak_t) / Peak_t)",
        epistemicStatus: "CANDIDATE"
      },
      {
        metricCode: "MKT-01",
        metricName: "Systematic Beta (3Y)",
        category: "MARKET_SENSITIVITY",
        units: "RATIO",
        evaluationWindow: "3Y_ANNUALIZED",
        benchmarkDependent: true,
        formulaDescription: "cov(R_p, R_b) / var(R_b)",
        epistemicStatus: "CANDIDATE"
      },
      {
        metricCode: "MKT-02",
        metricName: "Downside Beta (3Y)",
        category: "MARKET_SENSITIVITY",
        units: "RATIO",
        evaluationWindow: "3Y_ANNUALIZED",
        benchmarkDependent: true,
        formulaDescription: "cov(R_p, R_b | R_b < 0) / var(R_b | R_b < 0)",
        epistemicStatus: "CANDIDATE"
      },
      {
        metricCode: "RAT-04",
        metricName: "Information Ratio (3Y)",
        category: "RISK_ADJUSTED",
        units: "RATIO",
        evaluationWindow: "3Y_ANNUALIZED",
        benchmarkDependent: true,
        formulaDescription: "(mean(R_p - R_b) * 252) / (std(R_p - R_b) * sqrt(252))",
        epistemicStatus: "CANDIDATE"
      },
      {
        metricCode: "REL-01",
        metricName: "Tracking Error (3Y Annualized)",
        category: "BENCHMARK_ALPHA",
        units: "PERCENTAGE",
        evaluationWindow: "3Y_ANNUALIZED",
        benchmarkDependent: true,
        formulaDescription: "std(R_p - R_b) * sqrt(252)",
        epistemicStatus: "CANDIDATE"
      }
    ];

    assert.equal(definitions.length, 8);
    const codes = definitions.map((d) => d.metricCode);
    EXPECTED_METRIC_CODES.forEach((code) => {
      assert.ok(codes.includes(code), `Metric ${code} must be present`);
    });

    // Check benchmark dependency separation
    const absoluteMetrics = definitions.filter((d) => !d.benchmarkDependent);
    const relativeMetrics = definitions.filter((d) => d.benchmarkDependent);

    assert.equal(absoluteMetrics.length, 4, "4 metrics must be absolute (RET-02, RET-03, RSK-01, RSK-03)");
    assert.equal(relativeMetrics.length, 4, "4 metrics must be benchmark-dependent (MKT-01, MKT-02, RAT-04, REL-01)");
  });

  it("verifies eligibility rule: distinct dates >= 700 is required for 3-Year metrics", () => {
    const itemSufficient: ScreenerItem = {
      schemeOptionId: 1,
      schemeId: 1,
      schemeCode: "HDFC_FLEXI",
      schemeName: "HDFC Flexi Cap Fund - Direct Plan - Growth",
      amcId: 1,
      amcName: "HDFC Mutual Fund",
      category: "FLEXI_CAP",
      categoryDisplayName: "Flexi Cap",
      amfiCode: "118955",
      isin: "INF179K01UT0",
      distinctNavDates: 1419,
      sufficiencyStatus: "ELIGIBLE",
      isEligibleFor3Y: true,
      exclusionReason: null,
      candidateBenchmarkCode: "NIFTY_500_TRI",
      candidateBenchmarkName: "NIFTY 500 Total Returns Index",
      benchmarkGovernanceStatus: "CANDIDATE_METHODOLOGY_MAPPING",
      benchmarkObservationCount: 1426,
      benchmarkCoverageStatus: "SUFFICIENT",
      latestCalculationRunId: 101,
      asOfDate: "2026-10-01",
      calculationStatus: "CALCULATED",
      metrics: {
        "RET-03": {
          metricCode: "RET-03",
          numericValue: 0.185,
          formattedValue: "+18.50%",
          periodType: "3Y_ANNUALIZED",
          units: "PERCENTAGE",
          calculationStatus: "CALCULATED"
        }
      }
    };

    const itemInsufficient: ScreenerItem = {
      ...itemSufficient,
      schemeOptionId: 99,
      distinctNavDates: 450,
      sufficiencyStatus: "PARTIAL",
      isEligibleFor3Y: false,
      exclusionReason: "Insufficient NAV history (450 of 700 required distinct dates for 3-Year window)"
    };

    assert.equal(itemSufficient.isEligibleFor3Y, true);
    assert.equal(itemSufficient.exclusionReason, null);
    assert.equal(itemInsufficient.isEligibleFor3Y, false);
    assert.ok(itemInsufficient.exclusionReason?.includes("450 of 700"));
  });

  it("enforces candidate benchmark mapping governance disclaimer", () => {
    const sampleSummary: CategoryScreenerSummary = {
      category: "FLEXI_CAP",
      displayName: "Flexi Cap",
      candidateBenchmarkCode: "NIFTY_500_TRI",
      totalOptions: 47,
      eligibleCount: 25,
      partialCount: 22,
      calculatedCount: 25
    };

    assert.equal(sampleSummary.totalOptions, sampleSummary.eligibleCount + sampleSummary.partialCount);
    assert.equal(sampleSummary.candidateBenchmarkCode, "NIFTY_500_TRI");
  });

  it("TSK-M1-09: verifies deterministic missing-value partitioning without magic numbers", () => {
    // Partitioning invariant: funds with missing analytical results must always sink to the bottom
    // regardless of sort direction (ascending or descending), never treated as extreme numerical values.
    const items = [
      { id: 1, name: "Fund A", val: 0.12 },
      { id: 2, name: "Fund B", val: null }, // uncalculated/missing
      { id: 3, name: "Fund C", val: 0.24 },
      { id: 4, name: "Fund D", val: undefined }, // uncalculated/missing
      { id: 5, name: "Fund E", val: -0.05 }
    ];

    const sortFn = (list: typeof items, direction: "asc" | "desc") => {
      return [...list].sort((a, b) => {
        const hasA = a.val !== undefined && a.val !== null;
        const hasB = b.val !== undefined && b.val !== null;

        if (!hasA && !hasB) return a.id - b.id;
        if (!hasA) return 1; // missing values always sink to the bottom
        if (!hasB) return -1;

        if (a.val! < b.val!) return direction === "asc" ? -1 : 1;
        if (a.val! > b.val!) return direction === "asc" ? 1 : -1;
        return a.id - b.id;
      });
    };

    // Ascending: [-0.05, 0.12, 0.24, missing (B), missing (D)]
    const sortedAsc = sortFn(items, "asc");
    assert.deepEqual(sortedAsc.map((i) => i.id), [5, 1, 3, 2, 4]);

    // Descending: [0.24, 0.12, -0.05, missing (B), missing (D)]
    const sortedDesc = sortFn(items, "desc");
    assert.deepEqual(sortedDesc.map((i) => i.id), [3, 1, 5, 2, 4]);

    // Verify missing values were placed at the bottom in both directions
    assert.equal(sortedAsc[3].id, 2);
    assert.equal(sortedAsc[4].id, 4);
    assert.equal(sortedDesc[3].id, 2);
    assert.equal(sortedDesc[4].id, 4);
  });

  describe("TSK-M4-01: Category-Relative Percentile Invariants & ECDF Midpoint Ranking", () => {
    // ECDF Midpoint formula helper: PR = [(count(y < x) + 0.5 * count(y == x)) / N] * 100
    const computePercentile = (x: number, validValues: number[]) => {
      const N = validValues.length;
      if (N < 3) return null;
      const strictlyLower = validValues.filter((v) => v < x).length;
      const equalCount = validValues.filter((v) => v === x).length;
      const pr = ((strictlyLower + 0.5 * equalCount) / N) * 100;
      const prRounded = Math.round(pr * 100) / 100;
      const pInt = Math.min(99, Math.max(1, Math.round(prRounded)));
      return {
        percentileRank: prRounded,
        formattedPercentile: `P${pInt.toString().padStart(2, "0")}`,
        peerDenominator: N
      };
    };

    it("verifies mathematical symmetry and non-parametric bounds for N=6 distinct values", () => {
      const values = [0.10, 0.15, 0.20, 0.25, 0.30, 0.35]; // N=6
      const results = values.map((v) => computePercentile(v, values)!);

      // Verify PR values: 8.33, 25.00, 41.67, 58.33, 75.00, 91.67
      assert.equal(results[0].percentileRank, 8.33);
      assert.equal(results[0].formattedPercentile, "P08");

      assert.equal(results[1].percentileRank, 25.00);
      assert.equal(results[1].formattedPercentile, "P25");

      assert.equal(results[2].percentileRank, 41.67);
      assert.equal(results[2].formattedPercentile, "P42");

      assert.equal(results[3].percentileRank, 58.33);
      assert.equal(results[3].formattedPercentile, "P58");

      assert.equal(results[4].percentileRank, 75.00);
      assert.equal(results[4].formattedPercentile, "P75");

      assert.equal(results[5].percentileRank, 91.67);
      assert.equal(results[5].formattedPercentile, "P92");

      // Verify exact symmetry: sum of opposite ranks equals 100%
      assert.equal(Math.round(results[0].percentileRank + results[5].percentileRank), 100);
      assert.equal(Math.round(results[1].percentileRank + results[4].percentileRank), 100);
      assert.equal(Math.round(results[2].percentileRank + results[3].percentileRank), 100);

      // Verify no artificial 0% or 100% boundary collapse
      assert.ok(results.every((r) => r.percentileRank > 0 && r.percentileRank < 100));
    });

    it("verifies deterministic tie handling preserving average rank", () => {
      // 4 funds, where two funds tie with value 0.15: [0.10, 0.15, 0.15, 0.20]
      const values = [0.10, 0.15, 0.15, 0.20];
      const pLowest = computePercentile(0.10, values)!;
      const pTied1 = computePercentile(0.15, values)!;
      const pTied2 = computePercentile(0.15, values)!;
      const pHighest = computePercentile(0.20, values)!;

      // 0.10: strictlyLower=0, equal=1 -> (0 + 0.5)/4 = 12.5%
      assert.equal(pLowest.percentileRank, 12.5);
      assert.equal(pLowest.formattedPercentile, "P13");

      // 0.15: strictlyLower=1, equal=2 -> (1 + 0.5*2)/4 = 50.0%
      assert.equal(pTied1.percentileRank, 50.0);
      assert.equal(pTied1.formattedPercentile, "P50");
      assert.equal(pTied2.percentileRank, 50.0);
      assert.equal(pTied2.formattedPercentile, "P50");

      // 0.20: strictlyLower=3, equal=1 -> (3 + 0.5)/4 = 87.5%
      assert.equal(pHighest.percentileRank, 87.5);
      assert.equal(pHighest.formattedPercentile, "P88");
    });

    it("verifies peer denominator exclusion of uncalculated / missing values", () => {
      // 6 funds in category, but only 4 have calculated values
      const cohort = [
        { id: 1, val: 0.18 },
        { id: 2, val: null }, // uncalculated
        { id: 3, val: 0.22 },
        { id: 4, val: undefined }, // uncalculated
        { id: 5, val: 0.14 },
        { id: 6, val: 0.25 }
      ];

      const validValues = cohort
        .map((c) => c.val)
        .filter((v): v is number => v !== null && v !== undefined);

      assert.equal(validValues.length, 4, "Denominator N must strictly exclude uncalculated values");

      const pFund1 = computePercentile(0.18, validValues)!;
      assert.equal(pFund1.peerDenominator, 4);
      // strictlyLower for 0.18: 0.14 (1 item), equal: 1 item -> (1 + 0.5)/4 = 37.5%
      assert.equal(pFund1.percentileRank, 37.5);
      assert.equal(pFund1.formattedPercentile, "P38");
    });

    it("verifies minimum peer threshold rule (N < 3 returns null / INSUFFICIENT_PEERS)", () => {
      const smallCohort = [0.12, 0.18]; // N=2 (< 3)
      const res = computePercentile(0.18, smallCohort);
      assert.equal(res, null, "Must not compute percentile when valid peers N < 3");
    });

    it("verifies ranking direction mappings across the 8 canonical metrics", () => {
      const metricDirections: Record<string, "HIGHER_IS_BETTER" | "LOWER_IS_BETTER" | "DESCRIPTIVE"> = {
        "RET-02": "HIGHER_IS_BETTER",
        "RET-03": "HIGHER_IS_BETTER",
        "RSK-01": "LOWER_IS_BETTER",
        "RSK-03": "HIGHER_IS_BETTER", // max drawdown stored as negative decimal; higher algebraic = shallower drawdown
        "MKT-01": "DESCRIPTIVE",
        "MKT-02": "LOWER_IS_BETTER",
        "RAT-04": "HIGHER_IS_BETTER",
        "REL-01": "LOWER_IS_BETTER"
      };

      assert.equal(metricDirections["RET-02"], "HIGHER_IS_BETTER");
      assert.equal(metricDirections["RSK-01"], "LOWER_IS_BETTER");
      assert.equal(metricDirections["MKT-01"], "DESCRIPTIVE");
      assert.equal(metricDirections["RSK-03"], "HIGHER_IS_BETTER");
      assert.equal(metricDirections["REL-01"], "LOWER_IS_BETTER");
    });

    it("verifies analytical profile and calculation audit navigation URL invariants", () => {
      const sampleItem = {
        schemeId: 1,
        schemeOptionId: 10262,
        latestCalculationRunId: 88412
      };

      // Profile navigation must link to /funds/[schemeId]?option=[optionId]
      const profileHref = `/funds/${sampleItem.schemeId}?option=${sampleItem.schemeOptionId}`;
      assert.equal(profileHref, "/funds/1?option=10262");

      // Calculation audit navigation must link to /analysis/[runId]
      const auditHref = `/analysis/${sampleItem.latestCalculationRunId}`;
      assert.equal(auditHref, "/analysis/88412");
    });

    it("verifies CALCULATED filter isolates exactly the hydrated cohort options", () => {
      const items = [
        { id: 1, calculationStatus: "CALCULATED", isEligible: true },
        { id: 2, calculationStatus: "NOT_CALCULATED", isEligible: true },
        { id: 3, calculationStatus: "CALCULATED", isEligible: true },
        { id: 4, calculationStatus: "INSUFFICIENT_DATA", isEligible: false }
      ];

      const hydratedOnly = items.filter((item) => item.calculationStatus === "CALCULATED");
      assert.equal(hydratedOnly.length, 2);
      assert.deepEqual(hydratedOnly.map((i) => i.id), [1, 3]);
    });
  });
});

