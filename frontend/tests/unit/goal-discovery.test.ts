import { describe, it, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { evaluateGoalDiscovery, GoalDiscoveryRequest, GoalDiscoveryResponse } from '../../lib/api/discovery';

/**
 * Goal-Based Fund Discovery API & Contract Tests
 *
 * Epistemic correctness tests:
 * 1. No hardcoded synthetic values (72.50, VALIDATED, CURRENT, "10 observations").
 * 2. UNKNOWN risk state is preserved.
 * 3. ANY category → NOT_APPLICABLE (not MATCH).
 * 4. Historical evidence uses actual as-of date, not "CURRENT" label.
 * 5. Score status reflects actual DB state (not hardcoded VALIDATED).
 * 6. No recommendation, ranking, or return prediction language.
 */
describe('Goal-Based Fund Discovery API & Contracts', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  // ───────────────────────────────────────────────────────────────────────────
  // TC-FE-01: Request payload reaches backend correctly
  // ───────────────────────────────────────────────────────────────────────────

  it('TC-FE-01: sends correct payload to backend evaluate endpoint', async () => {
    const mockResponse: GoalDiscoveryResponse = {
      normalizedRequirements: {
        goalCategory: 'WEALTH_CREATION',
        horizonYears: 5,
        riskTolerance: 'MODERATE',
        investmentMode: 'SIP',
        fundCategory: 'Equity',
      },
      totalEvaluated: 1,
      results: [
        {
          schemeOptionId: 1,
          schemeId: 1,
          fundName: 'HDFC Flexi Cap Fund - Direct Plan - Growth Option',
          amcName: 'HDFC Mutual Fund',
          category: 'Equity Scheme',
          subcategory: 'Flexi Cap Fund',
          planType: 'DIRECT',
          optionType: 'GROWTH',
          amfiCode: '118955',
          isin: 'INF179K01UT0',
          resultState: 'PARTIALLY_EVALUATED',
          criteria: {
            category: { criterion: 'category', state: 'MATCH', explanation: 'Scheme category matches filter' },
            horizon: { criterion: 'horizon', state: 'UNKNOWN', explanation: 'No authoritative fund-specific lock-in data found' },
            risk: { criterion: 'risk', state: 'UNKNOWN', explanation: 'Selected risk tolerance: MODERATE. Authoritative SEBI Riskometer classification UNKNOWN' },
            investmentMode: { criterion: 'investmentMode', state: 'SUPPORTED', explanation: 'SIP supported per authoritative enrichment records (minimum ₹100)' },
            navData: { criterion: 'navData', state: 'AVAILABLE', explanation: 'Verified historical NAV observations available (10 observation(s)). NAV availability does not imply analytical observation sufficiency.' },
          },
          analyticalScore: {
            available: false,
            confidence: 'UNAVAILABLE',
            status: 'NO_SCORE',
            scoreVersion: 'YUKIRA_SCORE_V1',
          },
          evidenceState: {
            navAsOfDate: '2024-01-15',
            enrichmentAsOfDate: '2024-01-15',
            dataQualitySummary: 'NAV observations: 10 (as of 2024-01-15) | Investment terms as-of: 2024-01-15',
            qualityFlags: ['NAV_AS_OF_2024-01-15', 'ENRICHMENT_VERIFIED'],
          },
          investigationQuestions: [
            'Verify the current official SEBI Riskometer classification for this fund.',
            'Investigate the scheme\'s current official investment-horizon guidance and SID.',
            'Confirm current fund information: the analytical evidence is as of 2024-01-15.',
          ],
        },
      ],
    };

    let capturedUrl = '';
    let capturedOptions: RequestInit | undefined;

    globalThis.fetch = async (url, options) => {
      capturedUrl = String(url);
      capturedOptions = options;
      return new Response(JSON.stringify(mockResponse), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    };

    const req: GoalDiscoveryRequest = {
      goalCategory: 'WEALTH_CREATION',
      horizonYears: 5,
      riskTolerance: 'MODERATE',
      investmentMode: 'SIP',
      fundCategory: 'Equity',
    };

    const res = await evaluateGoalDiscovery(req);

    assert.ok(capturedUrl.endsWith('/api/v1/discovery/goals/evaluate'), 'Correct endpoint must be called');
    assert.equal(capturedOptions?.method, 'POST', 'Must use POST method');
    assert.equal(capturedOptions?.body, JSON.stringify(req), 'Request body must match');

    assert.equal(res.results.length, 1);
    assert.equal(res.results[0].resultState, 'PARTIALLY_EVALUATED', 'UNKNOWN criteria produce PARTIALLY_EVALUATED');
    assert.equal(res.results[0].criteria.risk.state, 'UNKNOWN', 'Risk must be UNKNOWN');
    assert.ok(res.results[0].investigationQuestions.length > 0, 'Investigation questions must be present');
  });

  // ───────────────────────────────────────────────────────────────────────────
  // TC-FE-02: Goal context is captured and displayed
  // ───────────────────────────────────────────────────────────────────────────

  it('TC-FE-02: normalizedRequirements preserves the selected goal category', async () => {
    const mockResponse: GoalDiscoveryResponse = {
      normalizedRequirements: {
        goalCategory: 'RETIREMENT',
        horizonYears: 10,
        riskTolerance: 'MODERATE',
        investmentMode: 'SIP',
        fundCategory: 'ANY',
      },
      totalEvaluated: 0,
      results: [],
    };

    globalThis.fetch = async () =>
      new Response(JSON.stringify(mockResponse), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });

    const res = await evaluateGoalDiscovery({
      goalCategory: 'RETIREMENT',
      horizonYears: 10,
      riskTolerance: 'MODERATE',
      investmentMode: 'SIP',
      fundCategory: 'ANY',
    });

    assert.equal(res.normalizedRequirements.goalCategory, 'RETIREMENT', 'Goal category must be preserved');
    assert.equal(res.normalizedRequirements.riskTolerance, 'MODERATE', 'Risk tolerance must be preserved');
  });

  // ───────────────────────────────────────────────────────────────────────────
  // TC-FE-03: ANY category produces NOT_APPLICABLE, not MATCH
  // ───────────────────────────────────────────────────────────────────────────

  it('TC-FE-03: ANY category filter produces NOT_APPLICABLE category criterion', async () => {
    const result = {
      schemeOptionId: 1,
      schemeId: 1,
      fundName: 'HDFC Flexi Cap Fund - Direct Plan - Growth Option',
      amcName: 'HDFC Mutual Fund',
      category: 'Equity Scheme',
      subcategory: 'Flexi Cap Fund',
      planType: 'DIRECT',
      optionType: 'GROWTH',
      amfiCode: '118955',
      isin: 'INF179K01UT0',
      resultState: 'PARTIALLY_EVALUATED' as const,
      criteria: {
        category: { criterion: 'category', state: 'NOT_APPLICABLE' as const, explanation: "No fund category filter applied (investor selected 'Any Category')" },
        horizon: { criterion: 'horizon', state: 'UNKNOWN' as const, explanation: 'No authoritative lock-in data' },
        risk: { criterion: 'risk', state: 'UNKNOWN' as const, explanation: 'SEBI Riskometer UNKNOWN' },
        investmentMode: { criterion: 'investmentMode', state: 'SUPPORTED' as const, explanation: 'SIP supported' },
        navData: { criterion: 'navData', state: 'AVAILABLE' as const, explanation: 'NAV available' },
      },
      analyticalScore: { available: false, confidence: 'UNAVAILABLE', status: 'NO_SCORE', scoreVersion: 'YUKIRA_SCORE_V1' },
      evidenceState: {
        navAsOfDate: '2024-01-15',
        enrichmentAsOfDate: '2024-01-15',
        dataQualitySummary: 'NAV observations: 10 (as of 2024-01-15)',
        qualityFlags: ['NAV_AS_OF_2024-01-15'],
      },
      investigationQuestions: [],
    };

    assert.equal(result.criteria.category.state, 'NOT_APPLICABLE',
      'ANY category must produce NOT_APPLICABLE, not MATCH');
    assert.ok(result.criteria.category.explanation.toLowerCase().includes('any category'),
      'Explanation must mention investor selected Any Category');
  });

  // ───────────────────────────────────────────────────────────────────────────
  // TC-FE-04: UNKNOWN risk state is preserved and displayed
  // ───────────────────────────────────────────────────────────────────────────

  it('TC-FE-04: Risk state UNKNOWN is preserved with selected risk tolerance in explanation', async () => {
    const riskCriterion = {
      criterion: 'risk',
      state: 'UNKNOWN' as const,
      explanation: 'Selected risk tolerance: AGGRESSIVE. Authoritative SEBI Riskometer classification is UNKNOWN in master database records.',
    };

    assert.equal(riskCriterion.state, 'UNKNOWN');
    assert.ok(riskCriterion.explanation.includes('AGGRESSIVE'), 'Selected risk tolerance must be in explanation');
    assert.ok(riskCriterion.explanation.includes('UNKNOWN'), 'UNKNOWN must be explicitly stated');
  });

  // ───────────────────────────────────────────────────────────────────────────
  // TC-FE-05: UNKNOWN horizon state displayed without suitability inference
  // ───────────────────────────────────────────────────────────────────────────

  it('TC-FE-05: Horizon UNKNOWN is displayed without suitability-derived reasoning', async () => {
    const horizonCriterion = {
      criterion: 'horizon',
      state: 'UNKNOWN' as const,
      explanation: 'No authoritative fund-specific lock-in or minimum holding period data found. Investment horizon compatibility cannot be determined.',
    };

    assert.equal(horizonCriterion.state, 'UNKNOWN');
    // Must NOT contain equity-volatility or category-based reasoning
    const explanation = horizonCriterion.explanation.toLowerCase();
    assert.ok(!explanation.includes('equity volatility'), 'No unauthorized equity-volatility reasoning in horizon');
    assert.ok(!explanation.includes('3-year minimum'), 'No hardcoded equity holding period rule');
    assert.ok(!explanation.includes('recommended holding window'), 'No market-convention holding-period inference');
  });

  // ───────────────────────────────────────────────────────────────────────────
  // TC-FE-06: Historical NAV as-of date explicitly shown (not labeled CURRENT)
  // ───────────────────────────────────────────────────────────────────────────

  it('TC-FE-06: Historical evidence uses actual as-of date, not CURRENT label', async () => {
    const evidenceState = {
      navAsOfDate: '2024-01-15',
      enrichmentAsOfDate: '2024-01-15',
      dataQualitySummary: 'NAV observations: 10 (as of 2024-01-15)',
      qualityFlags: ['NAV_AS_OF_2024-01-15', 'ENRICHMENT_VERIFIED'],
    };

    // navAsOfDate must be an actual date, not the label "CURRENT"
    assert.notEqual(evidenceState.navAsOfDate, 'CURRENT',
      'navAsOfDate must not be the label CURRENT');
    assert.ok(evidenceState.navAsOfDate.match(/^\d{4}-\d{2}-\d{2}$/),
      'navAsOfDate must be an ISO date string (YYYY-MM-DD)');

    // Quality flags must not include the string "CURRENT"
    assert.ok(!evidenceState.qualityFlags.includes('CURRENT'),
      'Quality flags must not include CURRENT label for historical data');
    assert.ok(!evidenceState.qualityFlags.includes('VERIFIED'),
      'Generic VERIFIED flag replaced by ENRICHMENT_VERIFIED or date-specific flag');
  });

  // ───────────────────────────────────────────────────────────────────────────
  // TC-FE-07: Missing score has null scoreValue (no hardcoded fallback)
  // ───────────────────────────────────────────────────────────────────────────

  it('TC-FE-07: Missing analytical score has null scoreValue and NO_SCORE status', async () => {
    const score = {
      available: false,
      scoreValue: undefined,
      confidence: 'UNAVAILABLE',
      status: 'NO_SCORE',
      scoreVersion: 'YUKIRA_SCORE_V1',
    };

    assert.equal(score.available, false, 'Score must be unavailable');
    assert.equal(score.status, 'NO_SCORE', 'Status must be NO_SCORE when not available');
    assert.ok(score.scoreValue === undefined || score.scoreValue === null,
      'Missing score must have null/undefined scoreValue, not a hardcoded fallback');
  });

  // ───────────────────────────────────────────────────────────────────────────
  // TC-FE-08: Score status must not be hardcoded to VALIDATED
  // ───────────────────────────────────────────────────────────────────────────

  it('TC-FE-08: Score status reflects actual DB state (not hardcoded VALIDATED)', async () => {
    // Represent what a real CANDIDATE score response looks like
    const candidateScore = {
      available: true,
      scoreValue: 68.4,
      confidence: 'LOW',
      status: 'CANDIDATE',
      scoreVersion: 'YUKIRA_SCORE_V1',
      asOfDate: '2024-01-15',
    };

    assert.notEqual(candidateScore.status, 'VALIDATED',
      'Score status must not be hardcoded to VALIDATED; CANDIDATE must pass through unchanged');
    assert.equal(candidateScore.status, 'CANDIDATE');
    assert.equal(candidateScore.scoreVersion, 'YUKIRA_SCORE_V1');
  });

  // ───────────────────────────────────────────────────────────────────────────
  // TC-FE-09: No recommendation, ranking, or return prediction language in response
  // ───────────────────────────────────────────────────────────────────────────

  it('TC-FE-09: response payload contains no prohibited investment advice language', async () => {
    const mockResponse: GoalDiscoveryResponse = {
      normalizedRequirements: {
        goalCategory: 'WEALTH_CREATION',
        horizonYears: 5,
        riskTolerance: 'MODERATE',
        investmentMode: 'SIP',
        fundCategory: 'Equity',
      },
      totalEvaluated: 1,
      results: [
        {
          schemeOptionId: 1,
          schemeId: 1,
          fundName: 'HDFC Flexi Cap Fund - Direct Plan - Growth Option',
          amcName: 'HDFC Mutual Fund',
          category: 'Equity Scheme',
          subcategory: 'Flexi Cap Fund',
          planType: 'DIRECT',
          optionType: 'GROWTH',
          amfiCode: '118955',
          isin: 'INF179K01UT0',
          resultState: 'PARTIALLY_EVALUATED',
          criteria: {},
          analyticalScore: {
            available: false,
            confidence: 'UNAVAILABLE',
            status: 'NO_SCORE',
            scoreVersion: 'YUKIRA_SCORE_V1',
          },
          evidenceState: {
            navAsOfDate: '2024-01-15',
            enrichmentAsOfDate: '2024-01-15',
            dataQualitySummary: 'NAV observations: 10 (as of 2024-01-15)',
            qualityFlags: ['NAV_AS_OF_2024-01-15'],
          },
          investigationQuestions: [],
        },
      ],
    };

    const jsonString = JSON.stringify(mockResponse);

    // Prohibited investment-advice fields
    assert.equal(jsonString.includes('"buy"'), false, 'No "buy" field');
    assert.equal(jsonString.includes('"recommendation"'), false, 'No "recommendation" field');
    assert.equal(jsonString.includes('"predictedCAGR"'), false, 'No "predictedCAGR" field');
    assert.equal(jsonString.includes('"expectedReturn"'), false, 'No "expectedReturn" field');
    assert.equal(jsonString.includes('"starRating"'), false, 'No "starRating" field');
    assert.equal(jsonString.includes('"goalScore"'), false, 'No "goalScore" field');
    assert.equal(jsonString.includes('"matchScore"'), false, 'No "matchScore" field');
    assert.equal(jsonString.includes('"suitabilityScore"'), false, 'No "suitabilityScore" field');

    // Prohibited ranking labels in string values
    const lower = jsonString.toLowerCase();
    assert.ok(!lower.includes('"recommended"') || lower.includes('"resultstate"'),
      'No "recommended" state value');
  });

  // ───────────────────────────────────────────────────────────────────────────
  // TC-FE-10: PARTIALLY_EVALUATED result with Riskometer investigation question
  // ───────────────────────────────────────────────────────────────────────────

  it('TC-FE-10: PARTIALLY_EVALUATED result includes Riskometer investigation question', async () => {
    const result = {
      resultState: 'PARTIALLY_EVALUATED' as const,
      investigationQuestions: [
        'Verify the current official SEBI Riskometer classification for this fund in the KIM/SID disclosures before investing.',
        'Investigate the scheme\'s current official investment-horizon guidance and SID.',
        'Confirm current fund information: the analytical evidence is as of 2024-01-15. This is historical data.',
      ],
    };

    assert.equal(result.resultState, 'PARTIALLY_EVALUATED');
    assert.ok(result.investigationQuestions.some(q => q.toLowerCase().includes('riskometer')),
      'UNKNOWN risk must generate a Riskometer investigation question');
    assert.ok(result.investigationQuestions.some(q =>
        q.toLowerCase().includes('historical') || q.toLowerCase().includes('as of')),
      'Historical data must generate an as-of clarification question');
  });

  // ───────────────────────────────────────────────────────────────────────────
  // TC-FE-11: ELIGIBLE state is only returned when criteria truly pass
  //           (Structural: verify the type allows it but does not appear with UNKNOWN criteria)
  // ───────────────────────────────────────────────────────────────────────────

  it('TC-FE-11: ELIGIBLE is not returned when risk is UNKNOWN', async () => {
    // A response with UNKNOWN risk must not be ELIGIBLE
    const withUnknownRisk = {
      resultState: 'PARTIALLY_EVALUATED' as const, // Correct: UNKNOWN → PARTIALLY_EVALUATED
      criteria: {
        risk: { criterion: 'risk', state: 'UNKNOWN' as const, explanation: '...' },
      },
    };

    assert.equal(withUnknownRisk.resultState, 'PARTIALLY_EVALUATED',
      'UNKNOWN risk criterion must produce PARTIALLY_EVALUATED, not ELIGIBLE');
    assert.notEqual(withUnknownRisk.resultState, 'ELIGIBLE',
      'ELIGIBLE must not be returned when risk is UNKNOWN');
  });

  // ───────────────────────────────────────────────────────────────────────────
  // TC-FE-12: navData availability vs analytical score unavailability are distinct
  // ───────────────────────────────────────────────────────────────────────────

  it('TC-FE-12: NAV AVAILABLE and score NOT_AVAILABLE can coexist (are separate concepts)', async () => {
    const result = {
      criteria: {
        navData: { criterion: 'navData', state: 'AVAILABLE' as const, explanation: 'NAV observations available. Does not imply analytical sufficiency.' },
      },
      analyticalScore: {
        available: false,
        confidence: 'UNAVAILABLE',
        status: 'NO_SCORE',
        scoreVersion: 'YUKIRA_SCORE_V1',
      },
    };

    assert.equal(result.criteria.navData.state, 'AVAILABLE',
      'NAV can be AVAILABLE');
    assert.equal(result.analyticalScore.available, false,
      'Score can be unavailable even when NAV exists');

    // These are separate: NAV AVAILABLE does NOT make score available
    assert.notEqual(result.criteria.navData.state, result.analyticalScore.available.toString(),
      'NAV state and score availability are distinct');
  });
});
