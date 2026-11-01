'use client';

import React from 'react';
import { FundHoldingsConstruction } from '@/lib/api/research';

interface Props {
  data: FundHoldingsConstruction | null;
  isLoading?: boolean;
}

export function HoldingsConstructionSection({ data, isLoading }: Props) {
  if (isLoading) {
    return (
      <div className="p-8 bg-slate-900 border border-slate-800 rounded-xl text-center text-slate-400 animate-pulse">
        Loading official monthly portfolio holdings disclosure...
      </div>
    );
  }

  if (!data || data.totalHoldingsCount === 0) {
    return (
      <div className="p-8 bg-slate-900 border border-slate-800 rounded-xl text-center text-slate-400">
        No official monthly holdings disclosure published for this instrument.
      </div>
    );
  }

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="font-mono text-xs px-2 py-0.5 rounded bg-purple-950 text-purple-400 border border-purple-800 font-semibold">
              PORTFOLIO CONSTRUCTION
            </span>
            <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300">
              Disclosure Date: {data.portfolioDate}
            </span>
            {data.disclosureLagDays != null && (
              <span className="text-xs px-2 py-0.5 rounded bg-slate-800/80 text-amber-300 border border-amber-800/50">
                Lag: {data.disclosureLagDays}d (SEBI 10d mandate)
              </span>
            )}
          </div>
          <h3 className="text-lg font-bold text-slate-100">Holdings, Concentration & Asset Allocation</h3>
          <p className="text-xs text-slate-400">
            {data.totalHoldingsCount} disclosed securities | Denominator: {data.concentrationDenominator}
          </p>
        </div>

        {/* Concentration Badges */}
        <div className="flex gap-3">
          <div className="bg-slate-950 px-3 py-2 rounded-lg border border-slate-800 text-center">
            <span className="text-[10px] text-slate-400 block uppercase font-semibold">Top 5</span>
            <span className="text-base font-mono font-bold text-blue-400">{data.top5ConcentrationPercentage}%</span>
          </div>
          <div className="bg-slate-950 px-3 py-2 rounded-lg border border-slate-800 text-center">
            <span className="text-[10px] text-slate-400 block uppercase font-semibold">Top 10</span>
            <span className="text-base font-mono font-bold text-blue-400">{data.top10ConcentrationPercentage}%</span>
          </div>
          <div className="bg-slate-950 px-3 py-2 rounded-lg border border-slate-800 text-center">
            <span className="text-[10px] text-slate-400 block uppercase font-semibold">Top 20</span>
            <span className="text-base font-mono font-bold text-blue-400">{data.top20ConcentrationPercentage}%</span>
          </div>
        </div>
      </div>

      {/* Epistemic Alert on Unresolved Securities */}
      {((data.unresolvedSecuritiesCount ?? 0) > 0 || (data.marketCapExposure.unclassifiedPercentage ?? 0) > 0) && (
        <div className="rounded-lg bg-amber-950/30 border border-amber-800/60 p-3 text-xs text-amber-200">
          <span className="font-semibold text-amber-300">ZERO SYNTHETIC REDISTRIBUTION: </span>
          {data.unresolvedSecuritiesCount ?? 'Some'} disclosed items ({data.unresolvedWeightPercentage ?? data.marketCapExposure.unclassifiedPercentage}% weight) could not be resolved against master security databases. In accordance with YUKIRA epistemic rules, these weights are retained as unclassified rather than artificially normalized or distributed across resolved assets.
        </div>
      )}

      {/* Asset Allocation & Market Cap Split */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Asset Class Split */}
        <div className="bg-slate-950 p-4 rounded-lg border border-slate-800 space-y-3">
          <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            Asset Class Breakdown
          </h4>
          <div className="space-y-2">
            <div className="flex justify-between text-xs">
              <span className="text-slate-300">Net Equity</span>
              <span className="font-mono font-bold text-emerald-400">{data.assetAllocation.netEquityPercentage}%</span>
            </div>
            <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
              <div
                className="bg-emerald-500 h-full rounded-full"
                style={{ width: `${Math.min(100, Math.max(0, data.assetAllocation.netEquityPercentage))}%` }}
              />
            </div>

            <div className="flex justify-between text-xs pt-1">
              <span className="text-slate-300">Debt & Money Market</span>
              <span className="font-mono font-bold text-blue-400">{data.assetAllocation.debtPercentage}%</span>
            </div>
            <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
              <div
                className="bg-blue-500 h-full rounded-full"
                style={{ width: `${Math.min(100, Math.max(0, data.assetAllocation.debtPercentage))}%` }}
              />
            </div>

            <div className="flex justify-between text-xs pt-1">
              <span className="text-slate-300">Cash & Net Current Assets</span>
              <span className="font-mono font-bold text-amber-400">{data.assetAllocation.cashPercentage}%</span>
            </div>
            <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
              <div
                className="bg-amber-500 h-full rounded-full"
                style={{ width: `${Math.min(100, Math.max(0, data.assetAllocation.cashPercentage))}%` }}
              />
            </div>
          </div>
        </div>

        {/* Market Cap Exposure */}
        <div className="bg-slate-950 p-4 rounded-lg border border-slate-800 space-y-3">
          <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            SEBI Market-Cap Distribution
          </h4>
          <div className="space-y-2">
            <div className="flex justify-between text-xs">
              <span className="text-slate-300">Large Cap (Top 100)</span>
              <span className="font-mono font-bold text-indigo-400">{data.marketCapExposure.largeCapPercentage}%</span>
            </div>
            <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
              <div
                className="bg-indigo-500 h-full rounded-full"
                style={{ width: `${Math.min(100, Math.max(0, data.marketCapExposure.largeCapPercentage))}%` }}
              />
            </div>

            <div className="flex justify-between text-xs pt-1">
              <span className="text-slate-300">Mid Cap (101-250)</span>
              <span className="font-mono font-bold text-purple-400">{data.marketCapExposure.midCapPercentage}%</span>
            </div>
            <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
              <div
                className="bg-purple-500 h-full rounded-full"
                style={{ width: `${Math.min(100, Math.max(0, data.marketCapExposure.midCapPercentage))}%` }}
              />
            </div>

            <div className="flex justify-between text-xs pt-1">
              <span className="text-slate-300">Small Cap (251+)</span>
              <span className="font-mono font-bold text-pink-400">{data.marketCapExposure.smallCapPercentage}%</span>
            </div>
            <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden">
              <div
                className="bg-pink-500 h-full rounded-full"
                style={{ width: `${Math.min(100, Math.max(0, data.marketCapExposure.smallCapPercentage))}%` }}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Top 10 Holdings Table */}
      <div>
        <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-2">
          Top Disclosed Holdings
        </h4>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950 text-slate-400 font-semibold border-b border-slate-800">
              <tr>
                <th className="py-2 px-3">#</th>
                <th className="py-2 px-3">Security Name &amp; ISIN</th>
                <th className="py-2 px-3">Asset Class</th>
                <th className="py-2 px-3">Sector</th>
                <th className="py-2 px-3">Cap Category</th>
                <th className="py-2 px-3">Resolution</th>
                <th className="py-2 px-3 text-right">Weight</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 font-mono text-slate-300">
              {data.topHoldings.slice(0, 10).map((h) => (
                <tr key={h.rank} className="hover:bg-slate-800/30">
                  <td className="py-2 px-3 text-slate-500 font-bold">{h.rank}</td>
                  <td className="py-2 px-3">
                    <span className="font-sans font-medium text-slate-100 block">{h.securityName}</span>
                    {h.isin && (
                      <span className="font-mono text-[10px] text-slate-400 block">{h.isin}</span>
                    )}
                  </td>
                  <td className="py-2 px-3 text-[11px] text-slate-400">{h.assetClass}</td>
                  <td className="py-2 px-3 font-sans text-slate-300">{h.sector}</td>
                  <td className="py-2 px-3">
                    <span className="px-1.5 py-0.5 rounded text-[10px] bg-slate-800 text-slate-300 font-sans">
                      {h.marketCapCategory}
                    </span>
                  </td>
                  <td className="py-2 px-3">
                    {h.resolutionStatus ? (
                      <span
                        className={`px-1.5 py-0.5 rounded text-[9px] font-mono border ${
                          h.resolutionStatus === 'EXACT_ISIN_MATCH'
                            ? 'bg-emerald-950/70 text-emerald-400 border-emerald-800'
                            : h.resolutionStatus === 'NAME_CANDIDATE'
                            ? 'bg-amber-950/70 text-amber-300 border-amber-800'
                            : 'bg-slate-800 text-slate-400 border-slate-700'
                        }`}
                      >
                        {h.resolutionStatus === 'EXACT_ISIN_MATCH'
                          ? 'ISIN MATCH'
                          : h.resolutionStatus === 'NAME_CANDIDATE'
                          ? 'NAME MATCH'
                          : 'UNRESOLVED'}
                      </span>
                    ) : (
                      <span className="text-[10px] text-slate-500 font-mono">—</span>
                    )}
                  </td>
                  <td className="py-2 px-3 text-right font-bold text-blue-400">{h.reportedWeight}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Source Lineage Footnote */}
      <div className="text-[11px] text-slate-500 pt-2 border-t border-slate-800/60 flex flex-wrap items-center justify-between gap-2">
        <span>Grounded in official AMC monthly portfolio disclosure.</span>
        {data.sourceArtifactHash && (
          <span className="font-mono text-slate-400">
            Artifact SHA-256: {data.sourceArtifactHash.substring(0, 16)}...
          </span>
        )}
      </div>
    </div>
  );
}

