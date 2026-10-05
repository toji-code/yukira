'use client';

import { useState } from 'react';
import Link from 'next/link';
import { PageContainer } from '@/components/layout/PageContainer';
import { StateView } from '@/components/epistemic/StateView';
import {
  evaluateGoalDiscovery,
  GoalDiscoveryRequest,
  GoalDiscoveryResponse,
  GoalDiscoveryResult,
} from '@/lib/api/discovery';

const GOAL_OPTIONS = [
  { id: 'WEALTH_CREATION', label: 'Wealth Creation', description: 'Long-term capital growth over multi-year horizons' },
  { id: 'RETIREMENT', label: 'Retirement', description: 'Long-term accumulation for post-career financial independence' },
  { id: 'CHILD_EDUCATION', label: 'Child Education', description: 'Targeted capital building for future higher education costs' },
  { id: 'HOUSE_PURCHASE', label: 'House / Major Purchase', description: 'Accumulating capital for a major planned expenditure' },
  { id: 'SHORT_MEDIUM_TERM', label: 'Short/Medium-Term Financial Goal', description: 'Capital preservation or growth over a 1–3 year horizon' },
  { id: 'CUSTOM', label: 'Custom Goal', description: 'Tailored financial objectives' },
];

const HORIZON_OPTIONS = [
  { years: 2, label: '< 3 years', note: 'Approximately 1–2 year investment horizon' },
  { years: 5, label: '3–5 years', note: 'Approximately 3–5 year investment horizon' },
  { years: 7, label: '5–10 years', note: 'Approximately 5–10 year investment horizon' },
  { years: 15, label: '10+ years', note: 'Approximately 10 or more year investment horizon' },
];

const RISK_OPTIONS = [
  { id: 'CONSERVATIVE', label: 'Conservative', description: 'Prioritizes downside protection and lower volatility' },
  { id: 'MODERATE', label: 'Moderate', description: 'Seeks balanced growth with managed market fluctuation' },
  { id: 'AGGRESSIVE', label: 'Aggressive', description: 'Accepts higher market volatility for long-term equity returns' },
];

const MODE_OPTIONS = [
  { id: 'SIP', label: 'SIP (Systematic Investment Plan)', description: 'Recurring periodic contributions' },
  { id: 'LUMPSUM', label: 'Lumpsum', description: 'One-time initial capital allocation' },
  { id: 'EITHER', label: 'Either / Flexible', description: 'No strict investment mode requirement' },
];

const CATEGORY_OPTIONS = [
  { id: 'ANY', label: 'Any Category' },
  { id: 'Equity', label: 'Equity Schemes' },
  { id: 'Hybrid', label: 'Hybrid Schemes' },
  { id: 'Debt', label: 'Debt Schemes' },
];

