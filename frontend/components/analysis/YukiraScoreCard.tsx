'use client';

import React, { useState, useEffect } from 'react';
import { fetchLatestScore, fetchScoreByRunId, fetchScoreHistory, fetchCurrentScore } from '@/lib/api/scores';
import { AnalyticalScore, MetricContribution } from '@/types/score';
import { formatDate } from '@/lib/utils/formatters';

interface YukiraScoreCardProps {
  schemeOptionId: number;
  calculationRunId?: number;
  initialScore?: AnalyticalScore | null;
  className?: string;
}

const CANONICAL_METRIC_LABELS: Record<string, string> = {
  'RET-03': '3Y CAGR',
  'RET-07': '3Y Annualized Active Return / Excess CAGR',
  'RSK-01': '3Y Annualized Volatility',
  'RSK-02': '3Y Downside Semideviation',
  'RSK-03': '3Y Maximum Drawdown',
  'REL-02': "Jensen's Alpha 3Y",
  'RAT-04': 'Information Ratio 3Y',
  'MKT-01': 'Beta 3Y',
  'MKT-02': 'Downside Beta 3Y',
  'MKT-05': 'Capture Spread 3Y',
};

const DIMENSION_EXPLANATIONS: Record<string, string> = {
  RETURN_QUALITY: 'Evaluates realized compound annual growth rate and benchmark excess return.',
  RISK_QUALITY: 'Evaluates total return volatility, downside semideviation, and peak-to-trough drawdowns.',
  BENCHMARK_RELATIVE_QUALITY: "Evaluates benchmark-adjusted alpha (Jensen's Alpha) and risk-adjusted efficiency (Information Ratio).",
  CONSISTENCY_DOWNSIDE_QUALITY: 'Evaluates downside market sensitivity (Beta & Downside Beta) and asymmetric capture behavior across market cycles.',
};

function getCanonicalMetricLabel(code?: string, defaultName?: string): string {
  if (code && CANONICAL_METRIC_LABELS[code]) {
    return CANONICAL_METRIC_LABELS[code];
  }
  return defaultName || code || 'Metric';
}

