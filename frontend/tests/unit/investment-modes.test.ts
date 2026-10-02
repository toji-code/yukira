import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  INVESTMENT_MODE_DATA_GAP,
  INVESTMENT_ROUTE_TERMS,
  NOT_AVAILABLE_TOKEN,
  SHARE_CLASS_TREATMENT_GUIDANCE,
  classifyShareClassTreatment,
  describeOptionType,
  describePlanType,
  describeRegistryStatus,
  summarizeShareClassCoverage,
} from "../../components/analysis/InvestmentModesView";
import { SchemeOption } from "../../types/domain";

function makeOption(partial: Partial<SchemeOption> & { id: number }): SchemeOption {
  return {
    id: partial.id,
    plan: partial.plan,
    optionType: partial.optionType ?? "GROWTH",
    amfiCode: partial.amfiCode ?? null,
    isin: partial.isin ?? null,
    status: partial.status ?? "ACTIVE",
  };
}

describe("SIP / Lumpsum Investment Information — taxonomy mappings", () => {
  it("maps persisted plan_type enums to investor language", () => {
    assert.equal(describePlanType("DIRECT"), "Direct Plan");
    assert.equal(describePlanType("REGULAR"), "Regular Plan");
    assert.equal(describePlanType("direct"), "Direct Plan");
  });

  it("maps persisted option_type enums to investor language", () => {
    assert.equal(describeOptionType("GROWTH"), "Growth Option (Accumulation)");
    assert.equal(describeOptionType("IDCW_PAYOUT"), "IDCW Payout Option (Dividend)");
    assert.equal(describeOptionType("IDCW_REINVESTMENT"), "IDCW Reinvestment Option (Dividend)");
  });

  it("never fabricates a taxonomy label for missing or unknown enum values", () => {
    for (const absent of [null, undefined, "", "   "]) {
      assert.equal(describePlanType(absent), NOT_AVAILABLE_TOKEN);
      assert.equal(describeOptionType(absent), NOT_AVAILABLE_TOKEN);
      assert.equal(describeRegistryStatus(absent), NOT_AVAILABLE_TOKEN);
    }

    // An unrecognised enum is surfaced verbatim rather than silently coerced.
    assert.equal(describeOptionType("SOMETHING_NEW"), "SOMETHING_NEW");
    assert.equal(describeRegistryStatus("MERGED"), "MERGED");
  });

  it("classifies share-class treatment only from the persisted option_type enum", () => {
    assert.equal(classifyShareClassTreatment("GROWTH"), "ACCUMULATION");
    assert.equal(classifyShareClassTreatment("IDCW_PAYOUT"), "DISTRIBUTION_PAYOUT");
    assert.equal(classifyShareClassTreatment("IDCW_REINVESTMENT"), "DISTRIBUTION_REINVESTMENT");
    assert.equal(classifyShareClassTreatment(null), "UNCLASSIFIED");
    assert.equal(classifyShareClassTreatment("UNKNOWN_ENUM"), "UNCLASSIFIED");
  });

  it("provides non-empty definitional guidance for every share-class treatment", () => {
    for (const treatment of [
      "ACCUMULATION",
      "DISTRIBUTION_PAYOUT",
      "DISTRIBUTION_REINVESTMENT",
      "UNCLASSIFIED",
    ] as const) {
      const guidance = SHARE_CLASS_TREATMENT_GUIDANCE[treatment];
      assert.ok(guidance && guidance.trim().length > 20, `${treatment} must carry real guidance`);
    }
  });
});

