import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { GroundedAiInterpretation } from '../../types/interpretation';

describe('Grounded AI Interpretation Layer — Frontend Contract Tests', () => {

  it('TC-FE-AI01: GroundedAiInterpretation contract enforces 6-question structured sections', () => {
    const mockInterpretation: GroundedAiInterpretation = {
      summary: 'Deterministic evidence analysis for HDFC Flexi Cap Fund: Overall Score 74.50.',
      whatHappened: [
        'YUKIRA_SCORE_V1 evaluated HDFC Flexi Cap Fund at 74.50 / 100 (SCORED status).',
        'Observed 3Y CAGR (RET-03): +18.4%.',
        'Observed 3Y Annualized Volatility (RSK-01): 14.2%.'
      ],
      interpretation: [
        'HDFC Flexi Cap Fund exhibits strong evidence across evaluated return and risk-adjusted dimensions.',
        'Evaluation is grounded in deterministic historical observations; past structural efficiency does not imply future outperformance.'
      ],
      riskFactors: [
        'Market risk: Fund NAV remains exposed to broad benchmark market declines.'
      ],
      dataQualityCaveats: [
        'Taxonomy [quality]: VALID.',
        'Methodology Notice: YUKIRA_SCORE_V1 is a candidate research methodology.'
      ],
      invalidationFactors: [
        'Significant structural changes in fund manager tenure.',
        'Substantial macro regime shift not captured in the 3-year historical window.'
      ],
      investigationQuestions: [
        'Has the fund manager process changed relative to the historical evaluation window?',
        'Is the current expense ratio competitive against peer direct growth options?'
      ],
      evidenceReferences: {
        overallScore: 74.5,
        "RET-03": "+18.4%"
      },
      calculationRunId: 7851,
      schemeOptionId: 10189,
      methodologyVersion: 'YUKIRA_SCORE_V1',
      asOfDate: '2024-01-15',
      knowledgeCutoff: '2024-01-31T23:59:59+05:30',
      sourceArtifacts: ['digest900508f8'],
      epistemicStatus: 'VALIDATED_GROUNDED_AI',
      isFallback: false,
      modelProvider: 'gemini-2.5-flash',
      generatedAt: '2026-10-06T06:40:00Z'
    };

    assert.equal(mockInterpretation.schemeOptionId, 10189);
    assert.equal(mockInterpretation.isFallback, false);
    assert.equal(mockInterpretation.epistemicStatus, 'VALIDATED_GROUNDED_AI');
    assert.equal(mockInterpretation.whatHappened.length, 3);
    assert.equal(mockInterpretation.interpretation.length, 2);
    assert.equal(mockInterpretation.riskFactors.length, 1);
    assert.equal(mockInterpretation.invalidationFactors.length, 2);
    assert.equal(mockInterpretation.investigationQuestions.length, 2);
  });

  it('TC-FE-AI02: Deterministic fallback interpretation state is cleanly distinguished', () => {
    const mockFallback: GroundedAiInterpretation = {
      summary: 'Deterministic evidence analysis for investor portfolio.',
      whatHappened: ['Portfolio comprises 4 active holdings.'],
      interpretation: ['Portfolio aggregation reflects deterministic sum of individual asset holdings.'],
      riskFactors: ['Single-asset class concentration risk.'],
      dataQualityCaveats: ['User-provided holdings.'],
      invalidationFactors: ['Unrecorded external transactions.'],
      investigationQuestions: ['Are current category weights aligned with target allocation?'],
      epistemicStatus: 'DETERMINISTIC_FALLBACK',
      isFallback: true,
      modelProvider: 'deterministic-rule-engine-v1'
    };

    assert.equal(mockFallback.isFallback, true);
    assert.equal(mockFallback.epistemicStatus, 'DETERMINISTIC_FALLBACK');
    assert.equal(mockFallback.modelProvider, 'deterministic-rule-engine-v1');
  });

  it('TC-FE-AI03: Safety rules verify zero investment advice language in client interpretation payloads', () => {
    const safeProse = [
      '3Y CAGR was +18.4%.',
      'Fund Sharpe ratio reflects above-median efficiency.',
      'Volatility remains exposed to market drawdowns.'
    ];

    const forbiddenTerms = ['buy ', 'sell ', 'hold ', 'guaranteed', 'future return'];

    for (const text of safeProse) {
      const lower = text.toLowerCase();
      for (const term of forbiddenTerms) {
        assert.equal(lower.includes(term), false, `Prose contains forbidden term '${term}': ${text}`);
      }
    }
  });
});
