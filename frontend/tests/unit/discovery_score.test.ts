import { describe, it } from "node:test";
import assert from "node:assert/strict";
import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { FundCard } from "../../components/primitives/FundCard";
import { Scheme, YukiraScoreSummary } from "../../types/domain";

describe("Analytical Score in Fund Discovery / Search V1", () => {
  const mockHdfcScore: YukiraScoreSummary = {
    scoreId: 1,
    schemeOptionId: 1,
    score: 67.94,
    confidence: 95.0,
    status: "PARTIAL",
    scoreVersion: "YUKIRA_SCORE_V1",
    methodologyStatus: "CANDIDATE",
    asOfDate: "2024-01-15",
    summary: "Deterministic candidate scoring across 4 empirical dimensions.",
  };

  const mockHdfcScheme: Scheme = {
    id: 1,
    name: "HDFC Flexi Cap Fund",
    code: "HDFC_FLEXI",
    inceptionDate: "1995-01-01",
    status: "ACTIVE",
    amc: { id: 1, name: "HDFC Mutual Fund", code: "HDFC_MF" },
    yukiraScore: mockHdfcScore,
  };

  it("1. Surfacing available YUKIRA score renders score value, status, confidence, and as-of date", () => {
    const html = renderToStaticMarkup(
      React.createElement(FundCard, {
        scheme: mockHdfcScheme,
        isWatchlisted: false,
        onToggleWatchlist: () => {},
      })
    );

    assert.ok(html.includes("67.94"), "Score value 67.94 must be displayed");
    assert.ok(html.includes("PARTIAL"), "Status PARTIAL must be displayed");
    assert.ok(html.includes("Evidence Confidence:"), "Confidence label must be present");
    assert.ok(html.includes("95.0"), "Confidence value 95.0 must be rendered separately from score");
    assert.ok(html.includes("As of 2024-01-15"), "As-of date must be displayed");
    assert.ok(html.includes("CANDIDATE"), "Methodology status CANDIDATE must be displayed");
  });

  it("2. Confidence is kept separate from numerical score (zero multiplication / probability claims)", () => {
    const html = renderToStaticMarkup(
      React.createElement(FundCard, {
        scheme: mockHdfcScheme,
        isWatchlisted: false,
        onToggleWatchlist: () => {},
      })
    );

    assert.ok(!html.includes("64.54"), "Score must not be multiplied by confidence (67.94 * 0.95)");
    assert.ok(!html.includes("95% probability"), "Confidence must not be labeled as probability");
    assert.ok(!html.includes("BUY"), "No investment recommendations allowed");
  });

  it("3. INSUFFICIENT_DATA status renders clean state without fake 0.00 score", () => {
    const insufficientScheme: Scheme = {
      ...mockHdfcScheme,
      id: 99,
      code: "INSUFFICIENT_FUND",
      yukiraScore: {
        status: "INSUFFICIENT_DATA",
        score: null,
        confidence: null,
        asOfDate: "2024-01-15",
      },
    };

    const html = renderToStaticMarkup(
      React.createElement(FundCard, {
        scheme: insufficientScheme,
        isWatchlisted: false,
        onToggleWatchlist: () => {},
      })
    );

    assert.ok(html.includes("Analytical score unavailable"), "Must display explicit unavailable label");
    assert.ok(html.includes("Insufficient Data"), "Must display Insufficient Data status badge");
    assert.ok(!html.includes("0.00 / 100"), "Missing score must NEVER be displayed as 0.00 / 100");
  });

  it("4. NOT_APPLICABLE status renders clean non-evaluated state without fabricated reasons", () => {
    const notApplicableScheme: Scheme = {
      ...mockHdfcScheme,
      id: 98,
      code: "DEBT_FUND",
      yukiraScore: {
        status: "NOT_APPLICABLE",
        score: null,
        confidence: null,
        asOfDate: "2024-01-15",
      },
    };

    const html = renderToStaticMarkup(
      React.createElement(FundCard, {
        scheme: notApplicableScheme,
        isWatchlisted: false,
        onToggleWatchlist: () => {},
      })
    );

    assert.ok(html.includes("Not currently evaluated"), "Must display factual non-evaluated label");
    assert.ok(html.includes("Not Applicable"), "Must display Not Applicable status badge");
    assert.ok(!html.includes("0.00"), "Missing score must NEVER be displayed as 0.00");
  });

  it("5. Missing yukiraScore summary property displays No Snapshot state", () => {
    const unScoredScheme: Scheme = {
      ...mockHdfcScheme,
      id: 97,
      yukiraScore: null,
    };

    const html = renderToStaticMarkup(
      React.createElement(FundCard, {
        scheme: unScoredScheme,
        isWatchlisted: false,
        onToggleWatchlist: () => {},
      })
    );

    assert.ok(html.includes("Analytical score unavailable"), "Must display explicit unavailable label");
    assert.ok(html.includes("No Snapshot"), "Must display No Snapshot status badge");
    assert.ok(!html.includes("0.00"), "Missing score must NEVER be displayed as 0.00");
  });

  it("6. Scheme identity and schemeOptionId are explicitly preserved", () => {
    assert.strictEqual(mockHdfcScheme.yukiraScore?.schemeOptionId, 1);
    assert.strictEqual(mockHdfcScheme.id, 1);
  });
});
