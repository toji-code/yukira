'use client';

import React, { useState, useEffect } from 'react';

interface PerformancePeriodItem {
  periodKey: string;
  periodLabel: string;
  targetStartDate: string;
  targetEndDate: string;
  effectiveStartDate: string | null;
  effectiveEndDate: string | null;
  startNav: number | null;
  endNav: number | null;
  simpleReturn: number | null;
  annualizedReturn: number | null;
  benchmarkReturn: number | null;
  excessReturn: number | null;
  tradingDaysCount: number;
  startNavLookbackDays: number;
  endNavLookbackDays: number;
  eligibilityStatus: string;
  statusReason: string;
}

interface PerformancePeriodsResponse {
  schemeOptionId: number;
  schemeName: string;
  benchmarkCode: string;
  asOfDate: string;
  knowledgeCutoffTime: string;
  periods: PerformancePeriodItem[];
  governanceNotice: string;
}

interface Props {
  schemeOptionId: number;
  asOfDate?: string;
  knowledgeCutoffTime?: string;
  onPeriodSelect?: (periodKey: string) => void;
}

export function PerformancePeriodSelector({
  schemeOptionId,
  asOfDate,
  knowledgeCutoffTime,
  onPeriodSelect,
}: Props) {
  const [data, setData] = useState<PerformancePeriodsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedKey, setSelectedKey] = useState<string>('3Y');

  useEffect(() => {
    let isMounted = true;
    async function fetchPeriods() {
      setLoading(true);
      setError(null);
      try {
        const params = new URLSearchParams();
        if (asOfDate) params.set('asOfDate', asOfDate);
        if (knowledgeCutoffTime) params.set('knowledgeCutoffTime', knowledgeCutoffTime);

        const url = `/api/v1/analysis/performance-periods/${schemeOptionId}${params.toString() ? '?' + params.toString() : ''}`;
        const res = await fetch(url);
        if (!res.ok) {
          throw new Error(`Failed to load performance periods: HTTP ${res.status}`);
        }
        const json = await res.json();
        if (isMounted) {
          setData(json);
          // If default 3Y is not in list or not eligible, select first eligible
          if (json.periods && json.periods.length > 0) {
            const has3Y = json.periods.some((p: PerformancePeriodItem) => p.periodKey === '3Y' && p.eligibilityStatus === 'ELIGIBLE');
            if (!has3Y) {
              const firstEligible = json.periods.find((p: PerformancePeriodItem) => p.eligibilityStatus === 'ELIGIBLE');
              if (firstEligible) setSelectedKey(firstEligible.periodKey);
            }
          }
        }
      } catch (err: any) {
        if (isMounted) {
          setError(err.message || 'Failed to load point-in-time performance periods.');
        }
      } finally {
        if (isMounted) setLoading(false);
      }
    }

    if (schemeOptionId) {
      fetchPeriods();
    }
    return () => {
      isMounted = false;
    };
  }, [schemeOptionId, asOfDate, knowledgeCutoffTime]);

  const handleSelect = (key: string) => {
    setSelectedKey(key);
    if (onPeriodSelect) onPeriodSelect(key);
  };

  const selectedPeriod = data?.periods?.find((p) => p.periodKey === selectedKey);

  const formatPct = (val: number | null | undefined) => {
    if (val === null || val === undefined) return 'N/A';
    const pct = val * 100;
    const sign = pct > 0 ? '+' : '';
    return `${sign}${pct.toFixed(2)}%`;
  };

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-lg p-5 text-slate-100 shadow-md">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2 mb-4">
        <div>
          <h3 className="text-base font-semibold text-slate-200">
            Point-in-Time Performance Periods (Task 132 & 134)
          </h3>
          <p className="text-xs text-slate-400">
            Deterministic boundary calculations via 4-day lookback window. Zero look-ahead bias.
          </p>
        </div>
        <span className="text-[11px] font-mono bg-slate-800 px-2 py-1 rounded text-slate-400 border border-slate-700">
          Cutoff: {asOfDate || 'Latest Authoritative'}
        </span>
      </div>

      {loading && (
        <div className="animate-pulse space-y-3 py-4">
          <div className="h-9 bg-slate-800 rounded w-full"></div>
          <div className="h-20 bg-slate-850 rounded w-full"></div>
        </div>
      )}

      {error && (
        <div className="p-3 bg-rose-950/40 border border-rose-800/60 rounded text-rose-300 text-xs">
          {error}
        </div>
      )}

      {data && data.periods && (
        <div>
          {/* Horizon Selection Buttons */}
          <div className="flex flex-wrap gap-2 mb-4">
            {data.periods.map((p) => {
              const isSelected = p.periodKey === selectedKey;
              const isEligible = p.eligibilityStatus === 'ELIGIBLE';
              return (
                <button
                  key={p.periodKey}
                  onClick={() => handleSelect(p.periodKey)}
                  className={`px-3 py-1.5 rounded text-xs font-medium transition-colors flex items-center gap-1.5 border ${
                    isSelected
                      ? 'bg-blue-600 border-blue-500 text-white shadow-sm'
                      : isEligible
                      ? 'bg-slate-800 hover:bg-slate-750 border-slate-700 text-slate-300'
                      : 'bg-slate-900/60 border-slate-800 text-slate-500 cursor-not-allowed opacity-70'
                  }`}
                >
                  <span>{p.periodLabel}</span>
                  {!isEligible && (
                    <span className="text-[10px] px-1 bg-amber-950/60 text-amber-400 rounded border border-amber-800/40">
                      Limited
                    </span>
                  )}
                </button>
              );
            })}
          </div>

          {/* Selected Horizon Metrics Card */}
          {selectedPeriod && (
            <div className="bg-slate-950/70 border border-slate-800 rounded-lg p-4 space-y-3">
              <div className="flex flex-wrap justify-between items-center gap-2 border-b border-slate-800/80 pb-2.5">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-semibold text-slate-100">
                    {selectedPeriod.periodLabel} Profile
                  </span>
                  <span
                    className={`text-[10px] uppercase font-bold px-1.5 py-0.5 rounded border ${
                      selectedPeriod.eligibilityStatus === 'ELIGIBLE'
                        ? 'bg-emerald-950/60 text-emerald-400 border-emerald-800/60'
                        : selectedPeriod.eligibilityStatus === 'INSUFFICIENT_HISTORY'
                        ? 'bg-amber-950/60 text-amber-400 border-amber-800/60'
                        : 'bg-rose-950/60 text-rose-400 border-rose-800/60'
                    }`}
                  >
                    {selectedPeriod.eligibilityStatus}
                  </span>
                </div>
                <div className="text-[11px] text-slate-400">
                  Target Window:{' '}
                  <span className="font-mono text-slate-300">{selectedPeriod.targetStartDate}</span> to{' '}
                  <span className="font-mono text-slate-300">{selectedPeriod.targetEndDate}</span>
                </div>
              </div>

              {selectedPeriod.eligibilityStatus === 'ELIGIBLE' ? (
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
                  <div className="bg-slate-900 p-2.5 rounded border border-slate-800">
                    <div className="text-[11px] text-slate-400">Simple Return (RET-02)</div>
                    <div
                      className={`text-base font-bold font-mono mt-0.5 ${
                        (selectedPeriod.simpleReturn ?? 0) >= 0 ? 'text-emerald-400' : 'text-rose-400'
                      }`}
                    >
                      {formatPct(selectedPeriod.simpleReturn)}
                    </div>
                    <div className="text-[10px] text-emerald-500/80 mt-0.5">Validated Baseline</div>
                  </div>

                  <div className="bg-slate-900 p-2.5 rounded border border-slate-800">
                    <div className="text-[11px] text-slate-400">CAGR Annualized (RET-03)</div>
                    <div
                      className={`text-base font-bold font-mono mt-0.5 ${
                        (selectedPeriod.annualizedReturn ?? 0) >= 0 ? 'text-blue-400' : 'text-rose-400'
                      }`}
                    >
                      {formatPct(selectedPeriod.annualizedReturn)}
                    </div>
                    <div className="text-[10px] text-amber-500/80 mt-0.5">Candidate Metric</div>
                  </div>

                  <div className="bg-slate-900 p-2.5 rounded border border-slate-800">
                    <div className="text-[11px] text-slate-400">Boundary NAVs</div>
                    <div className="text-xs font-mono text-slate-200 mt-1">
                      {selectedPeriod.startNav?.toFixed(2) ?? '—'} &rarr;{' '}
                      {selectedPeriod.endNav?.toFixed(2) ?? '—'}
                    </div>
                    <div className="text-[10px] text-slate-500 mt-0.5">
                      {selectedPeriod.tradingDaysCount} Trading Days
                    </div>
                  </div>

                  <div className="bg-slate-900 p-2.5 rounded border border-slate-800">
                    <div className="text-[11px] text-slate-400">Effective Dates</div>
                    <div className="text-[11px] font-mono text-slate-300 mt-1">
                      {selectedPeriod.effectiveStartDate} &rarr; {selectedPeriod.effectiveEndDate}
                    </div>
                    <div className="text-[10px] text-slate-500 mt-0.5">
                      Lookback: {selectedPeriod.startNavLookbackDays}d / {selectedPeriod.endNavLookbackDays}d
                    </div>
                  </div>
                </div>
              ) : (
                <div className="p-3 bg-amber-950/20 border border-amber-800/40 rounded text-amber-300/90 text-xs">
                  {selectedPeriod.statusReason}
                </div>
              )}

              {/* Epistemic Governance Disclaimer */}
              <div className="text-[11px] text-slate-500 bg-slate-900/40 p-2 rounded border border-slate-800/60 flex items-start gap-1.5">
                <span className="text-amber-400 font-bold">&#9432;</span>
                <span>
                  {data.governanceNotice ||
                    'RET-02 (Simple Period Return) is the only approved operational baseline. RET-03 is candidate methodology.'}
                </span>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
