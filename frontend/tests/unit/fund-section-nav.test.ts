import { describe, it } from "node:test";
import assert from "node:assert";
import { FUND_SECTIONS } from "../../components/analysis/FundSectionNav";

describe("Fund Profile In-Page Section Navigation Configuration", () => {
  it("defines the expected 13 analytical section anchors for Fund Detail page", () => {
    assert.strictEqual(FUND_SECTIONS.length, 13, "Fund profile must contain exactly 13 top-level section anchors");

    const expectedSectionIds = [
      "identity",
      "coverage",
      "return-quality",
      "risk",
      "drawdown",
      "tail-risk",
      "risk-adjusted",
      "benchmark-relationship",
      "portfolio",
      "investment-modes",
      "provenance",
      "data-quality",
      "decision-support",
    ];

    const actualIds = FUND_SECTIONS.map((s) => s.id);
    assert.deepStrictEqual(actualIds, expectedSectionIds);
  });

  it("ensures all section navigation items have non-empty labels and unique IDs", () => {
    const idSet = new Set<string>();

    for (const item of FUND_SECTIONS) {
      assert.ok(item.id && item.id.trim().length > 0, "Section ID must be non-empty");
      assert.ok(item.label && item.label.trim().length > 0, `Section ${item.id} must have a label`);
      assert.ok(item.shortLabel && item.shortLabel.trim().length > 0, `Section ${item.id} must have a short label`);
      assert.strictEqual(idSet.has(item.id), false, `Duplicate section ID found: ${item.id}`);
      idSet.add(item.id);
    }
  });

  it("maintains quantitative taxonomy badges for dimensions and core sections", () => {
    const itemMap = new Map(FUND_SECTIONS.map((s) => [s.id, s]));

    assert.strictEqual(itemMap.get("identity")?.badge, "Sec 1");
    assert.strictEqual(itemMap.get("coverage")?.badge, "Sec 2");
    assert.strictEqual(itemMap.get("return-quality")?.badge, "Dim 1");
    assert.strictEqual(itemMap.get("risk")?.badge, "Dim 2");
    assert.strictEqual(itemMap.get("drawdown")?.badge, "Dim 3");
    assert.strictEqual(itemMap.get("tail-risk")?.badge, "Dim 4");
    assert.strictEqual(itemMap.get("risk-adjusted")?.badge, "Dim 5");
    assert.strictEqual(itemMap.get("benchmark-relationship")?.badge, "Dim 5.5");
    assert.strictEqual(itemMap.get("portfolio")?.badge, "Dim 6");
    assert.strictEqual(itemMap.get("investment-modes")?.badge, "Sec 3");
  });
});
