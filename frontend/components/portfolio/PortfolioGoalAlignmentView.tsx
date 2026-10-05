'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  fetchPortfolioGoalAlignment,
  PortfolioGoalAlignmentDto,
  PortfolioGoalAlignmentRequest,
  HoldingGoalAlignmentDto,
  CriterionEvaluationDto
} from '@/lib/api/portfolio';
import { formatCurrency } from '@/lib/utils/formatters';

const GOAL_CATEGORIES = [
  { label: 'Wealth Creation', value: 'WEALTH_CREATION' },
  { label: 'Retirement', value: 'RETIREMENT' },
  { label: 'Child Education', value: 'CHILD_EDUCATION' },
  { label: 'House / Major Purchase', value: 'MAJOR_PURCHASE' },
  { label: 'Short / Medium-Term', value: 'SHORT_TERM' },
  { label: 'Custom', value: 'CUSTOM' },
];

const HORIZON_OPTIONS = [
  { label: '<3 years', value: 2 },
  { label: '3–5 years', value: 5 },
  { label: '5–10 years', value: 7 },
  { label: '10+ years', value: 10 },
];

const RISK_TOLERANCES = [
  { label: 'Conservative', value: 'CONSERVATIVE' },
  { label: 'Moderate', value: 'MODERATE' },
  { label: 'Aggressive', value: 'AGGRESSIVE' },
];

const INVESTMENT_MODES = [
  { label: 'SIP', value: 'SIP' },
  { label: 'Lumpsum', value: 'LUMPSUM' },
  { label: 'Either', value: 'EITHER' },
];

const CATEGORY_FILTERS = [
  { label: 'Any Category', value: 'ANY' },
  { label: 'Equity Scheme', value: 'Equity Scheme' },
  { label: 'Hybrid Scheme', value: 'Hybrid Scheme' },
  { label: 'Debt Scheme', value: 'Debt Scheme' },
];

