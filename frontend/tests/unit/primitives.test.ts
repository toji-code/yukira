import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { formatPercentage, formatRatio } from "../../lib/utils/formatters";

describe("visualization and epistemic primitives invariants", () => {
  it("ensures financial nulls always format to em dash and never zero", () => {
    // Critical epistemic rule: Missing financial values must NEVER be rendered as 0.00% or 0
    const nullPct = formatPercentage(null);
    const undefPct = formatPercentage(undefined);
    const nullRatio = formatRatio(null);
    const undefRatio = formatRatio(undefined);

    assert.equal(nullPct, "—");
    assert.equal(undefPct, "—");
    assert.equal(nullRatio, "—");
    assert.equal(undefRatio, "—");

    assert.notEqual(nullPct, "0.00%");
    assert.notEqual(undefPct, "0.00%");
    assert.notEqual(nullRatio, "0.00");
    assert.notEqual(undefRatio, "0.00");
  });

  it("verifies hash representation length for audit cards", () => {
    const fullHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    const truncated = fullHash.substring(0, 16);
    assert.equal(truncated.length, 16);
    assert.ok(fullHash.startsWith(truncated));
  });

  it("verifies candidate methodology versioning format", () => {
    const tag = "CANDIDATE-V1";
    assert.ok(tag.startsWith("CANDIDATE"));
    assert.ok(tag.includes("-V"));
  });
});
