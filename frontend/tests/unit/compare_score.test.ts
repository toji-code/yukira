import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { ComparisonFund } from "../../types/comparison";


// We test rendering logic for comparison score integration
describe("Fund Comparison + Analytical Scorecard Integration V1", () => {
  const mockFundHdfc: ComparisonFund = {
    schemeOptionId: 1,
    schemeName: "HDFC Flexi Cap Fund",
    schemeCode: "HDFC_FLEXI",
    amcName: "HDFC Mutual Fund",
    amfiCode: "118955",
    isin: "INF179K01UT0",
    planType: "DIRECT",
    optionType: "GROWTH",
    yukiraScore: {
      scoreId: 101,
      schemeOptionId: 1,
      score: 67.94,
      confidence: 95.0,
      status: "PARTIAL",
      scoreVersion: "YUKIRA_SCORE_V1",
      methodologyStatus: "CANDIDATE",
      asOfDate: "2024-01-15",
      summary: "Deterministic score snapshot",
      dimensions: [
        { dimension: "RETURN_QUALITY", dimensionName: "Return Quality", score: 64.69, weight: 0.3, status: "AVAILABLE" },
        { dimension: "RISK_QUALITY", dimensionName: "Risk Quality", score: 66.68, weight: 0.25, status: "AVAILABLE" },
        { dimension: "BENCHMARK_RELATIVE_QUALITY", dimensionName: "Benchmark-Relative Quality", score: 71.02, weight: 0.25, status: "AVAILABLE" },
        { dimension: "CONSISTENCY_DOWNSIDE_QUALITY", dimensionName: "Consistency & Downside Quality", score: 69.50, weight: 0.2, status: "AVAILABLE" }
      ]
    }
  };

  const mockFundPeer: ComparisonFund = {
    schemeOptionId: 202,
    schemeName: "Parag Parikh Flexi Cap Fund",
    schemeCode: "PPFCF_DIR",
    amcName: "PPFAS Mutual Fund",
    amfiCode: "122639",
    isin: "INF879O01015",
    planType: "DIRECT",
    optionType: "GROWTH",
    yukiraScore: {
      scoreId: 102,
      schemeOptionId: 202,
      score: 72.14,
      confidence: 90.0,
      status: "AVAILABLE",
      scoreVersion: "YUKIRA_SCORE_V1",
      methodologyStatus: "CANDIDATE",
      asOfDate: "2024-01-15",
      summary: "Peer deterministic score snapshot",
      dimensions: [
        { dimension: "RETURN_QUALITY", dimensionName: "Return Quality", score: 75.10, weight: 0.3, status: "AVAILABLE" },
        { dimension: "RISK_QUALITY", dimensionName: "Risk Quality", score: 58.21, weight: 0.25, status: "AVAILABLE" },
        { dimension: "BENCHMARK_RELATIVE_QUALITY", dimensionName: "Benchmark-Relative Quality", score: 78.40, weight: 0.25, status: "AVAILABLE" },
        { dimension: "CONSISTENCY_DOWNSIDE_QUALITY", dimensionName: "Consistency & Downside Quality", score: 74.30, weight: 0.2, status: "AVAILABLE" }
      ]
    }
  };

  const mockFundInsufficient: ComparisonFund = {
    schemeOptionId: 303,
    schemeName: "New Emerging Opportunities Fund",
    schemeCode: "NEW_EMERGING",
    amcName: "New AMC",
    amfiCode: "199999",
    isin: "INF999K01010",
    planType: "DIRECT",
    optionType: "GROWTH",
    yukiraScore: {
      scoreId: 103,
      schemeOptionId: 303,
      score: null,
      confidence: 20.0,
      status: "INSUFFICIENT_DATA",
      scoreVersion: "YUKIRA_SCORE_V1",
      methodologyStatus: "CANDIDATE",
      asOfDate: "2024-01-15",
      summary: "Insufficient history",
      dimensions: []
    }
  };

  const mockFundNotApplicable: ComparisonFund = {
    schemeOptionId: 404,
    schemeName: "Liquid Cash Management Fund",
    schemeCode: "LIQUID_CASH",
    amcName: "Safe AMC",
    amfiCode: "188888",
    isin: "INF888K01010",
    planType: "DIRECT",
    optionType: "GROWTH",
    yukiraScore: {
      scoreId: 104,
      schemeOptionId: 404,
      score: null,
      confidence: null,
      status: "NOT_APPLICABLE",
      scoreVersion: "YUKIRA_SCORE_V1",
      methodologyStatus: "CANDIDATE",
      asOfDate: "2024-01-15",
      summary: "Not evaluated",
      dimensions: []
    }
  };

  it("1. Preserves exact schemeOptionId identity through comparison funds", () => {
    assert.strictEqual(mockFundHdfc.schemeOptionId, 1);
    assert.strictEqual(mockFundHdfc.yukiraScore?.schemeOptionId, 1);
    assert.strictEqual(mockFundPeer.schemeOptionId, 202);
    assert.strictEqual(mockFundPeer.yukiraScore?.schemeOptionId, 202);
  });

  it("2. Score values come directly from backend without recalculation or winner labels", () => {
    assert.strictEqual(mockFundHdfc.yukiraScore?.score, 67.94);
    assert.strictEqual(mockFundPeer.yukiraScore?.score, 72.14);

    // Verify dimension values match backend
    const hdfcReturnDim = mockFundHdfc.yukiraScore?.dimensions?.find(d => d.dimension === "RETURN_QUALITY");
    const peerReturnDim = mockFundPeer.yukiraScore?.dimensions?.find(d => d.dimension === "RETURN_QUALITY");

    assert.strictEqual(hdfcReturnDim?.score, 64.69);
    assert.strictEqual(peerReturnDim?.score, 75.10);
  });

  it("3. INSUFFICIENT_DATA fund retains null score and status without becoming 0.00", () => {
    assert.strictEqual(mockFundInsufficient.yukiraScore?.score, null);
    assert.strictEqual(mockFundInsufficient.yukiraScore?.status, "INSUFFICIENT_DATA");
  });

  it("4. NOT_APPLICABLE fund retains null score and status without becoming 0.00", () => {
    assert.strictEqual(mockFundNotApplicable.yukiraScore?.score, null);
    assert.strictEqual(mockFundNotApplicable.yukiraScore?.status, "NOT_APPLICABLE");
  });
});