export function PortfolioGoalAlignmentView({ hasHoldings }: { hasHoldings: boolean }) {
  const [goalCategory, setGoalCategory] = useState<string>('WEALTH_CREATION');
  const [horizonYears, setHorizonYears] = useState<number>(5);
  const [riskTolerance, setRiskTolerance] = useState<string>('MODERATE');
  const [investmentMode, setInvestmentMode] = useState<string>('SIP');
  const [fundCategory, setFundCategory] = useState<string>('ANY');

  const [alignmentData, setAlignmentData] = useState<PortfolioGoalAlignmentDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    async function loadAlignment() {
      try {
        setLoading(true);
        setError(null);
        const req: PortfolioGoalAlignmentRequest = {
          goalCategory,
          horizonYears,
          riskTolerance,
          investmentMode,
          fundCategory,
        };
        const data = await fetchPortfolioGoalAlignment(req);
        if (!ignore) {
          setAlignmentData(data);
          setLoading(false);
        }
      } catch (err: unknown) {
        if (!ignore) {
          setError((err as Error).message || 'Failed to load goal alignment analysis');
          setLoading(false);
        }
      }
    }
    loadAlignment();
    return () => {
      ignore = true;
    };
  }, [goalCategory, horizonYears, riskTolerance, investmentMode, fundCategory]);

  if (!hasHoldings) {
    return (
      <div className="panel p-4 space-y-3 border-l-4 border-l-purple-500">
        <h3 className="text-[14px] font-semibold text-text-primary">Portfolio Goal Alignment & Horizon Planning V1</h3>
        <p className="font-mono text-[11px] text-text-tertiary">
          No external mutual fund holdings found in your portfolio. Add holdings above to evaluate alignment against selected goal criteria.
        </p>
      </div>
    );
  }

  const getCriterionBadgeClass = (state: string) => {
    switch (state?.toUpperCase()) {
      case 'MATCH':
      case 'SUPPORTED':
      case 'AVAILABLE':
        return 'state-verified';
      case 'NO_MATCH':
      case 'UNAVAILABLE':
        return 'state-unavailable';
      case 'UNKNOWN':
        return 'bg-amber-500/10 text-amber-400 border border-amber-500/20';
      case 'NOT_APPLICABLE':
      default:
        return 'state-candidate';
    }
  };

  const getAlignmentBadgeClass = (state: string) => {
    switch (state?.toUpperCase()) {
      case 'ELIGIBLE':
      case 'ALIGNED':
        return 'state-verified';
      case 'PARTIALLY_EVALUATED':
        return 'bg-amber-500/10 text-amber-400 border border-amber-500/20';
      case 'NOT_ELIGIBLE':
      case 'NOT_ALIGNED':
        return 'state-unavailable';
      case 'INSUFFICIENT_DATA':
      default:
        return 'state-candidate';
    }
  };

  return (
    <div className="panel p-5 space-y-5 border-l-4 border-l-purple-500">
      {/* Section Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
        <div>
          <h3 className="text-[15px] font-semibold text-text-primary flex items-center gap-2">
            <span>Portfolio Goal Alignment & Horizon Planning V1</span>
            <span className="status-badge state-verified text-[10px]">EVIDENCE-BASED COVERAGE</span>
          </h3>
          <p className="font-mono text-[11px] text-text-tertiary mt-0.5">
            See what your current portfolio evidence can and cannot tell you about the goal requirements you selected.
          </p>
        </div>
        {alignmentData && (
          <div className="flex items-center gap-2">
            <span className={`status-badge ${
              alignmentData.coverageState === 'COMPLETE' ? 'state-verified' : 'state-candidate'
            }`}>
              {alignmentData.coverageState} COVERAGE
            </span>
          </div>
        )}
      </div>

      {/* Goal Requirement Selectors Bar */}
      <div className="panel-inset p-3.5 bg-surface space-y-3 font-mono text-[11px]">
        <div className="flex items-center justify-between border-b border-border/50 pb-2">
          <span className="font-sans font-medium text-text-secondary text-[12px]">Selected Goal Criteria</span>
          <span className="text-text-tertiary text-[10px]">Stateless evaluation against Goal Discovery V1 semantics</span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
          {/* Goal Category */}
          <div>
            <label className="text-text-muted text-[10px] block mb-1">Goal Category</label>
            <select
              value={goalCategory}
              onChange={(e) => setGoalCategory(e.target.value)}
              className="w-full bg-surface-raised border border-border rounded px-2 py-1 text-text-primary text-[11px] focus:outline-none focus:border-accent"
            >
              {GOAL_CATEGORIES.map((cat) => (
                <option key={cat.value} value={cat.value}>{cat.label}</option>
              ))}
            </select>
          </div>

          {/* Horizon */}
          <div>
            <label className="text-text-muted text-[10px] block mb-1">Investment Horizon</label>
            <select
              value={horizonYears}
              onChange={(e) => setHorizonYears(parseInt(e.target.value, 10))}
              className="w-full bg-surface-raised border border-border rounded px-2 py-1 text-text-primary text-[11px] focus:outline-none focus:border-accent"
            >
              {HORIZON_OPTIONS.map((hor) => (
                <option key={hor.value} value={hor.value}>{hor.label}</option>
              ))}
            </select>
          </div>

          {/* Risk Tolerance */}
          <div>
            <label className="text-text-muted text-[10px] block mb-1">Risk Tolerance</label>
            <select
              value={riskTolerance}
              onChange={(e) => setRiskTolerance(e.target.value)}
              className="w-full bg-surface-raised border border-border rounded px-2 py-1 text-text-primary text-[11px] focus:outline-none focus:border-accent"
            >
              {RISK_TOLERANCES.map((risk) => (
                <option key={risk.value} value={risk.value}>{risk.label}</option>
              ))}
            </select>
          </div>

          {/* Investment Mode */}
          <div>
            <label className="text-text-muted text-[10px] block mb-1">Investment Mode</label>
            <select
              value={investmentMode}
              onChange={(e) => setInvestmentMode(e.target.value)}
              className="w-full bg-surface-raised border border-border rounded px-2 py-1 text-text-primary text-[11px] focus:outline-none focus:border-accent"
            >
              {INVESTMENT_MODES.map((mode) => (
                <option key={mode.value} value={mode.value}>{mode.label}</option>
              ))}
            </select>
          </div>

          {/* Fund Category Filter */}
          <div>
            <label className="text-text-muted text-[10px] block mb-1">Category Filter</label>
            <select
              value={fundCategory}
              onChange={(e) => setFundCategory(e.target.value)}
              className="w-full bg-surface-raised border border-border rounded px-2 py-1 text-text-primary text-[11px] focus:outline-none focus:border-accent"
            >
              {CATEGORY_FILTERS.map((f) => (
                <option key={f.value} value={f.value}>{f.label}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {loading && (
        <div className="py-6 text-center font-mono text-[11px] text-text-tertiary">
          Evaluating portfolio goal alignment against master database records...
        </div>
      )}

      {error && (
        <div className="state-panel-error">
          <span className="font-mono text-[12px]">Error: {error}</span>
        </div>
      )}

      {alignmentData && !loading && (
        <>
          {/* Portfolio-Level Factual Coverage Summary Cards */}
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 font-mono text-[11px]">
            <div className="panel-inset p-3 bg-surface">
              <span className="text-text-muted text-[10px] block">Aligned Portfolio Weight</span>
              <p className="font-semibold text-emerald-400 text-[14px] mt-1">
                {alignmentData.alignedPortfolioWeightPercentage.toFixed(2)}%
              </p>
              <p className="text-text-tertiary text-[10px] mt-0.5">
                {alignmentData.alignedHoldingsCount} of {alignmentData.totalHoldingsCount} holdings
              </p>
            </div>

            <div className="panel-inset p-3 bg-surface">
              <span className="text-text-muted text-[10px] block font-sans">Partially Evaluated</span>
              <p className="font-semibold text-amber-400 text-[14px] mt-1">
                {alignmentData.partiallyEvaluatedPortfolioWeightPercentage.toFixed(2)}%
              </p>
              <p className="text-text-tertiary text-[10px] mt-0.5">
                {alignmentData.partiallyEvaluatedHoldingsCount} holding(s) with UNKNOWN criteria
              </p>
            </div>

            <div className="panel-inset p-3 bg-surface">
              <span className="text-text-muted text-[10px] block font-sans">Not Aligned Weight</span>
              <p className="font-semibold text-rose-400 text-[14px] mt-1">
                {alignmentData.notAlignedPortfolioWeightPercentage.toFixed(2)}%
              </p>
              <p className="text-text-tertiary text-[10px] mt-0.5">
                {alignmentData.notAlignedHoldingsCount} holding(s) failed category filter
              </p>
            </div>

            <div className="panel-inset p-3 bg-surface">
              <span className="text-text-muted text-[10px] block font-sans">Incomplete Evidence</span>
              <p className="font-semibold text-text-primary text-[14px] mt-1">
                {alignmentData.unknownExposurePercentage.toFixed(2)}%
              </p>
              <p className="text-text-tertiary text-[10px] mt-0.5">
                Missing lock-in or Riskometer records
              </p>
            </div>
          </div>

          {/* Holding-Level Goal Alignment Table */}
          <div className="space-y-2 pt-2">
            <h4 className="text-[12px] font-semibold text-text-secondary flex items-center justify-between">
              <span>Holding-Level Alignment ({alignmentData.holdingEvaluations.length})</span>
              <span className="font-mono text-[10px] font-normal text-text-tertiary">Exact scheme_option_id evaluation</span>
            </h4>

            <div className="overflow-x-auto rounded border border-border">
              <table className="w-full text-left font-mono text-[11px]">
                <thead className="bg-surface-raised text-text-muted text-[10px] border-b border-border">
                  <tr>
                    <th className="py-2.5 px-3">Scheme Option</th>
                    <th className="py-2.5 px-3 text-right">Exposure Share</th>
                    <th className="py-2.5 px-3">Criteria Evaluation</th>
                    <th className="py-2.5 px-3 text-center">Alignment State</th>
                    <th className="py-2.5 px-3 text-right">YUKIRA Fund Score</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  {alignmentData.holdingEvaluations.map((h: HoldingGoalAlignmentDto) => (
                    <tr key={h.schemeOptionId} className="hover:bg-surface-raised/50">
                      <td className="py-3 px-3 font-sans">
                        <Link href={`/funds/${h.schemeOptionId}`} className="font-medium text-text-primary hover:text-accent">
                          {h.fundName}
                        </Link>
                        <div className="font-mono text-[10px] text-text-tertiary mt-0.5 flex items-center gap-2">
                          <span>Option ID: #{h.schemeOptionId}</span>
                          <span>AMFI: {h.amfiCode}</span>
                          <span>ISIN: {h.isin}</span>
                        </div>
                      </td>

                      <td className="py-3 px-3 text-right">
                        <span className="font-semibold text-text-primary">
                          {h.portfolioWeightPercentage !== null ? `${h.portfolioWeightPercentage.toFixed(2)}%` : '—'}
                        </span>
                        <span className="block text-[10px] text-text-tertiary">
                          {h.marketValue !== null ? formatCurrency(h.marketValue) : 'Unvalued'}
                        </span>
                      </td>

                      <td className="py-3 px-3">
                        <div className="space-y-1 text-[10px]">
                          {Object.entries(h.criteriaMap || {}).map(([key, evalItem]: [string, CriterionEvaluationDto]) => (
                            <div key={key} className="flex items-center gap-1.5">
                              <span className="w-24 text-text-muted capitalize">{key}:</span>
                              <span className={`status-badge text-[9px] ${getCriterionBadgeClass(evalItem.state)}`}>
                                {evalItem.state}
                              </span>
                            </div>
                          ))}
                        </div>
                      </td>

                      <td className="py-3 px-3 text-center">
                        <span className={`status-badge ${getAlignmentBadgeClass(h.alignmentState)}`}>
                          {h.alignmentState}
                        </span>
                      </td>

                      <td className="py-3 px-3 text-right">
                        {h.analyticalScore && h.analyticalScore.available && h.analyticalScore.scoreValue !== null ? (
                          <div>
                            <span className="font-mono font-semibold text-accent text-[12px]">
                              {h.analyticalScore.scoreValue.toFixed(2)}
                            </span>
                            <span className="block text-[9px] text-text-tertiary">
                              YUKIRA Quality Score
                            </span>
                          </div>
                        ) : (
                          <div>
                            <span className="text-text-muted text-[11px]">Score Unavailable</span>
                            <span className="block text-[9px] text-text-tertiary">Separate from alignment</span>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Investigation Questions & Limitations Section */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
            {/* What YUKIRA Cannot Evaluate / Questions */}
            <div className="panel-inset p-3.5 bg-surface space-y-2">
              <h4 className="text-[12px] font-semibold text-text-primary flex items-center gap-1.5">
                <span className="text-amber-400">?</span>
                <span>Questions Worth Investigating</span>
              </h4>
              <ul className="space-y-1.5 font-mono text-[11px] text-text-secondary list-disc list-inside">
                {alignmentData.investigationQuestions.map((q, idx) => (
                  <li key={idx} className="leading-relaxed">{q}</li>
                ))}
              </ul>
            </div>

            {/* Methodological Limitations & Boundaries */}
            <div className="panel-inset p-3.5 bg-surface space-y-2">
              <h4 className="text-[12px] font-semibold text-text-primary flex items-center gap-1.5">
                <span className="text-blue-400">ℹ</span>
                <span>Evidence Boundaries & Disclaimers</span>
              </h4>
              <ul className="space-y-1.5 font-mono text-[10px] text-text-tertiary list-disc list-inside">
                {alignmentData.limitations.map((lim, idx) => (
                  <li key={idx} className="leading-relaxed">{lim}</li>
                ))}
              </ul>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
