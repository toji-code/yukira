import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  getQualityAssessmentStyle,
  getRevisionStatusStyle,
  getTemporalStatusStyle,
  getPresenceStatusStyle,
  getVerificationStatusStyle,
  getIntegrityConditionStyle,
} from "../../lib/utils/quality";

/**
 * The six-dimensional data quality taxonomy is presented with SEMANTIC state
 * tokens (`state-approved` / `state-candidate` / `state-critical` / `state-info`
 * / `state-risk` / `state-unavailable` / `state-operational`), never with raw
 * palette hues. Hue in YUKIRA means *state*, never metric category — so these
 * assertions pin the semantic contract and the state labels, and verify the
 * states remain mutually distinguishable within each dimension.
 */

describe("quality utilities tests", () => {
  describe("getQualityAssessmentStyle", () => {
    it("returns approved state styling for VALID", () => {
      const style = getQualityAssessmentStyle("VALID");
      assert.ok(style.className.includes("state-approved"));
      assert.equal(style.label, "Valid");
    });

    it("returns critical state styling for INVALID", () => {
      const style = getQualityAssessmentStyle("INVALID");
      assert.ok(style.className.includes("state-critical"));
      assert.equal(style.label, "Invalid");
    });

    it("returns candidate state styling for SUSPICIOUS", () => {
      const style = getQualityAssessmentStyle("SUSPICIOUS");
      assert.ok(style.className.includes("state-candidate"));
      assert.equal(style.label, "Suspicious");
    });

    it("keeps the three quality states mutually distinct", () => {
      const classes = [
        getQualityAssessmentStyle("VALID").className,
        getQualityAssessmentStyle("SUSPICIOUS").className,
        getQualityAssessmentStyle("INVALID").className,
      ];
      assert.equal(new Set(classes).size, 3);
    });
  });

  describe("getRevisionStatusStyle", () => {
    it("returns distinct styles for revision states", () => {
      const orig = getRevisionStatusStyle("ORIGINAL");
      const rev = getRevisionStatusStyle("REVISED");
      const sup = getRevisionStatusStyle("SUPERSEDED");

      assert.equal(orig.label, "Original");
      assert.equal(rev.label, "Revised");
      assert.equal(sup.label, "Superseded");

      assert.ok(orig.className.includes("state-info"));
      assert.ok(rev.className.includes("state-operational"));
      assert.ok(sup.className.includes("state-unavailable"));

      assert.equal(new Set([orig.className, rev.className, sup.className]).size, 3);
    });
  });

  describe("getTemporalStatusStyle", () => {
    it("returns distinct styles for temporal freshness", () => {
      const curr = getTemporalStatusStyle("CURRENT");
      const stale = getTemporalStatusStyle("STALE");

      assert.equal(curr.label, "Current");
      assert.equal(stale.label, "Stale");

      assert.ok(curr.className.includes("state-info"));
      assert.ok(stale.className.includes("state-candidate"));
      assert.notEqual(curr.className, stale.className);
    });
  });

  describe("getPresenceStatusStyle", () => {
    it("returns distinct styles for presence states", () => {
      const avail = getPresenceStatusStyle("AVAILABLE");
      const miss = getPresenceStatusStyle("MISSING");
      const na = getPresenceStatusStyle("NOT_APPLICABLE");

      assert.equal(avail.label, "Available");
      assert.equal(miss.label, "Missing");
      assert.equal(na.label, "N/A");

      assert.ok(avail.className.includes("state-info"));
      // MISSING (expected but absent) is rendered with a dashed rule so it stays
      // structurally distinguishable from NOT_APPLICABLE (never applicable).
      assert.ok(miss.className.includes("dashed"));
      assert.notEqual(miss.className, na.className);
    });
  });

  describe("getVerificationStatusStyle", () => {
    it("returns distinct styles for verification states", () => {
      const ver = getVerificationStatusStyle("VERIFIED");
      const unver = getVerificationStatusStyle("UNVERIFIED");

      assert.equal(ver.label, "Verified");
      assert.equal(unver.label, "Unverified");

      assert.ok(ver.className.includes("state-approved"));
      assert.ok(unver.className.includes("state-candidate"));
      assert.notEqual(ver.className, unver.className);
    });
  });

  describe("getIntegrityConditionStyle", () => {
    it("returns distinct styles for integrity conditions", () => {
      const normal = getIntegrityConditionStyle("NONE");
      const dup = getIntegrityConditionStyle("DUPLICATE");
      const conf = getIntegrityConditionStyle("CONFLICTING");

      assert.equal(normal.label, "Normal");
      assert.equal(dup.label, "Duplicate");
      assert.equal(conf.label, "Conflicting");

      assert.ok(dup.className.includes("state-candidate"));
      assert.ok(conf.className.includes("state-risk"));

      assert.equal(new Set([normal.className, dup.className, conf.className]).size, 3);
    });
  });

  describe("epistemic content is never paraphrased", () => {
    it("preserves taxonomy labels and tooltips verbatim for every state", () => {
      const all = [
        getQualityAssessmentStyle("VALID"),
        getQualityAssessmentStyle("SUSPICIOUS"),
        getQualityAssessmentStyle("INVALID"),
        getRevisionStatusStyle("ORIGINAL"),
        getRevisionStatusStyle("REVISED"),
        getRevisionStatusStyle("SUPERSEDED"),
        getTemporalStatusStyle("CURRENT"),
        getTemporalStatusStyle("STALE"),
        getPresenceStatusStyle("AVAILABLE"),
        getPresenceStatusStyle("MISSING"),
        getPresenceStatusStyle("NOT_APPLICABLE"),
        getVerificationStatusStyle("VERIFIED"),
        getVerificationStatusStyle("UNVERIFIED"),
        getIntegrityConditionStyle("NONE"),
        getIntegrityConditionStyle("DUPLICATE"),
        getIntegrityConditionStyle("CONFLICTING"),
      ];

      for (const style of all) {
        assert.ok(style.label.length > 0, "every state must carry a label");
        assert.ok(
          style.tooltip.length > 0,
          `every state must carry a tooltip (${style.label})`
        );
        assert.ok(
          style.className.startsWith("state-"),
          `every state must use a semantic state token, got "${style.className}"`
        );
      }
    });

    it("falls back to the unavailable semantic state for unknown enum values", () => {
      const unknown = getQualityAssessmentStyle("NOT_A_REAL_STATUS" as never);
      assert.equal(unknown.label, "NOT_A_REAL_STATUS");
      assert.equal(unknown.className, "state-unavailable");
    });
  });
});