describe("SIP / Lumpsum Investment Information — zero-fabrication guarantees", () => {
  it("declares every SIP and lump-sum scheme term as an explicitly unavailable field", () => {
    // Critical epistemic rule: YUKIRA holds no AMC scheme terms. Every such term
    // must be enumerated as unavailable so that no gap is silently omitted.
    const sipLabels = INVESTMENT_ROUTE_TERMS.SIP.map((t) => t.label);
    const lumpLabels = INVESTMENT_ROUTE_TERMS.LUMPSUM.map((t) => t.label);

    for (const expected of [
      "Minimum SIP Amount",
      "Permitted SIP Frequencies",
      "Instalment Tenure & Minimum Instalments",
      "Step-up / Step-down Facility",
      "Instalment Debit Date & Cut-off Time",
      "SIP Exit Load / Deduction",
    ]) {
      assert.ok(sipLabels.includes(expected), `SIP route must disclose: ${expected}`);
    }

    for (const expected of [
      "Minimum Lump-sum Amount",
      "Purchase Cut-off Time & Applicable NAV",
      "Exit Load / Redemption Charge",
      "Lock-in Period",
    ]) {
      assert.ok(lumpLabels.includes(expected), `Lumpsum route must disclose: ${expected}`);
    }

    assert.equal(INVESTMENT_ROUTE_TERMS.SIP.length, sipLabels.length);
    assert.equal(INVESTMENT_ROUTE_TERMS.LUMPSUM.length, lumpLabels.length);
  });

  it("ships no numeric or amount placeholder inside any unavailable term", () => {
    const allTerms = [
      ...INVESTMENT_ROUTE_TERMS.SIP,
      ...INVESTMENT_ROUTE_TERMS.LUMPSUM,
    ];

    for (const term of allTerms) {
      assert.doesNotMatch(
        term.reason,
        /₹|rs\.?|\binr\b|\d+\s*(?:rupees|amount)|\d/,
        `Unavailable term "${term.label}" must not carry a fabricated numeric value`
      );
      assert.ok(term.reason.trim().length > 20, `Term "${term.label}" must explain its own absence`);
    }
  });

  it("publishes a data-gap disclosure that states the omission without recommending action", () => {
    assert.match(INVESTMENT_MODE_DATA_GAP.headline, /not in yukira/i);
    assert.match(INVESTMENT_MODE_DATA_GAP.body, /Not available/);
    assert.match(INVESTMENT_MODE_DATA_GAP.body, /AMC|fund house/i);

    // No suitability or recommendation language anywhere in the disclosure.
    assert.doesNotMatch(
      `${INVESTMENT_MODE_DATA_GAP.headline} ${INVESTMENT_MODE_DATA_GAP.body}`,
      /\b(buy|sell|hold|avoid|should invest|recommend(ed|s)?|best|ideal|suitable)\b/i,
      "Data-gap disclosure must not imply suitability or issue a recommendation"
    );
  });

  it("does not present registry status as a purchase-availability confirmation", () => {
    // describeRegistryStatus must be a verbatim passthrough: ACTIVE -> "ACTIVE",
    // never "ACTIVE (open for purchase)".
    assert.equal(describeRegistryStatus("ACTIVE"), "ACTIVE");
  });
});

describe("SIP / Lumpsum Investment Information — share class coverage summary", () => {
  const options: SchemeOption[] = [
    makeOption({ id: 1, optionType: "GROWTH", amfiCode: "118955", isin: "INF179K01UT0", plan: { id: 1, planType: "DIRECT", planCode: "HDFC_FLEXI_DIR", status: "ACTIVE" } }),
    makeOption({ id: 2, optionType: "IDCW_PAYOUT", amfiCode: "118956", isin: "INF179K01UT1", plan: { id: 1, planType: "DIRECT", planCode: "HDFC_FLEXI_DIR", status: "ACTIVE" } }),
    makeOption({ id: 3, optionType: "IDCW_REINVESTMENT", amfiCode: null, isin: null }),
    makeOption({ id: 4, optionType: "MYSTERY_CLASS", amfiCode: null, isin: null }),
  ];

  it("counts registered share classes and their persisted treatments deterministically", () => {
    const coverage = summarizeShareClassCoverage(options);

    assert.equal(coverage.total, 4);
    assert.equal(coverage.accumulation, 1);
    assert.equal(coverage.distributionPayout, 1);
    assert.equal(coverage.distributionReinvestment, 1);
    assert.equal(coverage.unclassified, 1);
  });

  it("counts only share classes that actually carry a transactional identifier", () => {
    const coverage = summarizeShareClassCoverage(options);
    assert.equal(coverage.registeredIdentifiers, 2, "Only options with an ISIN or AMFI code count");
  });

  it("keeps the per-treatment counts reconcilable against the total (no double counting)", () => {
    const coverage = summarizeShareClassCoverage(options);
    const summed =
      coverage.accumulation +
      coverage.distributionPayout +
      coverage.distributionReinvestment +
      coverage.unclassified;

    assert.equal(summed, coverage.total, "Every share class must fall into exactly one treatment");
  });

  it("returns zeroed counts for an empty scheme instead of throwing or inventing classes", () => {
    const coverage = summarizeShareClassCoverage([]);

    assert.equal(coverage.total, 0);
    assert.equal(coverage.accumulation, 0);
    assert.equal(coverage.distributionPayout, 0);
    assert.equal(coverage.distributionReinvestment, 0);
    assert.equal(coverage.unclassified, 0);
    assert.equal(coverage.registeredIdentifiers, 0);
  });
});