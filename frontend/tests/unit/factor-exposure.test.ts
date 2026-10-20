import { describe, it, afterEach } from "node:test";
import assert from "node:assert/strict";
import { fetchFactorExposure, PortfolioFactorExposureResponseDto } from "../../lib/api/portfolio";

describe("Portfolio Factor Exposure & Style Drift API Tests", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  const mockExposureResponse: PortfolioFactorExposureResponseDto = {
    schemeOptionId: 1,
    fundName: "HDFC Flexi Cap Fund",
    amfiCode: "118955",
    schemeCode: "HDFC_FLEXI",
    benchmarkCode: "NIFTY_500_TRI",
    benchmarkName: "Nifty 500 Total Returns Index",
    asOfDate: "2026-10-01",
    knowledgeCutoffTime: "2026-10-10T23:59:59+05:30",
    analysisStatus: "SUCCESS",
    statusReason: "Holdings-based factor exposure and style drift successfully calculated across 5 chronological disclosures.",
    currentExposure: {
      snapshotId: 5,
      portfolioDate: "2026-09-30",
      availabilityTime: "2026-10-05T18:00:00+05:30",
      sourceArtifactId: 105,
      sourceArtifactSha256: "900508f8bf137cb8ba02adae70a0eb6a0be7318389e9b3943f0bee738f3be259",
      totalHoldingsDisclosed: 85,
      sumReportedWeightsPct: 100.53,
      sumReportedWeightsPctFormatted: "100.53%",
      mappedWeightPct: 98.20,
      mappedWeightPctFormatted: "98.20%",
      unmappedWeightPct: 1.80,
      unmappedWeightPctFormatted: "1.80%",
      classificationCoveragePct: 98.20,
      classificationCoveragePctFormatted: "98.20%",
      marketCap: {
        largeCapWeightPct: 65.40,
        largeCapWeightPctFormatted: "65.40%",
        midCapWeightPct: 26.30,
        midCapWeightPctFormatted: "26.30%",
        smallCapWeightPct: 3.70,
        smallCapWeightPctFormatted: "3.70%",
        cashWeightPct: 2.80,
        cashWeightPctFormatted: "2.80%",
        debtOtherWeightPct: 0.00,
        debtOtherWeightPctFormatted: "0.00%",
        unclassifiedWeightPct: 1.80,
        unclassifiedWeightPctFormatted: "1.80%",
        classificationMethodology: "AMFI_SEBI_CIRCULAR_OCT_2017_PIT",
        classificationStatus: "VERIFIED_HISTORICAL_PIT",
        sourceRankingPeriod: "Jan-Jun 2026",
        sourcePublicationDate: "2026-07-06",
        effectiveFrom: "2026-08-01",
        effectiveTo: "2027-01-31",
        sourceArtifactId: 105,
        sourceArtifactSha256: "8e1256541331be9929abfeb1cd8531d72bd5e610559ef3b4ff77916c790a97ed",
        totalClassifiedHoldingsCount: 82,
        totalUnclassifiedHoldingsCount: 3,
      },
      otherFactors: {
        valueStatus: "UNAVAILABLE_PENDING_FUNDAMENTAL_FEED",
        qualityStatus: "UNAVAILABLE_PENDING_FUNDAMENTAL_FEED",
        momentumStatus: "UNAVAILABLE_PENDING_PRICE_SERIES_ALIGNMENT",
        documentation: "Value, Quality, and Momentum require security-level feeds not yet integrated.",
      },
      concentration: {
        top5WeightPct: 34.20,
        top5WeightPctFormatted: "34.20%",
        top10WeightPct: 52.10,
        top10WeightPctFormatted: "52.10%",
        hhi: 412.50,
        hhiClassification: "DIVERSIFIED",
      },
      topSectors: [
        { sector: "Financial Services", weightPct: 31.50, weightPctFormatted: "31.50%", isUnclassified: false },
        { sector: "Information Technology", weightPct: 12.40, weightPctFormatted: "12.40%", isUnclassified: false },
      ],
      completenessState: "FULL_DISCLOSURE",
      coverageLimitations: [],
    },
    driftHistory: [
      {
        snapshotId: 1,
        portfolioDate: "2024-01-31",
        availabilityTime: "2024-02-05T12:00:00+05:30",
        totalHoldingsDisclosed: 10,
        sumReportedWeightsPct: 54.82,
        mappedWeightPct: 54.82,
        classificationCoveragePct: 54.82,
        largeCapWeightPct: 54.82,
        midCapWeightPct: 0.00,
        smallCapWeightPct: 0.00,
        cashWeightPct: 0.00,
        debtOtherWeightPct: 0.00,
        unclassifiedWeightPct: 0.00,
        top5WeightPct: 34.20,
        top10WeightPct: 54.82,
        hhi: 360.00,
        classificationStatus: "VERIFIED_HISTORICAL_PIT",
        rankingPeriod: "Jul-Dec 2023",
        deltaFromPrevious: null,
      },
      {
        snapshotId: 2,
        portfolioDate: "2026-06-30",
        availabilityTime: "2026-07-05T18:00:00+05:30",
        totalHoldingsDisclosed: 77,
        sumReportedWeightsPct: 100.00,
        mappedWeightPct: 97.50,
        classificationCoveragePct: 97.50,
        largeCapWeightPct: 64.50,
        midCapWeightPct: 27.00,
        smallCapWeightPct: 4.00,
        cashWeightPct: 2.00,
        debtOtherWeightPct: 0.00,
        unclassifiedWeightPct: 2.50,
        top5WeightPct: 32.10,
        top10WeightPct: 50.80,
        hhi: 395.00,
        classificationStatus: "VERIFIED_HISTORICAL_PIT",
        rankingPeriod: "Jul-Dec 2025",
        deltaFromPrevious: {
          previousDate: "2024-01-31",
          largeCapDeltaPct: 9.68,
          largeCapDeltaPctFormatted: "+9.68%",
          midCapDeltaPct: 27.00,
          midCapDeltaPctFormatted: "+27.00%",
          smallCapDeltaPct: 4.00,
          smallCapDeltaPctFormatted: "+4.00%",
          cashDeltaPct: 2.00,
          cashDeltaPctFormatted: "+2.00%",
          top10DeltaPct: -4.02,
          top10DeltaPctFormatted: "-4.02%",
          hhiDelta: 35.00,
          coverageDeltaPct: 45.18,
          coverageDeltaPctFormatted: "+45.18%",
          isComparable: false,
          comparabilityNote: "Holdings weight coverage changed by +45.18% (54.82% -> 100.00%). Style drift is dominated by disclosure coverage expansion rather than portfolio rebalancing.",
          styleDriftAttribution: {
            largeCapRebalanceDeltaPct: 11.02,
            largeCapReclassifyDeltaPct: -1.34,
            largeCapCoverageDeltaPct: 0.00,
            largeCapNetDeltaPct: 9.68,
            midCapRebalanceDeltaPct: 25.66,
            midCapReclassifyDeltaPct: 1.34,
            midCapCoverageDeltaPct: 0.00,
            midCapNetDeltaPct: 27.00,
            smallCapRebalanceDeltaPct: 4.00,
            smallCapReclassifyDeltaPct: 0.00,
            smallCapCoverageDeltaPct: 0.00,
            smallCapNetDeltaPct: 4.00,
            attributionMethodology: "AMFI_POINT_IN_TIME_STYLE_DRIFT_DECOMPOSITION",
            attributionExplanation: "Separates active portfolio rebalancing and weight changes from AMFI categorization shifts across publication cycles.",
            reclassifiedHoldings: [
              {
                isin: "INE323A01026",
                companyName: "Bosch Ltd",
                previousCategory: "LARGE_CAP",
                previousRank: 87,
                currentCategory: "MID_CAP",
                currentRank: 101,
                portfolioWeightPct: 1.34,
                portfolioWeightPctFormatted: "1.34%",
                shiftDescription: "Reclassified by AMFI from LARGE_CAP (#87) to MID_CAP (#101)",
              },
            ],
          },
        },
      },
    ],
    largestObservedShifts: [
      {
        shiftDimension: "SIZE_LARGE_CAP",
        description: "Large-Cap exposure changed by +9.68% between 2024-01-31 and 2026-06-30.",
        fromDate: "2024-01-31",
        toDate: "2026-06-30",
        magnitudeDeltaPct: 9.68,
        magnitudeDeltaPctFormatted: "+9.68%",
        coincidesWithCoverageChange: true,
        evidenceNote: "Shift coincided with a disclosure coverage change (54.82% -> 100.00%).",
      },
    ],
    returnAttribution: {
      modelStatus: "METHODOLOGY_PREREQUISITES_MISSING",
      modelSpecification: "r_{f,t} - r_{0,t} = \\alpha + \\beta_{MKT} (r_{m,t} - r_{0,t}) + \\beta_{SMB} SMB_t + \\beta_{HML} HML_t + \\beta_{MOM} MOM_t + \\beta_{RMW} RMW_t + \\epsilon_t",
      missingPrerequisites: [
        {
          prerequisiteCode: "FACTOR_RETURNS_FEED",
          prerequisiteName: "Indian Factor Return Series (SMB, HML, MOM, RMW)",
          currentStatus: "MISSING_IN_REPOSITORY",
          technicalRequirement: "Audited daily factor portfolios for the Indian market.",
        },
      ],
      epistemicLimitations: [
        "Holdings-based exposure estimates portfolio characteristics at reporting dates, but does not establish empirical return attribution.",
      ],
      governanceDisclaimer: "Candidate methodology — not validated for production investment advice.",
    },
    epistemic: {
      methodologyVersion: "FACTOR_EXP_V1",
      governanceStatus: "CANDIDATE",
      disclaimer: "Candidate methodology — not validated for production investment advice.",
      factualObservations: [
        "Analyzed 2 official monthly disclosures.",
      ],
      investigationQuestions: [
        "Did the fund's large-cap allocation change due to market movement or deliberate rebalancing?",
      ],
    },
  };

  it("fetches factor exposure and parses valid response with correct query parameters", async () => {
    let capturedUrl = "";

    globalThis.fetch = (async (url: string | URL | Request) => {
      capturedUrl = url.toString();
      return {
        ok: true,
        status: 200,
        json: async () => mockExposureResponse,
      } as Response;
    }) as typeof fetch;

    const result = await fetchFactorExposure(1, "2026-10-01", "2026-10-10T23:59:59+05:30");

    assert.equal(result.schemeOptionId, 1);
    assert.equal(result.fundName, "HDFC Flexi Cap Fund");
    assert.equal(result.benchmarkCode, "NIFTY_500_TRI");
    assert.ok(capturedUrl.includes("/api/v1/schemes/options/1/holdings/factor-exposure"));
    assert.ok(capturedUrl.includes("asOfDate=2026-10-01"));
    assert.ok(capturedUrl.includes("knowledgeCutoff=2026-10-10T23%3A59%3A59%2B05%3A30"));
  });

  it("evaluates current market cap exposures and completeness status correctly", async () => {
    globalThis.fetch = (async () => ({
      ok: true,
      status: 200,
      json: async () => mockExposureResponse,
    })) as unknown as typeof fetch;

    const result = await fetchFactorExposure(1);

    assert.ok(result.currentExposure);
    assert.equal(result.currentExposure.marketCap.largeCapWeightPct, 65.40);
    assert.equal(result.currentExposure.marketCap.midCapWeightPct, 26.30);
    assert.equal(result.currentExposure.marketCap.smallCapWeightPct, 3.70);
    assert.equal(result.currentExposure.marketCap.cashWeightPct, 2.80);
    assert.equal(result.currentExposure.completenessState, "FULL_DISCLOSURE");
  });

  it("flags non-comparable intervals when coverage delta exceeds threshold", async () => {
    globalThis.fetch = (async () => ({
      ok: true,
      status: 200,
      json: async () => mockExposureResponse,
    })) as unknown as typeof fetch;

    const result = await fetchFactorExposure(1);

    assert.equal(result.driftHistory.length, 2);
    const delta = result.driftHistory[1].deltaFromPrevious;
    assert.ok(delta);
    assert.equal(delta.isComparable, false);
    assert.ok(delta.comparabilityNote.includes("coverage"));
  });

  it("preserves zero-fabrication contract: reports METHODOLOGY_PREREQUISITES_MISSING for return attribution", async () => {
    globalThis.fetch = (async () => ({
      ok: true,
      status: 200,
      json: async () => mockExposureResponse,
    })) as unknown as typeof fetch;

    const result = await fetchFactorExposure(1);

    assert.equal(result.returnAttribution.modelStatus, "METHODOLOGY_PREREQUISITES_MISSING");
    assert.ok(result.returnAttribution.missingPrerequisites.length > 0);
    assert.ok(result.returnAttribution.modelSpecification.includes("\\beta_{SMB}"));
  });

  it("correctly parses verified Point-in-Time market-cap classification metadata and source provenance", async () => {
    globalThis.fetch = (async () => ({
      ok: true,
      status: 200,
      json: async () => mockExposureResponse,
    })) as unknown as typeof fetch;

    const result = await fetchFactorExposure(1);

    assert.ok(result.currentExposure);
    const mcap = result.currentExposure.marketCap;
    assert.equal(mcap.classificationStatus, "VERIFIED_HISTORICAL_PIT");
    assert.equal(mcap.classificationMethodology, "AMFI_SEBI_CIRCULAR_OCT_2017_PIT");
    assert.equal(mcap.sourceRankingPeriod, "Jan-Jun 2026");
    assert.equal(mcap.sourcePublicationDate, "2026-07-06");
    assert.equal(mcap.effectiveFrom, "2026-08-01");
    assert.equal(mcap.effectiveTo, "2027-01-31");
    assert.equal(mcap.sourceArtifactSha256, "8e1256541331be9929abfeb1cd8531d72bd5e610559ef3b4ff77916c790a97ed");
    assert.equal(mcap.totalClassifiedHoldingsCount, 82);
    assert.equal(mcap.totalUnclassifiedHoldingsCount, 3);
  });

  it("decomposes style drift into active rebalancing vs AMFI reclassification and identifies reclassified holdings", async () => {
    globalThis.fetch = (async () => ({
      ok: true,
      status: 200,
      json: async () => mockExposureResponse,
    })) as unknown as typeof fetch;

    const result = await fetchFactorExposure(1);

    assert.equal(result.driftHistory.length, 2);
    const driftDelta = result.driftHistory[1].deltaFromPrevious;
    assert.ok(driftDelta);
    assert.ok(driftDelta.styleDriftAttribution);

    const attr = driftDelta.styleDriftAttribution;
    assert.equal(attr.largeCapRebalanceDeltaPct, 11.02);
    assert.equal(attr.largeCapReclassifyDeltaPct, -1.34);
    assert.equal(attr.largeCapNetDeltaPct, 9.68);
    assert.equal(attr.midCapRebalanceDeltaPct, 25.66);
    assert.equal(attr.midCapReclassifyDeltaPct, 1.34);
    assert.equal(attr.midCapNetDeltaPct, 27.00);

    // Reclassified holdings
    assert.equal(attr.reclassifiedHoldings.length, 1);
    const reclass = attr.reclassifiedHoldings[0];
    assert.equal(reclass.companyName, "Bosch Ltd");
    assert.equal(reclass.isin, "INE323A01026");
    assert.equal(reclass.previousCategory, "LARGE_CAP");
    assert.equal(reclass.previousRank, 87);
    assert.equal(reclass.currentCategory, "MID_CAP");
    assert.equal(reclass.currentRank, 101);
    assert.equal(reclass.portfolioWeightPct, 1.34);
    assert.ok(reclass.shiftDescription.includes("Reclassified by AMFI"));
  });
});
