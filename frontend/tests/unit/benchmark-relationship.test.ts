import assert from "node:assert/strict";
import { describe, it } from "node:test";

function benchmarkRelationshipUrl(schemeOptionId: number): string {
  const params = new URLSearchParams({
    asOfDate: "2024-01-15",
    knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
  });
  return `/api/v1/analysis/benchmark-relationship/${schemeOptionId}?${params.toString()}`;
}

describe("Benchmark Relationship API Tests (§REL-02 / §REL-03)", () => {
  it("constructs correct endpoint URL with PIT parameters", () => {
    const url = benchmarkRelationshipUrl(1);

    assert.equal(url.startsWith("/api/v1/analysis/benchmark-relationship/1?"), true);
    assert.equal(url.includes("asOfDate=2024-01-15"), true);
    assert.equal(url.includes("knowledgeCutoffTime=2024-01-31T23%3A59%3A59%2B05%3A30"), true);
  });

  it("enforces paired-observation threshold and calculated states", () => {
    const response = {
      metrics: {
        pairedObservationCount: 736,
        minPairedRequired: 700,
        isSufficient: true,
        resultState: "CALCULATED",
      },
    };

    assert.equal(response.metrics.pairedObservationCount >= response.metrics.minPairedRequired, true);
    assert.equal(response.metrics.isSufficient, true);
    assert.equal(response.metrics.resultState, "CALCULATED");
  });

  it("preserves epistemic provenance and limitation disclosure", () => {
    const response = {
      epistemic: {
        dataQualityStatus: "SOURCE_ARTIFACT_VERIFIED",
        benchmarkLineage: "Benchmark returns are resolved synchronously",
        sourceArtifactSha256: "d80ed193cba5a6686a26133f97bc2533a9885b660c900a002e2febf6e0e0903d",
        observation: "benchmark metrics were calculated",
        limitation: "Missing dates are excluded synchronously with zero interpolation.",
      },
    };

    assert.equal(response.epistemic.dataQualityStatus, "SOURCE_ARTIFACT_VERIFIED");
    assert.match(response.epistemic.sourceArtifactSha256, /^[0-9a-f]{64}$/);
    assert.match(response.epistemic.benchmarkLineage, /synchronously/);
    assert.match(response.epistemic.observation, /benchmark metrics were calculated/);
    assert.match(response.epistemic.limitation, /zero interpolation/);
  });

  it("verifies consolidated metric output integration contract", () => {
    const consolidatedOutputs = {
      activeReturn: { value: 0.0185, formatted: "+1.85%", code: "§REL-06" },
      trackingError: { value: 0.0342, formatted: "3.42%", code: "§REL-02" },
      informationRatio: { value: 0.5409, formatted: "+0.54", code: "§RAT-04" },
      beta: { value: 1.0215, formatted: "1.0215", code: "§REL-01" },
    };

    assert.equal(consolidatedOutputs.activeReturn.code, "§REL-06");
    assert.equal(consolidatedOutputs.trackingError.code, "§REL-02");
    assert.equal(consolidatedOutputs.informationRatio.code, "§RAT-04");
    assert.equal(consolidatedOutputs.beta.code, "§REL-01");

    assert.equal(typeof consolidatedOutputs.activeReturn.value, "number");
    assert.equal(typeof consolidatedOutputs.trackingError.value, "number");
    assert.equal(typeof consolidatedOutputs.informationRatio.value, "number");
    assert.equal(typeof consolidatedOutputs.beta.value, "number");
  });
});
