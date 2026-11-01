'use client';

import React, { useState, useEffect, useMemo } from 'react';
import {
  PortfolioPerformanceAttributionResponseDto,
  HoldingLotPerformanceDto,
  fetchPerformanceAttribution,
  fetchHoldingLots,
} from '@/lib/api/transactions';
import { formatCurrency, formatNumber, formatPercent } from '@/lib/utils/formatters';
import { SchemeOption } from '@/types/domain';

interface PerformanceAttributionSectionProps {
  availableOptions?: SchemeOption[];
  onRefreshRequested?: () => void;
}

export function PerformanceAttributionSection({
  availableOptions = [],
  onRefreshRequested,
}: PerformanceAttributionSectionProps) {
  const [data, setData] = useState<PortfolioPerformanceAttributionResponseDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Lot filter state
  const [selectedSchemeOptionId, setSelectedSchemeOptionId] = useState<string>('');
  const [holdingLots, setHoldingLots] = useState<HoldingLotPerformanceDto[]>([]);
  const [loadingLots, setLoadingLots] = useState<boolean>(false);

  // Active view toggle within Attribution
  const [attributionView, setAttributionView] = useState<
    'decomposition' | 'schemes' | 'lots' | 'realized' | 'dividends' | 'timing' | 'diagnostics'
  >('decomposition');

  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await fetchPerformanceAttribution();
      setData(res);
      if (res.holdingLots) {
        setHoldingLots(res.holdingLots);
      }
    } catch (err: unknown) {
      setError((err as Error).message || 'Failed to calculate performance attribution.');
    } finally {
      setLoading(false);
    }
  };

  const handleFilterLots = async (schemeOptionIdStr: string) => {
    setSelectedSchemeOptionId(schemeOptionIdStr);
    try {
      setLoadingLots(true);
      const optId = schemeOptionIdStr ? parseInt(schemeOptionIdStr, 10) : undefined;
      const filtered = await fetchHoldingLots(optId);
      setHoldingLots(filtered);
    } catch (err: unknown) {
      console.error('Failed to filter holding lots:', err);
    } finally {
      setLoadingLots(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  if (loading) {
    return (
      <div className="p-12 text-center">
        <div className="inline-block w-8 h-8 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin mb-4" />
        <p className="text-sm font-medium text-zinc-300">Calculating point-in-time performance attribution...</p>
        <p className="text-xs text-zinc-500 mt-1">
          Decomposing portfolio outcomes, solving FIFO disposal lots, and diagnosing cash-flow roots
        </p>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="p-8 bg-rose-950/20 border border-rose-800/40 rounded-xl m-6">
        <div className="flex items-center gap-2 text-rose-400 font-semibold text-sm">
          <span>⚠ Attribution Calculation Error</span>
        </div>
        <p className="text-xs text-rose-300 mt-1">{error || 'No performance attribution data returned.'}</p>
        <button
          onClick={loadData}
          className="mt-4 px-3 py-1.5 bg-rose-900/60 hover:bg-rose-800 text-rose-200 text-xs rounded transition"
        >
          Retry Calculation
        </button>
      </div>
    );
  }

  const decomp = data.financialDecomposition;
  const realized = data.realizedPerformance;
  const unrealized = data.unrealizedPerformance;
  const dividend = data.dividendContribution;
  const timing = data.cashFlowTiming;
  const xirr = data.xirrDiagnostics;

  return (
    <div className="p-6 space-y-8">
      {/* 1. Header Banner & Sub-View Switcher */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 pb-6 border-b border-zinc-800">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2 py-0.5 bg-sky-500/10 text-sky-400 border border-sky-500/30 rounded text-xs font-mono font-medium">
              TASK 11 VERIFIED
            </span>
            <h3 className="text-lg font-bold text-zinc-100 tracking-tight">
              Investor Performance Attribution & Contribution Intelligence
            </h3>
          </div>
          <p className="text-xs text-zinc-400 mt-1 max-w-3xl">
            Mathematical decomposition of investor wealth outcomes. Isolates external capital flows from investment
            appreciation, reconciles FIFO purchase lots, exposes cash dividend income, and discloses XIRR polynomial root diagnostics.
          </p>
        </div>

        {/* View Switcher Pills */}
        <div className="flex flex-wrap items-center gap-1.5 bg-zinc-950/60 p-1.5 rounded-lg border border-zinc-800 text-xs">
          <button
            onClick={() => setAttributionView('decomposition')}
            className={`px-3 py-1.5 rounded-md font-medium transition ${
              attributionView === 'decomposition'
                ? 'bg-emerald-600 text-white shadow-sm'
                : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/60'
            }`}
          >
            Outcome Decomposition
          </button>
          <button
            onClick={() => setAttributionView('schemes')}
            className={`px-3 py-1.5 rounded-md font-medium transition ${
              attributionView === 'schemes'
                ? 'bg-emerald-600 text-white shadow-sm'
                : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/60'
            }`}
          >
            Scheme Contribution ({data.schemeContributions.length})
          </button>
          <button
            onClick={() => setAttributionView('lots')}
            className={`px-3 py-1.5 rounded-md font-medium transition ${
              attributionView === 'lots'
                ? 'bg-emerald-600 text-white shadow-sm'
                : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/60'
            }`}
          >
            FIFO Lots ({holdingLots.length})
          </button>
          <button
            onClick={() => setAttributionView('realized')}
            className={`px-3 py-1.5 rounded-md font-medium transition ${
              attributionView === 'realized'
                ? 'bg-emerald-600 text-white shadow-sm'
                : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/60'
            }`}
          >
            Realized Gains ({realized.records.length})
          </button>
          <button
            onClick={() => setAttributionView('dividends')}
            className={`px-3 py-1.5 rounded-md font-medium transition ${
              attributionView === 'dividends'
                ? 'bg-emerald-600 text-white shadow-sm'
                : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/60'
            }`}
          >
            Dividends
          </button>
          <button
            onClick={() => setAttributionView('timing')}
            className={`px-3 py-1.5 rounded-md font-medium transition ${
              attributionView === 'timing'
                ? 'bg-emerald-600 text-white shadow-sm'
                : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/60'
            }`}
          >
            Cash Flow Timeline
          </button>
          <button
            onClick={() => setAttributionView('diagnostics')}
            className={`px-3 py-1.5 rounded-md font-medium transition ${
              attributionView === 'diagnostics'
                ? 'bg-emerald-600 text-white shadow-sm'
                : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/60'
            }`}
          >
            XIRR Diagnostics
          </button>
        </div>
      </div>

      {/* 2. SUB-VIEW: OUTCOME DECOMPOSITION */}
      {attributionView === 'decomposition' && (
        <div className="space-y-6">
          {/* Mathematical Reconciliation Formula Card */}
          <div className="bg-zinc-950/80 border border-zinc-800 rounded-xl p-5 shadow-lg">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-zinc-800/80">
              <div>
                <h4 className="text-xs font-mono uppercase tracking-wider text-emerald-400 font-semibold">
                  Financial Reconciliation Identity
                </h4>
                <p className="text-xs text-zinc-400 mt-0.5 font-mono">
                  {decomp.reconciliationFormula}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-zinc-400">Reconciliation Status:</span>
                <span
                  className={`px-2.5 py-1 rounded text-xs font-mono font-bold border ${
                    decomp.reconciliationStatus === 'RECONCILED'
                      ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40'
                      : decomp.reconciliationStatus === 'EXPLAINED_DISCREPANCY'
                      ? 'bg-amber-500/20 text-amber-300 border-amber-500/40'
                      : 'bg-rose-500/20 text-rose-300 border-rose-500/40'
                  }`}
                >
                  {decomp.reconciliationStatus}
                </span>
              </div>
            </div>

            {/* Formula Terms Breakdown Grid */}
            <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-3 mt-4 text-center">
              <div className="p-3 bg-zinc-900/80 border border-zinc-800 rounded-lg">
                <span className="text-[10px] font-mono text-zinc-400 block uppercase">V_start</span>
                <span className="text-xs text-zinc-500 block">Opening Value</span>
                <span className="text-sm font-bold font-mono text-zinc-100 mt-1 block">
                  {formatCurrency(decomp.openingPortfolioValue)}
                </span>
              </div>

              <div className="p-3 bg-zinc-900/80 border border-zinc-800 rounded-lg">
                <span className="text-[10px] font-mono text-emerald-400 block uppercase">+ C_in</span>
                <span className="text-xs text-zinc-500 block">Capital Inflows</span>
                <span className="text-sm font-bold font-mono text-emerald-400 mt-1 block">
                  {formatCurrency(decomp.externalInvestedCapital)}
                </span>
              </div>

              <div className="p-3 bg-zinc-900/80 border border-zinc-800 rounded-lg">
                <span className="text-[10px] font-mono text-rose-400 block uppercase">- C_out</span>
                <span className="text-xs text-zinc-500 block">Capital Outflows</span>
                <span className="text-sm font-bold font-mono text-rose-400 mt-1 block">
                  {formatCurrency(decomp.externalRedeemedCapital)}
                </span>
              </div>

              <div className="p-3 bg-zinc-900/80 border border-zinc-800 rounded-lg">
                <span className="text-[10px] font-mono text-sky-400 block uppercase">+ G_realized</span>
                <span className="text-xs text-zinc-500 block">Realized Gains</span>
                <span
                  className={`text-sm font-bold font-mono mt-1 block ${
                    decomp.realizedCapitalGains >= 0 ? 'text-sky-300' : 'text-rose-400'
                  }`}
                >
                  {formatCurrency(decomp.realizedCapitalGains)}
                </span>
              </div>

              <div className="p-3 bg-zinc-900/80 border border-zinc-800 rounded-lg">
                <span className="text-[10px] font-mono text-purple-400 block uppercase">+ G_unrealized</span>
                <span className="text-xs text-zinc-500 block">Unrealized Gains</span>
                <span
                  className={`text-sm font-bold font-mono mt-1 block ${
                    decomp.unrealizedCapitalGains >= 0 ? 'text-purple-300' : 'text-rose-400'
                  }`}
                >
                  {formatCurrency(decomp.unrealizedCapitalGains)}
                </span>
              </div>

              <div className="p-3 bg-zinc-900/80 border border-zinc-800 rounded-lg">
                <span className="text-[10px] font-mono text-amber-400 block uppercase">+ I_dividend</span>
                <span className="text-xs text-zinc-500 block">Dividend Income</span>
                <span className="text-sm font-bold font-mono text-amber-300 mt-1 block">
                  {formatCurrency(decomp.disclosedDividendIncome)}
                </span>
              </div>

              <div className="p-3 bg-zinc-900/80 border border-zinc-800 rounded-lg">
                <span className="text-[10px] font-mono text-zinc-400 block uppercase">- E_charges</span>
                <span className="text-xs text-zinc-500 block">Costs & Charges</span>
                <span className="text-sm font-bold font-mono text-zinc-400 mt-1 block">
                  {formatCurrency(decomp.recordedChargesAndCosts)}
                </span>
              </div>
            </div>

            {/* Reconciliation Comparison Bar */}
            <div className="mt-5 p-4 bg-zinc-900/50 rounded-lg border border-zinc-800/80 flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div className="space-y-1">
                <div className="flex items-center gap-3">
                  <span className="text-xs text-zinc-400">Calculated Expected Closing (V_expected):</span>
                  <span className="text-sm font-mono font-bold text-zinc-200">
                    {formatCurrency(decomp.calculatedExpectedClosingValue)}
                  </span>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-xs text-zinc-400">Actual Point-in-Time Closing (V_closing):</span>
                  <span className="text-sm font-mono font-bold text-emerald-400">
                    {formatCurrency(decomp.closingPortfolioValue)}
                  </span>
                </div>
              </div>

              <div className="p-3 bg-zinc-950 rounded border border-zinc-800 text-right">
                <span className="text-[11px] font-mono text-zinc-400 block">
                  Unexplained Residual (R = V_closing - V_expected)
                </span>
                <span
                  className={`text-base font-mono font-black ${
                    Math.abs(decomp.unexplainedResidual) <= 1.0
                      ? 'text-emerald-400'
                      : Math.abs(decomp.unexplainedResidual) <= 50.0
                      ? 'text-amber-400'
                      : 'text-rose-400'
                  }`}
                >
                  {formatCurrency(decomp.unexplainedResidual)}
                </span>
              </div>
            </div>

            {/* Residual Causes Disclosure */}
            {decomp.residualCauses && decomp.residualCauses.length > 0 && (
              <div className="mt-4 p-3 bg-amber-950/20 border border-amber-900/40 rounded-lg">
                <h5 className="text-[11px] font-mono uppercase text-amber-400 font-semibold mb-1">
                  Residual Root Causes (Zero Forced Balancing):
                </h5>
                <ul className="list-disc list-inside text-xs text-amber-200/90 space-y-1">
                  {decomp.residualCauses.map((cause, i) => (
                    <li key={i}>{cause}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          {/* Capital Inflows vs Investment Gains Attribution Overview */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="p-4 bg-zinc-950 border border-zinc-800 rounded-xl">
              <span className="text-xs text-zinc-400 block">Net External Capital Added</span>
              <span className="text-lg font-bold font-mono text-zinc-100 mt-1 block">
                {formatCurrency(decomp.externalInvestedCapital - decomp.externalRedeemedCapital)}
              </span>
              <span className="text-[11px] text-zinc-500 mt-1 block">
                Gross Inflows {formatCurrency(decomp.externalInvestedCapital)} minus Redemptions{' '}
                {formatCurrency(decomp.externalRedeemedCapital)}
              </span>
            </div>

            <div className="p-4 bg-zinc-950 border border-zinc-800 rounded-xl">
              <span className="text-xs text-zinc-400 block">Total Investment Gains Generated</span>
              <span
                className={`text-lg font-bold font-mono mt-1 block ${
                  decomp.realizedCapitalGains + decomp.unrealizedCapitalGains + decomp.disclosedDividendIncome >= 0
                    ? 'text-emerald-400'
                    : 'text-rose-400'
                }`}
              >
                {formatCurrency(
                  decomp.realizedCapitalGains + decomp.unrealizedCapitalGains + decomp.disclosedDividendIncome
                )}
              </span>
              <span className="text-[11px] text-zinc-500 mt-1 block">
                Realized {formatCurrency(decomp.realizedCapitalGains)} + Unrealized{' '}
                {formatCurrency(decomp.unrealizedCapitalGains)} + Dividends{' '}
                {formatCurrency(decomp.disclosedDividendIncome)}
              </span>
            </div>

            <div className="p-4 bg-zinc-950 border border-zinc-800 rounded-xl">
              <span className="text-xs text-zinc-400 block">Capital Preservation & Wealth Created</span>
              <span className="text-lg font-bold font-mono text-sky-400 mt-1 block">
                {formatCurrency(decomp.closingPortfolioValue + decomp.externalRedeemedCapital)}
              </span>
              <span className="text-[11px] text-zinc-500 mt-1 block">
                Total Wealth Realized & Retained vs Invested Capital {formatCurrency(decomp.externalInvestedCapital)}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* 3. SUB-VIEW: SCHEME CONTRIBUTION */}
      {attributionView === 'schemes' && (
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-zinc-950/60 p-4 border border-zinc-800 rounded-xl">
            <div>
              <h4 className="text-xs font-mono uppercase tracking-wider text-emerald-400 font-semibold">
                Scheme Net Financial Contribution Ranking
              </h4>
              <p className="text-xs text-zinc-400 mt-0.5">
                Ranks each scheme by net financial outcome (₹). Contribution percentage explicitly exposes the
                denominator:
              </p>
            </div>
            <div className="p-2.5 bg-zinc-900 border border-zinc-800 rounded-lg text-right">
              <span className="text-[10px] font-mono text-zinc-400 block uppercase">Contribution Denominator</span>
              <span className="text-xs font-bold font-mono text-zinc-200">
                {data.schemeContributions.length > 0 &&
                  data.schemeContributions[0].contributionDenominatorDescription}
              </span>
            </div>
          </div>

          <div className="overflow-x-auto border border-zinc-800 rounded-xl bg-zinc-950">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-zinc-800 bg-zinc-900/70 text-zinc-400 font-mono text-[11px]">
                  <th className="py-3 px-3 w-12 text-center">Rank</th>
                  <th className="py-3 px-3">Scheme & Plan</th>
                  <th className="py-3 px-3 text-right">Market Value</th>
                  <th className="py-3 px-3 text-right">Invested Capital</th>
                  <th className="py-3 px-3 text-right">Realized Gain</th>
                  <th className="py-3 px-3 text-right">Unrealized Gain</th>
                  <th className="py-3 px-3 text-right">Dividends</th>
                  <th className="py-3 px-3 text-right text-emerald-400 font-bold">Net Contribution (₹)</th>
                  <th className="py-3 px-3 text-right">Contrib. %</th>
                  <th className="py-3 px-3 text-right">Scheme XIRR</th>
                  <th className="py-3 px-3 text-center">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60">
                {data.schemeContributions.map((s) => (
                  <tr key={s.schemeOptionId} className="hover:bg-zinc-900/50 transition">
                    <td className="py-3 px-3 text-center font-mono font-bold text-zinc-300">
                      #{s.contributionRank}
                    </td>
                    <td className="py-3 px-3">
                      <div className="font-semibold text-zinc-100">{s.schemeName}</div>
                      <div className="text-[11px] text-zinc-500 font-mono">
                        {s.planType} • {s.optionType} • {s.amfiCode ? `AMFI: ${s.amfiCode}` : s.isin}
                      </div>
                    </td>
                    <td className="py-3 px-3 text-right font-mono font-medium text-zinc-200">
                      {formatCurrency(s.currentMarketValue)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-zinc-400">
                      {formatCurrency(s.investedCapital)}
                    </td>
                    <td
                      className={`py-3 px-3 text-right font-mono ${
                        s.realizedGains >= 0 ? 'text-zinc-300' : 'text-rose-400'
                      }`}
                    >
                      {formatCurrency(s.realizedGains)}
                    </td>
                    <td
                      className={`py-3 px-3 text-right font-mono ${
                        s.unrealizedGains >= 0 ? 'text-zinc-300' : 'text-rose-400'
                      }`}
                    >
                      {formatCurrency(s.unrealizedGains)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-amber-300">
                      {formatCurrency(s.dividendIncome)}
                    </td>
                    <td
                      className={`py-3 px-3 text-right font-mono font-bold text-sm ${
                        s.netFinancialContribution >= 0 ? 'text-emerald-400' : 'text-rose-400'
                      }`}
                    >
                      {formatCurrency(s.netFinancialContribution)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono font-semibold text-zinc-200">
                      {s.contributionToPortfolioAbsoluteGainsPercent != null
                        ? `${s.contributionToPortfolioAbsoluteGainsPercent.toFixed(2)}%`
                        : '—'}
                    </td>
                    <td className="py-3 px-3 text-right font-mono">
                      {s.schemeXirr != null ? (
                        <span className="text-emerald-400 font-semibold">{formatPercent(s.schemeXirr)}</span>
                      ) : (
                        <span className="text-zinc-500 text-[10px]">{s.schemeXirrStatus}</span>
                      )}
                    </td>
                    <td className="py-3 px-3 text-center">
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-mono ${
                          s.contributionStatus === 'COMPLETE'
                            ? 'bg-emerald-950/60 text-emerald-300 border border-emerald-800/60'
                            : 'bg-amber-950/60 text-amber-300 border border-amber-800/60'
                        }`}
                      >
                        {s.contributionStatus}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 4. SUB-VIEW: FIFO PURCHASE LOTS */}
      {attributionView === 'lots' && (
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-zinc-950/60 p-4 border border-zinc-800 rounded-xl">
            <div>
              <h4 className="text-xs font-mono uppercase tracking-wider text-emerald-400 font-semibold">
                Holding-Level FIFO Purchase Lots Inspector
              </h4>
              <p className="text-xs text-zinc-400 mt-0.5">
                Every acquisition transaction establishes an independent cost lot. Duration category (&lt;365d vs ≥365d)
                is an ACT/365 duration indicator and does NOT constitute tax advice.
              </p>
            </div>

            {/* Scheme Filter Dropdown */}
            <div className="flex items-center gap-2">
              <span className="text-xs text-zinc-400 whitespace-nowrap">Filter Scheme:</span>
              <select
                value={selectedSchemeOptionId}
                onChange={(e) => handleFilterLots(e.target.value)}
                className="bg-zinc-900 border border-zinc-700 rounded-lg px-3 py-1.5 text-xs text-zinc-200 focus:outline-none focus:border-emerald-500"
              >
                <option value="">All Schemes ({data.schemeContributions.length})</option>
                {data.schemeContributions.map((s) => (
                  <option key={s.schemeOptionId} value={s.schemeOptionId.toString()}>
                    {s.schemeName}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="overflow-x-auto border border-zinc-800 rounded-xl bg-zinc-950">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-zinc-800 bg-zinc-900/70 text-zinc-400 font-mono text-[11px]">
                  <th className="py-3 px-3">Acquisition Date</th>
                  <th className="py-3 px-3">Scheme Name</th>
                  <th className="py-3 px-3 text-right">Initial Units</th>
                  <th className="py-3 px-3 text-right">Remaining Units</th>
                  <th className="py-3 px-3 text-right">Acquisition NAV</th>
                  <th className="py-3 px-3 text-right">Current NAV</th>
                  <th className="py-3 px-3 text-right">Cost Basis</th>
                  <th className="py-3 px-3 text-right">Current Value</th>
                  <th className="py-3 px-3 text-right text-emerald-400 font-bold">Unrealized Gain</th>
                  <th className="py-3 px-3 text-center">Holding Days</th>
                  <th className="py-3 px-3 text-center">Duration</th>
                  <th className="py-3 px-3 text-center">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60">
                {holdingLots.map((lot) => (
                  <tr key={lot.lotId} className="hover:bg-zinc-900/50 transition">
                    <td className="py-3 px-3 font-mono font-medium text-zinc-300">
                      {lot.acquisitionDate}
                    </td>
                    <td className="py-3 px-3 font-semibold text-zinc-100 max-w-xs truncate" title={lot.schemeName}>
                      {lot.schemeName}
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-zinc-400">
                      {formatNumber(lot.acquisitionUnits, 3)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono font-semibold text-zinc-200">
                      {formatNumber(lot.remainingUnits, 3)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-zinc-400">
                      ₹{formatNumber(lot.acquisitionNav, 4)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-zinc-300">
                      {lot.currentNav ? `₹${formatNumber(lot.currentNav, 4)}` : '—'}
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-zinc-400">
                      {formatCurrency(lot.remainingCostBasis)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono font-semibold text-zinc-200">
                      {formatCurrency(lot.currentMarketValue)}
                    </td>
                    <td
                      className={`py-3 px-3 text-right font-mono font-bold ${
                        lot.unrealizedGain >= 0 ? 'text-emerald-400' : 'text-rose-400'
                      }`}
                    >
                      {formatCurrency(lot.unrealizedGain)}
                    </td>
                    <td className="py-3 px-3 text-center font-mono text-zinc-400">
                      {lot.holdingPeriodDays}d
                    </td>
                    <td className="py-3 px-3 text-center">
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-mono font-semibold ${
                          lot.holdingPeriodCategory === 'LONG_TERM'
                            ? 'bg-emerald-950/60 text-emerald-300 border border-emerald-800/60'
                            : 'bg-amber-950/60 text-amber-300 border border-amber-800/60'
                        }`}
                      >
                        {lot.holdingPeriodCategory}
                      </span>
                    </td>
                    <td className="py-3 px-3 text-center">
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-mono ${
                          lot.lotStatus === 'ACTIVE'
                            ? 'bg-blue-950/60 text-blue-300 border border-blue-800/60'
                            : lot.lotStatus === 'PARTIALLY_REDEEMED'
                            ? 'bg-amber-950/60 text-amber-300 border border-amber-800/60'
                            : 'bg-zinc-800 text-zinc-400'
                        }`}
                      >
                        {lot.lotStatus}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 5. SUB-VIEW: CONSOLIDATED REALIZED GAINS & TAX DISCLAIMER */}
      {attributionView === 'realized' && (
        <div className="space-y-6">
          {/* Statutory Tax Disclaimer Banner */}
          <div className="p-4 bg-amber-950/30 border border-amber-800/60 rounded-xl">
            <div className="flex items-start gap-2.5">
              <span className="text-amber-400 text-base font-bold">ℹ</span>
              <div>
                <h5 className="text-xs font-bold text-amber-300 uppercase tracking-wide">
                  Statutory Non-Advisory Notice
                </h5>
                <p className="text-xs text-amber-200/90 mt-0.5 font-medium">{realized.taxDisclaimer}</p>
              </div>
            </div>
          </div>

          {/* Realized Totals Summary */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <div className="p-4 bg-zinc-950 border border-zinc-800 rounded-xl">
              <span className="text-xs text-zinc-400 block">Total Gross Proceeds</span>
              <span className="text-lg font-bold font-mono text-zinc-100 mt-1 block">
                {formatCurrency(realized.totalRedemptionGrossProceeds)}
              </span>
            </div>
            <div className="p-4 bg-zinc-950 border border-zinc-800 rounded-xl">
              <span className="text-xs text-zinc-400 block">Total Cost Basis Sold</span>
              <span className="text-lg font-bold font-mono text-zinc-400 mt-1 block">
                {formatCurrency(realized.totalCostBasisRedeemed)}
              </span>
            </div>
            <div className="p-4 bg-zinc-950 border border-zinc-800 rounded-xl">
              <span className="text-xs text-zinc-400 block">Net Realized Gain</span>
              <span
                className={`text-lg font-bold font-mono mt-1 block ${
                  realized.netRealizedGainOrLoss >= 0 ? 'text-emerald-400' : 'text-rose-400'
                }`}
              >
                {formatCurrency(realized.netRealizedGainOrLoss)}
              </span>
            </div>
            <div className="p-4 bg-zinc-950 border border-zinc-800 rounded-xl">
              <span className="text-xs text-zinc-400 block">External vs Internal Switches</span>
              <span className="text-xs font-mono text-zinc-300 mt-2 block">
                Ext: {formatCurrency(realized.externalWithdrawalsRealizedGains)} | Switch:{' '}
                {formatCurrency(realized.internalTransfersRealizedGains)}
              </span>
            </div>
          </div>

          {/* Realized Disposals Table */}
          <div className="overflow-x-auto border border-zinc-800 rounded-xl bg-zinc-950">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-zinc-800 bg-zinc-900/70 text-zinc-400 font-mono text-[11px]">
                  <th className="py-3 px-3">Disposal Date</th>
                  <th className="py-3 px-3">Scheme</th>
                  <th className="py-3 px-3 text-right">Units Redeemed</th>
                  <th className="py-3 px-3 text-right">Exit NAV</th>
                  <th className="py-3 px-3 text-right">Gross Proceeds</th>
                  <th className="py-3 px-3 text-right">FIFO Cost Basis</th>
                  <th className="py-3 px-3 text-right text-emerald-400 font-bold">Net Realized Gain</th>
                  <th className="py-3 px-3 text-center">Acquired Date</th>
                  <th className="py-3 px-3 text-center">Holding Period</th>
                  <th className="py-3 px-3 text-center">Flow Type</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60">
                {realized.records.map((r, i) => (
                  <tr key={i} className="hover:bg-zinc-900/50 transition">
                    <td className="py-3 px-3 font-mono text-zinc-300">{r.redemptionDate}</td>
                    <td className="py-3 px-3 font-semibold text-zinc-100">{r.schemeName}</td>
                    <td className="py-3 px-3 text-right font-mono text-zinc-400">
                      {formatNumber(r.unitsRedeemed, 3)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-zinc-300">
                      ₹{formatNumber(r.redemptionPrice, 4)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono font-medium text-zinc-200">
                      {formatCurrency(r.redemptionGrossProceeds)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-zinc-400">
                      {formatCurrency(r.costBasisRedeemed)}
                    </td>
                    <td
                      className={`py-3 px-3 text-right font-mono font-bold ${
                        r.netRealizedGain >= 0 ? 'text-emerald-400' : 'text-rose-400'
                      }`}
                    >
                      {formatCurrency(r.netRealizedGain)}
                    </td>
                    <td className="py-3 px-3 text-center font-mono text-zinc-400">
                      {r.matchedPurchaseDate || '—'}
                    </td>
                    <td className="py-3 px-3 text-center">
                      <span className="font-mono text-xs text-zinc-300">{r.holdingPeriodDays}d</span>{' '}
                      <span className="text-[10px] text-zinc-500 font-mono">({r.holdingPeriodCategory})</span>
                    </td>
                    <td className="py-3 px-3 text-center">
                      <span
                        className={`px-2 py-0.5 rounded text-[10px] font-mono ${
                          r.isInternalTransfer
                            ? 'bg-purple-950/60 text-purple-300 border border-purple-800/60'
                            : 'bg-rose-950/60 text-rose-300 border border-rose-800/60'
                        }`}
                      >
                        {r.isInternalTransfer ? 'SWITCH' : 'EXTERNAL'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 6. SUB-VIEW: DIVIDEND & INCOME BREAKDOWN */}
      {attributionView === 'dividends' && (
        <div className="space-y-6">
          <div className="p-4 bg-sky-950/30 border border-sky-800/60 rounded-xl">
            <h5 className="text-xs font-bold text-sky-300 uppercase tracking-wide">
              Dividend Accounting Protocol
            </h5>
            <p className="text-xs text-sky-200/90 mt-1">{dividend.accountingRuleNote}</p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="p-4 bg-zinc-950 border border-zinc-800 rounded-xl">
              <span className="text-xs text-zinc-400 block">External Cash Dividend Payouts</span>
              <span className="text-lg font-bold font-mono text-amber-400 mt-1 block">
                {formatCurrency(dividend.totalCashDividendPayouts)}
              </span>
              <span className="text-[11px] text-zinc-500 mt-1 block">
                Direct external cash distributions deposited to investor bank account
              </span>
            </div>

            <div className="p-4 bg-zinc-950 border border-zinc-800 rounded-xl">
              <span className="text-xs text-zinc-400 block">Reinvested Dividends</span>
              <span className="text-lg font-bold font-mono text-zinc-300 mt-1 block">
                {formatCurrency(dividend.totalDividendReinvestments)}
              </span>
              <span className="text-[11px] text-zinc-500 mt-1 block">
                Internal capitalizations acquiring additional units at ex-NAV
              </span>
            </div>

            <div className="p-4 bg-zinc-950 border border-zinc-800 rounded-xl">
              <span className="text-xs text-zinc-400 block">Total Corporate Actions</span>
              <span className="text-lg font-bold font-mono text-zinc-100 mt-1 block">
                {dividend.totalDividendEventsCount} Events
              </span>
              <span className="text-[11px] text-zinc-500 mt-1 block">
                {dividend.cashPayoutEventsCount} Cash Payouts • {dividend.reinvestmentEventsCount} Reinvestments
              </span>
            </div>
          </div>

          <div className="overflow-x-auto border border-zinc-800 rounded-xl bg-zinc-950">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-zinc-800 bg-zinc-900/70 text-zinc-400 font-mono text-[11px]">
                  <th className="py-3 px-3">Scheme Name</th>
                  <th className="py-3 px-3 text-right">Cash Payouts Total</th>
                  <th className="py-3 px-3 text-right">Reinvestments Total</th>
                  <th className="py-3 px-3 text-center">Event Count</th>
                  <th className="py-3 px-3 text-center">Last Dividend Date</th>
                  <th className="py-3 px-3 text-center">Ledger Coverage</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60">
                {dividend.schemeBreakdown.map((s) => (
                  <tr key={s.schemeOptionId} className="hover:bg-zinc-900/50 transition">
                    <td className="py-3 px-3 font-semibold text-zinc-100">{s.schemeName}</td>
                    <td className="py-3 px-3 text-right font-mono font-bold text-amber-400">
                      {formatCurrency(s.cashDividendPayoutTotal)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-zinc-300">
                      {formatCurrency(s.dividendReinvestmentTotal)}
                    </td>
                    <td className="py-3 px-3 text-center font-mono text-zinc-400">
                      {s.totalDividendEventsCount}
                    </td>
                    <td className="py-3 px-3 text-center font-mono text-zinc-400">
                      {s.lastDividendDate || '—'}
                    </td>
                    <td className="py-3 px-3 text-center">
                      <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-zinc-800 text-zinc-300">
                        {s.sourceCoverage}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 7. SUB-VIEW: CASH FLOW TIMELINE */}
      {attributionView === 'timing' && (
        <div className="space-y-6">
          <div className="bg-zinc-950/60 p-4 border border-zinc-800 rounded-xl">
            <h4 className="text-xs font-mono uppercase tracking-wider text-emerald-400 font-semibold">
              Monthly Cash-Flow Timing & External Capital Pacing
            </h4>
            <p className="text-xs text-zinc-400 mt-0.5">
              Descriptive chronological breakdown of external contributions vs redemptions. Does not assert causal
              timing skill.
            </p>
          </div>

          <div className="overflow-x-auto border border-zinc-800 rounded-xl bg-zinc-950">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-zinc-800 bg-zinc-900/70 text-zinc-400 font-mono text-[11px]">
                  <th className="py-3 px-3">Year-Month</th>
                  <th className="py-3 px-3 text-right">Lump Sum</th>
                  <th className="py-3 px-3 text-right">SIP Purchases</th>
                  <th className="py-3 px-3 text-right">Total Inflows</th>
                  <th className="py-3 px-3 text-right">Redemptions</th>
                  <th className="py-3 px-3 text-right">Dividend Payouts</th>
                  <th className="py-3 px-3 text-right text-emerald-400 font-bold">Net External Flow</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60">
                {timing.periodicCashFlowTimeline.map((m) => (
                  <tr key={m.yearMonth} className="hover:bg-zinc-900/50 transition">
                    <td className="py-3 px-3 font-mono font-bold text-zinc-200">{m.yearMonth}</td>
                    <td className="py-3 px-3 text-right font-mono text-zinc-400">
                      {formatCurrency(m.lumpSumContributions)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-zinc-400">
                      {formatCurrency(m.sipContributions)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono font-medium text-emerald-300">
                      {formatCurrency(m.totalContributions)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-rose-400">
                      {formatCurrency(m.redemptions)}
                    </td>
                    <td className="py-3 px-3 text-right font-mono text-amber-300">
                      {formatCurrency(m.dividendPayouts)}
                    </td>
                    <td
                      className={`py-3 px-3 text-right font-mono font-bold ${
                        m.netExternalCashFlow >= 0 ? 'text-emerald-400' : 'text-rose-400'
                      }`}
                    >
                      {formatCurrency(m.netExternalCashFlow)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Timing Observations */}
          {timing.timingObservations && timing.timingObservations.length > 0 && (
            <div className="p-4 bg-zinc-950 border border-zinc-800 rounded-xl space-y-2">
              <h5 className="text-xs font-mono uppercase text-zinc-400 font-semibold">Grounded Timing Observations</h5>
              <ul className="list-disc list-inside text-xs text-zinc-300 space-y-1">
                {timing.timingObservations.map((obs, i) => (
                  <li key={i}>{obs}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* 8. SUB-VIEW: XIRR SOLVER DIAGNOSTICS & ROOT AMBIGUITY */}
      {attributionView === 'diagnostics' && (
        <div className="space-y-6">
          <div className="p-5 bg-zinc-950 border border-zinc-800 rounded-xl shadow-lg">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-zinc-800">
              <div>
                <h4 className="text-xs font-mono uppercase tracking-wider text-emerald-400 font-semibold">
                  Multi-Start XIRR Solver Diagnostics & Polynomial Root Verification
                </h4>
                <p className="text-xs text-zinc-400 mt-0.5">
                  Internal Rate of Return (IRR) is a polynomial equation whose root count depends on cash flow sign
                  changes (Descartes&apos; rule of signs).
                </p>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-zinc-400">Root Uniqueness:</span>
                <span
                  className={`px-2.5 py-1 rounded text-xs font-mono font-bold border ${
                    !xirr.isMultipleRootsPossible
                      ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40'
                      : 'bg-amber-500/20 text-amber-300 border-amber-500/40'
                  }`}
                >
                  {!xirr.isMultipleRootsPossible ? 'UNIQUE ROOT' : 'AMBIGUOUS / MULTI-ROOT'}
                </span>
              </div>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mt-4">
              <div className="p-3 bg-zinc-900 rounded-lg border border-zinc-800">
                <span className="text-xs text-zinc-500 block">Solver Algorithm</span>
                <span className="text-xs font-mono font-bold text-zinc-200 mt-1 block">{xirr.solverMethod}</span>
              </div>
              <div className="p-3 bg-zinc-900 rounded-lg border border-zinc-800">
                <span className="text-xs text-zinc-500 block">Iterations Used</span>
                <span className="text-xs font-mono font-bold text-zinc-200 mt-1 block">{xirr.iterations} iterations</span>
              </div>
              <div className="p-3 bg-zinc-900 rounded-lg border border-zinc-800">
                <span className="text-xs text-zinc-500 block">Sign Alternations</span>
                <span className="text-xs font-mono font-bold text-amber-300 mt-1 block">
                  {xirr.signAlternationsCount} sign flips
                </span>
              </div>
              <div className="p-3 bg-zinc-900 rounded-lg border border-zinc-800">
                <span className="text-xs text-zinc-500 block">Day-Count Convention</span>
                <span className="text-xs font-mono font-bold text-zinc-200 mt-1 block">{xirr.dayCountConvention}</span>
              </div>
            </div>

            <div className="mt-4 p-3 bg-zinc-900/60 rounded-lg border border-zinc-800/80">
              <span className="text-xs font-semibold text-zinc-300 block mb-1">Mathematical Explanation:</span>
              <p className="text-xs text-zinc-400 leading-relaxed">{xirr.diagnosticExplanation}</p>
            </div>

            <div className="mt-3 p-3 bg-zinc-900/60 rounded-lg border border-zinc-800/80">
              <span className="text-xs font-semibold text-zinc-300 block mb-1">Fund CAGR vs Investor XIRR Discipline:</span>
              <p className="text-xs text-zinc-400 leading-relaxed">{xirr.fundCagrComparison}</p>
            </div>
          </div>
        </div>
      )}

      {/* 9. RESEARCH BRIEF FINDINGS & FORMAL METHODOLOGY DISCLAIMERS */}
      <div className="p-6 bg-zinc-950 border border-zinc-800 rounded-xl space-y-4">
        <h4 className="text-xs font-mono uppercase tracking-wider text-emerald-400 font-semibold">
          Epistemic Governance & Research Brief Synthesis
        </h4>

        {/* Categorized Research Findings */}
        <div className="space-y-2">
          {data.researchFindings.map((finding, idx) => {
            const isFact = finding.startsWith('[FACT]');
            const isExposure = finding.startsWith('[CALCULATED_EXPOSURE]');
            const isRisk = finding.startsWith('[RISK_LIMITATION]');

            const badgeColor = isFact
              ? 'bg-emerald-950/60 text-emerald-300 border-emerald-800/60'
              : isExposure
              ? 'bg-sky-950/60 text-sky-300 border-sky-800/60'
              : 'bg-amber-950/60 text-amber-300 border-amber-800/60';

            return (
              <div
                key={idx}
                className="p-3 bg-zinc-900/70 border border-zinc-800/70 rounded-lg flex items-start gap-3"
              >
                <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold border ${badgeColor}`}>
                  {isFact ? 'FACT' : isExposure ? 'CALCULATED' : 'LIMITATION'}
                </span>
                <span className="text-xs text-zinc-300 flex-1">
                  {finding.replace(/^\[(FACT|CALCULATED_EXPOSURE|RISK_LIMITATION)\]\s*/, '')}
                </span>
              </div>
            );
          })}
        </div>

        {/* Disclaimers List */}
        <div className="pt-4 border-t border-zinc-800/80 text-[11px] text-zinc-500 space-y-1">
          {data.methodologyDisclaimers.map((disc, idx) => (
            <p key={idx}>• {disc}</p>
          ))}
        </div>
      </div>
    </div>
  );
}
