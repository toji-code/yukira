'use client';

import React from 'react';
import { PairwiseHoldingsOverlap } from '@/lib/api/research';

interface Props {
  overlap: PairwiseHoldingsOverlap | null;
  isOpen: boolean;
  onClose: () => void;
  isLoading?: boolean;
}

export function PairwiseHoldingsOverlapModal({ overlap, isOpen, onClose, isLoading }: Props) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-xs p-4">
      <div className="relative w-full max-w-2xl bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="p-6 border-b border-slate-800 flex items-start justify-between">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="font-mono text-xs px-2 py-0.5 rounded bg-blue-950 text-blue-400 border border-blue-800 font-semibold">
                PAIRWISE OVERLAP
              </span>
              <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300">
                Minimum Weight Algorithm
              </span>
            </div>
            <h2 className="text-xl font-bold text-slate-100">Holdings Overlap & Active Share</h2>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-100 p-1.5 rounded-lg hover:bg-slate-800 transition-colors"
          >
            ✕
          </button>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto space-y-6 text-sm">
          {isLoading ? (
            <div className="p-12 text-center text-slate-400 animate-pulse">
              Computing security-level overlap matrix between selected funds...
            </div>
          ) : !overlap ? (
            <div className="p-12 text-center text-slate-400">
              Unable to compute overlap: Insufficient monthly holdings disclosures.
            </div>
          ) : (
            <>
              {/* Date Mismatch Warning */}
              {overlap.hasReportingDateMismatch && (
                <div className="p-3 bg-amber-950/40 border border-amber-800/60 rounded-lg text-amber-300 text-xs flex items-center gap-2">
                  <span>⚠️</span>
                  <span>
                    <strong>Reporting Date Discrepancy:</strong> Fund A disclosed as of {overlap.portfolioDateA},
                    while Fund B disclosed as of {overlap.portfolioDateB}. Overlap is evaluated across the closest
                    available disclosures.
                  </span>
                </div>
              )}

              {/* Fund Names */}
              <div className="grid grid-cols-2 gap-4 bg-slate-950 p-4 rounded-xl border border-slate-800">
                <div>
                  <span className="text-xs text-slate-400 block font-semibold uppercase">Fund A</span>
                  <span className="font-bold text-slate-200">{overlap.schemeNameA}</span>
                  <span className="text-xs text-slate-500 block mt-0.5">As of {overlap.portfolioDateA}</span>
                </div>
                <div>
                  <span className="text-xs text-slate-400 block font-semibold uppercase">Fund B</span>
                  <span className="font-bold text-slate-200">{overlap.schemeNameB}</span>
                  <span className="text-xs text-slate-500 block mt-0.5">As of {overlap.portfolioDateB}</span>
                </div>
              </div>

              {/* Metrics Summary */}
              <div className="grid grid-cols-3 gap-4">
                <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 text-center">
                  <span className="text-xs text-slate-400 block uppercase font-semibold">Total Overlap</span>
                  <span className="text-2xl font-mono font-bold text-blue-400 mt-1 block">
                    {overlap.totalOverlapPercentage.toFixed(1)}%
                  </span>
                  <span className="text-[11px] text-slate-500 block mt-0.5">Shared Allocation</span>
                </div>
                <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 text-center">
                  <span className="text-xs text-slate-400 block uppercase font-semibold">Active Share</span>
                  <span className="text-2xl font-mono font-bold text-emerald-400 mt-1 block">
                    {overlap.activeSharePercentage.toFixed(1)}%
                  </span>
                  <span className="text-[11px] text-slate-500 block mt-0.5">Differentiated Weight</span>
                </div>
                <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 text-center">
                  <span className="text-xs text-slate-400 block uppercase font-semibold">Shared Securities</span>
                  <span className="text-2xl font-mono font-bold text-purple-400 mt-1 block">
                    {overlap.sharedSecuritiesCount}
                  </span>
                  <span className="text-[11px] text-slate-500 block mt-0.5">Common Stocks</span>
                </div>
              </div>

              {/* Overlapping Holdings Table */}
              <div>
                <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
                  Top Shared Securities & Overlap Contribution
                </h4>
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs font-mono">
                    <thead className="bg-slate-950 text-slate-400 font-semibold border-b border-slate-800">
                      <tr>
                        <th className="py-2.5 px-3">Security</th>
                        <th className="py-2.5 px-3">Sector</th>
                        <th className="py-2.5 px-3 text-right">Fund A Wt</th>
                        <th className="py-2.5 px-3 text-right">Fund B Wt</th>
                        <th className="py-2.5 px-3 text-right">Overlap Contrib</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60 text-slate-300">
                      {overlap.topOverlappingPositions.map((p, idx) => (
                        <tr key={idx} className="hover:bg-slate-800/30">
                          <td className="py-2 px-3 font-sans font-medium text-slate-200">{p.securityName}</td>
                          <td className="py-2 px-3 font-sans text-slate-400">{p.sector}</td>
                          <td className="py-2 px-3 text-right">{p.weightInFundA.toFixed(2)}%</td>
                          <td className="py-2 px-3 text-right">{p.weightInFundB.toFixed(2)}%</td>
                          <td className="py-2 px-3 text-right font-bold text-blue-400">
                            {p.overlapContribution.toFixed(2)}%
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="text-[11px] text-slate-500 pt-2 border-t border-slate-800 flex justify-between">
                <span>Deterministic math: sum of min(weight_A, weight_B).</span>
                <span>Zero fabricated holding estimates.</span>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

