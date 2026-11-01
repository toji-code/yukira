'use client';

import React, { useState } from 'react';
import { RollingReturnReport } from '@/lib/api/research';

interface Props {
  data: RollingReturnReport | null;
  selectedHorizon: string;
  onHorizonChange: (horizon: string) => void;
  isLoading?: boolean;
}

export function RollingReturnAnalysisView({
  data,
  selectedHorizon,
  onHorizonChange,
  isLoading,
}: Props) {
  const horizons = ['1Y', '3Y', '5Y'];

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="font-mono text-xs px-2 py-0.5 rounded bg-blue-950 text-blue-400 border border-blue-800 font-semibold">
              RET-03
            </span>
            <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300">
              Candidate Methodology
            </span>
          </div>
          <h3 className="text-lg font-bold text-slate-100">Rolling Return Distribution & Consistency</h3>
          <p className="text-xs text-slate-400">
            Annualized rolling window returns across shifting market regimes.
          </p>
        </div>

        {/* Horizon selector buttons */}
        <div className="flex rounded-lg border border-slate-800 bg-slate-950 p-1">
          {horizons.map((h) => (
            <button
              key={h}
              onClick={() => onHorizonChange(h)}
              className={`px-3 py-1 rounded text-xs font-semibold transition-colors ${
                selectedHorizon === h
                  ? 'bg-blue-600 text-white'
                  : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800'
              }`}
            >
              {h} Rolling
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="p-8 text-center text-slate-400 animate-pulse">
          Computing rolling returns across empirical observation path...
        </div>
      ) : !data || data.totalRollingPeriods === 0 ? (
        <div className="p-8 text-center text-slate-400">
          Insufficient historical observations for {selectedHorizon} rolling calculation.
        </div>
      ) : (
        <>
          {/* Key Distribution Cards */}
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-3">
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-800">
              <span className="text-[11px] text-slate-400 block">Minimum</span>
              <span className="text-lg font-mono font-bold text-rose-400">
                {data.minReturnPercentage.toFixed(2)}%
              </span>
            </div>
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-800">
              <span className="text-[11px] text-slate-400 block">25th Percentile</span>
              <span className="text-lg font-mono font-bold text-slate-300">
                {data.p25ReturnPercentage.toFixed(2)}%
              </span>
            </div>
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-800">
              <span className="text-[11px] text-slate-400 block font-semibold text-blue-400">Median</span>
              <span className="text-lg font-mono font-bold text-blue-400">
                {data.medianReturnPercentage.toFixed(2)}%
              </span>
            </div>
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-800">
              <span className="text-[11px] text-slate-400 block">75th Percentile</span>
              <span className="text-lg font-mono font-bold text-slate-300">
                {data.p75ReturnPercentage.toFixed(2)}%
              </span>
            </div>
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-800">
              <span className="text-[11px] text-slate-400 block">Maximum</span>
              <span className="text-lg font-mono font-bold text-emerald-400">
                {data.maxReturnPercentage.toFixed(2)}%
              </span>
            </div>
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-800">
              <span className="text-[11px] text-slate-400 block">Mean ± StdDev</span>
              <span className="text-xs font-mono font-bold text-slate-200">
                {data.meanReturnPercentage.toFixed(1)}% ± {data.standardDeviationPercentage.toFixed(1)}%
              </span>
            </div>
            <div className="bg-slate-950 p-3 rounded-lg border border-slate-800">
              <span className="text-[11px] text-slate-400 block">Positive Periods</span>
              <span className="text-lg font-mono font-bold text-emerald-400">
                {data.positivePeriodsPercentage.toFixed(1)}%
              </span>
            </div>
          </div>

          {/* Sampled History Timeline */}
          <div>
            <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
              Sampled Rolling Periods ({data.totalRollingPeriods} evaluated periods)
            </h4>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs font-mono">
                <thead className="bg-slate-950 text-slate-400 font-semibold border-b border-slate-800">
                  <tr>
                    <th className="py-2 px-3">Window Start</th>
                    <th className="py-2 px-3">Window End</th>
                    <th className="py-2 px-3 text-right">Annualized Return</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-slate-300">
                  {data.sampledPoints.slice(0, 10).map((pt, i) => (
                    <tr key={i} className="hover:bg-slate-800/30">
                      <td className="py-2 px-3">{pt.startDate}</td>
                      <td className="py-2 px-3">{pt.endDate}</td>
                      <td
                        className={`py-2 px-3 text-right font-bold ${
                          pt.annualizedReturnPercentage >= 0 ? 'text-emerald-400' : 'text-rose-400'
                        }`}
                      >
                        {pt.annualizedReturnPercentage > 0 ? '+' : ''}
                        {pt.annualizedReturnPercentage.toFixed(2)}%
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

