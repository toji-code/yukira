'use client';

import React, { useEffect, useState } from 'react';
import {
  fetchActiveTaxLots,
  fetchPortfolioDrift,
  FifoLotRecord,
  PortfolioDriftReport,
} from '@/lib/api/research';
import { formatCurrency, formatPercentage } from '@/lib/utils/formatters';

interface Props {
  portfolioId?: number;
}

export function FifoTaxLotsAndDriftSection({ portfolioId = 1 }: Props) {
  const [lots, setLots] = useState<FifoLotRecord[]>([]);
  const [drift, setDrift] = useState<PortfolioDriftReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'lots' | 'drift'>('lots');

  useEffect(() => {
    let ignore = false;
    async function load() {
      try {
        setLoading(true);
        setError(null);
        const [lotData, driftData] = await Promise.all([
          fetchActiveTaxLots(portfolioId).catch(() => []),
          fetchPortfolioDrift(portfolioId).catch(() => null),
        ]);
        if (!ignore) {
          setLots(lotData);
          setDrift(driftData);
          setLoading(false);
        }
      } catch (err: unknown) {
        if (!ignore) {
          setError(err instanceof Error ? err.message : 'Failed to load FIFO lots and portfolio drift');
          setLoading(false);
        }
      }
    }
    load();
    return () => {
      ignore = true;
    };
  }, [portfolioId]);

  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-6 space-y-6">
      {/* Header & Epistemic Boundary Notice */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-4 border-b border-slate-800 gap-3">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="font-mono text-xs px-2 py-0.5 rounded bg-blue-950 text-blue-400 border border-blue-800 font-semibold">
              TASKS 245 &amp; 247
            </span>
            <span className="font-mono text-xs px-2 py-0.5 rounded bg-amber-950 text-amber-300 border border-amber-800">
              STRICT ACCOUNTING
            </span>
          </div>
          <h3 className="text-lg font-bold text-slate-100">
            FIFO Tax-Lot Accounting &amp; Target Allocation Drift
          </h3>
          <p className="mt-1 text-xs text-slate-400">
            Point-in-time tax-lot acquisition tracking, STCG/LTCG tax qualification per IT Act §112A, and target allocation rebalance directives.
          </p>
        </div>

        {/* Tab switch */}
        <div className="inline-flex rounded-lg bg-slate-950 p-1 border border-slate-800 self-start sm:self-auto">
          <button
            type="button"
            onClick={() => setActiveTab('lots')}
            className={`px-3 py-1.5 rounded-md text-xs font-medium font-mono transition-colors ${
              activeTab === 'lots'
                ? 'bg-blue-600 text-white shadow-xs'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Active Tax Lots ({lots.length})
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('drift')}
            className={`px-3 py-1.5 rounded-md text-xs font-medium font-mono transition-colors ${
              activeTab === 'drift'
                ? 'bg-blue-600 text-white shadow-xs'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Allocation Drift &amp; Rebalance
          </button>
        </div>
      </div>

      {/* Epistemic Alert Banner */}
      <div className="rounded-lg bg-blue-950/30 border border-blue-800/60 p-3.5 text-xs text-blue-200 space-y-1">
        <div className="font-semibold text-blue-300 flex items-center gap-1.5">
          <span>ℹ️</span> EPISTEMIC BOUNDARY: Static Portfolio Proxy vs Actual Investor XIRR
        </div>
        <p className="text-slate-300 text-[11px] leading-relaxed">
          Static weighted returns (averaging fund returns by current allocation) fail to account for cash-flow timing, staggered SIPs, and capital redemptions. YUKIRA enforces FIFO transaction-lot accounting to guarantee that money-weighted returns (XIRR) and capital gains tax liabilities are computed deterministically without synthetic approximation.
        </p>
      </div>

      {loading ? (
        <div className="p-8 text-center text-xs font-mono text-slate-400 animate-pulse">
          Computing FIFO tax lots and target allocation drift from immutable transaction ledger...
        </div>
      ) : error ? (
        <div className="p-4 bg-red-950/30 border border-red-800 rounded text-xs text-red-300 font-mono">
          {error}
        </div>
      ) : activeTab === 'lots' ? (
        lots.length === 0 ? (
          <div className="p-8 text-center text-slate-400 text-xs font-mono border border-dashed border-slate-800 rounded-lg">
            No active open tax lots recorded for this portfolio. Import an official CAMS/KFintech CAS statement to populate your immutable transaction ledger.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs font-mono border-collapse">
              <thead>
                <tr className="border-b border-slate-800 text-[11px] text-slate-400 uppercase bg-slate-950/50">
                  <th className="py-2.5 px-3">Acquisition Date</th>
                  <th className="py-2.5 px-3">Units</th>
                  <th className="py-2.5 px-3">Buy NAV</th>
                  <th className="py-2.5 px-3">Current NAV</th>
                  <th className="py-2.5 px-3">Cost Basis</th>
                  <th className="py-2.5 px-3">Current Value</th>
                  <th className="py-2.5 px-3">Holding Period</th>
                  <th className="py-2.5 px-3">Tax Status</th>
                  <th className="py-2.5 px-3 text-right">Unrealized Gain</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/50">
                {lots.map((lot) => {
                  const isGain = (lot.unrealizedGainLoss ?? 0) >= 0;
                  const isLtcg = lot.capitalGainType === 'LTCG' || lot.holdingPeriodDays >= 365;
                  return (
                    <tr key={lot.lotId} className="hover:bg-slate-800/30 transition-colors">
                      <td className="py-3 px-3 text-slate-200">
                        {lot.acquisitionDate}
                      </td>
                      <td className="py-3 px-3 text-slate-300">
                        {lot.remainingUnits.toFixed(4)}
                      </td>
                      <td className="py-3 px-3 text-slate-400">
                        ₹{lot.acquisitionNav.toFixed(4)}
                      </td>
                      <td className="py-3 px-3 text-slate-200">
                        ₹{lot.currentNav.toFixed(4)}
                      </td>
                      <td className="py-3 px-3 text-slate-300">
                        {formatCurrency(lot.totalCostBasis)}
                      </td>
                      <td className="py-3 px-3 font-semibold text-slate-100">
                        {formatCurrency(lot.currentMarketValue)}
                      </td>
                      <td className="py-3 px-3 text-slate-300">
                        {lot.holdingPeriodDays} days{' '}
                        <span className="text-[10px] text-slate-500">
                          ({(lot.holdingPeriodDays / 365.25).toFixed(1)}Y)
                        </span>
                      </td>
                      <td className="py-3 px-3">
                        <div className="flex flex-col gap-1 items-start">
                          <span
                            className={`inline-block px-2 py-0.5 rounded text-[10px] font-semibold border ${
                              lot.capitalGainType === 'DEEMED_STCG'
                                ? 'bg-purple-950 text-purple-300 border-purple-800'
                                : isLtcg
                                ? 'bg-emerald-950 text-emerald-400 border-emerald-800'
                                : 'bg-amber-950 text-amber-300 border-amber-800'
                            }`}
                          >
                            {lot.capitalGainType === 'DEEMED_STCG'
                              ? 'DEEMED STCG'
                              : isLtcg
                              ? 'LTCG (>1Y)'
                              : 'STCG (≤1Y)'}
                          </span>
                          {lot.taxSection && (
                            <span className="font-mono text-[9px] text-slate-400 px-1 py-0.2 rounded bg-slate-800/80 border border-slate-700">
                              {lot.taxSection}
                            </span>
                          )}
                        </div>
                      </td>
                      <td
                        className={`py-3 px-3 text-right font-semibold ${
                          isGain ? 'text-emerald-400' : 'text-red-400'
                        }`}
                      >
                        {isGain ? '+' : ''}
                        {formatCurrency(lot.unrealizedGainLoss)}
                        <span className="block text-[10px] opacity-80">
                          ({isGain ? '+' : ''}
                          {lot.unrealizedGainLossPercentage.toFixed(2)}%)
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            <div className="mt-3 p-3 rounded bg-slate-950/40 border border-slate-800/80 text-[10px] text-slate-400 leading-relaxed">
              <span className="font-semibold text-slate-300">STATUTORY TAX NOTICE (Section 112A / 111A / 50AA / 112):</span> Capital gains tax classifications and holding periods are evaluated algorithmically using SEBI-mandated category asset allocation rules and statutory acquisition date criteria (Finance Acts 2018–2024). Debt fund units acquired on or after 1 April 2023 are classified as Deemed STCG under Section 50AA without indexation. This summary is for information only and does NOT constitute tax advice. Investors must consult a qualified Chartered Accountant before preparing returns.
            </div>
          </div>
        )
      ) : (
        /* Drift & Rebalance View */
        (() => {
          const driftItems = drift?.driftItems || (drift as any)?.items || [];
          const toleranceBand = drift?.toleranceBandPercentage ?? (drift as any)?.alertThresholdPercentage ?? 5.0;
          const outOfBandCount = driftItems.filter((i: any) => i.isOutOfTolerance ?? i.triggersDriftAlert).length;
          const rebalanceNeeded = outOfBandCount > 0;

          if (driftItems.length === 0) {
            return (
              <div className="p-8 text-center text-slate-400 text-xs font-mono border border-dashed border-slate-800 rounded-lg">
                No target allocation model configured for this portfolio. Set target weights in portfolio settings to evaluate allocation drift.
              </div>
            );
          }

          return (
            <div className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800">
                  <span className="text-[10px] text-slate-400 uppercase font-mono block">Max Allowed Drift</span>
                  <span className="text-base font-bold font-mono text-slate-200">
                    {toleranceBand.toFixed(1)}%
                  </span>
                </div>
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800">
                  <span className="text-[10px] text-slate-400 uppercase font-mono block">Positions Out of Band</span>
                  <span className="text-base font-bold font-mono text-amber-400">
                    {outOfBandCount} of {driftItems.length}
                  </span>
                </div>
                <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800">
                  <span className="text-[10px] text-slate-400 uppercase font-mono block">Rebalance Status</span>
                  <span
                    className={`text-base font-bold font-mono ${
                      rebalanceNeeded ? 'text-amber-400' : 'text-emerald-400'
                    }`}
                  >
                    {rebalanceNeeded ? 'REBALANCE RECOMMENDED' : 'WITHIN TOLERANCE'}
                  </span>
                </div>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs font-mono border-collapse">
                  <thead>
                    <tr className="border-b border-slate-800 text-[11px] text-slate-400 uppercase bg-slate-950/50">
                      <th className="py-2.5 px-3">Instrument / Segment</th>
                      <th className="py-2.5 px-3">Current Value</th>
                      <th className="py-2.5 px-3">Actual Weight</th>
                      <th className="py-2.5 px-3">Target Weight</th>
                      <th className="py-2.5 px-3">Drift %</th>
                      <th className="py-2.5 px-3">Band Status</th>
                      <th className="py-2.5 px-3 text-right">Rebalance Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/50">
                    {driftItems.map((item: any, idx: number) => {
                      const segmentName = item.segmentName || item.schemeName || 'Unknown Position';
                      const curVal = item.currentValue ?? item.actualMarketValue ?? 0;
                      const actWt = item.actualWeightPercentage ?? 0;
                      const tgtWt = item.targetWeightPercentage ?? 0;
                      const driftPct = item.driftPercentage ?? 0;
                      const isOutOfTol = item.isOutOfTolerance ?? item.triggersDriftAlert ?? false;
                      const action = item.action ?? item.rebalanceAction ?? 'HOLD';
                      const rebalAmt = item.rebalanceAmount ?? item.rebalanceCashAmount;

                      return (
                        <tr key={idx} className="hover:bg-slate-800/30 transition-colors">
                          <td className="py-3 px-3 font-semibold text-slate-100">
                            {segmentName}
                          </td>
                          <td className="py-3 px-3 text-slate-300">
                            {formatCurrency(curVal)}
                          </td>
                          <td className="py-3 px-3 text-slate-200">
                            {actWt.toFixed(2)}%
                          </td>
                          <td className="py-3 px-3 text-slate-400">
                            {tgtWt.toFixed(2)}%
                          </td>
                          <td
                            className={`py-3 px-3 font-semibold ${
                              Math.abs(driftPct) > toleranceBand
                                ? 'text-amber-400'
                                : 'text-slate-300'
                            }`}
                          >
                            {driftPct >= 0 ? '+' : ''}
                            {driftPct.toFixed(2)}%
                          </td>
                          <td className="py-3 px-3">
                            <span
                              className={`inline-block px-2 py-0.5 rounded text-[10px] font-semibold border ${
                                isOutOfTol
                                  ? 'bg-amber-950 text-amber-300 border-amber-800'
                                  : 'bg-emerald-950 text-emerald-400 border-emerald-800'
                              }`}
                            >
                              {isOutOfTol ? 'OUT OF BAND' : 'BALANCED'}
                            </span>
                          </td>
                          <td className="py-3 px-3 text-right">
                            <span
                              className={`font-semibold ${
                                action === 'TRIM' || action === 'SELL'
                                  ? 'text-rose-400'
                                  : action === 'ADD' || action === 'BUY'
                                  ? 'text-emerald-400'
                                  : 'text-slate-400'
                              }`}
                            >
                              {action}{' '}
                              {rebalAmt && action !== 'HOLD'
                                ? `(${formatCurrency(Math.abs(rebalAmt))})`
                                : ''}
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          );
        })()
      )}
    </div>
  );
}