export default function GoalDiscoveryPage() {
  // Input state
  const [goalCategory, setGoalCategory] = useState<string>('WEALTH_CREATION');
  const [horizonYears, setHorizonYears] = useState<number>(5);
  const [riskTolerance, setRiskTolerance] = useState<string>('MODERATE');
  const [investmentMode, setInvestmentMode] = useState<string>('SIP');
  const [fundCategory, setFundCategory] = useState<string>('ANY');

  // Async execution state
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [response, setResponse] = useState<GoalDiscoveryResponse | null>(null);

  const handleEvaluate = async () => {
    setLoading(true);
    setError(null);

    const req: GoalDiscoveryRequest = {
      goalCategory,
      horizonYears,
      riskTolerance,
      investmentMode,
      fundCategory,
    };

    try {
      const data = await evaluateGoalDiscovery(req);
      setResponse(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to evaluate goal discovery criteria.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <PageContainer
      title="Goal-Based Fund Discovery"
      subtitle="Translate explicit financial requirements into a transparent, auditable analytical discovery set."
      breadcrumbs={[
        { label: 'Home', href: '/' },
        { label: 'Funds', href: '/funds' },
        { label: 'Goal Discovery', href: '/discover' },
      ]}
    >
      {/* Product Philosophy & Non-Advisory Mandate Banner */}
      <div className="panel mb-6 border-accent/20 bg-surface-inset">
        <div className="panel-header">
          <span className="eyebrow text-accent font-mono uppercase">Transparent Analytical Discovery Mandate</span>
          <span className="mono-meta">NO BUY/SELL ADVICE · NO RETURN FORECASTS</span>
        </div>
        <p className="px-4 py-3 text-[13px] leading-relaxed text-text-secondary">
          YUKIRA Goal Discovery evaluates mutual fund characteristics against explicit investor criteria.
          This tool is strictly non-advisory and does NOT generate investment recommendations, star ratings, or projected market returns.
        </p>
      </div>

      {/* Input Form Panel */}
      <div className="panel mb-8">
        <div className="panel-header">
          <h2 className="text-base font-semibold text-text-primary">1. Explicit Investor Requirements</h2>
          <span className="mono-meta">Step 1–5 Configuration</span>
        </div>

        <div className="p-4 space-y-6">
          {/* Step 1: Goal Category */}
          <div>
            <label className="field-label mb-2 block font-semibold text-text-primary">
              Step 1: Goal Category
            </label>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {GOAL_OPTIONS.map((g) => (
                <button
                  key={g.id}
                  type="button"
                  onClick={() => setGoalCategory(g.id)}
                  className={`flex flex-col text-left p-3 rounded border text-xs transition-all ${
                    goalCategory === g.id
                      ? 'border-accent bg-accent/10 text-text-primary shadow-xs'
                      : 'border-border bg-surface hover:border-border-strong text-text-secondary'
                  }`}
                >
                  <span className="font-semibold text-sm mb-1 text-text-primary">{g.label}</span>
                  <span className="text-[11.5px] leading-normal text-text-tertiary">{g.description}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Step 2: Investment Horizon */}
          <div>
            <label className="field-label mb-2 block font-semibold text-text-primary">
              Step 2: Investment Horizon
            </label>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {HORIZON_OPTIONS.map((h) => (
                <button
                  key={h.years}
                  type="button"
                  onClick={() => setHorizonYears(h.years)}
                  className={`flex flex-col text-left p-3 rounded border text-xs transition-all ${
                    horizonYears === h.years
                      ? 'border-accent bg-accent/10 text-text-primary shadow-xs'
                      : 'border-border bg-surface hover:border-border-strong text-text-secondary'
                  }`}
                >
                  <span className="font-semibold text-sm mb-1 text-text-primary">{h.label}</span>
                  <span className="text-[11px] leading-tight text-text-tertiary">{h.note}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Step 3: Risk Tolerance */}
          <div>
            <label className="field-label mb-2 block font-semibold text-text-primary">
              Step 3: Risk Tolerance
            </label>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              {RISK_OPTIONS.map((r) => (
                <button
                  key={r.id}
                  type="button"
                  onClick={() => setRiskTolerance(r.id)}
                  className={`flex flex-col text-left p-3 rounded border text-xs transition-all ${
                    riskTolerance === r.id
                      ? 'border-accent bg-accent/10 text-text-primary shadow-xs'
                      : 'border-border bg-surface hover:border-border-strong text-text-secondary'
                  }`}
                >
                  <span className="font-semibold text-sm mb-1 text-text-primary">{r.label}</span>
                  <span className="text-[11.5px] leading-normal text-text-tertiary">{r.description}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Step 4 & 5: Investment Mode & Fund Category Preference */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
            <div>
              <label className="field-label mb-2 block font-semibold text-text-primary">
                Step 4: Investment Mode
              </label>
              <div className="space-y-2">
                {MODE_OPTIONS.map((m) => (
                  <label
                    key={m.id}
                    className={`flex items-start gap-2.5 p-2.5 rounded border text-xs cursor-pointer transition-all ${
                      investmentMode === m.id
                        ? 'border-accent bg-accent/10 text-text-primary'
                        : 'border-border bg-surface text-text-secondary hover:border-border-strong'
                    }`}
                  >
                    <input
                      type="radio"
                      name="investmentMode"
                      checked={investmentMode === m.id}
                      onChange={() => setInvestmentMode(m.id)}
                      className="mt-0.5"
                    />
                    <div>
                      <span className="font-medium block text-text-primary">{m.label}</span>
                      <span className="text-[11px] text-text-tertiary">{m.description}</span>
                    </div>
                  </label>
                ))}
              </div>
            </div>

            <div>
              <label className="field-label mb-2 block font-semibold text-text-primary">
                Step 5: Fund Category Preference (Optional)
              </label>
              <div className="space-y-2">
                {CATEGORY_OPTIONS.map((c) => (
                  <label
                    key={c.id}
                    className={`flex items-center gap-2.5 p-2.5 rounded border text-xs cursor-pointer transition-all ${
                      fundCategory === c.id
                        ? 'border-accent bg-accent/10 text-text-primary'
                        : 'border-border bg-surface text-text-secondary hover:border-border-strong'
                    }`}
                  >
                    <input
                      type="radio"
                      name="fundCategory"
                      checked={fundCategory === c.id}
                      onChange={() => setFundCategory(c.id)}
                    />
                    <span className="font-medium text-text-primary">{c.label}</span>
                  </label>
                ))}
              </div>
            </div>
          </div>

          {/* Submit Action */}
          <div className="pt-4 border-t border-border flex items-center justify-between">
            <div className="text-xs text-text-tertiary">
              Deterministic evaluation performed on backend · Zero AI return estimation
            </div>
            <button
              type="button"
              onClick={handleEvaluate}
              disabled={loading}
              className="btn btn-primary px-6 py-2.5 font-medium flex items-center gap-2"
            >
              {loading ? (
                <>
                  <svg className="animate-spin h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                  </svg>
                  <span>Evaluating Master Records...</span>
                </>
              ) : (
                <span>Evaluate Goal Discovery</span>
              )}
            </button>
          </div>
        </div>
      </div>

      {/* Error View */}
      {error && (
        <StateView
          kind="unavailable"
          title="Goal Discovery Evaluation Failed"
          message={error}
        />
      )}

      {/* Results View */}
      {response && (
        <div className="space-y-6">
          <div className="flex items-center justify-between border-b border-border pb-3">
            <div>
              <h2 className="text-lg font-semibold text-text-primary">Analytical Discovery Results</h2>
              <p className="text-xs text-text-tertiary mt-0.5">
                Evaluated {response.totalEvaluated} registered mutual fund scheme option(s). Sorted by eligibility state and data completeness.
              </p>
            </div>
            <span className="mono-meta bg-surface-raised px-3 py-1 rounded border border-border">
              {response.results.length} result(s) returned
            </span>
          </div>

          {response.results.length === 0 ? (
            <StateView
              kind="empty"
              title="No Funds Found"
              message="No mutual fund scheme options in the registry matched the specified criteria."
            />
          ) : (
            <div className="space-y-5">
              {response.results.map((result) => (
                <ResultCard key={result.schemeOptionId} result={result} />
              ))}
            </div>
          )}
        </div>
      )}
    </PageContainer>
  );
}

function ResultCard({ result }: { result: GoalDiscoveryResult }) {
  const getBadgeStyle = (state: string) => {
    switch (state) {
      case 'ELIGIBLE':
        return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30';
      case 'PARTIALLY_EVALUATED':
        return 'bg-amber-500/10 text-amber-400 border-amber-500/30';
      case 'INSUFFICIENT_DATA':
        return 'status-badge state-unavailable';
      case 'NOT_ELIGIBLE':
        return 'bg-rose-500/10 text-rose-400 border-rose-500/30';
      default:
        return 'bg-surface-raised text-text-tertiary border-border';
    }
  };

  const formatStateLabel = (state: string) => {
    switch (state) {
      case 'ELIGIBLE': return 'ELIGIBLE';
      case 'PARTIALLY_EVALUATED': return 'PARTIALLY EVALUATED';
      case 'INSUFFICIENT_DATA': return 'INSUFFICIENT DATA';
      case 'NOT_ELIGIBLE': return 'NOT ELIGIBLE';
      default: return state;
    }
  };

  return (
    <div className="panel border-border hover:border-border-strong transition-colors">
      {/* Card Header */}
      <div className="panel-header flex-wrap gap-2 py-3 px-4 bg-surface-inset border-b border-border">
        <div className="flex-1 min-w-[240px]">
          <div className="flex items-center gap-2">
            <h3 className="font-semibold text-base text-text-primary">{result.fundName}</h3>
            <span className={`text-[10px] font-mono px-2 py-0.5 rounded border ${getBadgeStyle(result.resultState)}`}>
              {formatStateLabel(result.resultState)}
            </span>
          </div>
          <p className="text-xs text-text-tertiary mt-1">
            {result.amcName} · {result.category} ({result.subcategory}) · Plan: {result.planType} · Option: {result.optionType}
          </p>
        </div>

        <div className="flex items-center gap-3 text-right">
          <div>
            <span className="mono-meta block">AMFI Code: {result.amfiCode}</span>
            <span className="mono-meta block">ISIN: {result.isin}</span>
          </div>
          <Link
            href={`/analysis/${result.schemeOptionId}`}
            className="btn btn-secondary text-xs px-3 py-1.5"
          >
            Fund Profile →
          </Link>
        </div>
      </div>

      <div className="p-4 space-y-4">
        {/* YUKIRA Analytical Quality Score Panel (Separated from Eligibility) */}
        <div className="p-3 rounded border border-border bg-surface text-xs flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <span className="eyebrow font-mono uppercase text-text-tertiary">YUKIRA Analytical Quality Score</span>
            {result.analyticalScore.available ? (
              <div className="flex items-center gap-2">
                <span className="font-mono text-base font-bold text-accent">
                  {result.analyticalScore.scoreValue?.toFixed(1)} / 100
                </span>
                <span className="mono-meta px-1.5 py-0.5 rounded border border-border bg-surface-raised">
                  Confidence: {result.analyticalScore.confidence}
                </span>
                {result.analyticalScore.status && result.analyticalScore.status !== 'NO_SCORE' && (
                  <span className="mono-meta px-1.5 py-0.5 rounded border border-amber-500/30 bg-amber-500/5 text-amber-400">
                    {result.analyticalScore.status}
                  </span>
                )}
              </div>
            ) : (
              <span className="mono-meta text-text-tertiary">
                Score Unavailable
              </span>
            )}
          </div>
          <div className="text-[11px] text-text-tertiary">
            Version: {result.analyticalScore.scoreVersion} · Status: {result.analyticalScore.status}
          </div>
        </div>

        {/* Criterion Breakdown Grid */}
        <div>
          <h4 className="text-xs font-mono uppercase tracking-wider text-text-tertiary mb-2">
            Why It Appears — Criteria Evaluation
          </h4>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2.5">
            {Object.entries(result.criteria).map(([key, evalItem]) => (
              <div key={key} className="p-2.5 rounded border border-border bg-surface text-xs space-y-1">
                <div className="flex items-center justify-between">
                  <span className="font-medium text-text-primary capitalize">{key}</span>
                  <span
                    className={`text-[10px] font-mono px-1.5 py-0.2 rounded border ${
                      evalItem.state === 'MATCH' || evalItem.state === 'SUPPORTED' || evalItem.state === 'AVAILABLE'
                        ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                        : evalItem.state === 'UNKNOWN' || evalItem.state === 'NOT_APPLICABLE'
                        ? 'bg-amber-500/10 text-amber-400 border-amber-500/20'
                        : 'bg-rose-500/10 text-rose-400 border-rose-500/20'
                    }`}
                  >
                    {evalItem.state}
                  </span>
                </div>
                <p className="text-[11px] leading-snug text-text-tertiary">{evalItem.explanation}</p>
              </div>
            ))}
          </div>
        </div>

        {/* Evidence State & Recency */}
        <div className="p-3 rounded border border-border bg-surface-inset text-xs space-y-1">
          <div className="flex items-center justify-between">
            <span className="font-mono text-[11px] uppercase text-text-tertiary">Evidence & As-Of Verification</span>
            <span className="mono-meta">{result.evidenceState.dataQualitySummary}</span>
          </div>
          <div className="flex flex-wrap gap-2 pt-1">
            <span className="mono-meta">NAV As-Of: {result.evidenceState.navAsOfDate}</span>
            <span className="mono-meta">Enrichment As-Of: {result.evidenceState.enrichmentAsOfDate}</span>
            {result.evidenceState.qualityFlags.map((flag) => (
              <span key={flag} className="mono-meta px-1.5 py-0.2 rounded bg-surface border border-border">
                {flag}
              </span>
            ))}
          </div>
        </div>

        {/* Evidence-Based Investigation Questions */}
        {result.investigationQuestions && result.investigationQuestions.length > 0 && (
          <div className="p-3 rounded border border-amber-500/20 bg-amber-500/5 text-xs space-y-1.5">
            <span className="font-mono text-[11px] font-semibold text-amber-400 uppercase block">
              Investigation Questions for Allocator Verification
            </span>
            <ul className="list-disc list-inside space-y-1 text-text-secondary text-[11.5px]">
              {result.investigationQuestions.map((q, idx) => (
                <li key={idx}>{q}</li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}
