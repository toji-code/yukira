'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import {
  ResearchPortfolioFundItemDto,
  PortfolioRiskAttributionResponseDto,
  fetchPortfolioRiskAttribution,
} from '@/lib/api/portfolio';
import { SchemeOption } from '@/types/domain';

interface RiskAttributionSectionProps {
  selectedFunds: ResearchPortfolioFundItemDto[];
  availableSchemes?: SchemeOption[];
  asOfDate: string;
}

export function RiskAttributionSection({
  selectedFunds,
  availableSchemes = [],
  asOfDate,
}: RiskAttributionSectionProps) {
  const [benchmarkCode, setBenchmarkCode] = useState<string>('NIFTY_500_TRI');
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<PortfolioRiskAttributionResponseDto | null>(null);

  // Sub-view toggle for Exposure Context
  const [exposureTab, setExposureTab] = useState<'sectors' | 'marketCap' | 'securities'>('sectors');

  // Trigger evaluation
  const runAttribution = useCallback(async (fundsToRun = selectedFunds, bm = benchmarkCode, date = asOfDate) => {
    if (fundsToRun.length === 0) {
      setData(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await fetchPortfolioRiskAttribution({
        funds: fundsToRun,
        asOfDate: date,
        benchmarkCode: bm,
      });
      setData(res);
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to calculate portfolio risk attribution.');
    } finally {
      setLoading(false);
    }
  }, [selectedFunds, benchmarkCode, asOfDate]);

  useEffect(() => {
    if (selectedFunds.length === 0) {
      setData(null);
      return;
    }
    let ignore = false;
    const timer = setTimeout(() => {
      setLoading(true);
      setError(null);
    }, 0);

    fetchPortfolioRiskAttribution({
      funds: selectedFunds,
      asOfDate,
      benchmarkCode,
    })
      .then((res) => {
        if (!ignore) {
          setData(res);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          setError((err as Error).message || 'Failed to calculate portfolio risk attribution.');
        }
      })
      .finally(() => {
        if (!ignore) {
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
      clearTimeout(timer);
    };
  }, [selectedFunds, benchmarkCode, asOfDate]);

  // Helper to resolve fund name
  const getFundName = (optId: number, fallback: string) => {
    const opt = availableSchemes.find(s => s.id === optId);
    return opt?.plan?.scheme?.name || fallback;
  };

  // 1. EMPTY STATE
  if (selectedFunds.length === 0) {
    return (
      <div className="panel p-8 text-center space-y-3">
        <span className="text-3xl">⚖️</span>
        <h3 className="text-[14px] font-semibold text-text-primary">No Constituent Funds Selected</h3>
        <p className="font-mono text-[11px] text-text-tertiary max-w-md mx-auto">
          Please add mutual funds to your research portfolio builder above to evaluate covariance-based risk decomposition.
        </p>
      </div>
    );
  }

  // 2. LOADING SKELETON
  if (loading && !data) {
    return (
      <div className="space-y-4">
        <div className="panel p-6 space-y-4 animate-pulse">
          <div className="h-4 bg-surface-raised rounded w-1/3" />
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            {[1, 2, 3, 4, 5, 6].map(i => (
              <div key={i} className="h-20 bg-surface-raised rounded" />
            ))}
          </div>
          <div className="h-48 bg-surface-raised rounded" />
        </div>
      </div>
    );
  }

  // 3. ERROR STATE
  if (error && !data) {
    return (
      <div className="panel p-6 border-l-4 border-l-rose-500 space-y-3">
        <div className="flex items-center gap-2 text-rose-400 font-semibold text-[13px]">
          <span>⚠️</span>
          <span>Risk Attribution Calculation Failed</span>
        </div>
        <p className="font-mono text-[11px] text-text-secondary">{error}</p>
        <button
          onClick={() => runAttribution()}
          className="px-3 py-1.5 rounded bg-surface-raised border border-border text-[11px] font-mono hover:bg-surface font-semibold"
        >
          ↻ Retry Calculation
        </button>
      </div>
    );
  }

  const isAvailable = data?.evaluationStatus === 'AVAILABLE';

  return (
    <div className="space-y-6">
      {/* SECTION HEADER & CONTROL BAR */}
      <div className="panel p-4 flex flex-wrap items-center justify-between gap-4 bg-surface-raised/40">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="font-mono text-[10px] uppercase tracking-wider text-accent font-bold">
              PORTFOLIO RISK ATTRIBUTION (PHASE 2O)
            </span>
            {isAvailable ? (
              <span className="status-badge state-verified text-[9px]">EULER RECONCILED</span>
            ) : (
              <span className="status-badge state-candidate text-[9px]">{data?.evaluationStatus || 'UNAVAILABLE'}</span>
            )}
          </div>
          <h2 className="text-[15px] font-semibold text-text-primary">
            Covariance-Based Volatility Decomposition
          </h2>
          <p className="font-mono text-[11px] text-text-tertiary">
            Exact mathematical decomposition of annualized portfolio volatility into constituent fund contributions: RC_i = w_i · (Σw)_i / σ_p.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {/* Benchmark Selector */}
          <div className="flex items-center gap-1.5">
            <label htmlFor="riskBmSelect" className="font-mono text-[11px] text-text-tertiary">
              Benchmark:
            </label>
            <select
              id="riskBmSelect"
              value={benchmarkCode}
              onChange={e => setBenchmarkCode(e.target.value)}
              className="field text-[11px] py-1 px-2.5 h-8 font-mono"
            >
              <option value="NIFTY_500_TRI">NIFTY 500 TRI</option>
              <option value="NIFTY_50">NIFTY 50</option>
            </select>
          </div>

          <button
            onClick={() => runAttribution()}
            disabled={loading}
            className="px-3.5 py-1.5 rounded bg-accent text-surface-dark font-mono text-[11px] font-bold hover:bg-accent/90 disabled:opacity-50 transition-all"
          >
            {loading ? 'Evaluating...' : '↻ Recalculate'}
          </button>
        </div>
      </div>

      {/* WARNINGS & GATING NOTICES */}
      {data?.dataQualityWarnings && data.dataQualityWarnings.length > 0 && (
        <div className="p-3.5 rounded border border-amber-500/30 bg-amber-500/10 text-amber-300 font-mono text-[11px] space-y-1">
          <span className="font-bold flex items-center gap-1.5">
            <span>⚠️</span> Data Coverage & Methodological Disclosures:
          </span>
          <ul className="list-disc list-inside space-y-0.5 opacity-90 pl-1">
            {data.dataQualityWarnings.map((w, idx) => (
              <li key={idx}>{w}</li>
            ))}
          </ul>
        </div>
      )}

      {/* SECTION A — PORTFOLIO RISK OVERVIEW */}
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <h3 className="text-[13px] font-semibold text-text-primary uppercase font-mono tracking-wider">
            Section A — Portfolio Risk Overview
          </h3>
          {data?.calculationStartDate && data?.calculationEndDate && (
            <span className="font-mono text-[10.5px] text-text-tertiary">
              Synchronized Window: {data.calculationStartDate} to {data.calculationEndDate} ({data.alignedObservationCount} days)
            </span>
          )}
        </div>

        {data?.overviewMetrics && data.overviewMetrics.length > 0 ? (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            {data.overviewMetrics.map((m) => (
              <div key={m.metricCode} className="metric-tile flex flex-col justify-between">
                <div>
                  <span className="eyebrow truncate block" title={m.metricName}>{m.metricName}</span>
                  <div className="metric-rule" />
                </div>
                <div className="my-1">
                  <p className="data-value-lg text-text-primary">
                    {m.formattedValue}
                  </p>
                </div>
                <p className="font-mono text-[9.5px] text-text-tertiary line-clamp-2" title={m.description}>
                  {m.description}
                </p>
              </div>
            ))}
          </div>
        ) : (
          <div className="panel p-4 font-mono text-[11px] text-text-tertiary text-center">
            Portfolio risk metrics are unavailable for the current date and constituent funds.
          </div>
        )}
      </div>

      {/* SECTION B — FUND-LEVEL VOLATILITY CONTRIBUTION */}
      <div className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 className="text-[13px] font-semibold text-text-primary uppercase font-mono tracking-wider">
              Section B — Fund-Level Volatility Contribution
            </h3>
            <p className="font-mono text-[11px] text-text-tertiary">
              Euler decomposition: RC_i = w_i · (Σw)_i / σ_p. Component risk contributions sum exactly to annualized portfolio volatility.
            </p>
          </div>

          {/* Mathematical Reconciliation Status Badge */}
          {data?.reconciliation && (
            <div className={`p-2 rounded border font-mono text-[11px] flex items-center gap-2 ${
              data.reconciliation.isReconciled
                ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
                : 'bg-amber-500/10 border-amber-500/30 text-amber-300'
            }`}>
              <span className="font-bold">Reconciliation:</span>
              <span>
                Σ RC_i ({data.reconciliation.sumRiskContributionsPct?.toFixed(2)}%) = σ_p ({data.reconciliation.portfolioVolatilityPct?.toFixed(2)}%)
              </span>
              <span className="text-[9.5px] opacity-75">
                (Δ {data.reconciliation.reconciliationDifferencePct?.toFixed(4)}% pts)
              </span>
            </div>
          )}
        </div>

        {/* 1. Horizontal Bar Chart of Volatility Contributions */}
        {data?.fundContributions && data.fundContributions.length > 0 ? (
          <div className="panel p-5 space-y-4 bg-surface">
            <h4 className="text-[12px] font-semibold font-mono text-text-secondary uppercase">
              Volatility Contribution Breakdown (% of Total Portfolio Risk)
            </h4>

            <div className="space-y-3.5">
              {data.fundContributions.map((fc) => {
                const pct = fc.percentageRiskContribution || 0;
                const isNegative = fc.isNegativeContribution;
                const absPct = Math.min(100, Math.abs(pct));
                const capWeight = fc.allocationWeightPct || 0;

                return (
                  <div key={fc.schemeOptionId} className="space-y-1 font-mono text-[11px]">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="font-sans font-semibold text-text-primary text-[12px]">
                          {getFundName(fc.schemeOptionId, fc.fundName)}
                        </span>
                        <span className="text-[10px] text-text-tertiary">({fc.planType})</span>
                        {isNegative ? (
                          <span className="px-1.5 py-0.5 rounded text-[9px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/40">
                            HISTORICAL VARIANCE DAMPENER
                          </span>
                        ) : fc.isDiversificationDampener ? (
                          <span className="px-1.5 py-0.5 rounded text-[9px] font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-500/40">
                            BELOW-WEIGHT CONTRIBUTION
                          </span>
                        ) : (
                          <span className="px-1.5 py-0.5 rounded text-[9px] font-bold bg-amber-500/20 text-amber-300 border border-amber-500/40">
                            PRIMARY RISK DRIVER
                          </span>
                        )}
                      </div>

                      <div className="flex items-center gap-3">
                        <span className="text-text-secondary text-[11px]">
                          Capital: <strong className="text-text-primary">{capWeight.toFixed(1)}%</strong>
                        </span>
                        <span className="text-[11px]">
                          Risk Contrib:{' '}
                          <strong className={isNegative ? 'text-emerald-400 font-bold' : 'text-accent font-bold'}>
                            {fc.volatilityContributionPct >= 0 ? '+' : ''}{fc.volatilityContributionPct?.toFixed(2)}% pts
                          </strong>{' '}
                          ({pct.toFixed(1)}%)
                        </span>
                      </div>
                    </div>

                    {/* Comparative Visual Bar */}
                    <div className="space-y-1">
                      {/* Risk Contribution Bar */}
                      <div className="h-4 w-full bg-surface-raised rounded-sm overflow-hidden flex items-center">
                        <div
                          className={`h-full transition-all duration-500 ${
                            isNegative ? 'bg-emerald-500' : 'bg-accent'
                          }`}
                          style={{ width: `${Math.max(2, absPct)}%` }}
                          title={`Risk Contribution: ${pct.toFixed(1)}%`}
                        />
                      </div>
                      {/* Capital Weight Bar for comparison */}
                      <div className="h-1.5 w-full bg-surface-raised/40 rounded-sm overflow-hidden flex items-center">
                        <div
                          className="h-full bg-text-tertiary/40"
                          style={{ width: `${Math.max(1, capWeight)}%` }}
                          title={`Capital Weight: ${capWeight.toFixed(1)}%`}
                        />
                      </div>
                    </div>

                    <p className="text-[10px] text-text-tertiary italic">
                      {fc.interpretation}
                    </p>
                  </div>
                );
              })}
            </div>

            {/* Reconciliation Formula Bar */}
            {data.reconciliation && (
              <div className="pt-3 border-t border-border flex flex-wrap items-center justify-between gap-2 font-mono text-[10.5px] text-text-tertiary">
                <div>
                  <span className="font-semibold text-text-secondary">Euler Homogeneity Identity: </span>
                  <code>{data.reconciliation.mathematicalIdentityFormula}</code>
                </div>
                <div>
                  Sum of Components: <strong className="text-text-primary">{data.reconciliation.sumRiskContributionsPct?.toFixed(2)}%</strong> |
                  Portfolio Volatility: <strong className="text-text-primary">{data.reconciliation.portfolioVolatilityPct?.toFixed(2)}%</strong>
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="panel p-6 text-center font-mono text-[11px] text-text-tertiary">
            Fund-level volatility contributions cannot be calculated. Check synchronized data availability.
          </div>
        )}

        {/* 2. Detailed Numerical Table */}
        {data?.fundContributions && data.fundContributions.length > 0 && (
          <div className="overflow-x-auto rounded border border-border">
            <table className="w-full text-left font-mono text-[11px]">
              <thead className="bg-surface-raised text-text-muted text-[10px] border-b border-border">
                <tr>
                  <th className="py-2.5 px-3">Constituent Fund</th>
                  <th className="py-2.5 px-3">Plan / AMFI</th>
                  <th className="py-2.5 px-3 text-right">Allocation Weight (w_i)</th>
                  <th className="py-2.5 px-3 text-right">Fund Volatility (σ_i)</th>
                  <th className="py-2.5 px-3 text-right">Marginal Risk (MC_i)</th>
                  <th className="py-2.5 px-3 text-right text-accent font-bold">Volatility Contrib (RC_i)</th>
                  <th className="py-2.5 px-3 text-right">% of Total Risk</th>
                  <th className="py-2.5 px-3">Role & Diversification</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border bg-surface">
                {data.fundContributions.map((fc) => (
                  <tr key={fc.schemeOptionId} className="hover:bg-surface-raised/40">
                    <td className="py-2.5 px-3 font-sans font-medium text-text-primary">
                      <Link href={`/funds/${fc.schemeOptionId}`} className="hover:text-accent">
                        {getFundName(fc.schemeOptionId, fc.fundName)}
                      </Link>
                    </td>
                    <td className="py-2.5 px-3 text-text-tertiary">
                      {fc.planType} · {fc.amfiCode || 'N/A'}
                    </td>
                    <td className="py-2.5 px-3 text-right font-bold">
                      {fc.allocationWeightPct?.toFixed(2)}%
                    </td>
                    <td className="py-2.5 px-3 text-right text-text-secondary">
                      {fc.annualizedVolatilityPct?.toFixed(2)}%
                    </td>
                    <td className="py-2.5 px-3 text-right text-text-secondary">
                      {fc.marginalContributionPct >= 0 ? '+' : ''}{fc.marginalContributionPct?.toFixed(2)}%
                    </td>
                    <td className="py-2.5 px-3 text-right font-bold">
                      <span className={fc.isNegativeContribution ? 'text-emerald-400' : 'text-accent'}>
                        {fc.volatilityContributionPct >= 0 ? '+' : ''}{fc.volatilityContributionPct?.toFixed(2)}% pts
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-right font-bold text-text-primary">
                      {fc.percentageRiskContribution?.toFixed(2)}%
                    </td>
                    <td className="py-2.5 px-3">
                      {fc.isNegativeContribution ? (
                        <span className="text-emerald-400 font-semibold text-[10px]">Variance Dampener (Sample Cov &lt; 0)</span>
                      ) : fc.isDiversificationDampener ? (
                        <span className="text-indigo-400 font-semibold text-[10px]">Below-Weight Contributor</span>
                      ) : (
                        <span className="text-amber-400 font-semibold text-[10px]">Primary Driver</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
              {data.reconciliation && (
                <tfoot className="bg-surface-raised font-bold border-t border-border">
                  <tr>
                    <td colSpan={2} className="py-2 px-3 text-text-secondary">
                      Total Synthesized Portfolio
                    </td>
                    <td className="py-2 px-3 text-right text-text-primary">100.00%</td>
                    <td className="py-2 px-3 text-right text-text-primary">
                      σ_p = {data.reconciliation.portfolioVolatilityPct?.toFixed(2)}%
                    </td>
                    <td className="py-2 px-3 text-right text-text-muted">—</td>
                    <td className="py-2 px-3 text-right text-accent">
                      {data.reconciliation.sumRiskContributionsPct?.toFixed(2)}% pts
                    </td>
                    <td className="py-2 px-3 text-right text-text-primary">
                      {data.reconciliation.sumPercentageContributions?.toFixed(2)}%
                    </td>
                    <td className="py-2 px-3 text-[10px] text-emerald-400">
                      ✓ Exact Euler Reconciled
                    </td>
                  </tr>
                </tfoot>
              )}
            </table>
          </div>
        )}
      </div>

      {/* SECTION C — PORTFOLIO EXPOSURE CONTEXT */}
      <div className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 className="text-[13px] font-semibold text-text-primary uppercase font-mono tracking-wider">
              Section C — Portfolio Look-Through Exposure Context
            </h3>
            <p className="font-mono text-[11px] text-text-tertiary">
              Direct underlying equity look-through weights. Exposure weights reflect capital allocation, not volatility contribution.
            </p>
          </div>

          {/* Sub-view navigation for exposure context */}
          <div className="flex rounded border border-border overflow-hidden text-[11px] font-mono">
            <button
              onClick={() => setExposureTab('sectors')}
              className={`px-3 py-1 font-semibold transition-all ${
                exposureTab === 'sectors' ? 'bg-accent text-surface-dark' : 'bg-surface text-text-secondary hover:text-text-primary'
              }`}
            >
              Sector Allocation ({data?.exposureContext?.sectors?.length || 0})
            </button>
            <button
              onClick={() => setExposureTab('marketCap')}
              className={`px-3 py-1 font-semibold transition-all border-l border-border ${
                exposureTab === 'marketCap' ? 'bg-accent text-surface-dark' : 'bg-surface text-text-secondary hover:text-text-primary'
              }`}
            >
              Market-Cap Tier
            </button>
            <button
              onClick={() => setExposureTab('securities')}
              className={`px-3 py-1 font-semibold transition-all border-l border-border ${
                exposureTab === 'securities' ? 'bg-accent text-surface-dark' : 'bg-surface text-text-secondary hover:text-text-primary'
              }`}
            >
              Top Securities ({data?.exposureContext?.topSecurities?.length || 0})
            </button>
          </div>
        </div>

        {/* Coverage & Zero-Renormalization Breakdown Bar */}
        {data?.exposureContext && (
          <div className="panel p-3.5 space-y-2 bg-surface">
            <div className="flex flex-wrap items-center justify-between text-[11px] font-mono">
              <span className="text-text-secondary">
                Disclosed Equity Coverage: <strong className="text-emerald-400">{data.exposureContext.disclosedEquityCoveragePct?.toFixed(1)}%</strong>
              </span>
              <span className="text-text-tertiary">
                Unclassified / Cash Reserves: <strong className="text-amber-400">{data.exposureContext.cashAndDebtWeightPct?.toFixed(1)}%</strong>
              </span>
            </div>
            <div className="h-2 w-full bg-surface-raised rounded-sm overflow-hidden flex">
              <div
                className="h-full bg-emerald-500"
                style={{ width: `${Math.min(100, data.exposureContext.disclosedEquityCoveragePct || 0)}%` }}
                title={`Disclosed Equity: ${data.exposureContext.disclosedEquityCoveragePct}%`}
              />
              <div
                className="h-full bg-amber-500/70"
                style={{ width: `${Math.min(100, data.exposureContext.cashAndDebtWeightPct || 0)}%` }}
                title={`Unclassified/Cash: ${data.exposureContext.cashAndDebtWeightPct}%`}
              />
            </div>
            <div className="text-[10px] text-text-tertiary font-mono italic">
              {data.exposureContext.exposureDisclaimers?.[1] ||
                'Zero-Renormalization Rule: Incomplete coverage is explicitly preserved and disclosed; never scaled to 100%.'}
            </div>
          </div>
        )}

        {/* Sub-View Content */}
        {data?.exposureContext ? (
          <div className="panel p-4">
            {exposureTab === 'sectors' && (
              <div className="space-y-3">
                <div className="overflow-x-auto rounded border border-border">
                  <table className="w-full text-left font-mono text-[11px]">
                    <thead className="bg-surface-raised text-text-muted text-[10px]">
                      <tr>
                        <th className="py-2 px-3">Sector</th>
                        <th className="py-2 px-3 text-right">Portfolio Weight</th>
                        <th className="py-2 px-3">Fund Contributions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border bg-surface">
                      {data.exposureContext.sectors.map((s) => (
                        <tr key={s.sectorName} className="hover:bg-surface-raised/40">
                          <td className="py-2 px-3 font-sans font-medium text-text-primary">
                            {s.sectorName}
                          </td>
                          <td className="py-2 px-3 text-right font-bold text-accent">
                            {s.portfolioWeightPct?.toFixed(2)}%
                          </td>
                          <td className="py-2 px-3 text-text-tertiary text-[10px]">
                            {s.fundContributions && Object.entries(s.fundContributions).map(([fName, w], i) => (
                              <span key={fName} className="mr-2">
                                {fName}: {Number(w).toFixed(1)}%{i < Object.keys(s.fundContributions).length - 1 ? ' | ' : ''}
                              </span>
                            ))}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {exposureTab === 'marketCap' && (
              <div className="space-y-3">
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                  {data.exposureContext.marketCaps.map((m) => (
                    <div key={m.category} className="metric-tile">
                      <span className="eyebrow">{m.category}</span>
                      <div className="metric-rule" />
                      <p className="data-value-lg text-text-primary">{m.portfolioWeightPct?.toFixed(1)}%</p>
                      <p className="mt-auto pt-1 font-mono text-[10px] text-text-tertiary">Look-Through Weight</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {exposureTab === 'securities' && (
              <div className="overflow-x-auto rounded border border-border">
                <table className="w-full text-left font-mono text-[11px]">
                  <thead className="bg-surface-raised text-text-muted text-[10px]">
                    <tr>
                      <th className="py-2 px-3">#</th>
                      <th className="py-2 px-3">Security Name</th>
                      <th className="py-2 px-3">ISIN</th>
                      <th className="py-2 px-3">Sector</th>
                      <th className="py-2 px-3">Cap Tier</th>
                      <th className="py-2 px-3 text-center">Funds</th>
                      <th className="py-2 px-3 text-right">Look-Through Weight</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border bg-surface">
                    {data.exposureContext.topSecurities.map((sec, idx) => (
                      <tr key={sec.isin || idx} className="hover:bg-surface-raised/40">
                        <td className="py-2 px-3 text-text-muted">{idx + 1}</td>
                        <td className="py-2 px-3 font-sans font-medium text-text-primary">{sec.securityName}</td>
                        <td className="py-2 px-3 text-text-tertiary">{sec.isin || 'N/A'}</td>
                        <td className="py-2 px-3 text-text-secondary">{sec.sector}</td>
                        <td className="py-2 px-3 text-text-tertiary">{sec.capCategory}</td>
                        <td className="py-2 px-3 text-center">
                          <span className={`px-1.5 py-0.5 rounded text-[9.5px] ${
                            sec.fundCount >= 2 ? 'bg-amber-500/20 text-amber-300 font-bold' : 'text-text-muted'
                          }`}>
                            {sec.fundCount}
                          </span>
                        </td>
                        <td className="py-2 px-3 text-right font-bold text-accent">
                          {sec.portfolioWeightPct?.toFixed(2)}%
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        ) : (
          <div className="panel p-4 font-mono text-[11px] text-text-tertiary text-center">
            Look-through exposure context unavailable.
          </div>
        )}
      </div>

      {/* SECTION D — RISK INTERPRETATION AND INVESTIGATION */}
      <div className="space-y-4">
        <h3 className="text-[13px] font-semibold text-text-primary uppercase font-mono tracking-wider">
          Section D — Risk Interpretation and Due-Diligence Investigation
        </h3>

        {data?.interpretation ? (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* Primary Drivers & Diversification Card */}
            <div className="panel p-5 space-y-4 bg-surface">
              <div>
                <span className="font-mono text-[10px] uppercase font-bold text-accent tracking-wider block">
                  Empirical Findings (Deterministic Rules)
                </span>
                <h4 className="text-[13px] font-semibold text-text-primary">
                  Volatility Drivers & Diversification Effects
                </h4>
              </div>

              {/* Primary Drivers */}
              <div className="space-y-1.5">
                <span className="font-mono text-[10px] uppercase text-text-tertiary font-bold">
                  Primary Volatility Contributors:
                </span>
                <ul className="list-disc list-inside space-y-1 font-mono text-[11px] text-text-secondary">
                  {data.interpretation.primaryRiskDrivers.map((d, idx) => (
                    <li key={idx} className="leading-relaxed">{d}</li>
                  ))}
                </ul>
              </div>

              {/* Diversification Insights */}
              <div className="space-y-1.5 pt-2 border-t border-border">
                <span className="font-mono text-[10px] uppercase text-text-tertiary font-bold">
                  Diversification & Correlation Dynamics:
                </span>
                <ul className="list-disc list-inside space-y-1 font-mono text-[11px] text-text-secondary">
                  {data.interpretation.diversificationInsights.map((d, idx) => (
                    <li key={idx} className="leading-relaxed">{d}</li>
                  ))}
                </ul>
              </div>

              {/* Concentration Assessment & Risk HHI */}
              <div className="p-3 rounded bg-surface-raised border border-border space-y-1 font-mono text-[11px]">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-text-primary">Risk Concentration Profile:</span>
                  <span className="text-accent font-bold">Risk HHI: {data.interpretation.riskHhi}</span>
                </div>
                <p className="text-text-tertiary text-[10.5px]">
                  {data.interpretation.concentrationAssessment}
                </p>
              </div>
            </div>

            {/* Due-Diligence Prompts & Limitations Card */}
            <div className="panel p-5 space-y-4 bg-surface">
              <div>
                <span className="font-mono text-[10px] uppercase font-bold text-indigo-400 tracking-wider block">
                  Actionable Due Diligence
                </span>
                <h4 className="text-[13px] font-semibold text-text-primary">
                  What The Allocator Should Investigate Next
                </h4>
              </div>

              {/* Investigation Prompts */}
              <div className="space-y-1.5">
                <span className="font-mono text-[10px] uppercase text-text-tertiary font-bold">
                  Recommended Investigative Questions:
                </span>
                <ul className="space-y-2 font-mono text-[11px] text-text-secondary">
                  {data.interpretation.investigationPrompts.map((p, idx) => (
                    <li key={idx} className="flex items-start gap-2 p-2 rounded bg-surface-raised/60 border border-border/50">
                      <span className="text-accent font-bold">Q{idx + 1}:</span>
                      <span className="leading-relaxed">{p}</span>
                    </li>
                  ))}
                </ul>
              </div>

              {/* Limitations */}
              <div className="space-y-1 pt-2 border-t border-border font-mono text-[10.5px] text-text-tertiary">
                <span className="font-bold uppercase text-[9.5px] block text-text-secondary">
                  Methodology & Data Limitations:
                </span>
                <ul className="list-disc list-inside space-y-0.5">
                  {data.interpretation.coverageLimitations.map((lim, idx) => (
                    <li key={idx}>{lim}</li>
                  ))}
                </ul>
              </div>

              {/* Zero-Advice Disclaimers */}
              <div className="p-2.5 rounded bg-amber-500/5 border border-amber-500/20 font-mono text-[10px] text-text-tertiary leading-relaxed">
                <strong>Epistemic Guardrail:</strong> Historical risk attribution is point-in-time descriptive evidence. It does not constitute investment advice, expected loss projections, or buy/sell recommendations.
              </div>
            </div>
          </div>
        ) : (
          <div className="panel p-4 font-mono text-[11px] text-text-tertiary text-center">
            Interpretation panel unavailable.
          </div>
        )}
      </div>
    </div>
  );
}
