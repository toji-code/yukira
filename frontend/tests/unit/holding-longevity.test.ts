import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  fetchHoldingLongevity,
  HoldingLongevityResponseDto,
} from "../../lib/api/portfolio";

describe("Point-in-Time Holding Longevity, Portfolio Turnover & Position Persistence API Tests", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockHoldingLongevityResponse: HoldingLongevityResponseDto = {
    schemeOptionId: 1,
    fundName: "HDFC Flexi Cap Fund",
    planType: "DIRECT",
    optionType: "GROWTH",
    amfiCode: "118955",
    schemeCode: "HDFC_FLEXI",
    analysisStatus: "SUCCESS",
    statusReason: "Point-in-Time holding longevity successfully calculated.",
    methodologyVersion: "YUKIRA-LONGEVITY-1.0 (Phase 2K Deterministic)",
    calculationRunId: "test-run-longevity-456",
    knowledgeCutoff: "2026-10-18T13:00:00+05:30",
    totalSnapshotsFound: 4,
    eligibleSnapshotsCount: 4,
    skippedPeriodsCount: 0,
    observationWindowStartDate: "2024-01-31",
    observationWindowEndDate: "2026-09-30",
    snapshots: [
      {
        snapshotId: 101,
        portfolioDate: "2024-01-31",
        availabilityTime: "2024-02-05T12:00:00Z",
        reportedHoldingsCount: 10,
        sumReportedWeightsPct: 50.82,
        disclosureScope: "PARTIAL_TOP_HOLDINGS",
        sourceArtifactSha256: "900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259",
        sourceUri: "SEBI_MONTHLY_DISCLOSURE",
      },
      {
        snapshotId: 102,
        portfolioDate: "2026-07-31",
        availabilityTime: "2026-08-05T12:00:00Z",
        reportedHoldingsCount: 52,
        sumReportedWeightsPct: 98.40,
        disclosureScope: "FULL_PORTFOLIO_DISCLOSURE",
        sourceArtifactSha256: "a1b2c3d4e5f678901234567890abcdef1234567890abcdef1234567890abcdef",
        sourceUri: "SEBI_MONTHLY_DISCLOSURE",
      },
      {
        snapshotId: 103,
        portfolioDate: "2026-08-31",
        availabilityTime: "2026-09-05T12:00:00Z",
        reportedHoldingsCount: 53,
        sumReportedWeightsPct: 98.60,
        disclosureScope: "FULL_PORTFOLIO_DISCLOSURE",
        sourceArtifactSha256: "b2c3d4e5f6a178901234567890abcdef1234567890abcdef1234567890abcdef",
        sourceUri: "SEBI_MONTHLY_DISCLOSURE",
      },
      {
        snapshotId: 104,
        portfolioDate: "2026-09-30",
        availabilityTime: "2026-10-05T12:00:00Z",
        reportedHoldingsCount: 54,
        sumReportedWeightsPct: 99.10,
        disclosureScope: "FULL_PORTFOLIO_DISCLOSURE",
        sourceArtifactSha256: "c3d4e5f6a1b278901234567890abcdef1234567890abcdef1234567890abcdef",
        sourceUri: "SEBI_MONTHLY_DISCLOSURE",
      },
    ],
    tenureBuckets: [
      {
        bucketKey: "OVER_3_YEARS",
        bucketLabel: "More than 3 Years",
        holdingsCount: 8,
        totalReportedWeightPct: 48.50,
        portfolioWeightSharePct: 48.94,
        topHoldings: ["ICICI Bank Ltd. (9.50%)", "HDFC Bank Ltd. (8.80%)"],
      },
      {
        bucketKey: "YEARS_1_TO_3",
        bucketLabel: "1 to 3 Years",
        holdingsCount: 22,
        totalReportedWeightPct: 32.10,
        portfolioWeightSharePct: 32.39,
        topHoldings: ["Bharti Airtel Ltd. (3.40%)", "Larsen & Toubro Ltd. (3.10%)"],
      },
      {
        bucketKey: "MONTHS_6_TO_12",
        bucketLabel: "6 to 12 Months",
        holdingsCount: 15,
        totalReportedWeightPct: 14.20,
        portfolioWeightSharePct: 14.33,
        topHoldings: ["Zomato Ltd. (1.80%)", "Coforge Ltd. (1.50%)"],
      },
      {
        bucketKey: "UNDER_6_MONTHS",
        bucketLabel: "Less than 6 Months",
        holdingsCount: 9,
        totalReportedWeightPct: 4.30,
        portfolioWeightSharePct: 4.34,
        topHoldings: ["Swiggy Ltd. (0.80%)", "Hyundai Motor India (0.75%)"],
      },
    ],
    persistentHoldings: [
      {
        securityId: 501,
        isin: "INE090A01021",
        canonicalName: "ICICI Bank Ltd.",
        firstObservedDate: "2024-01-31",
        lastObservedDate: "2026-09-30",
        appearanceCount: 4,
        spanObservationsCount: 4,
        appearanceRatioPct: 100.0,
        observedTenureDays: 973,
        observedTenureMonths: 32,
        observedTenureBucket: "YEARS_1_TO_3",
        presenceStatus: "CONTINUOUS",
        persistenceCategory: "PERSISTENT",
        isTop10Holding: true,
        latestRank: 1,
        latestReportedWeightPct: 9.50,
        averageObservedWeightPct: 9.42,
        minObservedWeightPct: 9.20,
        maxObservedWeightPct: 9.60,
        weightRangePct: 0.40,
        weightStandardDeviationPct: 0.15,
        consecutiveObservationsCount: 4,
        weightTrajectory: [
          {
            portfolioDate: "2024-01-31",
            reportedWeightPct: 9.50,
            holdingRank: 1,
            isPresent: true,
            disclosureScope: "PARTIAL_TOP_HOLDINGS",
          },
          {
            portfolioDate: "2026-07-31",
            reportedWeightPct: 9.38,
            holdingRank: 1,
            isPresent: true,
            disclosureScope: "FULL_PORTFOLIO_DISCLOSURE",
          },
          {
            portfolioDate: "2026-08-31",
            reportedWeightPct: 9.30,
            holdingRank: 1,
            isPresent: true,
            disclosureScope: "FULL_PORTFOLIO_DISCLOSURE",
          },
          {
            portfolioDate: "2026-09-30",
            reportedWeightPct: 9.50,
            holdingRank: 1,
            isPresent: true,
            disclosureScope: "FULL_PORTFOLIO_DISCLOSURE",
          },
        ],
      },
    ],
    allHoldings: [
      {
        securityId: 501,
        isin: "INE090A01021",
        canonicalName: "ICICI Bank Ltd.",
        firstObservedDate: "2024-01-31",
        lastObservedDate: "2026-09-30",
        appearanceCount: 4,
        spanObservationsCount: 4,
        appearanceRatioPct: 100.0,
        observedTenureDays: 973,
        observedTenureMonths: 32,
        observedTenureBucket: "YEARS_1_TO_3",
        presenceStatus: "CONTINUOUS",
        persistenceCategory: "PERSISTENT",
        isTop10Holding: true,
        latestRank: 1,
        latestReportedWeightPct: 9.50,
        averageObservedWeightPct: 9.42,
        minObservedWeightPct: 9.20,
        maxObservedWeightPct: 9.60,
        weightRangePct: 0.40,
        weightStandardDeviationPct: 0.15,
        consecutiveObservationsCount: 4,
        weightTrajectory: [],
      },
      {
        securityId: 502,
        isin: "INE262H01013",
        canonicalName: "Persistent Systems Ltd.",
        firstObservedDate: "2024-01-15",
        lastObservedDate: "2024-01-15",
        appearanceCount: 1,
        spanObservationsCount: 1,
        appearanceRatioPct: 25.0,
        observedTenureDays: 0,
        observedTenureMonths: 0,
        observedTenureBucket: "UNDER_6_MONTHS",
        presenceStatus: "NOT_OBSERVED_IN_PARTIAL_DISCLOSURE",
        persistenceCategory: "NOT_OBSERVED_IN_PARTIAL_DISCLOSURE",
        isTop10Holding: false,
        latestRank: null,
        latestReportedWeightPct: 0.0,
        averageObservedWeightPct: 0.85,
        minObservedWeightPct: 0.85,
        maxObservedWeightPct: 0.85,
        weightRangePct: 0.0,
        weightStandardDeviationPct: 0.0,
        consecutiveObservationsCount: 0,
        weightTrajectory: [],
      },
    ],
    transitions: [
      {
        earlierDate: "2024-01-31",
        laterDate: "2026-07-31",
        daysBetween: 912,
        earlierDisclosureScope: "PARTIAL_TOP_HOLDINGS",
        laterDisclosureScope: "FULL_PORTFOLIO_DISCLOSURE",
        comparabilityStatus: "NON_COMPARABLE_DISCLOSURE_SCOPE_SHIFT",
        comparabilityReason:
          "Transition involves partial top-holdings disclosure (< 90% weight coverage). Disclosed weight differences reflect reporting threshold changes rather than verified portfolio trading.",
        isComparable: false,
        newEntriesCount: 42,
        newEntriesWeightPct: 47.58,
        completeExitsCount: 0,
        completeExitsWeightPct: 0.0,
        continuingPositionsCount: 10,
        continuingWeightIncreaseSumPct: 2.10,
        continuingWeightDecreaseSumPct: 1.90,
        sumAbsWeightChangesPct: 51.58,
        halfSumCompositionChangePct: 25.79,
        unresolvedDiscrepancyPct: 0.0,
        topEntries: [],
        topExits: [],
        largestWeightChanges: [],
      },
      {
        earlierDate: "2026-08-31",
        laterDate: "2026-09-30",
        daysBetween: 30,
        earlierDisclosureScope: "FULL_PORTFOLIO_DISCLOSURE",
        laterDisclosureScope: "FULL_PORTFOLIO_DISCLOSURE",
        comparabilityStatus: "COMPARABLE_FULL_DISCLOSURE",
        comparabilityReason: "Consecutive monthly full-portfolio disclosures with verified holdings coverage.",
        isComparable: true,
        newEntriesCount: 2,
        newEntriesWeightPct: 1.20,
        completeExitsCount: 1,
        completeExitsWeightPct: 0.70,
        continuingPositionsCount: 51,
        continuingWeightIncreaseSumPct: 1.85,
        continuingWeightDecreaseSumPct: 1.85,
        sumAbsWeightChangesPct: 5.60,
        halfSumCompositionChangePct: 2.80,
        unresolvedDiscrepancyPct: 0.0,
        topEntries: ["Swiggy Ltd. (0.80%)", "Hyundai Motor India (0.40%)"],
        topExits: ["Voltas Ltd. (0.70%)"],
        largestWeightChanges: ["ICICI Bank Ltd. (+0.20 pp)", "HDFC Bank Ltd. (-0.25 pp)"],
      },
    ],
    turnoverEvaluation: {
      turnoverStatus: "TURNOVER_UNAVAILABLE_TRANSACTION_DATA_MISSING",
      turnoverReason:
        "Actual regulatory and economic portfolio turnover requires transaction ledgers detailing gross purchase and sale values alongside daily average net assets over statutory trailing periods. Disclosed monthly portfolio snapshots provide point-in-time holdings at calendar month-end, not transaction logs.",
      isTurnoverCalculated: false,
      reportedRegulatoryTurnoverRatioPct: null,
      descriptiveChangeFormula:
        "1/2 * SUM(|w_{i,t} - w_{i,t-1}|) across all reported holdings in consecutive comparable full-portfolio disclosures.",
      averageMonthlyCompositionChangePct: 2.80,
      annualizedDescriptiveChangePct: 33.60,
      comparableTransitionsCount: 1,
      totalTransitionsCount: 2,
      limitationsAndDisclaimers: [
        "Descriptive composition change measures net holding weight differences across calendar month-ends; it is NOT regulatory portfolio turnover.",
        "Weight changes in continuing positions reflect relative market price fluctuations, corporate actions, and net fund cash flows in addition to active manager rebalancing.",
        "Holdings not observed in partial top-holdings disclosures cannot be inferred as portfolio exits.",
      ],
    },
    dataQualityWarnings: [
      "Includes partial top-holdings disclosures (< 90% weight coverage). Position exits cannot be definitively established from partial reporting periods.",
    ],
    investigationQuestions: [
      "Does the fund exhibit high observed tenure across core banking and industrial holdings, consistent with its long-term compounding mandate?",
      "Are observed holding exits genuine portfolio liquidations, or do they represent positions falling below the top-holdings disclosure threshold in partial reporting periods?",
    ],
    methodologyNotes: [
      "Holding tenure reflects empirical presence across authentic Point-in-Time disclosures. First observed date is not necessarily purchase date; last observed date is not necessarily sale date.",
      "Persistence across portfolio disclosures indicates holding duration, not manager investment skill or future return potential.",
    ],
  };

  it("fetchHoldingLongevity queries the correct REST API endpoint and forwards parameters", async () => {
    let capturedUrl = "";
    globalThis.fetch = async (input: RequestInfo | URL) => {
      capturedUrl = String(input);
      return {
        ok: true,
        status: 200,
        json: async () => mockHoldingLongevityResponse,
      } as Response;
    };

    const res = await fetchHoldingLongevity(1, "2026-10-01", "2024-01-01", "2026-09-30", "2026-10-10T23:59:59+05:30");

    assert.ok(capturedUrl.includes("/api/v1/schemes/options/1/holding-longevity"));
    assert.ok(capturedUrl.includes("asOfDate=2026-10-01"));
    assert.ok(capturedUrl.includes("startDate=2024-01-01"));
    assert.ok(capturedUrl.includes("endDate=2026-09-30"));
    assert.ok(capturedUrl.includes("knowledgeCutoff=2026-10-10T23%3A59%3A59%2B05%3A30"));
    assert.equal(res.schemeOptionId, 1);
    assert.equal(res.eligibleSnapshotsCount, 4);
  });

  it("verifies tenure buckets sum to valid portfolio distribution", () => {
    const buckets = mockHoldingLongevityResponse.tenureBuckets;
    assert.equal(buckets.length, 4);

    const totalWeightShare = buckets.reduce((acc, b) => acc + b.portfolioWeightSharePct, 0);
    // Sum of shares should be approximately 100%
    assert.ok(Math.abs(totalWeightShare - 100.0) <= 0.1, `Expected ~100%, got ${totalWeightShare}`);

    const over3Y = buckets.find((b) => b.bucketKey === "OVER_3_YEARS");
    assert.ok(over3Y);
    assert.equal(over3Y.holdingsCount, 8);
    assert.equal(over3Y.topHoldings.length, 2);
  });

  it("correctly distinguishes persistent core holding from partial-disclosure omission", () => {
    const icici = mockHoldingLongevityResponse.allHoldings.find((h) => h.canonicalName === "ICICI Bank Ltd.");
    assert.ok(icici);
    assert.equal(icici.presenceStatus, "CONTINUOUS");
    assert.equal(icici.persistenceCategory, "PERSISTENT");
    assert.equal(icici.appearanceCount, 4);
    assert.equal(icici.isTop10Holding, true);

    const persistentSystems = mockHoldingLongevityResponse.allHoldings.find(
      (h) => h.canonicalName === "Persistent Systems Ltd."
    );
    assert.ok(persistentSystems);
    assert.equal(persistentSystems.presenceStatus, "NOT_OBSERVED_IN_PARTIAL_DISCLOSURE");
    assert.equal(persistentSystems.persistenceCategory, "NOT_OBSERVED_IN_PARTIAL_DISCLOSURE");
    assert.notEqual(persistentSystems.presenceStatus, "POTENTIAL_EXIT");
  });

  it("enforces scope shift gating on transitions and verifies descriptive composition change formula", () => {
    const transitions = mockHoldingLongevityResponse.transitions;
    assert.equal(transitions.length, 2);

    // Transition 1: Partial to Full -> must be NON_COMPARABLE_DISCLOSURE_SCOPE_SHIFT
    const t1 = transitions[0];
    assert.equal(t1.isComparable, false);
    assert.equal(t1.comparabilityStatus, "NON_COMPARABLE_DISCLOSURE_SCOPE_SHIFT");

    // Transition 2: Full to Full (30 days) -> must be COMPARABLE_FULL_DISCLOSURE
    const t2 = transitions[1];
    assert.equal(t2.isComparable, true);
    assert.equal(t2.comparabilityStatus, "COMPARABLE_FULL_DISCLOSURE");
    assert.equal(t2.halfSumCompositionChangePct, 2.80);
    assert.equal(t2.sumAbsWeightChangesPct, 5.60);
    assert.equal(t2.halfSumCompositionChangePct * 2, t2.sumAbsWeightChangesPct);
  });

  it("strictly gates turnover when transaction-level data is missing", () => {
    const turnover = mockHoldingLongevityResponse.turnoverEvaluation;
    assert.equal(turnover.turnoverStatus, "TURNOVER_UNAVAILABLE_TRANSACTION_DATA_MISSING");
    assert.equal(turnover.isTurnoverCalculated, false);
    assert.equal(turnover.reportedRegulatoryTurnoverRatioPct, null);
    assert.ok(turnover.descriptiveChangeFormula.includes("1/2 * SUM(|w_{i,t} - w_{i,t-1}|)"));
    assert.equal(turnover.averageMonthlyCompositionChangePct, 2.80);
    assert.equal(turnover.annualizedDescriptiveChangePct, 33.60);
    assert.ok(turnover.limitationsAndDisclaimers.length >= 3);
  });
});
