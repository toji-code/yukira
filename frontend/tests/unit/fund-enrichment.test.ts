import { describe, it } from 'node:test';
import assert from 'node:assert';
import { fetchOptionEnrichment, fetchOptionHoldings } from '../../lib/api/enrichment';
import { EnrichedFundProfileDto } from '../../types/enrichment';

describe('Fund Information Enrichment API Client & Domain Model Tests', () => {
  it('constructs correct enrichment URL and handles response', async () => {
    const mockData: EnrichedFundProfileDto = {
      schemeId: 10,
      schemeOptionId: 1,
      schemeName: 'HDFC Flexi Cap Fund',
      schemeCode: 'HDFC_FLEXI',
      planType: 'DIRECT',
      optionType: 'GROWTH',
      amfiCode: '118955',
      isin: 'INF179K01UT0',
      aum: {
        reportedTotalNetAssets: 47642.42,
        formattedAum: '₹47,642.42 Cr',
        currency: 'INR',
        unit: 'Crores',
        asOfDate: '2024-01-31',
        sourceArtifactId: 10,
        sourceDocumentTitle: 'Official AMC Monthly Portfolio Disclosure',
        qualityAssessment: 'VERIFIED',
        status: 'AVAILABLE',
      },
      expenseRatio: {
        expenseRatio: 0.0078,
        formattedExpenseRatio: '0.78%',
        planType: 'DIRECT',
        optionType: 'GROWTH',
        regularPlanRatio: 0.0144,
        formattedRegularPlanRatio: '1.44%',
        asOfDate: '2024-01-31',
        sourceArtifactId: 10,
        sourceDocumentTitle: 'AMC Statutory Total Expense Ratio (TER) Disclosure',
        qualityAssessment: 'VERIFIED',
        status: 'AVAILABLE',
      },
      fundManagers: [
        {
          managerName: 'Ms. Roshi Jain',
          role: 'Senior Fund Manager',
          startDate: '2022-07-29',
          endDate: null,
          asOfDate: '2024-01-31',
          sourceArtifactId: 10,
          sourceDocumentTitle: 'Official AMC Scheme Factsheet & SID',
          qualityAssessment: 'VERIFIED',
          status: 'AVAILABLE',
        },
      ],
      investmentTerms: {
        minSipAmount: 100,
        formattedMinSipAmount: '₹100',
        sipFrequencies: ['Monthly', 'Weekly', 'Daily', 'Quarterly'],
        minLumpsumAmount: 100,
        formattedMinLumpsumAmount: '₹100',
        minAdditionalAmount: 100,
        formattedMinAdditionalAmount: '₹100',
        lockInPeriodDays: null,
        exitLoadDescription: '1.00% if redeemed within 1 year; Nil thereafter',
        asOfDate: '2024-01-01',
        sourceArtifactId: 11,
        sourceDocumentTitle: 'Official Key Information Memorandum (KIM)',
        qualityAssessment: 'VERIFIED',
        status: 'AVAILABLE',
      },
      holdingsSummary: {
        asOfDate: '2024-01-31',
        reportedHoldingsCount: 10,
        sumReportedWeights: 0.5082,
        formattedSumReportedWeights: '50.82%',
        sourceArtifactId: 10,
        sourceDocumentTitle: 'SEBI Monthly Portfolio Disclosure',
        qualityAssessment: 'VERIFIED',
        status: 'AVAILABLE',
      },
      epistemicStatus: 'VERIFIED_PRIMARY_SOURCE',
    };

    const originalFetch = globalThis.fetch;
    let requestedUrl = '';

    globalThis.fetch = async (input: RequestInfo | URL) => {
      requestedUrl = input.toString();
      return {
        ok: true,
        json: async () => mockData,
      } as Response;
    };

    try {
      const result = await fetchOptionEnrichment(1, '2024-01-31T23:59:59Z');
      assert.strictEqual(
        requestedUrl,
        'http://localhost:8080/api/v1/schemes/options/1/enrichment?knowledgeCutoff=2024-01-31T23%3A59%3A59Z'
      );
      assert.strictEqual(result.schemeCode, 'HDFC_FLEXI');
      assert.strictEqual(result.planType, 'DIRECT');
      assert.strictEqual(result.aum.formattedAum, '₹47,642.42 Cr');
      assert.strictEqual(result.expenseRatio.formattedExpenseRatio, '0.78%');
      assert.strictEqual(result.fundManagers[0].managerName, 'Ms. Roshi Jain');
      assert.strictEqual(result.investmentTerms.formattedMinSipAmount, '₹100');
      assert.strictEqual(result.epistemicStatus, 'VERIFIED_PRIMARY_SOURCE');
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('preserves exact scheme-option isolation and direct vs regular separation', () => {
    const directTer: EnrichedFundProfileDto['expenseRatio'] = {
      expenseRatio: 0.0078,
      formattedExpenseRatio: '0.78%',
      planType: 'DIRECT',
      optionType: 'GROWTH',
      regularPlanRatio: 0.0144,
      formattedRegularPlanRatio: '1.44%',
      asOfDate: '2024-01-31',
      sourceArtifactId: 10,
      sourceDocumentTitle: 'AMC Statutory Total Expense Ratio (TER) Disclosure',
      qualityAssessment: 'VERIFIED',
      status: 'AVAILABLE',
    };

    assert.strictEqual(directTer.planType, 'DIRECT');
    assert.strictEqual(directTer.formattedExpenseRatio, '0.78%');
    assert.notStrictEqual(directTer.formattedExpenseRatio, directTer.formattedRegularPlanRatio);
  });

  it('handles missing enrichment fields with explicit MISSING state rather than zeros', () => {
    const missingAum: EnrichedFundProfileDto['aum'] = {
      reportedTotalNetAssets: null,
      formattedAum: null,
      currency: 'INR',
      unit: 'Crores',
      asOfDate: null,
      sourceArtifactId: null,
      sourceDocumentTitle: null,
      qualityAssessment: 'MISSING',
      status: 'MISSING',
    };

    assert.strictEqual(missingAum.status, 'MISSING');
    assert.strictEqual(missingAum.reportedTotalNetAssets, null);
    assert.strictEqual(missingAum.formattedAum, null);
    // Crucial: A missing value must never default to 0.00
    assert.notStrictEqual(missingAum.reportedTotalNetAssets, 0);
  });

  it('consumes pre-formatted holdings weights from backend without frontend math', async () => {
    const mockHoldings = [
      {
        securityName: 'ICICI Bank Ltd.',
        weight: 0.095,
        formattedWeight: '9.50%',
        category: 'Financial Services',
        assetClass: 'Equity',
        sector: 'Financial Services',
        isin: 'INE090A01021',
        asOfDate: '2024-01-31',
        source: 'Official Factsheet',
        dataQuality: 'VERIFIED',
      },
      {
        securityName: 'HDFC Bank Ltd.',
        weight: 0.0927,
        formattedWeight: '9.27%',
        category: 'Financial Services',
        assetClass: 'Equity',
        sector: 'Financial Services',
        isin: 'INE040A01034',
        asOfDate: '2024-01-31',
        source: 'Official Factsheet',
        dataQuality: 'VERIFIED',
      },
      {
        securityName: 'Cipla Ltd.',
        weight: 0.0539,
        formattedWeight: '5.39%',
        category: 'Healthcare',
        assetClass: 'Equity',
        sector: 'Healthcare',
        isin: 'INE059A01026',
        asOfDate: '2024-01-31',
        source: 'Official Factsheet',
        dataQuality: 'VERIFIED',
      },
    ];

    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () =>
      ({
        ok: true,
        json: async () => mockHoldings,
      } as Response);

    try {
      const holdings = await fetchOptionHoldings(1);
      assert.strictEqual(holdings.length, 3);
      assert.strictEqual(holdings[0].securityName, 'ICICI Bank Ltd.');
      assert.strictEqual(holdings[0].formattedWeight, '9.50%');
      assert.strictEqual(holdings[0].isin, 'INE090A01021');

      assert.strictEqual(holdings[1].securityName, 'HDFC Bank Ltd.');
      assert.strictEqual(holdings[1].formattedWeight, '9.27%');

      assert.strictEqual(holdings[2].securityName, 'Cipla Ltd.');
      assert.strictEqual(holdings[2].formattedWeight, '5.39%');
      assert.strictEqual(holdings[2].isin, 'INE059A01026');

      // Verify no frontend math was done: weights remain raw
      assert.strictEqual(holdings[0].weight, 0.095);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('ensures null lockInPeriodDays is handled as null and never converted to Nil, Open-ended, or 0 days', () => {
    const terms: EnrichedFundProfileDto['investmentTerms'] = {
      minSipAmount: 100,
      formattedMinSipAmount: '₹100',
      sipFrequencies: ['Monthly'],
      minLumpsumAmount: 100,
      formattedMinLumpsumAmount: '₹100',
      minAdditionalAmount: 100,
      formattedMinAdditionalAmount: '₹100',
      lockInPeriodDays: null,
      exitLoadDescription: '1.00% if redeemed within 1 year; Nil thereafter',
      asOfDate: '2024-01-01',
      sourceArtifactId: 11,
      sourceDocumentTitle: 'Official Key Information Memorandum (KIM)',
      qualityAssessment: 'VERIFIED',
      status: 'AVAILABLE',
    };

    assert.strictEqual(terms.lockInPeriodDays, null);
    assert.notStrictEqual(terms.lockInPeriodDays, 0);
  });
});
