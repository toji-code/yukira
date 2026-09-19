import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  formatPercentage,
  formatBasisPoints,
  formatRatio,
  formatCurrency,
  formatNumber,
  formatDate,
  parseDiagnostics,
} from "../../lib/utils/formatters";

describe("formatters utility tests", () => {
  describe("formatPercentage", () => {
    it("returns em dash for null and undefined", () => {
      assert.equal(formatPercentage(null), "—");
      assert.equal(formatPercentage(undefined), "—");
    });

    it("formats positive numbers with plus sign when requested", () => {
      assert.equal(formatPercentage(0.1234, 2, true), "+12.34%");
    });

    it("formats negative numbers with minus sign", () => {
      assert.equal(formatPercentage(-0.052), "-5.20%");
    });

    it("formats zero without sign", () => {
      assert.equal(formatPercentage(0), "0.00%");
    });

    it("omits sign by default for positive numbers", () => {
      assert.equal(formatPercentage(0.1234), "12.34%");
    });

    it("respects custom decimals count", () => {
      assert.equal(formatPercentage(0.123456, 4, false), "12.3456%");
    });
  });

  describe("formatBasisPoints", () => {
    it("returns em dash for null and undefined", () => {
      assert.equal(formatBasisPoints(null), "—");
      assert.equal(formatBasisPoints(undefined), "—");
    });

    it("correctly converts fractional return to basis points with plus sign", () => {
      assert.equal(formatBasisPoints(0.015), "+150 bps");
      assert.equal(formatBasisPoints(0.0001), "+1 bps");
    });
  });

  describe("formatRatio", () => {
    it("returns em dash for null and undefined", () => {
      assert.equal(formatRatio(null), "—");
      assert.equal(formatRatio(undefined), "—");
    });

    it("formats ratios with 2 decimal places and x suffix", () => {
      assert.equal(formatRatio(1.456), "1.46x");
      assert.equal(formatRatio(-0.25), "-0.25x");
    });
  });

  describe("formatCurrency", () => {
    it("returns em dash for null and undefined", () => {
      assert.equal(formatCurrency(null), "—");
      assert.equal(formatCurrency(undefined), "—");
    });

    it("formats positive amounts with currency symbol", () => {
      const result = formatCurrency(12500.5);
      assert.ok(result.includes("12,500.50"));
    });
  });

  describe("formatNumber", () => {
    it("returns em dash for null and undefined", () => {
      assert.equal(formatNumber(null), "—");
      assert.equal(formatNumber(undefined), "—");
    });

    it("formats integers with Indian numbering format and decimals", () => {
      assert.equal(formatNumber(1000000), "10,00,000.00");
    });
  });

  describe("formatDate", () => {
    it("returns em dash for null and undefined", () => {
      assert.equal(formatDate(null), "—");
      assert.equal(formatDate(undefined), "—");
    });

    it("formats valid ISO timestamp", () => {
      const formatted = formatDate("2026-03-15T10:30:00Z");
      assert.ok(formatted.includes("2026"));
      assert.ok(formatted.includes("Mar") || formatted.includes("03"));
    });
  });

  describe("parseDiagnostics", () => {
    it("returns null for null, undefined, or empty string", () => {
      assert.equal(parseDiagnostics(null), null);
      assert.equal(parseDiagnostics(undefined), null);
      assert.equal(parseDiagnostics(""), null);
    });

    it("returns parsed object for valid JSON string", () => {
      const json = '{"observations":252,"method":"log_returns"}';
      const parsed = parseDiagnostics(json);
      assert.deepEqual(parsed, { observations: 252, method: "log_returns" });
    });

    it("parses diagnostics directly from a MetricResult object", () => {
      const metricResult = {
        id: 1,
        metricCode: "RET-01",
        periodType: "1Y",
        numericValue: 0.15,
        stringValue: null,
        units: "PERCENTAGE",
        calculationStatus: "CALCULATED",
        diagnostics: '{"observations":252,"source":"amfi"}',
        errorMessage: null,
      };
      const parsed = parseDiagnostics(metricResult);
      assert.deepEqual(parsed, { observations: 252, source: "amfi" });
    });

    it("returns existing object if diagnostics is already an object", () => {
      const obj = { observations: 252 };
      assert.deepEqual(parseDiagnostics(obj), obj);
    });

    it("safely flags malformed JSON with malformed flag without throwing", () => {
      const invalid = "{ not-json }";
      assert.deepEqual(parseDiagnostics(invalid), { malformed: true, raw: invalid });
    });
  });
});
