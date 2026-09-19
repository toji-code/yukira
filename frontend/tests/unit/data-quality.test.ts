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

describe("quality utilities tests", () => {
  describe("getQualityAssessmentStyle", () => {
    it("returns emerald/green styling for VALID", () => {
      const style = getQualityAssessmentStyle("VALID");
      assert.ok(style.className.includes("emerald"));
      assert.equal(style.label, "Valid");
    });

    it("returns red/rose styling for INVALID", () => {
      const style = getQualityAssessmentStyle("INVALID");
      assert.ok(style.className.includes("rose"));
      assert.equal(style.label, "Invalid");
    });

    it("returns amber styling for SUSPICIOUS", () => {
      const style = getQualityAssessmentStyle("SUSPICIOUS");
      assert.ok(style.className.includes("amber"));
      assert.equal(style.label, "Suspicious");
    });
  });

  describe("getRevisionStatusStyle", () => {
    it("returns distinct styles for revision states", () => {
      const orig = getRevisionStatusStyle("ORIGINAL");
      const rev = getRevisionStatusStyle("REVISED");
      const sup = getRevisionStatusStyle("SUPERSEDED");

      assert.equal(orig.label, "Original");
      assert.ok(rev.className.includes("sky"));
      assert.equal(rev.label, "Revised");
      assert.ok(sup.className.includes("purple"));
      assert.equal(sup.label, "Superseded");
    });
  });

  describe("getTemporalStatusStyle", () => {
    it("returns distinct styles for temporal freshness", () => {
      const curr = getTemporalStatusStyle("CURRENT");
      const stale = getTemporalStatusStyle("STALE");

      assert.equal(curr.label, "Current");
      assert.ok(stale.className.includes("amber"));
      assert.equal(stale.label, "Stale");
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
      assert.ok(miss.className.includes("dashed"));
    });
  });

  describe("getVerificationStatusStyle", () => {
    it("returns distinct styles for verification states", () => {
      const ver = getVerificationStatusStyle("VERIFIED");
      const unver = getVerificationStatusStyle("UNVERIFIED");

      assert.ok(ver.className.includes("emerald"));
      assert.equal(ver.label, "Verified");
      assert.ok(unver.className.includes("amber"));
      assert.equal(unver.label, "Unverified");
    });
  });

  describe("getIntegrityConditionStyle", () => {
    it("returns distinct styles for integrity conditions", () => {
      const normal = getIntegrityConditionStyle("NONE");
      const dup = getIntegrityConditionStyle("DUPLICATE");
      const conf = getIntegrityConditionStyle("CONFLICTING");

      assert.equal(normal.label, "Normal");
      assert.ok(dup.className.includes("amber"));
      assert.equal(dup.label, "Duplicate");
      assert.ok(conf.className.includes("rose"));
      assert.equal(conf.label, "Conflicting");
    });
  });
});
