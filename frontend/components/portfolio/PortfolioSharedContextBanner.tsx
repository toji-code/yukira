'use client';

import React, { useState } from 'react';
import { PortfolioAllocationSnapshotDto, StressCalculationContextDto } from '@/types/portfolio-risk';
import { formatCurrency, formatPercentage } from '@/lib/utils/formatters';

interface PortfolioSharedContextBannerProps {
  snapshot?: PortfolioAllocationSnapshotDto | null;
  calculationContext?: StressCalculationContextDto | null;
  calculationRunId?: string | null;
  calculationInputFingerprint?: string | null;
  asOfDate?: string | null;
  knowledgeCutoff?: string | null;
  benchmarkCode?: string | null;
  sourceLineageState?: string | null;
  className?: string;
}

export function PortfolioSharedContextBanner({
  snapshot,
  calculationContext,
  calculationRunId,
  calculationInputFingerprint,
  asOfDate,
  knowledgeCutoff,
  benchmarkCode,
  sourceLineageState,
  className = '',
}: PortfolioSharedContextBannerProps) {
  const [copiedField, setCopiedField] = useState<string | null>(null);
  const [isExpanded, setIsExpanded] = useState<boolean>(false);

  const copyToClipboard = (text: string, label: string) => {
    if (!navigator?.clipboard) return;
    navigator.clipboard.writeText(text);
    setCopiedField(label);
    setTimeout(() => setCopiedField(null), 2000);
  };

  const effectiveSnapshotId = snapshot?.snapshotId || calculationContext?.portfolioAllocationSnapshotId || 'CANONICAL_PORTFOLIO_CURRENT';
  const effectiveFingerprint = snapshot?.snapshotFingerprint || calculationInputFingerprint || calculationContext?.calculationInputFingerprint;
  const effectiveRunId = calculationRunId || calculationContext?.calculationRunId;
  const effectiveAsOfDate = snapshot?.effectiveAsOfDate || asOfDate || calculationContext?.analysisCutoffDate || 'Point-in-Time';
  const effectiveKnowledgeCutoff = snapshot?.knowledgeCutoff || knowledgeCutoff || calculationContext?.knowledgeCutoff || 'Point-in-Time';
  const effectiveBenchmark = benchmarkCode || calculationContext?.requestedBenchmarkCode || 'PRIMARY_BENCHMARK';
  const effectiveLineageState = sourceLineageState || calculationContext?.sourceLineageState || 'AUTHORITATIVE_AMFI_ARCHIVE';

  const coveragePct = snapshot?.knownValueAllocationCoveragePct ?? 100.0;
  const isCompleteCoverage = snapshot ? snapshot.isCompleteValuationCoverage : coveragePct >= 99.99;
  const valuedCount = snapshot?.valuedPositionCount ?? 0;
  const unvaluedCount = snapshot?.unvaluedPositionCount ?? 0;
  const totalCount = snapshot?.totalPositionCount ?? (valuedCount + unvaluedCount);
  const knownValuedCapital = snapshot?.knownValuedCapitalAmount;
  const totalPortfolioCapital = snapshot?.totalPortfolioCapitalAmount;

  return (
    <div className={`rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-900/60 p-4 transition-all shadow-xs ${className}`}>
      {/* Top Bar: Primary Summary Badges & Quick Action */}
      <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-200/80 dark:border-slate-800/80">
        <div className="flex flex-wrap items-center gap-2 text-xs">
          <div className="flex items-center gap-1.5 font-medium text-slate-800 dark:text-slate-200">
            <svg className="w-4 h-4 text-primary-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
            </svg>
            <span className="font-semibold tracking-tight">Portfolio Provenance & Context</span>
          </div>

          <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-mono bg-slate-200/70 dark:bg-slate-800 text-slate-700 dark:text-slate-300">
            As-Of: <span className="font-semibold ml-1">{effectiveAsOfDate}</span>
          </span>

          <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-mono bg-slate-200/70 dark:bg-slate-800 text-slate-700 dark:text-slate-300" title="Point-in-Time Knowledge Cutoff Timestamp">
            Cutoff: <span className="font-semibold ml-1">{effectiveKnowledgeCutoff}</span>
          </span>

          <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-mono bg-blue-500/10 text-blue-700 dark:text-blue-300 border border-blue-500/20">
            Bench: <span className="font-semibold ml-1">{effectiveBenchmark}</span>
          </span>

          {/* Valuation Coverage Badge */}
          {isCompleteCoverage ? (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-500/20">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
              100% Capital Valued ({valuedCount}/{totalCount || valuedCount} holdings)
            </span>
          ) : (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-500/20">
              <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
              Partial Valuation: {formatPercentage(coveragePct)} ({valuedCount} valued, {unvaluedCount} excluded)
            </span>
          )}
        </div>

        {/* Toggle Details Button */}
        <button
          type="button"
          onClick={() => setIsExpanded(!isExpanded)}
          className="text-xs font-medium text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-100 flex items-center gap-1 cursor-pointer transition-colors"
        >
          <span>{isExpanded ? 'Hide Lineage Details' : 'Show Lineage Details'}</span>
          <svg className={`w-3.5 h-3.5 transition-transform ${isExpanded ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>
      </div>

      {/* Static Allocation Proxy Methodology Notice */}
      <div className="mt-2 text-[11px] text-slate-600 dark:text-slate-400 flex flex-wrap items-center justify-between gap-2">
        <p className="flex items-center gap-1.5">
          <span className="font-medium text-slate-700 dark:text-slate-300">Methodology Convention:</span>
          <span>Fixed-weight daily-rebalanced proxy replay of snapshot positions across historical stress episodes. Does not simulate past active trades or transaction friction.</span>
        </p>

        {effectiveFingerprint && (
          <div className="flex items-center gap-1 font-mono text-[10px] text-slate-500">
            <span>Input Hash:</span>
            <span className="text-slate-700 dark:text-slate-300 font-semibold" title={effectiveFingerprint}>
              {effectiveFingerprint.substring(0, 10)}...{effectiveFingerprint.substring(effectiveFingerprint.length - 6)}
            </span>
            <button
              type="button"
              onClick={() => copyToClipboard(effectiveFingerprint, 'fingerprint')}
              className="text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 cursor-pointer ml-0.5"
              title="Copy deterministic input fingerprint"
            >
              {copiedField === 'fingerprint' ? (
                <span className="text-emerald-500 font-bold">✓</span>
              ) : (
                <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 5H6a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2v-1M8 5a2 2 0 002 2h2a2 2 0 002-2M8 5a2 2 0 012-2h2a2 2 0 012 2m0 0h2a2 2 0 012 2v3m2 4H10a2 2 0 00-2 2v6a2 2 0 002 2h10a2 2 0 002-2v-6a2 2 0 00-2-2z" />
                </svg>
              )}
            </button>
          </div>
        )}
      </div>

      {/* Expanded Lineage & Cryptographic Audit Panel */}
      {isExpanded && (
        <div className="mt-3 pt-3 border-t border-slate-200/60 dark:border-slate-800/60 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
          <div className="bg-white/60 dark:bg-slate-800/40 p-2.5 rounded border border-slate-200/50 dark:border-slate-700/50">
            <div className="text-[10px] uppercase font-semibold text-slate-500">Allocation Snapshot</div>
            <div className="font-mono text-slate-800 dark:text-slate-200 font-medium truncate" title={effectiveSnapshotId}>
              {effectiveSnapshotId}
            </div>
            {knownValuedCapital !== undefined && knownValuedCapital !== null && (
              <div className="text-[11px] text-slate-600 dark:text-slate-400 mt-1">
                Known Valued: {formatCurrency(knownValuedCapital)}
                {totalPortfolioCapital ? ` / ${formatCurrency(totalPortfolioCapital)}` : ''}
              </div>
            )}
          </div>

          <div className="bg-white/60 dark:bg-slate-800/40 p-2.5 rounded border border-slate-200/50 dark:border-slate-700/50">
            <div className="text-[10px] uppercase font-semibold text-slate-500">Source Lineage State</div>
            <div className="font-semibold text-slate-800 dark:text-slate-200">
              {effectiveLineageState.replace(/_/g, ' ')}
            </div>
            <div className="text-[11px] text-slate-500 mt-1">
              Cryptographic SHA-256 AMFI Payload Authority
            </div>
          </div>

          <div className="bg-white/60 dark:bg-slate-800/40 p-2.5 rounded border border-slate-200/50 dark:border-slate-700/50">
            <div className="text-[10px] uppercase font-semibold text-slate-500">Run ID (Execution Instance)</div>
            <div className="flex items-center justify-between gap-1 mt-0.5">
              <span className="font-mono text-slate-700 dark:text-slate-300 truncate" title={effectiveRunId || 'N/A'}>
                {effectiveRunId ? `${effectiveRunId.substring(0, 16)}...` : 'N/A'}
              </span>
              {effectiveRunId && (
                <button
                  type="button"
                  onClick={() => copyToClipboard(effectiveRunId, 'runId')}
                  className="text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 cursor-pointer"
                  title="Copy execution Run ID"
                >
                  {copiedField === 'runId' ? <span className="text-emerald-500 font-bold">✓</span> : 'copy'}
                </button>
              )}
            </div>
            <div className="text-[10px] text-slate-400 mt-1">Ephemeral run execution context</div>
          </div>

          <div className="bg-white/60 dark:bg-slate-800/40 p-2.5 rounded border border-slate-200/50 dark:border-slate-700/50">
            <div className="text-[10px] uppercase font-semibold text-slate-500">Content-Addressed Fingerprint</div>
            <div className="flex items-center justify-between gap-1 mt-0.5">
              <span className="font-mono text-slate-700 dark:text-slate-300 truncate" title={effectiveFingerprint || 'N/A'}>
                {effectiveFingerprint ? `${effectiveFingerprint.substring(0, 16)}...` : 'N/A'}
              </span>
              {effectiveFingerprint && (
                <button
                  type="button"
                  onClick={() => copyToClipboard(effectiveFingerprint, 'fingerprint_full')}
                  className="text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 cursor-pointer"
                  title="Copy content-addressed input fingerprint"
                >
                  {copiedField === 'fingerprint_full' ? <span className="text-emerald-500 font-bold">✓</span> : 'copy'}
                </button>
              )}
            </div>
            <div className="text-[10px] text-slate-400 mt-1">Deterministic SHA-256 calculation inputs</div>
          </div>
        </div>
      )}
    </div>
  );
}

