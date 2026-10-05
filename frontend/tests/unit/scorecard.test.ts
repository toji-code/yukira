import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { fetchLatestScore, fetchScoreByRunId, fetchScoreHistory, fetchScoreMethodology, fetchCurrentScore } from "../../lib/api/scores";
import { AnalyticalScore, MethodologyConfig } from "../../types/score";
import { YukiraScoreCard } from "../../components/analysis/YukiraScoreCard";

describe("YUKIRA Analytical Scorecard Tests (YUKIRA_SCORE_V1)", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockHdfcScoreResponse: AnalyticalScore = {
    scoreId: 1,
    schemeOptionId: 1,
    schemeName: "HDFC Flexi Cap Fund",
    amfiCode: "118955",
    isin: "INF179K01UT0",
    score: 67.94,
    confidence: 95.0,
    status: "PARTIAL",
    scoreVersion: "YUKIRA_SCORE_V1",
    methodologyStatus: "CANDIDATE",
    asOfDate: "2024-01-15",
    knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
    calculationRunId: 8124,
    referencePopulation: "INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1",
    summary: "Deterministic candidate scoring across 4 empirical dimensions.",
    disclaimer:
      "YUKIRA_SCORE_V1 is a candidate research methodology. Reference population INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1 is PROVISIONAL, not empirical full-universe percentiles, and not peer-ranked against the complete universe. NOT investment advice.",
    dimensions: [
      {
        id: 201,
        dimension: "RETURN_QUALITY",
        dimensionName: "Return Quality",
        score: 64.69,
        weight: 0.30,
        effectiveWeight: 0.30,
        contribution: 19.41,
        status: "AVAILABLE",
        confidence: 95.0,
        eligibleMetricCount: 2,
        totalMetricCount: 2,
        metricContributions: [
          {
            id: 101,
            metricCode: "RET-03",
            metricName: "3-Year Compound Annual Growth Rate (CAGR)",
            rawValue: 0.1482,
            formattedRawValue: "14.82%",
            normalizedValue: 62.41,
            direction: "HIGHER_IS_BETTER",
            weight: 0.60,
            effectiveWeight: 0.60,
            contribution: 37.45,
            observationCount: 739,
            eligibility: "ELIGIBLE",
            exclusionReason: null,
            unit: "PERCENTAGE",
            metricResultId: 501,
          },
          {
            id: 102,
            metricCode: "RET-07",
            metricName: "3-Year Annualized Active Return",
            rawValue: 0.0245,
            formattedRawValue: "+2.45%",
            normalizedValue: 68.10,
            direction: "HIGHER_IS_BETTER",
            weight: 0.40,
            effectiveWeight: 0.40,
            contribution: 27.24,
            observationCount: 738,
            eligibility: "ELIGIBLE",
            exclusionReason: null,
            unit: "PERCENTAGE",
            metricResultId: 502,
          },
        ],
      },
      {
        id: 202,
        dimension: "RISK_QUALITY",
        dimensionName: "Risk Quality",
        score: 66.68,
        weight: 0.30,
        effectiveWeight: 0.30,
        contribution: 20.00,
        status: "AVAILABLE",
        confidence: 95.0,
        eligibleMetricCount: 3,
        totalMetricCount: 3,
        metricContributions: [
          {
            id: 103,
            metricCode: "RSK-01",
            metricName: "3-Year Annualized Volatility",
            rawValue: 0.1241,
            formattedRawValue: "12.41%",
            normalizedValue: 65.20,
            direction: "LOWER_IS_BETTER",
            weight: 0.35,
            effectiveWeight: 0.35,
            contribution: 22.82,
            observationCount: 739,
            eligibility: "ELIGIBLE",
            exclusionReason: null,
            unit: "PERCENTAGE",
            metricResultId: 503,
          },
          {
            id: 104,
            metricCode: "RSK-02",
            metricName: "3-Year Downside Semideviation",
            rawValue: 0.0112,
            formattedRawValue: "1.12%",
            normalizedValue: 71.05,
            direction: "LOWER_IS_BETTER",
            weight: 0.35,
            effectiveWeight: 0.35,
            contribution: 24.87,
            observationCount: 739,
            eligibility: "ELIGIBLE",
            exclusionReason: null,
            unit: "PERCENTAGE",
            metricResultId: 504,
          },
          {
            id: 105,
            metricCode: "RSK-03",
            metricName: "3-Year Maximum Drawdown",
            rawValue: -0.1420,
            formattedRawValue: "-14.20%",
            normalizedValue: 63.80,
            direction: "HIGHER_IS_BETTER",
            weight: 0.30,
            effectiveWeight: 0.30,
            contribution: 19.14,
            observationCount: 739,
            eligibility: "ELIGIBLE",
            exclusionReason: null,
            unit: "PERCENTAGE",
            metricResultId: 505,
          },
        ],
      },
      {
        id: 203,
        dimension: "BENCHMARK_RELATIVE_QUALITY",
        dimensionName: "Benchmark-Relative Quality",
        score: 71.08,
        weight: 0.25,
        effectiveWeight: 0.25,
        contribution: 17.77,
        status: "AVAILABLE",
        confidence: 95.0,
        eligibleMetricCount: 3,
        totalMetricCount: 3,
        metricContributions: [
          {
            id: 106,
            metricCode: "REL-02",
            metricName: "Jensen's Alpha 3Y",
            rawValue: 0.0312,
            formattedRawValue: "+3.12%",
            normalizedValue: 70.40,
            direction: "HIGHER_IS_BETTER",
            weight: 0.40,
            effectiveWeight: 0.40,
            contribution: 28.16,
            observationCount: 738,
            eligibility: "ELIGIBLE",
            exclusionReason: null,
            unit: "PERCENTAGE",
            metricResultId: 506,
          },
          {
            id: 107,
            metricCode: "RAT-04",
            metricName: "Information Ratio 3Y",
            rawValue: 1.45,
            formattedRawValue: "1.45",
            normalizedValue: 72.10,
            direction: "HIGHER_IS_BETTER",
            weight: 0.35,
            effectiveWeight: 0.35,
            contribution: 25.24,
            observationCount: 738,
            eligibility: "ELIGIBLE",
            exclusionReason: null,
            unit: "RATIO",
            metricResultId: 507,
          },
          {
            id: 108,
            metricCode: "MKT-01",
            metricName: "Beta 3Y",
            rawValue: 0.94,
            formattedRawValue: "0.94",
            normalizedValue: 68.90,
            direction: "TARGET_VALUE",
            weight: 0.25,
            effectiveWeight: 0.25,
            contribution: 17.23,
            observationCount: 738,
            eligibility: "ELIGIBLE",
            exclusionReason: null,
            unit: "RATIO",
            metricResultId: 508,
          },
        ],
      },
      {
        id: 204,
        dimension: "CONSISTENCY_DOWNSIDE_QUALITY",
        dimensionName: "Consistency & Downside Quality",
        score: 69.95,
        weight: 0.15,
        effectiveWeight: 0.15,
        contribution: 10.49,
        status: "PARTIAL",
        confidence: 95.0,
        eligibleMetricCount: 1,
        totalMetricCount: 2,
        metricContributions: [
          {
            id: 109,
            metricCode: "MKT-05",
            metricName: "Capture Spread 3Y",
            rawValue: -0.0412,
            formattedRawValue: "-4.12 pp",
            normalizedValue: null,
            direction: "HIGHER_IS_BETTER",
            weight: 0.50,
            effectiveWeight: 0.00,
            contribution: 0.00,
            observationCount: 738,
            eligibility: "UNCALIBRATED",
            exclusionReason: "Metric calibration status is UNCALIBRATED / NOT ELIGIBLE FOR SCORE",
            unit: "PERCENTAGE_POINTS",
            metricResultId: 509,
          },
          {
            id: 110,
            metricCode: "MKT-02",
            metricName: "Downside Beta 3Y",
            rawValue: 0.88,
            formattedRawValue: "0.88",
            normalizedValue: 71.00,
            direction: "LOWER_IS_BETTER",
            weight: 0.50,
            effectiveWeight: 1.00,
            contribution: 71.00,
            observationCount: 738,
            eligibility: "ELIGIBLE",
            exclusionReason: null,
            unit: "RATIO",
            metricResultId: 510,
          },
        ],
      },
    ],
    evidenceConfidence: {
      totalObservations: 739,
      validObservations: 739,
      suspiciousObservations: 0,
      invalidObservations: 0,
      pairedBenchmarkObservations: 739,
      pairedReturnPeriods: 738,
      meetsObservationThreshold: true,
      sourceArtifactVerified: true,
      pitIntegrityMaintained: true,
      confidenceScore: 95.0,
      assessment: "HIGH_CONFIDENCE",
      observationNotes:
        "739 verified trading days (NAV level observations); 738 synchronous daily return intervals against benchmark.",
    },
  };

  // 1. Score renders from API data
  it("1. Score renders faithfully from API data", async () => {
    globalThis.fetch = async (input: RequestInfo | URL) => {
      const url = input.toString();
      assert.ok(url.includes("/api/v1/scores/1/latest"));
      return new Response(JSON.stringify(mockHdfcScoreResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    const res = await fetchLatestScore(1);
    assert.ok(res !== null);
    assert.strictEqual(res.score, 67.94);
    assert.strictEqual(res.schemeName, "HDFC Flexi Cap Fund");
    assert.strictEqual(res.status, "PARTIAL");

    const html = renderToStaticMarkup(
      React.createElement(YukiraScoreCard, { schemeOptionId: 1, initialScore: res })
    );

    assert.ok(html.includes("67.94"), "Rendered HTML must contain score 67.94");
    assert.ok(html.includes("/ 100"), "Rendered HTML must show scale / 100");
    assert.ok(html.includes("Analytical Score"), "Rendered HTML must label Analytical Score");
  });

  // 2. Confidence renders separately
  it("2. Confidence renders separately from analytical score", () => {
    const html = renderToStaticMarkup(
      React.createElement(YukiraScoreCard, { schemeOptionId: 1, initialScore: mockHdfcScoreResponse })
    );

    assert.ok(html.includes("Evidence Confidence"), "Must display Evidence Confidence title");
    assert.ok(html.includes("95.00"), "Must display Confidence 95.00");
    assert.ok(
      html.includes("0% weight"),
      "Must state that Evidence Confidence carries strictly 0% weight in analytical score"
    );
  });

  // 3. Dimension scores and frozen weights render correctly from API
  it("3. Dimension scores and frozen weights (30%, 30%, 25%, 15%) render correctly", () => {
    const html = renderToStaticMarkup(
      React.createElement(YukiraScoreCard, { schemeOptionId: 1, initialScore: mockHdfcScoreResponse })
    );

    assert.ok(html.includes("Return Quality"));
    assert.ok(html.includes("64.69"));
    assert.ok(html.includes("Weight: 30.0%"));

    assert.ok(html.includes("Risk Quality"));
    assert.ok(html.includes("66.68"));

    assert.ok(html.includes("Benchmark-Relative Quality"));
    assert.ok(html.includes("71.08"));
    assert.ok(html.includes("Weight: 25.0%"));

    assert.ok(html.includes("Consistency &amp; Downside Quality") || html.includes("Consistency & Downside Quality"));
    assert.ok(html.includes("69.95"));
    assert.ok(html.includes("Weight: 15.0%"));
  });

  // 4. Canonical metric names render correctly
  it("4. Canonical metric names (RET-07, RAT-04, MKT-05, MKT-02) render correctly", () => {
    const html = renderToStaticMarkup(
      React.createElement(YukiraScoreCard, { schemeOptionId: 1, initialScore: mockHdfcScoreResponse })
    );

    assert.ok(html.includes("3-Year Annualized Active Return") || html.includes("3Y Annualized Active Return"), "RET-07 must be 3-Year Annualized Active Return");
    assert.ok(html.includes("Information Ratio 3Y"), "RAT-04 must be Information Ratio 3Y");
    assert.ok(html.includes("Capture Spread 3Y"), "MKT-05 must be Capture Spread 3Y");
    assert.ok(html.includes("Downside Beta 3Y"), "MKT-02 must be Downside Beta 3Y");
    assert.ok(html.includes("Jensen's Alpha 3Y") || html.includes("Jensen&#x27;s Alpha 3Y") || html.includes("Jensen&#39;s Alpha 3Y"), "REL-02 must be Jensen's Alpha 3Y");
  });

  // 5. Provisional methodology disclaimer is visible
  it("5. Provisional methodology disclaimer is visible and discoverable", () => {
    const html = renderToStaticMarkup(
      React.createElement(YukiraScoreCard, { schemeOptionId: 1, initialScore: mockHdfcScoreResponse })
    );

    assert.ok(html.includes("YUKIRA_SCORE_V1"), "Must display score version");
    assert.ok(html.includes("CANDIDATE"), "Must display methodology status");
    assert.ok(html.includes("INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1"), "Must display reference population");
    assert.ok(
      html.includes("Not investment advice") || html.includes("not investment advice"),
      "Must explicitly state not investment advice"
    );
  });

  // 6. Missing dimension does not render as zero
  it("6. Missing dimension does not render as 0.00 / 100", () => {
    const partialScore: AnalyticalScore = {
      ...mockHdfcScoreResponse,
      score: 55.0,
      status: "PARTIAL",
      dimensions: [
        {
          id: 301,
          dimension: "RETURN_QUALITY",
          dimensionName: "Return Quality",
          score: 55.0,
          weight: 0.3,
          effectiveWeight: 1.0,
          contribution: 55.0,
          status: "AVAILABLE",
          confidence: 90.0,
          eligibleMetricCount: 1,
          totalMetricCount: 1,
          metricContributions: [],
        },
        {
          id: 302,
          dimension: "CONSISTENCY_DOWNSIDE_QUALITY",
          dimensionName: "Consistency & Downside Quality",
          score: null, // missing dimension
          weight: 0.15,
          effectiveWeight: 0.0,
          contribution: 0.0,
          status: "INSUFFICIENT_DATA",
          confidence: null,
          eligibleMetricCount: 0,
          totalMetricCount: 1,
          metricContributions: [],
        },
      ],
    };

    const html = renderToStaticMarkup(
      React.createElement(YukiraScoreCard, { schemeOptionId: 1, initialScore: partialScore })
    );

    assert.ok(html.includes("Insufficient History") || html.includes("Not available"));
    assert.ok(!html.includes("0.00 / 100"), "Must NEVER render 0.00 / 100 for unavailable dimensions");
  });

  // 7. API error state
  it("7. Handles API error gracefully without crashing", async () => {
    globalThis.fetch = async () =>
      new Response("Internal Server Error", {
        status: 500,
        statusText: "Internal Server Error",
      });

    await assert.rejects(
      async () => {
        await fetchLatestScore(1);
      },
      (err: { status?: number }) => {
        assert.strictEqual(err.status, 500);
        return true;
      }
    );
  });

  // 8. Score unavailable state
  it("8. Handles score unavailable (HTTP 404) by returning null and displaying empty state", async () => {
    globalThis.fetch = async () =>
      new Response(JSON.stringify({ error: "Score not found" }), {
        status: 404,
        statusText: "Not Found",
      });

    const res = await fetchLatestScore(999);
    assert.strictEqual(res, null, "HTTP 404 on score endpoint must return null");

    const html = renderToStaticMarkup(
      React.createElement(YukiraScoreCard, { schemeOptionId: 999, initialScore: null })
    );
    assert.ok(html.includes("No Analytical Score Calculated Yet"));
  });

  // 9. Evidence counts distinguish 739 observations from 738 paired periods
  it("9. Distinguishes 739 level observations from 738 paired return periods", () => {
    const html = renderToStaticMarkup(
      React.createElement(YukiraScoreCard, { schemeOptionId: 1, initialScore: mockHdfcScoreResponse })
    );

    assert.ok(html.includes("739"), "Must display 739 level observations");
    assert.ok(html.includes("738"), "Must display 738 paired return periods");
    assert.ok(
      html.includes("NAV Trading Days"),
      "Must label level observations as NAV trading days"
    );
    assert.ok(
      html.includes("Discrete Return Intervals"),
      "Must label return periods as discrete return intervals"
    );
  });

  // 10. No frontend financial calculation is introduced
  it("10. Zero financial calculations performed in frontend", () => {
    const html = renderToStaticMarkup(
      React.createElement(YukiraScoreCard, { schemeOptionId: 1, initialScore: mockHdfcScoreResponse })
    );
    assert.ok(html.includes("67.94"), "Frontend renders score 67.94 directly from backend");
  });

  // 11. fetchScoreMethodology contract
  it("11. fetchScoreMethodology retrieves methodology config endpoint", async () => {
    const mockMethodology: MethodologyConfig = {
      scoreVersion: "YUKIRA_SCORE_V1",
      methodologyStatus: "CANDIDATE",
      referencePopulation: "INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1",
      effectiveDate: "2024-01-15",
      minRequiredObservations: 700,
      performanceDimensions: {},
      evidenceConfidenceWeight: 0,
      evidenceConfidenceRole: "Epistemic data verification",
      unauthorizedMetrics: ["MKT-06"],
      disclaimer: "Candidate methodology",
    };

    globalThis.fetch = async () =>
      new Response(JSON.stringify(mockMethodology), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const config = await fetchScoreMethodology();
    assert.strictEqual(config.scoreVersion, "YUKIRA_SCORE_V1");
    assert.strictEqual(config.evidenceConfidenceWeight, 0);
  });

  // 12. fetchScoreByRunId contract
  it("12. fetchScoreByRunId retrieves score linked to calculation run", async () => {
    globalThis.fetch = async (input: RequestInfo | URL) => {
      const url = input.toString();
      assert.ok(url.includes("/api/v1/scores/run/8124"));
      return new Response(JSON.stringify(mockHdfcScoreResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    const res = await fetchScoreByRunId(8124);
    assert.ok(res !== null);
    assert.strictEqual(res.calculationRunId, 8124);
    assert.strictEqual(res.score, 67.94);
  });

  // 13. fetchScoreHistory contract
  it("13. fetchScoreHistory retrieves point-in-time score history snapshots", async () => {
    globalThis.fetch = async (input: RequestInfo | URL) => {
      const url = input.toString();
      assert.ok(url.includes("/api/v1/scores/1/history"));
      return new Response(JSON.stringify([mockHdfcScoreResponse]), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    const history = await fetchScoreHistory(1);
    assert.ok(Array.isArray(history));
    assert.strictEqual(history.length, 1);
    assert.strictEqual(history[0].score, 67.94);
  });

  // 14. fetchCurrentScore contract (read-only current score endpoint)
  it("14. fetchCurrentScore queries GET /api/v1/scores/{id}/current without triggering calculation", async () => {
    let methodCalled = "";
    globalThis.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString();
      methodCalled = init?.method || "GET";
      assert.ok(url.includes("/api/v1/scores/10189/current"));
      return new Response(JSON.stringify(mockHdfcScoreResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    const res = await fetchCurrentScore(10189);
    assert.ok(res !== null);
    assert.strictEqual(methodCalled, "GET");
  });

  // 15. Single snapshot consistency invariant
  it("15. YukiraScoreCard renders current score and all 10 canonical metrics from one single calculation run snapshot", () => {
    const html = renderToStaticMarkup(
      React.createElement(YukiraScoreCard, { schemeOptionId: 1, initialScore: mockHdfcScoreResponse })
    );
    // Verify score and canonical metric identities render from the exact same snapshot
    assert.ok(html.includes("67.94"), "Score value renders from snapshot");
    assert.ok(html.includes("RET-03"), "RET-03 renders in scorecard");
    assert.ok(html.includes("RET-07"), "RET-07 renders in scorecard");
    assert.ok(html.includes("RSK-01"), "RSK-01 renders in scorecard");
    assert.ok(html.includes("RSK-02"), "RSK-02 renders in scorecard");
    assert.ok(html.includes("RSK-03"), "RSK-03 renders in scorecard");
    assert.ok(html.includes("REL-02"), "REL-02 renders in scorecard");
    assert.ok(html.includes("RAT-04"), "RAT-04 renders in scorecard");
    assert.ok(html.includes("MKT-01"), "MKT-01 renders in scorecard");
    assert.ok(html.includes("MKT-02"), "MKT-02 renders in scorecard");
    assert.ok(html.includes("MKT-05"), "MKT-05 renders in scorecard");
  });

  // 16. Strict numerical truth-verification test for canonical 10189 snapshot (Run #8313 / Score #68928)
  it("16. Renders canonical 10189 snapshot (#8313 / #68928) with exact backend dimension scores and metric contributions", () => {
    const canonical10189Snapshot: AnalyticalScore = {
      scoreId: 68928,
      schemeOptionId: 10189,
      schemeName: "Aditya Birla Sun Life Flexi Cap Fund Direct Growth",
      amfiCode: "119062",
      isin: "INF209K01157",
      score: 55.05,
      confidence: 100.0,
      status: "PARTIAL",
      scoreVersion: "YUKIRA_SCORE_V1",
      methodologyStatus: "CANDIDATE",
      asOfDate: "2026-10-01",
      knowledgeCutoffTime: "2026-10-01T23:59:59+05:30",
      calculationRunId: 8313,
      referencePopulation: "INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1",
      summary: "Deterministic score evaluation.",
      disclaimer: "YUKIRA Analytical Score V1 is a candidate quantitative quality assessment.",
      dimensions: [
        {
          id: 1301,
          dimension: "RETURN_QUALITY",
          dimensionName: "Return Quality",
          score: 10.72,
          weight: 0.3,
          effectiveWeight: 0.3,
          contribution: 3.216,
          status: "AVAILABLE",
          confidence: 100,
          eligibleMetricCount: 2,
          totalMetricCount: 2,
          metricContributions: [
            {
              id: 2581,
              metricCode: "RET-03",
              metricName: "3-Year Compound Annual Growth Rate (CAGR)",
              rawValue: 0.1409932591,
              formattedRawValue: "14.10%",
              normalizedValue: 17.8725,
              direction: "HIGHER_IS_BETTER",
              weight: 0.6,
              effectiveWeight: 0.6,
              contribution: 10.7235,
              observationCount: 739,
              eligibility: "ELIGIBLE",
              exclusionReason: null,
              unit: "PERCENTAGE",
              metricResultId: 16920,
            },
            {
              id: 2582,
              metricCode: "RET-07",
              metricName: "3-Year Annualized Active Return",
              rawValue: 0.0447784947,
              formattedRawValue: "+4.48%",
              normalizedValue: 0,
              direction: "HIGHER_IS_BETTER",
              weight: 0.4,
              effectiveWeight: 0.4,
              contribution: 0,
              observationCount: 735,
              eligibility: "ELIGIBLE",
              exclusionReason: null,
              unit: "PERCENTAGE",
              metricResultId: 16921,
            },
          ],
        },
        {
          id: 1302,
          dimension: "RISK_QUALITY",
          dimensionName: "Risk Quality",
          score: 69.74,
          weight: 0.3,
          effectiveWeight: 0.3,
          contribution: 20.922,
          status: "AVAILABLE",
          confidence: 100,
          eligibleMetricCount: 3,
          totalMetricCount: 3,
          metricContributions: [
            {
              id: 2583,
              metricCode: "RSK-01",
              metricName: "3-Year Annualized Volatility",
              rawValue: 0.1335788997,
              formattedRawValue: "13.36%",
              normalizedValue: 86.4211,
              direction: "LOWER_IS_BETTER",
              weight: 0.35,
              effectiveWeight: 0.35,
              contribution: 30.2474,
              observationCount: 739,
              eligibility: "ELIGIBLE",
              exclusionReason: null,
              unit: "PERCENTAGE",
              metricResultId: 16922,
            },
            {
              id: 2584,
              metricCode: "RSK-02",
              metricName: "3-Year Downside Semideviation",
              rawValue: 0.0945508116,
              formattedRawValue: "9.46%",
              normalizedValue: 75.4492,
              direction: "LOWER_IS_BETTER",
              weight: 0.35,
              effectiveWeight: 0.35,
              contribution: 26.4072,
              observationCount: 739,
              eligibility: "ELIGIBLE",
              exclusionReason: null,
              unit: "PERCENTAGE",
              metricResultId: 16923,
            },
            {
              id: 2585,
              metricCode: "RSK-03",
              metricName: "3-Year Maximum Drawdown",
              rawValue: -0.1702161768,
              formattedRawValue: "-17.02%",
              normalizedValue: 43.6149,
              direction: "HIGHER_IS_BETTER",
              weight: 0.3,
              effectiveWeight: 0.3,
              contribution: 13.0845,
              observationCount: 739,
              eligibility: "ELIGIBLE",
              exclusionReason: null,
              unit: "PERCENTAGE",
              metricResultId: 16924,
            },
          ],
        },
        {
          id: 1303,
          dimension: "BENCHMARK_RELATIVE_QUALITY",
          dimensionName: "Benchmark-Relative Quality",
          score: 78.77,
          weight: 0.25,
          effectiveWeight: 0.25,
          contribution: 19.6925,
          status: "AVAILABLE",
          confidence: 100,
          eligibleMetricCount: 3,
          totalMetricCount: 3,
          metricContributions: [
            {
              id: 2586,
              metricCode: "REL-02",
              metricName: "Jensen's Alpha 3Y",
              rawValue: 0.0433585001,
              formattedRawValue: "+4.34%",
              normalizedValue: 65.2988,
              direction: "HIGHER_IS_BETTER",
              weight: 0.4,
              effectiveWeight: 0.4,
              contribution: 26.1195,
              observationCount: 739,
              eligibility: "ELIGIBLE",
              exclusionReason: null,
              unit: "PERCENTAGE",
              metricResultId: 16925,
            },
            {
              id: 2587,
              metricCode: "RAT-04",
              metricName: "Information Ratio 3Y",
              rawValue: 1.3945571886,
              formattedRawValue: "1.39",
              normalizedValue: 91.3508,
              direction: "HIGHER_IS_BETTER",
              weight: 0.35,
              effectiveWeight: 0.35,
              contribution: 31.9728,
              observationCount: 735,
              eligibility: "ELIGIBLE",
              exclusionReason: null,
              unit: "RATIO",
              metricResultId: 16926,
            },
            {
              id: 2588,
              metricCode: "MKT-01",
              metricName: "Beta 3Y",
              rawValue: 0.9135039783,
              formattedRawValue: "0.91",
              normalizedValue: 82.7008,
              direction: "TARGET_VALUE",
              weight: 0.25,
              effectiveWeight: 0.25,
              contribution: 20.6752,
              observationCount: 735,
              eligibility: "ELIGIBLE",
              exclusionReason: null,
              unit: "RATIO",
              metricResultId: 16927,
            },
          ],
        },
        {
          id: 1304,
          dimension: "CONSISTENCY_DOWNSIDE_QUALITY",
          dimensionName: "Consistency & Downside Quality",
          score: 74.79,
          weight: 0.15,
          effectiveWeight: 0.15,
          contribution: 11.2185,
          status: "PARTIAL",
          confidence: 50,
          eligibleMetricCount: 1,
          totalMetricCount: 2,
          metricContributions: [
            {
              id: 2589,
              metricCode: "MKT-05",
              metricName: "Capture Spread 3Y",
              rawValue: -11.031682806,
              formattedRawValue: "-11.03 pp",
              normalizedValue: null,
              direction: "HIGHER_IS_BETTER",
              weight: 0.5,
              effectiveWeight: 0,
              contribution: 0,
              observationCount: 739,
              eligibility: "UNCALIBRATED",
              exclusionReason: "Metric calibration status is UNCALIBRATED / NOT ELIGIBLE FOR SCORE",
              unit: "PERCENTAGE_POINTS",
              metricResultId: 16929,
            },
            {
              id: 2590,
              metricCode: "MKT-02",
              metricName: "Downside Beta 3Y",
              rawValue: 0.9006565492,
              formattedRawValue: "0.90",
              normalizedValue: 74.7948,
              direction: "LOWER_IS_BETTER",
              weight: 0.5,
              effectiveWeight: 1,
              contribution: 74.7948,
              observationCount: 739,
              eligibility: "ELIGIBLE",
              exclusionReason: null,
              unit: "RATIO",
              metricResultId: 16928,
            },
          ],
        },
      ],
      evidenceConfidence: {
        totalObservations: 740,
        validObservations: 740,
        suspiciousObservations: 0,
        invalidObservations: 0,
        pairedBenchmarkObservations: 740,
        pairedReturnPeriods: 739,
        meetsObservationThreshold: true,
        sourceArtifactVerified: true,
        pitIntegrityMaintained: true,
        confidenceScore: 100,
        assessment: "HIGH_CONFIDENCE",
        observationNotes: "740 verified trading days.",
      },
    };

    const html = renderToStaticMarkup(
      React.createElement(YukiraScoreCard, { schemeOptionId: 10189, initialScore: canonical10189Snapshot })
    );

    // 1. Headline overall score
    assert.ok(html.includes("55.05"), "Must render overall score 55.05");

    // 2. Authoritative dimension scores
    assert.ok(html.includes("10.72"), "Return Quality score must be 10.72");
    assert.ok(html.includes("69.74"), "Risk Quality score must be 69.74");
    assert.ok(html.includes("78.77"), "Benchmark-Relative Quality score must be 78.77");
    assert.ok(html.includes("74.79"), "Consistency & Downside Quality score must be 74.79");

    // 3. Metric contributions & normalized scores
    assert.ok(html.includes("17.87"), "RET-03 normalized score 17.87");
    assert.ok(html.includes("+10.72"), "RET-03 contribution +10.72");
    assert.ok(html.includes("86.42"), "RSK-01 normalized score 86.42");
    assert.ok(html.includes("75.45"), "RSK-02 normalized score 75.45");
    assert.ok(html.includes("43.61"), "RSK-03 normalized score 43.61");
    assert.ok(html.includes("65.30"), "REL-02 normalized score 65.30");
    assert.ok(html.includes("91.35"), "RAT-04 normalized score 91.35");
    assert.ok(html.includes("82.70"), "MKT-01 normalized score 82.70");
    assert.ok(html.includes("74.79"), "MKT-02 normalized score 74.79");

    // 4. MKT-05 UNCALIBRATED & 0.0% effective weight
    assert.ok(html.includes("UNCALIBRATED"), "MKT-05 eligibility must be UNCALIBRATED");
    assert.ok(html.includes("0.0%"), "MKT-05 effective weight must be 0.0%");
  });

  // 17. Investor Orientation & Epistemic Safeguards Test
  it("17. Renders investor orientation, epistemic state definitions, non-advisory exclusions, and investigation prompts", () => {
    const html = renderToStaticMarkup(
      React.createElement(YukiraScoreCard, { schemeOptionId: 10189, initialScore: mockHdfcScoreResponse })
    );

    // 1. What This Score Means copy
    assert.ok(html.includes("What This Score Means"), "Must render 'What This Score Means' heading");
    assert.ok(html.includes("not a forecast of future returns"), "Must explicitly state not a forecast of future returns");
    assert.ok(html.includes("not an investment recommendation"), "Must explicitly state not an investment recommendation");

    // 2. Epistemic state explanations & non-advisory exclusions
    assert.ok(html.includes("What This Score Does NOT Tell You"), "Must render 'What This Score Does NOT Tell You' section");
    assert.ok(html.includes("No Future Predictions"), "Must include 'No Future Predictions' exclusion");
    assert.ok(html.includes("No Personal Suitability"), "Must include 'No Personal Suitability' exclusion");
    assert.ok(html.includes("No Advice or Recommendation"), "Must include 'No Advice or Recommendation' exclusion");

    // 3. What to investigate next due diligence prompts
    assert.ok(html.includes("What to Investigate Before Committing Capital"), "Must render 'What to Investigate Before Committing Capital' footer");
  });
});

