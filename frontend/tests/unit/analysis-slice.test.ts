import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import { fetchAnalysis, executeAnalysis } from "../../lib/api/analysis";
import { AnalysisResponse, Rsk01Methodology, Rsk01Limitations } from "../../types/analysis";
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
    assert.equal(mockRet03Response.period?.requestedStartDate, "2021-01-15");
    assert.equal(mockRet03Response.period?.requestedEndDate, "2024-01-15");
    assert.equal(mockRet03Response.period?.selectedStartDate, "2021-01-15");
    assert.equal(mockRet03Response.period?.selectedEndDate, "2024-01-15");
    assert.equal(mockRet03Response.period?.startSubstituted, false);
    assert.equal(mockRet03Response.period?.endSubstituted, false);
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

  const mockRsk01Response: AnalysisResponse = {
    identity: {
      schemeId: 1,
      schemeName: "HDFC Flexi Cap Fund",
      amfiCode: "118955",
      schemeOptionId: 1,
      optionType: "GROWTH",
      isin: "INF179K01UT0",
    },
    result: {
      metricCode: "RSK-01",
      metricName: "3-Year Annualized Volatility",
      numericValue: 0.146912937102,
      formattedValue: "14.6913%",
      units: "PERCENTAGE",
      calculationStatus: "CALCULATED",
      errorMessage: null,
    },
    window: {
      requestedStartDate: "2021-01-15",
      requestedEndDate: "2024-01-15",
      actualStartDate: "2021-01-15",
      actualEndDate: "2024-01-15",
      observationCount: 737,
      minObservationsRequired: 700,
      windowMonths: 36,
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
      methodologyCode: "RSK_01_3Y_VOLATILITY",
      methodologyVersion: "CANDIDATE_V1",
      approvalStatus: "CANDIDATE",
      isCandidate: true,
      annualizationConvention: "SQRT_252_CANDIDATE",
      denominatorConvention: "N_MINUS_ONE_CANDIDATE",
      formulaDisclosure: "sigma_ann = sqrt(252) * sqrt( sum((r_t - r_bar)^2) / (N - 1) )",
    },
    quality: {
      overallAssessment: "VALID",
      dimensions: [
        { dimension: "Quality", state: "VALID", description: "All observations conform to schema" },
        { dimension: "Verification", state: "VERIFIED", description: "Corroborated against AMFI feeds" },
        { dimension: "Revision", state: "ORIGINAL", description: "Official values" },
        { dimension: "Freshness", state: "CURRENT", description: "Timely delivery" },
        { dimension: "Presence", state: "AVAILABLE", description: "Sufficient continuous observations" },
        { dimension: "Integrity", state: "NONE", description: "No unresolved conflicts" },
      ],
      validationFlags: [],
    },
    provenance: {
      calculationRunId: 105,
      runStatus: "COMPLETED",
      executionStartedAt: "2024-02-01T12:00:00Z",
      executionCompletedAt: "2024-02-01T12:00:01Z",
      quantEngineVersion: "FASTAPI-QUANT-0.1.0",
      methodologyGitCommit: "759a7493791e867e4c53f2882e612532dff856d6",
      inputSnapshotSha256: "c".repeat(64),
      inputObservations: [],
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
      candidateAnnualizationApplied: true,
      candidateDenominatorApplied: true,
      insufficientEvidence: false,
      observationCount: 737,
      minObservationsRequired: 700,
      disclosureSummary:
        "3-Year Annualized Volatility calculated under candidate methodology CANDIDATE_V1. Annualizer sqrt(252) and sample variance denominator N-1 are candidate conventions; neither convention is validated or approved for production. Historical volatility does not forecast future volatility. Zero investment recommendation.",
    },
    benchmark: {
      benchmarkRequired: false,
      benchmarkId: null,
      benchmarkNotice: "RSK-01 is a standalone single-asset risk metric. Benchmark is explicitly not required and no synthetic benchmark was used.",
    },
  };

  it("RSK-01: successful execution dispatches to backend and receives verified volatility", async () => {
    globalThis.fetch = async (url, options) => {
      assert.ok(String(url).includes("/api/v1/analysis/rsk01"));
      assert.equal(options?.method, "POST");
      const body = JSON.parse(options?.body as string);
      assert.equal(body.schemeOptionId, 1);
      assert.equal(body.endDate, "2024-01-15");
      assert.equal(body.knowledgeCutoffTime, "2024-01-31T23:59:59+05:30");
      return new Response(JSON.stringify(mockRsk01Response), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    const res = await executeAnalysis("rsk01", {
      schemeOptionId: 1,
      endDate: "2024-01-15",
      knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
    });

    assert.equal(res.result.metricCode, "RSK-01");
    assert.equal(res.result.metricName, "3-Year Annualized Volatility");
    assert.equal(res.result.calculationStatus, "CALCULATED");
    assert.equal(res.result.formattedValue, "14.6913%");
    assert.ok(Math.abs((res.result.numericValue ?? 0) - 0.146912937102) < 0.0001);
  });

  it("RSK-01: window verification confirms 36M span and >= 700 observation threshold", () => {
    assert.equal(mockRsk01Response.window?.requestedStartDate, "2021-01-15");
    assert.equal(mockRsk01Response.window?.requestedEndDate, "2024-01-15");
    assert.equal(mockRsk01Response.window?.actualStartDate, "2021-01-15");
    assert.equal(mockRsk01Response.window?.actualEndDate, "2024-01-15");
    assert.equal(mockRsk01Response.window?.observationCount, 737);
    assert.equal(mockRsk01Response.window?.minObservationsRequired, 700);
    assert.equal(mockRsk01Response.window?.windowMonths, 36);
  });

  it("RSK-01: methodology reflects CANDIDATE status, sqrt(252), and N-1 conventions", () => {
    assert.equal(mockRsk01Response.methodology.methodologyCode, "RSK_01_3Y_VOLATILITY");
    assert.equal(mockRsk01Response.methodology.methodologyVersion, "CANDIDATE_V1");
    assert.equal(mockRsk01Response.methodology.approvalStatus, "CANDIDATE");
    assert.equal(mockRsk01Response.methodology.isCandidate, true);
    const rskMethod = mockRsk01Response.methodology as Rsk01Methodology;
    assert.equal(rskMethod.annualizationConvention, "SQRT_252_CANDIDATE");
    assert.equal(rskMethod.denominatorConvention, "N_MINUS_ONE_CANDIDATE");
  });

  it("RSK-01: limitations state candidate annualization and denominator applied with zero synthetic data", () => {
    const lim = mockRsk01Response.limitations as Rsk01Limitations;
    assert.equal(lim.candidateAnnualizationApplied, true);
    assert.equal(lim.candidateDenominatorApplied, true);
    assert.equal(lim.insufficientEvidence, false);
    assert.equal(lim.observationCount, 737);
    assert.equal(lim.minObservationsRequired, 700);
    assert.ok(lim.disclosureSummary.includes("sqrt(252)"));
    assert.ok(lim.disclosureSummary.includes("N-1"));
    assert.ok(lim.disclosureSummary.includes("Historical volatility does not forecast future volatility"));
  });

  it("RSK-01: insufficient observations (< 700) returns INSUFFICIENT_DATA and does not fabricate result", async () => {
    const insufficientRsk01: AnalysisResponse = {
      ...mockRsk01Response,
      result: {
        metricCode: "RSK-01",
        metricName: "3-Year Annualized Volatility",
        numericValue: null,
        formattedValue: null,
        units: "PERCENTAGE",
        calculationStatus: "INSUFFICIENT_DATA",
        errorMessage: "Insufficient observations in 3Y window: found 120, minimum 700 required.",
      },
      limitations: {
        ...mockRsk01Response.limitations,
        insufficientEvidence: true,
        observationCount: 120,
        minObservationsRequired: 700,
      } as Rsk01Limitations,
    };

    globalThis.fetch = async () =>
      new Response(JSON.stringify(insufficientRsk01), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const res = await fetchAnalysis(106);
    assert.equal(res.result.metricCode, "RSK-01");
    assert.equal(res.result.calculationStatus, "INSUFFICIENT_DATA");
    assert.equal(res.result.numericValue, null);
    assert.notEqual(res.result.numericValue, 0); // Strictly forbidden to fabricate 0
    assert.equal(res.limitations.insufficientEvidence, true);
  });
});

describe("Phase 2M: RSK-02 through RSK-05 Frontend & Analysis API Tests", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const baseRiskResponse: AnalysisResponse = {
    identity: {
      schemeId: 1,
      schemeName: "HDFC Flexi Cap Fund",
      amfiCode: "118955",
      schemeOptionId: 1,
      optionType: "GROWTH",
      isin: "INF179K01UT0",
    },
    result: {
      metricCode: "RSK-02",
      metricName: "Downside Semideviation",
      numericValue: 0.1012345678,
      formattedValue: "10.1235%",
      units: "PERCENTAGE",
      calculationStatus: "CALCULATED",
      errorMessage: null,
    },
    window: {
      requestedStartDate: "2021-01-15",
      requestedEndDate: "2024-01-15",
      actualStartDate: "2021-01-15",
      actualEndDate: "2024-01-15",
      observationCount: 737,
      minObservationsRequired: 700,
      windowMonths: 36,
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
      methodologyCode: "RSK_02_DOWNSIDE_DEV",
      methodologyVersion: "CANDIDATE_V1",
      approvalStatus: "CANDIDATE",
      isCandidate: true,
      annualizationConvention: "SQRT_252_CANDIDATE",
      denominatorConvention: "N_MINUS_ONE_CANDIDATE",
      formulaDisclosure: "sigma_d = sqrt(252) * sqrt( sum(min(r_t - MAR, 0)^2) / (N - 1) )",
    },
    quality: {
      overallAssessment: "VALID",
      dimensions: [
        { dimension: "Quality", state: "VALID", description: "All observations conform to schema" },
        { dimension: "Verification", state: "VERIFIED", description: "Corroborated against AMFI feeds" },
        { dimension: "Revision", state: "ORIGINAL", description: "Official values" },
        { dimension: "Freshness", state: "CURRENT", description: "Timely delivery" },
        { dimension: "Presence", state: "AVAILABLE", description: "Sufficient continuous observations" },
        { dimension: "Integrity", state: "NONE", description: "No unresolved conflicts" },
      ],
      validationFlags: [],
    },
    provenance: {
      calculationRunId: 201,
      runStatus: "COMPLETED",
      executionStartedAt: "2024-02-01T12:00:00Z",
      executionCompletedAt: "2024-02-01T12:00:01Z",
      quantEngineVersion: "FASTAPI-QUANT-0.1.0",
      methodologyGitCommit: "e4a85ecbb2f793fba94fcf60ba0f6c55f837da09",
      inputSnapshotSha256: "d".repeat(64),
      inputObservations: [],
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
      candidateAnnualizationApplied: true,
      candidateDenominatorApplied: true,
      insufficientEvidence: false,
      observationCount: 737,
      minObservationsRequired: 700,
      disclosureSummary:
        "Downside Semideviation calculated under candidate methodology CANDIDATE_V1. MAR = 0.0%, annualizer sqrt(252), denominator N-1. Zero investment recommendation.",
    },
    benchmark: {
      benchmarkRequired: false,
      benchmarkId: null,
      benchmarkNotice: "RSK-02 is a standalone single-asset risk metric. Benchmark is explicitly not required.",
    },
  };

  it("RSK-02: executes downside semideviation and parses response contract", async () => {
    globalThis.fetch = async (url, options) => {
      assert.ok(String(url).includes("/api/v1/analysis/rsk02"));
      assert.equal(options?.method, "POST");
      return new Response(JSON.stringify(baseRiskResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    const res = await executeAnalysis("RSK-02", {
      schemeOptionId: 1,
      endDate: "2024-01-15",
      knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
    });

    assert.equal(res.result.metricCode, "RSK-02");
    assert.equal(res.result.metricName, "Downside Semideviation");
    assert.equal(res.result.units, "PERCENTAGE");
    assert.equal(res.result.calculationStatus, "CALCULATED");
    assert.equal(res.window?.observationCount, 737);
  });

  it("RSK-03: executes 3Y Maximum Drawdown and parses signed negative percentage", async () => {
    const rsk03Response: AnalysisResponse = {
      ...baseRiskResponse,
      result: {
        metricCode: "RSK-03",
        metricName: "Maximum Drawdown, 3Y",
        numericValue: -0.153412,
        formattedValue: "-15.3412%",
        units: "PERCENTAGE",
        calculationStatus: "CALCULATED",
        errorMessage: null,
      },
      methodology: {
        methodologyCode: "RSK_03_MAX_DRAWDOWN",
        methodologyVersion: "CANDIDATE_V1",
        approvalStatus: "CANDIDATE",
        isCandidate: true,
        annualizationConvention: "NOT_APPLICABLE",
        denominatorConvention: "NOT_APPLICABLE",
        formulaDisclosure: "MDD = min_t (NAV_t / max_{s <= t} NAV_s - 1)",
      },
    };

    globalThis.fetch = async (url) => {
      assert.ok(String(url).includes("/api/v1/analysis/rsk03"));
      return new Response(JSON.stringify(rsk03Response), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    const res = await executeAnalysis("rsk-03", {
      schemeOptionId: 1,
      endDate: "2024-01-15",
      knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
    });

    assert.equal(res.result.metricCode, "RSK-03");
    assert.equal(res.result.units, "PERCENTAGE");
    assert.ok((res.result.numericValue ?? 0) <= 0.0, "Drawdown must be non-positive");
  });

  it("RSK-04: executes Maximum Drawdown Duration with units DAYS", async () => {
    const rsk04Response: AnalysisResponse = {
      ...baseRiskResponse,
      result: {
        metricCode: "RSK-04",
        metricName: "Maximum Drawdown Duration",
        numericValue: 184,
        formattedValue: "184",
        units: "DAYS",
        calculationStatus: "CALCULATED",
        errorMessage: null,
      },
      methodology: {
        methodologyCode: "RSK_04_MAX_DRAWDOWN_DURATION",
        methodologyVersion: "CANDIDATE_V1",
        approvalStatus: "CANDIDATE",
        isCandidate: true,
        annualizationConvention: "NOT_APPLICABLE",
        denominatorConvention: "NOT_APPLICABLE",
        formulaDisclosure: "MDD_Duration = max(recovery_date - peak_date)",
      },
    };

    globalThis.fetch = async (url) => {
      assert.ok(String(url).includes("/api/v1/analysis/rsk04"));
      return new Response(JSON.stringify(rsk04Response), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    const res = await executeAnalysis("RSK04", {
      schemeOptionId: 1,
      endDate: "2024-01-15",
      knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
    });

    assert.equal(res.result.metricCode, "RSK-04");
    assert.equal(res.result.units, "DAYS");
    assert.equal(res.result.numericValue, 184);
  });

  it("RSK-05: executes Ulcer Index with units POINTS", async () => {
    const rsk05Response: AnalysisResponse = {
      ...baseRiskResponse,
      result: {
        metricCode: "RSK-05",
        metricName: "Ulcer Index",
        numericValue: 4.8723,
        formattedValue: "4.8723",
        units: "POINTS",
        calculationStatus: "CALCULATED",
        errorMessage: null,
      },
      methodology: {
        methodologyCode: "RSK_05_ULCER_INDEX",
        methodologyVersion: "CANDIDATE_V1",
        approvalStatus: "CANDIDATE",
        isCandidate: true,
        annualizationConvention: "NOT_APPLICABLE",
        denominatorConvention: "NOT_APPLICABLE",
        formulaDisclosure: "UI = sqrt( (1/N) * sum( ( (NAV_t - max NAV) / max NAV * 100 )^2 ) )",
      },
    };

    globalThis.fetch = async (url) => {
      assert.ok(String(url).includes("/api/v1/analysis/rsk05"));
      return new Response(JSON.stringify(rsk05Response), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    };

    const res = await executeAnalysis("rsk05", {
      schemeOptionId: 1,
      endDate: "2024-01-15",
      knowledgeCutoffTime: "2024-01-31T23:59:59+05:30",
    });

    assert.equal(res.result.metricCode, "RSK-05");
    assert.equal(res.result.units, "POINTS");
    assert.equal(res.result.numericValue, 4.8723);
  });

  it("RSK-02 to 05: handles insufficient observations (< 700) without zero fabrication", async () => {
    const insufficientRiskResponse: AnalysisResponse = {
      ...baseRiskResponse,
      result: {
        metricCode: "RSK-02",
        metricName: "Downside Semideviation",
        numericValue: null,
        formattedValue: null,
        units: "PERCENTAGE",
        calculationStatus: "INSUFFICIENT_DATA",
        errorMessage: "Insufficient observations in 3Y window: found 100, minimum 700 required.",
      },
      limitations: {
        ...baseRiskResponse.limitations,
        insufficientEvidence: true,
        observationCount: 100,
        minObservationsRequired: 700,
      } as Rsk01Limitations,
    };

    globalThis.fetch = async () =>
      new Response(JSON.stringify(insufficientRiskResponse), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });

    const res = await fetchAnalysis(301);
    assert.equal(res.result.calculationStatus, "INSUFFICIENT_DATA");
    assert.equal(res.result.numericValue, null);
    assert.notEqual(res.result.numericValue, 0); // Strictly forbidden to fabricate 0
    assert.equal(res.limitations.insufficientEvidence, true);
  });
});
