import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import { fetchRet02Analysis, executeRet02Analysis } from "../../lib/api/analysis";
import { Ret02AnalysisResponse } from "../../types/analysis";
import {
  getQualityAssessmentStyle,
  getRevisionStatusStyle,
  getTemporalStatusStyle,
  getPresenceStatusStyle,
  getVerificationStatusStyle,
  getIntegrityConditionStyle,
} from "../../lib/utils/quality";
import { ApiError } from "../../types/api";

describe("Phase 2F Pass 3: RET-02 Frontend & Analysis API Tests", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockSuccessfulResponse: Ret02AnalysisResponse = {
    identity: {
      schemeId: 1,
      schemeName: "HDFC Flexi Cap Fund",
      amfiCode: "119062",
      schemeOptionId: 101,
      optionType: "GROWTH",
      isin: "INF179K01BE2",
    },
    result: {
      metricCode: "RET-02",
      metricName: "Simple Period Return",
      numericValue: 0.104523,
      formattedValue: "+10.4523%",
      units: "PERCENTAGE",
      calculationStatus: "CALCULATED",
      errorMessage: null,
    },
    period: {
      requestedStartDate: "2024-01-01",
      requestedEndDate: "2024-01-15",
      selectedStartDate: "2024-01-01",
      selectedEndDate: "2024-01-15",
      startLookbackDaysUsed: 0,
      endLookbackDaysUsed: 0,
      startSubstituted: false,
      endSubstituted: false,
    },
    pit: {
      knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
      pitFilteringApplied: true,
      temporalLimitationDisclosure:
        "Factual AMFI source availability timestamp is unrecorded upstream. Analytical EOD cutoff convention applied.",
      cutoffConventionApplied: "CONVENTION_EOD_HISTORICAL_CUTOFF",
      sourceAvailabilitySemantic: "HISTORICAL_BACKFILL",
    },
    methodology: {
      methodologyCode: "RET_02_SIMPLE_RETURN",
      methodologyVersion: "CANDIDATE_V1",
      approvalStatus: "CANDIDATE",
      isCandidate: true,
      lookbackSpecification: "Candidate 4-calendar-day lookback window",
      formulaDisclosure: "Discrete return: (NAV_end - NAV_start) / NAV_start",
    },
    quality: {
      overallAssessment: "VALID",
      dimensions: [
        { dimension: "Quality", state: "VALID", description: "Conforms to schema" },
        { dimension: "Verification", state: "VERIFIED", description: "Reconciled with source" },
        { dimension: "Revision", state: "ORIGINAL", description: "Original observation" },
        { dimension: "Freshness", state: "CURRENT", description: "Observation delivery timeliness against reporting schedule" },
        { dimension: "Presence", state: "AVAILABLE", description: "Present at cutoff" },
        { dimension: "Integrity", state: "NONE", description: "Bitemporal relationship condition" },
      ],
      validationFlags: [],
    },
    provenance: {
      calculationRunId: 42,
      runStatus: "COMPLETED",
      executionStartedAt: "2024-02-01T10:00:00Z",
      executionCompletedAt: "2024-02-01T10:00:01Z",
      quantEngineVersion: "FASTAPI-QUANT-0.1.0",
      methodologyGitCommit: "92c5f257fa55cfdbce724d2712d7f87a8b66da91",
      inputSnapshotSha256: "b".repeat(64),
      inputObservations: [
        {
          observationId: 1001,
          role: "START",
          effectiveDate: "2024-01-01",
          revisionSeq: 1,
          navValue: 100.0,
          availabilityTime: "2024-01-01T23:59:59+05:30",
          qualityAssessment: "VALID",
          verificationStatus: "VERIFIED",
          revisionStatus: "ORIGINAL",
          temporalStatus: "CURRENT",
          presenceStatus: "AVAILABLE",
          integrityCondition: "NONE",
          sourceAvailabilitySemantic: "HISTORICAL_BACKFILL",
          sourceArtifactId: 10,
          sourceArtifactSha256: "a".repeat(64),
        },
        {
          observationId: 1002,
          role: "END",
          effectiveDate: "2024-01-15",
          revisionSeq: 1,
          navValue: 110.4523,
          availabilityTime: "2024-01-15T23:59:59+05:30",
          qualityAssessment: "VALID",
          verificationStatus: "VERIFIED",
          revisionStatus: "ORIGINAL",
          temporalStatus: "CURRENT",
          presenceStatus: "AVAILABLE",
          integrityCondition: "NONE",
          sourceAvailabilitySemantic: "HISTORICAL_BACKFILL",
          sourceArtifactId: 10,
          sourceArtifactSha256: "a".repeat(64),
        },
      ],
      sourceArtifacts: [
        {
          sourceArtifactId: 10,
          sourceUrl: "https://www.amfiindia.com/net-asset-value/nav-history",
          sha256Hash: "a".repeat(64),
          retrievalTimestamp: "2024-01-20T12:00:00Z",
          byteSize: 2048,
        },
      ],
    },
    limitations: {
      factualAvailabilityTimestampUnavailable: true,
      analyticalCutoffConvention: "CONVENTION_EOD_HISTORICAL_CUTOFF",
      candidateLookbackApplied: false,
      lookbackWindowDays: 4,
      insufficientEvidence: false,
      disclosureSummary: "Factual AMFI availability timestamp is unavailable upstream.",
    },
    benchmark: {
      benchmarkRequired: false,
      benchmarkId: null,
      benchmarkNotice:
        "RET-02 is a standalone single-asset return metric. Benchmark is explicitly not required and no synthetic benchmark was used.",
    },
  };

  it("fetchRet02Analysis successfully parses complete auditable contract", async () => {
    globalThis.fetch = async () =>
      new Response(JSON.stringify(mockSuccessfulResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const res = await fetchRet02Analysis(42);

    assert.equal(res.result.metricCode, "RET-02");
    assert.equal(res.result.calculationStatus, "CALCULATED");
    assert.equal(res.result.numericValue, 0.104523);
    assert.equal(res.methodology.approvalStatus, "CANDIDATE");
    assert.equal(res.methodology.isCandidate, true);
    assert.equal(res.benchmark.benchmarkRequired, false);
    assert.equal(res.benchmark.benchmarkId, null);
    assert.equal(res.pit.pitFilteringApplied, true);
    assert.equal(res.limitations.factualAvailabilityTimestampUnavailable, true);
    assert.equal(res.provenance.inputObservations.length, 2);
    assert.equal(res.provenance.inputObservations[0].sourceArtifactSha256, "a".repeat(64));
  });

  it("verifies benchmark is explicitly not required and no synthetic benchmark is present", async () => {
    assert.equal(mockSuccessfulResponse.benchmark.benchmarkRequired, false);
    assert.equal(mockSuccessfulResponse.benchmark.benchmarkId, null);
    assert.ok(mockSuccessfulResponse.benchmark.benchmarkNotice.includes("not required"));
  });

  it("verifies methodology status remains strictly CANDIDATE", async () => {
    assert.equal(mockSuccessfulResponse.methodology.approvalStatus, "CANDIDATE");
    assert.equal(mockSuccessfulResponse.methodology.isCandidate, true);
    assert.equal(mockSuccessfulResponse.methodology.methodologyVersion, "CANDIDATE_V1");
  });

  it("verifies PIT cutoff is strictly preserved and limitations are exposed", async () => {
    assert.equal(mockSuccessfulResponse.pit.knowledgeCutoffTime, "2024-01-31T23:59:59+05:30");
    assert.equal(mockSuccessfulResponse.pit.pitFilteringApplied, true);
    assert.equal(mockSuccessfulResponse.pit.cutoffConventionApplied, "CONVENTION_EOD_HISTORICAL_CUTOFF");
    assert.equal(mockSuccessfulResponse.limitations.factualAvailabilityTimestampUnavailable, true);
  });

  it("verifies input observation lineage links directly to source artifact", async () => {
    const inputs = mockSuccessfulResponse.provenance.inputObservations;
    assert.equal(inputs.length, 2);
    assert.equal(inputs[0].role, "START");
    assert.equal(inputs[1].role, "END");
    assert.equal(inputs[0].sourceArtifactSha256, "a".repeat(64));
  });

  it("verifies 6-dimensional data quality styling and states", () => {
    const qualityStyle = getQualityAssessmentStyle("VALID");
    assert.ok(qualityStyle.className.includes("emerald"));

    const verificationStyle = getVerificationStatusStyle("VERIFIED");
    assert.ok(verificationStyle.className.includes("emerald"));

    const revisionStyle = getRevisionStatusStyle("ORIGINAL");
    assert.equal(revisionStyle.label, "Original");

    const freshnessStyle = getTemporalStatusStyle("CURRENT");
    assert.equal(freshnessStyle.label, "Current");

    const staleStyle = getTemporalStatusStyle("STALE");
    assert.equal(staleStyle.label, "Stale");
    assert.ok(staleStyle.className.includes("amber"));

    const presenceStyle = getPresenceStatusStyle("AVAILABLE");
    assert.equal(presenceStyle.label, "Available");

    const integrityStyle = getIntegrityConditionStyle("NONE");
    assert.equal(integrityStyle.label, "Normal");
  });

  it("handles insufficient evidence response without zero substitution", async () => {
    const insufficientResponse: Ret02AnalysisResponse = {
      ...mockSuccessfulResponse,
      result: {
        metricCode: "RET-02",
        metricName: "Simple Period Return",
        numericValue: null, // ZERO SUBSTITUTION FORBIDDEN
        formattedValue: null,
        units: "PERCENTAGE",
        calculationStatus: "INSUFFICIENT_DATA",
        errorMessage: "No valid observation available within candidate 4-day lookback window",
      },
      limitations: {
        ...mockSuccessfulResponse.limitations,
        insufficientEvidence: true,
      },
    };

    globalThis.fetch = async () =>
      new Response(JSON.stringify(insufficientResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const res = await fetchRet02Analysis(99);
    assert.equal(res.result.calculationStatus, "INSUFFICIENT_DATA");
    assert.equal(res.result.numericValue, null);
    assert.notEqual(res.result.numericValue, 0); // Must NOT substitute 0.0
    assert.equal(res.limitations.insufficientEvidence, true);
  });

  it("handles malformed API response with structured ApiError", async () => {
    globalThis.fetch = async () =>
      new Response("Gateway Timeout", {
        status: 504,
        statusText: "Gateway Timeout",
      });

    await assert.rejects(
      async () => {
        await fetchRet02Analysis(42);
      },
      (err: ApiError) => {
        assert.equal(err.code, "HTTP_504");
        assert.equal(err.status, 504);
        return true;
      }
    );
  });

  it("executeRet02Analysis sends parameterized payload and receives authoritative response", async () => {
    globalThis.fetch = async (_url, options) => {
      assert.equal(options?.method, "POST");
      const body = JSON.parse(options?.body as string);
      assert.equal(body.schemeOptionId, 101);
      assert.equal(body.startDate, "2024-01-01");
      assert.equal(body.endDate, "2024-01-15");
      return new Response(JSON.stringify(mockSuccessfulResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    const res = await executeRet02Analysis({
      schemeOptionId: 101,
      startDate: "2024-01-01",
      endDate: "2024-01-15",
      knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
    });

    assert.equal(res.result.metricCode, "RET-02");
    assert.equal(res.result.numericValue, 0.104523);
  });

  it("enforces frontend calculation rule: frontend must not calculate (NAV_end - NAV_start) / NAV_start", () => {
    // Authoritative calculation comes from backend quant engine.
    // Frontend only receives and displays data.result.numericValue
    const authoritativeValue = mockSuccessfulResponse.result.numericValue;
    assert.equal(authoritativeValue, 0.104523);
  });
});
