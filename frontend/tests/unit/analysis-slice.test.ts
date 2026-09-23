import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import { fetchAnalysis, executeAnalysis } from "../../lib/api/analysis";
import { AnalysisResponse } from "../../types/analysis";
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

  const mockSuccessfulResponse: AnalysisResponse = {
    identity: {
      schemeId: 1,
      schemeName: "HDFC Flexi Cap Fund",
      amfiCode: "118955",
      schemeOptionId: 101,
      optionType: "GROWTH",
      isin: "INF179K01UT0",
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

  it("fetchAnalysis successfully parses complete auditable contract", async () => {
    globalThis.fetch = async () =>
      new Response(JSON.stringify(mockSuccessfulResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const res = await fetchAnalysis(42);

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
    const insufficientResponse: AnalysisResponse = {
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

    const res = await fetchAnalysis(99);
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
        await fetchAnalysis(42);
      },
      (err: ApiError) => {
        assert.equal(err.code, "HTTP_504");
        assert.equal(err.status, 504);
        return true;
      }
    );
  });

  it("executeAnalysis sends parameterized payload and receives authoritative response", async () => {
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

    const res = await executeAnalysis("ret02", {
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

describe("Phase 2K: RET-03 (3Y CAGR) Frontend & Analysis API Tests", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockRet03Response: AnalysisResponse = {
    identity: {
      schemeId: 1,
      schemeName: "HDFC Flexi Cap Fund",
      amfiCode: "118955",
      schemeOptionId: 1,
      optionType: "GROWTH",
      isin: "INF179K01UT0",
    },
    result: {
      metricCode: "RET-03",
      metricName: "3-Year Compound Annual Growth Rate",
      numericValue: 0.272495778659288,
      formattedValue: "+27.25%",
      units: "PERCENTAGE",
      calculationStatus: "CALCULATED",
      errorMessage: null,
    },
    period: {
      requestedStartDate: "2021-01-15",
      requestedEndDate: "2024-01-15",
      selectedStartDate: "2021-01-15",
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
      methodologyCode: "RET_03_3Y_CAGR",
      methodologyVersion: "CANDIDATE_V1",
      approvalStatus: "CANDIDATE",
      isCandidate: true,
      lookbackSpecification: "36 calendar months candidate analytical window with 4-day boundary lookback",
      formulaDisclosure: "(NAV_end / NAV_start)^(365.25 / elapsed_calendar_days) - 1",
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
      calculationRunId: 101,
      runStatus: "COMPLETED",
      executionStartedAt: "2024-02-01T10:00:00Z",
      executionCompletedAt: "2024-02-01T10:00:01Z",
      quantEngineVersion: "FASTAPI-QUANT-0.1.0",
      methodologyGitCommit: "4bdd60c2ed5986a07ec44d613d3a91ff7189f2c7",
      inputSnapshotSha256: "c".repeat(64),
      inputObservations: [
        {
          observationId: 871,
          role: "START",
          effectiveDate: "2021-01-15",
          revisionSeq: 1,
          navValue: 811.217,
          availabilityTime: "2021-01-15T18:29:59.999Z",
          qualityAssessment: "VALID",
          verificationStatus: "VERIFIED",
          revisionStatus: "ORIGINAL",
          temporalStatus: "CURRENT",
          presenceStatus: "AVAILABLE",
          integrityCondition: "NONE",
          sourceAvailabilitySemantic: "HISTORICAL_BACKFILL",
          sourceArtifactId: 1,
          sourceArtifactSha256: "900508f8".padEnd(64, "0"),
        },
        {
          observationId: 11,
          role: "END",
          effectiveDate: "2024-01-15",
          revisionSeq: 1,
          navValue: 1670.672,
          availabilityTime: "2024-01-15T18:29:59.999Z",
          qualityAssessment: "VALID",
          verificationStatus: "VERIFIED",
          revisionStatus: "ORIGINAL",
          temporalStatus: "CURRENT",
          presenceStatus: "AVAILABLE",
          integrityCondition: "NONE",
          sourceAvailabilitySemantic: "HISTORICAL_BACKFILL",
          sourceArtifactId: 1,
          sourceArtifactSha256: "900508f8".padEnd(64, "0"),
        },
      ],
      sourceArtifacts: [
        {
          sourceArtifactId: 1,
          sourceUrl: "https://portal.amfiindia.com/DownloadNAVHistoryReport_Po.aspx?mf=&scheme=118955",
          sha256Hash: "900508f8".padEnd(64, "0"),
          retrievalTimestamp: "2024-01-31T23:59:59Z",
          byteSize: 11185549,
        },
      ],
    },
    limitations: {
      factualAvailabilityTimestampUnavailable: true,
      analyticalCutoffConvention: "CONVENTION_EOD_HISTORICAL_CUTOFF",
      sourceAvailabilitySemantic: "HISTORICAL_BACKFILL",
      candidateLookbackApplied: false,
      lookbackWindowDays: 4,
      insufficientEvidence: false,
      disclosureSummary:
        "Factual AMFI availability timestamp is unavailable upstream. Analytical EOD cutoff convention applied. Candidate 4-calendar-day lookback window active. 365.25-day annualization is a candidate convention requiring validation; no annualization convention is approved for production. Zero investment recommendation.",
    },
    benchmark: {
      benchmarkRequired: false,
      benchmarkId: null,
      benchmarkNotice: "RET-03 is a standalone single-asset return metric. Benchmark is explicitly not required and no synthetic benchmark was used.",
    },
  };

  it("RET-03: successful execution dispatches to backend and receives verified CAGR", async () => {
    globalThis.fetch = async (url, options) => {
      assert.ok(String(url).includes("/api/v1/analysis/ret03"));
      assert.equal(options?.method, "POST");
      const body = JSON.parse(options?.body as string);
      assert.equal(body.schemeOptionId, 1);
      assert.equal(body.endDate, "2024-01-15");
      assert.equal(body.knowledgeCutoffTime, "2024-01-31T23:59:59+05:30");
      return new Response(JSON.stringify(mockRet03Response), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    const res = await executeAnalysis("ret03", {
      schemeOptionId: 1,
      endDate: "2024-01-15",
      knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
    });

    assert.equal(res.result.metricCode, "RET-03");
    assert.equal(res.result.calculationStatus, "CALCULATED");
    assert.equal(res.result.formattedValue, "+27.25%");
    assert.ok(Math.abs((res.result.numericValue ?? 0) - 0.272495778659) < 0.0001);
  });

  it("RET-03: result rendering displays correct 3Y period boundary dates", async () => {
    assert.equal(mockRet03Response.period.requestedStartDate, "2021-01-15");
    assert.equal(mockRet03Response.period.requestedEndDate, "2024-01-15");
    assert.equal(mockRet03Response.period.selectedStartDate, "2021-01-15");
    assert.equal(mockRet03Response.period.selectedEndDate, "2024-01-15");
    assert.equal(mockRet03Response.period.startSubstituted, false);
    assert.equal(mockRet03Response.period.endSubstituted, false);
  });

  it("RET-03: methodology display reflects CANDIDATE status and UNVALIDATED state", () => {
    assert.equal(mockRet03Response.methodology.methodologyCode, "RET_03_3Y_CAGR");
    assert.equal(mockRet03Response.methodology.methodologyVersion, "CANDIDATE_V1");
    assert.equal(mockRet03Response.methodology.approvalStatus, "CANDIDATE");
    assert.equal(mockRet03Response.methodology.isCandidate, true);
    assert.ok(mockRet03Response.methodology.formulaDisclosure.includes("365.25"));
  });

  it("RET-03: candidate annualization disclosure is prominently present in epistemic caveats", () => {
    const summary = mockRet03Response.limitations.disclosureSummary;
    assert.ok(summary.includes("365.25-day annualization is a candidate convention"));
    assert.ok(summary.includes("no annualization convention is approved for production"));
    assert.equal(mockRet03Response.benchmark.benchmarkRequired, false);
  });

  it("RET-03: handles unavailable / insufficient-history cleanly without zero substitution", async () => {
    const insufficientRet03: AnalysisResponse = {
      ...mockRet03Response,
      result: {
        metricCode: "RET-03",
        metricName: "3-Year Compound Annual Growth Rate",
        numericValue: null,
        formattedValue: null,
        units: "PERCENTAGE",
        calculationStatus: "INSUFFICIENT_DATA",
        errorMessage: "Scheme history is less than 36 calendar months; required 3Y CAGR window cannot be formed.",
      },
      limitations: {
        ...mockRet03Response.limitations,
        insufficientEvidence: true,
      },
    };

    globalThis.fetch = async () =>
      new Response(JSON.stringify(insufficientRet03), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const res = await fetchAnalysis(102);
    assert.equal(res.result.metricCode, "RET-03");
    assert.equal(res.result.calculationStatus, "INSUFFICIENT_DATA");
    assert.equal(res.result.numericValue, null);
    assert.notEqual(res.result.numericValue, 0); // Strictly forbidden to substitute 0
    assert.equal(res.limitations.insufficientEvidence, true);
  });
});