export function YukiraScoreCard({
  schemeOptionId,
  calculationRunId,
  initialScore,
  className = '',
}: YukiraScoreCardProps) {
  const [fetchedScore, setFetchedScore] = useState<AnalyticalScore | null>(null);
  const [scoreHistory, setScoreHistory] = useState<AnalyticalScore[]>([]);
  const [loading, setLoading] = useState<boolean>(initialScore === undefined);
  const [error, setError] = useState<string | null>(null);

  const scoreData = initialScore !== undefined ? initialScore : fetchedScore;

  // Expanded states for progressive disclosure
  const [expandedDimensions, setExpandedDimensions] = useState<Record<string, boolean>>({});
  const [showMethodology, setShowMethodology] = useState<boolean>(false);
  const [showEvidenceAudit, setShowEvidenceAudit] = useState<boolean>(false);
  const [showHistory, setShowHistory] = useState<boolean>(false);
  const [showEpistemicStates, setShowEpistemicStates] = useState<boolean>(false);


  useEffect(() => {
    if (initialScore !== undefined) {
      return;
    }

    let active = true;

    const loadScore = async () => {
      try {
        let res: AnalyticalScore | null = null;
        if (calculationRunId) {
          res = await fetchScoreByRunId(calculationRunId);
        }
        if (!res && schemeOptionId) {
          try {
            const currentRes = await fetchCurrentScore(schemeOptionId);
            if (currentRes) {
              if ('score' in currentRes && typeof currentRes.score === 'object' && currentRes.score !== null) {
                res = currentRes.score as AnalyticalScore;
              } else if (currentRes.score !== undefined || currentRes.dimensions) {
                res = currentRes as unknown as AnalyticalScore;
              }
            }
          } catch {
            // Fallback if fetchCurrentScore 404s
          }
          if (!res) {
            res = await fetchLatestScore(schemeOptionId);
          }
        }

        let historyList: AnalyticalScore[] = [];
        if (schemeOptionId) {
          historyList = await fetchScoreHistory(schemeOptionId).catch(() => []);
        }

        if (active) {
          setFetchedScore(res);
          setScoreHistory(historyList);
          setLoading(false);
        }
      } catch (err: unknown) {
        if (active) {
          setError(
            err instanceof Error ? err.message : 'Unable to retrieve analytical score from service.'
          );
          setLoading(false);
        }
      }
    };

    loadScore();

    return () => {
      active = false;
    };
  }, [schemeOptionId, calculationRunId, initialScore]);

  const toggleDimension = (dimKey: string) => {
    setExpandedDimensions((prev) => {
      const isCurrentlyExpanded = prev[dimKey] !== false;
      return {
        ...prev,
        [dimKey]: !isCurrentlyExpanded,
      };
    });
  };

  const expandAllDimensions = () => {
    setExpandedDimensions({});
  };

  const collapseAllDimensions = () => {
    if (!scoreData?.dimensions) return;
    const allCollapsed = scoreData.dimensions.reduce((acc, d) => {
      acc[d.dimension] = false;
      return acc;
    }, {} as Record<string, boolean>);
    setExpandedDimensions(allCollapsed);
  };

  // 1. LOADING STATE
  if (loading) {
    return (
      <div
        className={`panel p-5 space-y-5 ${className}`}
        aria-busy="true"
        aria-label="Loading Analytical Scorecard"
      >
        <div className="flex items-center justify-between">
          <div className="skeleton h-5 w-48" />
          <div className="skeleton h-5 w-24" />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="panel-inset p-4 space-y-3">
            <div className="skeleton h-4 w-32" />
            <div className="skeleton h-8 w-24" />
            <div className="skeleton h-3 w-full" />
          </div>
          <div className="panel-inset p-4 space-y-3">
            <div className="skeleton h-4 w-32" />
            <div className="skeleton h-8 w-24" />
            <div className="skeleton h-3 w-full" />
          </div>
        </div>
        <div className="space-y-3">
          <div className="skeleton h-4 w-40" />
          <div className="skeleton h-12 w-full" />
          <div className="skeleton h-12 w-full" />
        </div>
      </div>
    );
  }

  // 2. ERROR STATE
  if (error) {
    return (
      <div className={`state-panel-error p-5 space-y-2 ${className}`} role="alert">
        <div className="flex items-center gap-2">
          <span className="status-badge state-critical">Scorecard Service Error</span>
        </div>
        <h3 className="text-[14px] font-semibold text-text-primary">
          Unable to Retrieve Analytical Score
        </h3>
        <p className="text-[12.5px] leading-[1.6] text-critical-fg">{error}</p>
        <p className="text-[11.5px] text-text-tertiary">
          Ensure the backend scoring service is running and the specified scheme option is registered.
        </p>
      </div>
    );
  }

  // 3. UNAVAILABLE / EMPTY STATE
  if (!scoreData || scoreData.status === 'NOT_APPLICABLE') {
    return (
      <div className={`panel p-6 text-center space-y-3 ${className}`}>
        <div className="inline-flex items-center gap-2">
          <span className="status-badge state-unavailable">Score Unavailable</span>
          <span className="mono-meta">Option #{schemeOptionId}</span>
        </div>
        <h3 className="text-[14px] font-semibold text-text-primary">
          No Analytical Score Calculated Yet
        </h3>
        <p className="max-w-[64ch] mx-auto text-[12.5px] leading-[1.6] text-text-secondary">
          An analytical score has not been calculated for this share class option or the option does not
          currently meet minimum qualification requirements. Analytical scores require verified point-in-time
          NAV observations and benchmark data.
        </p>
      </div>
    );
  }

  const {
    score,
    confidence,
    status,
    scoreVersion,
    methodologyStatus,
    asOfDate,
    referencePopulation,
    disclaimer,
    dimensions,
    evidenceConfidence,
    calculationRunId: runId,
  } = scoreData;

  // Format helpers strictly without calculations
  const displayScore = score !== null ? score.toFixed(2) : '—';
  const displayConfidence = confidence !== null ? confidence.toFixed(2) : '—';

  // Deterministic investigation questions based directly on score data
  const investigationQuestions = getDeterministicInvestigationQuestions(scoreData);

  return (
    <article
      className={`panel overflow-hidden border border-border shadow-xs ${className}`}
      aria-label="YUKIRA Analytical Quality Scorecard"
    >
      {/* 1. SCORECARD HEADER & METHODOLOGY GOVERNANCE BANNER */}
      <header className="border-b border-border bg-surface-raised p-4 sm:p-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="eyebrow">YUKIRA Institutional Research</span>
              <span className="status-badge state-candidate" title="Methodology Governance Tier">
                {scoreVersion || 'YUKIRA_SCORE_V1'} · {methodologyStatus || 'CANDIDATE / RESEARCH'}
              </span>
              <span
                className={`status-badge ${
                  status === 'AVAILABLE' ? 'state-operational' : 'state-unavailable'
                }`}
              >
                {status}
              </span>
            </div>
            <h2 className="mt-1 text-[17px] sm:text-[19px] font-semibold tracking-[-0.015em] text-text-primary">
              Analytical Quality Scorecard
            </h2>
          </div>

          <div className="flex flex-wrap items-center gap-3 text-right">
            <div className="panel-inset px-2.5 py-1 text-left sm:text-right">
              <span className="mono-meta block">As of Date</span>
              <span className="font-mono text-[12px] font-semibold text-text-primary">
                {formatDate(asOfDate)}
              </span>
            </div>
            {runId && (
              <div className="panel-inset px-2.5 py-1 text-left sm:text-right">
                <span className="mono-meta block">Calculation Run</span>
                <span className="font-mono text-[12px] font-semibold text-text-primary">
                  #{runId}
                </span>
              </div>
            )}
          </div>
        </div>

        {/* Epistemic disclaimer notice */}
        <div className="mt-3.5 rounded border border-candidate-border bg-candidate-bg/40 p-3 text-[12px] leading-[1.55] text-candidate-fg">
          <div className="flex items-start gap-2">
            <svg
              className="mt-0.5 h-4 w-4 shrink-0 text-candidate-fg"
              viewBox="0 0 20 20"
              fill="currentColor"
              aria-hidden="true"
            >
              <path
                fillRule="evenodd"
                d="M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.17 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495zM10 5a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 5zm0 9a1 1 0 100-2 1 1 0 000 2z"
                clipRule="evenodd"
              />
            </svg>
            <div>
              <strong className="font-semibold">Candidate Methodology &amp; Provisional Reference:</strong>{' '}
              This score is an empirical mathematical assessment evaluated against a provisional category
              reference ({referencePopulation || 'INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1'}).{' '}
              <strong>Not investment advice, not a recommendation, not a star rating, and not a future return prediction.</strong>{' '}
              Do not commit capital based solely on composite scores.
            </div>
          </div>
        </div>
      </header>

      {/* 2. PRIMARY DUAL-METRIC HERO: SCORE vs CONFIDENCE */}
      <section
        className="border-b border-border bg-surface p-4 sm:p-5"
        aria-label="Analytical Score and Evidence Confidence"
      >
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {/* Tile 1: Quantitative Analytical Score */}
          <div className="metric-tile relative">
            <div className="flex items-baseline justify-between">
              <span className="eyebrow">Quantitative Assessment</span>
              <span className="mono-meta">Max 100.00</span>
            </div>

            <div className="mt-2 flex items-baseline gap-2">
              <span className="data-value-lg text-[2.25rem] text-text-primary">
                {displayScore}
              </span>
              <span className="font-mono text-[14px] text-text-tertiary">/ 100</span>
            </div>

            <h3 className="mt-1 text-[13px] font-semibold text-text-primary">
              Analytical Score
            </h3>
            <p className="mt-0.5 text-[12px] leading-[1.5] text-text-secondary">
              Composite assessment of realized returns, risk behavior, benchmark sensitivity, and downside
              consistency.
            </p>

            {/* Score visual bar */}
            <div className="mt-3">
              <div
                className="h-2 w-full overflow-hidden rounded-full bg-surface-raised"
                role="progressbar"
                aria-valuenow={score ?? 0}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-label={`Analytical Score: ${displayScore} out of 100`}
              >
                <div
                  className="h-full bg-accent transition-all duration-300"
                  style={{ width: `${Math.min(Math.max(score ?? 0, 0), 100)}%` }}
                />
              </div>
            </div>
          </div>

          {/* Tile 2: Evidence & Data Confidence */}
          <div className="metric-tile relative">
            <div className="flex items-baseline justify-between">
              <span className="eyebrow">Epistemic Verification</span>
              <span className="mono-meta">{evidenceConfidence?.assessment || 'High Confidence'}</span>
            </div>

            <div className="mt-2 flex items-baseline gap-2">
              <span className="data-value-lg text-[2.25rem] text-text-primary">
                {displayConfidence}
              </span>
              <span className="font-mono text-[14px] text-text-tertiary">/ 100</span>
            </div>

            <h3 className="mt-1 text-[13px] font-semibold text-text-primary">
              Evidence Confidence
            </h3>
            <p className="mt-0.5 text-[12px] leading-[1.5] text-text-secondary">
              Completeness, verification, and bitemporal point-in-time integrity of underlying market
              observations.
            </p>

            {/* Confidence visual bar */}
            <div className="mt-3">
              <div
                className="h-2 w-full overflow-hidden rounded-full bg-surface-raised"
                role="progressbar"
                aria-valuenow={confidence ?? 0}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-label={`Evidence Confidence: ${displayConfidence} out of 100`}
              >
                <div
                  className="h-full bg-series-2 transition-all duration-300"
                  style={{ width: `${Math.min(Math.max(confidence ?? 0, 0), 100)}%` }}
                />
              </div>
            </div>
          </div>
        </div>

        {/* Epistemic separation note */}
        <div className="mt-3 panel-inset p-3 text-[11.5px] leading-[1.5] text-text-secondary flex items-start gap-2">
          <svg className="h-4 w-4 shrink-0 text-accent mt-0.5" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
          </svg>
          <div>
            <strong className="text-text-primary">Strict Independence Invariant:</strong> Analytical Score and
            Evidence Confidence are reported as separate dimensions. Evidence confidence has strictly{' '}
            <strong className="text-text-primary">0% weight</strong> in the analytical score. Weak evidence never acts
            as a performance penalty: high score with low confidence and low score with high confidence are both
            epistemically valid outcomes.
          </div>
        </div>
      </section>

      {/* 2.1 WHAT THIS SCORE MEANS (Investor Orientation) */}
      <section
        className="border-b border-border bg-surface-raised/40 p-4 sm:p-5"
        aria-label="What This Score Means"
      >
        <div className="flex flex-col gap-3">
          <div>
            <span className="eyebrow">Investor Orientation &middot; Purpose</span>
            <h3 className="mt-0.5 text-[15px] font-semibold text-text-primary">
              What This Score Means
            </h3>
          </div>

          <div className="space-y-2 text-[12.5px] leading-[1.6] text-text-secondary">
            <p>
              The <strong className="text-text-primary">YUKIRA Score</strong> is a 0–100 quantitative quality score summarizing the fund&apos;s evaluated{' '}
              <strong className="text-text-primary">Return Quality</strong>,{' '}
              <strong className="text-text-primary">Risk Quality</strong>,{' '}
              <strong className="text-text-primary">Benchmark-Relative Quality</strong>, and{' '}
              <strong className="text-text-primary">Consistency &amp; Downside</strong> characteristics over the evaluated 3-year historical period.
            </p>
            <div className="rounded border border-border bg-surface-inset p-3 text-[12px] text-text-secondary">
              <strong className="font-semibold text-text-primary">Backward-Looking Analytical Assessment:</strong>{' '}
              This score summarizes historical empirical evidence evaluated under strictly audited mathematical rules. It is{' '}
              <strong>not a forecast of future returns</strong>, <strong>not a probability of profit</strong>, and{' '}
              <strong>not an investment recommendation to buy, hold, or sell</strong>.
            </div>
          </div>
        </div>
      </section>

      {/* 3. PERFORMANCE DIMENSION BREAKDOWN (The 4 Dimensions) */}
      <section className="border-b border-border p-4 sm:p-5" aria-label="Performance Dimensions">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between mb-4">
          <div>
            <span className="eyebrow">Decomposition</span>
            <h3 className="text-[15px] font-semibold text-text-primary">
              Performance Dimensions Breakdown
            </h3>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={expandAllDimensions}
              className="btn btn-ghost btn-sm text-[11.5px]"
            >
              Expand All Metrics
            </button>
            <button
              type="button"
              onClick={collapseAllDimensions}
              className="btn btn-ghost btn-sm text-[11.5px]"
            >
              Collapse
            </button>
          </div>
        </div>

        <div className="space-y-3">
          {dimensions && dimensions.length > 0 ? (
            dimensions.map((dim) => {
              const isExpanded = expandedDimensions[dim.dimension] !== false;
              const hasScore = dim.score !== null && dim.status !== 'INSUFFICIENT_DATA';
              const isAvailable = dim.status === 'AVAILABLE' || dim.status === 'PARTIAL';
              const dimScoreFormatted =
                dim.score !== null && hasScore ? dim.score.toFixed(2) : null;
              const weightPct = (dim.weight * 100).toFixed(1);
              const effWeightPct = (dim.effectiveWeight * 100).toFixed(1);
              const contribFormatted =
                dim.contribution !== null && hasScore ? dim.contribution.toFixed(2) : null;

              return (
                <div
                  key={dim.dimension}
                  className="rounded border border-border bg-surface transition-colors"
                >
                  {/* Dimension row header */}
                  <div className="p-3.5 sm:p-4">
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                      <div className="flex items-center gap-2.5">
                        <button
                          type="button"
                          onClick={() => toggleDimension(dim.dimension)}
                          aria-expanded={isExpanded}
                          aria-controls={`panel-dim-${dim.dimension}`}
                          className="flex h-6 w-6 items-center justify-center rounded border border-border hover:bg-surface-raised focus-visible:outline-none"
                          aria-label={`${isExpanded ? 'Collapse' : 'Expand'} ${dim.dimensionName} metrics`}
                        >
                          <svg
                            className={`h-3.5 w-3.5 text-text-secondary transition-transform ${
                              isExpanded ? 'rotate-90' : ''
                            }`}
                            viewBox="0 0 20 20"
                            fill="currentColor"
                          >
                            <path
                              fillRule="evenodd"
                              d="M7.21 14.77a.75.75 0 01.02-1.06L11.168 10 7.23 6.29a.75.75 0 111.04-1.08l4.5 4.25a.75.75 0 010 1.08l-4.5 4.25a.75.75 0 01-1.06-.02z"
                              clipRule="evenodd"
                            />
                          </svg>
                        </button>

                        <div>
                          <h4 className="text-[13.5px] font-semibold text-text-primary">
                            {dim.dimensionName}
                          </h4>
                          {DIMENSION_EXPLANATIONS[dim.dimension] && (
                            <p className="mt-0.5 text-[12px] leading-[1.4] text-text-secondary">
                              {DIMENSION_EXPLANATIONS[dim.dimension]}
                            </p>
                          )}
                          <span className="mono-meta mt-1 block">
                            Weight: {weightPct}%
                            {weightPct !== effWeightPct && ` (Effective: ${effWeightPct}%)`} ·{' '}
                            {dim.eligibleMetricCount} of {dim.totalMetricCount} metrics eligible
                          </span>
                        </div>
                      </div>

                      <div className="flex items-center gap-3 sm:text-right">
                        <div className="text-left sm:text-right">
                          {hasScore && dimScoreFormatted ? (
                            <div className="flex items-baseline gap-1">
                              <span className="font-mono text-[16px] font-semibold text-text-primary">
                                {dimScoreFormatted}
                              </span>
                              <span className="font-mono text-[11px] text-text-tertiary">/ 100</span>
                              {contribFormatted && (
                                <span className="mono-meta ml-1 text-text-secondary">
                                  (+{contribFormatted} pts)
                                </span>
                              )}
                            </div>
                          ) : (
                            <span className="data-unavailable text-[12.5px]">
                              {dim.status === 'INSUFFICIENT_DATA'
                                ? 'Insufficient History'
                                : 'Not available'}
                            </span>
                          )}
                        </div>

                        <span
                          className={`status-badge shrink-0 ${
                            isAvailable ? 'state-operational' : 'state-unavailable'
                          }`}
                        >
                          {dim.status}
                        </span>
                      </div>
                    </div>

                    {/* Progress bar representation (neutral, no good/bad coloring) */}
                    <div className="mt-3">
                      <div
                        className="h-1.5 w-full overflow-hidden rounded bg-surface-inset"
                        role="progressbar"
                        aria-valuenow={isAvailable && dim.score !== null ? dim.score : 0}
                        aria-valuemin={0}
                        aria-valuemax={100}
                        aria-label={`${dim.dimensionName}: ${
                          isAvailable && dimScoreFormatted ? `${dimScoreFormatted} out of 100` : 'Unavailable'
                        }`}
                      >
                        {isAvailable && dim.score !== null && (
                          <div
                            className="h-full bg-accent transition-all duration-300"
                            style={{
                              width: `${Math.min(Math.max(dim.score, 0), 100)}%`,
                            }}
                          />
                        )}
                      </div>
                    </div>
                  </div>

                  {/* 4. "WHY THIS SCORE?" PROGRESSIVE DISCLOSURE: METRIC BREAKDOWN */}
                  {isExpanded && (
                    <div
                      id={`panel-dim-${dim.dimension}`}
                      className="border-t border-border bg-surface-raised/60 p-3 sm:p-4"
                    >
                      <div className="mb-2 flex items-center justify-between">
                        <span className="eyebrow text-[11px]">
                          Underlying Metric Contributions ({dim.metricContributions?.length || 0})
                        </span>
                        <span className="mono-meta">Normalized &middot; Zero Frontend Math</span>
                      </div>

                      {dim.metricContributions && dim.metricContributions.length > 0 ? (
                        <div className="scroll-region">
                          <table className="data-table text-[12px]">
                            <thead>
                              <tr>
                                <th>Metric</th>
                                <th>Code</th>
                                <th className="num">Raw Observed</th>
                                <th className="num">Normalized</th>
                                <th>Direction</th>
                                <th className="num">Eff. Weight</th>
                                <th className="num">Contribution</th>
                                <th className="num">Observations</th>
                                <th>Status</th>
                              </tr>
                            </thead>
                            <tbody>
                              {dim.metricContributions.map((mc: MetricContribution) => {
                                const isEligible = mc.eligibility === 'ELIGIBLE';
                                return (
                                  <tr key={mc.metricCode || mc.id}>
                                    <td className="key font-medium text-text-primary">
                                      {getCanonicalMetricLabel(mc.metricCode, mc.metricName)}
                                      {mc.exclusionReason && (
                                        <span className="block text-[11px] text-critical-fg">
                                          Reason: {mc.exclusionReason}
                                        </span>
                                      )}
                                    </td>
                                    <td className="font-mono text-[11px] text-text-tertiary">
                                      {mc.metricCode}
                                    </td>
                                    <td className="num font-mono">
                                      {mc.formattedRawValue ||
                                        (mc.rawValue !== null ? String(mc.rawValue) : '—')}
                                      {mc.unit && mc.unit !== 'PERCENTAGE' && ` ${mc.unit}`}
                                    </td>
                                    <td className="num font-mono font-semibold">
                                      {mc.normalizedValue !== null
                                        ? `${mc.normalizedValue.toFixed(2)}`
                                        : '—'}
                                    </td>
                                    <td className="text-[11px] text-text-secondary whitespace-nowrap">
                                      {mc.direction === 'HIGHER_IS_BETTER'
                                        ? 'Higher is better'
                                        : mc.direction === 'LOWER_IS_BETTER'
                                        ? 'Lower is better'
                                        : mc.direction}
                                    </td>
                                    <td className="num font-mono">
                                      {(mc.effectiveWeight * 100).toFixed(1)}%
                                    </td>
                                    <td className="num font-mono text-accent font-semibold">
                                      {isEligible && mc.contribution !== null
                                        ? `+${mc.contribution.toFixed(2)}`
                                        : '—'}
                                    </td>
                                    <td className="num font-mono">
                                      {mc.observationCount !== null ? mc.observationCount : '—'}
                                    </td>
                                    <td>
                                      <span
                                        className={`status-badge text-[10px] ${
                                          isEligible ? 'state-approved' : 'state-unavailable'
                                        }`}
                                      >
                                        {mc.eligibility}
                                      </span>
                                    </td>
                                  </tr>
                                );
                              })}
                            </tbody>
                          </table>
                        </div>
                      ) : (
                        <p className="text-[12px] text-text-secondary italic">
                          No metric contributions registered for this dimension.
                        </p>
                      )}
                    </div>
                  )}
                </div>
              );
            })
          ) : (
            <p className="text-[12px] text-text-secondary">No dimensions available for this score.</p>
          )}
        </div>
      </section>

      {/* 4.5 EPISTEMIC STATES DISCLOSURE */}
      <section className="border-b border-border p-4 sm:p-5" aria-label="Epistemic States Disclosure">
        <div className="flex items-center justify-between">
          <div>
            <span className="eyebrow">Governance Taxonomy &middot; State Definitions</span>
            <h3 className="text-[15px] font-semibold text-text-primary">
              Understanding Epistemic &amp; Governance States
            </h3>
          </div>
          <button
            type="button"
            onClick={() => setShowEpistemicStates(!showEpistemicStates)}
            aria-expanded={showEpistemicStates}
            aria-controls="panel-epistemic-states"
            className="btn btn-secondary btn-sm"
          >
            {showEpistemicStates ? 'Hide State Details' : 'Inspect State Definitions'}
          </button>
        </div>

        {showEpistemicStates && (
          <div id="panel-epistemic-states" className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 text-[12px]">
            <div className="panel-inset p-3.5 space-y-1">
              <div className="flex items-center gap-2">
                <span className="status-badge state-candidate text-[10px]">PARTIAL</span>
                <strong className="font-semibold text-text-primary">Partial Score Status</strong>
              </div>
              <p className="text-[11.5px] leading-[1.5] text-text-secondary">
                The score is calculable and usable under the current methodology, but 1 or more authorized components (e.g., MKT-05 Capture Spread) are currently uncalibrated or carry 0% effective weight. It does <strong>NOT</strong> mean the calculation failed.
              </p>
            </div>

            <div className="panel-inset p-3.5 space-y-1">
              <div className="flex items-center gap-2">
                <span className="status-badge state-candidate text-[10px]">CANDIDATE</span>
                <strong className="font-semibold text-text-primary">Candidate Methodology Tier</strong>
              </div>
              <p className="text-[11.5px] leading-[1.5] text-text-secondary">
                YUKIRA_SCORE_V1 is an implemented quantitative research specification that has not yet reached final validated/approved production tier status. It indicates research-grade execution, not a star rating.
              </p>
            </div>

            <div className="panel-inset p-3.5 space-y-1">
              <div className="flex items-center gap-2">
                <span className="status-badge state-operational text-[10px]">CONFIDENCE 100%</span>
                <strong className="font-semibold text-text-primary">Evidence Confidence 100%</strong>
              </div>
              <p className="text-[11.5px] leading-[1.5] text-text-secondary">
                Underlying required evidence and point-in-time provenance is 100% complete according to data quality rules. It measures <strong>data quality completeness</strong>, NOT 100% confidence of future return performance.
              </p>
            </div>

            <div className="panel-inset p-3.5 space-y-1">
              <div className="flex items-center gap-2">
                <span className="status-badge state-unavailable text-[10px]">UNCALIBRATED</span>
                <strong className="font-semibold text-text-primary">Uncalibrated Metric (MKT-05)</strong>
              </div>
              <p className="text-[11.5px] leading-[1.5] text-text-secondary">
                The metric is calculated and presented as evidence, but carries 0.0% effective weight until complete universe category distribution bounds are finalized.
              </p>
            </div>

            <div className="panel-inset p-3.5 space-y-1 sm:col-span-2">
              <div className="flex items-center gap-2">
                <span className="status-badge state-unavailable text-[10px]">MISSING / INSUFFICIENT DATA</span>
                <strong className="font-semibold text-text-primary">Missing or Insufficient Data State</strong>
              </div>
              <p className="text-[11.5px] leading-[1.5] text-text-secondary">
                Indicates required historical observations or benchmark pairing data are unavailable for the evaluated trading period. Missing data is explicitly reported and <strong>never converted to zero or interpolated</strong>.
              </p>
            </div>
          </div>
        )}
      </section>

      {/* 4.6 WHAT THIS SCORE DOES NOT TELL YOU */}
      <section className="border-b border-border bg-surface-inset p-4 sm:p-5" aria-label="What This Score Does Not Tell You">
        <div className="mb-3">
          <span className="eyebrow">Epistemic Safeguards &middot; Scope Boundaries</span>
          <h3 className="text-[15px] font-semibold text-text-primary">
            What This Score Does NOT Tell You
          </h3>
          <p className="mt-0.5 text-[12px] text-text-secondary">
            YUKIRA Score measures historical quantitative evidence quality. Critical investment boundaries apply:
          </p>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 text-[12px]">
          <div className="panel p-3 space-y-1">
            <strong className="font-semibold text-text-primary block">1. No Future Predictions</strong>
            <p className="text-[11.5px] leading-[1.5] text-text-secondary">
              Does not predict future returns, market outperformance, or future NAV trajectories.
            </p>
          </div>

          <div className="panel p-3 space-y-1">
            <strong className="font-semibold text-text-primary block">2. No Personal Suitability</strong>
            <p className="text-[11.5px] leading-[1.5] text-text-secondary">
              Does not measure whether this fund aligns with your specific investment goals, horizon, tax position, or risk tolerance.
            </p>
          </div>

          <div className="panel p-3 space-y-1">
            <strong className="font-semibold text-text-primary block">3. No Substitute for Due Diligence</strong>
            <p className="text-[11.5px] leading-[1.5] text-text-secondary">
              Does not replace fundamental analysis of fund holdings, portfolio construction, manager style, liquidity, or expense ratios.
            </p>
          </div>

          <div className="panel p-3 space-y-1">
            <strong className="font-semibold text-text-primary block">4. Low Score ≠ Automatic Disqualification</strong>
            <p className="text-[11.5px] leading-[1.5] text-text-secondary">
              A lower score does not automatically make a fund unsuitable for all portfolio roles (e.g., specialized tail hedges or early-stage funds).
            </p>
          </div>

          <div className="panel p-3 space-y-1">
            <strong className="font-semibold text-text-primary block">5. High Score ≠ Automatic Purchase</strong>
            <p className="text-[11.5px] leading-[1.5] text-text-secondary">
              A higher score does not mean a fund is automatically attractive or immune to future market corrections.
            </p>
          </div>

          <div className="panel p-3 space-y-1">
            <strong className="font-semibold text-text-primary block">6. No Advice or Recommendation</strong>
            <p className="text-[11.5px] leading-[1.5] text-text-secondary">
              Does not issue BUY, HOLD, or SELL signals. YUKIRA provides evidence verification, not capital allocation advice.
            </p>
          </div>
        </div>
      </section>

      {/* 5. EVIDENCE & DATA QUALITY AUDIT (distinguishing level observations from paired return periods) */}
      <section className="border-b border-border p-4 sm:p-5" aria-label="Evidence and Data Verification">
        <div className="flex items-center justify-between">
          <div>
            <span className="eyebrow">Evidence Ledger &middot; Data Lineage</span>
            <h3 className="text-[15px] font-semibold text-text-primary">
              Empirical Observations &amp; Verification Evidence
            </h3>
          </div>
          <button
            type="button"
            onClick={() => setShowEvidenceAudit(!showEvidenceAudit)}
            aria-expanded={showEvidenceAudit}
            className="btn btn-secondary btn-sm"
          >
            {showEvidenceAudit ? 'Hide Audit Details' : 'View Audit Details'}
          </button>
        </div>

        {/* Prominent evidence counts: explicitly separating level observations from paired return periods */}
        <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="panel-inset p-3">
            <span className="def-label">Level Observations</span>
            <span className="data-value-md block">
              {evidenceConfidence?.totalObservations ?? '—'}
            </span>
            <span className="mono-meta mt-1 block">NAV Trading Days</span>
          </div>

          <div className="panel-inset p-3">
            <span className="def-label">Paired Return Periods</span>
            <span className="data-value-md block">
              {evidenceConfidence?.pairedReturnPeriods ?? '—'}
            </span>
            <span className="mono-meta mt-1 block">Discrete Return Intervals (N - 1)</span>
          </div>

          <div className="panel-inset p-3">
            <span className="def-label">Valid Observations</span>
            <span className="data-value-md block">
              {evidenceConfidence?.validObservations ?? '—'}
            </span>
            <span className="mono-meta mt-1 block">
              {evidenceConfidence?.totalObservations
                ? `${(
                    (evidenceConfidence.validObservations / evidenceConfidence.totalObservations) *
                    100
                  ).toFixed(1)}% verified valid`
                : '100%'}
            </span>
          </div>

          <div className="panel-inset p-3">
            <span className="def-label">Data Quality Status</span>
            <span className="data-value-md block">
              {evidenceConfidence?.suspiciousObservations === 0 &&
              evidenceConfidence?.invalidObservations === 0
                ? 'CLEAN'
                : 'FLAGGED'}
            </span>
            <span className="mono-meta mt-1 block">
              {evidenceConfidence?.suspiciousObservations ?? 0} suspicious &middot;{' '}
              {evidenceConfidence?.invalidObservations ?? 0} invalid
            </span>
          </div>
        </div>

        {/* Explanatory callout regarding observation-vs-return-period distinction */}
        <div className="mt-3 rounded border border-border bg-surface-inset p-3 text-[12px] leading-[1.55] text-text-secondary">
          <strong className="text-text-primary">Point-in-Time Reconciliation:</strong>{' '}
          {evidenceConfidence?.observationNotes ||
            'The quantitative engine rigorously distinguishes between NAV price level observations and discrete paired return periods (N - 1). It does not conflate price days with return intervals.'}
        </div>

        {showEvidenceAudit && (
          <div className="mt-4 space-y-3 border-t border-border pt-4">
            <h4 className="text-[13px] font-semibold text-text-primary">
              Full Epistemic Verification Manifest
            </h4>
            <dl className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-[12px]">
              <div className="panel-inset p-3">
                <dt className="def-label">Source Payload Verification</dt>
                <dd className="def-value">
                  {evidenceConfidence?.sourceArtifactVerified
                    ? 'Verified against official AMFI payload SHA-256'
                    : 'Unverified source payload'}
                </dd>
              </div>

              <div className="panel-inset p-3">
                <dt className="def-label">Point-in-Time (PIT) Filtering</dt>
                <dd className="def-value">
                  {evidenceConfidence?.pitIntegrityMaintained
                    ? 'Enforced (Dual analysis & knowledge cutoff applied)'
                    : 'Unfiltered'}
                </dd>
              </div>

              <div className="panel-inset p-3">
                <dt className="def-label">Sample Size Threshold</dt>
                <dd className="def-value">
                  {evidenceConfidence?.meetsObservationThreshold
                    ? 'Satisfied (>= 700 minimum required observations)'
                    : 'Below threshold'}
                </dd>
              </div>

              <div className="panel-inset p-3">
                <dt className="def-label">Benchmark Paired Days</dt>
                <dd className="def-value">
                  {evidenceConfidence?.pairedBenchmarkObservations ?? '—'} synchronous trading days
                </dd>
              </div>
            </dl>
          </div>
        )}
      </section>

      {/* 6. METHODOLOGY & PROVISIONAL NORMALIZATION DISCLOSURE */}
      <section className="border-b border-border p-4 sm:p-5" aria-label="Methodology Disclosure">
        <div className="flex items-center justify-between">
          <div>
            <span className="eyebrow">Governance &middot; Normalization Architecture</span>
            <h3 className="text-[15px] font-semibold text-text-primary">
              How This Score Is Calculated
            </h3>
          </div>
          <button
            type="button"
            onClick={() => setShowMethodology(!showMethodology)}
            aria-expanded={showMethodology}
            className="btn btn-secondary btn-sm"
          >
            {showMethodology ? 'Hide Methodology' : 'Inspect Methodology'}
          </button>
        </div>

        {showMethodology && (
          <div className="mt-4 space-y-4 border-t border-border pt-4">
            <div className="rounded border border-candidate-border bg-candidate-bg/30 p-3.5 text-[12px] leading-[1.6] text-candidate-fg">
              <strong className="block font-semibold">Authoritative Methodology Disclosure:</strong>
              The current reference population ({referencePopulation}) is provisional and is not an
              empirical ranking against the complete Indian mutual-fund universe. A score of{' '}
              <strong className="font-mono">{displayScore}</strong> does{' '}
              <strong>NOT</strong> mean this fund ranks in the {displayScore}th percentile of Indian
              mutual funds. Normalization transforms raw quantitative metrics against provisional
              category bounds established for candidate research. It indicates calibrated metric
              behavior, not competitive standing.
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-[12px]">
              <div className="panel-inset p-3">
                <span className="def-label">Methodology Tier</span>
                <span className="def-value font-mono">{scoreVersion}</span>
                <span className="mono-meta mt-1 block">Candidate Research</span>
              </div>

              <div className="panel-inset p-3">
                <span className="def-label">Reference Population</span>
                <span className="def-value font-mono break-all">{referencePopulation}</span>
                <span className="mono-meta mt-1 block">Provisional Category Baseline</span>
              </div>

              <div className="panel-inset p-3">
                <span className="def-label">Formulaic Weights</span>
                <span className="def-value font-mono">
                  Return 30% &middot; Risk 30% &middot; Rel 25% &middot; Downside 15%
                </span>
                <span className="mono-meta mt-1 block">Evidence Confidence 0% Weight</span>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-[12px] pt-2">
              <div className="panel-inset p-3 space-y-1">
                <span className="def-label text-candidate-fg font-semibold">1. PARTIAL Score Status</span>
                <p className="text-[11.5px] leading-[1.5] text-text-secondary">
                  PARTIAL status indicates complete mathematical execution across all available metrics.
                  One or more metrics (e.g., MKT-05) are currently uncalibrated or carry 0% weight.
                  It does <strong>NOT</strong> mean the calculation failed.
                </p>
              </div>

              <div className="panel-inset p-3 space-y-1">
                <span className="def-label text-candidate-fg font-semibold">2. CANDIDATE Governance</span>
                <p className="text-[11.5px] leading-[1.5] text-text-secondary">
                  CANDIDATE methodology tier represents an empirical quantitative research specification.
                  It is <strong>NOT</strong> a production investment advice recommendation engine or star rating.
                </p>
              </div>

              <div className="panel-inset p-3 space-y-1">
                <span className="def-label text-candidate-fg font-semibold">3. 100% Evidence Confidence</span>
                <p className="text-[11.5px] leading-[1.5] text-text-secondary">
                  100% confidence measures complete point-in-time data verification and SHA-256 source provenance.
                  It measures <strong>data quality completeness</strong>, not future return probability.
                </p>
              </div>

              <div className="panel-inset p-3 space-y-1">
                <span className="def-label text-candidate-fg font-semibold">4. Uncalibrated Metric (MKT-05)</span>
                <p className="text-[11.5px] leading-[1.5] text-text-secondary">
                  Capture Spread 3Y (MKT-05) is currently marked UNCALIBRATED in YUKIRA_SCORE_V1 and carries 0.0% effective weight until complete universe category bounds are finalized.
                </p>
              </div>
            </div>

            {disclaimer && (
              <p className="text-[11.5px] leading-[1.5] text-text-tertiary italic">
                {disclaimer}
              </p>
            )}
          </div>
        )}
      </section>

      {/* 6.5 HISTORICAL POINT-IN-TIME SCORE SNAPSHOTS */}
      {scoreHistory && scoreHistory.length > 0 && (
        <section className="border-b border-border p-4 sm:p-5" aria-label="Historical Score Snapshots">
          <div className="flex items-center justify-between">
            <div>
              <span className="eyebrow">Bitemporal Point-in-Time Audit</span>
              <h3 className="text-[15px] font-semibold text-text-primary">
                Historical Score Snapshots ({scoreHistory.length})
              </h3>
            </div>
            <button
              type="button"
              onClick={() => setShowHistory(!showHistory)}
              aria-expanded={showHistory}
              className="btn btn-secondary btn-sm"
            >
              {showHistory ? 'Hide History' : 'Inspect Score History'}
            </button>
          </div>

          {showHistory && (
            <div className="mt-4 space-y-3 border-t border-border pt-4">
              <p className="text-[12px] leading-[1.55] text-text-secondary">
                Point-in-time score evaluations preserved across discrete historical cutoff dates.
                Each snapshot reflects market information known strictly as of its specified knowledge cutoff.
              </p>

              <div className="scroll-region">
                <table className="data-table text-[12px]">
                  <thead>
                    <tr>
                      <th>As-of Date</th>
                      <th>Knowledge Cutoff</th>
                      <th className="num">Score</th>
                      <th className="num">Confidence</th>
                      <th>Status</th>
                      <th>Methodology</th>
                      <th className="num">Run ID</th>
                    </tr>
                  </thead>
                  <tbody>
                    {scoreHistory.map((snap) => (
                      <tr key={snap.scoreId}>
                        <td className="key font-mono font-semibold text-text-primary">
                          {formatDate(snap.asOfDate)}
                        </td>
                        <td className="font-mono text-[11px] text-text-tertiary">
                          {snap.knowledgeCutoffTime ? snap.knowledgeCutoffTime.split('T')[0] : snap.asOfDate}
                        </td>
                        <td className="num font-mono font-bold text-accent">
                          {snap.score !== null ? snap.score.toFixed(2) : '—'} / 100
                        </td>
                        <td className="num font-mono">
                          {snap.confidence !== null ? snap.confidence.toFixed(2) : '—'} / 100
                        </td>
                        <td>
                          <span className={`status-badge text-[10px] ${snap.status === 'AVAILABLE' ? 'state-operational' : 'state-unavailable'}`}>
                            {snap.status}
                          </span>
                        </td>
                        <td className="font-mono text-[11px] text-text-tertiary">
                          {snap.scoreVersion}
                        </td>
                        <td className="num font-mono">
                          #{snap.calculationRunId || '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </section>
      )}

      {/* 7. RESTRAINED WHAT TO INVESTIGATE DUE DILIGENCE PROMPTS */}
      <footer className="bg-surface-raised p-4 sm:p-5" aria-label="Investigation Due Diligence">
        <div className="mb-3">
          <span className="eyebrow">Due Diligence Checkpoint</span>
          <h3 className="text-[15px] font-semibold text-text-primary">
            What to Investigate Before Committing Capital
          </h3>
          <p className="mt-0.5 text-[12px] text-text-secondary">
            Factual analytical questions derived deterministically from this fund&apos;s score and evidence.
            These are investigation prompts for allocators, not recommendations or advice.
          </p>
        </div>

        <ol className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {investigationQuestions.map((q, idx) => (
            <li
              key={idx}
              className="rounded border border-border bg-surface p-3.5 text-[12px] leading-[1.55]"
            >
              <div className="flex items-baseline gap-2">
                <span className="font-mono text-[11px] font-bold text-accent">
                  0{idx + 1}
                </span>
                <h4 className="font-semibold text-text-primary">{q.title}</h4>
              </div>
              <p className="mt-1.5 text-text-secondary">{q.question}</p>
              <span className="mt-2 block font-mono text-[11px] text-text-tertiary">
                Context: {q.context}
              </span>
            </li>
          ))}
        </ol>
      </footer>
    </article>
  );
}

/**
 * Deterministically constructs due-diligence questions based directly on the score data.
 * Zero Large Language Model generation; pure deterministic inspection of dimensional divergence.
 */
function getDeterministicInvestigationQuestions(
  score: AnalyticalScore
): Array<{ title: string; question: string; context: string }> {
  const questions: Array<{ title: string; question: string; context: string }> = [];

  const returnDim = score.dimensions?.find((d) => d.dimension === 'RETURN_QUALITY');
  const riskDim = score.dimensions?.find((d) => d.dimension === 'RISK_QUALITY');
  const benchmarkDim = score.dimensions?.find(
    (d) => d.dimension === 'BENCHMARK_RELATIVE_QUALITY'
  );
  const consistencyDim = score.dimensions?.find(
    (d) => d.dimension === 'CONSISTENCY_DOWNSIDE_QUALITY'
  );

  // 1. Dimension Divergence: Consistency/Downside vs Benchmark-Relative
  if (
    consistencyDim?.score !== null &&
    benchmarkDim?.score !== null &&
    consistencyDim?.score !== undefined &&
    benchmarkDim?.score !== undefined &&
    benchmarkDim.score - consistencyDim.score > 20
  ) {
    questions.push({
      title: 'Downside Consistency vs. Benchmark Outperformance Divergence',
      question: `Why is Consistency & Downside Quality (${consistencyDim.score.toFixed(
        2
      )}) materially lower than Benchmark-Relative Quality (${benchmarkDim.score.toFixed(2)})?`,
      context:
        'The fund generated high benchmark excess returns and information ratio, but exhibited lower rolling consistency and higher drawdown volatility during market correction intervals.',
    });
  } else if (
    returnDim?.score !== null &&
    riskDim?.score !== null &&
    returnDim?.score !== undefined &&
    riskDim?.score !== undefined &&
    Math.abs(returnDim.score - riskDim.score) > 15
  ) {
    questions.push({
      title: 'Return vs. Total Risk Quality Balance',
      question: `What explains the spread between Return Quality (${returnDim.score.toFixed(
        2
      )}) and Risk Quality (${riskDim.score.toFixed(2)})?`,
      context:
        'Examine whether realized returns were achieved through aggressive beta positioning or disciplined capital preservation.',
    });
  } else {
    questions.push({
      title: 'Cross-Dimension Balance',
      question: 'How consistent are the realized outcomes across all four evaluated performance dimensions?',
      context:
        'Evaluate whether the score is driven by a single strong dimension or reflects uniform stability across return, risk, and benchmark sensitivity.',
    });
  }

  // 2. Capture Spread / Asymmetry Investigation
  questions.push({
    title: 'Up-Market vs. Down-Market Capture Asymmetry',
    question: 'What explains the fund’s capture spread across up-market versus down-market regimes?',
    context:
      'Verify whether outperformance is driven by high upside participation during rallies or capital protection during corrections.',
  });

  // 3. Provisional Normalization Sensitivity
  questions.push({
    title: 'Provisional Reference Population Sensitivity',
    question: `How sensitive is the overall score (${
      score.score !== null ? score.score.toFixed(2) : '—'
    }) to the provisional normalization bounds (${score.referencePopulation})?`,
    context:
      'Provisional bounds are calibrated for research. Once full category universe percentiles are ingested, percentile placement may shift.',
  });

  // 4. Metric Contribution Concentration
  questions.push({
    title: 'Metric Contribution Drivers',
    question: 'Which individual metrics drove the largest positive and negative contributions within each dimension?',
    context:
      'Inspect the progressive metric table above to identify whether the score reflects broad multi-metric strength or is skewed by an outlier metric.',
  });

  return questions;
}
