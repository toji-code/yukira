'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import {
  fetchPortfolioComparison,
  PortfolioComparisonResponse,
  ComparisonFund,
  ComparisonMetricRow,
  ScoreDimensionSummary
} from '@/lib/api/portfolio';
import { PortfolioHoldingDto } from '@/lib/api/portfolio';
import { formatCurrency, formatPercentage } from '@/lib/utils/formatters';

interface PortfolioComparisonViewProps {
  holdings: PortfolioHoldingDto[];
}

const MAX_COMPARISON_FUNDS = 5;

export function PortfolioComparisonView({ holdings }: PortfolioComparisonViewProps) {
  // User explicitly toggled/cleared selection, or null if using default portfolio selection
  const [userSelectedIds, setUserSelectedIds] = useState<number[] | null>(null);

  // Compute active selected scheme_option_ids
  const selectedIds = userSelectedIds !== null
    ? userSelectedIds
    : holdings.slice(0, 3).map(h => h.schemeOptionId);

  const [comparisonData, setComparisonData] = useState<PortfolioComparisonResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Fetch comparison data whenever selectedIds change
  useEffect(() => {
    let ignore = false;
    async function loadComparison() {
      if (selectedIds.length === 0) {
        setComparisonData(null);
        return;
      }
      try {
        setLoading(true);
        setError(null);
        const res = await fetchPortfolioComparison(selectedIds);
        if (!ignore) {
          setComparisonData(res as PortfolioComparisonResponse);
          setLoading(false);
        }
      } catch (err: unknown) {
        if (!ignore) {
          setError((err as Error).message || 'Failed to load portfolio comparison data');
          setLoading(false);
        }
      }
    }
    loadComparison();
    return () => {
      ignore = true;
    };
  }, [selectedIds]);

  const toggleFundSelection = (schemeOptionId: number) => {
    if (selectedIds.includes(schemeOptionId)) {
      setUserSelectedIds(selectedIds.filter(id => id !== schemeOptionId));
    } else {
      if (selectedIds.length >= MAX_COMPARISON_FUNDS) {
        setError(`Maximum ${MAX_COMPARISON_FUNDS} funds can be compared side-by-side for optimal layout readability.`);
        return;
      }
      setUserSelectedIds([...selectedIds, schemeOptionId]);
    }
  };

  const selectAllHoldings = () => {
    const ids = holdings.slice(0, MAX_COMPARISON_FUNDS).map(h => h.schemeOptionId);
    setUserSelectedIds(ids);
  };

  const clearSelection = () => {
    setUserSelectedIds([]);
  };

  if (holdings.length === 0) {
    return (
      <div className="panel p-5 space-y-3 border-l-4 border-l-amber-500">
        <h3 className="text-[14px] font-semibold text-text-primary">Portfolio Fund Comparison & Evidence Matrix V1</h3>
        <p className="font-mono text-[11px] text-text-tertiary">
          No external mutual fund holdings found in your portfolio. Import holdings to enable side-by-side fund comparison and evidence verification.
        </p>
      </div>
    );
  }

  const funds: ComparisonFund[] = comparisonData?.funds || [];
  const metrics: ComparisonMetricRow[] = comparisonData?.canonicalMetrics || [];

  return (
    <div className="panel p-5 space-y-6 border-l-4 border-l-indigo-500">
      {/* Header & Feature Directive Banner */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="text-[15px] font-semibold text-text-primary">
              Portfolio Fund Comparison & Evidence Matrix V1
            </h3>
            <span className="status-badge state-verified text-[10px]">EVIDENCE MATRIX</span>
          </div>
          <p className="font-mono text-[11px] text-text-tertiary mt-1">
            Side-by-side analytical comparison of persisted fund scores, risk evidence, benchmark relationships, and investor holdings.
          </p>
        </div>

        <div className="flex items-center gap-2 font-mono text-[11px]">
          <button
            onClick={selectAllHoldings}
            disabled={holdings.length === 0}
            className="px-3 py-1.5 rounded bg-surface border border-border text-text-primary hover:bg-surface-raised font-medium"
          >
            Select All Holdings (Max {MAX_COMPARISON_FUNDS})
          </button>
          <button
            onClick={clearSelection}
            disabled={selectedIds.length === 0}
            className="px-3 py-1.5 rounded bg-surface border border-border text-text-tertiary hover:text-text-primary hover:bg-surface-raised"
          >
            Clear Selection
          </button>
        </div>
      </div>

      {/* Fund Selector Chips Bar */}
      <div className="space-y-2">
        <div className="flex items-center justify-between font-mono text-[11px]">
          <span className="text-text-secondary font-semibold">
            Select Holdings to Compare ({selectedIds.length} / {MAX_COMPARISON_FUNDS} max selected)
          </span>
          <span className="text-text-tertiary">Identity: exact scheme_option_id</span>
        </div>

        <div className="flex flex-wrap gap-2">
          {holdings.map(h => {
            const isSelected = selectedIds.includes(h.schemeOptionId);
            return (
              <button
                key={h.schemeOptionId}
                onClick={() => toggleFundSelection(h.schemeOptionId)}
                className={`px-3 py-1.5 rounded-lg border font-mono text-[11px] transition-all flex items-center gap-2 ${
                  isSelected
                    ? 'bg-accent/10 border-accent text-accent font-semibold shadow-sm'
                    : 'bg-surface border-border text-text-secondary hover:border-text-tertiary hover:bg-surface-raised'
                }`}
              >
                <span>{isSelected ? '✓' : '+'}</span>
                <span className="font-sans">{h.fundName}</span>
                <span className="text-[10px] text-text-tertiary">(ID: {h.schemeOptionId})</span>
              </button>
            );
          })}
        </div>
      </div>

      {error && (
        <div className="panel-inset p-3 bg-rose-500/10 border-rose-500/30 text-rose-300 font-mono text-[11px]">
          {error}
        </div>
      )}

      {selectedIds.length < 2 && (
        <div className="panel-inset p-4 bg-surface-raised text-center font-mono text-[12px] text-text-tertiary">
          Select at least 2 funds from your portfolio above to display the side-by-side evidence matrix.
        </div>
      )}

      {loading && (
        <div className="space-y-3 py-6">
          <div className="skeleton h-12 w-full" />
          <div className="skeleton h-48 w-full" />
          <div className="skeleton h-64 w-full" />
        </div>
      )}

      {!loading && comparisonData && funds.length >= 1 && (
        <div className="space-y-6">

          {/* Section 1: Fund Identity & Summary Header */}
          <div className="space-y-2">
            <h4 className="text-[13px] font-semibold text-text-primary flex items-center justify-between">
              <span>01. Selected Holdings & Canonical Identity</span>
              <span className="font-mono text-[10px] text-text-tertiary font-normal">
                Comparison Size: {funds.length} funds
              </span>
            </h4>

            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-left font-mono text-[11px]">
                <thead className="bg-surface-raised text-text-muted text-[10px] border-b border-border">
                  <tr>
                    <th className="py-2.5 px-3">Identity Attribute</th>
                    {funds.map(f => (
                      <th key={f.schemeOptionId} className="py-2.5 px-3 font-sans font-semibold text-text-primary min-w-[180px]">
                        <Link href={`/funds/${f.schemeOptionId}`} className="hover:text-accent">
                          {f.schemeName}
                        </Link>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">scheme_option_id</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 font-mono font-bold text-accent">
                        {f.schemeOptionId}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">AMC</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-text-secondary">
                        {f.amcName}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Plan & Option</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-text-secondary">
                        {f.planType} / {f.optionType}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">AMFI Code</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-text-secondary">
                        {f.amfiCode || 'Unavailable'}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">ISIN</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-text-secondary">
                        {f.isin || 'Unavailable'}
                      </td>
                    ))}
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          {/* Section 2: Persisted Analytical Score Comparison */}
          <div className="space-y-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h4 className="text-[13px] font-semibold text-text-primary flex items-center gap-2">
                <span>02. Persisted YUKIRA Analytical Scores</span>
                <span className="status-badge state-candidate text-[9px]">CANDIDATE METHODOLOGY</span>
              </h4>
              <span className="font-mono text-[10px] text-text-tertiary">
                No relative ranking score calculated. Factual side-by-side display.
              </span>
            </div>

            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-left font-mono text-[11px]">
                <thead className="bg-surface-raised text-text-muted text-[10px] border-b border-border">
                  <tr>
                    <th className="py-2.5 px-3">Score Attribute</th>
                    {funds.map(f => (
                      <th key={f.schemeOptionId} className="py-2.5 px-3 font-sans font-semibold text-text-primary">
                        {f.schemeName}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  <tr>
                    <td className="py-2.5 px-3 text-text-tertiary font-medium">YUKIRA Analytical Score</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2.5 px-3">
                        {f.analyticalScore && f.analyticalScore.scoreValue !== null ? (
                          <div className="flex items-baseline gap-1">
                            <span className="text-lg font-bold text-accent">
                              {f.analyticalScore.scoreValue.toFixed(2)}
                            </span>
                            <span className="text-text-tertiary text-[10px]">/ 100</span>
                          </div>
                        ) : (
                          <span className="text-text-disabled italic">
                            {f.analyticalScore?.scoreStatus === 'INSUFFICIENT_DATA' ? 'INSUFFICIENT DATA' : 'Unavailable'}
                          </span>
                        )}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Evidence Confidence</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-text-secondary">
                        {f.analyticalScore?.confidence !== null && f.analyticalScore?.confidence !== undefined
                          ? `${f.analyticalScore.confidence.toFixed(0)} / 100`
                          : 'Unavailable'}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Score Status</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3">
                        <span className={`status-badge text-[9px] ${
                          f.analyticalScore?.scoreStatus === 'AVAILABLE' ? 'state-verified' :
                          f.analyticalScore?.scoreStatus === 'PARTIAL' ? 'state-candidate' : 'state-unavailable'
                        }`}>
                          {f.analyticalScore?.scoreStatus || 'UNSCORED'}
                        </span>
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Methodology Version</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-text-secondary">
                        {f.analyticalScore?.methodologyVersion || 'YUKIRA_SCORE_V1'}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Methodology Status</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3">
                        <span className="status-badge state-candidate text-[9px]">
                          {f.analyticalScore?.methodologyStatus || 'CANDIDATE'}
                        </span>
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Analytical As-Of Date</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-text-secondary">
                        {f.analyticalScore?.asOfDate || '2024-01-15'}
                      </td>
                    ))}
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          {/* Section 3: Score Dimensions Breakdown */}
          <div className="space-y-2">
            <h4 className="text-[13px] font-semibold text-text-primary">
              03. Persisted Score Dimension Breakdown
            </h4>

            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-left font-mono text-[11px]">
                <thead className="bg-surface-raised text-text-muted text-[10px] border-b border-border">
                  <tr>
                    <th className="py-2.5 px-3">Score Dimension</th>
                    {funds.map(f => (
                      <th key={f.schemeOptionId} className="py-2.5 px-3 font-sans font-semibold text-text-primary">
                        {f.schemeName}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  {['Return Quality', 'Risk Quality', 'Benchmark-Relative Quality', 'Consistency & Downside Quality'].map((dimName) => (
                    <tr key={dimName}>
                      <td className="py-2 px-3 text-text-secondary font-medium">{dimName}</td>
                      {funds.map(f => {
                        const dim = f.analyticalScore?.dimensions?.find((d: ScoreDimensionSummary) => d.dimensionName === dimName || d.dimension === dimName.toUpperCase().replace(/[^A-Z]/g, '_'));
                        return (
                          <td key={f.schemeOptionId} className="py-2 px-3">
                            {dim && dim.score !== null && dim.score !== undefined ? (
                              <div className="flex items-center gap-2">
                                <span className="font-bold text-text-primary">{dim.score.toFixed(2)}</span>
                                <span className="text-[9px] text-text-tertiary">(wt: {(dim.weight * 100).toFixed(0)}%)</span>
                              </div>
                            ) : (
                              <span className="text-text-disabled italic">Unavailable</span>
                            )}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Section 4: Canonical Metric Evidence Matrix */}
          <div className="space-y-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h4 className="text-[13px] font-semibold text-text-primary flex items-center gap-2">
                <span>04. Canonical Metric Evidence Matrix</span>
                <span className="status-badge state-verified text-[9px]">PERSISTED EVIDENCE</span>
              </h4>
              <span className="font-mono text-[10px] text-text-tertiary">
                Exact metric identities (3-Year evaluation window)
              </span>
            </div>

            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-left font-mono text-[11px]">
                <thead className="bg-surface-raised text-text-muted text-[10px] border-b border-border">
                  <tr>
                    <th className="py-2.5 px-3">Code</th>
                    <th className="py-2.5 px-3">Canonical Metric Name</th>
                    {funds.map(f => (
                      <th key={f.schemeOptionId} className="py-2.5 px-3 font-sans font-semibold text-text-primary text-right min-w-[140px]">
                        {f.schemeName}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  {metrics.map(row => (
                    <tr key={row.metricCode} className="hover:bg-surface-raised/40">
                      <td className="py-2 px-3 font-bold text-accent">{row.metricCode}</td>
                      <td className="py-2 px-3 font-sans text-text-secondary">
                        {row.metricName}
                        {row.metricCode === 'MKT-05' && (
                          <span className="block font-mono text-[9px] text-amber-400 mt-0.5">
                            Available evidence — uncalibrated / not used in YUKIRA_SCORE_V1 score
                          </span>
                        )}
                      </td>
                      {funds.map(f => {
                        const mVal = row.fundValues[String(f.schemeOptionId)];
                        return (
                          <td key={f.schemeOptionId} className="py-2 px-3 text-right">
                            {mVal && mVal.availabilityState === 'AVAILABLE' && mVal.formattedValue ? (
                              <span className={`font-semibold ${
                                row.metricCode === 'RSK-03' ? 'text-rose-400' :
                                row.metricCode === 'RSK-01' ? 'text-amber-300' : 'text-text-primary'
                              }`}>
                                {mVal.formattedValue}
                              </span>
                            ) : (
                              <span className="text-text-disabled italic text-[10px]">
                                Unavailable
                              </span>
                            )}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Section 5: Fund Information & Enrichment */}
          <div className="space-y-2">
            <h4 className="text-[13px] font-semibold text-text-primary">
              05. Fund Information & Terms Enrichment
            </h4>

            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-left font-mono text-[11px]">
                <thead className="bg-surface-raised text-text-muted text-[10px] border-b border-border">
                  <tr>
                    <th className="py-2.5 px-3">Information Field</th>
                    {funds.map(f => (
                      <th key={f.schemeOptionId} className="py-2.5 px-3 font-sans font-semibold text-text-primary">
                        {f.schemeName}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Fund AUM</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-text-secondary">
                        {f.enrichment?.aumFormatted || 'Unavailable'}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Total Expense Ratio (TER)</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-text-secondary">
                        {f.enrichment?.terFormatted || 'Unavailable'}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Fund Manager</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-text-secondary">
                        {f.enrichment?.fundManager || 'Unavailable'}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Investment Terms (Min SIP/Lumpsum)</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-text-secondary">
                        {f.enrichment?.investmentTerms || 'Unavailable'}
                      </td>
                    ))}
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          {/* Section 6: Investor Holding Economics */}
          <div className="space-y-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h4 className="text-[13px] font-semibold text-text-primary flex items-center gap-2">
                <span>06. Investor Holding Economics (Portfolio Context)</span>
                <span className="status-badge state-verified text-[9px]">INVESTOR PRIVATE DATA</span>
              </h4>
              <span className="font-mono text-[10px] text-text-tertiary">
                Holding economics do not alter fund analytical scores or metrics.
              </span>
            </div>

            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-left font-mono text-[11px]">
                <thead className="bg-surface-raised text-text-muted text-[10px] border-b border-border">
                  <tr>
                    <th className="py-2.5 px-3">Holding Attribute</th>
                    {funds.map(f => (
                      <th key={f.schemeOptionId} className="py-2.5 px-3 font-sans font-semibold text-text-primary text-right">
                        {f.schemeName}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border bg-surface">
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Units Held</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-right text-text-primary font-bold">
                        {f.holdingContext?.unitsHeld !== undefined ? f.holdingContext.unitsHeld.toFixed(4) : '0.0000'}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Current Portfolio Weight (%)</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-right text-accent font-bold">
                        {f.holdingContext?.portfolioWeight !== null && f.holdingContext?.portfolioWeight !== undefined
                          ? `${f.holdingContext.portfolioWeight.toFixed(2)}%`
                          : '—'}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Available Valuation</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-right text-text-primary">
                        {f.holdingContext?.availableValue !== null && f.holdingContext?.availableValue !== undefined
                          ? formatCurrency(f.holdingContext.availableValue)
                          : 'Valuation Unavailable'}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Cost Basis / Unit</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-right text-text-secondary">
                        {f.holdingContext?.costBasisAmount !== null && f.holdingContext?.costBasisAmount !== undefined
                          ? formatCurrency(f.holdingContext.costBasisAmount)
                          : 'N/A'}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Invested Amount</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-right text-text-secondary">
                        {f.holdingContext?.investedAmount !== null && f.holdingContext?.investedAmount !== undefined
                          ? formatCurrency(f.holdingContext.investedAmount)
                          : 'N/A'}
                      </td>
                    ))}
                  </tr>
                  <tr>
                    <td className="py-2 px-3 text-text-tertiary">Investor Absolute Gain / Loss</td>
                    {funds.map(f => (
                      <td key={f.schemeOptionId} className="py-2 px-3 text-right font-bold">
                        {f.holdingContext?.absoluteGainLoss !== null && f.holdingContext?.absoluteGainLoss !== undefined ? (
                          <span className={f.holdingContext.absoluteGainLoss >= 0 ? 'text-emerald-400' : 'text-rose-400'}>
                            {f.holdingContext.absoluteGainLoss >= 0 ? '+' : ''}
                            {formatCurrency(f.holdingContext.absoluteGainLoss)}
                            {f.holdingContext.absoluteGainLossPercentage !== null && (
                              <span className="ml-1 text-[10px]">
                                ({f.holdingContext.absoluteGainLossPercentage >= 0 ? '+' : ''}
                                {formatPercentage(f.holdingContext.absoluteGainLossPercentage)})
                              </span>
                            )}
                          </span>
                        ) : (
                          <span className="text-text-disabled">N/A</span>
                        )}
                      </td>
                    ))}
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          {/* Section 7: Data Quality & Coverage Summary */}
          <div className="panel p-4 space-y-3 bg-surface-raised border border-border">
            <h4 className="text-[12px] font-semibold text-text-primary flex items-center justify-between">
              <span>07. Data Quality & Evidence Coverage Summary</span>
              <span className="font-mono text-[10px] text-text-tertiary">
                As-Of: {comparisonData.asOfDate || '2024-01-15'} | Knowledge Cutoff: {comparisonData.knowledgeCutoff || '2024-01-31T23:59:59+05:30'}
              </span>
            </h4>

            {comparisonData.coverageSummary && (
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 font-mono text-[11px]">
                <div className="panel-inset p-2.5 bg-surface">
                  <span className="text-text-muted text-[10px] block">Scored Funds</span>
                  <p className="font-semibold text-text-primary text-[13px] mt-1">
                    {comparisonData.coverageSummary.scoredFundsCount} of {comparisonData.coverageSummary.totalSelectedFunds} Funds
                  </p>
                </div>
                <div className="panel-inset p-2.5 bg-surface">
                  <span className="text-text-muted text-[10px] block">Complete Risk Evidence</span>
                  <p className="font-semibold text-text-primary text-[13px] mt-1">
                    {comparisonData.coverageSummary.completeRiskEvidenceCount} of {comparisonData.coverageSummary.totalSelectedFunds} Funds
                  </p>
                </div>
                <div className="panel-inset p-2.5 bg-surface">
                  <span className="text-text-muted text-[10px] block">Missing Metrics</span>
                  <p className="font-semibold text-text-primary text-[13px] mt-1">
                    {comparisonData.coverageSummary.missingMetricsCount} Metrics
                  </p>
                </div>
                <div className="panel-inset p-2.5 bg-surface">
                  <span className="text-text-muted text-[10px] block">As-Of Date Alignment</span>
                  <p className="font-semibold text-text-primary text-[13px] mt-1">
                    {comparisonData.coverageSummary.differingAsOfDates ? 'Dates Differ' : 'Aligned'}
                  </p>
                </div>
              </div>
            )}
          </div>

          {/* Section 8: Deterministic Investigation Prompts */}
          {comparisonData.investigationPrompts && comparisonData.investigationPrompts.length > 0 && (
            <div className="space-y-2 pt-2 border-t border-border">
              <h4 className="text-[12px] font-semibold text-accent flex items-center gap-2">
                <span>08. Deterministic Evidence Investigation Questions</span>
                <span className="mono-meta text-[10px]">FACTUAL ANALYSIS</span>
              </h4>
              <ul className="list-disc list-inside space-y-1 font-mono text-[11px] text-text-secondary">
                {comparisonData.investigationPrompts.map((promptText: string, idx: number) => (
                  <li key={idx} className="leading-relaxed">{promptText}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Disclaimers & Methodology Scope */}
          <div className="panel-inset p-3 font-mono text-[10px] leading-[1.6] text-text-tertiary space-y-1">
            <p>
              <strong className="text-text-secondary">Comparison Governance Notice:</strong> YUKIRA Portfolio Fund Comparison presents persisted evidence side-by-side without generating composite comparison scores, relative rankings, or BUY/SELL/HOLD recommendations.
            </p>
            <p>
              Reference Population: {comparisonData.referencePopulation || 'INDIAN_EQUITY_FLEXI_CAP_PROVISIONAL_V1'} | Calculation Engine: Pure Python Vectorized Kernel (Deterministic execution)
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
