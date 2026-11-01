'use client';

import React from 'react';
import { DrawdownEpisodeReport } from '@/lib/api/research';

interface Props {
  data: DrawdownEpisodeReport | null;
  isLoading?: boolean;
}

export function DrawdownEpisodeView({ data, isLoading }: Props) {
  if (isLoading) {
    return (
      <div className="p-6 bg-slate-900 border border-slate-800 rounded-xl text-center text-slate-400 animate-pulse">
        Calculating historical drawdown episodes from empirical daily NAV observations...
      </div>
    );
  }

  if (!data || data.totalObservations === 0) {
    return (
      <div className="p-6 bg-slate-900 border border-slate-800 rounded-xl text-center text-slate-400">
        No historical drawdown observations available for this evaluation window.
      </div>
    );
  }

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="font-mono text-xs px-2 py-0.5 rounded bg-amber-950 text-amber-400 border border-amber-800 font-semibold">
              RSK-03 & RSK-04
            </span>
            <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300">
              Candidate Methodology
            </span>
          </div>
          <h3 className="text-lg font-bold text-slate-100">Historical Drawdown Episodes & Recovery</h3>
          <p className="text-xs text-slate-400">
            Observation Window: {data.observationStartDate} to {data.observationEndDate} ({data.totalObservations} trading days)
          </p>
        </div>

        <div className="flex gap-4 text-right">
          <div className="bg-slate-950 px-4 py-2 rounded-lg border border-slate-800">
            <span className="text-xs text-slate-400 block">Maximum Drawdown</span>
            <span className="text-xl font-mono font-bold text-rose-400">
              {data.maxDrawdownPercentage.toFixed(2)}%
            </span>
          </div>
          {data.currentDrawdownPercentage != null && (
            <div className="bg-slate-950 px-4 py-2 rounded-lg border border-slate-800">
              <span className="text-xs text-slate-400 block">Current Drawdown</span>
              <span
                className={`text-xl font-mono font-bold ${
                  data.currentDrawdownPercentage < -0.1 ? 'text-amber-400' : 'text-emerald-400'
                }`}
              >
                {data.currentDrawdownPercentage.toFixed(2)}%
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Episodes Table */}
      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs">
          <thead className="bg-slate-950 text-slate-400 uppercase font-semibold border-b border-slate-800">
            <tr>
              <th className="py-2.5 px-3">#</th>
              <th className="py-2.5 px-3">Peak Date & NAV</th>
              <th className="py-2.5 px-3">Trough Date & NAV</th>
              <th className="py-2.5 px-3">Drawdown Depth</th>
              <th className="py-2.5 px-3">Days to Trough</th>
              <th className="py-2.5 px-3">Recovery Date</th>
              <th className="py-2.5 px-3">Days to Recovery</th>
              <th className="py-2.5 px-3">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60 font-mono text-slate-300">
            {data.topEpisodes.map((ep) => (
              <tr key={ep.rank} className="hover:bg-slate-800/40 transition-colors">
                <td className="py-2.5 px-3 text-slate-500 font-bold">{ep.rank}</td>
                <td className="py-2.5 px-3">
                  <div>{ep.peakDate}</div>
                  <div className="text-[11px] text-slate-400">₹{ep.peakNav?.toFixed(4)}</div>
                </td>
                <td className="py-2.5 px-3">
                  <div>{ep.troughDate}</div>
                  <div className="text-[11px] text-slate-400">₹{ep.troughNav?.toFixed(4)}</div>
                </td>
                <td className="py-2.5 px-3 font-bold text-rose-400">
                  {ep.drawdownDepthPercentage.toFixed(2)}%
                </td>
                <td className="py-2.5 px-3">{ep.daysToTrough} days</td>
                <td className="py-2.5 px-3">{ep.recoveryDate || '—'}</td>
                <td className="py-2.5 px-3">{ep.daysToRecovery ? `${ep.daysToRecovery} days` : '—'}</td>
                <td className="py-2.5 px-3 font-sans">
                  {ep.isRecovered ? (
                    <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-950 text-emerald-400 border border-emerald-800">
                      RECOVERED
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-rose-950 text-rose-400 border border-rose-800">
                      ONGOING
                    </span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="text-[11px] text-slate-500 pt-2 border-t border-slate-800/60 flex items-center justify-between">
        <span>Deterministic calculation from daily empirical NAVs without smoothing.</span>
        <span className="font-mono">Formula: (Trough_NAV - Peak_NAV) / Peak_NAV</span>
      </div>
    </div>
  );
}

